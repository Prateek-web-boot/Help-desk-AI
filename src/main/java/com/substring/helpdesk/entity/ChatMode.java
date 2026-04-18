package com.substring.helpdesk.entity;

public enum ChatMode {
    TICKET,
    RAG;

    public static ChatMode from(String value) {
        if (value == null || value.isBlank()) {
            return TICKET;
        }

        try {
            return ChatMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return TICKET;
        }
    }
}
