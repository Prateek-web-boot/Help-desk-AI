package com.substring.helpdesk.controller;

import com.substring.helpdesk.entity.ChatMessageDTO;
import com.substring.helpdesk.entity.ChatMode;
import com.substring.helpdesk.entity.ChatRequestDTO;
import com.substring.helpdesk.entity.Conversation;
import com.substring.helpdesk.repository.ConversationRepository;
import com.substring.helpdesk.service.AIService;
import com.substring.helpdesk.service.AudioToTextService;
import com.substring.helpdesk.service.ProjectCatalogService;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/helpdesk")
public class AIController {

    @Autowired
    private AIService aiService;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private JdbcChatMemoryRepository jdbcChatMemoryRepository;
    @Autowired
    private AudioToTextService audioToTextService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProjectCatalogService projectCatalogService;




    @PostMapping
    public ResponseEntity<String> addTicket(@RequestBody String requestBody, @RequestHeader("conversationId") String conversationId, @RequestHeader("userEmail") String email) {
        ChatRequestDTO request = parseChatRequest(requestBody);
        String response = aiService.chatResponse(request.uQuery(), conversationId, email, request.mode(), request.project(), request.allowFileTools());
        return ResponseEntity.ok(response);
    }


    // 1. CHAT ENDPOINT: Handles sending messages and creating conversation metadata
    @PostMapping("/chat")
    public ResponseEntity<String> chat(
            @RequestBody String requestBody,
            @RequestHeader("conversationId") String conversationId,
            @RequestHeader("userEmail") String email) {

        ChatRequestDTO request = parseChatRequest(requestBody);
        String uQuery = request.uQuery();

        // If this is the first message in a new session, create the sidebar entry
        if (!conversationRepository.existsById(conversationId)) {
            String title = uQuery.length() > 40 ? uQuery.substring(0, 40) + "..." : uQuery;
            Conversation newConvo = Conversation.builder()
                    .id(conversationId)
                    .email(email)
                    .title(title)
                    .updatedAt(LocalDateTime.now())
                    .build();
            conversationRepository.save(newConvo);
        }

        // Get AI Response using your AIService
        String response = aiService.chatResponse(uQuery, conversationId, email, request.mode(), request.project(), request.allowFileTools());

        return ResponseEntity.ok(response);

    }


/*
    @GetMapping("/chat/formatted")
    public List<Player> getOutputinCertainFormat(@RequestParam String sportName) {

        BeanOutputConverter<List<Player>> converter =
                new BeanOutputConverter<>(new ParameterizedTypeReference<List<Player>>() {

        });

        String message = """
    Generate a list of Career Achievements for the sport {sportName} for top 3 players.
    Each player should have at least two achievements in {format}.
    """;

        PromptTemplate template  = new PromptTemplate(message);

        Prompt prompt = template.create(Map.of("sportName", sportName, "format", converter.getFormat()));

//        ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
//
//        return response.getResult().getOutput().getContent();

        Generation result =  chatClient.prompt(prompt).call().chatResponse()
                .getResult();

        return converter.convert(result.getOutput().getText());





    }*/

/*
    @PostMapping("/chat/git")
    public ResponseEntity<String> chatWithGit(@RequestParam("query") String query) {

        String response = chatClient.prompt(query).call().content();

        return  ResponseEntity.ok(response);
    }*/


    // 2. SIDEBAR ENDPOINT: Gets list of all chats for the logged-in user
    @GetMapping("/conversations")
    public ResponseEntity<List<Conversation>> getHistory(@RequestParam String email) {
        return ResponseEntity.ok(conversationRepository.findByEmailOrderByUpdatedAtDesc(email));
    }

    // 3. HISTORY ENDPOINT: Gets actual messages for a specific clicked chat
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<ChatMessageDTO>> getChatMessages(@PathVariable String conversationId) {
        // 1.1.2 uses findByConversationId
        var messages = jdbcChatMemoryRepository.findByConversationId(conversationId);

        // Map to DTO because the Message interface cannot be serialized to JSON directly
        List<ChatMessageDTO> dtoList = messages.stream()
                .map(m -> new ChatMessageDTO(
                        m.getMessageType().name().toLowerCase(),
                        m.getText()
                ))
                .toList();

        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/projects")
    public ResponseEntity<List<String>> getProjects() {
        return ResponseEntity.ok(projectCatalogService.getAvailableProjects());
    }

    //4. Audio to Text ENDPOINT
    @PostMapping("/transcribe")
    public ResponseEntity<String> convertAudioToText(@RequestHeader("audio") MultipartFile file) throws IOException {

        Resource audioResource = file.getResource();
        String text = audioToTextService.transcribe(audioResource);
        return ResponseEntity.ok(text);

    }

    private ChatRequestDTO parseChatRequest(String requestBody) {
        if (requestBody == null) {
            return new ChatRequestDTO("", ChatMode.TICKET, "", false);
        }

        String trimmed = requestBody.trim();
        if (trimmed.isEmpty()) {
            return new ChatRequestDTO("", ChatMode.TICKET, "", false);
        }

        if (trimmed.startsWith("{")) {
            try {
                JsonNode root = objectMapper.readTree(trimmed);
                JsonNode queryNode = root.get("uQuery");
                if (queryNode == null) {
                    queryNode = root.get("query");
                }
                JsonNode modeNode = root.get("mode");
                if (modeNode == null) {
                    modeNode = root.get("chatMode");
                }
                JsonNode projectNode = root.get("project");
                if (projectNode == null) {
                    projectNode = root.get("docProject");
                }
                JsonNode fileToolsNode = root.get("allowFileTools");
                if (fileToolsNode == null) {
                    fileToolsNode = root.get("fileTools");
                }
                if (fileToolsNode == null) {
                    fileToolsNode = root.get("enableFileTools");
                }
                if (queryNode != null && !queryNode.isNull()) {
                    boolean allowFileTools = parseBooleanLike(fileToolsNode);
                    return new ChatRequestDTO(
                            queryNode.asText("").trim(),
                            ChatMode.from(modeNode != null ? modeNode.asText(null) : null),
                            projectNode != null && !projectNode.isNull() ? projectNode.asText("") : "",
                            allowFileTools
                    );
                }
            } catch (Exception ignored) {
                // fall through to raw text handling
            }
        }

        return new ChatRequestDTO(trimmed, ChatMode.TICKET, "", false);
    }

    private boolean parseBooleanLike(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }

        if (node.isBoolean()) {
            return node.asBoolean(false);
        }

        if (node.isTextual()) {
            String value = node.asText("").trim().toLowerCase();
            return value.equals("true")
                    || value.equals("1")
                    || value.equals("yes")
                    || value.equals("on")
                    || value.equals("filetools")
                    || value.equals("file-tools")
                    || value.equals("enabled");
        }

        return node.asBoolean(false);
    }




}
