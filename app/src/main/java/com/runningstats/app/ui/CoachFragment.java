package com.runningstats.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;
import com.runningstats.app.MainActivity;
import com.runningstats.app.R;
import com.runningstats.app.data.GeminiClient;
import com.runningstats.app.data.StatsCalculator;
import com.runningstats.app.model.ChatMessage;
import com.runningstats.app.model.RunActivity;
import com.runningstats.app.model.WellnessEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CoachFragment extends Fragment implements RefreshableFragment {

    private final List<ChatMessage> messages = new ArrayList<>();
    private ChatAdapter adapter;
    private RecyclerView rvChat;
    private TextInputEditText etCoachMessage;
    private MaterialButton btnSendCoach;
    private ProgressBar progressCoach;
    private TextView tvCoachStatus;

    private ExecutorService executor;
    private Handler mainHandler;
    private boolean isSending = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_coach, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        rvChat = view.findViewById(R.id.rvChat);
        etCoachMessage = view.findViewById(R.id.etCoachMessage);
        btnSendCoach = view.findViewById(R.id.btnSendCoach);
        progressCoach = view.findViewById(R.id.progressCoach);
        tvCoachStatus = view.findViewById(R.id.tvCoachStatus);

        adapter = new ChatAdapter(messages);
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);
        rvChat.setLayoutManager(lm);
        rvChat.setAdapter(adapter);

        bindQuickChip(view.findViewById(R.id.chipPromptHrPace),
                "Analyze my heart rate compared to pace over time, my aerobic efficiency, and my Garmin VO2 Max / resting HR / HRV. Am I getting aerobically fitter?");
        bindQuickChip(view.findViewById(R.id.chipPromptMonth),
                "Review my running over the last 4 weeks. What looks strong and what should I adjust?");
        bindQuickChip(view.findViewById(R.id.chipPromptYears),
                "How has my running volume, pace, and aerobic efficiency trended across the years?");
        bindQuickChip(view.findViewById(R.id.chipPromptInjury),
                "Look at my recent weekly mileage ramp and ACWR. Am I increasing volume safely?");
        bindQuickChip(view.findViewById(R.id.chipPromptNextWeek),
                "Based on my recent runs, suggest a balanced 4-run schedule for next week.");

        btnSendCoach.setOnClickListener(v -> {
            String text = etCoachMessage.getText() != null ? etCoachMessage.getText().toString().trim() : "";
            if (!text.isEmpty()) {
                etCoachMessage.setText("");
                sendQuestion(text);
            }
        });

        refreshData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void bindQuickChip(Chip chip, String prompt) {
        chip.setOnClickListener(v -> sendQuestion(prompt));
    }

    @Override
    public void refreshData() {
        if (!(getActivity() instanceof MainActivity) || !isAdded()) return;
        MainActivity host = (MainActivity) getActivity();

        if (messages.isEmpty()) {
            int count = host.getDb().getRunCount();
            String welcome;
            if (!host.getPrefs().isGeminiConfigured()) {
                welcome = "Hi! I'm your AI Running Coach (" + count + " runs loaded).\n\n"
                        + "To enable AI chat, paste your personal Gemini API key in Settings (free at aistudio.google.com). "
                        + "Once added, tap any prompt above or ask me anything about your training!";
            } else {
                welcome = "Hi! I have " + count + " of your runs loaded. Ask me about your pace & heart-rate trends, mileage ramp, race readiness, or next week's training!";
            }
            messages.add(new ChatMessage(false, welcome, System.currentTimeMillis()));
            adapter.notifyItemInserted(messages.size() - 1);
        }
    }

    private void sendQuestion(String question) {
        if (isSending || !(getActivity() instanceof MainActivity) || !isAdded()) return;
        MainActivity host = (MainActivity) getActivity();

        if (!host.getPrefs().isGeminiConfigured()) {
            Toast.makeText(requireContext(), "Add your Gemini API key in Settings first.", Toast.LENGTH_LONG).show();
            host.navigateToTab(R.id.nav_settings);
            return;
        }

        messages.add(new ChatMessage(true, question, System.currentTimeMillis()));
        adapter.notifyItemInserted(messages.size() - 1);
        rvChat.scrollToPosition(messages.size() - 1);

        isSending = true;
        progressCoach.setVisibility(View.VISIBLE);
        tvCoachStatus.setVisibility(View.VISIBLE);
        tvCoachStatus.setText("Preparing your running stats...");
        btnSendCoach.setEnabled(false);

        List<ChatMessage> snapshot = new ArrayList<>(messages);
        boolean useMiles = host.getPrefs().useMiles();

        executor.execute(() -> {
            try {
                List<RunActivity> allRuns = host.getDb().getAllRuns();
                List<WellnessEntry> allWellness = host.getDb().getAllWellness();
                String context = StatsCalculator.buildAiCoachContext(allRuns, allWellness, useMiles);
                String reply = GeminiClient.askCoach(host.getPrefs(), context, snapshot, statusMsg ->
                        mainHandler.post(() -> {
                            if (isAdded()) {
                                tvCoachStatus.setText(statusMsg);
                            }
                        })
                );

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    isSending = false;
                    progressCoach.setVisibility(View.GONE);
                    tvCoachStatus.setVisibility(View.GONE);
                    btnSendCoach.setEnabled(true);
                    messages.add(new ChatMessage(false, reply, System.currentTimeMillis()));
                    adapter.notifyItemInserted(messages.size() - 1);
                    rvChat.scrollToPosition(messages.size() - 1);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    isSending = false;
                    progressCoach.setVisibility(View.GONE);
                    tvCoachStatus.setVisibility(View.GONE);
                    btnSendCoach.setEnabled(true);
                    messages.add(new ChatMessage(false, "Could not reach Gemini: " + e.getMessage(), System.currentTimeMillis()));
                    adapter.notifyItemInserted(messages.size() - 1);
                    rvChat.scrollToPosition(messages.size() - 1);
                });
            }
        });
    }
}
