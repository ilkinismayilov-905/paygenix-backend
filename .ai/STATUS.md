# Paygenix status

Last updated: 2026-11-27 (after PR #111). Roadmap and decisions: [`docs/ROADMAP.md`](../docs/ROADMAP.md).
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
| 9 | Quality and docs: README, complete OpenAPI, observability | partly: README and OpenAPI done; observability open |
| 10 | Frontend, including a simple admin UI | done: every screen in `screens.md`, English + Azerbaijani |
| 11 | Release: deploy preparation, fresh hardened server | partly: nginx web container and compose ready; server, TLS and keys open |

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

1. **Deploy (M11):** a server; `docker compose up -d --build`; TLS in front of `paygenix-web`; set `FRONTEND_URL`,
   `APP_BASE_URL`, `CORS_ALLOWED_ORIGINS`, `GOOGLE_REDIRECT_URI` to the public https origin; create buckets `stories`,
   `profile` and private `kyc`; Stripe test keys (`STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`). Needs the owner.
2. **Observability (M9):** metrics (e.g. Prometheus on a management port not published) and a request id in logs.
3. **Per-IP login limit:** the client IP is reliable now (T-076); add it next to the per-email limit in
   `LoginAttemptService`.
4. **Playwright E2E suite in the repo** (ADR 0001): sign in, transfer, pay, change PIN, admin review against compose.
5. **Real phone operator:** reserve → call outside the transaction → settle or release, with reconciliation (see the
   shortcut note in `PaymentService.confirmPayment`).
6. Smaller shortcuts noted in code (`grep -rn "shortcut:" src frontend/src`): JWT filter session lookup cache, card
   issue key per currency, sign-up flow lost on reload.

## Decisions taken (beyond the roadmap)

- Stories are promos by admins, shown to everyone in one app-branded row; no user-published stories.
- KYC: one pending submission per user, 5 per day, JPEG/PNG/WebP ≤ 5 MB; nobody reviews their own; reject needs a reason.
- Admins cannot lock themselves or other admins; locking revokes every session.
- Top-up amounts 1.00–10 000.00; a paid amount or currency that differs, or a closed card, becomes REFUND_NEEDED.
- Trusted proxies default to loopback; compose trusts only the web container's fixed address.
- Language follows the saved choice, else the browser; switching reloads the page.
