/**
 * Spring Data JPA repositories — one per entity, following the entity's name
 * (TicketRepository for Ticket, etc.). No business logic belongs here.
 *
 * Conventions:
 *   - Prefer Spring Data derived query methods (findByXxx) for simple lookups.
 *   - Use @Query (JPQL by default; nativeQuery = true only when JPQL genuinely
 *     can't express it, e.g. TIMESTAMPDIFF in reporting queries) for anything
 *     more complex — see TicketRepository / CommentRepository for examples.
 *   - Native queries can return java.sql.Timestamp instead of LocalDateTime
 *     depending on JDBC driver — always normalize with an instanceof check in
 *     the calling service/controller rather than casting directly (a cast bug
 *     here caused a real 500 error in Reports once — see ReportController).
 */
package com.enterprise.ticketmaster.repository;
