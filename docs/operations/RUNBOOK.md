# Operations runbook

**Status:** Draft (T-E6-08, 2026-11-07) · **Audience:** whoever operates the server · **Owner's guide:** `docs/operations/GUIA-BACKOFFICE.md` (Spanish)

This runbook documents what the repository contains today. Steps that depend on work not done yet are listed in
[Gaps](#gaps-to-close) instead of being invented; each one names the task that closes it. Commands run from the
repository root on the server, with `deploy/secrets/` filled (see `deploy/secrets/README.md`) and `.env` set.

`COMPOSE` below stands for `docker compose -f deploy/docker-compose.yml --env-file .env`.

## 1. The stack

One server (4 GB RAM), four containers (`deploy/docker-compose.yml`):

| Service | What it does | Notes |
|---|---|---|
| `caddy` | HTTPS, static storefront, product photos, proxy to the API | The only service with published ports (80, 443). Fixed address `172.30.0.10`, the only proxy the API trusts for `X-Forwarded-For`. |
| `api` | Spring Boot, profile `prod` | No ports. Reads the Docker secrets as files. Photos in the `uploads` volume. |
| `db` | PostgreSQL 17 | On the internal `data` network only. Data in the `db-data` volume. |
| `migrate` | Flyway, one shot, as `tienda_owner` | Runs before `api` starts; the API never holds the owner credentials. |

Volumes: `db-data` (the database), `uploads` (product photos), `caddy-data` (certificates; losing it only means
Caddy asks for new ones), `caddy-config`.

Database roles (`deploy/db/init/01-roles.sh`, created once, when `db-data` is empty): `tienda_owner` (migrations),
`tienda_app` (the API; no DDL, no `BYPASSRLS`), `tienda_backup` (read-only, for `pg_dump`). Changing a password file
later does **not** change the role: use `ALTER ROLE`.

## 2. Start, stop, restart

```
$COMPOSE up -d --build --wait        # build and start everything; waits for the health checks
$COMPOSE ps                          # state of each service
$COMPOSE restart api                 # restart one service (caddy, api, db)
$COMPOSE stop                        # stop all; data stays in the volumes
$COMPOSE down                        # remove the containers; volumes stay. Never add -v: it deletes the data
```

`restart: unless-stopped` brings every service back after a reboot of the server or of Docker.

## 3. Health and logs

- Health: `GET https://DOMAIN/api/health`. The storefront's home page answers from Caddy even if the API is down.
- Logs: `$COMPOSE logs --tail=200 api` (add `-f` to follow; same for `caddy` and `db`). Each container keeps 5 files
  of 10 MB (json-file driver), so the disk cannot fill with logs.
- Logs and the database log carry no personal data by design (R-05): no email, national ID or address. If you find
  one, it is a bug; report it.
- The API answers `503` with `Retry-After` when its pools are saturated and `429` with `Retry-After` on rate limits.
  A burst of either is load or abuse, not a crash.

## 4. Deploy a new version

One command, from any folder of the server: `deploy/deploy.sh` (T-E0-09). `--check` only verifies that `.env`, the secret
files and the compose file are in place; `--no-pull` deploys the commit already checked out. It pulls the code (`git pull
--ff-only`), builds the images with the site still up, takes a `pg_dump` into `deploy/backups/` (the last 5 are kept), turns
maintenance mode on, starts everything (`migrate` runs Flyway first, then `api`, then `caddy`), turns maintenance mode off
and checks `GET /api/health` through Caddy.

If it fails after maintenance mode went on, it **leaves maintenance mode on**, prints the last lines of the `migrate` and
`api` logs, the previous commit and the backup file, and does not roll back by itself (Flyway may already have changed the
schema). Fix it and run the script again, or go back to the previous commit and restore the backup. Afterwards open the
home page and one product and sign in to the back office.

Schema changes only come through Flyway migrations; never edit the database by hand to "fix" a version.

## 5. Maintenance mode

While the file `deploy/maintenance/maintenance.on` exists, Caddy answers every page with the maintenance page (`503`,
`Retry-After`) and `/api/*` with a problem+json `503` (RNF-31.7). Caddy also shows the page by itself when the API
cannot answer a page (`502`, `503`, `504`).

```
touch deploy/maintenance/maintenance.on     # on
rm deploy/maintenance/maintenance.on        # off
```

The deploy script turns it on and off by itself; around a restore (T-E6-04) do it by hand.

## 6. Secrets and configuration

- One file per secret in `deploy/secrets/` (never committed); the table in `deploy/secrets/README.md` says what each
  one is and where to get it. Non-secret settings are in `.env` (`.env.example` documents each).
- Rotating a secret: change the file, then `$COMPOSE up -d --force-recreate api` (the `api` reads secrets at start).
  Exceptions: the role passwords (`ALTER ROLE`, see section 1) and `SECOND_FACTOR_KEY` (never change it once the
  administrator enrolled, see section 8).
- `ADMIN_INITIAL_PASSWORD` is used only at the first start. Afterwards overwrite the file with a random value; it
  never changes the administrator's password.
- MODO is off unless `MODO_ENABLED=true`; Google and Facebook sign-in are each off while their client id is empty.
- Analytics ids (`VITE_GA4_MEASUREMENT_ID`, `VITE_META_PIXEL_ID`) are read **when the image is built**: changing them
  needs `up -d --build`.

## 7. Payments and email (what to check when an order looks wrong)

- **An order waits for payment** (`AWAITING_PAYMENT`): the stock is reserved for 30 minutes. The buyer can retry from
  the tracking link; after the reservation the order expires and the units go back on sale. Nothing to do.
- **A paid order does not move to "pagado"**: MercadoPago's webhook did not arrive or was refused. The endpoint is
  `https://DOMAIN/api/payments/mercadopago/webhook` (event "Pagos") and it is signed with
  `MERCADOPAGO_WEBHOOK_SECRET`; a wrong secret makes every notification fail with `401` (look for them in the `api`
  and `caddy` logs). Fix the secret, then resend the notification from the MercadoPago panel.
- **Payment approved after the order expired or was cancelled**: the order detail shows an alert. Refund from the
  MercadoPago panel (there is no refund button in the back office yet).
- **Emails** go through an outbox, sent every minute with retries at 1, 5, 15 and 60 minutes, up to 5 attempts. If
  `RESEND_API_KEY` or `MAIL_FROM` is wrong, emails wait in the outbox and end as `FAILED`; the order detail then
  tells the owner to call the buyer. Fix the configuration and recreate `api`; failed emails are not resent on their
  own. Bounces and complaints arrive at `https://DOMAIN/api/mail/webhook` (secret `RESEND_WEBHOOK_SECRET`) and mark
  the order with an alert.

## 8. The administrator

- **Forgot the password**: no procedure exists (see Gaps). The first administrator is created from `ADMIN_EMAIL`,
  `ADMIN_NAME` and `ADMIN_INITIAL_PASSWORD` only when none exists, so changing those later does nothing.
- **Account locked**: after 5 wrong passwords the account is locked; the sign-in screen says until when (UC-04).
- **Lost the phone and the recovery codes**: in `.env` set `ADMIN_RESET_SECOND_FACTOR=true`, run
  `$COMPOSE up -d --force-recreate api`, sign in (the back office offers to enrol the second factor again and shows
  new recovery codes), then set the variable back to `false` and recreate `api` again.
- **Sessions** last 60 minutes.

## 9. Disk and capacity

- `docker system df` shows the space used by images, containers and volumes; `docker image prune` removes unused
  images after several deploys. Do not prune volumes.
- The database is tuned for the 4 GB server (`max_connections=30`, `shared_buffers=512MB`). At fewer than 20 orders a
  day the limit to watch is memory: `docker stats --no-stream`.

## Gaps to close

These have no procedure in the repository yet. Add each one here when its task is done, and delete the line:

| Missing | Task |
|---|---|
| Provisioning the server, Docker, firewall and SSH key access | T-E0-07 |
| DNS and the HTTPS certificate check | T-E0-08 |
| First deploy with the script (T-E0-09 wrote it; it needs the server) | T-E0-07, T-E0-09 |
| Daily `pg_dump` (with `tienda_backup`) and copy of `uploads` off the server, 14 days of retention | T-E0-10, T-E6-04 |
| **Restore procedure and a tested restore** (database, photos; with maintenance mode) | T-E6-04 |
| Resetting the administrator's forgotten password (UC-04 defines none; decide the flow first) | to be assigned |
| Uptime monitoring and alerts on `/api/health` and the home page | T-E6-05 |
| What to do if the hosting provider fails (single server) | T-E6-12, with the real provider |
| Real-server checks of idempotency, rate limits and email (SPF, DKIM, DMARC) | T-E6-15 |
| Load figures measured on the real server (RNF-21, RNF-22) | T-E6-14 |
