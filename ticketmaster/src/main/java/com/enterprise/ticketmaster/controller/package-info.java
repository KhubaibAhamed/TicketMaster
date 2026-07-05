/**
 * REST controllers — one per resource/feature area, named after what they
 * expose (TicketController -> /api/tickets, SlaConfigController -> /api/sla-config).
 *
 * Conventions:
 *   - Controllers validate input and delegate to a service; they should not
 *     contain business logic themselves (if you're writing an `if` that
 *     decides *what happens*, not just *whether the request is well-formed*,
 *     it probably belongs in a service instead).
 *   - Role checks that can't be expressed declaratively in SecurityConfig
 *     (e.g. "ADMIN or DEVELOPER but not USER") are done inline via
 *     Authentication.getAuthorities() — see TriageController/KnowledgeArticleController
 *     for the pattern.
 *   - Every new endpoint needs a matching rule in config/SecurityConfig.java —
 *     there's no default-allow; unmatched paths are denied by default.
 */
package com.enterprise.ticketmaster.controller;
