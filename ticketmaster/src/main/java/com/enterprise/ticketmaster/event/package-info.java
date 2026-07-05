/**
 * Application-event plumbing for notifications. Services publish a TicketEvent
 * (a plain POJO, not a Spring ApplicationEvent subclass) via
 * ApplicationEventPublisher whenever something notification-worthy happens
 * (ticket created/assigned/resolved, SLA breached, escalated, merged, ...).
 *
 * NotificationEventListener is the single place that reacts to every
 * TicketEvent type and decides who gets notified (in-app notification row,
 * email, or both). If you add a new NotificationType, you MUST add a
 * matching case in NotificationEventListener's switch — the compiler won't
 * warn you if you forget, since NotificationType isn't sealed/exhaustive-checked.
 */
package com.enterprise.ticketmaster.event;
