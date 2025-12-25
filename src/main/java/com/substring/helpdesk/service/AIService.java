package com.substring.helpdesk.service;

import com.substring.helpdesk.tools.EmailTool;
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

    private final EmailTool emailTool;

    @Value("classpath:/helpdesk-prompt.st")
    private Resource systemPromptResource;

    private ChatMemory chatMemory;

    public String chatResponse(String uQuery, String convoId) {
        return this.chatClient.prompt()
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, convoId))
                .tools(ticketCreationTools, emailTool)
                .system(systemPromptResource)
                .user(uQuery)
                .call()
                .content();
    }

}
