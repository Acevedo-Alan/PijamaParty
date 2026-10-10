---
name: new-use-case
description: Scaffolds one use case of the Pijama Party backend following the AddItemToCart example of docs/architecture/ARCHITECTURE.md section 7 - inbound port with Command record and sealed Result, package-private service with constructor-injected ports, REST controller that switches over the Result into problem+json, and the tests (use-case test with in-memory fakes, port contract tests, web slice test). Use it when a task needs a new use case in a module.
argument-hint: <module> <UseCaseName>   e.g. cart AddItemToCart
---

# New use case: $ARGUMENTS

`$ARGUMENTS` is `<module> <UseCaseName>`. The base package is `com.pijamaparty.store.<module>`, and Java sources live under `backend/src/main/java` and `backend/src/test/java`.

## 0. Before generating

- Read ARCHITECTURE.md sections 4, 5, 7 and 10, and the use case that defines this behavior (`docs/use-cases/`). The **inputs, outcomes, HTTP statuses, route and messages come from the use case**. If something is missing, ask the user (`AskUserQuestion`) instead of inventing it.
- List the outcomes of the use case. Each one becomes one `Result` variant: one success, plus one variant per expected business failure. Unexpected failures stay exceptions.
- List the outbound ports the service needs. Keep them small and split reads from writes (`LoadCart` / `SaveCart`). Reuse existing ports in the module before creating new ones. Another module's data comes through a port whose adapter calls that module's `api`, only if `package-info.java` allows the dependency.
- Business rules go into the domain aggregate (returning its own sealed outcome, like `Cart.AddOutcome`), not into the service.

## 1. Inbound port: `<module>/application/<UseCaseName>.java`

```java
package com.pijamaparty.store.<module>.application;

public interface <UseCaseName> {

    Result execute(Command command);

    /** Inputs only. Never amounts the server must compute (R-02). */
    record Command(Actor actor, /* typed ids and value objects, not raw strings */) {}

    sealed interface Result {
        record <Success>(/* view record */) implements Result {}
        record <BusinessFailure>(/* data the caller needs */) implements Result {}
    }
}
```

- Use records for the command, the results and the views. The `Actor` (from `platform.domain`) goes in the command whenever authorization matters.
- Use typed ids (UUID records) and `Money` for amounts.

## 2. Service: `<module>/application/<UseCaseName>Service.java`

```java
package com.pijamaparty.store.<module>.application;

@Service
class <UseCaseName>Service implements <UseCaseName> {

    private final LoadX loadX;   // outbound ports only
    private final SaveX saveX;
    private final Clock clock;   // only if it needs time; never now() without it

    <UseCaseName>Service(LoadX loadX, SaveX saveX, Clock clock) {
        this.loadX = loadX;
        this.saveX = saveX;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        // 1. authorize with command.actor()
        // 2. load through ports
        // 3. let the aggregate decide (switch over its sealed outcome)
        // 4. save through ports, and return a Result variant
    }
}
```

- The class is package-private and uses constructor injection only. From Spring it may use only `@Service` and `@Transactional`.
- New ids come from `IdGenerator`. Time comes from `Clock`.
- One use case per class: if it grows a second responsibility, split it.

## 3. Outbound ports: `<module>/application/<PortName>.java`

They are small interfaces owned by the application layer. Their implementations go in `<module>/adapter/out/persistence` (JPA entities separate from aggregates, native SQL in text blocks with named parameters only) or in `<module>/adapter/out/<provider>`.

## 4. Controller: `<module>/adapter/in/web/<UseCaseName>Controller.java`

```java
package com.pijamaparty.store.<module>.adapter.in.web;

@RestController
@RequestMapping("/api/<route from the use case>")
class <UseCaseName>Controller {

    private final <UseCaseName> useCase;

    <UseCaseName>Controller(<UseCaseName> useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    ResponseEntity<?> handle(@Valid @RequestBody Request request /*, actor from the security context */) {
        return switch (useCase.execute(request.toCommand(/* actor */))) {
            case Result.<Success> ok -> ResponseEntity.ok(Response.from(ok));
            case Result.<BusinessFailure> failure -> problem(HttpStatus.<STATUS FROM USE CASE>, failure);
        };
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, Result failure) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        // title/detail: user-facing Spanish text comes from the i18n bundle (MessageSource), never hard-coded
        // extra properties: only non-personal data the client needs (e.g. variantId, available)
        return ResponseEntity.status(status).body(problem);
    }

    record Request(/* Bean Validation: types, max lengths, formats */) {
        <UseCaseName>.Command toCommand(/* actor */) { ... }
    }

    record Response(/* what the client needs */) {
        static Response from(Result.<Success> ok) { ... }
    }
}
```

- The `switch` is exhaustive with no `default`, so adding a `Result` variant breaks the build until it is mapped.
- The controller only translates: no business rules and no repository calls.
- Errors are `application/problem+json`. Validation errors and unexpected errors are handled by the shared `@RestControllerAdvice` in `platform`.
- Document the endpoint for OpenAPI (springdoc annotations), including every problem status.
- Add the RNF-22 rate-limit policy if the endpoint is public, and require `Idempotency-Key` if the use case says so (RNF-20).

## 5. Tests

Name every test after an acceptance criterion: `given..._when..._then...`.

1. **Domain unit tests** (`<module>/domain/...Test.java`): the aggregate's rules, with no Spring.
2. **Use-case test** (`<module>/application/<UseCaseName>ServiceTest.java`): build the service with `new`, using in-memory fakes of its ports and `Clock.fixed(...)`. There is one test per `Result` variant and per acceptance criterion.
3. **Port contract test**, one per new outbound port. It is an abstract `<PortName>ContractTest` that creates the subject through an abstract factory method and states the port's behavior once. Two subclasses run it:
   - `InMemory<PortName>ContractTest`, against the fake used by the service tests;
   - `<Jpa|Provider><PortName>ContractTest`, against the real adapter, with Testcontainers PostgreSQL (`TestcontainersConfiguration`) for persistence.

   Reuse the contract-test base classes and the `InMemory<Port>` pattern from T-E0-19 if they exist.
4. **Web slice test** (`<module>/adapter/in/web/<UseCaseName>ControllerTest.java`, `@WebMvcTest`). It mocks the inbound port and checks, for every `Result` variant, the HTTP status, the `application/problem+json` content type and body, Bean Validation errors, and that no personal data appears in the response or the logs.

## 6. Finish

Run `./mvnw verify` from `backend/`. `ArchitectureTest` and `ModularityTest` must pass without being modified. Then go back to the calling task (normally the `implement-task` skill) for the review and the commit.
