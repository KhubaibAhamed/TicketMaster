package com.enterprise.ticketmaster.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import com.enterprise.ticketmaster.model.*;
import com.enterprise.ticketmaster.repository.TicketRepository;
import com.enterprise.ticketmaster.repository.TicketCustomFieldValueRepository;
import com.enterprise.ticketmaster.repository.CustomFieldDefinitionRepository;
import com.enterprise.ticketmaster.service.RoutingEngineService;
import com.enterprise.ticketmaster.service.SlaManagementService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.enterprise.ticketmaster.exception.ResourceNotFoundException;
import com.enterprise.ticketmaster.event.TicketEvent;
import com.enterprise.ticketmaster.repository.CategoryRepository;
import com.enterprise.ticketmaster.model.Priority;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import com.enterprise.ticketmaster.repository.TicketLinkRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.enterprise.ticketmaster.repository.TicketHistoryRepository;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final RoutingEngineService routingEngine;
    private final SlaManagementService slaEngine;
    private final CategoryRepository categoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TicketHistoryRepository ticketHistoryRepository;
    private final TicketLinkRepository ticketLinkRepository;
    private final TicketCustomFieldValueRepository customFieldValueRepository;
    private final CustomFieldDefinitionRepository customFieldDefinitionRepository;

    public TicketService(TicketRepository ticketRepository, RoutingEngineService routingEngine,
                         SlaManagementService slaEngine, CategoryRepository categoryRepository,
                         ApplicationEventPublisher eventPublisher,
                         TicketHistoryRepository ticketHistoryRepository,
                         TicketLinkRepository ticketLinkRepository,
                         TicketCustomFieldValueRepository customFieldValueRepository,
                         CustomFieldDefinitionRepository customFieldDefinitionRepository) {
        this.ticketRepository = ticketRepository;
        this.routingEngine = routingEngine;
        this.slaEngine = slaEngine;
        this.categoryRepository = categoryRepository;
        this.eventPublisher = eventPublisher;
        this.ticketHistoryRepository = ticketHistoryRepository;
        this.ticketLinkRepository = ticketLinkRepository;
        this.customFieldValueRepository = customFieldValueRepository;
        this.customFieldDefinitionRepository = customFieldDefinitionRepository;
    }

    public Ticket createTicket(Ticket ticket) {
        if (ticket.getCategory() != null && ticket.getCategory().getId() != null) {
            Category category = categoryRepository.findById(ticket.getCategory().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + ticket.getCategory().getId()));
            ticket.setCategory(category);
        }
        routingEngine.routeTicket(ticket);
        slaEngine.assignSla(ticket);
        // Once routed to a queue, the ticket is no longer "fresh/unassigned" — it's OPEN
        ticket.setStatus(Status.OPEN);
        Ticket saved = ticketRepository.save(ticket);
        eventPublisher.publishEvent(new TicketEvent(saved, NotificationType.TICKET_CREATED));

        if (saved.getAssignedAgent() != null && !saved.getAssignedAgent().isBlank()) {
            eventPublisher.publishEvent(new TicketEvent(saved, NotificationType.TICKET_ASSIGNED));
        }
        if (ticket.getCustomFieldValues() != null && !ticket.getCustomFieldValues().isEmpty()) {
            ticket.getCustomFieldValues().forEach((fieldDefId, value) -> {
                customFieldDefinitionRepository.findById(fieldDefId).ifPresent(fieldDef -> {
                    TicketCustomFieldValue fieldValue = new TicketCustomFieldValue();
                    fieldValue.setTicketId(saved.getId());
                    fieldValue.setFieldDefinitionId(fieldDefId);
                    fieldValue.setFieldName(fieldDef.getFieldName());
                    fieldValue.setValue(value);
                    customFieldValueRepository.save(fieldValue);
                });
            });
        }
        return saved;
    }

    // Fetch all records from the database table
    public List<Ticket> getAllTickets() {
        return ticketRepository.findAllWithDetails();
    }

    public Page<Ticket> getTicketsPaginated(int page, int size) {
        return ticketRepository.findAllPaginated(PageRequest.of(page, size));
    }

    public Page<Ticket> searchTickets(String q, String statusStr, String priorityStr,
                                      Long categoryId, String raisedBy, String assignedAgent,
                                      String sortParam, int page, int size) {
        if (q == null) q = "";
        Status status     = (statusStr   != null && !statusStr.isBlank())   ? Status.valueOf(statusStr)     : null;
        Priority priority = (priorityStr != null && !priorityStr.isBlank()) ? Priority.valueOf(priorityStr) : null;

        Long numericQ = null;
        try {
            if (!q.isBlank()) numericQ = Long.parseLong(q.trim().replace("#", ""));
        } catch (NumberFormatException ignored) {}

        String raisedByParam      = (raisedBy      != null && !raisedBy.isBlank())      ? raisedBy      : null;
        String assignedAgentParam = (assignedAgent  != null && !assignedAgent.isBlank()) ? assignedAgent : null;

        // Previously, a request with zero filters (q/status/priority/category/
        // raisedBy/assignedAgent all empty — which is exactly an ADMIN's
        // default, unfiltered Operations Console view) took a shortcut to
        // getTicketsPaginated(), which builds its PageRequest with no Sort at
        // all, silently discarding whatever the sort dropdown was set to.
        // Developer/User views usually have a filter active (assigned-to-me,
        // raised-by-me) so they never hit that shortcut, which is why sorting
        // looked like it "worked for dev but not admin." Always go through
        // the query that actually honors the requested Sort.
        Sort sort = parseSort(sortParam);
        return ticketRepository.searchWithFiltersSorted(q, numericQ, status, priority,
                categoryId, raisedByParam, assignedAgentParam, PageRequest.of(page, size, sort));
    }

    private Sort parseSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) return Sort.by(Sort.Direction.DESC, "id");
        String[] parts = sortParam.split(",");
        String field = parts[0].trim();
        Sort.Direction dir = parts.length > 1 && parts[1].trim().equals("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, field);
    }

    public Ticket getTicketById(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + id));

        List<TicketCustomFieldValue> values = customFieldValueRepository.findByTicketId(ticket.getId());
        Map<Long, String> valueMap = new HashMap<>();
        values.forEach(v -> valueMap.put(v.getFieldDefinitionId(), v.getValue()));
        ticket.setCustomFieldValues(valueMap);

        return ticket;
    }

    public List<Ticket> getTicketsByStatus(Status status) {
        return ticketRepository.findByStatusWithDetails(status);
    }

    public Ticket updateTicket(Long id, Ticket updatedTicketDetails, String resolvedBy) {
        return ticketRepository.findById(id).map(existingTicket -> {

            if (updatedTicketDetails.getCategory() != null && updatedTicketDetails.getCategory().getId() != null) {
                Category realCategory = categoryRepository.findById(updatedTicketDetails.getCategory().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
                updatedTicketDetails.setCategory(realCategory);
            }

            Status oldStatus = existingTicket.getStatus();
            Priority oldPriority = existingTicket.getPriority();
            String oldAssignedTo = existingTicket.getAssignedTo();
            Category oldCategory = existingTicket.getCategory();
            Status newStatus = updatedTicketDetails.getStatus();

            existingTicket.setTitle(updatedTicketDetails.getTitle());
            existingTicket.setDescription(updatedTicketDetails.getDescription());
            existingTicket.setStatus(newStatus);

            if (newStatus == Status.RESOLVED) {
                existingTicket.setResolvedAt(LocalDateTime.now());
            } else if (newStatus == Status.REOPENED) {
                existingTicket.setResolvedAt(null);
            }

            // SLA pause/resume: time spent waiting on the customer shouldn't count against the deadline
            if (newStatus == Status.PENDING && oldStatus != Status.PENDING) {
                // Entering a pause — record when it started
                existingTicket.setPausedAt(LocalDateTime.now());
            } else if (oldStatus == Status.PENDING && newStatus != Status.PENDING && existingTicket.getPausedAt() != null) {
                // Resuming — push the deadline back by exactly how long it was paused
                Duration pausedDuration = Duration.between(existingTicket.getPausedAt(), LocalDateTime.now());
                if (existingTicket.getSlaDueDate() != null) {
                    existingTicket.setSlaDueDate(existingTicket.getSlaDueDate().plus(pausedDuration));
                }
                existingTicket.setPausedAt(null);
            }

            // Business logic lives here: if resolved, record who signed off
            if (Status.RESOLVED.equals(newStatus) && resolvedBy != null) {
                existingTicket.setResolvedBy(resolvedBy);
            }

            // --- Audit history recording ---
            LocalDateTime now = LocalDateTime.now();

            if (updatedTicketDetails.getStatus() != null && updatedTicketDetails.getStatus() != oldStatus) {
                TicketHistory h = new TicketHistory();
                h.setTicketId(id);
                h.setChangedBy(resolvedBy);
                h.setChangedAt(now);
                h.setFieldName("status");
                h.setOldValue(oldStatus != null ? oldStatus.name() : null);
                h.setNewValue(updatedTicketDetails.getStatus().name());
                ticketHistoryRepository.save(h);
            }

            if (updatedTicketDetails.getPriority() != null && updatedTicketDetails.getPriority() != oldPriority) {
                TicketHistory h = new TicketHistory();
                h.setTicketId(id);
                h.setChangedBy(resolvedBy);
                h.setChangedAt(now);
                h.setFieldName("priority");
                h.setOldValue(oldPriority != null ? oldPriority.name() : null);
                h.setNewValue(updatedTicketDetails.getPriority().name());
                ticketHistoryRepository.save(h);
            }

            if (updatedTicketDetails.getCategory() != null && oldCategory != null
                    && !updatedTicketDetails.getCategory().getId().equals(oldCategory.getId())) {
                TicketHistory h = new TicketHistory();
                h.setTicketId(id);
                h.setChangedBy(resolvedBy);
                h.setChangedAt(now);
                h.setFieldName("category");
                h.setOldValue(oldCategory.getName());
                h.setNewValue(updatedTicketDetails.getCategory().getName());
                ticketHistoryRepository.save(h);
            }

            if (updatedTicketDetails.getAssignedTo() != null
                    && !updatedTicketDetails.getAssignedTo().equals(oldAssignedTo)) {
                TicketHistory h = new TicketHistory();
                h.setTicketId(id);
                h.setChangedBy(resolvedBy);
                h.setChangedAt(now);
                h.setFieldName("assignedTo");
                h.setOldValue(oldAssignedTo);
                h.setNewValue(updatedTicketDetails.getAssignedTo());
                ticketHistoryRepository.save(h);
            }
// --- End audit history ---

            Ticket saved = ticketRepository.save(existingTicket);

            if (newStatus == Status.REOPENED && oldStatus != Status.REOPENED) {
                eventPublisher.publishEvent(new TicketEvent(saved, NotificationType.TICKET_REOPENED));
            }
            if (newStatus == Status.RESOLVED && oldStatus != Status.RESOLVED) {
                eventPublisher.publishEvent(new TicketEvent(saved, NotificationType.TICKET_RESOLVED));
            }

            return saved;
        }).orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + id));
    }

    public void deleteTicket(Long id) {
        if (!ticketRepository.existsById(id)) {
            throw new ResourceNotFoundException("Ticket not found with id: " + id);
        }
        ticketRepository.deleteById(id);
    }

    public Ticket saveTicket(Ticket ticket) {
        return ticketRepository.save(ticket);
    }

    public Ticket assignAgent(Long id, String agent, String actor) {
        Ticket ticket = getTicketById(id);
        String previousAgent = ticket.getAssignedAgent();
        ticket.setAssignedAgent(agent);
        Ticket saved = ticketRepository.save(ticket);

        if (agent != null && !agent.equals(previousAgent)) {
            TicketHistory h = new TicketHistory();
            h.setTicketId(id);
            h.setChangedBy(actor);
            h.setChangedAt(LocalDateTime.now());
            h.setFieldName("assignedAgent");
            h.setOldValue(previousAgent != null ? previousAgent : "Unassigned");
            h.setNewValue(agent);
            ticketHistoryRepository.save(h);
            eventPublisher.publishEvent(new TicketEvent(saved, NotificationType.TICKET_ASSIGNED));
        }

        return saved;
    }

    @Transactional
    public Ticket mergeTicket(Long duplicateId, Long primaryId, String username) {
        if (duplicateId.equals(primaryId)) {
            throw new IllegalArgumentException("A ticket cannot be merged into itself.");
        }
        Ticket duplicate = getTicketById(duplicateId);
        Ticket primary = getTicketById(primaryId);

        Comment noteOnPrimary = new Comment();
        noteOnPrimary.setTicket(primary);
        noteOnPrimary.setContent("🔗 Merged duplicate ticket #" + duplicateId + " (\"" + duplicate.getTitle() + "\") into this ticket.");
        noteOnPrimary.setInternal(true);
        noteOnPrimary.setAuthorUsername(username);
        primary.getComments().add(noteOnPrimary);

        Comment noteOnDuplicate = new Comment();
        noteOnDuplicate.setTicket(duplicate);
        noteOnDuplicate.setContent("🔗 This ticket was identified as a duplicate and merged into #" + primaryId + " (\"" + primary.getTitle() + "\").");
        noteOnDuplicate.setInternal(true);
        noteOnDuplicate.setAuthorUsername(username);
        duplicate.getComments().add(noteOnDuplicate);

        duplicate.setStatus(Status.CLOSED);

        if (!ticketLinkRepository.linkExists(duplicateId, primaryId)) {
            TicketLink link = new TicketLink();
            link.setSourceTicketId(duplicateId);
            link.setTargetTicketId(primaryId);
            link.setLinkType(TicketLink.LinkType.DUPLICATES);
            link.setCreatedBy(username);
            link.setCreatedAt(LocalDateTime.now());
            ticketLinkRepository.save(link);
        }

        ticketRepository.save(primary);
        Ticket savedDuplicate = ticketRepository.save(duplicate);

        eventPublisher.publishEvent(new TicketEvent(savedDuplicate, NotificationType.TICKET_MERGED));
        return savedDuplicate;
    }
}