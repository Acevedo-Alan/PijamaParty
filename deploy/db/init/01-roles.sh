#!/usr/bin/env bash
# Database roles with least privilege (RNF-27, RNF-28, RNF-29.2). Run once by the postgres image entrypoint on an
# empty data directory, as the container superuser. Passwords are read from the environment inside psql (\getenv),
# so they never appear on a command line, and reach SQL only quoted as :'name', never by string concatenation.
#
#   tienda_owner   owns the schema and the tables; Flyway migrates with it; BYPASSRLS
#   tienda_app     the application: no owner, no superuser, no BYPASSRLS, no DDL; only SELECT/INSERT/UPDATE/DELETE
#   tienda_backup  pg_dump: read-only, BYPASSRLS so the dump is complete
set -euo pipefail

: "${DB_OWNER_PASSWORD:?DB_OWNER_PASSWORD is required}"
: "${DB_APP_PASSWORD:?DB_APP_PASSWORD is required}"
: "${DB_BACKUP_PASSWORD:?DB_BACKUP_PASSWORD is required}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -v db="$POSTGRES_DB" <<'SQL'
\getenv owner_password DB_OWNER_PASSWORD
\getenv app_password DB_APP_PASSWORD
\getenv backup_password DB_BACKUP_PASSWORD

CREATE ROLE tienda_owner  LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE BYPASSRLS   PASSWORD :'owner_password';
CREATE ROLE tienda_app    LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD :'app_password';
CREATE ROLE tienda_backup LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE BYPASSRLS   PASSWORD :'backup_password';

-- RNF-21 limits apply to the application; migrations and backups may run longer.
ALTER ROLE tienda_app    SET statement_timeout = '10s';
ALTER ROLE tienda_app    SET idle_in_transaction_session_timeout = '30s';
ALTER ROLE tienda_owner  SET statement_timeout = 0;
ALTER ROLE tienda_backup SET statement_timeout = 0;
ALTER ROLE tienda_app    SET timezone = 'America/Argentina/Buenos_Aires';

ALTER DATABASE :"db" OWNER TO tienda_owner;
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO tienda_app, tienda_backup;
-- The built-in databases are not for the application roles.
REVOKE CONNECT, TEMPORARY ON DATABASE postgres, template1 FROM PUBLIC;

ALTER SCHEMA public OWNER TO tienda_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO tienda_app, tienda_backup;

-- Every table and sequence Flyway creates as tienda_owner gets these grants automatically. Flyway's own history
-- table is excluded again after each migration (db/migration/afterMigrate.sql).
ALTER DEFAULT PRIVILEGES FOR ROLE tienda_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO tienda_app;
ALTER DEFAULT PRIVILEGES FOR ROLE tienda_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO tienda_app;
ALTER DEFAULT PRIVILEGES FOR ROLE tienda_owner IN SCHEMA public
    GRANT SELECT ON TABLES TO tienda_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE tienda_owner IN SCHEMA public
    GRANT SELECT ON SEQUENCES TO tienda_backup;
-- Functions are not executable by everyone: grant EXECUTE explicitly where a migration needs it.
ALTER DEFAULT PRIVILEGES FOR ROLE tienda_owner REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
SQL
