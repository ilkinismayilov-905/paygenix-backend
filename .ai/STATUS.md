# Paygenix status

Last updated: 2026-11-28 (deploy preparation PR). Roadmap and decisions: [`docs/ROADMAP.md`](../docs/ROADMAP.md).
Screens and their APIs: [`docs/frontend/screens.md`](../docs/frontend/screens.md).

> The earlier `.ai/MASTER_PROMPT.md`, `.ai/STATUS.md` and Ruflo memory were never committed and were lost with their
> session. This file was rebuilt from the git history, commit messages and code comments. Task numbers below are the
> ones found there; gaps in the numbering are tasks whose ids were not recorded.

## Milestones

| # | Milestone | State |
|---|---|---|
| 1–5 | Stop the bleeding, secret hygiene, foundation, critical and high-severity security | done (SEC-1…SEC-39 findings, T-016…T-122) |
| 6 | Stabilization (Boot 4.0.x, springdoc 3, stripe-java) | done |
| 7 | Money core: ledger, fees, idempotency, payment states, Stripe top-up, phone top-up adapter | done (mock operator; see open items) |
| 8 | Flows: KYC + admin review, admin API, promo stories, paginated history, `/api/v1` | done |
| 9 | Quality and docs: README, complete OpenAPI, observability | done: README, OpenAPI, metrics + request ids |
| 10 | Frontend, including a simple admin UI | done: every screen in `screens.md`, English + Azerbaijani |
| 11 | Release: deploy preparation, fresh hardened server | prepared (TLS edge, storage, runbook); the server, domains and keys are the owner's |

## Done (by task id)

Task ids found in the history and code: T-016, T-038, T-060, T-061, T-064, T-070, T-071, T-073, T-076, T-077, T-080,
T-082, T-083, T-085, T-092–T-098, T-100–T-102, T-106, T-109–T-118, T-121, T-122, T-126, T-129, T-130, T-132, T-135,
T-136, T-138, T-140. The latest ones:

| Task | What |
|---|---|
| T-064 | Card top-ups through Stripe Checkout; credited only by the signature-verified webhook, exactly once |
| T-070, T-071 | Identity verification: document upload to the private `kyc` bucket, admin approve / reject with reason |
| T-076 | Real client IP behind trusted proxies (Tomcat RemoteIpValve, `TRUSTED_PROXIES`) |
| T-129 | `GET /api/v1/fees`; transfer shows fee and total, pay shows cashback |
| T-132 | A payment not confirmed within 15 minutes expires (`PAYMENT_EXPIRED`, housekeeping job) |
| T-138, T-140 | Email-code lockout kept after 5 wrong tries; PIN mismatch checked before PIN_UNCHANGED |
| T-141 | Observability (M9): actuator on an unpublished management port (`MANAGEMENT_PORT`, guarded), Prometheus metrics with HTTP histograms, `paygenix_auth_logins_total`, `paygenix_ledger_postings_total`; request id from nginx in every log line and `X-Request-Id`; compose health check and a `monitoring` profile with Prometheus |
| T-142 | Per-address login limit: 30 attempts / 15 min per client IP (IPv6 per /64) across all emails, counted before the password check; a success gives its attempt back; `LOGIN_RATE_LIMITED` (429 + Retry-After); `LOGIN_IP_MAX_ATTEMPTS` |
| T-143 | Playwright E2E suite in `e2e/` against the real stack (sign-up with emailed code, sign in, transfer + fee, wrong PIN, phone pay + cashback + operator refusal, change PIN, admin resolves a report, admin lock/unlock); `e2e` CI job (API jar + `vite preview` + Postgres, Redis, Mailpit); Mailpit in compose (profiles `local`, `e2e`); `USERNAME_CHECK_IP_MAX_ATTEMPTS` configurable |
| T-144 | Phone payments: reserve (card → `PAYMENT_HOLD`, committed) → operator call with no transaction or row lock → settle (hold → payout + cashback) or release (`PAYMENT_RELEASE`); unknown answers stay `PROCESSING`, a retry re-asks with the same reference, `PaymentReconciliation` asks `status()` every minute after 2 min; confirm's idempotency key is claimed outside the long transaction (`executeInSteps`, 409 RESOURCE_BUSY while running); cards with a held payment cannot be closed; V20 |
| T-145 | Card issue: one open Idempotency-Key per currency, so a USD try between a lost AZN answer and its retry cannot issue a second AZN card |
| T-146 | Deploy preparation: Caddy TLS edge (profile `tls`, `ops/caddy/Caddyfile`), nginx takes client IP/scheme only from it (real_ip), SeaweedFS S3 storage + bucket init in compose (profiles `local`/`e2e`/`storage`) replacing the unpullable MinIO image, KYC upload E2E test with storage in CI, runbook `docs/DEPLOY.md` |

New tasks are numbered from T-141 on (the highest id found was T-140).

Done in PR #111 without a task id (number them if the old list had them):

- Screens: change email, signed-in devices, report a problem, Terms/Privacy pages, verify identity, profile photo,
  top up, promo stories (row + viewer), admin: identity reviews, promo stories, problem reports, users.
- Promo stories published by admins (the old user upload always failed); phone payments through an operator adapter
  with a mock (`...000` refused, `...999` times out, nothing charged).
- Admin API: user search, lock (signs out everywhere) / unlock; problem reports by status.
- Every endpoint under `/api/v1` (refresh cookie path too: users signed in once more after deploy).
- Azerbaijani translation with a language switch; sign-out goes home on the next sign-in.
- nginx web container (`frontend/Dockerfile`, `frontend/nginx/`) in compose; API published on 127.0.0.1 only.
- Fixes: problem report reference was dropped; `/me` returned the email as `username`; profile photos could never be
  shown; `User` updates write only changed columns (`@DynamicUpdate`); uploads checked by magic bytes; idempotency keys
  purged after 24 h; presigning needs no storage round trip (`MINIO_REGION`); released migrations are checksum-guarded.

## Unknown

- **T-139:** the session that closed T-138/T-140 planned it, but its text was in the lost `STATUS.md`. If the owner
  remembers it, record it here.

## Next (open)

1. **Deploy (M11)** — **blocked: needs a server (SSH access), two DNS names (`APP_DOMAIN`, `MEDIA_DOMAIN`), Stripe
   test keys and the Google OAuth client from the owner.** Everything else is ready (T-146): follow `docs/DEPLOY.md`,
   then rebuild `.github/workflows/deploy.yaml` (non-root user, the compose profiles, CI as a gate).
2. **Real phone operator adapter** — **blocked: needs an operator (or aggregator) contract, API docs and test
   credentials.** The flow is ready for it (T-144): implement `PhoneTopUpProvider.topUp` / `status` with the reference as
   the operator's idempotency key and an HTTP timeout well under 2 minutes.

## Decisions taken (beyond the roadmap)

- Stories are promos by admins, shown to everyone in one app-branded row; no user-published stories.
- KYC: one pending submission per user, 5 per day, JPEG/PNG/WebP ≤ 5 MB; nobody reviews their own; reject needs a reason.
- Admins cannot lock themselves or other admins; locking revokes every session.
- Top-up amounts 1.00–10 000.00; a paid amount or currency that differs, or a closed card, becomes REFUND_NEEDED.
- Trusted proxies default to loopback; compose trusts only the web container's fixed address.
- Language follows the saved choice, else the browser; switching reloads the page.
- Metrics and health only on the management port (never published, never on the API port); readiness = database +
  Redis (an SMTP outage does not mark the API down). Request ids come from nginx (`$request_id`), else a UUID.
- E2E fixtures that have no API go to the database (`e2e/support/db.ts`): granting ADMIN_ROLE, and funding a card
  with a balanced OPENING_BALANCE posting. Everything else goes through the UI or the API.
- Storage is SeaweedFS (S3 API) in compose, since the MinIO image is no longer published; the app keeps the MinIO
  client and the `MINIO_*` settings. TLS is a Caddy edge (automatic Let's Encrypt) in front of nginx; nginx believes
  forwarded headers only from Caddy's fixed address (172.28.0.11), the API only from nginx's (172.28.0.10).
- Phone payments hold the amount before the operator is called; an unknown answer keeps it held (never refunded on a
  guess). A delivery reported after the hold was released is flagged `PAYMENT_NEEDS_REVIEW` (metric
  `paygenix_payments_review_total`), not fixed automatically.
- Kept as they are (reviewed after T-145): the JWT filter's one indexed session lookup per request (cache only if a
  profile shows it), and sign-up starting over on reload (resuming would mean keeping the onboarding token in browser
  storage, which ADR 0001 rules out for tokens).
