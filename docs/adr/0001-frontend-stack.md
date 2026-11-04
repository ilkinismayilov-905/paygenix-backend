# ADR 0001: Frontend stack and repository layout

- Status: accepted (owner, 2026-11-03)
- Context: Phase 10. The backend is a Spring Boot REST API (`/api/**`, OpenAPI at `/v3/api-docs`) with JWT access tokens, rotating refresh tokens and device sessions.

## Decision

1. **Same repository, `/frontend` folder.** The backend stays at the repo root, so its Dockerfile, CI and deploy paths do not change. API and UI changes for one flow ship in one PR.
2. **React 19 + TypeScript (strict) + Vite**, as a single-page app.
   - Paygenix is a signed-in banking app. Its pages are private, so SSR and SEO add nothing.
   - The build is static files. There is no Node server in production.
3. **Same origin as the API.**
   - In development, the Vite dev server (port 3000) proxies `/api` and `/oauth2` to the backend on port 8000.
   - In production, nginx serves the build and proxies `/api` and `/oauth2` to the backend.
   - So the browser never makes a cross-origin call, cookies work without CORS credentials, and the backend CORS setup (`allowCredentials=false`) can stay as it is.
4. **Libraries** (kept small):
   - React Router: routing, code-split per route.
   - TanStack Query: server state, caching, retries and loading/error states.
   - `openapi-typescript` + `openapi-fetch`: a typed client generated from the backend's OpenAPI spec. Types are regenerated with `npm run api:generate`. CI fails if the generated file is stale.
   - React Hook Form + Zod: forms. The Zod schemas mirror the backend validation rules.
   - Plain CSS with design tokens as CSS custom properties: no UI kit, so the look is our own.
   - i18n: strings live in message files from day one. English first, Azerbaijani second.
   - Tests: Vitest + Testing Library for components, Playwright for E2E against the local docker-compose stack.

## Money in the client

- Amounts stay decimal strings end to end and never become a JS `number`. Zod validates them as a positive decimal with at most 2 places.
- Balances are shown from the API's `displayAmount` + `currency`. Amounts the user types are formatted with `Intl.NumberFormat` for their currency, use tabular numerals and are right-aligned.
- Every money request carries an `Idempotency-Key` (UUID) that is created once per user intent, so retrying after a timeout reuses the same key.

## Auth and token storage

- What the backend does today:
  - `/api/auth/login`, `/api/auth/oauth/exchange` and `/api/auth/refresh-token` return the access token in the JSON body. The refresh token is only ever set as the httpOnly `pg_refresh` cookie (T-109, T-116).
  - `/api/auth/refresh-token` reads only that cookie and needs an allowed `Origin` header.
  - Refresh tokens rotate. Presenting an already-used one revokes the whole session (`REFRESH_TOKEN_REUSED`).
  - Sign-out is `POST /api/auth/logout`: it revokes the cookie's session and clears the cookie (T-118). Other devices are signed out with `DELETE /api/auth/sessions/{id}`.
- Plan:
  - The **access token stays in memory only**: never in localStorage or sessionStorage, so an XSS bug cannot read a stored token later.
  - The **refresh token lives in an httpOnly, Secure, SameSite=Strict cookie scoped to `/api/auth`** (done in T-109):
    - The refresh and logout endpoints also check `Origin`, because CSRF protection is off for the API.
    - Sign-out clears the cookie.
  - **Exactly one refresh at a time.** On a 401 the client starts one refresh. Every other request that gets a 401 waits for that same refresh, then retries once. If the refresh fails, the user goes to the sign-in screen.
    - Once the cookie exists, tabs coordinate through the Web Locks API, so two tabs never present the same refresh token.
- XSS is the main risk for an in-memory token, so nginx sends a strict Content-Security-Policy for the build: `script-src 'self'`, no inline scripts, `frame-ancestors 'none'`.
- Errors are RFC 9457 problem+json. The UI branches on `code`, never on `message`.

## Consequences

- How production serves the frontend (nginx, same origin) is set up in Phase 11.
- `/frontend` has its own `package.json`. The ignored tooling `package.json` at the repo root is unrelated.
- The generated API types make backend contract changes show up as TypeScript errors.
