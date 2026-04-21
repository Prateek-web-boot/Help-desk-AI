package com.substring.helpdesk.entity;

public record ChatRequestDTO(String uQuery, ChatMode mode, String project, boolean allowFileTools) {

    public ChatRequestDTO {
        mode = mode == null ? ChatMode.TICKET : mode;
        project = project == null ? "" : project.trim();
    }

    public boolean isRagMode() {
        return mode == ChatMode.RAG;
    }

    public boolean hasProject() {
        return project != null && !project.isBlank();
    }
}
