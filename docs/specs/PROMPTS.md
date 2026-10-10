# Prompt templates for Claude Code

All prompts are in English on purpose. Replace the placeholders in `<angle brackets>`.

## 1. Implement one task

```text
Read these files first:
- CLAUDE.md
- docs/specs/README.md
- docs/specs/00-dominio-y-reglas.md
- docs/specs/<STAGE>/spec.md
- docs/specs/<STAGE>/plan.md
- docs/specs/<STAGE>/tasks.md

Implement ONLY task <T-Ex-yy> from tasks.md.

Rules:
- Follow the spec and plan exactly. Do not invent behavior that is not written there.
- If something is ambiguous or missing, stop and ask me before coding.
- Write tests that cover every acceptance criterion of the related use case (CU-xx).
- Add a Flyway migration for any schema change and update the OpenAPI contract for any API change.
- Never log personal data (DNI, address, email).
- When finished, list the files you changed and mark the task as done in tasks.md.
```

## 2. Verify a stage against its spec

```text
Read docs/specs/<STAGE>/spec.md and review the current code.
For each use case and each acceptance criterion, tell me:
1. Whether it is implemented, partially implemented or missing.
2. Which test covers it (or that no test covers it).
3. Any behavior in the code that is NOT described in the spec.
Do not modify any file. Output a table.
```

## 3. Update the spec after a change

```text
The behavior of <CU-xx> must change: <describe the change>.
Update docs/specs/<STAGE>/spec.md and plan.md first, show me the diff,
and wait for my approval before touching any code.
```
