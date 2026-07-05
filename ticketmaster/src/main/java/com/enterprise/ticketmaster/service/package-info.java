/**
 * Business logic layer. This is where almost all real behavior lives —
 * controllers should stay thin (validate input, call a service, map the
 * result to a response) and repositories should stay dumb (just queries).
 *
 * A few services worth knowing about when onboarding, because they're
 * central and other services depend on them:
 *   - TicketService: the hub — ticket CRUD, merging, custom field values.
 *   - SlaManagementService: scheduled jobs (breach detection, warnings,
 *     auto-close) — look here first for anything SLA-timing related.
 *   - BusinessHoursCalculator: pure calculation, no I/O — computes SLA due
 *     dates/elapsed time respecting the admin's business-hours calendar.
 *   - RoutingEngineService: decides which queue/agent a new ticket goes to.
 *   - EscalationService: what happens the moment a ticket breaches SLA
 *     (priority bump + notify escalation contact).
 *
 * External integrations are isolated into their own thin wrapper services
 * (EmailService, LlmClientService if present, WebhookDispatchService) so the
 * business logic that calls them doesn't need to know the wire format.
 */
package com.enterprise.ticketmaster.service;
