package com.runningstats.app.data;

import com.runningstats.app.model.RunActivity;
import com.runningstats.app.model.WellnessEntry;
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
        public double acwr;
        public double recentEfficiency; // last 90d meters/beat
        public double priorEfficiency;  // 90-180d meters/beat
        public double efficiencyChangePct;
    }

    public static class BestEffort {
        public final String label;
        public final long estimatedSeconds;
        public final double paceSecPerKm;
        public final RunActivity sourceRun;

        public BestEffort(String label, long estimatedSeconds, double paceSecPerKm, RunActivity sourceRun) {
            this.label = label;
            this.estimatedSeconds = estimatedSeconds;
            this.paceSecPerKm = paceSecPerKm;
            this.sourceRun = sourceRun;
        }
    }

    public static class HrZonePaceRow {
        public final String label;
        public int recentCount;
        public double recentPaceSecPerKm;
        public int olderCount;
        public double olderPaceSecPerKm;

        public HrZonePaceRow(String label) {
            this.label = label;
        }
    }

    public static class WellnessSummary {
        public double latestVo2max;
        public String latestVo2Date = "";
        public double latestRestingHr;
        public double avg30dRestingHr;
        public double latestHrv;
        public double avg30dHrv;
        public double latestSleepScore;
        public double avg30dSleepHours;
        public double latestWeight;
        public int entryCount;
    }

    public static class MonthlyPoint {
        public final YearMonth yearMonth;
        public final String label;
        public double distanceMeters;
        public long movingSeconds;
        public double hrWeightedSum;
        public long hrSeconds;
        public double effWeightedSum;
        public long effSeconds;
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
            if (hrSeconds <= 0) return 0.0;
            return hrWeightedSum / hrSeconds;
        }

        public double getAvgEfficiency() {
            if (effSeconds <= 0) return 0.0;
            return effWeightedSum / effSeconds;
        }
    }

    public static class MonthlyWellnessPoint {
        public final YearMonth yearMonth;
        public final String label;
        public double vo2Sum;
        public int vo2Count;
        public double rhrSum;
        public int rhrCount;
        public double hrvSum;
        public int hrvCount;

        public MonthlyWellnessPoint(YearMonth ym, String label) {
            this.yearMonth = ym;
            this.label = label;
        }

        public double getAvgVo2() {
            return vo2Count > 0 ? vo2Sum / vo2Count : 0.0;
        }

        public double getAvgRhr() {
            return rhrCount > 0 ? rhrSum / rhrCount : 0.0;
        }

        public double getAvgHrv() {
            return hrvCount > 0 ? hrvSum / hrvCount : 0.0;
        }
    }

    public static SummaryStats computeSummary(List<RunActivity> runs) {
        SummaryStats s = new SummaryStats();
        if (runs == null) return s;
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

    public static List<BestEffort> computePersonalBests(List<RunActivity> allRuns) {
        List<BestEffort> list = new ArrayList<>();
        if (allRuns == null || allRuns.isEmpty()) return list;

        addBestForDistance(list, allRuns, "5K", 4900.0, 5000.0);
        addBestForDistance(list, allRuns, "10K", 9800.0, 10000.0);
        addBestForDistance(list, allRuns, "Half Marathon", 20900.0, 21097.5);
        addBestForDistance(list, allRuns, "Marathon", 41800.0, 42195.0);
        return list;
    }

    private static void addBestForDistance(
            List<BestEffort> out,
            List<RunActivity> runs,
            String label,
            double minMeters,
            double exactTargetMeters
    ) {
        RunActivity best = null;
        double bestPaceSecPerKm = Double.MAX_VALUE;
        for (RunActivity r : runs) {
            if (r.distanceMeters >= minMeters && r.movingTimeSeconds > 60) {
                double pace = r.getPaceSeconds(false);
                if (pace > 150.0 && pace < bestPaceSecPerKm) {
                    bestPaceSecPerKm = pace;
                    best = r;
                }
            }
        }
        if (best != null) {
            long estSec = Math.round(bestPaceSecPerKm * (exactTargetMeters / 1000.0));
            out.add(new BestEffort(label, estSec, bestPaceSecPerKm, best));
        }
    }

    /**
     * Compares average pace in 4 heart-rate bands: Recent 90 days vs Prior history (older than 90 days).
     */
    public static List<HrZonePaceRow> computeHrZonePaceComparison(List<RunActivity> allRuns) {
        HrZonePaceRow[] rows = {
                new HrZonePaceRow("Easy (<135 bpm)"),
                new HrZonePaceRow("Steady (135–149 bpm)"),
                new HrZonePaceRow("Tempo (150–164 bpm)"),
                new HrZonePaceRow("Hard (165+ bpm)")
        };
        if (allRuns == null || allRuns.isEmpty()) {
            List<HrZonePaceRow> empty = new ArrayList<>();
            Collections.addAll(empty, rows);
            return empty;
        }

        LocalDate cutoff = LocalDate.now().minusDays(90);
        double[] recentDist = new double[4];
        long[] recentSec = new long[4];
        double[] olderDist = new double[4];
        long[] olderSec = new long[4];

        for (RunActivity r : allRuns) {
            if (r.averageHeartrate <= 40.0 || r.distanceMeters < 1500.0 || r.movingTimeSeconds <= 60) {
                continue;
            }
            int band;
            if (r.averageHeartrate < 135.0) {
                band = 0;
            } else if (r.averageHeartrate < 150.0) {
                band = 1;
            } else if (r.averageHeartrate < 165.0) {
                band = 2;
            } else {
                band = 3;
            }

            if (!r.getLocalDate().isBefore(cutoff)) {
                rows[band].recentCount++;
                recentDist[band] += r.distanceMeters;
                recentSec[band] += r.movingTimeSeconds;
            } else {
                rows[band].olderCount++;
                olderDist[band] += r.distanceMeters;
                olderSec[band] += r.movingTimeSeconds;
            }
        }

        List<HrZonePaceRow> result = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (recentDist[i] > 100.0) {
                rows[i].recentPaceSecPerKm = recentSec[i] / (recentDist[i] / 1000.0);
            }
            if (olderDist[i] > 100.0) {
                rows[i].olderPaceSecPerKm = olderSec[i] / (olderDist[i] / 1000.0);
            }
            if (rows[i].recentCount > 0 || rows[i].olderCount > 0) {
                result.add(rows[i]);
            }
        }
        return result;
    }

    public static WellnessSummary computeWellnessSummary(List<WellnessEntry> entries) {
        WellnessSummary s = new WellnessSummary();
        if (entries == null || entries.isEmpty()) return s;

        LocalDate cutoff30 = LocalDate.now().minusDays(30);
        double rhrSum = 0.0;
        int rhrCount = 0;
        double hrvSum = 0.0;
        int hrvCount = 0;
        double sleepHrsSum = 0.0;
        int sleepCount = 0;

        // entries are ordered date DESC
        for (WellnessEntry w : entries) {
            s.entryCount++;
            if (s.latestVo2max <= 0.0 && w.vo2max > 0.0) {
                s.latestVo2max = w.vo2max;
                s.latestVo2Date = w.date;
            }
            if (s.latestRestingHr <= 0.0 && w.restingHr > 25.0) {
                s.latestRestingHr = w.restingHr;
            }
            if (s.latestHrv <= 0.0 && w.hrv > 0.0) {
                s.latestHrv = w.hrv;
            }
            if (s.latestSleepScore <= 0.0 && w.sleepScore > 0.0) {
                s.latestSleepScore = w.sleepScore;
            }
            if (s.latestWeight <= 0.0 && w.weight > 20.0) {
                s.latestWeight = w.weight;
            }

            if (!w.getLocalDate().isBefore(cutoff30)) {
                if (w.restingHr > 25.0) {
                    rhrSum += w.restingHr;
                    rhrCount++;
                }
                if (w.hrv > 0.0) {
                    hrvSum += w.hrv;
                    hrvCount++;
                }
                if (w.sleepSecs > 3600) {
                    sleepHrsSum += w.getSleepHours();
                    sleepCount++;
                }
            }
        }

        if (rhrCount > 0) s.avg30dRestingHr = rhrSum / rhrCount;
        if (hrvCount > 0) s.avg30dHrv = hrvSum / hrvCount;
        if (sleepCount > 0) s.avg30dSleepHours = sleepHrsSum / sleepCount;
        return s;
    }

    public static List<RunActivity> filterByPeriod(List<RunActivity> allRuns, int periodIndex) {
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
        if (allRuns == null || allRuns.isEmpty()) return insights;

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

        if (effRecentCount > 0) insights.recentEfficiency = effRecentSum / effRecentCount;
        if (effPriorCount > 0) insights.priorEfficiency = effPriorSum / effPriorCount;
        if (insights.recentEfficiency > 0.0 && insights.priorEfficiency > 0.0) {
            insights.efficiencyChangePct = ((insights.recentEfficiency - insights.priorEfficiency) / insights.priorEfficiency) * 100.0;
        }

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
            if (runStreak > longest) longest = runStreak;
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
        if (allRuns == null) return map;
        for (RunActivity r : allRuns) {
            int y = r.getYear();
            double prev = map.containsKey(y) ? map.get(y) : 0.0;
            map.put(y, prev + r.getDistance(useMiles));
        }
        return map;
    }

    public static LinkedHashMap<Integer, float[]> computeCumulativeByYear(List<RunActivity> allRuns, boolean useMiles, int maxYears) {
        LinkedHashMap<Integer, float[]> result = new LinkedHashMap<>();
        if (allRuns == null || allRuns.isEmpty()) return result;

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

    /**
     * Computes monthly run stats (distance, pace, HR, aerobic efficiency).
     * Pass maxMonths = 12, 24, or 0 for All Years (up to 72 months).
     */
    public static List<MonthlyPoint> computeMonthlySeries(List<RunActivity> allRuns, int maxMonths) {
        YearMonth current = YearMonth.now();
        YearMonth startYm;
        if (maxMonths > 0) {
            startYm = current.minusMonths(maxMonths - 1);
        } else {
            startYm = current.minusMonths(11);
            if (allRuns != null && !allRuns.isEmpty()) {
                for (RunActivity r : allRuns) {
                    YearMonth ym = r.getYearMonth();
                    if (ym.isBefore(startYm)) {
                        startYm = ym;
                    }
                }
            }
            // Cap at 72 months so chart labels remain readable
            YearMonth cap = current.minusMonths(71);
            if (startYm.isBefore(cap)) {
                startYm = cap;
            }
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yy", Locale.US);
        Map<YearMonth, MonthlyPoint> map = new LinkedHashMap<>();
        YearMonth cursor = startYm;
        while (!cursor.isAfter(current)) {
            map.put(cursor, new MonthlyPoint(cursor, cursor.format(fmt)));
            cursor = cursor.plusMonths(1);
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
                    double eff = r.getAerobicEfficiency();
                    if (eff > 0.0 && r.movingTimeSeconds > 0) {
                        mp.effWeightedSum += eff * r.movingTimeSeconds;
                        mp.effSeconds += r.movingTimeSeconds;
                    }
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    public static List<MonthlyWellnessPoint> computeMonthlyWellness(List<WellnessEntry> entries, int maxMonths) {
        YearMonth current = YearMonth.now();
        YearMonth startYm;
        if (maxMonths > 0) {
            startYm = current.minusMonths(maxMonths - 1);
        } else {
            startYm = current.minusMonths(11);
            if (entries != null && !entries.isEmpty()) {
                for (WellnessEntry w : entries) {
                    YearMonth ym = w.getYearMonth();
                    if (ym.isBefore(startYm)) startYm = ym;
                }
            }
            YearMonth cap = current.minusMonths(71);
            if (startYm.isBefore(cap)) startYm = cap;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yy", Locale.US);
        Map<YearMonth, MonthlyWellnessPoint> map = new LinkedHashMap<>();
        YearMonth cursor = startYm;
        while (!cursor.isAfter(current)) {
            map.put(cursor, new MonthlyWellnessPoint(cursor, cursor.format(fmt)));
            cursor = cursor.plusMonths(1);
        }

        if (entries != null) {
            for (WellnessEntry w : entries) {
                MonthlyWellnessPoint mp = map.get(w.getYearMonth());
                if (mp != null) {
                    if (w.vo2max > 0.0) {
                        mp.vo2Sum += w.vo2max;
                        mp.vo2Count++;
                    }
                    if (w.restingHr > 25.0) {
                        mp.rhrSum += w.restingHr;
                        mp.rhrCount++;
                    }
                    if (w.hrv > 0.0) {
                        mp.hrvSum += w.hrv;
                        mp.hrvCount++;
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

    public static String buildAiCoachContext(List<RunActivity> allRuns, List<WellnessEntry> wellness, boolean useMiles) {
        String unit = useMiles ? "mi" : "km";
        StringBuilder sb = new StringBuilder();
        sb.append("You are an experienced, supportive, evidence-based running coach analyzing the user's real Garmin running history & health metrics.\n");
        sb.append("Give concise, practical, personalized advice grounded directly in their numbers below. Use ").append(unit).append(" and min/").append(unit).append(".\n");
        sb.append("Do not diagnose medical conditions; suggest seeing a professional if they mention sharp or persistent pain.\n\n");

        if (allRuns == null || allRuns.isEmpty()) {
            sb.append("ATHLETE DATA: No runs synced yet.\n");
            return sb.toString();
        }

        SummaryStats allTime = computeSummary(allRuns);
        TrainingInsights insights = computeInsights(allRuns);

        sb.append("=== ALL-TIME SUMMARY ===\n");
        sb.append(String.format(Locale.US, "Total Runs: %d | Distance: %s | Avg Pace: %s | Avg HR: %s | Elevation: %s\n",
                allTime.runCount,
                FormatUtils.formatDistance(allTime.totalDistanceMeters, useMiles),
                FormatUtils.formatPace(allTime.getAvgPaceSeconds(useMiles), useMiles),
                allTime.hrRunCount > 0 ? FormatUtils.formatHr(allTime.avgHr) : "N/A",
                FormatUtils.formatElevation(allTime.totalElevationMeters, useMiles)));

        List<BestEffort> prs = computePersonalBests(allRuns);
        if (!prs.isEmpty()) {
            sb.append("\n=== ESTIMATED PERSONAL BESTS ===\n");
            for (BestEffort pr : prs) {
                double paceUnit = useMiles ? pr.paceSecPerKm * 1.609344 : pr.paceSecPerKm;
                sb.append(String.format(Locale.US, "%s: %s (%s) on %s\n",
                        pr.label,
                        FormatUtils.formatDuration(pr.estimatedSeconds),
                        FormatUtils.formatPace(paceUnit, useMiles),
                        pr.sourceRun.getLocalDate()));
            }
        }

        sb.append("\n=== CURRENT TRAINING LOAD & HR-VS-PACE EFFICIENCY ===\n");
        sb.append(String.format(Locale.US, "Last 7 Days: %s | 28-Day Weekly Avg: %s | ACWR: %.2f\n",
                FormatUtils.formatDistance(insights.last7DaysMeters, useMiles),
                FormatUtils.formatDistance(insights.weeklyAvg28DaysMeters, useMiles),
                insights.acwr));
        sb.append(String.format(Locale.US, "Weekly Streak: %d weeks current (%d longest)\n",
                insights.currentWeekStreak, insights.longestWeekStreak));
        if (insights.recentEfficiency > 0) {
            sb.append(String.format(Locale.US, "Aerobic Efficiency (90d): %.2f m/beat (change vs prior 90d: %+.1f%%)\n",
                    insights.recentEfficiency, insights.efficiencyChangePct));
        }

        List<HrZonePaceRow> hrRows = computeHrZonePaceComparison(allRuns);
        if (!hrRows.isEmpty()) {
            sb.append("\n=== PACE BY HEART RATE ZONE (RECENT 90D VS PRIOR) ===\n");
            for (HrZonePaceRow row : hrRows) {
                double recPace = useMiles ? row.recentPaceSecPerKm * 1.609344 : row.recentPaceSecPerKm;
                double oldPace = useMiles ? row.olderPaceSecPerKm * 1.609344 : row.olderPaceSecPerKm;
                sb.append(String.format(Locale.US, "%s: Recent 90d %s (%d runs) vs Prior %s (%d runs)\n",
                        row.label,
                        row.recentCount > 0 ? FormatUtils.formatPace(recPace, useMiles) : "N/A",
                        row.recentCount,
                        row.olderCount > 0 ? FormatUtils.formatPace(oldPace, useMiles) : "N/A",
                        row.olderCount));
            }
        }

        WellnessSummary ws = computeWellnessSummary(wellness);
        if (ws.entryCount > 0) {
            sb.append("\n=== GARMIN HEALTH & WELLNESS ===\n");
            if (ws.latestVo2max > 0) {
                sb.append(String.format(Locale.US, "VO2 Max: %.1f (as of %s)\n", ws.latestVo2max, ws.latestVo2Date));
            }
            if (ws.latestRestingHr > 0) {
                sb.append(String.format(Locale.US, "Resting HR: %.0f bpm (30d avg: %.0f bpm)\n",
                        ws.latestRestingHr, ws.avg30dRestingHr > 0 ? ws.avg30dRestingHr : ws.latestRestingHr));
            }
            if (ws.latestHrv > 0) {
                sb.append(String.format(Locale.US, "HRV: %.0f ms (30d avg: %.0f ms)\n",
                        ws.latestHrv, ws.avg30dHrv > 0 ? ws.avg30dHrv : ws.latestHrv));
            }
            if (ws.latestSleepScore > 0 || ws.avg30dSleepHours > 0) {
                sb.append(String.format(Locale.US, "Sleep: latest score %.0f | 30d avg %.1f hrs/night\n",
                        ws.latestSleepScore, ws.avg30dSleepHours));
            }
        }

        sb.append("\n=== YEARLY BREAKDOWN ===\n");
        Map<Integer, List<RunActivity>> byYear = new TreeMap<>();
        for (RunActivity r : allRuns) {
            int y = r.getYear();
            if (!byYear.containsKey(y)) byYear.put(y, new ArrayList<>());
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

        sb.append("\n=== MOST RECENT 15 RUNS ===\n");
        int limit = Math.min(15, allRuns.size());
        for (int i = 0; i < limit; i++) {
            RunActivity r = allRuns.get(i);
            sb.append(String.format(Locale.US, "%s | %s | %s | %s | HR: %s | Eff: %.2f m/beat\n",
                    r.getLocalDate(),
                    r.name,
                    FormatUtils.formatDistance(r.distanceMeters, useMiles),
                    FormatUtils.formatPace(r.getPaceSeconds(useMiles), useMiles),
                    r.averageHeartrate > 40 ? FormatUtils.formatHr(r.averageHeartrate) : "N/A",
                    r.getAerobicEfficiency()));
        }

        return sb.toString();
    }
}
