package com.enterprise.ticketmaster.event;

import com.enterprise.ticketmaster.model.NotificationType;
import com.enterprise.ticketmaster.model.Ticket;

// A plain POJO event — Spring has supported arbitrary objects as events since 4.2,
// no need to extend ApplicationEvent anymore.
public class TicketEvent {
    private final Ticket ticket;
    private final NotificationType type;
    private final String escalationContact; // optional override recipient, used by ESCALATION_TRIGGERED

    public TicketEvent(Ticket ticket, NotificationType type) {
        this(ticket, type, null);
    }

    public TicketEvent(Ticket ticket, NotificationType type, String escalationContact) {
        this.ticket = ticket;
        this.type = type;
        this.escalationContact = escalationContact;
    }

    public Ticket getTicket() { return ticket; }
    public NotificationType getType() { return type; }
    public String getEscalationContact() { return escalationContact; }
}