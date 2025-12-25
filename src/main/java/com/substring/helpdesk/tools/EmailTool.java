package com.substring.helpdesk.tools;

import com.substring.helpdesk.service.TicketService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class EmailTool {

    private TicketService ticketService;

    @Tool(description = "this too,l heps to trigger an email to supoprt team after a ticket has been created")
    public String sendEmail(String email, String subject, String body) {

        return  "";
    }
}
