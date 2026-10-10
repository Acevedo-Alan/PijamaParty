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
