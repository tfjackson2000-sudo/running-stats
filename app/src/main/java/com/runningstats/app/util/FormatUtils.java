package com.runningstats.app.util;

import com.runningstats.app.model.RunActivity;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

public class FormatUtils {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    public static String formatDistance(double meters, boolean useMiles) {
        double value = meters / (useMiles ? RunActivity.METERS_PER_MILE : RunActivity.METERS_PER_KM);
        String unit = useMiles ? "mi" : "km";
        if (value >= 1000.0) {
            return String.format(Locale.US, "%,.0f %s", value, unit);
        }
        return String.format(Locale.US, "%.1f %s", value, unit);
    }

    public static String formatPace(double secondsPerUnit, boolean useMiles) {
        if (secondsPerUnit <= 0.0 || Double.isNaN(secondsPerUnit) || Double.isInfinite(secondsPerUnit)) {
            return "--:--";
        }
        int totalSec = (int) Math.round(secondsPerUnit);
        int mins = totalSec / 60;
        int secs = totalSec % 60;
        return String.format(Locale.US, "%d:%02d /%s", mins, secs, useMiles ? "mi" : "km");
    }

    public static String formatPaceShort(double minutesPerUnit) {
        if (minutesPerUnit <= 0.0 || Double.isNaN(minutesPerUnit) || Double.isInfinite(minutesPerUnit)) {
            return "";
        }
        int totalSec = (int) Math.round(minutesPerUnit * 60.0);
        int mins = totalSec / 60;
        int secs = totalSec % 60;
        return String.format(Locale.US, "%d:%02d", mins, secs);
    }

    public static String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "0m";
        }
        long hours = seconds / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours >= 10) {
            return String.format(Locale.US, "%dh %02dm", hours, mins);
        } else if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs);
        }
        return String.format(Locale.US, "%d:%02d", mins, secs);
    }

    public static String formatElevation(double meters, boolean useMiles) {
        if (useMiles) {
            double feet = meters * 3.28084;
            return String.format(Locale.US, "%,.0f ft", feet);
        }
        return String.format(Locale.US, "%,.0f m", meters);
    }

    public static String formatHr(double bpm) {
        if (bpm <= 40.0) {
            return "--";
        }
        return String.format(Locale.US, "%.0f bpm", bpm);
    }

    public static String formatDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(DATE_FMT);
    }

    public static String formatTimestamp(long epochMs) {
        if (epochMs <= 0L) {
            return "Never";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, HH:mm", Locale.US);
        return sdf.format(new Date(epochMs));
    }
}
