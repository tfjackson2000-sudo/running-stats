package com.runningstats.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.runningstats.app.BuildConfig;

public class PrefsManager {
    private static final String PREFS_NAME = "runstats_prefs";

    private static final String KEY_USE_MILES = "use_miles";
    private static final String KEY_INTERVALS_API_KEY = "intervals_api_key";
    private static final String KEY_ATHLETE_NAME = "athlete_name";
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

    public String getIntervalsApiKey() {
        String saved = prefs.getString(KEY_INTERVALS_API_KEY, "");
        if (saved != null && !saved.trim().isEmpty()) {
            return saved.trim();
        }
        return BuildConfig.DEFAULT_INTERVALS_API_KEY != null ? BuildConfig.DEFAULT_INTERVALS_API_KEY.trim() : "";
    }

    public void setIntervalsApiKey(String apiKey) {
        prefs.edit().putString(KEY_INTERVALS_API_KEY, apiKey != null ? apiKey.trim() : "").apply();
    }

    public boolean isIntervalsConnected() {
        return !getIntervalsApiKey().isEmpty();
    }

    public void clearIntervalsConnection() {
        prefs.edit()
                .remove(KEY_INTERVALS_API_KEY)
                .remove(KEY_ATHLETE_NAME)
                .apply();
    }

    public String getAthleteName() {
        return prefs.getString(KEY_ATHLETE_NAME, "");
    }

    public void setAthleteName(String athleteName) {
        prefs.edit().putString(KEY_ATHLETE_NAME, athleteName != null ? athleteName.trim() : "").apply();
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
