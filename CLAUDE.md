# Paygenix: notes for Claude Code

A consumer wallet (portfolio project): Spring Boot 4 / Java 21 backend at the repo root, React 19 + TypeScript + Vite
frontend in `frontend/`. Start every session by reading [`.ai/STATUS.md`](.ai/STATUS.md): what is done, what is next,
and the decisions already taken. Update it in the same PR as the work it describes.

## Working with the owner

- Reply in **Azerbaijani**. Code, comments, commits, PRs and docs stay in English.
- The owner wants steady progress: where a choice is theirs, pick the best-practice option, say which in one line, and
  keep going. Ask only for things only they have (keys, accounts, product decisions not covered by the roadmap).
- Every change: tests → commit → push. A PR is opened and merged when the owner asks; this repo allows **rebase**
  merges only (merge commits are refused). Wait for CI to be green before merging.
- Independent review is part of the routine: after a feature, a separate review pass, then a "review fixes" commit.
- This repo lives only on GitHub; sessions run in fresh cloud containers. Anything not committed (memory tools, local
  notes, databases) is gone in the next session, so durable context goes into this file or `.ai/STATUS.md`.

## Commands

```bash
./mvnw test                                   # backend: Testcontainers (needs Docker)
./mvnw test -Dtest=SomeTest                   # one class
UPDATE_OPENAPI=true ./mvnw test -Dtest=OpenApiSnapshotTest   # after any API change
```

```bash
cd frontend && npm run api:generate           # after the snapshot changed (CI checks it is in sync)
cd frontend && npm run typecheck && npm run lint && npm test
```

CI (`.github/workflows/ci.yaml`) runs the backend on Java 21 and the frontend on **Node 24**; run frontend tests on 24
too when touching uploads or `fetch` stubs (`npx -y node@24 node_modules/vitest/vitest.mjs run`).

## Conventions

- **Commits:** Conventional Commits (`feat:`, `fix(frontend):`, `test:`, `refactor!:` for breaking), imperative, the
  body says why. Task ids (`T-123`) in the subject or body when a task is done.
- **API:** everything under `/api/v1`. Errors are RFC 9457 problem+json with a stable `code`; clients branch on `code`,
  never on messages. New endpoints document their codes in `@ApiResponses`.
- **Money:** `BigDecimal` on the server, decimal strings on the wire and in the UI (never a JS number), HALF_EVEN to 2
  decimals. Every money movement is a balanced double-entry posting (`LedgerService`). Money requests take an
  `Idempotency-Key`.
- **Migrations:** Flyway, `src/main/resources/db/migration`. A released migration is never edited (checksums in
  `src/test/resources/migration-checksums.txt`; `MigrationImmutabilityTest`). New migration = new file + a line there.
- **Security defaults:** 404 (not 403) for other users' resources; secrets only from the environment; no PII or PINs in
  logs (`toString` overrides on request records); uploads are checked by type and magic bytes.
- **Frontend:** texts only through `t()` with keys in both `src/i18n/en.ts` and `src/i18n/az.ts` (a test keeps them in
  step); forms with React Hook Form + Zod mirroring the backend rules; access token in memory only, refresh token in an
  httpOnly cookie; every screen has loading, empty and error states and moves focus after a step changes.
- **Docs:** `docs/frontend/screens.md` lists each screen and the API it uses; keep it current with the screens.

## Running the app in a cloud session

No services survive between sessions. Start Docker (`dockerd &`), then `docker compose up -d postgres-db redis`, fill
`.env` from `.env.example` (dev values: `REFRESH_COOKIE_SECURE=false`, any 32+ byte `JWT_SECRET` / `CARD_PAN_HMAC_KEY`),
run `./mvnw spring-boot:run` and `cd frontend && npm run dev`. Without SMTP, email codes need a local SMTP catcher;
without MinIO, uploads answer 503 but everything else works. Tell the app which port to preview (3000).
