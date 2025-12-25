package com.substring.helpdesk.controller;

import com.substring.helpdesk.service.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/helpdesk")
public class AIController {

    @Autowired
    private AIService aiService;

    @PostMapping
    public ResponseEntity addTicket(@RequestBody String uQuery, @RequestHeader("convoId") String convoId) {
        String response = aiService.chatResponse(uQuery,convoId);
        return ResponseEntity.ok(response);
    }
}
