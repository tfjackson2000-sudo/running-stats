package com.runningstats.app.data;

import android.content.Context;
import android.net.Uri;

import com.runningstats.app.model.RunActivity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Imports runs directly from a Garmin Connect "Activities.csv" export file
 * (connect.garmin.com -> Activities -> All Activities -> Export CSV).
 */
public class GarminCsvImporter {

    private static final DateTimeFormatter GARMIN_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US);

    public static int importFromUri(Context context, Uri uri, RunDatabaseHelper db, boolean csvInMiles) throws Exception {
        InputStream stream = context.getContentResolver().openInputStream(uri);
        if (stream == null) {
            throw new IllegalStateException("Could not open selected CSV file.");
        }

        List<RunActivity> parsedRuns = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return 0;
            }
            // Strip UTF-8 BOM if present
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }

            List<String> headers = parseCsvLine(headerLine);
            Map<String, Integer> cols = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                cols.put(headers.get(i).trim().toLowerCase(Locale.US), i);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                List<String> fields = parseCsvLine(line);

                String type = getCol(fields, cols, "activity type", "type");
                if (!isRunningType(type)) {
                    continue;
                }

                String dateRaw = getCol(fields, cols, "date", "start time");
                String title = getCol(fields, cols, "title", "name");
                if (title.isEmpty()) title = type;

                double rawDist = parseNumber(getCol(fields, cols, "distance"));
                if (rawDist <= 0.0) continue;

                double distMeters = rawDist * (csvInMiles ? RunActivity.METERS_PER_MILE : RunActivity.METERS_PER_KM);
                int movingSec = parseDurationSeconds(getCol(fields, cols, "moving time", "time"));
                int elapsedSec = parseDurationSeconds(getCol(fields, cols, "elapsed time", "time"));
                if (movingSec <= 0) movingSec = elapsedSec;
                if (elapsedSec <= 0) elapsedSec = movingSec;

                double avgHr = parseNumber(getCol(fields, cols, "avg hr", "average heart rate"));
                double maxHr = parseNumber(getCol(fields, cols, "max hr", "max heart rate"));
                double elevRaw = parseNumber(getCol(fields, cols, "total ascent", "elevation gain", "elev gain"));
                double elevMeters = csvInMiles ? (elevRaw / 3.28084) : elevRaw;
                double cadence = parseNumber(getCol(fields, cols, "avg run cadence", "avg cadence"));

                long epoch = parseGarminDateEpoch(dateRaw);
                String isoDate = toIsoLocalDate(dateRaw, epoch);
                long id = deterministicId(dateRaw, title, distMeters);

                parsedRuns.add(new RunActivity(
                        id, title, type, epoch, isoDate,
                        distMeters, movingSec, elapsedSec, elevMeters,
                        avgHr, maxHr, cadence, 0.0
                ));
            }
        }

        if (!parsedRuns.isEmpty()) {
            db.deleteSampleRuns();
            return db.upsertRuns(parsedRuns);
        }
        return 0;
    }

    private static boolean isRunningType(String type) {
        if (type == null) return false;
        String n = type.trim().toLowerCase(Locale.US);
        return n.contains("run") || n.contains("treadmill");
    }

    private static String getCol(List<String> fields, Map<String, Integer> cols, String... names) {
        for (String name : names) {
            Integer idx = cols.get(name);
            if (idx != null && idx >= 0 && idx < fields.size()) {
                String val = fields.get(idx).trim();
                if (!val.isEmpty() && !val.equals("--")) {
                    return val;
                }
            }
        }
        return "";
    }

    private static double parseNumber(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("--")) return 0.0;
        String cleaned = raw.replace(",", "").replaceAll("[^0-9.\\-]", "");
        if (cleaned.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static int parseDurationSeconds(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("--")) return 0;
        String clean = raw.trim();
        int dot = clean.indexOf('.');
        if (dot > 0) clean = clean.substring(0, dot);
        String[] parts = clean.split(":");
        try {
            if (parts.length == 3) {
                return Integer.parseInt(parts[0]) * 3600
                        + Integer.parseInt(parts[1]) * 60
                        + Integer.parseInt(parts[2]);
            } else if (parts.length == 2) {
                return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            }
        } catch (NumberFormatException ignored) {
        }
        return 0;
    }

    private static long parseGarminDateEpoch(String raw) {
        if (raw != null && !raw.isEmpty()) {
            try {
                return LocalDateTime.parse(raw.trim(), GARMIN_DT)
                        .atZone(ZoneId.systemDefault())
                        .toEpochSecond();
            } catch (Exception ignored) {
            }
            if (raw.length() >= 10) {
                try {
                    return LocalDate.parse(raw.substring(0, 10))
                            .atStartOfDay(ZoneId.systemDefault())
                            .toEpochSecond();
                } catch (Exception ignored) {
                }
            }
        }
        return System.currentTimeMillis() / 1000L;
    }

    private static String toIsoLocalDate(String raw, long epoch) {
        if (raw != null && raw.length() >= 10) {
            try {
                LocalDate d = LocalDate.parse(raw.substring(0, 10));
                return d.toString() + "T00:00:00";
            } catch (Exception ignored) {
            }
        }
        return java.time.Instant.ofEpochSecond(epoch)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString() + "T00:00:00";
    }

    private static long deterministicId(String dateRaw, String title, double distMeters) {
        String key = "csv|" + dateRaw + "|" + title + "|" + Math.round(distMeters);
        long h = 1125899906842597L;
        for (int i = 0; i < key.length(); i++) {
            h = 31L * h + key.charAt(i);
        }
        return Math.abs(h == Long.MIN_VALUE ? 1L : h);
    }

    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result;
    }
}
