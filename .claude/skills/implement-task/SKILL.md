---
name: implement-task
description: Implements exactly one task (T-Ex-yy) or use case (UC-xx) of the Pijama Party store, spec-driven - reads the use case and ARCHITECTURE.md, writes one test per acceptance criterion, runs ./mvnw verify, has the architecture-reviewer and security-reviewer subagents review the diff, and commits with a Conventional Commit that references the ID.
argument-hint: <T-Ex-yy | UC-xx>
disable-model-invocation: true
---

# Implement one task: $ARGUMENTS

Implement **only** `$ARGUMENTS`. Do not start the next task and do not fix unrelated things you notice; list them in the final report instead.

## 1. Read before coding

1. `CLAUDE.md` (all of it).
2. `docs/architecture/ARCHITECTURE.md` (all of it). Re-read sections 3, 4, 5 and 9 before touching a module.
3. Find the task or use case:
   - `Grep` for `$ARGUMENTS` in `docs/use-cases/` (English, implementation-facing) and in `docs/specs/` (Spanish: `spec.md`, `plan.md`, `tasks.md` of the stage).
   - For a use case, the Spanish ID is `CU-xx`, and UC-xx maps to the same number.
4. The cross-cutting requirements the task touches, from `docs/specs/01-requisitos-transversales.md` (RNF-20 to RNF-31) and `docs/specs/00-dominio-y-reglas.md` (rules R-xx).
5. `docs/architecture/GLOSSARY.md` for names.
6. The existing code of the modules involved, including their `package-info.java` (`allowedDependencies`), and `platform` (`Money`, `Actor`, `IdGenerator`, `Clock` wiring, `Pii`).

Write down for yourself the list of acceptance criteria (Given / When / Then). Each one becomes at least one test.

## 2. Resolve ambiguity first

- If anything needed is not in the use case, the spec or the architecture document, or if two documents disagree, **stop and ask the user** with `AskUserQuestion` before deciding. Offer a recommended option. Never invent behavior.
- If the behavior must change, **update the use case first** (`docs/use-cases/...`, and the Spanish spec if it is affected). Commit that doc change separately with `docs(<ID>): ...`, then implement.
- Record technical decisions that later tasks depend on (for example a data type or a naming convention) in the architecture document or the decisions table of `00-dominio-y-reglas.md`.

## 3. Implement

- Follow the hexagonal layout of ARCHITECTURE.md section 4. For a new use case (inbound port, service, controller, tests), use the `new-use-case` skill.
- Domain first: aggregates and value objects with their invariants, tested with plain unit tests and no Spring.
- Then the application service, using ports, constructor injection, `Clock` and `IdGenerator`. Expected business failures are sealed `Result` types.
- Then the adapters. JPA entities are separate from aggregates. Native SQL goes in text blocks with named parameters only. Controllers translate results to `problem+json`.
- Schema changes go only through a new Flyway migration (`backend/src/main/resources/db/migration/V<n>__<description>.sql`), with RLS policies for tables that hold customer data (RNF-27).
- An API change updates the OpenAPI contract (springdoc annotations) in the same task.
- Frontend and tooling are TypeScript only (`.ts`/`.tsx`); never add `.js`/`.jsx`/`.mjs`/`.cjs` files.
- Never log personal data; use `platform.logging.Pii`.

## 4. Test every acceptance criterion

- One test per acceptance criterion, named after it: `given..._when..._then...`.
- Pick the level from ARCHITECTURE.md section 10:
  - domain unit tests;
  - use-case tests with in-memory fakes;
  - contract tests for every new port implementation (fake and real);
  - Testcontainers for persistence, RLS and concurrency;
  - MockMvc for web, validation, `problem+json` and `429`.
- Idempotency and stock requirements need a repeat test and a concurrent test (RNF-20).

## 5. Verify

Run from `backend/`:

```
./mvnw verify
```

Everything must be green: unit tests, `ArchitectureTest`, `ModularityTest` and the Testcontainers tests. Check the SpotBugs and find-sec-bugs output too, and fix new findings in the code you touched. Never weaken `ArchitectureTest`, `ModularityTest` or `allowedDependencies` to make the build pass. If the build fails for a reason outside this task, stop and report it.

For frontend tasks, also run the frontend commands listed under "Commands" in `CLAUDE.md`.

## 6. Review

Launch both subagents **in parallel** on the current diff (uncommitted changes, including untracked files):

- `architecture-reviewer`
- `security-reviewer`

Tell each one the task ID and the list of changed files. Fix every CRITICAL and HIGH finding. Fix MEDIUM findings when they are inside the task's scope; otherwise list them in the report. After fixing, run `./mvnw verify` again. If you disagree with a finding, do not fix it; explain why in the report.

## 7. Commit

- Stage only the files of this task, and check `git status` first.
- Use one Conventional Commit that references the ID, for example `feat(T-E1-03): add product listing query`, `feat(UC-03): ...`, `test(T-E0-17): ...` or `chore(T-E0-01): ...`. Add a body listing what was added and any decision taken.
- Follow the session's commit attribution rules. Never use `--no-verify`. Do not push unless the user asks.
- Tick the task checkbox in its use-case or tasks file if the task is complete.

## 8. Report to the user (in Spanish)

- What was implemented and which acceptance criteria are covered by which tests.
- The `./mvnw verify` result (tests run, failures, SpotBugs).
- The review verdicts and what was fixed or deliberately left out.
- Decisions taken, open questions and the next task. Then stop.
