package com.substring.helpdesk.service;

import com.substring.helpdesk.repository.TicketRepository;
import com.substring.helpdesk.entity.Ticket;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Getter
@Setter
public class TicketService {

    private Logger logger = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;
    private final Map<String, Ticket> duplicateTicketCache = new ConcurrentHashMap<>();

    @Transactional
    public TicketCreationOutcome createTicket(Ticket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket cannot be null");
        }

        String duplicateKey = buildDuplicateKey(ticket.getEmail(), ticket.getDescription());
        Ticket cachedTicket = duplicateTicketCache.get(duplicateKey);
        if (cachedTicket != null) {
            logger.info("Returning cached ticket for duplicate issue: {}", duplicateKey);
            return new TicketCreationOutcome(cachedTicket, true);
        }

        List<Ticket> existingTickets = getTicketByEmail(ticket.getEmail());
        for (Ticket existingTicket : existingTickets) {
            if (isSameIssue(existingTicket, ticket)) {
                logger.info("Returning existing ticket instead of creating duplicate: {}", existingTicket.getId());
                duplicateTicketCache.putIfAbsent(duplicateKey, existingTicket);
                return new TicketCreationOutcome(existingTicket, true);
            }
        }

        ticket.setId(null);
        Ticket saved = ticketRepository.save(ticket);
        duplicateTicketCache.put(duplicateKey, saved);
        return new TicketCreationOutcome(saved, false);
    }

    //update ticket
    @Transactional
    public Ticket updateTicket(Ticket ticket) {
        Ticket saved = ticketRepository.save(ticket);
        refreshDuplicateCache(saved);
        return saved;
    }

    //find ticket by id
    public Ticket getTicket(Long id) {
        return ticketRepository.findById(id).orElse(null);
    }

    //find ticket by email
    public List<Ticket> getTicketByEmail(String email) {
        logger.info("getTicketByEmail Service method called: " + email);
        List<Ticket> tt = ticketRepository.findByEmailIgnoreCase(email.trim());
        logger.info("getTicketByEmail Service method called Data: " + tt);
        return tt;
    }

    private void refreshDuplicateCache(Ticket ticket) {
        if (ticket == null || ticket.getEmail() == null) {
            return;
        }

        String duplicateKey = buildDuplicateKey(ticket.getEmail(), ticket.getDescription());
        duplicateTicketCache.put(duplicateKey, ticket);
    }

    private boolean isSameIssue(Ticket existingTicket, Ticket incomingTicket) {
        if (existingTicket == null || incomingTicket == null) {
            return false;
        }

        String existingKey = buildDuplicateKey(existingTicket.getEmail(), existingTicket.getDescription());
        String incomingKey = buildDuplicateKey(incomingTicket.getEmail(), incomingTicket.getDescription());
        return existingKey.equals(incomingKey);
    }

    private String buildDuplicateKey(String email, String description) {
        return normalize(email) + "|" + normalize(description);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }


}
