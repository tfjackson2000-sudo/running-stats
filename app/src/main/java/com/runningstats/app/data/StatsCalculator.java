package com.runningstats.app.data;

import com.runningstats.app.model.RunActivity;
import com.runningstats.app.util.FormatUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class StatsCalculator {

    public static class SummaryStats {
        public int runCount;
        public double totalDistanceMeters;
        public long totalMovingSeconds;
        public double totalElevationMeters;
        public double avgHr;
        public int hrRunCount;
        public RunActivity longestRun;
        public RunActivity fastestRun; // >= 5km
        public RunActivity biggestClimbRun;

        public double getAvgPaceSeconds(boolean useMiles) {
            double dist = totalDistanceMeters / (useMiles ? RunActivity.METERS_PER_MILE : RunActivity.METERS_PER_KM);
            if (dist <= 0.01 || totalMovingSeconds <= 0) {
                return 0.0;
            }
            return totalMovingSeconds / dist;
        }
    }

    public static class TrainingInsights {
        public int currentWeekStreak;
        public int longestWeekStreak;
        public double last7DaysMeters;
        public double weeklyAvg28DaysMeters;
        public double acwr; // Acute (7d) : Chronic (28d weekly avg)
        public double recentEfficiency; // last 90d meters/beat
        public double priorEfficiency;  // 90-180d meters/beat
        public double efficiencyChangePct;
    }

    public static class MonthlyPoint {
        public final YearMonth yearMonth;
        public final String label;
        public double distanceMeters;
        public long movingSeconds;
        public double hrWeightedSum;
        public long hrSeconds;
        public int runCount;

        public MonthlyPoint(YearMonth ym, String label) {
            this.yearMonth = ym;
            this.label = label;
        }

        public double getDistance(boolean useMiles) {
            return distanceMeters / (useMiles ? RunActivity.METERS_PER_MILE : RunActivity.METERS_PER_KM);
        }

        public double getAvgPaceMinutes(boolean useMiles) {
            double dist = getDistance(useMiles);
            if (dist <= 0.01 || movingSeconds <= 0) {
                return 0.0;
            }
            return (movingSeconds / dist) / 60.0;
        }

        public double getAvgHr() {
            if (hrSeconds <= 0) {
                return 0.0;
            }
            return hrWeightedSum / hrSeconds;
        }
    }

    public static SummaryStats computeSummary(List<RunActivity> runs) {
        SummaryStats s = new SummaryStats();
        if (runs == null) {
            return s;
        }
        double hrSum = 0.0;
        for (RunActivity r : runs) {
            s.runCount++;
            s.totalDistanceMeters += r.distanceMeters;
            s.totalMovingSeconds += r.movingTimeSeconds;
            s.totalElevationMeters += r.elevationGainMeters;

            if (r.averageHeartrate > 40.0) {
                hrSum += r.averageHeartrate;
                s.hrRunCount++;
            }
            if (s.longestRun == null || r.distanceMeters > s.longestRun.distanceMeters) {
                s.longestRun = r;
            }
            if (r.distanceMeters >= 4800.0 && r.movingTimeSeconds > 0) {
                if (s.fastestRun == null || r.getPaceSeconds(false) < s.fastestRun.getPaceSeconds(false)) {
                    s.fastestRun = r;
                }
            }
            if (s.biggestClimbRun == null || r.elevationGainMeters > s.biggestClimbRun.elevationGainMeters) {
                s.biggestClimbRun = r;
            }
        }
        if (s.hrRunCount > 0) {
            s.avgHr = hrSum / s.hrRunCount;
        }
        return s;
    }

    public static List<RunActivity> filterByPeriod(List<RunActivity> allRuns, int periodIndex) {
        // 0 = This Year, 1 = Last 30 Days, 2 = All Time
        if (allRuns == null || allRuns.isEmpty() || periodIndex == 2) {
            return allRuns != null ? allRuns : Collections.emptyList();
        }
        LocalDate today = LocalDate.now();
        List<RunActivity> filtered = new ArrayList<>();
        for (RunActivity r : allRuns) {
            LocalDate d = r.getLocalDate();
            if (periodIndex == 0 && d.getYear() == today.getYear()) {
                filtered.add(r);
            } else if (periodIndex == 1 && !d.isBefore(today.minusDays(30))) {
                filtered.add(r);
            }
        }
        return filtered;
    }

    public static TrainingInsights computeInsights(List<RunActivity> allRuns) {
        TrainingInsights insights = new TrainingInsights();
        if (allRuns == null || allRuns.isEmpty()) {
            return insights;
        }

        LocalDate today = LocalDate.now();
        LocalDate d7 = today.minusDays(6);
        LocalDate d28 = today.minusDays(27);
        LocalDate d90 = today.minusDays(90);
        LocalDate d180 = today.minusDays(180);

        Set<LocalDate> activeWeeks = new HashSet<>();
        double effRecentSum = 0.0;
        int effRecentCount = 0;
        double effPriorSum = 0.0;
        int effPriorCount = 0;

        for (RunActivity r : allRuns) {
            LocalDate d = r.getLocalDate();
            LocalDate monday = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            activeWeeks.add(monday);

            if (!d.isBefore(d7) && !d.isAfter(today)) {
                insights.last7DaysMeters += r.distanceMeters;
            }
            if (!d.isBefore(d28) && !d.isAfter(today)) {
                insights.weeklyAvg28DaysMeters += r.distanceMeters;
            }

            double eff = r.getAerobicEfficiency();
            if (eff > 0.0) {
                if (!d.isBefore(d90)) {
                    effRecentSum += eff;
                    effRecentCount++;
                } else if (!d.isBefore(d180)) {
                    effPriorSum += eff;
                    effPriorCount++;
                }
            }
        }

        insights.weeklyAvg28DaysMeters /= 4.0;
        if (insights.weeklyAvg28DaysMeters > 500.0) {
            insights.acwr = insights.last7DaysMeters / insights.weeklyAvg28DaysMeters;
        }

        if (effRecentCount > 0) {
            insights.recentEfficiency = effRecentSum / effRecentCount;
        }
        if (effPriorCount > 0) {
            insights.priorEfficiency = effPriorSum / effPriorCount;
        }
        if (insights.recentEfficiency > 0.0 && insights.priorEfficiency > 0.0) {
            insights.efficiencyChangePct = ((insights.recentEfficiency - insights.priorEfficiency) / insights.priorEfficiency) * 100.0;
        }

        // Compute weekly streaks
        List<LocalDate> sortedWeeks = new ArrayList<>(activeWeeks);
        Collections.sort(sortedWeeks);

        int longest = 0;
        int runStreak = 0;
        LocalDate prev = null;
        for (LocalDate wk : sortedWeeks) {
            if (prev != null && prev.plusWeeks(1).equals(wk)) {
                runStreak++;
            } else {
                runStreak = 1;
            }
            if (runStreak > longest) {
                longest = runStreak;
            }
            prev = wk;
        }
        insights.longestWeekStreak = longest;

        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate cursor = activeWeeks.contains(thisMonday) ? thisMonday : thisMonday.minusWeeks(1);
        int current = 0;
        while (activeWeeks.contains(cursor)) {
            current++;
            cursor = cursor.minusWeeks(1);
        }
        insights.currentWeekStreak = current;

        return insights;
    }

    public static TreeMap<Integer, Double> computeYearlyDistances(List<RunActivity> allRuns, boolean useMiles) {
        TreeMap<Integer, Double> map = new TreeMap<>();
        if (allRuns == null) {
            return map;
        }
        for (RunActivity r : allRuns) {
            int y = r.getYear();
            double prev = map.containsKey(y) ? map.get(y) : 0.0;
            map.put(y, prev + r.getDistance(useMiles));
        }
        return map;
    }

    /**
     * Returns cumulative monthly distances (index 0..11 = Jan..Dec) for up to maxYears recent years.
     */
    public static LinkedHashMap<Integer, float[]> computeCumulativeByYear(List<RunActivity> allRuns, boolean useMiles, int maxYears) {
        LinkedHashMap<Integer, float[]> result = new LinkedHashMap<>();
        if (allRuns == null || allRuns.isEmpty()) {
            return result;
        }
        TreeMap<Integer, double[]> monthlyByYear = new TreeMap<>();
        for (RunActivity r : allRuns) {
            LocalDate d = r.getLocalDate();
            int year = d.getYear();
            int monthIdx = d.getMonthValue() - 1;
            double[] arr = monthlyByYear.get(year);
            if (arr == null) {
                arr = new double[12];
                monthlyByYear.put(year, arr);
            }
            arr[monthIdx] += r.getDistance(useMiles);
        }

        List<Integer> years = new ArrayList<>(monthlyByYear.keySet());
        int startIdx = Math.max(0, years.size() - maxYears);
        for (int i = startIdx; i < years.size(); i++) {
            int year = years.get(i);
            double[] monthly = monthlyByYear.get(year);
            float[] cumulative = new float[12];
            double running = 0.0;
            for (int m = 0; m < 12; m++) {
                running += monthly[m];
                cumulative[m] = (float) running;
            }
            result.put(year, cumulative);
        }
        return result;
    }

    public static List<MonthlyPoint> computeRecentMonths(List<RunActivity> allRuns, int monthCount) {
        YearMonth current = YearMonth.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yy", Locale.US);
        Map<YearMonth, MonthlyPoint> map = new LinkedHashMap<>();
        for (int i = monthCount - 1; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            map.put(ym, new MonthlyPoint(ym, ym.format(fmt)));
        }

        if (allRuns != null) {
            for (RunActivity r : allRuns) {
                YearMonth ym = r.getYearMonth();
                MonthlyPoint mp = map.get(ym);
                if (mp != null) {
                    mp.runCount++;
                    mp.distanceMeters += r.distanceMeters;
                    mp.movingSeconds += r.movingTimeSeconds;
                    if (r.averageHeartrate > 40.0 && r.movingTimeSeconds > 0) {
                        mp.hrWeightedSum += r.averageHeartrate * r.movingTimeSeconds;
                        mp.hrSeconds += r.movingTimeSeconds;
                    }
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    public static List<Integer> getAvailableYears(List<RunActivity> allRuns) {
        Set<Integer> set = new HashSet<>();
        if (allRuns != null) {
            for (RunActivity r : allRuns) {
                set.add(r.getYear());
            }
        }
        List<Integer> years = new ArrayList<>(set);
        years.sort(Comparator.reverseOrder());
        return years;
    }

    /**
     * Builds a compact, rich athlete context block for the Gemini AI Running Coach.
     */
    public static String buildAiCoachContext(List<RunActivity> allRuns, boolean useMiles) {
        String unit = useMiles ? "mi" : "km";
        StringBuilder sb = new StringBuilder();
        sb.append("You are an experienced, supportive, evidence-based running coach analyzing the user's real running history synced from Garmin.\n");
        sb.append("Give concise, practical, personalized advice grounded directly in their numbers below. Use ").append(unit).append(" and min/").append(unit).append(" for all distances and paces.\n");
        sb.append("Do not diagnose medical conditions; suggest seeing a professional if they mention sharp or persistent pain.\n\n");

        if (allRuns == null || allRuns.isEmpty()) {
            sb.append("ATHLETE DATA: No runs synced yet.\n");
            return sb.toString();
        }

        SummaryStats allTime = computeSummary(allRuns);
        TrainingInsights insights = computeInsights(allRuns);

        sb.append("=== ALL-TIME SUMMARY ===\n");
        sb.append(String.format(Locale.US, "Total Runs: %d | Distance: %s | Avg Pace: %s | Avg HR: %s | Total Elevation: %s\n",
                allTime.runCount,
                FormatUtils.formatDistance(allTime.totalDistanceMeters, useMiles),
                FormatUtils.formatPace(allTime.getAvgPaceSeconds(useMiles), useMiles),
                allTime.hrRunCount > 0 ? FormatUtils.formatHr(allTime.avgHr) : "N/A",
                FormatUtils.formatElevation(allTime.totalElevationMeters, useMiles)));
        if (allTime.longestRun != null) {
            sb.append(String.format(Locale.US, "Longest Run: %s on %s (%s)\n",
                    FormatUtils.formatDistance(allTime.longestRun.distanceMeters, useMiles),
                    allTime.longestRun.getLocalDate(),
                    allTime.longestRun.name));
        }
        if (allTime.fastestRun != null) {
            sb.append(String.format(Locale.US, "Fastest 5K+ Run: %s pace over %s on %s\n",
                    FormatUtils.formatPace(allTime.fastestRun.getPaceSeconds(useMiles), useMiles),
                    FormatUtils.formatDistance(allTime.fastestRun.distanceMeters, useMiles),
                    allTime.fastestRun.getLocalDate()));
        }

        sb.append("\n=== CURRENT TRAINING LOAD & FITNESS ===\n");
        sb.append(String.format(Locale.US, "Last 7 Days: %s | 28-Day Weekly Avg: %s | Acute:Chronic Ratio: %.2f\n",
                FormatUtils.formatDistance(insights.last7DaysMeters, useMiles),
                FormatUtils.formatDistance(insights.weeklyAvg28DaysMeters, useMiles),
                insights.acwr));
        sb.append(String.format(Locale.US, "Weekly Streak: %d weeks current (%d weeks longest)\n",
                insights.currentWeekStreak, insights.longestWeekStreak));
        if (insights.recentEfficiency > 0) {
            sb.append(String.format(Locale.US, "Aerobic Efficiency (last 90d): %.2f m/beat (change vs prior 90d: %+.1f%%)\n",
                    insights.recentEfficiency, insights.efficiencyChangePct));
        }

        sb.append("\n=== YEARLY BREAKDOWN ===\n");
        Map<Integer, List<RunActivity>> byYear = new TreeMap<>();
        for (RunActivity r : allRuns) {
            int y = r.getYear();
            if (!byYear.containsKey(y)) {
                byYear.put(y, new ArrayList<>());
            }
            byYear.get(y).add(r);
        }
        for (Map.Entry<Integer, List<RunActivity>> entry : byYear.entrySet()) {
            SummaryStats ys = computeSummary(entry.getValue());
            sb.append(String.format(Locale.US, "%d: %d runs, %s, avg pace %s, avg HR %s\n",
                    entry.getKey(),
                    ys.runCount,
                    FormatUtils.formatDistance(ys.totalDistanceMeters, useMiles),
                    FormatUtils.formatPace(ys.getAvgPaceSeconds(useMiles), useMiles),
                    ys.hrRunCount > 0 ? FormatUtils.formatHr(ys.avgHr) : "N/A"));
        }

        sb.append("\n=== LAST 8 WEEKS ===\n");
        LocalDate thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<LocalDate, List<RunActivity>> weekBuckets = new HashMap<>();
        for (RunActivity r : allRuns) {
            LocalDate wk = r.getLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (!wk.isBefore(thisMonday.minusWeeks(7))) {
                if (!weekBuckets.containsKey(wk)) {
                    weekBuckets.put(wk, new ArrayList<>());
                }
                weekBuckets.get(wk).add(r);
            }
        }
        for (int i = 7; i >= 0; i--) {
            LocalDate wk = thisMonday.minusWeeks(i);
            SummaryStats ws = computeSummary(weekBuckets.get(wk));
            sb.append(String.format(Locale.US, "Week of %s: %d runs, %s, avg pace %s\n",
                    wk,
                    ws.runCount,
                    FormatUtils.formatDistance(ws.totalDistanceMeters, useMiles),
                    ws.runCount > 0 ? FormatUtils.formatPace(ws.getAvgPaceSeconds(useMiles), useMiles) : "-"));
        }

        sb.append("\n=== MOST RECENT 15 RUNS ===\n");
        int limit = Math.min(15, allRuns.size());
        for (int i = 0; i < limit; i++) {
            RunActivity r = allRuns.get(i);
            sb.append(String.format(Locale.US, "%s | %s | %s | %s | HR: %s | Elev: %s\n",
                    r.getLocalDate(),
                    r.name,
                    FormatUtils.formatDistance(r.distanceMeters, useMiles),
                    FormatUtils.formatPace(r.getPaceSeconds(useMiles), useMiles),
                    r.averageHeartrate > 40 ? FormatUtils.formatHr(r.averageHeartrate) : "N/A",
                    FormatUtils.formatElevation(r.elevationGainMeters, useMiles)));
        }

        return sb.toString();
    }
}
