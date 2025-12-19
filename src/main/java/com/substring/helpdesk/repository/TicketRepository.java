package com.substring.helpdesk.repository;

import com.substring.helpdesk.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByEmailIgnoreCase(String email);
    //List<Ticket> findByEmailIgnoreCase(@Param("email") String email);
}
