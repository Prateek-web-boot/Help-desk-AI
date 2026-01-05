package com.substring.helpdesk.service;

import com.substring.helpdesk.tools.EmailTool;
import com.substring.helpdesk.tools.TicketCreationTools;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;

import java.util.List;

@Service
@Getter
@Setter
public class AIService {

    private final ChatClient chatClient;

//    private final TicketCreationTools ticketCreationTools;
//
//    private final EmailTool emailTool;

    @Value("classpath:/helpdesk-prompt.st")
    private Resource systemPromptResource;


    @Qualifier("pgVectorStore")
    private final VectorStore neonCacheVectorStore;

    private ChatMemory chatMemory;


    private final McpSyncClient mcpSyncClient;


    public AIService(@Lazy ChatClient.Builder builder,
                     List<McpSyncClient> mcpClients,
                     @Qualifier("pgVectorStore") VectorStore neonVectorStore) {

        this.mcpSyncClient = mcpClients.get(0);
        this.neonCacheVectorStore = neonVectorStore;


        var mcpToolProvider = new SyncMcpToolCallbackProvider(mcpClients);

        this.chatClient = builder
                .defaultToolCallbacks(mcpToolProvider.getToolCallbacks())
                .defaultSystem("You are Brio. If you call a tool and get a result, " +
                        "always show that result to the user immediately.")
                .build();

    }


    @Retryable(
            retryFor = { Exception.class }, // Catches OpenAI 429s and connection timeouts
            maxAttempts = 3,
            backoff = @Backoff(
                    delay = 2000,      // Start with 2 seconds
                    multiplier = 2.0,  // Then 4 seconds, then 8 seconds
                    maxDelay = 10000   // Never wait more than 10 seconds
            )
    )
    public String chatResponse(String uQuery, String convoId, String userEmail) {

        String tweakedQuery = uQuery + " (Note: If the answer isn't in the provided context documents, check our chat history for the answer.)";

        String identityInstructions = String.format(
                "\n[IDENTITY CONTEXT]: The current user is logged in with email: %s. " +
                        "You already have this email. NEVER ask the user for their email address. " +
                        "Use this email automatically for all ticket searches and creations.",
                userEmail
        );

        return this.chatClient.prompt()
                .advisors(advisorSpec -> advisorSpec
                        .param(ChatMemory.CONVERSATION_ID, convoId)
                        .advisors(QuestionAnswerAdvisor.builder(neonCacheVectorStore)
                                .searchRequest(SearchRequest.builder()
                                        .similarityThreshold(0.97)
                                        .filterExpression("userEmail == '" + userEmail + "' && convoId == '" + convoId + "'")
                                        .topK(1)
                                        .build())
                                .build())
                )
//                .tools(ticketCreationTools, emailTool)
                .system(s-> s.text(systemPromptResource).params(java.util.Map.of("identity", identityInstructions)))
                .user(tweakedQuery)
                .call()
                .content();
    }



    // This method runs ONLY if all 3 retries fail
    @Recover
    public String recover(Exception e, String chatId, String userMessage) {
        System.err.println("All retries failed for " + chatId + ". Error: " + e.getMessage());
        return "I'm having trouble connecting to my brain (OpenAI) right now due to rate limits. ";
    }

}
