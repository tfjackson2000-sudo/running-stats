package com.runningstats.app.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.runningstats.app.MainActivity;
import com.runningstats.app.R;
import com.runningstats.app.data.StatsCalculator;
import com.runningstats.app.model.RunActivity;
import com.runningstats.app.util.FormatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RunsFragment extends Fragment implements RefreshableFragment {

    private TextInputEditText etSearchRuns;
    private ChipGroup chipGroupYears;
    private TextView tvRunsCount;
    private RecyclerView rvRuns;

    private RunAdapter adapter;
    private List<RunActivity> allRuns = new ArrayList<>();
    private Integer selectedYear = null; // null = All Years
    private String searchQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_runs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etSearchRuns = view.findViewById(R.id.etSearchRuns);
        chipGroupYears = view.findViewById(R.id.chipGroupYears);
        tvRunsCount = view.findViewById(R.id.tvRunsCount);
        rvRuns = view.findViewById(R.id.rvRuns);

        adapter = new RunAdapter(this::showRunDetailDialog);
        rvRuns.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvRuns.setAdapter(adapter);

        etSearchRuns.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s != null ? s.toString().trim().toLowerCase(Locale.US) : "";
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        refreshData();
    }

    @Override
    public void refreshData() {
        if (!(getActivity() instanceof MainActivity) || !isAdded()) return;
        MainActivity host = (MainActivity) getActivity();
        allRuns = host.getDb().getAllRuns();
        buildYearChips();
        applyFilters();
    }

    private void buildYearChips() {
        chipGroupYears.removeAllViews();

        Chip allChip = createFilterChip("All Years", null);
        allChip.setChecked(selectedYear == null);
        chipGroupYears.addView(allChip);

        List<Integer> years = StatsCalculator.getAvailableYears(allRuns);
        for (Integer year : years) {
            Chip chip = createFilterChip(String.valueOf(year), year);
            chip.setChecked(year.equals(selectedYear));
            chipGroupYears.addView(chip);
        }
    }

    private Chip createFilterChip(String label, Integer yearValue) {
        Chip chip = new Chip(requireContext());
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setOnClickListener(v -> {
            selectedYear = yearValue;
            applyFilters();
        });
        return chip;
    }

    private void applyFilters() {
        if (!(getActivity() instanceof MainActivity) || !isAdded()) return;
        boolean useMiles = ((MainActivity) getActivity()).getPrefs().useMiles();

        List<RunActivity> filtered = new ArrayList<>();
        double totalMeters = 0.0;
        for (RunActivity r : allRuns) {
            if (selectedYear != null && r.getYear() != selectedYear) {
                continue;
            }
            if (!searchQuery.isEmpty()) {
                String haystack = (r.name + " " + r.getLocalDate() + " " + r.sportType).toLowerCase(Locale.US);
                if (!haystack.contains(searchQuery)) {
                    continue;
                }
            }
            filtered.add(r);
            totalMeters += r.distanceMeters;
        }

        adapter.setRuns(filtered, useMiles);
        tvRunsCount.setText(String.format(
                Locale.US,
                "%d runs • %s",
                filtered.size(),
                FormatUtils.formatDistance(totalMeters, useMiles)
        ));
    }

    private void showRunDetailDialog(RunActivity run) {
        if (!(getActivity() instanceof MainActivity) || !isAdded()) return;
        boolean useMiles = ((MainActivity) getActivity()).getPrefs().useMiles();

        StringBuilder sb = new StringBuilder();
        sb.append("Date: ").append(FormatUtils.formatDate(run.getLocalDate())).append("\n");
        sb.append("Type: ").append(run.sportType).append("\n");
        sb.append("Distance: ").append(FormatUtils.formatDistance(run.distanceMeters, useMiles)).append("\n");
        sb.append("Avg Pace: ").append(FormatUtils.formatPace(run.getPaceSeconds(useMiles), useMiles)).append("\n");
        sb.append("Moving Time: ").append(FormatUtils.formatDuration(run.movingTimeSeconds)).append("\n");
        sb.append("Elapsed Time: ").append(FormatUtils.formatDuration(run.elapsedTimeSeconds)).append("\n");
        sb.append("Elevation Gain: +").append(FormatUtils.formatElevation(run.elevationGainMeters, useMiles)).append("\n");

        if (run.averageHeartrate > 40.0) {
            sb.append("Avg Heart Rate: ").append(FormatUtils.formatHr(run.averageHeartrate)).append("\n");
        }
        if (run.maxHeartrate > 40.0) {
            sb.append("Max Heart Rate: ").append(FormatUtils.formatHr(run.maxHeartrate)).append("\n");
        }
        if (run.getStepsPerMinute() > 0.0) {
            sb.append(String.format(Locale.US, "Cadence: %.0f spm\n", run.getStepsPerMinute()));
        }
        if (run.getAerobicEfficiency() > 0.0) {
            sb.append(String.format(Locale.US, "Aerobic Efficiency: %.2f m/beat\n", run.getAerobicEfficiency()));
        }
        if (run.sufferScore > 0.0) {
            sb.append(String.format(Locale.US, "Relative Effort: %.0f", run.sufferScore));
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(run.name)
                .setMessage(sb.toString().trim())
                .setPositiveButton("Close", null)
                .show();
    }
}
