package com.substring.helpdesk.entity;

public record TicketInput(
        String summary,
        String description,
        String category,
        String priority, // LOW, MEDIUM, HIGH, URGENT
        String email
) {}