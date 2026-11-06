# Paygenix

A consumer wallet built as a portfolio project: virtual cards, card-to-card transfers, phone payments with cashback,
Stripe top-ups, identity verification with admin review and promo stories. It is a demo: no real money moves and the
texts are not legal documents.

- **Backend:** Spring Boot 4 (Java 21), PostgreSQL (Flyway), Redis, S3-compatible storage (MinIO), Stripe (test mode).
- **Frontend:** React 19 + TypeScript + Vite in [`frontend/`](frontend), typed API client generated from the OpenAPI spec.

## What it does

| Area | Highlights |
|---|---|
| Accounts | Email sign-up with a code, password login, Google sign-in (one-time code exchange), per-device sessions with rotating refresh tokens in an httpOnly cookie, sign out one device or everywhere |
| Security | 6-digit transaction PIN with lockout, change PIN and change email by emailed codes (the old address confirms too), rate limits in Redis, RFC 9457 problem details with stable `code`s, RBAC for admin APIs |
| Money | Double-entry ledger (every movement sums to zero), configurable commission and cashback, idempotency keys on every money request, row locking against overspending, decimal strings end to end |
| Phone payments | Pay a phone number through an operator adapter; a mock operator refuses numbers ending in 000 and times out on 999, and nothing is charged then |
| Cards | Virtual cards with Luhn-valid numbers; only an HMAC and the last four digits are stored; the full number is shown once |
| Top-ups | Stripe Checkout; the balance changes only after a signature-verified webhook, exactly once |
| Verification | Identity document upload to a private bucket, manual admin review (approve / reject with a reason), decision by email |
| Stories | Promo stories published by admins, viewed in a full-screen viewer, view counts |
| Legal | Versioned Terms and Privacy; money moves wait until the current versions are accepted |

## Run it locally

Requirements: Java 21, Maven, Node 22, Docker.

```bash
cp .env.example .env    # then fill in DB_PASSWORD, JWT_SECRET, CARD_PAN_HMAC_KEY, MINIO_*; see the comments
```

```bash
docker compose up -d postgres-db redis
```

```bash
./mvnw spring-boot:run
```

```bash
cd frontend && npm ci && npm run dev    # http://localhost:3000
```

Optional pieces, each switched off cleanly when not configured:

- **Mail** (`MAIL_*`): codes are sent by SMTP. Any local SMTP catcher works for development.
- **Storage** (`MINIO_*`): buckets `stories`, `profile` and `kyc` (keep `kyc` private). `docker compose --profile local up -d minio`, or any S3-compatible store.
- **Google sign-in** (`GOOGLE_*`).
- **Stripe top-ups** (`STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`): without them top-ups answer `503 TOP_UP_UNAVAILABLE`. Locally: `stripe listen --forward-to localhost:8000/api/stripe/webhook` prints the webhook secret. Pay with the test card `4242 4242 4242 4242`.

An admin is a user with the `ADMIN_ROLE` row in `user_roles`; the frontend then shows **Review** (identity documents, promo stories).

## Tests

```bash
./mvnw test             # integration tests with Testcontainers (needs Docker)
```

```bash
cd frontend && npm run typecheck && npm run lint && npm test
```

`OpenApiSnapshotTest` fails when the API changed but `frontend/openapi.json` did not; refresh it with
`UPDATE_OPENAPI=true ./mvnw test -Dtest=OpenApiSnapshotTest`, then `npm run api:generate` in `frontend/`.

## Documentation

- [Roadmap and key decisions](docs/ROADMAP.md)
- [Frontend screens and the API each uses](docs/frontend/screens.md)
- [ADR 0001: frontend stack](docs/adr/0001-frontend-stack.md)
- API reference: start with `SWAGGER_ENABLED=true` and open `/swagger-ui.html`.

## Deploying

Manual only (see `.github/workflows`). Run Flyway migrations with the old version stopped (single instance), put nginx in
front for TLS and same-origin `/api`, and set `TRUSTED_PROXIES`, `CORS_ALLOWED_ORIGINS` and `FRONTEND_URL` to the real
values.
