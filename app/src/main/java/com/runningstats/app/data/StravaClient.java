package com.runningstats.app.data;

import android.net.Uri;

import com.runningstats.app.model.RunActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class StravaClient {
    public static final String REDIRECT_URI = "runningstats://localhost";
    private static final String TOKEN_URL = "https://www.strava.com/oauth/token";
    private static final String ACTIVITIES_URL = "https://www.strava.com/api/v3/athlete/activities";

    public interface SyncProgressListener {
        void onProgress(String message);
    }

    public static Uri buildAuthorizeUri(String clientId) {
        return Uri.parse("https://www.strava.com/oauth/mobile/authorize")
                .buildUpon()
                .appendQueryParameter("client_id", clientId.trim())
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("approval_prompt", "auto")
                .appendQueryParameter("scope", "read,activity:read_all")
                .build();
    }

    public static String extractCodeFromInput(String rawInput) {
        if (rawInput == null) {
            return "";
        }
        String trimmed = rawInput.trim();
        if (trimmed.contains("code=")) {
            try {
                Uri uri = Uri.parse(trimmed);
                String code = uri.getQueryParameter("code");
                if (code != null && !code.isEmpty()) {
                    return code.trim();
                }
            } catch (Exception ignored) {
            }
        }
        return trimmed;
    }

    public static String exchangeCodeForTokens(PrefsManager prefs, String code) throws Exception {
        String clientId = prefs.getStravaClientId();
        String clientSecret = prefs.getStravaClientSecret();
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            throw new IllegalStateException("Please enter your Strava Client ID and Client Secret in Settings first.");
        }

        String formBody = "client_id=" + urlEncode(clientId)
                + "&client_secret=" + urlEncode(clientSecret)
                + "&code=" + urlEncode(code)
                + "&grant_type=authorization_code";

        String response = postForm(TOKEN_URL, formBody);
        JSONObject json = new JSONObject(response);

        String accessToken = json.getString("access_token");
        String refreshToken = json.getString("refresh_token");
        long expiresAt = json.getLong("expires_at");

        String athleteName = "";
        JSONObject athlete = json.optJSONObject("athlete");
        if (athlete != null) {
            String first = athlete.optString("firstname", "");
            String last = athlete.optString("lastname", "");
            athleteName = (first + " " + last).trim();
        }

        prefs.saveStravaTokens(accessToken, refreshToken, expiresAt, athleteName);
        return athleteName.isEmpty() ? "Strava Athlete" : athleteName;
    }

    public static String ensureValidAccessToken(PrefsManager prefs) throws Exception {
        if (!prefs.isStravaConnected()) {
            throw new IllegalStateException("Not connected to Strava. Connect your account in Settings.");
        }

        long nowSec = System.currentTimeMillis() / 1000L;
        String currentAccess = prefs.getStravaAccessToken();
        if (currentAccess != null && !currentAccess.isEmpty() && prefs.getStravaExpiresAt() > nowSec + 300L) {
            return currentAccess;
        }

        String clientId = prefs.getStravaClientId();
        String clientSecret = prefs.getStravaClientSecret();
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            throw new IllegalStateException("Missing Strava Client ID or Client Secret in Settings.");
        }

        String formBody = "client_id=" + urlEncode(clientId)
                + "&client_secret=" + urlEncode(clientSecret)
                + "&refresh_token=" + urlEncode(prefs.getStravaRefreshToken())
                + "&grant_type=refresh_token";

        String response = postForm(TOKEN_URL, formBody);
        JSONObject json = new JSONObject(response);

        String accessToken = json.getString("access_token");
        String refreshToken = json.getString("refresh_token");
        long expiresAt = json.getLong("expires_at");

        prefs.saveStravaTokens(accessToken, refreshToken, expiresAt, prefs.getAthleteName());
        return accessToken;
    }

    /**
     * Syncs running activities from Strava into the local SQLite database.
     *
     * @param fullHistory if true, fetches all pages from history; otherwise fetches runs newer than latest synced run.
     * @return number of running activities saved.
     */
    public static int syncRuns(
            PrefsManager prefs,
            RunDatabaseHelper db,
            boolean fullHistory,
            SyncProgressListener listener
    ) throws Exception {
        String accessToken = ensureValidAccessToken(prefs);
        long afterEpoch = 0L;
        if (!fullHistory) {
            long newest = db.getNewestRealRunEpochSeconds();
            if (newest > 0L) {
                // Subtract 24 hours overlap so edits/late uploads are never missed
                afterEpoch = Math.max(0L, newest - 86400L);
            }
        }

        int page = 1;
        int perPage = 100;
        int totalRunsSaved = 0;

        // Replace sample placeholder data once real Strava data begins syncing
        boolean clearedSample = false;

        while (page <= 80) {
            if (listener != null) {
                listener.onProgress("Fetching Strava page " + page + " (" + totalRunsSaved + " runs saved)...");
            }

            StringBuilder urlBuilder = new StringBuilder(ACTIVITIES_URL)
                    .append("?per_page=").append(perPage)
                    .append("&page=").append(page);
            if (afterEpoch > 0L) {
                urlBuilder.append("&after=").append(afterEpoch);
            }

            String body = getWithBearer(urlBuilder.toString(), accessToken);
            JSONArray array = new JSONArray(body);
            if (array.length() == 0) {
                break;
            }

            List<RunActivity> pageRuns = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String sportType = item.optString("sport_type", item.optString("type", ""));
                if (!isRunningType(sportType)) {
                    continue;
                }

                long id = item.getLong("id");
                String name = item.optString("name", "Run");
                String startDateUtc = item.optString("start_date", "");
                String startDateLocal = item.optString("start_date_local", startDateUtc);
                long startEpoch = parseEpochSeconds(startDateUtc);
                double distance = item.optDouble("distance", 0.0);
                int movingTime = item.optInt("moving_time", 0);
                int elapsedTime = item.optInt("elapsed_time", movingTime);
                double elevation = item.optDouble("total_elevation_gain", 0.0);
                double avgHr = item.has("average_heartrate") && !item.isNull("average_heartrate")
                        ? item.optDouble("average_heartrate", 0.0) : 0.0;
                double maxHr = item.has("max_heartrate") && !item.isNull("max_heartrate")
                        ? item.optDouble("max_heartrate", 0.0) : 0.0;
                double avgCadence = item.has("average_cadence") && !item.isNull("average_cadence")
                        ? item.optDouble("average_cadence", 0.0) : 0.0;
                double sufferScore = item.has("suffer_score") && !item.isNull("suffer_score")
                        ? item.optDouble("suffer_score", 0.0) : 0.0;

                pageRuns.add(new RunActivity(
                        id, name, sportType, startEpoch, startDateLocal,
                        distance, movingTime, elapsedTime, elevation,
                        avgHr, maxHr, avgCadence, sufferScore
                ));
            }

            if (!pageRuns.isEmpty()) {
                if (!clearedSample) {
                    db.deleteSampleRuns();
                    clearedSample = true;
                }
                totalRunsSaved += db.upsertRuns(pageRuns);
            }

            if (array.length() < perPage) {
                break;
            }
            page++;
        }

        prefs.setLastSyncMs(System.currentTimeMillis());
        return totalRunsSaved;
    }

    private static boolean isRunningType(String sportType) {
        if (sportType == null) {
            return false;
        }
        String normalized = sportType.trim().toLowerCase();
        return normalized.equals("run")
                || normalized.equals("trailrun")
                || normalized.equals("trail_run")
                || normalized.equals("virtualrun")
                || normalized.equals("virtual_run");
    }

    private static long parseEpochSeconds(String isoUtc) {
        if (isoUtc == null || isoUtc.isEmpty()) {
            return System.currentTimeMillis() / 1000L;
        }
        try {
            return Instant.parse(isoUtc).getEpochSecond();
        } catch (Exception e) {
            return System.currentTimeMillis() / 1000L;
        }
    }

    private static String postForm(String urlString, String formBody) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(25000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try {
            byte[] bytes = formBody.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }
            return readResponse(conn);
        } finally {
            conn.disconnect();
        }
    }

    private static String getWithBearer(String urlString, String accessToken) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(25000);
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept", "application/json");
        try {
            return readResponse(conn);
        } finally {
            conn.disconnect();
        }
    }

    private static String readResponse(HttpURLConnection conn) throws IOException {
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
            if (code == 429) {
                throw new IOException("Strava API rate limit reached (HTTP 429). Wait 15 minutes and sync again — runs already fetched are saved.");
            }
            throw new IOException("Strava HTTP " + code + ": " + sb);
        }
        return sb.toString();
    }

    private static String urlEncode(String val) throws IOException {
        return URLEncoder.encode(val, "UTF-8");
    }
}
