# CLAUDE.md

## Project
Custom e-commerce for Pijama Party Santa Fe (sleepwear), replacing a Treinta store. Mobile-first, Argentina (ARS), fewer than 20 orders per day expected.

## Stack
Java 21, Spring Boot (Maven), Spring Security, PostgreSQL + Flyway, Spring Modulith + ArchUnit (architecture tests), React + Vite + TypeScript, Docker Compose, Caddy, a single server (4 GB RAM).

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
- Build and test: `./mvnw verify` (includes SpotBugs + find-sec-bugs, report only for now)
- Run locally with a throwaway PostgreSQL container: `./mvnw spring-boot:test-run`
- Run locally against your own PostgreSQL (profile `dev`): set the variables of `.env.example`, then `./mvnw spring-boot:run`
- Vulnerable dependency scan (slow, downloads the NVD database): `./mvnw dependency-check:check`
- Health check: `GET http://localhost:8080/api/health`

Frontend: (fill in)
