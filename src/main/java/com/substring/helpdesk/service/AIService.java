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
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Getter
@Setter
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    private final ChatClient chatClient;
    private final TicketCreationTools ticketCreationTools;
    private final EmailTool emailTool;
    private final List<ToolCallbackProvider> mcpToolCallbackProviders;

    @Qualifier("companyDocsVectorStore")
    private final VectorStore companyDocsVectorStore;

    private final SemanticCacheService semanticCacheService;

    @Value("classpath:/helpdesk-prompt.st")
    private Resource systemPromptResource;

    @Value("classpath:/helpdesk-rag-prompt.st")
    private Resource ragSystemPromptResource;

    @Qualifier("conversationVectorStore")
    private final VectorStore conversationVectorStore;

    public AIService(ChatClient chatClient,
                     @Qualifier("conversationVectorStore") VectorStore conversationVectorStore,
                     @Qualifier("companyDocsVectorStore") VectorStore companyDocsVectorStore,
                     SemanticCacheService semanticCacheService,
                     TicketCreationTools ticketCreationTools,
                     EmailTool emailTool,
                     List<ToolCallbackProvider> mcpToolCallbackProviders) {

        this.chatClient = chatClient;
        this.conversationVectorStore = conversationVectorStore;
        this.companyDocsVectorStore = companyDocsVectorStore;
        this.semanticCacheService = semanticCacheService;
        this.ticketCreationTools = ticketCreationTools;
        this.emailTool = emailTool;
        this.mcpToolCallbackProviders = mcpToolCallbackProviders;
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

        String cachedAnswer = semanticCacheService.getCachedAnswer(userEmail, uQuery, convoId, ChatMode.TICKET);
        if (cachedAnswer != null) {
            log.debug("Semantic cache hit for conversationId={} and userEmail={} in ticket mode", convoId, userEmail);
            conversationVectorStore.add(List.of(
                    new Document(uQuery, Map.of(
                            "userEmail", userEmail,
                            "convoId", convoId,
                            "mode", ChatMode.TICKET.name(),
                            "response", cachedAnswer
                    ))
            ));
            return cachedAnswer;
        }

        String identityInstructions = String.format(
                "[IDENTITY CONTEXT]: user email is %s. Never ask again.",
                userEmail
        );

        String response = this.chatClient.prompt()
                .advisors(advisorSpec -> advisorSpec
                        .param(org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID, convoId)
                        .advisors(
                                QuestionAnswerAdvisor.builder(conversationVectorStore)
                                        .searchRequest(SearchRequest.builder()
                                                .similarityThreshold(0.85)
                                                .filterExpression("userEmail == '" + userEmail + "'")
                                                .topK(3)
                                                .build())
                                        .build()
                        ))
                .system(s -> s.text(systemPromptResource)
                        .params(Map.of("identity", identityInstructions)))
                .tools(ticketCreationTools, emailTool)
                .toolCallbacks(mcpToolCallbackProviders.toArray(new ToolCallbackProvider[0]))
                .user(uQuery)
                .call()
                .content();

        if (uQuery.length() > 10 && response != null && !response.isBlank() && !response.contains("I don't know")) {
            semanticCacheService.setCachedAnswer(userEmail, uQuery, response, convoId, ChatMode.TICKET);
        }

        return response;
    }

    private String ragResponse(String uQuery, String convoId, String userEmail, String project) {
        log.debug("Received RAG-mode chat request for conversationId={} and userEmail={}", convoId, userEmail);

        String cachedAnswer = semanticCacheService.getCachedAnswer(userEmail, uQuery, convoId, ChatMode.RAG);
        if (cachedAnswer != null) {
            log.debug("Semantic cache hit for conversationId={} and userEmail={} in RAG mode", convoId, userEmail);
            conversationVectorStore.add(List.of(
                    new Document(uQuery, Map.of(
                            "userEmail", userEmail,
                            "convoId", convoId,
                            "mode", ChatMode.RAG.name(),
                            "response", cachedAnswer
                    ))
            ));
            return cachedAnswer;
        }

        String identityInstructions = String.format(
                "[IDENTITY CONTEXT]: user email is %s. Use it only for personalization and cache scoping.",
                userEmail
        );

        String response = this.chatClient.prompt()
                .advisors(advisorSpec -> advisorSpec
                        .param(org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID, convoId)
                        .advisors(
                                QuestionAnswerAdvisor.builder(companyDocsVectorStore)
                                        .searchRequest(buildCompanyDocsSearchRequest(project))
                                        .build(),
                                QuestionAnswerAdvisor.builder(conversationVectorStore)
                                        .searchRequest(SearchRequest.builder()
                                                .similarityThreshold(0.85)
                                                .filterExpression("userEmail == '" + userEmail + "'")
                                                .topK(3)
                                                .build())
                                        .build()
                        ))
                .system(s -> s.text(ragSystemPromptResource)
                        .params(Map.of("identity", identityInstructions)))
                .toolCallbacks(mcpToolCallbackProviders.toArray(new ToolCallbackProvider[0]))
                .user(uQuery)
                .call()
                .content();

        if (uQuery.length() > 10 && response != null && !response.isBlank() && !response.contains("I don't know")) {
            semanticCacheService.setCachedAnswer(userEmail, uQuery, response, convoId, ChatMode.RAG);
        }

        return response;
    }

    private SearchRequest buildCompanyDocsSearchRequest(String project) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .similarityThreshold(0.65)
                .topK(8);

        if (project == null || project.isBlank()) {
            return builder.build();
        }

        String safeProject = project.replace("'", "''");
        return builder
                .filterExpression("project == '" + safeProject + "'")
                .build();
    }

    @Recover
    public String recover(Exception e, String uQuery, String convoId, String userEmail, ChatMode mode) {
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
