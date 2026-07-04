package com.enterprise.ticketmaster.service;

import com.enterprise.ticketmaster.model.RoutingRule;
import com.enterprise.ticketmaster.model.Ticket;
import com.enterprise.ticketmaster.repository.RoutingRuleRepository;
import com.enterprise.ticketmaster.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoutingEngineService {

    private static final Logger log = LoggerFactory.getLogger(RoutingEngineService.class);

    private final RoutingRuleRepository ruleRepository;
    private final TicketRepository ticketRepository;

    public RoutingEngineService(RoutingRuleRepository ruleRepository, TicketRepository ticketRepository) {
        this.ruleRepository = ruleRepository;
        this.ticketRepository = ticketRepository;
    }

    public void routeTicket(Ticket ticket) {
        List<RoutingRule> rules = ruleRepository.findAllByOrderByRuleOrderAsc();

        for (RoutingRule rule : rules) {
            // Check if the rule's category matches (or if the rule ignores category)
            boolean categoryMatch = (rule.getCategory() == null) ||
                    (ticket.getCategory() != null && rule.getCategory().getId().equals(ticket.getCategory().getId()));

            // Check if the rule's priority matches (or if the rule ignores priority)
            boolean priorityMatch = (rule.getPriority() == null) ||
                    (rule.getPriority() == ticket.getPriority());

            // First matching rule wins
            if (categoryMatch && priorityMatch) {
                ticket.setAssignedTo(rule.getTargetQueue());
                if (rule.isAutoAssignEnabled()) {
                    autoAssignLeastLoadedAgent(ticket, rule);
                }
                return;
            }
        }

        // If no rules match, push it to a default manual triage queue
        ticket.setAssignedTo("DEFAULT_TRIAGE_QUEUE");
    }

    /** Picks the least-loaded agent from the rule's eligible pool and assigns the ticket directly to them. */
    private void autoAssignLeastLoadedAgent(Ticket ticket, RoutingRule rule) {
        List<String> eligible = rule.getEligibleAgentList();
        if (eligible.isEmpty()) {
            log.warn("Routing rule #{} has auto-assign enabled but no eligible agents configured — skipping auto-assign.", rule.getId());
            return;
        }

        Map<String, Long> workload = new HashMap<>();
        for (Object[] row : ticketRepository.findOpenTicketCountsByAgent()) {
            workload.put((String) row[0], ((Number) row[1]).longValue());
        }

        // Agents with zero open tickets simply won't appear in the workload query result — treat as 0, not "unknown"
        String leastLoaded = eligible.stream()
                .min((a, b) -> Long.compare(workload.getOrDefault(a, 0L), workload.getOrDefault(b, 0L)))
                .orElse(null);

        if (leastLoaded != null) {
            ticket.setAssignedAgent(leastLoaded);
            log.info("Auto-assigned ticket to '{}' via routing rule #{} (workload-based pick from {})",
                    leastLoaded, rule.getId(), eligible);
        }
    }
}