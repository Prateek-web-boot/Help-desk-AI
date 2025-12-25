package com.substring.helpdesk.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageDTO {
    private String role;    // "user", "assistant", or "system"
    private String content; // The actual message text
}