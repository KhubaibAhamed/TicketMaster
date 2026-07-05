package com.enterprise.ticketmaster.service;

import com.enterprise.ticketmaster.model.Status;
import com.enterprise.ticketmaster.model.Ticket;
import com.enterprise.ticketmaster.repository.TicketRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TriageAssistService {

    private final TicketRepository ticketRepository;

    public TriageAssistService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /** Basic keyword-overlap duplicate check. Upgrades to semantic/embedding-based matching later, once LLM integration is wired in. */
    public List<Ticket> findLikelyDuplicates(Ticket ticket) {
        Set<String> queryTokens = tokenize(ticket.getTitle() + " " + ticket.getDescription());
        if (queryTokens.isEmpty()) return List.of();

        List<Status> openStatuses = List.of(Status.OPEN, Status.IN_PROGRESS, Status.PENDING);

        return ticketRepository.findAllWithDetails().stream()
                .filter(t -> !t.getId().equals(ticket.getId()))
                .filter(t -> openStatuses.contains(t.getStatus()))
                .map(t -> Map.entry(t, overlapScore(queryTokens, tokenize(t.getTitle() + " " + t.getDescription()))))
                .filter(e -> e.getValue() >= 3) // require meaningful overlap, not one shared common word
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private int overlapScore(Set<String> a, Set<String> b) {
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return intersection.size();
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9]+"))
                .filter(w -> w.length() >= 3)
                .collect(Collectors.toSet());
    }
}