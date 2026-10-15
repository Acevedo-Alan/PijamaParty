# Production secrets (never committed)

Everything in this folder except this file is ignored by git. Before the first `docker compose up`, create one file
per secret, containing only the value (no newline is needed):

| File | Used by |
|---|---|
| `DB_ADMIN_PASSWORD` | PostgreSQL superuser, only for the first start (`deploy/db/init`) |
| `DB_OWNER_PASSWORD` | `tienda_owner`: the `migrate` container (Flyway) |
| `DB_APP_PASSWORD` | `tienda_app`: the `api` container |
| `DB_BACKUP_PASSWORD` | `tienda_backup`: `pg_dump` (T-E0-10) |
| `ADMIN_INITIAL_PASSWORD` | `api`: password of the first administrator (UC-04), 12 characters to 72 bytes. Used only at the first start, when no administrator exists; with `ADMIN_EMAIL` and `ADMIN_NAME` in `.env`. After that first start, overwrite the file with a random value: changing it never changes the administrator's password |
| `SECOND_FACTOR_KEY` | `api`: AES-256 key of the administrator's authenticator secret (T-E1-16), the base64 of 32 random bytes: `openssl rand -base64 32`. Without it the API does not start. Never change it once the administrator enrolled: the stored secret could not be read, and the administrator would have to be reset with `ADMIN_RESET_SECOND_FACTOR=true` |
| `MERCADOPAGO_ACCESS_TOKEN` | `api`: the seller's MercadoPago access token (T-E3-06), from the MercadoPago developers panel, "Credenciales". Test credentials give the sandbox; production credentials charge for real. Without it the API does not start |
| `MERCADOPAGO_WEBHOOK_SECRET` | `api`: the secret key that signs MercadoPago's notifications (T-E3-07), from "Tus integraciones" > the application > "Webhooks" (production URL `https://DOMAIN/api/payments/mercadopago/webhook`, event "Pagos"). Without it the API does not start |
| `RESEND_API_KEY` | `api`: Resend's API key for the payment-confirmed emails (T-E3-09), from the Resend dashboard, "API Keys", with "Sending access" only. `MAIL_FROM` (an address of a domain verified at Resend) and `NOTIFICATIONS_ADMIN_EMAIL` go in `.env`. Without it the API does not start |
| `RESEND_WEBHOOK_SECRET` | `api`: the secret that signs Resend's bounce and complaint notifications (T-E3-22), from the Resend dashboard, "Webhooks" > the webhook > "Signing secret" (`whsec_...`). Create the webhook with the production URL `https://DOMAIN/api/mail/webhook` and the events `email.bounced`, `email.suppressed` and `email.complained` only. Without it the API does not start |

Generate each one with a password manager or `openssl rand -base64 32`. The role passwords are set when the database
volume is created; changing a file later does not change the role password (use `ALTER ROLE`).

## Permissions

Docker Compose (without Swarm) mounts each secret file into the container with the host file's owner and mode. The
containers read them as different non-root users (`store`, uid 10001, in `api`; `postgres`, uid 70, in `db`), and
`db` and `api` share `DB_APP_PASSWORD`, so the files must be readable by any user inside the containers. Protect the
folder instead, so no other host user can reach them:

```
chmod 700 deploy/secrets      # owned by the deploy user
chmod 444 deploy/secrets/DB_*
chmod 444 deploy/secrets/SECOND_FACTOR_KEY
chmod 444 deploy/secrets/MERCADOPAGO_ACCESS_TOKEN
chmod 444 deploy/secrets/MERCADOPAGO_WEBHOOK_SECRET
chmod 444 deploy/secrets/RESEND_API_KEY
chmod 444 deploy/secrets/RESEND_WEBHOOK_SECRET
```
