package com.runningstats.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.runningstats.app.R;
import com.runningstats.app.model.ChatMessage;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageHolder> {
    private static final int TYPE_USER = 1;
    private static final int TYPE_COACH = 2;

    private final List<ChatMessage> messages;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isUser ? TYPE_USER : TYPE_COACH;
    }

    @NonNull
    @Override
    public MessageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = (viewType == TYPE_USER) ? R.layout.item_chat_user : R.layout.item_chat_coach;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new MessageHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageHolder holder, int position) {
        holder.tvMessage.setText(messages.get(position).text);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageHolder extends RecyclerView.ViewHolder {
        final TextView tvMessage;

        MessageHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
        }
    }
}
