package com.runningstats.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.runningstats.app.MainActivity;
import com.runningstats.app.R;
import com.runningstats.app.data.PrefsManager;
import com.runningstats.app.data.StatsCalculator;
import com.runningstats.app.model.RunActivity;
import com.runningstats.app.model.WellnessEntry;
import com.runningstats.app.util.FormatUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class DashboardFragment extends Fragment implements RefreshableFragment {

    private SwipeRefreshLayout swipeRefresh;
    private TextView tvWeekRuns, tvWeekTime, tvWeekDistance, tvWeekCompare;
    private TextView tvMonthRuns, tvMonthTime, tvMonthDistance, tvMonthCompare;
    private TextView tvYearRuns, tvYearTime, tvYearDistance, tvYearCompare;
    private TextView tvAllRuns, tvAllTime, tvAllDistance, tvAllCompare;
    private TextView tvSyncStatus;
    private TextView tvSyncSubtitle;
    private MaterialButton btnSyncNow;
    private MaterialButton btnLoadDemo;
    private ChipGroup chipGroupPeriod;

    private TextView tvStatDistance;
    private TextView tvStatRuns;
    private TextView tvStatPace;
    private TextView tvStatHrElev;
    private TextView tvHealthPeriodTitle;
    private TextView tvHealthVo2;
    private TextView tvHealthRhr;
    private TextView tvHealthHrv;
    private TextView tvHealthSleep;
    private TextView tvPersonalBests;
    private TextView tvLoadSummary;
    private TextView tvHighlights;

    private int selectedPeriod = 2; // 0 = Week, 1 = Month, 2 = Year, 3 = All Time

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        tvWeekRuns = view.findViewById(R.id.tvWeekRuns);
        tvWeekTime = view.findViewById(R.id.tvWeekTime);
        tvWeekDistance = view.findViewById(R.id.tvWeekDistance);
        tvWeekCompare = view.findViewById(R.id.tvWeekCompare);
        tvMonthRuns = view.findViewById(R.id.tvMonthRuns);
        tvMonthTime = view.findViewById(R.id.tvMonthTime);
        tvMonthDistance = view.findViewById(R.id.tvMonthDistance);
        tvMonthCompare = view.findViewById(R.id.tvMonthCompare);
        tvYearRuns = view.findViewById(R.id.tvYearRuns);
        tvYearTime = view.findViewById(R.id.tvYearTime);
        tvYearDistance = view.findViewById(R.id.tvYearDistance);
        tvYearCompare = view.findViewById(R.id.tvYearCompare);
        tvAllRuns = view.findViewById(R.id.tvAllRuns);
        tvAllTime = view.findViewById(R.id.tvAllTime);
        tvAllDistance = view.findViewById(R.id.tvAllDistance);
        tvAllCompare = view.findViewById(R.id.tvAllCompare);
        tvSyncStatus = view.findViewById(R.id.tvSyncStatus);
        tvSyncSubtitle = view.findViewById(R.id.tvSyncSubtitle);
        btnSyncNow = view.findViewById(R.id.btnSyncNow);
        btnLoadDemo = view.findViewById(R.id.btnLoadDemo);
        chipGroupPeriod = view.findViewById(R.id.chipGroupPeriod);

        tvStatDistance = view.findViewById(R.id.tvStatDistance);
        tvStatRuns = view.findViewById(R.id.tvStatRuns);
        tvStatPace = view.findViewById(R.id.tvStatPace);
        tvStatHrElev = view.findViewById(R.id.tvStatHrElev);
        tvHealthPeriodTitle = view.findViewById(R.id.tvHealthPeriodTitle);
        tvHealthVo2 = view.findViewById(R.id.tvHealthVo2);
        tvHealthRhr = view.findViewById(R.id.tvHealthRhr);
        tvHealthHrv = view.findViewById(R.id.tvHealthHrv);
        tvHealthSleep = view.findViewById(R.id.tvHealthSleep);
        tvPersonalBests = view.findViewById(R.id.tvPersonalBests);
        tvLoadSummary = view.findViewById(R.id.tvLoadSummary);
        tvHighlights = view.findViewById(R.id.tvHighlights);

        swipeRefresh.setOnRefreshListener(this::triggerSync);

        btnSyncNow.setOnClickListener(v -> {
            MainActivity host = getHostActivity();
            if (host == null) return;
            if (!host.getPrefs().isIntervalsConnected()) {
                host.navigateToTab(R.id.nav_settings);
            } else {
                triggerSync();
            }
        });

        btnLoadDemo.setOnClickListener(v -> {
            MainActivity host = getHostActivity();
            if (host == null) return;
            int count = host.getDb().seedSampleRuns();
            Toast.makeText(requireContext(), "Loaded " + count + " sample runs & 3 years of health data!", Toast.LENGTH_SHORT).show();
            refreshData();
        });

        chipGroupPeriod.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipThisWeek) {
                selectedPeriod = 0;
            } else if (id == R.id.chipThisMonth) {
                selectedPeriod = 1;
            } else if (id == R.id.chipThisYear) {
                selectedPeriod = 2;
            } else if (id == R.id.chipAllTime) {
                selectedPeriod = 3;
            }
            refreshData();
        });

        refreshData();
    }

    private void triggerSync() {
        MainActivity host = getHostActivity();
        if (host == null) {
            swipeRefresh.setRefreshing(false);
            return;
        }
        if (!host.getPrefs().isIntervalsConnected()) {
            swipeRefresh.setRefreshing(false);
            Toast.makeText(requireContext(), "Add your free Intervals.icu key in You → Settings.", Toast.LENGTH_SHORT).show();
            host.navigateToTab(R.id.nav_settings);
            return;
        }
        swipeRefresh.setRefreshing(true);
        host.syncIntervals(false, new MainActivity.SyncCallback() {
            @Override
            public void onProgress(String message) {
                if (isAdded()) {
                    tvSyncSubtitle.setText(message);
                }
            }

            @Override
            public void onComplete(boolean success, String message) {
                if (!isAdded()) return;
                swipeRefresh.setRefreshing(false);
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                refreshData();
            }
        });
    }

    @Override
    public void refreshData() {
        MainActivity host = getHostActivity();
        if (host == null || !isAdded()) return;

        PrefsManager prefs = host.getPrefs();
        boolean useMiles = prefs.useMiles();
        List<RunActivity> allRuns = host.getDb().getAllRuns();
        List<WellnessEntry> allWellness = host.getDb().getAllWellness();

        // Multi-Period Snapshots + Comparisons
        StatsCalculator.SummaryStats wkSum = StatsCalculator.computeSummary(StatsCalculator.filterByPeriod(allRuns, 0));
        StatsCalculator.SummaryStats prevWkSum = StatsCalculator.computeSummary(StatsCalculator.filterByPreviousPeriod(allRuns, 0));
        StatsCalculator.SummaryStats moSum = StatsCalculator.computeSummary(StatsCalculator.filterByPeriod(allRuns, 1));
        StatsCalculator.SummaryStats prevMoSum = StatsCalculator.computeSummary(StatsCalculator.filterByPreviousPeriod(allRuns, 1));
        StatsCalculator.SummaryStats yrSum = StatsCalculator.computeSummary(StatsCalculator.filterByPeriod(allRuns, 2));
        StatsCalculator.SummaryStats prevYrSum = StatsCalculator.computeSummary(StatsCalculator.filterByPreviousPeriod(allRuns, 2));
        StatsCalculator.SummaryStats allSum = StatsCalculator.computeSummary(allRuns);

        String prevMonthName = YearMonth.now().minusMonths(1).format(DateTimeFormatter.ofPattern("MMM", Locale.US));
        String prevYearName = String.valueOf(LocalDate.now().getYear() - 1);

        tvWeekRuns.setText(String.valueOf(wkSum.runCount));
        tvWeekTime.setText(FormatUtils.formatDuration(wkSum.totalMovingSeconds));
        tvWeekDistance.setText(FormatUtils.formatDistance(wkSum.totalDistanceMeters, useMiles));
        tvWeekCompare.setText(formatCompareLine("Last Week", wkSum, prevWkSum, useMiles));

        tvMonthRuns.setText(String.valueOf(moSum.runCount));
        tvMonthTime.setText(FormatUtils.formatDuration(moSum.totalMovingSeconds));
        tvMonthDistance.setText(FormatUtils.formatDistance(moSum.totalDistanceMeters, useMiles));
        tvMonthCompare.setText(formatCompareLine(prevMonthName, moSum, prevMoSum, useMiles));

        tvYearRuns.setText(String.valueOf(yrSum.runCount));
        tvYearTime.setText(FormatUtils.formatDuration(yrSum.totalMovingSeconds));
        tvYearDistance.setText(FormatUtils.formatDistance(yrSum.totalDistanceMeters, useMiles));
        tvYearCompare.setText(formatCompareLine(prevYearName, yrSum, prevYrSum, useMiles));

        tvAllRuns.setText(String.valueOf(allSum.runCount));
        tvAllTime.setText(FormatUtils.formatDuration(allSum.totalMovingSeconds));
        tvAllDistance.setText(FormatUtils.formatDistance(allSum.totalDistanceMeters, useMiles));
        int yrCount = StatsCalculator.getAvailableYears(allRuns).size();
        if (yrCount > 0) {
            tvAllCompare.setText(String.format(
                    Locale.US,
                    "Across %d %s • Avg %s/yr",
                    yrCount,
                    yrCount == 1 ? "year" : "years",
                    FormatUtils.formatDistance(allSum.totalDistanceMeters / yrCount, useMiles)
            ));
        } else {
            tvAllCompare.setText("");
        }

        if (prefs.isIntervalsConnected()) {
            String athlete = prefs.getAthleteName();
            String label = athlete.isEmpty() ? "Garmin Sync Ready" : athlete;
            tvSyncStatus.setText(String.format(
                    Locale.US,
                    "%s • %d runs • Synced %s",
                    label,
                    allRuns.size(),
                    FormatUtils.formatTimestamp(prefs.getLastSyncMs())
            ));
            tvSyncSubtitle.setText("Auto-syncs on open or pull down to refresh.");
            btnSyncNow.setText("Sync");
        } else {
            tvSyncStatus.setText(allRuns.isEmpty()
                    ? "Not connected yet"
                    : allRuns.size() + " runs stored locally");
            tvSyncSubtitle.setText("Connect free Garmin sync via Intervals.icu in You → Settings.");
            btnSyncNow.setText("Connect");
        }

        // Selected Period Detailed Breakdown
        String periodName = selectedPeriod == 0 ? "This Week"
                : (selectedPeriod == 1 ? "This Month"
                : (selectedPeriod == 2 ? "This Year" : "All Time"));

        List<RunActivity> periodRuns = StatsCalculator.filterByPeriod(allRuns, selectedPeriod);
        StatsCalculator.SummaryStats summary = StatsCalculator.computeSummary(periodRuns);

        tvStatDistance.setText(FormatUtils.formatDistance(summary.totalDistanceMeters, useMiles));
        tvStatRuns.setText(String.valueOf(summary.runCount));
        tvStatPace.setText(FormatUtils.formatPace(summary.getAvgPaceSeconds(useMiles), useMiles));

        String hrPart = summary.hrRunCount > 0 ? FormatUtils.formatHr(summary.avgHr) : "-- bpm";
        String elevPart = "+" + FormatUtils.formatElevation(summary.totalElevationMeters, useMiles);
        tvStatHrElev.setText(hrPart + "\n" + elevPart);

        // Garmin Health & Readiness — tied to the selected Detailed Breakdown period!
        tvHealthPeriodTitle.setText("Garmin Health & Readiness (" + periodName + ")");
        List<WellnessEntry> periodWellness = StatsCalculator.filterWellnessByPeriod(allWellness, selectedPeriod);
        StatsCalculator.WellnessSummary ws = StatsCalculator.computeWellnessSummary(
                periodWellness, periodRuns, allRuns, allWellness
        );

        if (ws.avgVo2max > 0) {
            tvHealthVo2.setText(String.format(Locale.US, "%.1f%s", ws.avgVo2max, ws.vo2IsEstimated ? " (est)" : ""));
        } else {
            tvHealthVo2.setText("--");
        }
        if (ws.avgRestingHr > 0) {
            tvHealthRhr.setText(String.format(Locale.US, "%.0f bpm", ws.avgRestingHr));
        } else {
            tvHealthRhr.setText("--");
        }
        if (ws.avgHrv > 0) {
            tvHealthHrv.setText(String.format(Locale.US, "%.0f ms", ws.avgHrv));
        } else {
            tvHealthHrv.setText("--");
        }
        if (ws.avgSleepScore > 0 || ws.avgSleepHours > 0) {
            StringBuilder sleepSb = new StringBuilder();
            if (ws.avgSleepHours > 0) {
                sleepSb.append(String.format(Locale.US, "%.1fh", ws.avgSleepHours));
            }
            if (ws.avgSleepScore > 0) {
                if (sleepSb.length() > 0) sleepSb.append(" • ");
                sleepSb.append(String.format(Locale.US, "Score %.0f", ws.avgSleepScore));
            }
            tvHealthSleep.setText(sleepSb.toString());
        } else {
            tvHealthSleep.setText("--");
        }

        // Personal Bests
        List<StatsCalculator.BestEffort> prs = StatsCalculator.computePersonalBests(allRuns);
        if (prs.isEmpty()) {
            tvPersonalBests.setText("Sync runs of 5K or longer to see your all-time Personal Bests.");
        } else {
            StringBuilder prSb = new StringBuilder();
            for (int i = 0; i < prs.size(); i++) {
                StatsCalculator.BestEffort pr = prs.get(i);
                double paceUnit = useMiles ? pr.paceSecPerKm * 1.609344 : pr.paceSecPerKm;
                if (i > 0) prSb.append("\n");
                prSb.append(String.format(Locale.US, "• %s: %s (%s) — %s",
                        pr.label,
                        FormatUtils.formatDuration(pr.estimatedSeconds),
                        FormatUtils.formatPace(paceUnit, useMiles),
                        FormatUtils.formatDate(pr.sourceRun.getLocalDate())));
            }
            tvPersonalBests.setText(prSb.toString());
        }

        // Training Load & Insights
        if (allRuns.isEmpty()) {
            tvLoadSummary.setText("Sync runs or tap Load Sample to see your volume ramp, streaks, and aerobic efficiency.");
        } else {
            StatsCalculator.TrainingInsights ins = StatsCalculator.computeInsights(allRuns);
            String rampLabel;
            if (ins.acwr <= 0.01) {
                rampLabel = "Building baseline";
            } else if (ins.acwr < 0.80) {
                rampLabel = "Recovery / light week";
            } else if (ins.acwr <= 1.30) {
                rampLabel = "Optimal build zone";
            } else if (ins.acwr <= 1.50) {
                rampLabel = "High ramp — watch fatigue";
            } else {
                rampLabel = "Very sharp spike — consider extra rest";
            }

            StringBuilder loadSb = new StringBuilder();
            loadSb.append(String.format(Locale.US, "• Last 7 days: %s (28d weekly avg: %s)\n",
                    FormatUtils.formatDistance(ins.last7DaysMeters, useMiles),
                    FormatUtils.formatDistance(ins.weeklyAvg28DaysMeters, useMiles)));
            loadSb.append(String.format(Locale.US, "• Load ratio (ACWR): %.2f — %s\n", ins.acwr, rampLabel));
            loadSb.append(String.format(Locale.US, "• Weekly streak: %d weeks current (%d longest)",
                    ins.currentWeekStreak, ins.longestWeekStreak));
            if (ins.recentEfficiency > 0.0) {
                loadSb.append(String.format(Locale.US, "\n• Aerobic efficiency (90d): %.2f m/beat", ins.recentEfficiency));
                if (ins.priorEfficiency > 0.0) {
                    loadSb.append(String.format(Locale.US, " (%+.1f%% vs prior 90d)", ins.efficiencyChangePct));
                }
            }
            tvLoadSummary.setText(loadSb.toString());
        }

        // Period Highlights
        if (periodRuns.isEmpty()) {
            tvHighlights.setText("No runs recorded in " + periodName.toLowerCase(Locale.US) + " yet.");
        } else {
            StringBuilder hiSb = new StringBuilder();
            hiSb.append("• Total moving time: ").append(FormatUtils.formatDuration(summary.totalMovingSeconds));
            if (summary.longestRun != null) {
                hiSb.append(String.format(Locale.US, "\n• Longest run: %s (%s)",
                        FormatUtils.formatDistance(summary.longestRun.distanceMeters, useMiles),
                        FormatUtils.formatDate(summary.longestRun.getLocalDate())));
            }
            if (summary.fastestRun != null) {
                hiSb.append(String.format(Locale.US, "\n• Fastest 5K+ run: %s over %s (%s)",
                        FormatUtils.formatPace(summary.fastestRun.getPaceSeconds(useMiles), useMiles),
                        FormatUtils.formatDistance(summary.fastestRun.distanceMeters, useMiles),
                        FormatUtils.formatDate(summary.fastestRun.getLocalDate())));
            }
            if (summary.biggestClimbRun != null && summary.biggestClimbRun.elevationGainMeters > 0) {
                hiSb.append(String.format(Locale.US, "\n• Biggest climb: +%s (%s)",
                        FormatUtils.formatElevation(summary.biggestClimbRun.elevationGainMeters, useMiles),
                        summary.biggestClimbRun.name));
            }
            tvHighlights.setText(hiSb.toString());
        }
    }

    private String formatCompareLine(String prevLabel, StatsCalculator.SummaryStats curr, StatsCalculator.SummaryStats prev, boolean useMiles) {
        if (prev.runCount == 0 && prev.totalDistanceMeters <= 0.0) {
            return "vs " + prevLabel + ": no runs recorded";
        }
        double diffMeters = curr.totalDistanceMeters - prev.totalDistanceMeters;
        String sign = diffMeters >= 0 ? "+" : "-";
        String diffDist = sign + FormatUtils.formatDistance(Math.abs(diffMeters), useMiles);
        String pctStr = "";
        if (prev.totalDistanceMeters > 100.0) {
            double pct = (diffMeters / prev.totalDistanceMeters) * 100.0;
            pctStr = String.format(Locale.US, " (%+.0f%%)", pct);
        }
        return String.format(
                Locale.US,
                "vs %s: %s (%d runs) • %s%s",
                prevLabel,
                FormatUtils.formatDistance(prev.totalDistanceMeters, useMiles),
                prev.runCount,
                diffDist,
                pctStr
        );
    }

    private MainActivity getHostActivity() {
        return (getActivity() instanceof MainActivity) ? (MainActivity) getActivity() : null;
    }
}
