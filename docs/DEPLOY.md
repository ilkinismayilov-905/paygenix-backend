# Deploying Paygenix to one server

This runbook takes a fresh server to a running Paygenix with HTTPS. The whole stack runs in `docker-compose.yaml`:

```
browser ──https──> caddy (TLS, profile "tls") ──> paygenix-web (nginx: SPA + /api proxy) ──> paygenix-app (API)
        └─https──> caddy ──> storage (SeaweedFS, S3; profile "storage")        postgres-db, redis
```

Only Caddy is reachable from the internet (ports 80 and 443). The API listens on 127.0.0.1 only, and its management
port (health checks, metrics) is never published. nginx believes `X-Forwarded-For` / `-Proto` only from Caddy's fixed
address, and the API believes them only from nginx's fixed address (T-076, T-146). The per-address limits therefore
see the real client.

What the owner provides: the server, two DNS names, the Stripe test keys, and the Google OAuth client.

## 1. Server

- Ubuntu 24.04 LTS, at least 2 vCPU, 4 GB RAM and 40 GB disk. Fresh, not reused.
- A non-root user with sudo and SSH key login only. In `/etc/ssh/sshd_config`: `PasswordAuthentication no` and
  `PermitRootLogin no`, then `sudo systemctl reload ssh`.
- The firewall: `sudo ufw allow OpenSSH && sudo ufw allow 80,443/tcp && sudo ufw allow 443/udp && sudo ufw enable`.
- Security updates: `sudo apt install unattended-upgrades && sudo dpkg-reconfigure -plow unattended-upgrades`.
- Docker Engine and the compose plugin from Docker's apt repository (docs.docker.com/engine/install/ubuntu). Add the
  user to the `docker` group.

Docker publishes ports past ufw. That is why every port in compose except Caddy's is bound to `127.0.0.1`. Keep it
that way when you change compose.

## 2. DNS

Point two names at the server's address with A (and AAAA) records:

| Name | Used for |
|---|---|
| `APP_DOMAIN`, e.g. `app.example.com` | the app and its API |
| `MEDIA_DOMAIN`, e.g. `media.example.com` | presigned links to stories, profile photos and identity documents |

Caddy requests the Let's Encrypt certificates on first start. Both names must already resolve to the server at that
point.

## 3. Configuration (`.env`)

```bash
git clone https://github.com/ilkinismayilov-905/paygenix-backend.git paygenix && cd paygenix
cp .env.example .env && chmod 600 .env
```

Fill in `.env`. Generate each secret with `openssl rand -base64 48`, and back up `CARD_PAN_HMAC_KEY` offline: it can
never change.

| Key | Value |
|---|---|
| `APP_DOMAIN`, `MEDIA_DOMAIN`, `ACME_EMAIL` | the two names, and an address for certificate notices |
| `WEB_PORT` | `127.0.0.1:8080` (only Caddy talks to nginx) |
| `APP_BASE_URL`, `APP_WEB_URL`, `FRONTEND_URL` | `https://APP_DOMAIN` |
| `CORS_ALLOWED_ORIGINS` | `https://APP_DOMAIN` |
| `GOOGLE_REDIRECT_URI` | `https://APP_DOMAIN/login/oauth2/code/google`, and the same URI in the Google console |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | from the Google console |
| `DB_PASSWORD`, `JWT_SECRET`, `CARD_PAN_HMAC_KEY` | new random values |
| `MINIO_URL` | `https://MEDIA_DOMAIN` |
| `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` | new random values (the storage container takes the same ones) |
| `MAIL_*` | a real SMTP account (a Gmail app password works for a demo) |
| `STRIPE_SECRET_KEY` | `sk_test_...` (Stripe dashboard → Developers → API keys, **test mode**) |
| `STRIPE_WEBHOOK_SECRET` | `whsec_...` of a webhook endpoint `https://APP_DOMAIN/api/v1/stripe/webhook` with the events `checkout.session.completed`, `checkout.session.expired`, `checkout.session.async_payment_succeeded` and `checkout.session.async_payment_failed` |

Leave `REFRESH_COOKIE_SECURE`, `SWAGGER_ENABLED`, `MANAGEMENT_PORT` and the limits at their defaults.

## 4. Start

```bash
docker compose --profile storage --profile tls up -d --build
```

- `storage-init` creates the buckets `stories`, `profile` and `kyc` and exits. Running it again does nothing.
- The web container waits until the API's readiness check (database and Redis) passes.
- To use an external S3 store instead, leave out `--profile storage`, point `MINIO_URL` at it, create the three
  buckets there, and delete the `MEDIA_DOMAIN` block from `ops/caddy/Caddyfile`.

## 5. Check

```bash
docker compose ps                                   # every service up; paygenix-app "healthy"
docker compose exec paygenix-app curl -fsS localhost:8081/actuator/health/readiness
curl -sI https://APP_DOMAIN | head -1               # HTTP/2 200, with a valid certificate
```

Then in a browser:
1. Sign up; the code arrives by email.
2. Issue a card and top it up with Stripe's test card `4242 4242 4242 4242`. The balance is credited only when the
   webhook arrives.
3. Send an identity document.
4. Promote yourself to admin: `docker compose exec postgres-db psql -U $DB_USERNAME -d $DB_NAME -c "INSERT INTO
   user_roles (user_id, role) SELECT id, 'ADMIN_ROLE' FROM users WHERE email = 'you@example.com'"`. Then review
   the document under Review.

## 6. Operate

- **Logs:** `docker compose logs -f paygenix-app`. Each line carries the request id that the response returned in
  `X-Request-Id`.
- **Metrics:** `docker compose --profile monitoring up -d prometheus`, then `ssh -L 9090:127.0.0.1:9090 server` and
  open http://localhost:9090. Watch these:
  - `paygenix_auth_logins_total{outcome="rate_limited"}`;
  - `paygenix_payments_review_total`;
  - the error log line "needs manual review".
- **Backups:**
  - nightly `docker compose exec -T postgres-db pg_dump -U $DB_USERNAME $DB_NAME | gzip > backup-$(date +%F).sql.gz`;
  - the `s3data` volume;
  - `.env`.

  Copy all three off the server.
- **Updates:** `git pull && docker compose --profile storage --profile tls up -d --build`. Flyway migrates on start.
  Run one API instance only, and do not run the old and new versions side by side during a migration.
- **The GitHub deploy workflow** (`.github/workflows/deploy.yaml`) is manual and predates this runbook: it copies to
  `/root` and runs `docker compose down`. Rebuild it once the server exists, with a non-root user, these profiles, and
  CI as a gate.
