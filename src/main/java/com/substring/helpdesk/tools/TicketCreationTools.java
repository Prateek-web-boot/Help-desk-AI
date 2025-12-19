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

    @Tool(description = "Retrieve ticket details using the user's email address")
    public String getTicketByEmail(@ToolParam(description = "The user's email address") String email) {
        logger.info("Invoking tool getTicketByEmail for: {}", email);

        // Fetch the entity from the service
        Ticket tt = ticketService.getTicketByEmail(email.trim());
        logger.info("] tool getTicketByEmail Data: {}", tt);

        if (tt == null) {
            return "I'm sorry, I couldn't find any ticket associated with the email: " + email;
        }

        // IMPORTANT: Manually build the string here.
        // This forces JPA to load the data from Neon before the tool returns.
        return String.format(
                "Found Ticket Details:\n" +
                        "- ID: %d\n" +
                        "- Summary: %s\n" +
                        "- Status: %s\n" +
                        "- Category: %s\n" +
                        "- Priority: %s\n" +
                        "- Description: %s\n" +
                        "- Last Updated: %s",
                tt.getId(),
                tt.getSummary() != null ? tt.getSummary() : "N/A",
                tt.getStatus() != null ? tt.getStatus() : "OPEN",
                tt.getCategory() != null ? tt.getCategory() : "General",
                tt.getPriority() != null ? tt.getPriority() : "Medium",
                tt.getDescription() != null ? tt.getDescription() : "No description provided",
                tt.getUpdatedAt() != null ? tt.getUpdatedAt().toString() : "N/A"
        );
    }



    // get current system time
    @Tool(description = "This tool helps to get current system time.")
    public String getCurrentTime() {
        return String.valueOf(System.currentTimeMillis());
    }
}
