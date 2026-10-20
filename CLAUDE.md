# CLAUDE.md

## Project
Custom e-commerce for Pijama Party Santa Fe (sleepwear), replacing a Treinta store. Mobile-first, Argentina (ARS), fewer than 20 orders per day expected.

## Stack
Java 21, Spring Boot (Maven), Spring Security, PostgreSQL + Flyway, Spring Modulith + ArchUnit (architecture tests), React + Vite + TypeScript, Docker Compose, Caddy, a single server (4 GB RAM).

Frontend and tooling: TypeScript only (.ts/.tsx). No .js/.jsx/.mjs/.cjs files, including configs and scripts. If a tool cannot read a TS config, use JSON. Node >= 22.18 or tsx for scripts.

## Workflow (spec-driven)
- Specs live in `docs/specs` (Spanish, business-facing) and `docs/use-cases` (English, implementation-facing). Architecture lives in `docs/architecture/ARCHITECTURE.md`.
- Read the stage's use cases and the architecture document before coding. Implement ONE task at a time.
- Never invent behavior that is not in a use case. Ask when something is ambiguous. If behavior must change, update the use case first.
- Reference task IDs (`T-Ex-yy`) and use cases (`UC-xx`) in Conventional Commits, e.g. `feat(UC-03): ...`.

## Language
- English for code, database, API routes, tests and documentation. Spanish only for user-facing texts (UI and customer emails) through i18n resource bundles.
- Business vocabulary: `docs/architecture/GLOSSARY.md`.

## Architecture (see ARCHITECTURE.md)
- Modular monolith. One module = one package under `com.pijamaparty.store.<module>`.
- Hexagonal inside every module: `api`, `domain`, `application`, `adapter.in`, `adapter.out`.
- `domain` is pure Java: no Spring, JPA, Jackson or adapter imports.
- `application` holds one class per use case plus the ports (interfaces) it needs. It may use Spring stereotypes and `@Transactional`, nothing else from the framework.
- Modules talk to each other only through `<module>.api`. No cyclic module dependencies.
- Business rules live in the domain, never in controllers or repositories.

## SOLID checklist (review every change against it)
- S: one use case per class; adapters only translate; aggregates own their invariants.
- O: new behavior by adding a class (strategy, adapter), not by editing a switch.
- L: every implementation of a port passes the shared contract test of that port.
- I: small ports (`LoadCart`, `SaveCart`), read and write sides separated.
- D: depend on ports; constructor injection only; inject `Clock`, never call `Instant.now()` in domain or application code.

## Business and technical rules
- Money is `Money` (integer cents, ARS). The server is the source of truth for prices, discounts, shipping and stock.
- Never log or send to analytics personal data (national ID, address, email).
- Idempotency: checkout requires `Idempotency-Key`; webhooks, stock movements, order transitions and emails are idempotent through PostgreSQL unique constraints.
- Pools and limits are bounded; on saturation return 503 with `Retry-After`. Rate limiting is done in the app (Bucket4j); return 429 with `Retry-After`.
- Errors are `application/problem+json`. Expected business failures are modeled as sealed `Result` types, not exceptions.
- All SQL is parameterized. Sorting columns come from enums. Row Level Security is active on tables with customer data; every transaction sets the security context.
- Schema changes only through Flyway; API changes update OpenAPI; every acceptance criterion has a test.

## Commands
Backend (run from `backend/`; requires JDK 21 and Docker running for Testcontainers):
- Build and test: `./mvnw verify` (find-sec-bugs fails on SQL built from strings; all other findings go to `target/spotbugsReport.xml`)
- Run locally with a throwaway PostgreSQL container: `./mvnw spring-boot:test-run`
- Local PostgreSQL (from the repository root): `docker compose -f deploy/docker-compose.dev.yml --env-file .env up -d`
- Run locally against your own PostgreSQL: set the variables of `.env.example` (including `SPRING_PROFILES_ACTIVE=dev`; there is no default profile), then `./mvnw spring-boot:run`
- Vulnerable dependency scan (slow, downloads the NVD database; set NVD_API_KEY): `./mvnw dependency-check:check`
- Health check: `GET http://localhost:8080/api/health`; Swagger UI (dev only): `http://localhost:8080/swagger-ui.html`

Frontend (run from `frontend/`; Node >= 22.18, npm):
- Install: `npm ci`
- Dev server with /api proxied to the backend on :8080: `npm run dev`
- Checks: `npm run typecheck`, `npm run lint`, `npm test`, `npm run build`
- Regenerate the typed API client after an API change: run the backend tests (they write `backend/target/openapi/openapi.json`), then `npm run api:generate`
- Production dependency audit: `npm audit --omit=dev --audit-level=high`

End-to-end tests (run from `e2e/`; Node >= 22.18, Docker running, `npm ci` done in `frontend/` too):
- Install: `npm ci`, then `npx playwright install chromium`
- Run (Playwright starts the MercadoPago and Resend stubs, the backend with the `e2e` profile and the frontend build; outside CI it reuses servers already running): `npm test`
- Lighthouse on home, listing, product and checkout, plus the security headers, the CSP and the link previews of products (T-E6-02, T-E6-03, T-E6-07; builds the production Caddy image and runs it on `https://localhost:8443` in front of the `e2e` backend; needs libvips: `vips` on the PATH or `VIPS_BIN_DIR`; HTML reports in `lighthouse-report/`): `npm run lighthouse`
- Screenshots of the store next to the prototype (T-E7-11; home, listing, product, cart and checkout on a phone and a computer; same Caddy image and libvips as `npm run lighthouse`; the prototype loads its fonts and photos from the internet; open `screenshots/index.html`): `npm run screenshots`
- Load test with k6 (T-E6-14; not in CI; k6 runs from its Docker image, nothing to install; starts the stubs, the `e2e` backend and the production Caddy image as `npm run lighthouse` does, seeds the catalog and checks orders, stock and emails after each scenario; `LOAD_DURATION` shortens the 2-minute browsing): `npm run load`
- Checks: `npm run typecheck` (also typechecks the k6 scripts of `load/k6`)

Production stack (from the repository root, with Docker; fill `deploy/secrets/` first, see its README, and set `DOMAIN` in `.env`):
- Build and start caddy, api, db and the one-shot Flyway `migrate` step: `docker compose -f deploy/docker-compose.yml --env-file .env up -d --build --wait`
- Try it locally without touching ports 80/443: `DOMAIN=localhost`, `HTTP_PORT=8088`, `HTTPS_PORT=8443` in the env file, then `https://localhost:8443/api/health`

Security gates also run in CI (from the repository root, with Docker):
- SQL built from strings in JPA (Semgrep): `docker run --rm -v "$PWD:/src" -w /src semgrep/semgrep:1.180.0 semgrep scan --config .semgrep --error --metrics=off backend/src`
- Secrets in the history (gitleaks): `docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:v8.30.1 git /repo --redact`
