package com.substring.helpdesk.tools;

import com.substring.helpdesk.repository.TicketRepository;
import com.substring.helpdesk.entity.Priority;
import com.substring.helpdesk.entity.Status;
import com.substring.helpdesk.entity.Ticket;
import com.substring.helpdesk.entity.TicketInput;
import com.substring.helpdesk.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
@RequiredArgsConstructor
public class TicketCreationTools {

    private Logger logger = LoggerFactory.getLogger(TicketCreationTools.class);

    private final TicketService ticketService;

    @Tool(description = "Create a new help desk ticket once email, summary, and description are known")
    public String createTicket(@ToolParam(description = "The details for the new ticket") TicketInput input) {
        Ticket ticket = Ticket.builder()
                .summary(input.summary())
                .description(input.description())
                .category(input.category())
                .priority(Priority.valueOf(input.priority().toUpperCase()))
                .email(input.email())
                .status(Status.OPEN)
                .build();

        Ticket saved = ticketService.createTicket(ticket);
        return "SUCCESS: Ticket #" + saved.getId() + " created for " + saved.getEmail();
    }

    @Tool(description = "this tools helps in update of a existing ticket in Database")
    public Ticket updateTicket(@ToolParam(description = "these ticket values are required to create a new ticket") Ticket ticket) {
        return ticketService.updateTicket(ticket);
    }

    @Tool(description = "Retrieve all ticket details associated with the user's email address")
    public String getTicketByEmail(@ToolParam(description = "The user's email address") String email) {
        logger.info("Invoking tool getTicketByEmail for: {}", email);

        List<Ticket> tickets = ticketService.getTicketByEmail(email.trim());

        if (tickets == null || tickets.isEmpty()) {
            return "I'm sorry, I couldn't find any tickets associated with the email: " + email;
        }

        StringBuilder sb = new StringBuilder("I found the following tickets for you:\n");
        for (Ticket t : tickets) {
            sb.append(String.format(
                    "--- Ticket #%d ---\n" +
                            "Summary: %s\n" +
                            "Status: %s\n" +
                            "Priority: %s\n" +
                            "Description: %s\n" +
                            "Last Updated: %s\n\n",
                    t.getId(),
                    t.getSummary(),
                    t.getStatus(),
                    t.getPriority(),
                    t.getDescription(),
                    t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : "N/A"
            ));
        }
        return sb.toString();
    }


    // get current system time
    @Tool(description = "This tool helps to get current system time.")
    public String getCurrentTime() {
        return String.valueOf(System.currentTimeMillis());
    }
}
