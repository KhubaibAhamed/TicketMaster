package com.enterprise.ticketmaster.event;

import com.enterprise.ticketmaster.model.Notification;
import com.enterprise.ticketmaster.model.NotificationType;
import com.enterprise.ticketmaster.model.Ticket;
import com.enterprise.ticketmaster.repository.NotificationRepository;
import com.enterprise.ticketmaster.repository.TicketRepository;
import com.enterprise.ticketmaster.repository.UserRepository;
import com.enterprise.ticketmaster.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final com.enterprise.ticketmaster.repository.TicketRepository ticketRepository;

    @Value("${app.mail.notify:}")
    private String notifyEmail;

    public NotificationEventListener(NotificationRepository notificationRepository,
                                     @org.springframework.beans.factory.annotation.Autowired(required = false)
                                     EmailService emailService,
                                     UserRepository userRepository,
                                     TicketRepository ticketRepository) {
        this.notificationRepository = notificationRepository;
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
    }

    @EventListener
    public void handleTicketEvent(TicketEvent event) {
        Ticket ticket = event.getTicket();
        Notification notification = new Notification();
        notification.setType(event.getType());
        notification.setRelatedTicketId(ticket.getId());

        Long ticketId  = ticket.getId();
        String title   = ticket.getTitle();
        String assignedAgent = ticket.getAssignedAgent();

        switch (event.getType()) {
            case TICKET_CREATED -> {
                notification.setMessage("New ticket #" + ticketId + " raised: " + title);
                // Intake alert — nobody's personally responsible for it yet, so this goes to admins for oversight
                notification.setRecipientRole("ADMIN");

                if (emailService != null && !notifyEmail.isBlank()) {
                    emailService.sendTicketCreated(notifyEmail, ticketId, title);
                }
                // Confirmation to the person who raised it — distinct wording/tone from the admin alert above
                String raisedByUsername = ticket.getRaisedBy();
                if (emailService != null && raisedByUsername != null && !raisedByUsername.isBlank()) {
                    userRepository.findByUsername(raisedByUsername).ifPresent(user -> {
                        String raiserEmail = user.getEmail();
                        if (raiserEmail != null && !raiserEmail.isBlank()) {
                            emailService.sendTicketReceived(raiserEmail, ticketId, title);
                        }
                    });
                }
            }
            case SLA_WARNING -> {
                notification.setMessage("⚠️ SLA warning on ticket #" + ticketId
                        + ": 25% of time remaining — " + title);
                assignOrEscalateToAdmin(notification, assignedAgent);

                if (emailService != null && !notifyEmail.isBlank()) {
                    emailService.sendSlaWarning(notifyEmail, ticketId, title);
                }
            }
            case SLA_CRITICAL -> {
                notification.setMessage("🔴 SLA critical on ticket #" + ticketId
                        + ": 10% of time remaining — " + title);
                assignOrEscalateToAdmin(notification, assignedAgent);

                if (emailService != null && !notifyEmail.isBlank()) {
                    emailService.sendSlaCritical(notifyEmail, ticketId, title);
                }
            }
            case SLA_BREACHED -> {
                notification.setMessage("SLA breached on ticket #" + ticketId + ": " + title);
                assignOrEscalateToAdmin(notification, assignedAgent);

                if (emailService != null && !notifyEmail.isBlank()) {
                    emailService.sendSlaBreached(notifyEmail, ticketId, title);
                }
            }
            case TICKET_ASSIGNED -> {
                notification.setMessage("Ticket #" + ticketId + " has been assigned to you: " + title);
                notification.setRecipientUsername(assignedAgent);

                if (assignedAgent != null && !assignedAgent.isBlank()) {
                    userRepository.findByUsername(assignedAgent).ifPresent(user -> {
                        String agentEmail = user.getEmail();
                        if (emailService != null && agentEmail != null && !agentEmail.isBlank()) {
                            emailService.sendTicketAssignmentEmail(agentEmail, ticket);
                        }
                    });
                }
            }
            case TICKET_RESOLVED -> {
                notification.setMessage("Ticket #" + ticketId + " resolved: " + title);
                notification.setRecipientUsername(ticket.getRaisedBy());

                String raisedByUsername = ticket.getRaisedBy();
                if (emailService != null && raisedByUsername != null && !raisedByUsername.isBlank()) {
                    userRepository.findByUsername(raisedByUsername).ifPresent(user -> {
                        String raiserEmail = user.getEmail();
                        if (raiserEmail != null && !raiserEmail.isBlank()) {
                            if (ticket.getCsatToken() == null) {
                                ticket.setCsatToken(java.util.UUID.randomUUID().toString());
                                ticketRepository.save(ticket);
                            }
                            emailService.sendTicketResolved(raiserEmail, ticketId, title, ticket.getResolvedBy(), ticket.getCsatToken());
                        }
                    });
                }
            }
            case TICKET_REOPENED -> {
                notification.setMessage("Ticket #" + ticketId + " reopened: " + title);
                assignOrEscalateToAdmin(notification, assignedAgent);
            }
            case TICKET_AUTO_CLOSED -> {
                notification.setMessage("Ticket #" + ticketId + " was automatically closed after 3 days in Resolved status.");
                notification.setRecipientUsername(ticket.getRaisedBy());
            }
            case ESCALATION_TRIGGERED -> {
                notification.setMessage("🚨 Ticket #" + ticketId + " escalated after SLA breach — priority now "
                        + ticket.getPriority() + ": " + title);
                String contact = event.getEscalationContact();
                if (contact != null && !contact.isBlank()) {
                    notification.setRecipientUsername(contact);
                    userRepository.findByUsername(contact).ifPresent(user -> {
                        String contactEmail = user.getEmail();
                        if (emailService != null && contactEmail != null && !contactEmail.isBlank()) {
                            emailService.sendEscalation(contactEmail, ticketId, title, ticket.getPriority().toString());
                        }
                    });
                } else {
                    notification.setRecipientRole("ADMIN");
                }
            }
            case TICKET_MERGED -> {
                notification.setMessage("Ticket #" + ticketId + " was identified as a duplicate and merged.");
                notification.setRecipientUsername(ticket.getRaisedBy());
            }
        }

        notificationRepository.save(notification);
    }

    // Shared targeting rule: if someone's actually assigned to the ticket, it's their notification.
    // If nobody's assigned, fall back to an admin broadcast so it doesn't disappear into nowhere.
    private void assignOrEscalateToAdmin(Notification notification, String assignedAgent) {
        if (assignedAgent != null && !assignedAgent.isBlank()) {
            notification.setRecipientUsername(assignedAgent);
        } else {
            notification.setRecipientRole("ADMIN");
        }
    }
}