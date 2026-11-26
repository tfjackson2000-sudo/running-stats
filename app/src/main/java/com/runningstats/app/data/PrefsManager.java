package com.runningstats.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.runningstats.app.BuildConfig;

public class PrefsManager {
    private static final String PREFS_NAME = "runstats_prefs";

    private static final String KEY_USE_MILES = "use_miles";
    private static final String KEY_STRAVA_CLIENT_ID = "strava_client_id";
    private static final String KEY_STRAVA_CLIENT_SECRET = "strava_client_secret";
    private static final String KEY_STRAVA_ACCESS_TOKEN = "strava_access_token";
    private static final String KEY_STRAVA_REFRESH_TOKEN = "strava_refresh_token";
    private static final String KEY_STRAVA_EXPIRES_AT = "strava_expires_at";
    private static final String KEY_STRAVA_ATHLETE_NAME = "strava_athlete_name";
    private static final String KEY_LAST_SYNC_MS = "last_sync_ms";
    private static final String KEY_GEMINI_API_KEY = "gemini_api_key";
    private static final String KEY_GEMINI_MODEL = "gemini_model";

    public static final String DEFAULT_GEMINI_MODEL = "gemini-flash-latest";

    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean useMiles() {
        return prefs.getBoolean(KEY_USE_MILES, false);
    }

    public void setUseMiles(boolean useMiles) {
        prefs.edit().putBoolean(KEY_USE_MILES, useMiles).apply();
    }

    public String getStravaClientId() {
        String saved = prefs.getString(KEY_STRAVA_CLIENT_ID, "");
        if (saved != null && !saved.trim().isEmpty()) {
            return saved.trim();
        }
        return BuildConfig.DEFAULT_STRAVA_CLIENT_ID != null ? BuildConfig.DEFAULT_STRAVA_CLIENT_ID.trim() : "";
    }

    public void setStravaClientId(String clientId) {
        prefs.edit().putString(KEY_STRAVA_CLIENT_ID, clientId != null ? clientId.trim() : "").apply();
    }

    public String getStravaClientSecret() {
        String saved = prefs.getString(KEY_STRAVA_CLIENT_SECRET, "");
        if (saved != null && !saved.trim().isEmpty()) {
            return saved.trim();
        }
        return BuildConfig.DEFAULT_STRAVA_CLIENT_SECRET != null ? BuildConfig.DEFAULT_STRAVA_CLIENT_SECRET.trim() : "";
    }

    public void setStravaClientSecret(String clientSecret) {
        prefs.edit().putString(KEY_STRAVA_CLIENT_SECRET, clientSecret != null ? clientSecret.trim() : "").apply();
    }

    public boolean hasStravaCredentials() {
        return !getStravaClientId().isEmpty() && !getStravaClientSecret().isEmpty();
    }

    public String getStravaAccessToken() {
        return prefs.getString(KEY_STRAVA_ACCESS_TOKEN, "");
    }

    public String getStravaRefreshToken() {
        return prefs.getString(KEY_STRAVA_REFRESH_TOKEN, "");
    }

    public long getStravaExpiresAt() {
        return prefs.getLong(KEY_STRAVA_EXPIRES_AT, 0L);
    }

    public String getAthleteName() {
        return prefs.getString(KEY_STRAVA_ATHLETE_NAME, "");
    }

    public boolean isStravaConnected() {
        String refresh = getStravaRefreshToken();
        return refresh != null && !refresh.isEmpty();
    }

    public void saveStravaTokens(String accessToken, String refreshToken, long expiresAt, String athleteName) {
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_STRAVA_ACCESS_TOKEN, accessToken != null ? accessToken : "")
                .putString(KEY_STRAVA_REFRESH_TOKEN, refreshToken != null ? refreshToken : "")
                .putLong(KEY_STRAVA_EXPIRES_AT, expiresAt);
        if (athleteName != null && !athleteName.trim().isEmpty()) {
            editor.putString(KEY_STRAVA_ATHLETE_NAME, athleteName.trim());
        }
        editor.apply();
    }

    public void clearStravaTokens() {
        prefs.edit()
                .remove(KEY_STRAVA_ACCESS_TOKEN)
                .remove(KEY_STRAVA_REFRESH_TOKEN)
                .remove(KEY_STRAVA_EXPIRES_AT)
                .remove(KEY_STRAVA_ATHLETE_NAME)
                .apply();
    }

    public long getLastSyncMs() {
        return prefs.getLong(KEY_LAST_SYNC_MS, 0L);
    }

    public void setLastSyncMs(long timestampMs) {
        prefs.edit().putLong(KEY_LAST_SYNC_MS, timestampMs).apply();
    }

    public String getGeminiApiKey() {
        String saved = prefs.getString(KEY_GEMINI_API_KEY, "");
        if (saved != null && !saved.trim().isEmpty()) {
            return saved.trim();
        }
        return BuildConfig.DEFAULT_GEMINI_API_KEY != null ? BuildConfig.DEFAULT_GEMINI_API_KEY.trim() : "";
    }

    public void setGeminiApiKey(String apiKey) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, apiKey != null ? apiKey.trim() : "").apply();
    }

    public boolean isGeminiConfigured() {
        return !getGeminiApiKey().isEmpty();
    }

    public String getGeminiModel() {
        String saved = prefs.getString(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL);
        if (saved == null || saved.trim().isEmpty()) {
            return DEFAULT_GEMINI_MODEL;
        }
        return saved.trim();
    }

    public void setGeminiModel(String model) {
        String clean = (model == null || model.trim().isEmpty()) ? DEFAULT_GEMINI_MODEL : model.trim();
        prefs.edit().putString(KEY_GEMINI_MODEL, clean).apply();
    }
}
