#!/bin/sh
# Reads the owner password from its Docker secret into Flyway's environment (never onto a command line) and migrates.
set -eu
FLYWAY_PASSWORD="$(cat /run/secrets/DB_OWNER_PASSWORD)"
export FLYWAY_PASSWORD
export FLYWAY_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?logServerErrorDetail=false"
export FLYWAY_USER="${DB_OWNER_USER:-tienda_owner}"
export FLYWAY_LOCATIONS="filesystem:/flyway/sql"
exec flyway -connectRetries=10 migrate
