package com.enterprise.ticketmaster.service;

import com.enterprise.ticketmaster.event.TicketEvent;
import com.enterprise.ticketmaster.model.NotificationType;
import com.enterprise.ticketmaster.model.SlaConfig;
import com.enterprise.ticketmaster.model.Status;
import com.enterprise.ticketmaster.model.Ticket;
import org.springframework.transaction.annotation.Transactional;
import com.enterprise.ticketmaster.repository.TicketRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class SlaManagementService {
    private static final Logger log = LoggerFactory.getLogger(SlaManagementService.class);

    private final TicketRepository ticketRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SlaConfigService slaConfigService;
    private final BusinessHoursCalculator businessHoursCalculator;
    private final EscalationService escalationService;
    private final WebhookDispatchService webhookDispatchService;

    public SlaManagementService(TicketRepository ticketRepository, ApplicationEventPublisher eventPublisher,
                                SlaConfigService slaConfigService, BusinessHoursCalculator businessHoursCalculator,
                                EscalationService escalationService, WebhookDispatchService webhookDispatchService) {
        this.ticketRepository = ticketRepository;
        this.eventPublisher = eventPublisher;
        this.slaConfigService = slaConfigService;
        this.businessHoursCalculator = businessHoursCalculator;
        this.escalationService = escalationService;
        this.webhookDispatchService = webhookDispatchService;
    }

    // Assigns the initial deadline based on severity, honoring the business-hours calendar if enabled
    public void assignSla(Ticket ticket) {
        LocalDateTime now = LocalDateTime.now();
        SlaConfig config = slaConfigService.getConfig();
        long hours = config.hoursFor(ticket.getPriority());
        ticket.setSlaDueDate(businessHoursCalculator.addBusinessHours(now, hours, config));
    }

    // The Background Worker: Runs every hour on the hour
    @Scheduled(cron = "0 0 * * * *")
    public void monitorSlaBreaches() {
        // We do NOT penalize agents if the ticket is waiting on the customer (PENDING)
        List<Status> excludedStatuses = Arrays.asList(Status.RESOLVED, Status.CLOSED, Status.PENDING);

        // Fetch all active tickets whose due date is in the past
        List<Ticket> breachedTickets = ticketRepository
                .findBySlaBreachedFalseAndStatusNotInAndSlaDueDateBefore(excludedStatuses, LocalDateTime.now());

        if (!breachedTickets.isEmpty()) {
            for (Ticket ticket : breachedTickets) {
                ticket.setSlaBreached(true);
                System.out.println("SLA BREACH DETECTED: Ticket ID " + ticket.getId() + " assigned to " + ticket.getAssignedTo());
            }
            // Save all the breached statuses to the database in one batch
            ticketRepository.saveAll(breachedTickets);
            for (Ticket ticket : breachedTickets) {
                eventPublisher.publishEvent(new TicketEvent(ticket, NotificationType.SLA_BREACHED));
                escalationService.escalate(ticket);
                webhookDispatchService.dispatch(
                        com.enterprise.ticketmaster.model.WebhookSubscription.WebhookEventType.SLA_BREACHED,
                        "🚨 SLA Breach — Ticket #" + ticket.getId() + ": " + ticket.getTitle()
                                + " (assigned to " + (ticket.getAssignedAgent() != null ? ticket.getAssignedAgent() : "unassigned") + ")"
                );
            }
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void monitorSlaThresholds() {
        List<Status> excludedStatuses = Arrays.asList(
                Status.RESOLVED, Status.CLOSED, Status.PENDING);

        List<Ticket> activeTickets = ticketRepository
                .findActiveTicketsWithSla(excludedStatuses);

        LocalDateTime now = LocalDateTime.now();
        SlaConfig config = slaConfigService.getConfig();

        for (Ticket ticket : activeTickets) {
            LocalDateTime due = ticket.getSlaDueDate();
            if (due == null) continue;

            // Total SLA duration from priority, in business minutes (already the "budget" used when the due date was set)
            long totalMinutes = config.hoursFor(ticket.getPriority()) * 60L;

            // Business-hours-aware elapsed/remaining calc — falls back to plain calendar time when disabled
            long minutesUsed = businessHoursCalculator.businessMinutesBetween(ticket.getCreatedAt(), now, config);
            long minutesRemaining = totalMinutes - minutesUsed;
            long percentRemaining = (minutesRemaining * 100) / totalMinutes;

            // CRITICAL — 10% or less remaining, not yet sent
            if (percentRemaining <= 10 && !ticket.isSlaCriticalSent()) {
                ticket.setSlaCriticalSent(true);
                ticketRepository.save(ticket);
                eventPublisher.publishEvent(
                        new TicketEvent(ticket, NotificationType.SLA_CRITICAL));
                log.warn("SLA CRITICAL: Ticket #{} — {}min remaining ({}% of SLA)",
                        ticket.getId(), minutesRemaining, percentRemaining);
            }
            // WARNING — 25% or less remaining, not yet sent
            else if (percentRemaining <= 25 && !ticket.isSlaWarningSent()) {
                ticket.setSlaWarningSent(true);
                ticketRepository.save(ticket);
                eventPublisher.publishEvent(
                        new TicketEvent(ticket, NotificationType.SLA_WARNING));
                log.info("SLA WARNING: Ticket #{} — {}min remaining ({}% of SLA)",
                        ticket.getId(), minutesRemaining, percentRemaining);
            }
        }
    }

    @Scheduled(fixedRate = 10_000)
    @Transactional
    public void autoCloseResolvedTickets() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(3);
        List<Ticket> candidates = ticketRepository.findAutoCloseCandidates(Status.RESOLVED, cutoff);

        if (candidates.isEmpty()) return;

        log.info("Auto-close scheduler: {} ticket(s) eligible for closure", candidates.size());

        for (Ticket ticket : candidates) {
            ticket.setStatus(Status.CLOSED);
            ticketRepository.save(ticket);
            eventPublisher.publishEvent(
                    new TicketEvent(ticket, NotificationType.TICKET_AUTO_CLOSED)
            );
        }
    }
}