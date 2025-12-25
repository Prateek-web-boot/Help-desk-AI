package com.substring.helpdesk.service;

import com.substring.helpdesk.entity.Conversation;
import com.substring.helpdesk.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;

    public List<Conversation> getConversationsByEmail(String email) {
        return conversationRepository.findByEmailOrderByUpdatedAtDesc(email);
    }

    @Transactional
    public Conversation saveOrUpdate(Conversation conversation) {
        return conversationRepository.save(conversation);
    }

    public boolean exists(String id) {
        return conversationRepository.existsById(id);
    }

}
