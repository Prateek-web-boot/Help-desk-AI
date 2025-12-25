package com.substring.helpdesk.service;

import com.substring.helpdesk.repository.TicketRepository;
import com.substring.helpdesk.entity.Ticket;
import com.substring.helpdesk.tools.TicketCreationTools;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Getter
@Setter
public class TicketService {

    private Logger logger = LoggerFactory.getLogger(TicketService.class);


    private final TicketRepository ticketRepository;


    // create ticket
    @Transactional
    public Ticket createTicket(Ticket ticket) {
        ticket.setId(null);
        return ticketRepository.save(ticket);
    }

    //update ticket
    @Transactional
    public Ticket updateTicket(Ticket ticket) {
        return ticketRepository.save(ticket);
    }

    //find ticket by id
    public Ticket getTicket(Long id) {
        return ticketRepository.findById(id).orElse(null);
    }

    //find ticket by email
    public List<Ticket> getTicketByEmail(String email) {
        logger.info("getTicketByEmail Service method called: " + email);
        List<Ticket> tt =  ticketRepository.findByEmailIgnoreCase(email.trim());
        logger.info("getTicketByEmail Service method called Data: " + tt);
        return tt;
    }


}
