package com.substring.helpdesk.service;

import com.substring.helpdesk.entity.Ticket;

public record TicketCreationOutcome(Ticket ticket, boolean duplicate) {
}
