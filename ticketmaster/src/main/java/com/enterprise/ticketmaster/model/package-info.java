/**
 * JPA entities and plain data-carrier classes (DTOs, request/response records).
 *
 * Conventions:
 *   - One class per file, matching the table/concept name (e.g. Ticket, Category).
 *   - Enums (Status, Priority, NotificationType, ...) live here too, not in a
 *     separate "enums" package — they're small and tightly coupled to one entity.
 *   - Entities expose plain getters/setters, not Lombok — kept explicit on
 *     purpose so IDE-navigation and diffs stay simple for newcomers.
 *   - A few classes here (AgentPerformanceDto, TicketSuggestRequest/Response)
 *     are NOT JPA entities — they're view models returned by controllers.
 *     Don't confuse these with persisted entities when tracing a bug.
 */
package com.enterprise.ticketmaster.model;
