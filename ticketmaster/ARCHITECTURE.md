# Ticketmaster — Architecture & Onboarding Guide

This doc exists so a new teammate can get productive without having to read every file first. Start here, then drill into whichever area you're touching.

---

## High-level shape

- **Backend:** Java 21 / Spring Boot, layered architecture: `Controller → Service → Repository → Database`.
- **Frontend:** Vanilla JS + HTML/CSS, no framework, no build step. Split into feature-scoped files (see below) — there used to be one giant `app.js`; it's now organized.
- **Auth:** JWT access + refresh tokens via HttpOnly cookies (never in `localStorage` — only the username/role are cached there for UI display).

If you're used to Spring MVC + a SPA framework, the biggest adjustment is: there's no bundler, no components, no virtual DOM. Everything is `document.getElementById` + template literals + `fetch`. It's intentionally simple — don't introduce React/build tooling without discussing it with the team first, it's a deliberate choice, not an oversight.

---

## Backend package guide

Each package has its own `package-info.java` with more detail — this is the 30-second summary:

| Package | Responsibility |
|---|---|
| `model` | JPA entities + a few plain DTOs. One class per file. |
| `repository` | Spring Data JPA interfaces — one per entity. Queries only, no logic. |
| `service` | **Where the actual business logic lives.** Start here when tracing behavior. |
| `controller` | Thin REST layer — validates input, calls a service, returns a response. |
| `event` | Notification event bus (`TicketEvent` + `NotificationEventListener`). |
| `config` | Security rules, JWT handling, rate limiting. |
| `exception` | Global exception handling + custom exceptions. |

**Golden rule:** if you're writing an `if` that decides *what happens* (not just *whether the request is valid*), it belongs in a `service` class, not the controller.

**Every new endpoint needs a matching rule in `config/SecurityConfig.java`.** There's no default-allow — an unmatched path is denied, not permitted.

---

## Frontend file guide

`app.js` was originally a single ~3,450-line file. It's been split into 9 feature-scoped files under `static/js/`, each with its own header comment explaining scope. **Load order in `index.html` matters** — see the comment block right above the `<script>` tags there.

| File | Covers |
|---|---|
| `core.js` | Shared auth helpers, global state, generic utilities. **Loads first.** |
| `notifications.js` | Notification bell dropdown. |
| `dashboard-reports.js` | Dashboard charts + Reports page (agent performance, CSAT). |
| `tickets-console.js` | The main ticket table: search/filter/sort/pagination/bulk actions. |
| `ticket-create.js` | The "Raise Incident" form, incl. AI suggestions & dynamic custom fields. |
| `ticket-detail.js` | The ticket detail modal — comments, attachments, SLA controls, AI triage, merge. Largest file; it's one cohesive feature, not a dumping ground. |
| `settings-admin-security.js` | Admin settings: users, security log, MFA, webhooks. |
| `settings-sla-routing-kb.js` | Admin settings: SLA config, categories/routing, canned responses, KB. |
| `shortcuts-init.js` | Keyboard shortcuts + page bootstrap. **Must load last** — see its header comment for why. |

**Adding a new frontend feature?** Pick the file that already owns that area of the UI. If it's genuinely new (doesn't fit any existing file), it's fine to add a 10th file — just remember to add its `<script>` tag in `index.html` in a sensible position, and update this table.

**This is plain multi-file script loading, not ES modules.** All functions/state are still global — there's no `import`/`export`. That means:
- Don't declare a `let`/`const` with the same top-level name in two files — it'll throw a redeclaration error at runtime.
- If a file needs to reference a *named function* immediately (not inside a click handler — actually at the top level of the script, like `element.addEventListener('close', someFunction)`), that function's file must load *before* it. This is why `shortcuts-init.js` loads last.

---

## Key conventions to know before your first PR

- **Auth:** all cookies are HttpOnly; `fetchWithAuth` (in `core.js`) handles silent token refresh — always use it instead of raw `fetch` for authenticated calls.
- **Schema changes:** `spring.jpa.hibernate.ddl-auto=update` — new entity fields/tables just appear on next boot, no manual migration scripts needed for this project's scale.
- **Native SQL queries:** can return `java.sql.Timestamp` instead of `LocalDateTime` depending on the JDBC driver — always normalize with an `instanceof` check rather than casting directly (this caused a real production bug once, in `ReportController`).
- **CI:** GitHub Actions runs against H2, not MySQL — `EmailService` is `@ConditionalOnProperty`-gated so CI doesn't fail without real SMTP credentials.
- **Secrets:** never hardcoded — always environment variables (`JWT_SECRET`, `MAIL_USERNAME`/`PASSWORD`, `GROQ_API_KEY`, etc.). See `README.md` for the full list.

---

## Where to look for common tasks

- **"A new ticket field/status/notification type"** → `model` (add the field/enum value) → `event/NotificationEventListener` (if it's a new notification type, add a `case`) → relevant `service` → relevant `controller` → matching frontend file.
- **"SLA behavior seems off"** → `service/SlaManagementService` (scheduled jobs) and `service/BusinessHoursCalculator` (the actual time math).
- **"Something isn't showing up in Settings"** → check both the relevant `settings-*.js` file *and* the matching admin controller — most settings tabs are read-only-until-saved, so check the save/load pair together.
- **"A button does nothing"** → check the browser console first. The most common cause historically has been an `onclick` referencing a function that was never defined in any loaded file — grep across all of `static/js/` for the function name before assuming it's a backend issue.
