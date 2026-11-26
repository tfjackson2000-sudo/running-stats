package com.runningstats.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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
import com.runningstats.app.data.StravaClient;
import com.runningstats.app.util.FormatUtils;

public class SettingsFragment extends Fragment implements RefreshableFragment {

    private MaterialSwitch switchUseMiles;
    private TextView tvStravaConnectionStatus;
    private TextInputEditText etStravaClientId;
    private TextInputEditText etStravaClientSecret;
    private MaterialButton btnConnectStrava;
    private MaterialButton btnPasteCode;
    private MaterialButton btnSyncIncremental;
    private MaterialButton btnSyncFull;
    private TextView tvSyncProgress;

    private TextInputEditText etGeminiKey;
    private TextInputEditText etGeminiModel;
    private MaterialButton btnSaveAi;

    private MaterialButton btnSeedSample;
    private MaterialButton btnClearData;
    private MaterialButton btnDisconnectStrava;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        switchUseMiles = view.findViewById(R.id.switchUseMiles);
        tvStravaConnectionStatus = view.findViewById(R.id.tvStravaConnectionStatus);
        etStravaClientId = view.findViewById(R.id.etStravaClientId);
        etStravaClientSecret = view.findViewById(R.id.etStravaClientSecret);
        btnConnectStrava = view.findViewById(R.id.btnConnectStrava);
        btnPasteCode = view.findViewById(R.id.btnPasteCode);
        btnSyncIncremental = view.findViewById(R.id.btnSyncIncremental);
        btnSyncFull = view.findViewById(R.id.btnSyncFull);
        tvSyncProgress = view.findViewById(R.id.tvSyncProgress);

        etGeminiKey = view.findViewById(R.id.etGeminiKey);
        etGeminiModel = view.findViewById(R.id.etGeminiModel);
        btnSaveAi = view.findViewById(R.id.btnSaveAi);

        btnSeedSample = view.findViewById(R.id.btnSeedSample);
        btnClearData = view.findViewById(R.id.btnClearData);
        btnDisconnectStrava = view.findViewById(R.id.btnDisconnectStrava);

        loadFields();

        switchUseMiles.setOnCheckedChangeListener((buttonView, isChecked) -> {
            MainActivity host = getHostActivity();
            if (host == null) return;
            host.getPrefs().setUseMiles(isChecked);
            Toast.makeText(requireContext(), isChecked ? "Switched to Miles (mi)" : "Switched to Kilometers (km)", Toast.LENGTH_SHORT).show();
        });

        btnConnectStrava.setOnClickListener(v -> {
            MainActivity host = getHostActivity();
            if (host == null) return;
            String clientId = getText(etStravaClientId);
            String clientSecret = getText(etStravaClientSecret);
            if (clientId.isEmpty() || clientSecret.isEmpty()) {
                Toast.makeText(requireContext(), "Enter both Strava Client ID and Client Secret first.", Toast.LENGTH_LONG).show();
                return;
            }
            host.getPrefs().setStravaClientId(clientId);
            host.getPrefs().setStravaClientSecret(clientSecret);

            Uri authUri = StravaClient.buildAuthorizeUri(clientId);
            Intent intent = new Intent(Intent.ACTION_VIEW, authUri);
            startActivity(intent);
        });

        btnPasteCode.setOnClickListener(v -> showManualCodeDialog());
        btnSyncIncremental.setOnClickListener(v -> runSync(false));
        btnSyncFull.setOnClickListener(v -> runSync(true));

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
                .setMessage("This removes all cached runs from your phone. You can re-sync from Strava anytime.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    MainActivity host = getHostActivity();
                    if (host == null) return;
                    host.getDb().clearAllRuns();
                    Toast.makeText(requireContext(), "Local runs cleared.", Toast.LENGTH_SHORT).show();
                    refreshData();
                })
                .show());

        btnDisconnectStrava.setOnClickListener(v -> {
            MainActivity host = getHostActivity();
            if (host == null) return;
            host.getPrefs().clearStravaTokens();
            Toast.makeText(requireContext(), "Disconnected Strava tokens.", Toast.LENGTH_SHORT).show();
            refreshData();
        });
    }

    private void loadFields() {
        MainActivity host = getHostActivity();
        if (host == null) return;
        PrefsManager prefs = host.getPrefs();
        switchUseMiles.setChecked(prefs.useMiles());
        etStravaClientId.setText(prefs.getStravaClientId());
        etStravaClientSecret.setText(prefs.getStravaClientSecret());
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

        if (prefs.isStravaConnected()) {
            String name = prefs.getAthleteName();
            String label = name.isEmpty() ? "Connected" : "Connected as " + name;
            tvStravaConnectionStatus.setText("Status: " + label + " • " + runCount + " runs (Last sync: "
                    + FormatUtils.formatTimestamp(prefs.getLastSyncMs()) + ")");
        } else {
            tvStravaConnectionStatus.setText("Status: Not connected • " + runCount + " runs stored locally");
        }
    }

    private void runSync(boolean fullHistory) {
        MainActivity host = getHostActivity();
        if (host == null) return;

        // Save latest ID/Secret edits in case user updated them
        String cid = getText(etStravaClientId);
        String csec = getText(etStravaClientSecret);
        if (!cid.isEmpty()) host.getPrefs().setStravaClientId(cid);
        if (!csec.isEmpty()) host.getPrefs().setStravaClientSecret(csec);

        tvSyncProgress.setVisibility(View.VISIBLE);
        tvSyncProgress.setText(fullHistory ? "Starting full Strava history sync..." : "Checking for new runs...");
        btnSyncIncremental.setEnabled(false);
        btnSyncFull.setEnabled(false);

        host.syncStrava(fullHistory, new MainActivity.SyncCallback() {
            @Override
            public void onProgress(String message) {
                if (isAdded()) {
                    tvSyncProgress.setText(message);
                }
            }

            @Override
            public void onComplete(boolean success, String message) {
                if (!isAdded()) return;
                btnSyncIncremental.setEnabled(true);
                btnSyncFull.setEnabled(true);
                tvSyncProgress.setText(message);
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                refreshData();
            }
        });
    }

    private void showManualCodeDialog() {
        MainActivity host = getHostActivity();
        if (host == null) return;
        String cid = getText(etStravaClientId);
        String csec = getText(etStravaClientSecret);
        if (!cid.isEmpty()) host.getPrefs().setStravaClientId(cid);
        if (!csec.isEmpty()) host.getPrefs().setStravaClientSecret(csec);

        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("Paste redirected URL or code= value");

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Manual Strava Authorization")
                .setMessage("If your browser did not automatically return to RunStats, copy the address bar URL (or the code parameter) and paste it here.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Connect", (dialog, which) -> {
                    String raw = input.getText() != null ? input.getText().toString().trim() : "";
                    host.exchangeStravaCode(raw);
                })
                .show();
    }

    private String getText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private MainActivity getHostActivity() {
        return (getActivity() instanceof MainActivity) ? (MainActivity) getActivity() : null;
    }
}
