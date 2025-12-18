package com.substring.helpdesk.controller;

import com.substring.helpdesk.service.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class TicketController {

    @Autowired
    private AIService aiService;

    @PostMapping
    public ResponseEntity addTicket(@RequestBody String uQuery) {
        String response = aiService.chatResponse(uQuery);
        return ResponseEntity.ok(response);
    }
}
