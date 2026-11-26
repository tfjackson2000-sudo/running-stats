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
import java.util.List;

public class GeminiClient {

    public static String askCoach(
            PrefsManager prefs,
            String systemContext,
            List<ChatMessage> conversationHistory
    ) throws Exception {
        String apiKey = prefs.getGeminiApiKey();
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("Add your Gemini API key in Settings to use the AI Running Coach.");
        }
        String model = prefs.getGeminiModel();
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";

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

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(90000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("x-goog-api-key", apiKey);

        try {
            byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
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
                throw new IOException("Gemini API error (" + code + "): " + errMsg);
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
