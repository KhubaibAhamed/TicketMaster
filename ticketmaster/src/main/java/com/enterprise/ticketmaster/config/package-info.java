/**
 * Cross-cutting infrastructure config: Spring Security rules, JWT
 * generation/validation, the JWT auth filter, and login rate limiting.
 *
 * SecurityConfig is the single source of truth for "what can call what" —
 * every REST endpoint must have a matching requestMatchers(...) rule here.
 * When adding a new controller, come here next; there's no default-allow.
 */
package com.enterprise.ticketmaster.config;
