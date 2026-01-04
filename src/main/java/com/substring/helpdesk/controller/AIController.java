package com.substring.helpdesk.controller;

import com.substring.helpdesk.entity.Conversation;
import com.substring.helpdesk.repository.ConversationRepository;
import com.substring.helpdesk.service.AIService;
import com.substring.helpdesk.service.AudioToTextService;
import com.substring.helpdesk.service.SemanticCacheService;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
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
    private SemanticCacheService semanticCacheService;


    @PostMapping
    public ResponseEntity<String> addTicket(@RequestBody String uQuery, @RequestHeader("conversationId") String conversationId, @RequestHeader("userEmail") String email) {
        String response = aiService.chatResponse(uQuery,conversationId, email);
        return ResponseEntity.ok(response);
    }


    // 1. CHAT ENDPOINT: Handles sending messages and creating conversation metadata
    @PostMapping("/chat")
    public ResponseEntity<String> chat(
            @RequestBody String uQuery,
            @RequestHeader("conversationId") String conversationId,
            @RequestHeader("userEmail") String email) {


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
        String response = aiService.chatResponse(uQuery, conversationId, email);

        // setting the cache
        semanticCacheService.setCachedAnswer(email, uQuery, response, conversationId);

        return ResponseEntity.ok(response);

    }

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

    // Simple record for serialization
    public record ChatMessageDTO(String role, String content) {}

    //4. Audio to Text ENDPOINT
    @PostMapping("/transcribe")
    public ResponseEntity<String> convertAudioToText(@RequestHeader("audio") MultipartFile file) throws IOException {

        Resource audioResource = file.getResource();
        String text = audioToTextService.transcribe(audioResource);
        return ResponseEntity.ok(text);

    }




}
