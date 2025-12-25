package com.substring.helpdesk.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Conversation {
    @Id
    private String id; // This will be your UUID/convoId
    private String email; // Links the chat to a user
    private String title; // e.g., "Help with Spring Boot"
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}
