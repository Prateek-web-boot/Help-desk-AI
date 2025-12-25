package com.substring.helpdesk.repository;

import com.substring.helpdesk.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, String> {

    // Finds all chats for a user, showing the most recent first
    List<Conversation> findByEmailOrderByUpdatedAtDesc(String email);

}
