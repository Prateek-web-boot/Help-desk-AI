package com.substring.helpdesk.service;

import com.substring.helpdesk.tools.TicketCreationTools;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;

@Service
@RequiredArgsConstructor
@Getter
@Setter
public class AIService {

    private final ChatClient chatClient;

    private final TicketCreationTools ticketCreationTools;

    @Value("classpath:/helpdesk-prompt.st")
    private Resource systemPromptResource;

    private ChatMemory chatMemory;

    public String chatResponse(String uQuery) {
        return this.chatClient.prompt()
                .tools(ticketCreationTools)
                .system(systemPromptResource)
                .user(uQuery)
                .call()
                .content();
    }

}
