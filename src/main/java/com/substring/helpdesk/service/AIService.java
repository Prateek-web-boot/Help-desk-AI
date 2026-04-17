package com.substring.helpdesk.service;

import com.substring.helpdesk.tools.EmailTool;
import com.substring.helpdesk.tools.TicketCreationTools;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
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

    @Qualifier("companyDocsVectorStore")
    private final VectorStore companyDocsVectorStore;

    private final SemanticCacheService semanticCacheService;

    @Value("classpath:/helpdesk-prompt.st")
    private Resource systemPromptResource;

    @Qualifier("conversationVectorStore")
    private final VectorStore conversationVectorStore;

    public AIService(ChatClient chatClient,
                     @Qualifier("conversationVectorStore") VectorStore conversationVectorStore,
                     @Qualifier("companyDocsVectorStore") VectorStore companyDocsVectorStore,
                     SemanticCacheService semanticCacheService,
                     TicketCreationTools ticketCreationTools,
                     EmailTool emailTool) {

        this.chatClient = chatClient;
        this.conversationVectorStore = conversationVectorStore;
        this.companyDocsVectorStore = companyDocsVectorStore;
        this.semanticCacheService = semanticCacheService;
        this.ticketCreationTools = ticketCreationTools;
        this.emailTool = emailTool;
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
        log.debug("Received chat request for conversationId={} and userEmail={}", convoId, userEmail);

        String cachedAnswer = semanticCacheService.getCachedAnswer(userEmail, uQuery, convoId);
        if (cachedAnswer != null) {
            log.debug("Semantic cache hit for conversationId={} and userEmail={}", convoId, userEmail);
            conversationVectorStore.add(List.of(
                    new Document(uQuery, Map.of(
                            "userEmail", userEmail,
                            "convoId", convoId,
                            "response", cachedAnswer
                    ))
            ));
            return cachedAnswer;
        }

        log.debug("Semantic cache miss for conversationId={} and userEmail={}", convoId, userEmail);

        String identityInstructions = String.format(
                "[IDENTITY CONTEXT]: user email is %s. Never ask again.",
                userEmail
        );

        //2. Multi RAG + LLM
        String response = this.chatClient.prompt()
                .advisors(advisorSpec -> advisorSpec
                        .param(org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID, convoId)
                        .advisors(

                                //Company Docs RAG
                                QuestionAnswerAdvisor.builder(companyDocsVectorStore)
                                        .searchRequest(SearchRequest.builder()
                                                .similarityThreshold(0.8)
                                                .topK(3)
                                                .build())
                                        .build(),


                                //User Cache Content( optional Context)
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
                .user(uQuery)
                .call()
                .content();

        if (uQuery.length() > 10 && !response.contains("I don't know")) {
            semanticCacheService.setCachedAnswer(userEmail, uQuery, response, convoId);
        }

        return response;
    }

    @Recover
    public String recover(Exception e, String chatId, String userMessage) {
        log.error("All retries failed for conversationId={}", chatId, e);
        return "I'm having trouble connecting to my brain (OpenAI) right now due to rate limits.";
    }
}
