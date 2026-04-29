package com.substring.helpdesk.service;

import com.substring.helpdesk.entity.ChatMode;
import com.substring.helpdesk.tools.EmailTool;
import com.substring.helpdesk.tools.TicketCreationTools;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
@Getter
@Setter
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    private final ChatClient chatClient;
    private final TicketCreationTools ticketCreationTools;
    private final EmailTool emailTool;

    @Qualifier("companyDocsVectorStore")
    private final VectorStore companyDocsVectorStore;

    @Value("${helpdesk.rag.company-docs.similarity-threshold:0.60}")
    private double companyDocsSimilarityThreshold = 0.60d;

    @Value("${helpdesk.rag.company-docs.top-k:5}")
    private int companyDocsTopK = 5;

    @Value("classpath:/helpdesk-prompt.st")
    private Resource systemPromptResource;

    @Value("classpath:/helpdesk-rag-prompt.st")
    private Resource ragSystemPromptResource;

    public AIService(ChatClient chatClient,
                     @Qualifier("companyDocsVectorStore") VectorStore companyDocsVectorStore,
                     TicketCreationTools ticketCreationTools,
                     EmailTool emailTool) {

        this.chatClient = chatClient;
        this.companyDocsVectorStore = companyDocsVectorStore;
        this.ticketCreationTools = ticketCreationTools;
        this.emailTool = emailTool;
    }

    public String chatResponse(String uQuery, String convoId, String userEmail) {
        return chatResponse(uQuery, convoId, userEmail, ChatMode.TICKET, "");
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
    public String chatResponse(String uQuery, String convoId, String userEmail, ChatMode mode, String project) {
        ChatMode resolvedMode = mode == null ? ChatMode.TICKET : mode;

        return switch (resolvedMode) {
            case RAG -> ragResponse(uQuery, convoId, userEmail, project);
            case TICKET -> ticketResponse(uQuery, convoId, userEmail);
        };
    }

    public String chatResponse(String uQuery, String convoId, String userEmail, ChatMode mode) {
        return chatResponse(uQuery, convoId, userEmail, mode, "");
    }

    private String ticketResponse(String uQuery, String convoId, String userEmail) {
        log.debug("Received ticket-mode chat request for conversationId={} and userEmail={}", convoId, userEmail);

        String identityInstructions = String.format(
                "[IDENTITY CONTEXT]: user email is %s. Never ask again.",
                userEmail
        );

        var prompt = this.chatClient.prompt()
                .advisors(advisorSpec -> advisorSpec
                        .param(org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID, convoId))
                .system(s -> s.text(buildSystemPrompt(systemPromptResource, identityInstructions)))
                .tools(ticketCreationTools, emailTool);

        String response = prompt
                .user(uQuery)
                .call()
                .content();

        return response;
    }

    private String ragResponse(String uQuery, String convoId, String userEmail, String project) {
        log.debug("Received RAG-mode chat request for conversationId={} and userEmail={}", convoId, userEmail);

        String identityInstructions = String.format(
                "[IDENTITY CONTEXT]: user email is %s. Use it only for personalization.",
                userEmail
        );

        var prompt = this.chatClient.prompt()
                .advisors(advisorSpec -> advisorSpec
                        .param(org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID, convoId)
                        .advisors(
                                QuestionAnswerAdvisor.builder(companyDocsVectorStore)
                                        .searchRequest(buildCompanyDocsSearchRequest(project))
                                        .build()
                        ))
                .system(s -> s.text(buildSystemPrompt(ragSystemPromptResource, identityInstructions)));

        String response = prompt
                .user(uQuery)
                .call()
                .content();

        return response;
    }

    private SearchRequest buildCompanyDocsSearchRequest(String project) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .similarityThreshold(companyDocsSimilarityThreshold)
                .topK(companyDocsTopK);

        if (project == null || project.isBlank()) {
            return builder.build();
        }

        String safeProject = project.replace("'", "''");
        return builder
                .filterExpression("project == '" + safeProject + "'")
                .build();
    }

    private String buildSystemPrompt(Resource basePromptResource, String identityInstructions) {
        return readResource(basePromptResource).replace("{identity}", identityInstructions);
    }

    private String readResource(Resource resource) {
        try {
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read prompt resource: " + resource, e);
        }
    }

    @Recover
    public String recover(Exception e, String uQuery, String convoId, String userEmail, ChatMode mode, String project) {
        log.error(
                "All retries failed for conversationId={} and userEmail={} while processing query={} in mode={}",
                convoId,
                userEmail,
                uQuery,
                mode,
                e
        );
        return "I'm having trouble connecting to my brain (OpenAI) right now due to rate limits.";
    }
}
