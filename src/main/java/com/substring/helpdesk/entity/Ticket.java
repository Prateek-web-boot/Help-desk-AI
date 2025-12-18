package com.substring.helpdesk.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Table(name = "help_desk_tickets")
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Lob
    private String summary;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    @Column(unique = true)
    private String username;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    private Status status;

    @PrePersist
    void preSave() {
        if(this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void postSave() {
        this.updatedAt = LocalDateTime.now();
    }
}
