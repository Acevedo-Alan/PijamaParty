#!/usr/bin/env bash
# One-command deploy (T-E0-09). Run on the server, from any folder:
#
#   deploy/deploy.sh             update the code, back up the database, migrate and start everything
#   deploy/deploy.sh --check     only verify that everything is in place; changes nothing
#   deploy/deploy.sh --no-pull   deploy the commit that is already checked out
#
# The steps and what happens when one fails are in docs/use-cases/E0-foundation.md ("Deploy script"). In short: a
# failure before maintenance mode changes nothing; a failure after it leaves maintenance mode on and never rolls the
# code back by itself, because Flyway may already have changed the schema.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

COMPOSE=(docker compose -f deploy/docker-compose.yml --env-file .env)
FLAG=deploy/maintenance/maintenance.on
BACKUPS=deploy/backups
KEEP_BACKUPS=5

check_only=false
pull=true
for argument in "$@"; do
    case "$argument" in
        --check) check_only=true ;;
        --no-pull) pull=false ;;
        *) echo "Unknown option: $argument (use --check or --no-pull)" >&2; exit 2 ;;
    esac
done

say() { printf '==> %s\n' "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# Value of KEY in .env (no sourcing: values such as MAIL_FROM contain spaces and angle brackets).
env_value() {
    local line
    line="$(grep -E "^$1=" .env | tail -n 1 || true)"
    line="${line#*=}"
    line="${line%\"}"
    printf '%s' "${line#\"}"
}

# --- 1. Checks -------------------------------------------------------------------------------------------------------
[ -f .env ] || fail ".env is missing (copy .env.example and fill it)."
domain="$(env_value DOMAIN)"
[ -n "$domain" ] || fail "DOMAIN is empty in .env."

missing=()
while IFS= read -r secret; do
    [ -f "deploy/secrets/$secret" ] || missing+=("$secret")
done < <(sed -n 's|^ *file: \./secrets/\(.*\)$|\1|p' deploy/docker-compose.yml)
if [ "${#missing[@]}" -gt 0 ]; then
    printf 'Missing secret files in deploy/secrets/ (see deploy/secrets/README.md):\n' >&2
    printf '  %s\n' "${missing[@]}" >&2
    exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
    git status --short >&2
    fail "The working tree has uncommitted changes; commit or discard them first."
fi
"${COMPOSE[@]}" config --quiet || fail "docker compose config is not valid."

if $check_only; then
    say "Ready: .env, secrets, working tree and compose file are in place."
    exit 0
fi

# --- 2. Update the code ----------------------------------------------------------------------------------------------
previous="$(git rev-parse --short HEAD)"
if $pull; then
    say "Updating the code (git pull --ff-only)"
    git pull --ff-only
fi
current="$(git rev-parse --short HEAD)"
say "Commit: $previous -> $current"

# --- 3. Build with the site still up ---------------------------------------------------------------------------------
say "Building the images"
"${COMPOSE[@]}" build

# --- 4. Backup before migrating --------------------------------------------------------------------------------------
backup=""
if [ -n "$("${COMPOSE[@]}" ps --status running -q db)" ]; then
    mkdir -p "$BACKUPS"
    backup="$BACKUPS/pre-deploy-$(date -u +%Y%m%dT%H%M%SZ)-$previous.dump"
    say "Backing up the database to $backup"
    # The role's password is a Docker secret already mounted in db; it never appears on a command line.
    # shellcheck disable=SC2016  # the expansion must happen inside the container
    if ! "${COMPOSE[@]}" exec -T db sh -c \
        'PGPASSWORD="$(cat /run/secrets/DB_BACKUP_PASSWORD)" pg_dump -U tienda_backup -Fc "$POSTGRES_DB"' \
        > "$backup"; then
        rm -f "$backup"
        fail "The backup failed; nothing was changed."
    fi
    [ -s "$backup" ] || { rm -f "$backup"; fail "The backup is empty; nothing was changed."; }
    # Keep the newest ones only.
    # shellcheck disable=SC2012
    ls -1t "$BACKUPS"/pre-deploy-*.dump | tail -n +$((KEEP_BACKUPS + 1)) | xargs -r rm -f
else
    say "No running database: first deploy, no backup to take"
fi

# --- 5. Maintenance on -----------------------------------------------------------------------------------------------
mkdir -p "$(dirname "$FLAG")"
touch "$FLAG"
say "Maintenance mode on"

stop_in_maintenance() {
    {
        printf 'ERROR: %s\n' "$1"
        printf '\nMaintenance mode stays ON (%s). Nothing was rolled back.\n' "$FLAG"
        printf 'Previous commit: %s   Current commit: %s\n' "$previous" "$current"
        [ -z "$backup" ] || printf 'Database backup taken before migrating: %s\n' "$backup"
        printf '\n--- migrate (last 40 lines)\n'
        "${COMPOSE[@]}" logs --no-color --tail=40 migrate || true
        printf '\n--- api (last 40 lines)\n'
        "${COMPOSE[@]}" logs --no-color --tail=40 api || true
        printf '\nFix it and run deploy/deploy.sh again, or go back to %s and restore the backup (RUNBOOK).\n' \
            "$previous"
    } >&2
    exit 1
}

# --- 6. Start --------------------------------------------------------------------------------------------------------
say "Starting (migrate, api, caddy)"
"${COMPOSE[@]}" up -d --wait || stop_in_maintenance "docker compose up failed."

# --- 7. Maintenance off and smoke test -------------------------------------------------------------------------------
rm -f "$FLAG"
say "Maintenance mode off"
port="$(env_value HTTPS_PORT)"
url="https://$domain${port:+:$port}/api/health"
insecure=()
[ "$domain" != "localhost" ] || insecure=(-k)
say "Checking $url"
if ! curl -fsS "${insecure[@]}" --max-time 5 --retry 10 --retry-delay 3 --retry-connrefused "$url" > /dev/null; then
    touch "$FLAG"
    stop_in_maintenance "$url did not answer 200."
fi

say "Deployed $current. Check the home page, one product and the back office."
