package com.runningstats.app.data;

import com.runningstats.app.model.ChatMessage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class GeminiClient {

    private static final String[] STATIC_FALLBACKS = {
            "gemini-pro-latest",
            "gemini-flash-latest",
            "gemini-flash-lite-latest",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite"
    };

    public static String askCoach(
            PrefsManager prefs,
            String systemContext,
            List<ChatMessage> conversationHistory
    ) throws Exception {
        String apiKey = prefs.getGeminiApiKey();
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("Add your Gemini API key in Settings to use the AI Running Coach.");
        }
        String primaryModel = prefs.getGeminiModel();
        String payloadJson = buildPayload(systemContext, conversationHistory);

        // Build ordered candidate list: primary first, then static fallbacks
        Set<String> tried = new LinkedHashSet<>();
        List<String> candidates = new ArrayList<>();
        candidates.add(primaryModel);
        for (String fb : STATIC_FALLBACKS) {
            if (!fb.equalsIgnoreCase(primaryModel)) {
                candidates.add(fb);
            }
        }

        Exception lastError = null;
        for (String model : candidates) {
            if (!tried.add(model.toLowerCase(Locale.US))) continue;
            try {
                int attempts = model.equalsIgnoreCase(primaryModel) ? 2 : 1;
                String reply = callModelWithRetry(apiKey, model, payloadJson, attempts);
                if (!model.equalsIgnoreCase(primaryModel)) {
                    return reply + "\n\n(Answered via " + model + " while " + primaryModel + " was busy.)";
                }
                return reply;
            } catch (RetryableApiException e) {
                lastError = e;
            }
        }

        // Last resort: dynamically query available generateContent models for this API key
        List<String> discovered = fetchAvailableGeminiModels(apiKey);
        for (String model : discovered) {
            if (!tried.add(model.toLowerCase(Locale.US))) continue;
            try {
                String reply = callModelWithRetry(apiKey, model, payloadJson, 1);
                return reply + "\n\n(Answered via " + model + " while " + primaryModel + " was busy.)";
            } catch (RetryableApiException e) {
                lastError = e;
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("No Gemini model was available to answer right now. Please try again in a minute.");
    }

    private static class RetryableApiException extends IOException {
        RetryableApiException(String message) {
            super(message);
        }
    }

    private static String callModelWithRetry(String apiKey, String model, String payloadJson, int maxAttempts) throws Exception {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return callModelOnce(apiKey, model, payloadJson);
            } catch (RetryableApiException e) {
                if (attempt == maxAttempts) {
                    throw e;
                }
                try {
                    Thread.sleep(1500L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw new IOException("Gemini request failed.");
    }

    private static List<String> fetchAvailableGeminiModels(String apiKey) {
        List<String> proModels = new ArrayList<>();
        List<String> flashModels = new ArrayList<>();
        List<String> liteModels = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=100").openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("x-goog-api-key", apiKey);
            if (conn.getResponseCode() < 200 || conn.getResponseCode() >= 300) {
                return new ArrayList<>();
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            JSONObject root = new JSONObject(sb.toString());
            JSONArray models = root.optJSONArray("models");
            if (models == null) return new ArrayList<>();

            for (int i = 0; i < models.length(); i++) {
                JSONObject m = models.getJSONObject(i);
                String name = m.optString("name", "");
                if (name.startsWith("models/")) {
                    name = name.substring("models/".length());
                }
                String lower = name.toLowerCase(Locale.US);
                if (!lower.startsWith("gemini-") || lower.contains("embedding") || lower.contains("tts")
                        || lower.contains("image") || lower.contains("vision") || lower.contains("robotics")) {
                    continue;
                }
                JSONArray methods = m.optJSONArray("supportedGenerationMethods");
                boolean supportsGen = false;
                if (methods != null) {
                    for (int j = 0; j < methods.length(); j++) {
                        if ("generateContent".equals(methods.optString(j))) {
                            supportsGen = true;
                            break;
                        }
                    }
                }
                if (!supportsGen) continue;

                if (lower.contains("lite")) {
                    liteModels.add(name);
                } else if (lower.contains("flash")) {
                    flashModels.add(name);
                } else if (lower.contains("pro")) {
                    proModels.add(name);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        List<String> ordered = new ArrayList<>();
        ordered.addAll(flashModels);
        ordered.addAll(liteModels);
        ordered.addAll(proModels);
        return ordered;
    }

    private static String buildPayload(String systemContext, List<ChatMessage> conversationHistory) throws Exception {
        JSONObject payload = new JSONObject();

        // System instruction carrying the athlete's real running metrics
        JSONObject sysInstruction = new JSONObject();
        JSONArray sysParts = new JSONArray();
        sysParts.put(new JSONObject().put("text", systemContext));
        sysInstruction.put("parts", sysParts);
        payload.put("system_instruction", sysInstruction);

        // Recent conversation turns (up to last 14 messages to keep payload focused)
        JSONArray contents = new JSONArray();
        int start = Math.max(0, conversationHistory.size() - 14);
        for (int i = start; i < conversationHistory.size(); i++) {
            ChatMessage msg = conversationHistory.get(i);
            JSONObject turn = new JSONObject();
            turn.put("role", msg.isUser ? "user" : "model");
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text", msg.text));
            turn.put("parts", parts);
            contents.put(turn);
        }
        payload.put("contents", contents);

        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", 0.5);
        generationConfig.put("maxOutputTokens", 4096);
        payload.put("generationConfig", generationConfig);
        return payload.toString();
    }

    private static String callModelOnce(String apiKey, String model, String payloadJson) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(90000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("x-goog-api-key", apiKey);

        try {
            byte[] bodyBytes = payloadJson.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            int code = conn.getResponseCode();
            InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
            }

            if (code < 200 || code >= 300) {
                String errMsg = extractErrorMessage(sb.toString());
                String fullErr = "Gemini API error (" + code + "): " + errMsg;
                if (code == 404 || code == 429 || code == 503 || code == 500) {
                    throw new RetryableApiException(fullErr);
                }
                throw new IOException(fullErr);
            }

            JSONObject resp = new JSONObject(sb.toString());
            JSONArray candidates = resp.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                throw new IOException("Gemini returned no response candidates.");
            }
            JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
            if (content == null) {
                throw new IOException("Gemini response had empty content.");
            }
            JSONArray parts = content.optJSONArray("parts");
            if (parts == null || parts.length() == 0) {
                throw new IOException("Gemini response had no text parts.");
            }

            StringBuilder answer = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                String t = parts.getJSONObject(i).optString("text", "");
                if (!t.isEmpty()) {
                    if (answer.length() > 0) {
                        answer.append("\n");
                    }
                    answer.append(t);
                }
            }
            return answer.toString().trim();
        } finally {
            conn.disconnect();
        }
    }

    private static String extractErrorMessage(String raw) {
        try {
            JSONObject obj = new JSONObject(raw);
            JSONObject err = obj.optJSONObject("error");
            if (err != null) {
                return err.optString("message", raw);
            }
        } catch (Exception ignored) {
        }
        return raw;
    }
}
