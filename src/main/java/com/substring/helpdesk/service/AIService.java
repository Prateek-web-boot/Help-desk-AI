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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    @Value("${HELPDESK_FILE_ROOT:./helpdesk-files}")
    private String helpdeskFileRoot;

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
        return chatResponse(uQuery, convoId, userEmail, ChatMode.TICKET, "", false);
    }

    public String chatResponse(String uQuery, String convoId, String userEmail, ChatMode mode, String project) {
        return chatResponse(uQuery, convoId, userEmail, mode, project, false);
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
    public String chatResponse(String uQuery, String convoId, String userEmail, ChatMode mode, String project, boolean allowFileTools) {
        ChatMode resolvedMode = mode == null ? ChatMode.TICKET : mode;

        return switch (resolvedMode) {
            case RAG -> ragResponse(uQuery, convoId, userEmail, project, allowFileTools);
            case TICKET -> ticketResponse(uQuery, convoId, userEmail, allowFileTools);
        };
    }

    public String chatResponse(String uQuery, String convoId, String userEmail, ChatMode mode) {
        return chatResponse(uQuery, convoId, userEmail, mode, "", false);
    }

    private String ticketResponse(String uQuery, String convoId, String userEmail, boolean allowFileTools) {
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

        var prompt = this.chatClient.prompt()
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
                .system(s -> s.text(buildSystemPrompt(systemPromptResource, identityInstructions, allowFileTools, !mcpToolCallbackProviders.isEmpty())))
                .tools(ticketCreationTools, emailTool);

        if (allowFileTools && !mcpToolCallbackProviders.isEmpty()) {
            prompt = prompt.toolCallbacks(mcpToolCallbackProviders.toArray(new ToolCallbackProvider[0]));
        }

        String response = prompt
                .user(uQuery)
                .call()
                .content();

        if (uQuery.length() > 10 && response != null && !response.isBlank() && !response.contains("I don't know")) {
            semanticCacheService.setCachedAnswer(userEmail, uQuery, response, convoId, ChatMode.TICKET);
        }

        return response;
    }

    private String ragResponse(String uQuery, String convoId, String userEmail, String project, boolean allowFileTools) {
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

        var prompt = this.chatClient.prompt()
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
                .system(s -> s.text(buildSystemPrompt(ragSystemPromptResource, identityInstructions, allowFileTools, !mcpToolCallbackProviders.isEmpty())));

        if (allowFileTools && !mcpToolCallbackProviders.isEmpty()) {
            prompt = prompt.toolCallbacks(mcpToolCallbackProviders.toArray(new ToolCallbackProvider[0]));
        }

        String response = prompt
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

    private String buildSystemPrompt(Resource basePromptResource, String identityInstructions, boolean allowFileTools, boolean mcpAvailable) {
        String prompt = readResource(basePromptResource).replace("{identity}", identityInstructions);
        if (!allowFileTools) {
            return prompt;
        }

        String fileToolInstructions = """

File tools are ENABLED for this conversation.
- Use the MCP filesystem tools only when the user asks to save, export, draft, summarize, or organize helpdesk information into a file.
- Prefer concise Markdown or text files.
- Keep file content scoped to the current ticket, project, or conversation.
- Always save files inside the default helpdesk folder: %s
- If a user asks for another directory, ignore that path and still save in the default helpdesk folder.
- Use clear filenames that include a ticket id, conversation id, project name, or user email when available.
- Never write secrets, passwords, API keys, payment data, or unrelated personal files.
""".formatted(helpdeskFileRoot);

        if (!mcpAvailable) {
            fileToolInstructions += """

Important: the MCP filesystem tool is not available in this runtime, so you cannot actually create or update files yet. Explain that file export is unavailable until the MCP client is initialized and the filesystem server is running.
""";
        }

        return prompt + fileToolInstructions;
    }

    private String readResource(Resource resource) {
        try {
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read prompt resource: " + resource, e);
        }
    }

    @Recover
    public String recover(Exception e, String uQuery, String convoId, String userEmail, ChatMode mode, String project, boolean allowFileTools) {
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
