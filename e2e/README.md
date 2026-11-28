# Paygenix E2E tests

Playwright against the whole running stack (ADR 0001): the real API, Postgres, Redis and Mailpit, which catches the
emailed codes. Nothing is mocked. Each test signs up users of its own, so the tests run in parallel and a run can be
repeated against the same database.

| Spec | Covers |
|---|---|
| `sign-up.spec.ts` | the 7-step sign-up with the emailed code, sign out, a wrong password, sign in |
| `money.spec.ts` | a transfer with its fee and both balances, a wrong PIN, a phone payment with cashback, a number the mock operator refuses, a lost operator answer (amount held, Pay again completes it once) |
| `change-pin.spec.ts` | change the PIN by emailed code; the old PIN is refused and the new one confirms a payment |
| `admin.spec.ts` | a user's problem report resolved by an admin; an admin locks a user (who cannot sign in) and unlocks them |

Only two fixtures go to the database directly, because the product has no API for them: granting `ADMIN_ROLE`, and
funding a card with a balanced `OPENING_BALANCE` posting (a top-up needs Stripe). See `support/db.ts`.

## Running locally

1. Services: `docker compose --profile e2e up -d postgres-db redis mailpit`.
2. The API on the host with a `.env` from `.env.example`, plus mail through Mailpit and the per-address limits
   raised (every request comes from 127.0.0.1):

   ```
   MAIL_HOST=localhost
   MAIL_PORT=1025
   MAIL_SMTP_AUTH=false
   MAIL_STARTTLS_ENABLE=false
   REFRESH_COOKIE_SECURE=false
   LOGIN_IP_MAX_ATTEMPTS=100000
   USERNAME_CHECK_IP_MAX_ATTEMPTS=100000
   ```

   then `./mvnw spring-boot:run`.
3. The frontend on port 3000: `cd frontend && npm run dev` (or `npm run build && npx vite preview --port 3000`).
4. `cd e2e && npm ci && npx playwright install chromium && npx playwright test`.

Defaults are in `support/env.ts`. Override them with `E2E_BASE_URL`, `E2E_MAILPIT_URL` and `E2E_DATABASE_URL` (its
default uses `DB_PASSWORD=paygenix-dev-password`). CI runs the same suite in the `e2e` job of
`.github/workflows/ci.yaml` and uploads the report when it fails.
