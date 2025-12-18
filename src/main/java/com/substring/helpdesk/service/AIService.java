package com.substring.helpdesk.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AIService {

    private final ChatClient chatClient;

    public String chatResponse(String uQuery) {
        return this.chatClient.prompt()
                .user(uQuery)
                .call()
                .content();
    }

}
