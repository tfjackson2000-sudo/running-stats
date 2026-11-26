package com.runningstats.app.model;

import java.time.LocalDate;
import java.time.YearMonth;

public class WellnessEntry {
    public final String date; // YYYY-MM-DD
    public final double vo2max;
    public final double restingHr;
    public final double hrv;
    public final int sleepSecs;
    public final double sleepScore;
    public final double weight;
    public final boolean isSample;

    public WellnessEntry(
            String date,
            double vo2max,
            double restingHr,
            double hrv,
            int sleepSecs,
            double sleepScore,
            double weight,
            boolean isSample
    ) {
        this.date = date != null ? date : "";
        this.vo2max = Math.max(0.0, vo2max);
        this.restingHr = Math.max(0.0, restingHr);
        this.hrv = Math.max(0.0, hrv);
        this.sleepSecs = Math.max(0, sleepSecs);
        this.sleepScore = Math.max(0.0, sleepScore);
        this.weight = Math.max(0.0, weight);
        this.isSample = isSample;
    }

    public boolean hasAnyData() {
        return vo2max > 0.0 || restingHr > 0.0 || hrv > 0.0 || sleepSecs > 0 || sleepScore > 0.0 || weight > 0.0;
    }

    public LocalDate getLocalDate() {
        try {
            return LocalDate.parse(date.substring(0, 10));
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    public YearMonth getYearMonth() {
        return YearMonth.from(getLocalDate());
    }

    public double getSleepHours() {
        return sleepSecs > 0 ? sleepSecs / 3600.0 : 0.0;
    }
}
