package com.enterprise.ticketmaster.service;

import com.enterprise.ticketmaster.event.TicketEvent;
import com.enterprise.ticketmaster.model.Category;
import com.enterprise.ticketmaster.model.NotificationType;
import com.enterprise.ticketmaster.model.Priority;
import com.enterprise.ticketmaster.model.Ticket;
import com.enterprise.ticketmaster.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class EscalationService {
    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);

    private final TicketRepository ticketRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EscalationService(TicketRepository ticketRepository, ApplicationEventPublisher eventPublisher) {
        this.ticketRepository = ticketRepository;
        this.eventPublisher = eventPublisher;
    }

    public void escalate(Ticket ticket) {
        Category category = ticket.getCategory();
        boolean shouldBump = category == null || category.isAutoBumpPriorityOnBreach();

        if (shouldBump) {
            Priority before = ticket.getPriority();
            Priority after = before.bump();
            if (after != before) {
                ticket.setPriority(after);
                ticketRepository.save(ticket);
                log.info("Escalation: Ticket #{} priority bumped {} -> {}", ticket.getId(), before, after);
            }
        }

        String escalationContact = category != null ? category.getEscalationContactUsername() : null;
        eventPublisher.publishEvent(new TicketEvent(ticket, NotificationType.ESCALATION_TRIGGERED, escalationContact));
    }
}