package com.runningstats.app.data;

import android.util.Base64;

import com.runningstats.app.model.RunActivity;
import com.runningstats.app.model.WellnessEntry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Client for the free Intervals.icu personal REST API (https://intervals.icu).
 * Syncs both Garmin running activities AND Garmin wellness data (VO2 Max, Resting HR, HRV, Sleep, Weight).
 */
public class IntervalsClient {
    private static final String BASE_URL = "https://intervals.icu/api/v1/athlete/0";

    public interface SyncProgressListener {
        void onProgress(String message);
    }

    public static String verifyAndFetchAthleteName(PrefsManager prefs) throws Exception {
        String apiKey = prefs.getIntervalsApiKey();
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("Enter your Intervals.icu API key in Settings first.");
        }
        String body = getWithBasicAuth(BASE_URL, apiKey);
        JSONObject json = new JSONObject(body);
        String name = json.optString("name", "").trim();
        if (name.isEmpty()) {
            String first = json.optString("firstname", "").trim();
            String last = json.optString("lastname", "").trim();
            name = (first + " " + last).trim();
        }
        if (name.isEmpty()) {
            name = "Garmin Runner";
        }
        prefs.setAthleteName(name);
        return name;
    }

    public static int syncRuns(
            PrefsManager prefs,
            RunDatabaseHelper db,
            boolean fullHistory,
            SyncProgressListener listener
    ) throws Exception {
        String apiKey = prefs.getIntervalsApiKey();
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("Add your free Intervals.icu API key in Settings first.");
        }

        if (prefs.getAthleteName().isEmpty()) {
            try {
                verifyAndFetchAthleteName(prefs);
            } catch (Exception ignored) {
            }
        }

        LocalDate today = LocalDate.now();
        int totalRunsSaved = 0;
        boolean clearedSample = false;

        if (!fullHistory) {
            long newestEpoch = db.getNewestRealRunEpochSeconds();
            if (newestEpoch > 0L) {
                LocalDate oldest = Instant.ofEpochSecond(newestEpoch)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .minusDays(3);
                if (listener != null) {
                    listener.onProgress("Syncing runs since " + oldest + "...");
                }
                List<RunActivity> fetched = fetchRunsRange(apiKey, oldest, today);
                if (!fetched.isEmpty()) {
                    db.deleteSampleRuns();
                    totalRunsSaved += db.upsertRuns(fetched);
                }
                prefs.setLastSyncMs(System.currentTimeMillis());
                return totalRunsSaved;
            }
        }

        int startYear = 2012;
        int endYear = today.getYear();
        for (int yr = endYear; yr >= startYear; yr -= 2) {
            int chunkStart = Math.max(startYear, yr - 1);
            LocalDate oldest = LocalDate.of(chunkStart, 1, 1);
            LocalDate newest = (yr == endYear) ? today : LocalDate.of(yr, 12, 31);

            if (listener != null) {
                listener.onProgress("Syncing runs " + chunkStart + "–" + yr + " (" + totalRunsSaved + " saved)...");
            }

            List<RunActivity> chunk = fetchRunsRange(apiKey, oldest, newest);
            if (!chunk.isEmpty()) {
                if (!clearedSample) {
                    db.deleteSampleRuns();
                    clearedSample = true;
                }
                totalRunsSaved += db.upsertRuns(chunk);
            }
        }

        prefs.setLastSyncMs(System.currentTimeMillis());
        return totalRunsSaved;
    }

    /**
     * Syncs daily Garmin wellness entries (VO2 Max, Resting HR, HRV, Sleep, Weight) from Intervals.icu.
     */
    public static int syncWellness(
            PrefsManager prefs,
            RunDatabaseHelper db,
            boolean fullHistory,
            SyncProgressListener listener
    ) throws Exception {
        String apiKey = prefs.getIntervalsApiKey();
        if (apiKey.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now();
        int totalSaved = 0;
        boolean clearedSample = false;

        if (!fullHistory) {
            String newestDateStr = db.getNewestRealWellnessDate();
            if (newestDateStr != null && !newestDateStr.isEmpty()) {
                try {
                    LocalDate oldest = LocalDate.parse(newestDateStr.substring(0, 10)).minusDays(7);
                    if (listener != null) {
                        listener.onProgress("Syncing Garmin health & VO2 Max...");
                    }
                    List<WellnessEntry> fetched = fetchWellnessRange(apiKey, oldest, today);
                    if (!fetched.isEmpty()) {
                        db.deleteSampleWellness();
                        totalSaved += db.upsertWellness(fetched);
                    }
                    return totalSaved;
                } catch (Exception ignored) {
                }
            }
        }

        int startYear = 2016;
        int endYear = today.getYear();
        for (int yr = endYear; yr >= startYear; yr -= 2) {
            int chunkStart = Math.max(startYear, yr - 1);
            LocalDate oldest = LocalDate.of(chunkStart, 1, 1);
            LocalDate newest = (yr == endYear) ? today : LocalDate.of(yr, 12, 31);

            if (listener != null) {
                listener.onProgress("Syncing Garmin wellness " + chunkStart + "–" + yr + "...");
            }

            List<WellnessEntry> chunk = fetchWellnessRange(apiKey, oldest, newest);
            if (!chunk.isEmpty()) {
                if (!clearedSample) {
                    db.deleteSampleWellness();
                    clearedSample = true;
                }
                totalSaved += db.upsertWellness(chunk);
            }
        }
        return totalSaved;
    }

    private static List<RunActivity> fetchRunsRange(String apiKey, LocalDate oldest, LocalDate newest) throws Exception {
        String url = BASE_URL + "/activities?oldest=" + oldest + "&newest=" + newest;
        String body = getWithBasicAuth(url, apiKey);
        JSONArray array = new JSONArray(body);

        List<RunActivity> runs = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);

            double distance = optDoubleSafe(item, "distance", 0.0);
            int movingTime = optIntSafe(item, "moving_time", 0);
            if (distance <= 10.0 && movingTime <= 10) {
                continue;
            }

            String type = item.optString("type", "");
            if (!isRunningType(type)) {
                continue;
            }

            String rawId = item.optString("id", "");
            long id = toPositiveLongId(rawId, item.optString("start_date_local", ""), distance);
            String name = item.optString("name", "").trim();
            if (name.isEmpty()) {
                name = type;
            }

            String startDateLocal = item.optString("start_date_local", "");
            String startDateUtc = item.optString("start_date", "");
            long startEpoch = parseEpochSeconds(startDateUtc, startDateLocal);

            int elapsedTime = optIntSafe(item, "elapsed_time", movingTime);
            double elevation = optDoubleSafe(item, "total_elevation_gain", 0.0);
            double avgHr = optDoubleSafe(item, "average_heartrate", 0.0);
            double maxHr = optDoubleSafe(item, "max_heartrate", 0.0);
            double avgCadence = optDoubleSafe(item, "average_cadence", 0.0);
            double trainingLoad = optDoubleSafe(item, "icu_training_load", 0.0);

            runs.add(new RunActivity(
                    id, name, type, startEpoch, startDateLocal,
                    distance, movingTime, elapsedTime, elevation,
                    avgHr, maxHr, avgCadence, trainingLoad
            ));
        }
        return runs;
    }

    private static List<WellnessEntry> fetchWellnessRange(String apiKey, LocalDate oldest, LocalDate newest) throws Exception {
        String url = BASE_URL + "/wellness?oldest=" + oldest + "&newest=" + newest;
        String body = getWithBasicAuth(url, apiKey);
        JSONArray array = new JSONArray(body);

        List<WellnessEntry> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            String idDate = item.optString("id", "").trim();
            if (idDate.length() < 10) continue;
            idDate = idDate.substring(0, 10);

            double vo2max = optDoubleSafe(item, "vo2max", 0.0);
            double restingHr = optDoubleSafe(item, "restingHR", 0.0);
            double hrv = optDoubleSafe(item, "hrv", 0.0);
            int sleepSecs = optIntSafe(item, "sleepSecs", 0);
            double sleepScore = optDoubleSafe(item, "sleepScore", 0.0);
            double weight = optDoubleSafe(item, "weight", 0.0);

            WellnessEntry entry = new WellnessEntry(
                    idDate, vo2max, restingHr, hrv, sleepSecs, sleepScore, weight, false
            );
            if (entry.hasAnyData()) {
                list.add(entry);
            }
        }
        return list;
    }

    private static boolean isRunningType(String type) {
        if (type == null) return false;
        String n = type.trim().toLowerCase(Locale.US);
        return n.equals("run")
                || n.equals("trailrun")
                || n.equals("trail_run")
                || n.equals("virtualrun")
                || n.equals("virtual_run")
                || n.equals("treadmill")
                || n.contains("run");
    }

    private static long toPositiveLongId(String rawId, String dateLocal, double distance) {
        if (rawId != null && !rawId.isEmpty()) {
            String digits = rawId.replaceAll("[^0-9]", "");
            if (!digits.isEmpty() && digits.length() <= 18) {
                try {
                    long parsed = Long.parseLong(digits);
                    if (parsed > 0L) {
                        return parsed;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        String key = (rawId != null ? rawId : "") + "|" + dateLocal + "|" + Math.round(distance);
        long h = 1125899906842597L;
        for (int i = 0; i < key.length(); i++) {
            h = 31L * h + key.charAt(i);
        }
        return Math.abs(h == Long.MIN_VALUE ? 1L : h);
    }

    private static long parseEpochSeconds(String isoUtc, String isoLocal) {
        if (isoUtc != null && !isoUtc.isEmpty()) {
            try {
                return Instant.parse(isoUtc).getEpochSecond();
            } catch (Exception ignored) {
            }
        }
        if (isoLocal != null && !isoLocal.isEmpty()) {
            try {
                String clean = isoLocal.endsWith("Z") ? isoLocal.substring(0, isoLocal.length() - 1) : isoLocal;
                return LocalDateTime.parse(clean).atZone(ZoneId.systemDefault()).toEpochSecond();
            } catch (Exception ignored) {
            }
            if (isoLocal.length() >= 10) {
                try {
                    return LocalDate.parse(isoLocal.substring(0, 10))
                            .atStartOfDay(ZoneId.systemDefault())
                            .toEpochSecond();
                } catch (Exception ignored) {
                }
            }
        }
        return System.currentTimeMillis() / 1000L;
    }

    private static double optDoubleSafe(JSONObject obj, String key, double fallback) {
        if (!obj.has(key) || obj.isNull(key)) return fallback;
        double val = obj.optDouble(key, fallback);
        return (Double.isNaN(val) || Double.isInfinite(val)) ? fallback : val;
    }

    private static int optIntSafe(JSONObject obj, String key, int fallback) {
        if (!obj.has(key) || obj.isNull(key)) return fallback;
        return obj.optInt(key, fallback);
    }

    private static String getWithBasicAuth(String urlString, String apiKey) throws IOException {
        String credentials = "API_KEY:" + apiKey.trim();
        String basicAuth = "Basic " + Base64.encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP
        );

        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Authorization", basicAuth);
        conn.setRequestProperty("Accept", "application/json");
        try {
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
            if (code == 401 || code == 403) {
                throw new IOException("Invalid Intervals.icu API key (HTTP " + code + "). Check Settings → Developer Settings on intervals.icu.");
            }
            if (code < 200 || code >= 300) {
                throw new IOException("Intervals.icu HTTP " + code + ": " + sb);
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}
