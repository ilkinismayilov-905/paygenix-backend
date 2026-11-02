# ADR 0001: Frontend stack and repository layout

- Status: accepted (owner, 2026-11-03)
- Context: Phase 10. The backend is a Spring Boot REST API (`/api/**`, OpenAPI at `/v3/api-docs`) with JWT access tokens and device sessions.

## Decision

1. **Same repository, `/frontend` folder.** The backend stays at the repo root, so its Dockerfile, CI and deploy paths do not change. API and UI changes for one flow ship in one PR.
2. **React 18 + TypeScript (strict) + Vite**, as a single-page app.
   - Paygenix is a signed-in banking app. Its pages are private, so SSR and SEO add nothing.
   - The build is static files, served by nginx (or any static host) next to the API. There is no Node server to run in production.
3. **Libraries** (kept small):
   - React Router: routing, code-split per route.
   - TanStack Query: server state, caching, retries and loading/error states.
   - `openapi-typescript` + `openapi-fetch`: a typed client generated from the backend's OpenAPI spec. Types are regenerated with `npm run api:generate`. CI fails if the generated file is stale.
   - React Hook Form + Zod: forms. The Zod schemas mirror the backend validation rules.
   - Plain CSS with design tokens as CSS custom properties: no UI kit, so the look is our own.
   - i18n: strings live in message files from day one. English first, Azerbaijani second.
   - Tests: Vitest + Testing Library for components, Playwright for E2E against the local docker-compose stack.
4. **Dev server on port 3000.** The backend's `FRONTEND_URL` and CORS defaults already point to `http://localhost:3000`.

## Auth and token storage

- What the backend does today: `/api/auth/login`, `/api/auth/oauth/exchange` and `/api/auth/refresh-token` return the access and refresh tokens in the JSON body. Sign-out is `DELETE /api/auth/sessions/{id}` on the session marked `current`.
- Plan:
  - The **access token stays in memory only**: never in localStorage or sessionStorage, so an XSS bug cannot read a stored token later.
  - The **refresh token moves to an httpOnly, Secure, SameSite=Strict cookie scoped to `/api/auth`**. This needs a small backend change, tracked as T-109 and done before the sign-in screens. Until then the client keeps the refresh token in memory, so a page reload signs the user out. That is acceptable during development.
  - On a 401 the client refreshes once and retries the request. If that also fails, it goes to the sign-in screen.
- Errors are RFC 9457 problem+json. The UI branches on `code`, never on `message`.

## Consequences

- Deploying the frontend is a static build; how it is served is decided in Phase 11.
- `/frontend` has its own `package.json`. The ignored tooling `package.json` at the repo root is unrelated.
- The generated API types make backend contract changes show up as TypeScript errors.
