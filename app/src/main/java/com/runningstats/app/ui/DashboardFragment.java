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
import java.util.List;
import java.util.Locale;

public class DashboardFragment extends Fragment implements RefreshableFragment {

    private SwipeRefreshLayout swipeRefresh;
    private TextView tvWeekRuns;
    private TextView tvWeekTime;
    private TextView tvWeekDistance;
    private TextView tvSyncStatus;
    private TextView tvSyncSubtitle;
    private MaterialButton btnSyncNow;
    private MaterialButton btnLoadDemo;
    private ChipGroup chipGroupPeriod;

    private TextView tvStatDistance;
    private TextView tvStatRuns;
    private TextView tvStatPace;
    private TextView tvStatHrElev;
    private TextView tvHealthVo2;
    private TextView tvHealthRhr;
    private TextView tvHealthHrv;
    private TextView tvHealthSleep;
    private TextView tvPersonalBests;
    private TextView tvLoadSummary;
    private TextView tvHighlights;

    private int selectedPeriod = 0; // 0 = This Year, 1 = Last 30 Days, 2 = All Time

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
        tvSyncStatus = view.findViewById(R.id.tvSyncStatus);
        tvSyncSubtitle = view.findViewById(R.id.tvSyncSubtitle);
        btnSyncNow = view.findViewById(R.id.btnSyncNow);
        btnLoadDemo = view.findViewById(R.id.btnLoadDemo);
        chipGroupPeriod = view.findViewById(R.id.chipGroupPeriod);

        tvStatDistance = view.findViewById(R.id.tvStatDistance);
        tvStatRuns = view.findViewById(R.id.tvStatRuns);
        tvStatPace = view.findViewById(R.id.tvStatPace);
        tvStatHrElev = view.findViewById(R.id.tvStatHrElev);
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
            if (id == R.id.chipThisYear) {
                selectedPeriod = 0;
            } else if (id == R.id.chip30Days) {
                selectedPeriod = 1;
            } else if (id == R.id.chipAllTime) {
                selectedPeriod = 2;
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

        // Weekly Snapshot (last 7 days)
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(6);
        int wkRuns = 0;
        long wkSeconds = 0L;
        double wkMeters = 0.0;
        for (RunActivity r : allRuns) {
            LocalDate d = r.getLocalDate();
            if (!d.isBefore(sevenDaysAgo) && !d.isAfter(today)) {
                wkRuns++;
                wkSeconds += r.movingTimeSeconds;
                wkMeters += r.distanceMeters;
            }
        }
        tvWeekRuns.setText(String.valueOf(wkRuns));
        tvWeekTime.setText(FormatUtils.formatDuration(wkSeconds));
        tvWeekDistance.setText(FormatUtils.formatDistance(wkMeters, useMiles));

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
            tvSyncSubtitle.setText("Pull down anytime to fetch your newest Garmin runs & health.");
            btnSyncNow.setText("Sync");
        } else {
            tvSyncStatus.setText(allRuns.isEmpty()
                    ? "Not connected yet"
                    : allRuns.size() + " runs stored locally");
            tvSyncSubtitle.setText("Connect free Garmin sync via Intervals.icu in You → Settings.");
            btnSyncNow.setText("Connect");
        }

        List<RunActivity> periodRuns = StatsCalculator.filterByPeriod(allRuns, selectedPeriod);
        StatsCalculator.SummaryStats summary = StatsCalculator.computeSummary(periodRuns);

        tvStatDistance.setText(FormatUtils.formatDistance(summary.totalDistanceMeters, useMiles));
        tvStatRuns.setText(String.valueOf(summary.runCount));
        tvStatPace.setText(FormatUtils.formatPace(summary.getAvgPaceSeconds(useMiles), useMiles));

        String hrPart = summary.hrRunCount > 0 ? FormatUtils.formatHr(summary.avgHr) : "-- bpm";
        String elevPart = "+" + FormatUtils.formatElevation(summary.totalElevationMeters, useMiles);
        tvStatHrElev.setText(hrPart + "\n" + elevPart);

        // Garmin Health & Readiness
        StatsCalculator.WellnessSummary ws = StatsCalculator.computeWellnessSummary(allWellness);
        tvHealthVo2.setText(ws.latestVo2max > 0 ? String.format(Locale.US, "%.1f", ws.latestVo2max) : "--");
        if (ws.latestRestingHr > 0) {
            String avgPart = ws.avg30dRestingHr > 0 ? String.format(Locale.US, " (30d: %.0f)", ws.avg30dRestingHr) : "";
            tvHealthRhr.setText(String.format(Locale.US, "%.0f bpm%s", ws.latestRestingHr, avgPart));
        } else {
            tvHealthRhr.setText("--");
        }
        if (ws.latestHrv > 0) {
            String avgPart = ws.avg30dHrv > 0 ? String.format(Locale.US, " (30d: %.0f)", ws.avg30dHrv) : "";
            tvHealthHrv.setText(String.format(Locale.US, "%.0f ms%s", ws.latestHrv, avgPart));
        } else {
            tvHealthHrv.setText("--");
        }
        if (ws.latestSleepScore > 0 || ws.avg30dSleepHours > 0) {
            StringBuilder sleepSb = new StringBuilder();
            if (ws.avg30dSleepHours > 0) {
                sleepSb.append(String.format(Locale.US, "%.1fh", ws.avg30dSleepHours));
            }
            if (ws.latestSleepScore > 0) {
                if (sleepSb.length() > 0) sleepSb.append(" • ");
                sleepSb.append(String.format(Locale.US, "Score %.0f", ws.latestSleepScore));
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
            tvHighlights.setText("No runs recorded in this period yet.");
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

    private MainActivity getHostActivity() {
        return (getActivity() instanceof MainActivity) ? (MainActivity) getActivity() : null;
    }
}
