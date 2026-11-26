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
import com.runningstats.app.MainActivity;
import com.runningstats.app.R;
import com.runningstats.app.data.StatsCalculator;
import com.runningstats.app.model.RunActivity;
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

    private TextView tvGraphsEmpty;
    private TextView tvYearlyTitle;
    private TextView tvCumulativeTitle;
    private TextView tvMonthlyTitle;
    private TextView tvPaceHrTitle;

    private BarChart chartYearly;
    private LineChart chartCumulative;
    private BarChart chartMonthly;
    private LineChart chartPaceHr;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_graphs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvGraphsEmpty = view.findViewById(R.id.tvGraphsEmpty);
        tvYearlyTitle = view.findViewById(R.id.tvYearlyTitle);
        tvCumulativeTitle = view.findViewById(R.id.tvCumulativeTitle);
        tvMonthlyTitle = view.findViewById(R.id.tvMonthlyTitle);
        tvPaceHrTitle = view.findViewById(R.id.tvPaceHrTitle);

        chartYearly = view.findViewById(R.id.chartYearly);
        chartCumulative = view.findViewById(R.id.chartCumulative);
        chartMonthly = view.findViewById(R.id.chartMonthly);
        chartPaceHr = view.findViewById(R.id.chartPaceHr);

        refreshData();
    }

    @Override
    public void refreshData() {
        if (!(getActivity() instanceof MainActivity) || !isAdded()) return;
        MainActivity host = (MainActivity) getActivity();
        boolean useMiles = host.getPrefs().useMiles();
        String unit = useMiles ? "mi" : "km";

        tvYearlyTitle.setText("Yearly Distance (" + unit + ")");
        tvCumulativeTitle.setText("Cumulative Year Comparison (" + unit + ")");
        tvMonthlyTitle.setText("Monthly Distance — Last 12 Months (" + unit + ")");
        tvPaceHrTitle.setText("Avg Pace (min/" + unit + ") & Heart Rate (12 Months)");

        List<RunActivity> allRuns = host.getDb().getAllRuns();
        if (allRuns.isEmpty()) {
            tvGraphsEmpty.setVisibility(View.VISIBLE);
            chartYearly.clear();
            chartCumulative.clear();
            chartMonthly.clear();
            chartPaceHr.clear();
            return;
        }
        tvGraphsEmpty.setVisibility(View.GONE);

        int textSecondary = ContextCompat.getColor(requireContext(), R.color.text_secondary);
        int primaryBlue = ContextCompat.getColor(requireContext(), R.color.brand_blue_primary);
        int cyan = ContextCompat.getColor(requireContext(), R.color.brand_cyan);
        int indigo = ContextCompat.getColor(requireContext(), R.color.brand_indigo);
        int slate = ContextCompat.getColor(requireContext(), R.color.brand_slate);

        renderYearlyChart(allRuns, useMiles, unit, primaryBlue, textSecondary);
        renderCumulativeChart(allRuns, useMiles, new int[]{slate, indigo, cyan, primaryBlue}, textSecondary);
        renderMonthlyChart(allRuns, useMiles, cyan, textSecondary);
        renderPaceHrChart(allRuns, useMiles, primaryBlue, indigo, textSecondary);
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

            // Pick palette color so the most recent year gets the primary brand orange
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

    private void renderMonthlyChart(List<RunActivity> runs, boolean useMiles, int barColor, int axisColor) {
        List<StatsCalculator.MonthlyPoint> months = StatsCalculator.computeRecentMonths(runs, 12);
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
        xAxis.setLabelRotationAngle(-40f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(axisColor);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));

        YAxis left = chartMonthly.getAxisLeft();
        left.setAxisMinimum(0f);
        left.setTextColor(axisColor);

        chartMonthly.animateY(450);
        chartMonthly.invalidate();
    }

    private void renderPaceHrChart(List<RunActivity> runs, boolean useMiles, int paceColor, int hrColor, int axisColor) {
        List<StatsCalculator.MonthlyPoint> months = StatsCalculator.computeRecentMonths(runs, 12);
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
            LineDataSet paceSet = new LineDataSet(paceEntries, "Avg Pace");
            paceSet.setAxisDependency(YAxis.AxisDependency.LEFT);
            paceSet.setColor(paceColor);
            paceSet.setCircleColor(paceColor);
            paceSet.setLineWidth(2.5f);
            paceSet.setCircleRadius(3f);
            paceSet.setDrawValues(false);
            dataSets.add(paceSet);
        }

        if (!hrEntries.isEmpty()) {
            LineDataSet hrSet = new LineDataSet(hrEntries, "Avg HR (bpm)");
            hrSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
            hrSet.setColor(hrColor);
            hrSet.setCircleColor(hrColor);
            hrSet.setLineWidth(2.2f);
            hrSet.setCircleRadius(3f);
            hrSet.setDrawValues(false);
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
        xAxis.setLabelRotationAngle(-40f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(axisColor);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));

        YAxis left = chartPaceHr.getAxisLeft();
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

        chartPaceHr.animateX(450);
        chartPaceHr.invalidate();
    }
}
