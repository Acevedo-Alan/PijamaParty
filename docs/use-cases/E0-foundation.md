# E0 – Foundation and architecture skeleton

**Status:** Draft · **Use cases:** none (technical stage) · **Spanish counterpart:** `docs/specs/E0-fundaciones`

## Goal

A walking skeleton deployed locally (and later on the real server) in which the architecture rules are already enforced by tests, so every later module is built inside the same guard rails.

## Scope

1. Repository, `CLAUDE.md`, backend and frontend projects (tasks `T-E0-01` to `T-E0-06`, unchanged).
2. Pools, rate limiting, roles and security context (tasks `T-E0-11` to `T-E0-15`, unchanged).
3. The architecture skeleton described here (new tasks `T-E0-16` to `T-E0-19`).
4. Server, DNS, deploy and backups (`T-E0-07` to `T-E0-10`) remain in Part B, after the server is contracted.

## New tasks

- [x] **T-E0-16** Create the module packages under `com.pijamaparty.store` with their `api`, `domain`, `application`, `adapter.in`, `adapter.out` sub-packages, each module with a `package-info.java` declaring it as a Spring Modulith application module. Modules: `catalog`, `inventory`, `cart`, `promotions`, `shipping`, `ordering`, `payments`, `notifications`, `accounts`, `reviews`, `backoffice`, `platform`.
- [x] **T-E0-17** Add the architecture tests: Spring Modulith `verify()` and the ArchUnit rules of `ARCHITECTURE.md` section 9. They must pass on the empty skeleton and fail when a rule is broken (include one intentionally failing test case in a scratch branch to prove it).
- [x] **T-E0-18** Build the shared kernel in `platform`: `Money` (integer cents, ARS, no floating point), `Clock` wiring (injected `java.time.Clock` for `America/Argentina/Buenos_Aires`), typed ids, `Actor` (role, user id, tokens), a generic sealed `Result` helper if needed, and the PII-safe logging utility.
- [x] **T-E0-19** Create reusable abstract contract-test base classes and the in-memory test fixtures pattern (one `InMemory<Port>` fake per outbound port, with a shared contract test).

## Acceptance criteria

- Given the empty skeleton, when the build runs, then Spring Modulith verification and all ArchUnit rules pass.
- Given a class in `domain` that imports `org.springframework`, when the build runs, then it fails.
- Given a class in `application` that imports an `adapter` package, when the build runs, then it fails.
- Given a module that depends on another module's internal package, when the build runs, then it fails.
- Given `Money.of(1999)`, when adding `Money.of(1)`, then the result is `Money.of(2000)`, and negative amounts are rejected where the type forbids them.
- Given a use case that needs the current time, when it is tested, then the test controls time through the injected `Clock`.

## Design notes

- Package layout and rules: `docs/architecture/ARCHITECTURE.md` sections 3, 4 and 9.
- Nothing in this stage is user-visible. Success is the build being green and the rules being impossible to bypass silently.

## Deploy script (T-E0-09)

`deploy/deploy.sh`, run on the server from any folder: one command to put the tested commit of `main` in production. Bash, no dependencies beyond Docker, Git and curl. Decided with the owner on 2026-11-07.

**Order of the steps** (each one stops the script, with a message, if it fails):
1. **Checks** (`--check` runs only these and changes nothing): `.env` exists with `DOMAIN`; every file that `docker-compose.yml` lists under `secrets:` exists in `deploy/secrets/`; the working tree has no uncommitted changes; `docker compose config` is valid.
2. **Update the code:** `git pull --ff-only` (skipped with `--no-pull`, for a commit already checked out). The previous and the new commit are printed.
3. **Build** the images with the site still up (`docker compose build`): a build that fails changes nothing.
4. **Backup before migrating:** when the `db` container is running, `pg_dump -Fc` as `tienda_backup` (the secret is already mounted in `db`) into `deploy/backups/pre-deploy-<UTC timestamp>-<previous commit>.dump`; the last 5 are kept. A failed dump stops the deploy before any change. The first deploy (no `db` yet) skips it. This is not the daily off-server backup (T-E0-10).
5. **Maintenance on:** creates `deploy/maintenance/maintenance.on` (RNF-31.7).
6. **Start:** `docker compose up -d --wait`: `migrate` runs Flyway, then `api` starts, then `caddy`.
7. **Maintenance off and smoke test:** removes the file and requests `GET /api/health` through Caddy (10 tries, 3 s apart; `-k` when `DOMAIN` is `localhost`, with `HTTPS_PORT` from `.env`).

**When a step after 5 fails** (decided): the script **leaves maintenance mode on**, prints the last 40 lines of the `migrate` and `api` logs, the previous commit and the backup file, and exits with a non-zero code. It never rolls the code back by itself: Flyway may already have changed the schema, and the previous code can fail against it. The owner fixes and runs the script again, or returns to the previous commit and restores the backup by hand (RUNBOOK, T-E6-04).

**Acceptance criteria**
- Given a missing secret file, when the script runs, then it lists the missing files and changes nothing.
- Given `--check`, when everything is in place, then it prints "ready" and no container, file or commit changes.
- Given a running database, when a deploy starts, then a dump exists before maintenance mode turns on, and only the 5 newest remain.
- Given a failing build or dump, when the script stops, then the site never entered maintenance mode.
- Given a failing `migrate` or `api`, when the script stops, then `maintenance.on` still exists and the message says what to do.
- Given a healthy deploy, when the script ends, then `maintenance.on` is gone and `/api/health` answered 200.
