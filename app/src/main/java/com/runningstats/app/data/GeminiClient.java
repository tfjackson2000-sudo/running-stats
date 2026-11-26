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

    public interface StatusListener {
        void onStatus(String message);
    }

    private static final String[] FAST_FALLBACKS = {
            "gemini-flash-latest",
            "gemini-flash-lite-latest"
    };

    public static String askCoach(
            PrefsManager prefs,
            String systemContext,
            List<ChatMessage> conversationHistory,
            StatusListener statusListener
    ) throws Exception {
        String apiKey = prefs.getGeminiApiKey();
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("Add your Gemini API key in Settings to use the AI Running Coach.");
        }
        String primaryModel = prefs.getGeminiModel();
        String payloadJson = buildPayload(systemContext, conversationHistory);

        // Keep fallback cascade short and snappy (at most 3 models total)
        Set<String> seen = new LinkedHashSet<>();
        List<String> candidates = new ArrayList<>();
        candidates.add(primaryModel);
        for (String fb : FAST_FALLBACKS) {
            if (!fb.equalsIgnoreCase(primaryModel)) {
                candidates.add(fb);
            }
        }

        Exception lastError = null;
        int triedCount = 0;
        for (String model : candidates) {
            if (!seen.add(model.toLowerCase(Locale.US))) continue;
            if (triedCount >= 3) break;
            triedCount++;

            if (statusListener != null) {
                statusListener.onStatus(triedCount == 1
                        ? "Thinking with " + model + "..."
                        : primaryModel + " busy — trying " + model + "...");
            }

            try {
                String reply = callModelOnce(apiKey, model, payloadJson);
                if (!model.equalsIgnoreCase(primaryModel)) {
                    return reply + "\n\n(Answered via " + model + " while " + primaryModel + " was busy.)";
                }
                return reply;
            } catch (RetryableApiException e) {
                lastError = e;
            }
        }

        // One final quick discovery attempt if static aliases were unavailable
        if (statusListener != null) {
            statusListener.onStatus("Finding an available Gemini model...");
        }
        List<String> discovered = fetchAvailableGeminiModels(apiKey);
        int extraTried = 0;
        for (String model : discovered) {
            if (!seen.add(model.toLowerCase(Locale.US))) continue;
            if (extraTried >= 2) break;
            extraTried++;
            if (statusListener != null) {
                statusListener.onStatus("Trying " + model + "...");
            }
            try {
                String reply = callModelOnce(apiKey, model, payloadJson);
                return reply + "\n\n(Answered via " + model + " while " + primaryModel + " was busy.)";
            } catch (RetryableApiException e) {
                lastError = e;
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("Gemini models are temporarily busy. Please try again in a moment.");
    }

    private static class RetryableApiException extends IOException {
        RetryableApiException(String message) {
            super(message);
        }
    }

    private static String buildPayload(String systemContext, List<ChatMessage> conversationHistory) throws Exception {
        JSONObject payload = new JSONObject();

        // System instruction carrying the athlete's real running metrics
        JSONObject sysInstruction = new JSONObject();
        JSONArray sysParts = new JSONArray();
        sysParts.put(new JSONObject().put("text", systemContext));
        sysInstruction.put("parts", sysParts);
        payload.put("system_instruction", sysInstruction);

        // Build clean alternating user/model history starting with a user message
        // (skip local welcome banner and previous error bubbles)
        List<ChatMessage> clean = new ArrayList<>();
        int start = Math.max(0, conversationHistory.size() - 14);
        for (int i = start; i < conversationHistory.size(); i++) {
            ChatMessage msg = conversationHistory.get(i);
            if (!msg.isUser && (msg.text.startsWith("Could not reach Gemini:") || clean.isEmpty())) {
                continue;
            }
            clean.add(msg);
        }

        JSONArray contents = new JSONArray();
        String lastRole = null;
        for (ChatMessage msg : clean) {
            String role = msg.isUser ? "user" : "model";
            if (role.equals(lastRole) && contents.length() > 0) {
                JSONObject prev = contents.getJSONObject(contents.length() - 1);
                JSONArray prevParts = prev.getJSONArray("parts");
                prevParts.put(new JSONObject().put("text", msg.text));
            } else {
                JSONObject turn = new JSONObject();
                turn.put("role", role);
                JSONArray parts = new JSONArray();
                parts.put(new JSONObject().put("text", msg.text));
                turn.put("parts", parts);
                contents.put(turn);
                lastRole = role;
            }
        }
        payload.put("contents", contents);

        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", 0.5);
        generationConfig.put("maxOutputTokens", 2048);
        payload.put("generationConfig", generationConfig);
        return payload.toString();
    }

    private static String callModelOnce(String apiKey, String model, String payloadJson) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(25000);
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
        } catch (java.net.SocketTimeoutException e) {
            throw new RetryableApiException("Timed out waiting for " + model + ".");
        } finally {
            conn.disconnect();
        }
    }

    private static List<String> fetchAvailableGeminiModels(String apiKey) {
        List<String> flashModels = new ArrayList<>();
        List<String> liteModels = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=100").openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
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
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        List<String> ordered = new ArrayList<>();
        ordered.addAll(liteModels);
        ordered.addAll(flashModels);
        return ordered;
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
