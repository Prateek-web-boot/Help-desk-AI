package com.substring.helpdesk.service;

import com.substring.helpdesk.TicketRepository;
import com.substring.helpdesk.entity.Ticket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;


    // create ticket
    public void createTicket(Ticket ticket) {

        ticketRepository.save(ticket);
    }

    //update ticket

    public void updateTicket(Ticket ticket) {
        Optional<Ticket> existingTicket= ticketRepository.findById(ticket.getId());
        existingTicket.ifPresent(ticketRepository::save);
    }

    //find ticket by id
    public Ticket getTicket(Long id) {
        return ticketRepository.findById(id).orElse(null);
    }

    //find ticket by username
    public Ticket getTicketByUsername(String username) {
        return ticketRepository.findByUsername(username).orElse(null);
    }


}
