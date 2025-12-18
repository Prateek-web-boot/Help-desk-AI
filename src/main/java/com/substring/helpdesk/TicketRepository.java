package com.substring.helpdesk;

import com.substring.helpdesk.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    //Optional<Ticket> findByTicketId(@Param("ticketId") String ticketId);
    Optional<Ticket> findByUsername(@Param("username") String username);
}
