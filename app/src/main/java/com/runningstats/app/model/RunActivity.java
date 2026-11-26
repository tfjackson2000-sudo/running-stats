package com.runningstats.app.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

public class RunActivity {
    public static final double METERS_PER_KM = 1000.0;
    public static final double METERS_PER_MILE = 1609.344;

    public final long id;
    public final String name;
    public final String sportType;
    public final long startEpochSeconds;
    public final String startDateLocal;
    public final double distanceMeters;
    public final int movingTimeSeconds;
    public final int elapsedTimeSeconds;
    public final double elevationGainMeters;
    public final double averageHeartrate;
    public final double maxHeartrate;
    public final double averageCadence;
    public final double sufferScore;

    public RunActivity(
            long id,
            String name,
            String sportType,
            long startEpochSeconds,
            String startDateLocal,
            double distanceMeters,
            int movingTimeSeconds,
            int elapsedTimeSeconds,
            double elevationGainMeters,
            double averageHeartrate,
            double maxHeartrate,
            double averageCadence,
            double sufferScore
    ) {
        this.id = id;
        this.name = name != null ? name : "Run";
        this.sportType = sportType != null ? sportType : "Run";
        this.startEpochSeconds = startEpochSeconds;
        this.startDateLocal = startDateLocal != null ? startDateLocal : "";
        this.distanceMeters = Math.max(0.0, distanceMeters);
        this.movingTimeSeconds = Math.max(0, movingTimeSeconds);
        this.elapsedTimeSeconds = Math.max(0, elapsedTimeSeconds);
        this.elevationGainMeters = Math.max(0.0, elevationGainMeters);
        this.averageHeartrate = Math.max(0.0, averageHeartrate);
        this.maxHeartrate = Math.max(0.0, maxHeartrate);
        this.averageCadence = Math.max(0.0, averageCadence);
        this.sufferScore = Math.max(0.0, sufferScore);
    }

    public LocalDate getLocalDate() {
        if (startDateLocal != null && startDateLocal.length() >= 10) {
            try {
                return LocalDate.parse(startDateLocal.substring(0, 10));
            } catch (Exception ignored) {
            }
        }
        return Instant.ofEpochSecond(startEpochSeconds)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    public int getYear() {
        return getLocalDate().getYear();
    }

    public YearMonth getYearMonth() {
        return YearMonth.from(getLocalDate());
    }

    public double getDistance(boolean useMiles) {
        return distanceMeters / (useMiles ? METERS_PER_MILE : METERS_PER_KM);
    }

    /**
     * Returns pace in seconds per km (or seconds per mile if useMiles is true).
     */
    public double getPaceSeconds(boolean useMiles) {
        double dist = getDistance(useMiles);
        if (dist <= 0.01 || movingTimeSeconds <= 0) {
            return 0.0;
        }
        return movingTimeSeconds / dist;
    }

    /**
     * Returns pace in minutes per km (or minutes per mile if useMiles is true).
     */
    public double getPaceMinutes(boolean useMiles) {
        return getPaceSeconds(useMiles) / 60.0;
    }

    /**
     * Meters covered per heartbeat (higher indicates stronger aerobic efficiency).
     */
    public double getAerobicEfficiency() {
        if (averageHeartrate <= 40.0 || movingTimeSeconds <= 60 || distanceMeters <= 100.0) {
            return 0.0;
        }
        double totalBeats = averageHeartrate * (movingTimeSeconds / 60.0);
        return distanceMeters / totalBeats;
    }

    /**
     * Strava reports running cadence in single-leg strides/min; convert to steps/min when needed.
     */
    public double getStepsPerMinute() {
        if (averageCadence <= 0.0) {
            return 0.0;
        }
        return averageCadence < 120.0 ? averageCadence * 2.0 : averageCadence;
    }
}
