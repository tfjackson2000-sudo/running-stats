package com.runningstats.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.runningstats.app.R;
import com.runningstats.app.model.RunActivity;
import com.runningstats.app.util.FormatUtils;

import java.util.ArrayList;
import java.util.List;

public class RunAdapter extends RecyclerView.Adapter<RunAdapter.RunViewHolder> {

    public interface OnRunClickListener {
        void onRunClick(RunActivity run);
    }

    private final List<RunActivity> items = new ArrayList<>();
    private final OnRunClickListener listener;
    private boolean useMiles = false;

    public RunAdapter(OnRunClickListener listener) {
        this.listener = listener;
    }

    public void setRuns(List<RunActivity> runs, boolean useMiles) {
        this.useMiles = useMiles;
        this.items.clear();
        if (runs != null) {
            this.items.addAll(runs);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RunViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_run, parent, false);
        return new RunViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RunViewHolder holder, int position) {
        RunActivity run = items.get(position);
        holder.tvRunName.setText(run.name);
        holder.tvRunDate.setText(FormatUtils.formatDate(run.getLocalDate()));
        holder.tvRunDistance.setText(FormatUtils.formatDistance(run.distanceMeters, useMiles));
        holder.tvRunPace.setText(FormatUtils.formatPace(run.getPaceSeconds(useMiles), useMiles));
        holder.tvRunDuration.setText(FormatUtils.formatDuration(run.movingTimeSeconds));

        StringBuilder meta = new StringBuilder();
        if (run.averageHeartrate > 40.0) {
            meta.append("HR: ").append(FormatUtils.formatHr(run.averageHeartrate)).append(" • ");
        }
        meta.append("Elev: +").append(FormatUtils.formatElevation(run.elevationGainMeters, useMiles));
        meta.append(" • ").append(run.sportType);
        holder.tvRunMeta.setText(meta.toString());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRunClick(run);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class RunViewHolder extends RecyclerView.ViewHolder {
        final TextView tvRunName;
        final TextView tvRunDate;
        final TextView tvRunDistance;
        final TextView tvRunPace;
        final TextView tvRunDuration;
        final TextView tvRunMeta;

        RunViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRunName = itemView.findViewById(R.id.tvRunName);
            tvRunDate = itemView.findViewById(R.id.tvRunDate);
            tvRunDistance = itemView.findViewById(R.id.tvRunDistance);
            tvRunPace = itemView.findViewById(R.id.tvRunPace);
            tvRunDuration = itemView.findViewById(R.id.tvRunDuration);
            tvRunMeta = itemView.findViewById(R.id.tvRunMeta);
        }
    }
}
