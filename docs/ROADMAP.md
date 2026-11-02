# Paygenix Roadmap

Paygenix is a consumer wallet (backend first, frontend later): virtual cards, internal transfers, top-ups and promo stories.
This roadmap orders the work by risk. Security and money correctness come before new features.

## Milestones

| Milestone | Goal | Tag |
|---|---|---|
| 1. Stop the bleeding | Manual-only deploy, no secrets in logs, live instance stopped | — |
| 2. Secret hygiene | Configuration only from the environment, secret scanning in CI and pre-commit | — |
| 3. Foundation | Integration test harness (Testcontainers), CI on every PR, Flyway baseline with `ddl-auto=validate` | — |
| 4. Critical security | Token type separation, RBAC, card ownership, safe transfers and payments, row locking | — |
| 5. High-severity security & auth model | Rate limiting, hardened errors and CORS, password login, 6-digit transaction PIN with lockout, per-device sessions, Google OAuth via one-time code exchange | — |
| 6. Stabilization | Latest Spring Boot 4.0.x, springdoc 3.x, current stripe-java, quality tooling | v0.1.0 |
| 7. Money core | Double-entry ledger, configurable fees, idempotency keys, payment state machine, Stripe sandbox top-up, phone top-up adapter | — |
| 8. Flows | KYC with manual admin review, admin API, promo stories, paginated history, `/api/v1` | — |
| 9. Quality & docs | Clean architecture refactors, observability, complete OpenAPI, portfolio README | v0.2.0 |
| 10. Product | Frontend (including simple admin UI). Started early, before milestones 8–9 are finished | v0.3.0 |
| 11. Release | Deployment preparation and a fresh, hardened server | v1.0.0 |

> **Before the repository is made public:** ask GitHub Support to run a sensitive data removal for the `refs/pull/*` refs. They still point to commits from before the history rewrite, and only GitHub can purge them and their cached views.

## Key decisions

- **Virtual cards only.** The system generates Luhn-valid card numbers and stores only the last four digits. No CVV is stored.
- **Double-entry ledger.** Every money movement is a set of debit and credit entries that sum to zero. Commission (2%) goes to a fee revenue account. Cashback (0.5%) is funded by a marketing expense account. Rates come from configuration. Stripe funds pass through a clearing account. Rounding is HALF_EVEN to 2 decimals.
- **Top-ups through Stripe (sandbox).** The balance is credited only after a signature-verified, idempotently processed webhook, never from the client response.
- **Phone top-up through a provider adapter**, with a mock provider for success, failure and timeout cases.
- **No currency exchange.** Transfers between cards in different currencies are rejected with a clear error code.
- **KYC by manual admin review.** Documents are stored in a private bucket and served through presigned URLs.
- **Separate credentials.** A password is used for login. A 6-digit PIN confirms transactions and has an attempt limit.
- **Breaking API changes are allowed.** All endpoints move under `/api/v1`.
- **Schema managed by Flyway**, starting from a clean baseline.
- **Frontend before the docs milestone.** Frontend work started on 2026-11-03, while milestone 8 (flows) and milestone 9 (docs) are still open. Backend gaps that a screen needs are closed before that screen is built (docs/frontend/screens.md).
- **No separate design phase.** There is no Figma step. The design system is built in code first (light/dark color tokens, typography, spacing, core components), then the screens flow by flow, reviewed in the browser. Stack: docs/adr/0001-frontend-stack.md.

## Out of scope

- Currency exchange (FX)
- GitHub OAuth
- Social follow graph for stories
- Real mobile operator integration for phone top-up (the adapter keeps this open)
