package com.runningstats.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.runningstats.app.model.RunActivity;
import com.runningstats.app.model.WellnessEntry;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RunDatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "runstats.db";
    private static final int DB_VERSION = 2;

    private static final String TABLE_RUNS = "runs";
    private static final String TABLE_WELLNESS = "wellness";

    public RunDatabaseHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + TABLE_RUNS + " (" +
                        "id INTEGER PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "sport_type TEXT NOT NULL, " +
                        "start_epoch INTEGER NOT NULL, " +
                        "start_date_local TEXT NOT NULL, " +
                        "distance_meters REAL NOT NULL, " +
                        "moving_time_sec INTEGER NOT NULL, " +
                        "elapsed_time_sec INTEGER NOT NULL, " +
                        "elevation_gain REAL NOT NULL, " +
                        "avg_hr REAL NOT NULL, " +
                        "max_hr REAL NOT NULL, " +
                        "avg_cadence REAL NOT NULL, " +
                        "suffer_score REAL NOT NULL" +
                        ")"
        );
        db.execSQL("CREATE INDEX idx_runs_start_epoch ON " + TABLE_RUNS + " (start_epoch DESC)");
        createWellnessTable(db);
    }

    private void createWellnessTable(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_WELLNESS + " (" +
                        "date TEXT PRIMARY KEY, " +
                        "vo2max REAL NOT NULL, " +
                        "resting_hr REAL NOT NULL, " +
                        "hrv REAL NOT NULL, " +
                        "sleep_secs INTEGER NOT NULL, " +
                        "sleep_score REAL NOT NULL, " +
                        "weight REAL NOT NULL, " +
                        "is_sample INTEGER NOT NULL DEFAULT 0" +
                        ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Non-destructive upgrade so users keep all previously synced runs on app updates
        if (oldVersion < 2) {
            createWellnessTable(db);
        }
    }

    public int upsertRuns(List<RunActivity> runs) {
        if (runs == null || runs.isEmpty()) {
            return 0;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        int count = 0;
        try {
            for (RunActivity run : runs) {
                ContentValues cv = new ContentValues();
                cv.put("id", run.id);
                cv.put("name", run.name);
                cv.put("sport_type", run.sportType);
                cv.put("start_epoch", run.startEpochSeconds);
                cv.put("start_date_local", run.startDateLocal);
                cv.put("distance_meters", run.distanceMeters);
                cv.put("moving_time_sec", run.movingTimeSeconds);
                cv.put("elapsed_time_sec", run.elapsedTimeSeconds);
                cv.put("elevation_gain", run.elevationGainMeters);
                cv.put("avg_hr", run.averageHeartrate);
                cv.put("max_hr", run.maxHeartrate);
                cv.put("avg_cadence", run.averageCadence);
                cv.put("suffer_score", run.sufferScore);
                db.insertWithOnConflict(TABLE_RUNS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                count++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return count;
    }

    public List<RunActivity> getAllRuns() {
        List<RunActivity> results = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_RUNS,
                null,
                null,
                null,
                null,
                null,
                "start_epoch DESC"
        );
        try {
            int idxId = cursor.getColumnIndexOrThrow("id");
            int idxName = cursor.getColumnIndexOrThrow("name");
            int idxSport = cursor.getColumnIndexOrThrow("sport_type");
            int idxEpoch = cursor.getColumnIndexOrThrow("start_epoch");
            int idxDateLocal = cursor.getColumnIndexOrThrow("start_date_local");
            int idxDist = cursor.getColumnIndexOrThrow("distance_meters");
            int idxMoving = cursor.getColumnIndexOrThrow("moving_time_sec");
            int idxElapsed = cursor.getColumnIndexOrThrow("elapsed_time_sec");
            int idxElev = cursor.getColumnIndexOrThrow("elevation_gain");
            int idxAvgHr = cursor.getColumnIndexOrThrow("avg_hr");
            int idxMaxHr = cursor.getColumnIndexOrThrow("max_hr");
            int idxCadence = cursor.getColumnIndexOrThrow("avg_cadence");
            int idxSuffer = cursor.getColumnIndexOrThrow("suffer_score");

            while (cursor.moveToNext()) {
                results.add(new RunActivity(
                        cursor.getLong(idxId),
                        cursor.getString(idxName),
                        cursor.getString(idxSport),
                        cursor.getLong(idxEpoch),
                        cursor.getString(idxDateLocal),
                        cursor.getDouble(idxDist),
                        cursor.getInt(idxMoving),
                        cursor.getInt(idxElapsed),
                        cursor.getDouble(idxElev),
                        cursor.getDouble(idxAvgHr),
                        cursor.getDouble(idxMaxHr),
                        cursor.getDouble(idxCadence),
                        cursor.getDouble(idxSuffer)
                ));
            }
        } finally {
            cursor.close();
        }
        return results;
    }

    public long getNewestRealRunEpochSeconds() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT MAX(start_epoch) FROM " + TABLE_RUNS + " WHERE id > 0", null);
        try {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
            return 0L;
        } finally {
            cursor.close();
        }
    }

    public int getRunCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_RUNS, null);
        try {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
            return 0;
        } finally {
            cursor.close();
        }
    }

    public int upsertWellness(List<WellnessEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        int count = 0;
        try {
            for (WellnessEntry w : entries) {
                if (!w.hasAnyData() || w.date.isEmpty()) continue;
                ContentValues cv = new ContentValues();
                cv.put("date", w.date);
                cv.put("vo2max", w.vo2max);
                cv.put("resting_hr", w.restingHr);
                cv.put("hrv", w.hrv);
                cv.put("sleep_secs", w.sleepSecs);
                cv.put("sleep_score", w.sleepScore);
                cv.put("weight", w.weight);
                cv.put("is_sample", w.isSample ? 1 : 0);
                db.insertWithOnConflict(TABLE_WELLNESS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                count++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return count;
    }

    public List<WellnessEntry> getAllWellness() {
        List<WellnessEntry> results = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_WELLNESS,
                null,
                null,
                null,
                null,
                null,
                "date DESC"
        );
        try {
            int idxDate = cursor.getColumnIndexOrThrow("date");
            int idxVo2 = cursor.getColumnIndexOrThrow("vo2max");
            int idxRhr = cursor.getColumnIndexOrThrow("resting_hr");
            int idxHrv = cursor.getColumnIndexOrThrow("hrv");
            int idxSleepSecs = cursor.getColumnIndexOrThrow("sleep_secs");
            int idxSleepScore = cursor.getColumnIndexOrThrow("sleep_score");
            int idxWeight = cursor.getColumnIndexOrThrow("weight");
            int idxSample = cursor.getColumnIndexOrThrow("is_sample");

            while (cursor.moveToNext()) {
                results.add(new WellnessEntry(
                        cursor.getString(idxDate),
                        cursor.getDouble(idxVo2),
                        cursor.getDouble(idxRhr),
                        cursor.getDouble(idxHrv),
                        cursor.getInt(idxSleepSecs),
                        cursor.getDouble(idxSleepScore),
                        cursor.getDouble(idxWeight),
                        cursor.getInt(idxSample) == 1
                ));
            }
        } finally {
            cursor.close();
        }
        return results;
    }

    public String getNewestRealWellnessDate() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT MAX(date) FROM " + TABLE_WELLNESS + " WHERE is_sample = 0", null);
        try {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getString(0);
            }
            return "";
        } finally {
            cursor.close();
        }
    }

    public void deleteSampleRuns() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_RUNS, "id < 0", null);
    }

    public void deleteSampleWellness() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_WELLNESS, "is_sample = 1", null);
    }

    public void clearAllRuns() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_RUNS, null, null);
        db.delete(TABLE_WELLNESS, null, null);
    }

    /**
     * Generates 3 years of realistic sample runs AND wellness data (VO2 Max, Resting HR, HRV, Sleep)
     * so users can preview every chart, PR table, and AI coaching before syncing.
     */
    public int seedSampleRuns() {
        List<RunActivity> sample = new ArrayList<>();
        List<WellnessEntry> sampleWellness = new ArrayList<>();
        Random rng = new Random(42L);
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusYears(3).plusDays(7);
        ZoneId zone = ZoneId.systemDefault();

        String[] easyNames = {"Morning Easy Run", "Neighborhood Loop", "Recovery Jog", "Park Shakeout", "Aerobic Base Run"};
        String[] tempoNames = {"Threshold Tempo", "Cruise Intervals", "Progression Run", "Fast Finish 10K"};
        String[] longNames = {"Sunday Long Run", "River Trail Long Run", "Endurance Builder", "Half Marathon Prep"};

        long sampleId = -1L;
        LocalDate date = start;
        int totalDays = (int) (today.toEpochDay() - start.toEpochDay());
        int dayIndex = 0;

        while (!date.isAfter(today)) {
            int dow = date.getDayOfWeek().getValue();
            double progress = totalDays > 0 ? (double) dayIndex / totalDays : 1.0;

            double baseEasyPaceSecPerKm = 355.0 - (28.0 * progress); // 5:55/km -> 5:27/km
            double baseHr = 148.0 - (10.0 * progress); // 148 bpm -> 138 bpm

            // Daily sample wellness entry
            double vo2 = 48.0 + (5.2 * progress) + (rng.nextGaussian() * 0.4);
            double rhr = 55.0 - (6.0 * progress) + (rng.nextGaussian() * 1.5);
            double hrv = 52.0 + (14.0 * progress) + (rng.nextGaussian() * 5.0);
            int sleepSecs = (int) ((7.3 + rng.nextGaussian() * 0.65) * 3600.0);
            double sleepScore = Math.max(55.0, Math.min(96.0, 79.0 + rng.nextGaussian() * 8.0));
            double weight = 76.0 - (2.0 * progress) + (rng.nextGaussian() * 0.4);
            sampleWellness.add(new WellnessEntry(
                    date.toString(), vo2, rhr, hrv, Math.max(16000, sleepSecs), sleepScore, weight, true
            ));

            boolean shouldRun = false;
            String kind = "easy";

            if (dow == 2 || dow == 4 || dow == 6) {
                shouldRun = rng.nextDouble() < 0.78;
                kind = (dow == 4 && rng.nextDouble() < 0.55) ? "tempo" : "easy";
            } else if (dow == 7) {
                shouldRun = rng.nextDouble() < 0.82;
                kind = "long";
            } else if (dow == 3) {
                shouldRun = rng.nextDouble() < 0.35;
                kind = "easy";
            }

            if (shouldRun) {
                double distKm;
                double paceSecPerKm;
                double avgHr;
                double elevM;
                String name;
                String sportType = "Run";

                if ("long".equals(kind)) {
                    distKm = 13.0 + rng.nextDouble() * 9.5 + (2.5 * progress);
                    paceSecPerKm = baseEasyPaceSecPerKm + 8.0 + (rng.nextGaussian() * 7.0);
                    avgHr = baseHr + 4.0 + (rng.nextGaussian() * 4.0);
                    elevM = distKm * (7.0 + rng.nextDouble() * 8.0);
                    name = longNames[rng.nextInt(longNames.length)];
                    if (rng.nextDouble() < 0.30) {
                        sportType = "TrailRun";
                        elevM *= 1.8;
                        paceSecPerKm += 22.0;
                    }
                } else if ("tempo".equals(kind)) {
                    distKm = 5.1 + rng.nextDouble() * 6.0;
                    paceSecPerKm = baseEasyPaceSecPerKm - 38.0 + (rng.nextGaussian() * 6.0);
                    avgHr = baseHr + 18.0 + (rng.nextGaussian() * 3.5);
                    elevM = distKm * (4.0 + rng.nextDouble() * 5.0);
                    name = tempoNames[rng.nextInt(tempoNames.length)];
                } else {
                    distKm = 5.2 + rng.nextDouble() * 5.2;
                    paceSecPerKm = baseEasyPaceSecPerKm + (rng.nextGaussian() * 9.0);
                    avgHr = baseHr + (rng.nextGaussian() * 4.5);
                    elevM = distKm * (4.5 + rng.nextDouble() * 5.5);
                    name = easyNames[rng.nextInt(easyNames.length)];
                }

                int movingSec = (int) Math.round(distKm * paceSecPerKm);
                int elapsedSec = movingSec + rng.nextInt(90);
                double maxHr = Math.min(196.0, avgHr + 12.0 + rng.nextDouble() * 10.0);
                double cadenceHalf = 84.0 + rng.nextDouble() * 6.0;
                double suffer = Math.max(15.0, (movingSec / 60.0) * ((avgHr - 105.0) / 35.0));

                long epoch = date.atTime(7, 15).atZone(zone).toEpochSecond();
                String dateLocal = date.toString() + "T07:15:00Z";

                sample.add(new RunActivity(
                        sampleId--,
                        name,
                        sportType,
                        epoch,
                        dateLocal,
                        distKm * 1000.0,
                        movingSec,
                        elapsedSec,
                        elevM,
                        avgHr,
                        maxHr,
                        cadenceHalf,
                        suffer
                ));
            }

            date = date.plusDays(1);
            dayIndex++;
        }

        upsertWellness(sampleWellness);
        return upsertRuns(sample);
    }
}
