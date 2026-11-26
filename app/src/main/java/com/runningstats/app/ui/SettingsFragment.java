package com.runningstats.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.runningstats.app.MainActivity;
import com.runningstats.app.R;
import com.runningstats.app.data.PrefsManager;
import com.runningstats.app.util.FormatUtils;

public class SettingsFragment extends Fragment implements RefreshableFragment {

    private MaterialSwitch switchUseMiles;
    private TextView tvConnectionStatus;
    private TextInputEditText etIntervalsKey;
    private MaterialButton btnSaveAndSync;
    private MaterialButton btnSyncIncremental;
    private MaterialButton btnOpenIntervals;
    private TextView tvSyncProgress;

    private MaterialButton btnImportCsv;
    private TextView tvCsvStatus;

    private TextInputEditText etGeminiKey;
    private TextInputEditText etGeminiModel;
    private MaterialButton btnSaveAi;

    private MaterialButton btnSeedSample;
    private MaterialButton btnClearData;
    private MaterialButton btnDisconnect;

    private ActivityResultLauncher<String[]> csvPickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        csvPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        promptCsvUnitsAndImport(uri);
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        switchUseMiles = view.findViewById(R.id.switchUseMiles);
        tvConnectionStatus = view.findViewById(R.id.tvConnectionStatus);
        etIntervalsKey = view.findViewById(R.id.etIntervalsKey);
        btnSaveAndSync = view.findViewById(R.id.btnSaveAndSync);
        btnSyncIncremental = view.findViewById(R.id.btnSyncIncremental);
        btnOpenIntervals = view.findViewById(R.id.btnOpenIntervals);
        tvSyncProgress = view.findViewById(R.id.tvSyncProgress);

        btnImportCsv = view.findViewById(R.id.btnImportCsv);
        tvCsvStatus = view.findViewById(R.id.tvCsvStatus);

        etGeminiKey = view.findViewById(R.id.etGeminiKey);
        etGeminiModel = view.findViewById(R.id.etGeminiModel);
        btnSaveAi = view.findViewById(R.id.btnSaveAi);

        btnSeedSample = view.findViewById(R.id.btnSeedSample);
        btnClearData = view.findViewById(R.id.btnClearData);
        btnDisconnect = view.findViewById(R.id.btnDisconnect);

        loadFields();

        switchUseMiles.setOnCheckedChangeListener((buttonView, isChecked) -> {
            MainActivity host = getHostActivity();
            if (host == null) return;
            host.getPrefs().setUseMiles(isChecked);
            Toast.makeText(requireContext(), isChecked ? "Switched to Miles (mi)" : "Switched to Kilometers (km)", Toast.LENGTH_SHORT).show();
        });

        btnSaveAndSync.setOnClickListener(v -> {
            MainActivity host = getHostActivity();
            if (host == null) return;
            String key = getText(etIntervalsKey);
            if (key.isEmpty()) {
                Toast.makeText(requireContext(), "Paste your free Intervals.icu API key first.", Toast.LENGTH_LONG).show();
                return;
            }
            host.getPrefs().setIntervalsApiKey(key);
            runIntervalsSync(true);
        });

        btnSyncIncremental.setOnClickListener(v -> {
            MainActivity host = getHostActivity();
            if (host == null) return;
            String key = getText(etIntervalsKey);
            if (!key.isEmpty()) {
                host.getPrefs().setIntervalsApiKey(key);
            }
            runIntervalsSync(false);
        });

        btnOpenIntervals.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://intervals.icu/settings"));
            startActivity(intent);
        });

        btnImportCsv.setOnClickListener(v -> csvPickerLauncher.launch(new String[]{"text/*", "application/octet-stream", "*/*"}));

        btnSaveAi.setOnClickListener(v -> {
            MainActivity host = getHostActivity();
            if (host == null) return;
            host.getPrefs().setGeminiApiKey(getText(etGeminiKey));
            host.getPrefs().setGeminiModel(getText(etGeminiModel));
            Toast.makeText(requireContext(), "AI Coach settings saved!", Toast.LENGTH_SHORT).show();
        });

        btnSeedSample.setOnClickListener(v -> {
            MainActivity host = getHostActivity();
            if (host == null) return;
            int count = host.getDb().seedSampleRuns();
            Toast.makeText(requireContext(), "Loaded " + count + " sample runs.", Toast.LENGTH_SHORT).show();
            refreshData();
        });

        btnClearData.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear Local Runs?")
                .setMessage("This removes all cached runs from your phone. You can re-sync or re-import anytime.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    MainActivity host = getHostActivity();
                    if (host == null) return;
                    host.getDb().clearAllRuns();
                    Toast.makeText(requireContext(), "Local runs cleared.", Toast.LENGTH_SHORT).show();
                    refreshData();
                })
                .show());

        btnDisconnect.setOnClickListener(v -> {
            MainActivity host = getHostActivity();
            if (host == null) return;
            host.getPrefs().clearIntervalsConnection();
            etIntervalsKey.setText("");
            Toast.makeText(requireContext(), "Cleared Intervals.icu API key.", Toast.LENGTH_SHORT).show();
            refreshData();
        });
    }

    private void loadFields() {
        MainActivity host = getHostActivity();
        if (host == null) return;
        PrefsManager prefs = host.getPrefs();
        switchUseMiles.setChecked(prefs.useMiles());
        etIntervalsKey.setText(prefs.getIntervalsApiKey());
        etGeminiKey.setText(prefs.getGeminiApiKey());
        etGeminiModel.setText(prefs.getGeminiModel());
        refreshData();
    }

    @Override
    public void refreshData() {
        MainActivity host = getHostActivity();
        if (host == null || !isAdded()) return;
        PrefsManager prefs = host.getPrefs();
        int runCount = host.getDb().getRunCount();

        if (prefs.isIntervalsConnected()) {
            String name = prefs.getAthleteName();
            String label = name.isEmpty() ? "API Key Saved" : "Connected as " + name;
            tvConnectionStatus.setText("Status: " + label + " • " + runCount + " runs (Last sync: "
                    + FormatUtils.formatTimestamp(prefs.getLastSyncMs()) + ")");
        } else {
            tvConnectionStatus.setText("Status: Not connected • " + runCount + " runs stored locally");
        }
    }

    private void runIntervalsSync(boolean fullHistory) {
        MainActivity host = getHostActivity();
        if (host == null) return;

        tvSyncProgress.setVisibility(View.VISIBLE);
        tvSyncProgress.setText(fullHistory ? "Starting full Garmin/Intervals history sync..." : "Checking for new runs...");
        btnSaveAndSync.setEnabled(false);
        btnSyncIncremental.setEnabled(false);

        host.syncIntervals(fullHistory, new MainActivity.SyncCallback() {
            @Override
            public void onProgress(String message) {
                if (isAdded()) {
                    tvSyncProgress.setText(message);
                }
            }

            @Override
            public void onComplete(boolean success, String message) {
                if (!isAdded()) return;
                btnSaveAndSync.setEnabled(true);
                btnSyncIncremental.setEnabled(true);
                tvSyncProgress.setText(message);
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                refreshData();
            }
        });
    }

    private void promptCsvUnitsAndImport(Uri csvUri) {
        MainActivity host = getHostActivity();
        if (host == null) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Garmin CSV Distance Units")
                .setMessage("Were distances in this Garmin Connect CSV exported in Kilometers or Miles?")
                .setPositiveButton("Kilometers (km)", (d, w) -> startCsvImport(csvUri, false))
                .setNegativeButton("Miles (mi)", (d, w) -> startCsvImport(csvUri, true))
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void startCsvImport(Uri csvUri, boolean csvInMiles) {
        MainActivity host = getHostActivity();
        if (host == null) return;

        tvCsvStatus.setVisibility(View.VISIBLE);
        tvCsvStatus.setText("Importing Garmin CSV...");
        btnImportCsv.setEnabled(false);

        host.importGarminCsv(csvUri, csvInMiles, new MainActivity.SyncCallback() {
            @Override
            public void onProgress(String message) {
                if (isAdded()) tvCsvStatus.setText(message);
            }

            @Override
            public void onComplete(boolean success, String message) {
                if (!isAdded()) return;
                btnImportCsv.setEnabled(true);
                tvCsvStatus.setText(message);
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                refreshData();
            }
        });
    }

    private String getText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private MainActivity getHostActivity() {
        return (getActivity() instanceof MainActivity) ? (MainActivity) getActivity() : null;
    }
}
