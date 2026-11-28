// Where the stack under test lives. Defaults match `docker compose --profile e2e` with the API and the Vite dev
// server on the host (see e2e/README.md); CI and other setups override them.
export const env = {
  baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
  mailpitURL: process.env.E2E_MAILPIT_URL ?? 'http://127.0.0.1:8025',
  // only for fixtures the product has no API for: granting the admin role and funding a card
  databaseURL: process.env.E2E_DATABASE_URL ?? 'postgres://paygenix:paygenix-dev-password@127.0.0.1:5455/paygenix',
}
