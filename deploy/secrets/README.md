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
```
