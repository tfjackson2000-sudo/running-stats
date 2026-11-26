package com.runningstats.app.model;

public class ChatMessage {
    public final boolean isUser;
    public final String text;
    public final long timestampMs;

    public ChatMessage(boolean isUser, String text, long timestampMs) {
        this.isUser = isUser;
        this.text = text;
        this.timestampMs = timestampMs;
    }
}
