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
import org.springframework.ai.document.Document;
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
import java.util.Map;

@Service
@Getter
@Setter
public class AIService {

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

    private ChatMemory chatMemory;



    public AIService(@Lazy ChatClient.Builder builder,
                     @Qualifier("conversationVectorStore") VectorStore conversationVectorStore,
                     @Qualifier("companyDocsVectorStore") VectorStore companyDocsVectorStore,
                     SemanticCacheService semanticCacheService,
                     TicketCreationTools ticketCreationTools,
                     EmailTool emailTool) {

        this.conversationVectorStore = conversationVectorStore;
        this.companyDocsVectorStore = companyDocsVectorStore;
        this.semanticCacheService = semanticCacheService;
        this.ticketCreationTools = ticketCreationTools;
        this.emailTool = emailTool;


        this.chatClient = builder
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

        System.out.println("QUERY: " + uQuery);


        String cachedAnswer = semanticCacheService.getCachedAnswer(userEmail,uQuery, convoId);

        if(cachedAnswer != null){
            System.out.println("CACHE HIT ❌: " + cachedAnswer);
        } else {
            System.out.println("CACHE MISS ✅");
        }

        if(cachedAnswer != null){
            // still store interaction
            conversationVectorStore.add(List.of(
                    new Document(uQuery, Map.of(
                            "userEmail", userEmail,
                            "convoId", convoId,
                            "response", cachedAnswer
                    ))
            ));
            return cachedAnswer;
        }

        String identityInstructions = String.format(
                "[IDENTITY CONTEXT]: user email is %s. Never ask again.",
                userEmail
        );

        //2. Multi RAG + LLM
        String response = this.chatClient.prompt()
                .advisors(advisorSpec -> advisorSpec
                        .param(ChatMemory.CONVERSATION_ID, convoId)
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
                                                .similarityThreshold(0.9)
                                                .filterExpression("userEmail == '" + userEmail + "'")
                                                .topK(1)
                                                .build())
                                        .build()
                        ))
                .system(s -> s.text(systemPromptResource)
                        .params(Map.of("identity", identityInstructions)))
                .tools(ticketCreationTools, emailTool)
                .user(uQuery)
                .call()
                .content();


        //3.save Cache
        if(uQuery.length() > 10 && !response.contains("I don't know")) {
            semanticCacheService.setCachedAnswer(userEmail, uQuery, response, convoId);
        }
        return response;




       /* String tweakedQuery = uQuery + " (Note: If the answer isn't in the provided context documents, check our chat history for the answer.)";

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
                .content();*/
    }



    // This method runs ONLY if all 3 retries fail
    @Recover
    public String recover(Exception e, String chatId, String userMessage) {
        System.err.println("All retries failed for " + chatId + ". Error: " + e.getMessage());
        return "I'm having trouble connecting to my brain (OpenAI) right now due to rate limits. ";
    }

}
