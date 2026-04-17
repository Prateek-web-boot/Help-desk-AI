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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Getter
@Setter
public class TicketService {

    private Logger logger = LoggerFactory.getLogger(TicketService.class);
    private static final double DUPLICATE_SIMILARITY_THRESHOLD = 0.55;
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "at", "be", "been", "but", "for", "from",
            "has", "have", "i", "in", "is", "it", "my", "of", "on", "or", "that",
            "the", "this", "to", "up", "was", "with", "you"
    );

    private final TicketRepository ticketRepository;
    private final Map<String, Ticket> duplicateTicketCache = new ConcurrentHashMap<>();

    @Transactional
    public TicketCreationOutcome createTicket(Ticket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket cannot be null");
        }

        String duplicateKey = buildDuplicateKey(ticket.getEmail(), ticket.getSummary(), ticket.getDescription());
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

        String duplicateKey = buildDuplicateKey(ticket.getEmail(), ticket.getSummary(), ticket.getDescription());
        duplicateTicketCache.put(duplicateKey, ticket);
    }

    private boolean isSameIssue(Ticket existingTicket, Ticket incomingTicket) {
        if (existingTicket == null || incomingTicket == null) {
            return false;
        }

        if (sameNormalizedIssue(existingTicket, incomingTicket)) {
            return true;
        }

        double similarity = issueSimilarity(existingTicket, incomingTicket);
        if (similarity >= DUPLICATE_SIMILARITY_THRESHOLD) {
            logger.info("Detected semantic duplicate with similarity {} between existing ticket {} and incoming ticket.",
                    similarity, existingTicket.getId());
            return true;
        }

        return false;
    }

    private boolean sameNormalizedIssue(Ticket existingTicket, Ticket incomingTicket) {
        String existingKey = buildDuplicateKey(existingTicket.getEmail(), existingTicket.getSummary(), existingTicket.getDescription());
        String incomingKey = buildDuplicateKey(incomingTicket.getEmail(), incomingTicket.getSummary(), incomingTicket.getDescription());
        return existingKey.equals(incomingKey);
    }

    private String buildDuplicateKey(String email, String summary, String description) {
        return normalize(email) + "|" + normalizeIssueText(summary, description);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeIssueText(String summary, String description) {
        return String.join(" ", Arrays.asList(summary, description).stream()
                .filter(v -> v != null && !v.isBlank())
                .map(this::canonicalizeText)
                .toList());
    }

    private double issueSimilarity(Ticket existingTicket, Ticket incomingTicket) {
        Set<String> existingTokens = tokenizeIssue(existingTicket);
        Set<String> incomingTokens = tokenizeIssue(incomingTicket);

        if (existingTokens.isEmpty() || incomingTokens.isEmpty()) {
            return 0.0;
        }

        int matches = 0;
        for (String token : existingTokens) {
            if (containsApproximateToken(incomingTokens, token)) {
                matches++;
            }
        }

        Set<String> union = new HashSet<>(existingTokens);
        union.addAll(incomingTokens);
        return union.isEmpty() ? 0.0 : (double) matches / union.size();
    }

    private Set<String> tokenizeIssue(Ticket ticket) {
        Set<String> tokens = new HashSet<>();
        addTokens(tokens, ticket.getSummary());
        addTokens(tokens, ticket.getDescription());
        addTokens(tokens, ticket.getCategory());
        return tokens;
    }

    private void addTokens(Set<String> tokens, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        for (String rawToken : canonicalizeText(value).split(" ")) {
            String token = rawToken.trim();
            if (!token.isBlank() && !STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }
    }

    private boolean containsApproximateToken(Set<String> tokens, String token) {
        if (tokens.contains(token)) {
            return true;
        }

        for (String candidate : tokens) {
            if (levenshteinDistance(candidate, token) <= 2) {
                return true;
            }
        }
        return false;
    }

    private String canonicalizeText(String value) {
        return normalize(value)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\b(heating|heated|heater|heat|hot|warm|warming|overheating|overheat|overheated)\\b", "overheat")
                .replaceAll("\\b(slow|slowing|lag|lagging|freezing|frozen)\\b", "performance_issue")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int levenshteinDistance(String left, String right) {
        if (left == null || right == null) {
            return Integer.MAX_VALUE;
        }
        if (left.equals(right)) {
            return 0;
        }
        if (left.isEmpty()) {
            return right.length();
        }
        if (right.isEmpty()) {
            return left.length();
        }

        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];

        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost
                );
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }

        return previous[right.length()];
    }


}
