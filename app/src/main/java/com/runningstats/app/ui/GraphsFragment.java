package com.runningstats.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.material.chip.ChipGroup;
import com.runningstats.app.MainActivity;
import com.runningstats.app.R;
import com.runningstats.app.data.StatsCalculator;
import com.runningstats.app.model.RunActivity;
import com.runningstats.app.model.WellnessEntry;
import com.runningstats.app.util.FormatUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class GraphsFragment extends Fragment implements RefreshableFragment {

    private static final String[] MONTH_LABELS = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    private ChipGroup chipGroupChartRange;
    private TextView tvGraphsEmpty;
    private TextView tvPaceHrTitle;
    private TextView tvEfficiencySummary;
    private TextView tvHrZoneBreakdown;
    private TextView tvWellnessChartNote;
    private TextView tvYearlyTitle;
    private TextView tvCumulativeTitle;
    private TextView tvMonthlyTitle;

    private LineChart chartPaceHr;
    private LineChart chartEfficiency;
    private LineChart chartWellness;
    private BarChart chartYearly;
    private LineChart chartCumulative;
    private BarChart chartMonthly;

    private int selectedMonths = 12; // 12, 24, or 0 (All Years)

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_graphs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        chipGroupChartRange = view.findViewById(R.id.chipGroupChartRange);
        tvGraphsEmpty = view.findViewById(R.id.tvGraphsEmpty);
        tvPaceHrTitle = view.findViewById(R.id.tvPaceHrTitle);
        tvEfficiencySummary = view.findViewById(R.id.tvEfficiencySummary);
        tvHrZoneBreakdown = view.findViewById(R.id.tvHrZoneBreakdown);
        tvWellnessChartNote = view.findViewById(R.id.tvWellnessChartNote);
        tvYearlyTitle = view.findViewById(R.id.tvYearlyTitle);
        tvCumulativeTitle = view.findViewById(R.id.tvCumulativeTitle);
        tvMonthlyTitle = view.findViewById(R.id.tvMonthlyTitle);

        chartPaceHr = view.findViewById(R.id.chartPaceHr);
        chartEfficiency = view.findViewById(R.id.chartEfficiency);
        chartWellness = view.findViewById(R.id.chartWellness);
        chartYearly = view.findViewById(R.id.chartYearly);
        chartCumulative = view.findViewById(R.id.chartCumulative);
        chartMonthly = view.findViewById(R.id.chartMonthly);

        chipGroupChartRange.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipRange12m) {
                selectedMonths = 12;
            } else if (id == R.id.chipRange2y) {
                selectedMonths = 24;
            } else if (id == R.id.chipRangeAll) {
                selectedMonths = 0;
            }
            refreshData();
        });

        refreshData();
    }

    @Override
    public void refreshData() {
        if (!(getActivity() instanceof MainActivity) || !isAdded()) return;
        MainActivity host = (MainActivity) getActivity();
        boolean useMiles = host.getPrefs().useMiles();
        String unit = useMiles ? "mi" : "km";
        String rangeLabel = selectedMonths == 12 ? "12 Months" : (selectedMonths == 24 ? "2 Years" : "All Years");

        tvPaceHrTitle.setText("Heart Rate vs. Pace (" + rangeLabel + ")");
        tvYearlyTitle.setText("Yearly Distance (" + unit + ")");
        tvCumulativeTitle.setText("Cumulative Year Comparison (" + unit + ")");
        tvMonthlyTitle.setText("Monthly Distance — " + rangeLabel + " (" + unit + ")");

        List<RunActivity> allRuns = host.getDb().getAllRuns();
        List<WellnessEntry> allWellness = host.getDb().getAllWellness();

        if (allRuns.isEmpty()) {
            tvGraphsEmpty.setVisibility(View.VISIBLE);
            chartPaceHr.clear();
            chartEfficiency.clear();
            chartWellness.clear();
            chartYearly.clear();
            chartCumulative.clear();
            chartMonthly.clear();
            return;
        }
        tvGraphsEmpty.setVisibility(View.GONE);

        int textSecondary = ContextCompat.getColor(requireContext(), R.color.text_secondary);
        int primaryBlue = ContextCompat.getColor(requireContext(), R.color.brand_blue_primary);
        int cyan = ContextCompat.getColor(requireContext(), R.color.brand_cyan);
        int indigo = ContextCompat.getColor(requireContext(), R.color.brand_indigo);
        int slate = ContextCompat.getColor(requireContext(), R.color.brand_slate);

        List<StatsCalculator.MonthlyPoint> monthly = StatsCalculator.computeMonthlySeries(allRuns, selectedMonths);

        renderPaceHrChart(monthly, useMiles, primaryBlue, indigo, textSecondary);
        renderEfficiencyChart(allRuns, monthly, cyan, textSecondary);
        renderHrZoneBreakdown(allRuns, useMiles);
        renderWellnessChart(allWellness, primaryBlue, cyan, textSecondary);
        renderYearlyChart(allRuns, useMiles, unit, primaryBlue, textSecondary);
        renderCumulativeChart(allRuns, useMiles, new int[]{slate, indigo, cyan, primaryBlue}, textSecondary);
        renderMonthlyChart(monthly, useMiles, cyan, textSecondary);
    }

    private void renderPaceHrChart(List<StatsCalculator.MonthlyPoint> months, boolean useMiles, int paceColor, int hrColor, int axisColor) {
        List<Entry> paceEntries = new ArrayList<>();
        List<Entry> hrEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < months.size(); i++) {
            StatsCalculator.MonthlyPoint mp = months.get(i);
            labels.add(mp.label);
            double paceMin = mp.getAvgPaceMinutes(useMiles);
            if (paceMin > 1.0 && paceMin < 20.0) {
                paceEntries.add(new Entry(i, (float) paceMin));
            }
            double hr = mp.getAvgHr();
            if (hr > 40.0) {
                hrEntries.add(new Entry(i, (float) hr));
            }
        }

        List<ILineDataSet> dataSets = new ArrayList<>();
        if (!paceEntries.isEmpty()) {
            LineDataSet paceSet = new LineDataSet(paceEntries, "Avg Pace (faster ↑)");
            paceSet.setAxisDependency(YAxis.AxisDependency.LEFT);
            paceSet.setColor(paceColor);
            paceSet.setCircleColor(paceColor);
            paceSet.setLineWidth(2.6f);
            paceSet.setCircleRadius(months.size() > 24 ? 2f : 3.2f);
            paceSet.setDrawValues(false);
            paceSet.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
            dataSets.add(paceSet);
        }

        if (!hrEntries.isEmpty()) {
            LineDataSet hrSet = new LineDataSet(hrEntries, "Avg HR bpm (easier ↓)");
            hrSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
            hrSet.setColor(hrColor);
            hrSet.setCircleColor(hrColor);
            hrSet.setLineWidth(2.2f);
            hrSet.setCircleRadius(months.size() > 24 ? 2f : 3f);
            hrSet.setDrawValues(false);
            hrSet.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
            dataSets.add(hrSet);
        }

        if (dataSets.isEmpty()) {
            chartPaceHr.clear();
            return;
        }

        chartPaceHr.setData(new LineData(dataSets));
        chartPaceHr.getDescription().setEnabled(false);

        XAxis xAxis = chartPaceHr.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(Math.min(7, labels.size()));
        xAxis.setLabelRotationAngle(-35f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(axisColor);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));

        // Invert left pace axis so faster pace (smaller min/km) rises UPWARD visually
        YAxis left = chartPaceHr.getAxisLeft();
        left.setInverted(true);
        left.setTextColor(paceColor);
        left.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return FormatUtils.formatPaceShort(value);
            }
        });

        YAxis right = chartPaceHr.getAxisRight();
        right.setEnabled(!hrEntries.isEmpty());
        right.setTextColor(hrColor);
        right.setDrawGridLines(false);

        Legend legend = chartPaceHr.getLegend();
        legend.setTextColor(axisColor);
        legend.setWordWrapEnabled(true);

        chartPaceHr.animateX(450);
        chartPaceHr.invalidate();
    }

    private void renderEfficiencyChart(List<RunActivity> allRuns, List<StatsCalculator.MonthlyPoint> months, int lineColor, int axisColor) {
        StatsCalculator.TrainingInsights ins = StatsCalculator.computeInsights(allRuns);
        if (ins.recentEfficiency > 0.0) {
            if (ins.priorEfficiency > 0.0) {
                tvEfficiencySummary.setText(String.format(
                        Locale.US,
                        "Last 90d: %.2f m/beat (%+.1f%% vs prior 90d) — higher is fitter",
                        ins.recentEfficiency, ins.efficiencyChangePct
                ));
            } else {
                tvEfficiencySummary.setText(String.format(
                        Locale.US,
                        "Last 90d: %.2f meters per heartbeat — higher is fitter",
                        ins.recentEfficiency
                ));
            }
        } else {
            tvEfficiencySummary.setText("Needs runs with heart rate data to compute aerobic efficiency.");
        }

        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < months.size(); i++) {
            StatsCalculator.MonthlyPoint mp = months.get(i);
            labels.add(mp.label);
            double eff = mp.getAvgEfficiency();
            if (eff > 0.2 && eff < 5.0) {
                entries.add(new Entry(i, (float) eff));
            }
        }

        if (entries.isEmpty()) {
            chartEfficiency.clear();
            return;
        }

        LineDataSet set = new LineDataSet(entries, "Meters / Heartbeat");
        set.setColor(lineColor);
        set.setCircleColor(lineColor);
        set.setLineWidth(2.6f);
        set.setCircleRadius(months.size() > 24 ? 2f : 3.2f);
        set.setDrawValues(false);
        set.setDrawFilled(true);
        set.setFillColor(lineColor);
        set.setFillAlpha(35);
        set.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);

        chartEfficiency.setData(new LineData(set));
        chartEfficiency.getDescription().setEnabled(false);
        chartEfficiency.getAxisRight().setEnabled(false);
        chartEfficiency.getLegend().setEnabled(false);

        XAxis xAxis = chartEfficiency.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(Math.min(7, labels.size()));
        xAxis.setLabelRotationAngle(-35f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(axisColor);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));

        YAxis left = chartEfficiency.getAxisLeft();
        left.setTextColor(axisColor);
        left.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.US, "%.2f", value);
            }
        });

        chartEfficiency.animateX(450);
        chartEfficiency.invalidate();
    }

    private void renderHrZoneBreakdown(List<RunActivity> allRuns, boolean useMiles) {
        List<StatsCalculator.HrZonePaceRow> rows = StatsCalculator.computeHrZonePaceComparison(allRuns);
        if (rows.isEmpty()) {
            tvHrZoneBreakdown.setText("Sync runs with heart rate data to compare your pace in each HR zone.");
            return;
        }
        String unit = useMiles ? "/mi" : "/km";
        double factor = useMiles ? 1.609344 : 1.0;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            StatsCalculator.HrZonePaceRow row = rows.get(i);
            if (i > 0) sb.append("\n");
            sb.append("• ").append(row.label).append(": ");
            if (row.recentCount > 0 && row.olderCount > 0) {
                double rec = row.recentPaceSecPerKm * factor;
                double old = row.olderPaceSecPerKm * factor;
                long diffSec = Math.round(rec - old);
                String delta;
                if (diffSec <= -2) {
                    delta = String.format(Locale.US, " (%ds %s faster!)", Math.abs(diffSec), unit);
                } else if (diffSec >= 2) {
                    delta = String.format(Locale.US, " (+%ds %s)", diffSec, unit);
                } else {
                    delta = " (steady)";
                }
                sb.append(FormatUtils.formatPace(rec, useMiles))
                        .append(" vs ")
                        .append(FormatUtils.formatPace(old, useMiles))
                        .append(delta);
            } else if (row.recentCount > 0) {
                sb.append(FormatUtils.formatPace(row.recentPaceSecPerKm * factor, useMiles))
                        .append(" (").append(row.recentCount).append(" recent runs)");
            } else {
                sb.append(FormatUtils.formatPace(row.olderPaceSecPerKm * factor, useMiles))
                        .append(" (").append(row.olderCount).append(" older runs)");
            }
        }
        tvHrZoneBreakdown.setText(sb.toString());
    }

    private void renderWellnessChart(List<WellnessEntry> allWellness, int vo2Color, int rhrColor, int axisColor) {
        if (allWellness == null || allWellness.isEmpty()) {
            tvWellnessChartNote.setText("Tap Sync in You → Settings to pull Garmin VO2 Max, Resting HR & HRV from Intervals.icu.");
            chartWellness.clear();
            return;
        }

        List<StatsCalculator.MonthlyWellnessPoint> months = StatsCalculator.computeMonthlyWellness(allWellness, selectedMonths);
        List<Entry> vo2Entries = new ArrayList<>();
        List<Entry> rhrEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < months.size(); i++) {
            StatsCalculator.MonthlyWellnessPoint mp = months.get(i);
            labels.add(mp.label);
            if (mp.getAvgVo2() > 15.0) {
                vo2Entries.add(new Entry(i, (float) mp.getAvgVo2()));
            }
            if (mp.getAvgRhr() > 25.0) {
                rhrEntries.add(new Entry(i, (float) mp.getAvgRhr()));
            }
        }

        List<ILineDataSet> dataSets = new ArrayList<>();
        if (!vo2Entries.isEmpty()) {
            LineDataSet vo2Set = new LineDataSet(vo2Entries, "VO2 Max");
            vo2Set.setAxisDependency(YAxis.AxisDependency.LEFT);
            vo2Set.setColor(vo2Color);
            vo2Set.setCircleColor(vo2Color);
            vo2Set.setLineWidth(2.6f);
            vo2Set.setCircleRadius(3f);
            vo2Set.setDrawValues(false);
            dataSets.add(vo2Set);
        }
        if (!rhrEntries.isEmpty()) {
            LineDataSet rhrSet = new LineDataSet(rhrEntries, "Resting HR (bpm)");
            rhrSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
            rhrSet.setColor(rhrColor);
            rhrSet.setCircleColor(rhrColor);
            rhrSet.setLineWidth(2.2f);
            rhrSet.setCircleRadius(3f);
            rhrSet.setDrawValues(false);
            dataSets.add(rhrSet);
        }

        if (dataSets.isEmpty()) {
            tvWellnessChartNote.setText("No VO2 Max or Resting HR wellness records synced yet.");
            chartWellness.clear();
            return;
        }

        tvWellnessChartNote.setText("Blue = VO2 Max (left) • Cyan = Resting HR bpm (right)");
        chartWellness.setData(new LineData(dataSets));
        chartWellness.getDescription().setEnabled(false);

        XAxis xAxis = chartWellness.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(Math.min(7, labels.size()));
        xAxis.setLabelRotationAngle(-35f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(axisColor);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));

        YAxis left = chartWellness.getAxisLeft();
        left.setEnabled(!vo2Entries.isEmpty());
        left.setTextColor(vo2Color);

        YAxis right = chartWellness.getAxisRight();
        right.setEnabled(!rhrEntries.isEmpty());
        right.setTextColor(rhrColor);
        right.setDrawGridLines(false);

        Legend legend = chartWellness.getLegend();
        legend.setTextColor(axisColor);

        chartWellness.animateX(450);
        chartWellness.invalidate();
    }

    private void renderYearlyChart(List<RunActivity> runs, boolean useMiles, String unit, int barColor, int axisColor) {
        TreeMap<Integer, Double> yearly = StatsCalculator.computeYearlyDistances(runs, useMiles);
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int idx = 0;
        for (Map.Entry<Integer, Double> e : yearly.entrySet()) {
            entries.add(new BarEntry(idx++, e.getValue().floatValue()));
            labels.add(String.valueOf(e.getKey()));
        }

        BarDataSet dataSet = new BarDataSet(entries, "Distance (" + unit + ")");
        dataSet.setColor(barColor);
        dataSet.setValueTextColor(axisColor);
        dataSet.setValueTextSize(10f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.US, "%.0f", value);
            }
        });

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.55f);

        chartYearly.setData(barData);
        chartYearly.getDescription().setEnabled(false);
        chartYearly.getLegend().setEnabled(false);
        chartYearly.setFitBars(true);
        chartYearly.getAxisRight().setEnabled(false);

        XAxis xAxis = chartYearly.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(axisColor);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));

        YAxis leftAxis = chartYearly.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setTextColor(axisColor);

        chartYearly.animateY(450);
        chartYearly.invalidate();
    }

    private void renderCumulativeChart(List<RunActivity> runs, boolean useMiles, int[] palette, int axisColor) {
        LinkedHashMap<Integer, float[]> byYear = StatsCalculator.computeCumulativeByYear(runs, useMiles, 4);
        List<ILineDataSet> dataSets = new ArrayList<>();
        int totalYears = byYear.size();
        int idx = 0;

        for (Map.Entry<Integer, float[]> entry : byYear.entrySet()) {
            int year = entry.getKey();
            float[] values = entry.getValue();
            List<Entry> points = new ArrayList<>();
            for (int m = 0; m < 12; m++) {
                points.add(new Entry(m, values[m]));
            }

            int paletteOffset = Math.max(0, palette.length - totalYears);
            int color = palette[Math.min(palette.length - 1, paletteOffset + idx)];
            boolean isLatest = (idx == totalYears - 1);

            LineDataSet set = new LineDataSet(points, String.valueOf(year));
            set.setColor(color);
            set.setCircleColor(color);
            set.setLineWidth(isLatest ? 3f : 2f);
            set.setCircleRadius(isLatest ? 3.5f : 2.5f);
            set.setDrawValues(false);
            set.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
            dataSets.add(set);
            idx++;
        }

        chartCumulative.setData(new LineData(dataSets));
        chartCumulative.getDescription().setEnabled(false);
        chartCumulative.getAxisRight().setEnabled(false);

        XAxis xAxis = chartCumulative.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(axisColor);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(MONTH_LABELS));

        YAxis left = chartCumulative.getAxisLeft();
        left.setAxisMinimum(0f);
        left.setTextColor(axisColor);

        Legend legend = chartCumulative.getLegend();
        legend.setTextColor(axisColor);
        legend.setWordWrapEnabled(true);

        chartCumulative.animateX(450);
        chartCumulative.invalidate();
    }

    private void renderMonthlyChart(List<StatsCalculator.MonthlyPoint> months, boolean useMiles, int barColor, int axisColor) {
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < months.size(); i++) {
            StatsCalculator.MonthlyPoint mp = months.get(i);
            entries.add(new BarEntry(i, (float) mp.getDistance(useMiles)));
            labels.add(mp.label);
        }

        BarDataSet dataSet = new BarDataSet(entries, "Monthly");
        dataSet.setColor(barColor);
        dataSet.setDrawValues(false);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.62f);

        chartMonthly.setData(barData);
        chartMonthly.getDescription().setEnabled(false);
        chartMonthly.getLegend().setEnabled(false);
        chartMonthly.setFitBars(true);
        chartMonthly.getAxisRight().setEnabled(false);

        XAxis xAxis = chartMonthly.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(Math.min(7, labels.size()));
        xAxis.setLabelRotationAngle(-35f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(axisColor);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));

        YAxis left = chartMonthly.getAxisLeft();
        left.setAxisMinimum(0f);
        left.setTextColor(axisColor);

        chartMonthly.animateY(450);
        chartMonthly.invalidate();
    }
}
