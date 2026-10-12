# Architecture

**Status:** Draft · **Applies to:** the whole backend (the frontend section is at the end)

## 1. Context and drivers

- One developer, nine weeks, a single 4 GB server, fewer than 20 orders per day.
- Java 21, Spring Boot, Spring Security, PostgreSQL.
- High-value correctness: stock must never oversell, payments must never double-charge, customer data must be protected (idempotency, Row Level Security, SQL-injection resistance, rate limiting, bounded pools).
- The system must stay testable and easy to extend (new payment provider such as MODO, new discount rules, invoicing later).

## 2. Decision

**Modular monolith with Hexagonal Architecture (ports and adapters) inside each module, light Domain-Driven Design, and a CQRS-lite read side.**

| Alternative | Why not |
|---|---|
| Microservices | Operational cost (deployments, networking, distributed transactions) is far beyond what one developer and one small server justify. |
| Classic layered MVC with anemic entities | Business rules leak into services and controllers, tests need the whole framework, and the SOLID principles erode quickly. |
| One big package with a shared `Service` per entity | No enforceable boundaries; every change touches everything. |

A modular monolith keeps one deployable and one PostgreSQL (simple operations, real transactions) while the module boundaries are enforced by tests. If a module ever has to be extracted, its `api` package is already the contract.

## 3. Modules

| Module | Responsibility | Key types | Depends on (`api` packages only) |
|---|---|---|---|
| `catalog` | Categories, products, variants, images, listing, filters, text search | `Category`, `Product` (with `Variant`, `Image`) | `inventory` |
| `inventory` | Stock levels, reservations, movements | `StockLevel`, `StockReservation`, `StockMovement` | `accounts` (names of the users in the stock history) |
| `cart` | Carts and items | `Cart` | `catalog`, `inventory`, `promotions` |
| `promotions` | Coupons and automatic discount rules | `Coupon`, `DiscountRule` | – |
| `shipping` | Cost per zone, free-shipping threshold | `ShippingZoneRate`, `ShippingSettings` | – |
| `ordering` | Checkout orchestration, orders, state machine, tracking link, withdrawal requests ("botón de arrepentimiento") | `Order`, `OrderItem` | `cart`, `inventory`, `promotions`, `shipping`, `payments` |
| `payments` | Provider gateways, payment attempts, webhooks | `Payment`, `PaymentEvent` | – |
| `notifications` | Email outbox, sending, bounces | `OutboxEmail` | `ordering` (events) |
| `accounts` | Users, authentication (password, Google and Facebook through OAuth2), 2FA, address book, account deletion and data export | `User`, `Address` | – |
| `reviews` | Reviews and moderation | `Review` | `ordering`, `catalog` |
| `backoffice` | Composition of admin screens that need data from several modules | read models only | `ordering`, `notifications`, `catalog`, `inventory` |
| `platform` | Shared kernel: `Money`, `Clock`, ids, `Actor`, idempotency, rate limiting, problem+json, security context for RLS | – | – |

**Rules**
1. The module dependency graph must be acyclic. `ordering → payments`, `notifications → ordering` and `reviews → ordering` all point one way.
2. A module may use another module only through that module's `api` package (public interfaces, DTO records and events).
3. **Avoiding cycles:** when two modules need data from each other (for example the admin order screen needs the email-bounce alert from `notifications`), the composition happens in `backoffice`, which depends on both and which nobody depends on. Events flow in one direction only.
4. Writes touch only the tables of the owning module. Read-side queries may read another module's data only through a database view published by the owner (for example `inventory.v_variant_availability`).
5. Cross-module reactions use domain events published after commit (section 6), never direct calls into another module's internals.
6. **Foreign keys stay inside a module.** A table that refers to another module's data stores only the `uuid` (for example `stock_level.variant_id`), with no foreign key, so the database does not couple modules against their dependency direction. Consistency comes from events, or from a call through the owner's `api` in the same transaction when the dependency already points that way (`catalog` calls `inventory.api.StockLevels.open` when it creates variants, UC-06).

## 4. Layers inside a module

```text
com.pijamaparty.store.<module>/
  api/                       public contract: facade interfaces, DTO records, events
  domain/                    entities, value objects, domain services, repository ports
  application/               use cases (inbound ports), outbound ports, orchestration
  adapter/
    in/web/                  REST controllers, request/response DTOs, mapping
    in/scheduler/            scheduled jobs
    in/webhook/              provider callbacks
    out/persistence/         JPA entities, Spring Data repositories, mappers, native SQL
    out/<provider>/          MercadoPago, Resend, image processing, file storage ...
  config/                    Spring wiring for the module
```

**Dependency direction:** `adapter → application → domain`. The domain depends on nothing.

| Layer | May import | Must not import |
|---|---|---|
| `domain` | JDK only | Spring, JPA, Jackson, anything from `application` or `adapter` |
| `application` | `domain`, `platform`, other modules' `api`, Spring stereotypes and `@Transactional` | `adapter`, JPA, web types |
| `adapter` | `application`, `domain`, frameworks | other modules' internals |

Persistence uses separate JPA entities mapped to domain aggregates, so the domain stays pure. For simple CRUD modules the mapper is trivial; the rule is kept anyway so the codebase stays uniform.

**Exception: JDBC adapters.** When a constraint violation must become a port outcome (for example `SLUG_ALREADY_USED`) without aborting the caller's transaction, the adapter uses `NamedParameterJdbcTemplate` with constant SQL text blocks and runs each such write inside a savepoint of the caller's transaction (`JdbcCategoryStore`, T-E1-03), through the shared `platform.persistence.Savepoints` helper (T-E2-06). PostgreSQL aborts the whole transaction on an error, and Hibernate cannot recover from a failed flush, so a JPA entity cannot do this. Rows are still mapped by hand to the aggregate, never exposed.

## 5. SOLID, concretely

| Principle | How the architecture applies it | Example |
|---|---|---|
| **S**ingle Responsibility | One use case per class; aggregates guard their own invariants; adapters only translate between formats; one reason to change per class. | `AddItemToCart` only adds items; `Cart` only enforces cart rules. |
| **O**pen/Closed | New behavior is a new class behind an interface, not an edit to a `switch`. | `DiscountRule` strategies (percentage, minimum purchase, category); `PaymentGateway` implementations (MercadoPago, later MODO); `ShippingCostCalculator` (zone table now, carrier API later). |
| **L**iskov Substitution | Every implementation of a port honors the same contract; an abstract contract test runs against every implementation, including the in-memory fake. | `PaymentGatewayContractTest`, `ProductRepositoryContractTest`, `EmailSenderContractTest`. |
| **I**nterface Segregation | Small ports shaped by what each use case needs; read and write sides separated. | `LoadCart` and `SaveCart` instead of one `CartRepository` that does everything; `ProductListingQuery` (read) apart from `ProductRepository` (write). |
| **D**ependency Inversion | Domain and application depend on ports they own; adapters implement them; wiring through constructor injection in `config`. Time, randomness and ids come through injected abstractions. | `Clock` is injected; `TokenGenerator` and `OrderNumberSequence` are ports. |

**Review checklist for every pull request**
- Does the class have one reason to change?
- Could a new variant of this behavior be added without editing existing code?
- Does any port have methods that some implementers or callers do not need?
- Does any domain or application class import a framework or an adapter?
- Is there a contract test for each new port implementation?

## 6. Patterns used and where

| Pattern | Where |
|---|---|
| Aggregate and invariants | `Order`, `Product`, `Cart`, `StockLevel`, `Coupon` |
| Value object (`record`) | `Money` (integer cents), `Sku`, `Slug`, `Email`, `NationalId`, `Quantity`, `OrderNumber` |
| Typed id (`record` wrapping a `UUID`) | `UserId` (in `platform`), `ProductId`, `OrderId` ... (in their module). The application creates ids through the `IdGenerator` port before persisting; columns are `uuid` |
| Strategy | `DiscountRule`, `ShippingCostCalculator`, `PaymentGateway` |
| State machine (transition table) | `OrderStatus` allowed transitions, checked inside `Order` |
| Specification / filter object | Catalog filters (`ProductFilter`) with whitelisted sort columns |
| Anti-corruption layer | MercadoPago and Resend DTOs are translated to domain types inside their adapters |
| Domain event + transactional outbox | Cross-module reactions and emails; handlers are idempotent |
| Idempotent receiver | Webhooks, checkout (`Idempotency-Key`), emails, stock movements |
| CQRS-lite | Commands go through aggregates; catalog listing and search use read queries returning projections |
| Unit of Work | `@Transactional` at the use-case boundary |

**Domain events:** published through Spring's event mechanism and persisted by the Spring Modulith event publication registry (stored in PostgreSQL), so delivery is at least once. Every handler must therefore be idempotent. Verify Spring Modulith's compatibility with your Spring Boot version before adopting it.

**Event payloads carry ids, never personal data.** The registry table (`event_publication`) has no Row Level Security, so an event holds ids and non-personal values only: no email, national ID, address, phone, name or access token. A listener that needs more loads it by id through the owner's `api`. Events are records that implement `platform.domain.DomainEvent`, and `ArchitectureTest` rejects such records with personal-data fields. Completed publications are deleted (`completion-mode: delete`).

## 7. Java 21 usage

- **Records** for commands, results, value objects, events and DTOs (immutable by default).
- **Sealed interfaces** for use-case results and events, so every `switch` is exhaustive and the compiler flags unhandled cases.
- **Pattern matching for `switch`** to translate results into HTTP responses.
- **Expected business failures are results, not exceptions** (`OutOfStock`, `CouponExpired`). Exceptions stay for unexpected failures.
- **Text blocks** for native SQL, always with named parameters.
- Platform threads (bounded Tomcat pool), as decided in RNF-21. Virtual threads remain a future option.

### Example: one use case end to end

#### `cart/application/AddItemToCart.java`

```java
public interface AddItemToCart {

    Result execute(Command command);

    record Command(CartRef cart, VariantId variantId, int quantity) {}

    sealed interface Result {
        record Added(CartView cart) implements Result {}
        record InsufficientStock(VariantId variantId, int available) implements Result {}
        record VariantUnavailable(VariantId variantId) implements Result {}
    }
}
```

#### `cart/application/AddItemToCartService.java`

```java
@Service
class AddItemToCartService implements AddItemToCart {

    private final LoadCart loadCart;               // outbound port
    private final SaveCart saveCart;               // outbound port
    private final VariantAvailability availability; // outbound port, backed by inventory.api

    AddItemToCartService(LoadCart loadCart, SaveCart saveCart, VariantAvailability availability) {
        this.loadCart = loadCart;
        this.saveCart = saveCart;
        this.availability = availability;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        var stock = availability.of(command.variantId());
        if (stock.isEmpty()) {
            return new Result.VariantUnavailable(command.variantId());
        }
        var cart = loadCart.findOrCreate(command.cart());
        return switch (cart.add(command.variantId(), command.quantity(), stock.get())) {
            case Cart.AddOutcome.Accepted ok -> {
                saveCart.save(cart);
                yield new Result.Added(CartView.from(cart));
            }
            case Cart.AddOutcome.ExceedsStock over ->
                new Result.InsufficientStock(command.variantId(), over.available());
        };
    }
}
```

The web adapter maps `Result` to HTTP in a `switch`; the domain `Cart` decides, the service orchestrates, the adapters translate. Tests: `Cart` is tested with plain unit tests, the service with in-memory fakes of its three ports, the JPA adapter with a contract test on Testcontainers PostgreSQL.

## 8. Cross-cutting concerns

| Concern | Where it lives | Notes |
|---|---|---|
| Problem+json errors | `platform.web` | One `@RestControllerAdvice`; use-case results are mapped in each controller |
| Rate limiting (RNF-22) | `platform.web` filter | Bucket4j, policies by property |
| Idempotency (RNF-20) | `platform.idempotency` | Used by `ordering` through a decorator on the checkout use case |
| Row Level Security context (RNF-27) | `platform.persistence` | `RlsTransactionManager` sets `app.role` (`ANONYMOUS`, `CUSTOMER`, `ADMIN`, `SYSTEM`), `app.user_id`, `app.cart_token_hash` and `app.tracking_token_hash` (SHA-256) with `set_config(..., true)` at the start of every transaction, from the actor bound in `CurrentActor` (by `ActorFilter` per request, or `CurrentActor.callAsSystem` for background work). Policies read them through `app_role()`, `app_user_id()`, `app_cart_token_hash()` and `app_tracking_token_hash()` (V2); Background work wraps the transaction in `callAsSystem`, never runs it inside one (refused); controllers build commands with `CurrentActor.get()`. One exception: a guest's first add to the cart issues its `cart_token`, so `CartController` rebinds the guest with the new token (`CurrentActor.callAs`) before the use case's transaction begins (T-E2-04). RLS contains application logic bugs, not SQL injection (any role can call `set_config`): RNF-28 is the defense against injection. tests use `RlsPolicyTester` |
| Authentication, session, CSRF, security headers (RNF-29) | `platform.security` | Spring Security; HSTS and CSP are set in Caddy |
| Authorization | Use cases receive an `Actor` (role, user id, tokens) in the command | Checked in the use case, then enforced again by RLS |
| Pools and timeouts (RNF-21) | `platform.config` | HikariCP, Tomcat, HTTP clients |
| PII-safe logging | `platform.logging` | A masking utility; no national ID, address or email in logs |
| Analytics (RNF-25) | Frontend only | The backend never receives or forwards analytics data |

## 9. Enforcing the architecture

- **Spring Modulith:** a test calls `ApplicationModules.of(StoreApplication.class).verify()` to fail the build on cycles or on access to a module's internals.
- **ArchUnit:** rules for the layer table above.

#### `src/test/java/com/pijamaparty/store/ArchitectureTest.java`

```java
@AnalyzeClasses(packages = "com.pijamaparty.store", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_has_no_framework_dependencies =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..", "jakarta.persistence..", "com.fasterxml..", "..adapter..", "..application..");

    @ArchTest
    static final ArchRule application_does_not_depend_on_adapters =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule no_field_injection =
        noFields().should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired");

    @ArchTest
    static final ArchRule no_static_clock_usage =
        noClasses().that().resideInAnyPackage("..domain..", "..application..")
            .should().callMethod(java.time.Instant.class, "now");
}
```

- **CI:** both tests run on every build, together with the SQL-injection static analysis (RNF-28).

## 10. Testing strategy

| Level | Scope | Tooling |
|---|---|---|
| Domain unit tests | Aggregates, value objects, state machine; no Spring | JUnit 5, AssertJ |
| Use-case tests | Application services with in-memory fakes of the ports | JUnit 5 |
| Contract tests | One abstract test per port, run against every implementation (fake and real) | JUnit 5 |
| Persistence and RLS tests | Migrations, queries, policies, concurrency (last unit of stock) | Testcontainers PostgreSQL |
| Web slice tests | Controllers, validation, problem+json, rate-limit responses | MockMvc |
| Architecture tests | Layers and module boundaries | ArchUnit, Spring Modulith |
| End to end | Critical flows with the payment provider in sandbox | Playwright, in TypeScript (E6) |
| Load | Targets in the E6 spec | k6, in TypeScript |

Test names follow the acceptance criteria: `givenAnItemExceedingStock_whenAddingToCart_thenItIsRejected`.

### Contract tests and in-memory fakes

Shared test support lives in test sources under `com.pijamaparty.store.platform.testing`.

| Piece | Convention |
|---|---|
| Contract | `abstract class <Port>ContractTest extends ContractTest<Port>`, in the port's package. It states the port's behavior once and gets the implementation from `createSubject()`. A read/write pair has one contract over a subject implementing both (`<T extends LoadCart & SaveCart>`): it arranges through the write port, calls `persistAndForget()`, then asserts through the read port. |
| Fake | `InMemory<Port>` in test sources, in the port's package, one per outbound port. A fake for a read/write pair (`LoadCart` + `SaveCart`) may implement both on top of `InMemoryStore`. |
| Fake contract | `InMemory<Port>ContractTest extends <Port>ContractTest`: plain JUnit, no Spring. |
| Real contract | `<Technology><Port>ContractTest extends <Port>ContractTest`, in the adapter's package. Persistence adapters use `@PersistenceAdapterTest` (JPA slice on Testcontainers PostgreSQL, constructor injection, rolled back after each test), `@Import` the adapter and override `persistAndForget()` with flush and clear, so assertions read what PostgreSQL stored. |
| Real contract with savepoints | A JDBC adapter that needs the caller's transaction (section 4) uses `@ApiTest` instead: the contract runs through a wrapper that gives every call its own committed transaction, plus one test that breaks a constraint and commits other writes in the same transaction. The rolled-back slice's transaction was not active in contract methods inherited from the abstract class (T-E1-03). |
| Concurrency and RLS | Not on the rolled-back slice: other connections cannot see uncommitted rows, and the transaction-local security context would last for the whole test. These tests commit real transactions and clean up explicitly. Tests connect as `tienda_app` (no owner, no `BYPASSRLS`) after Flyway migrated as `tienda_owner`, so policies apply as in production. |
| Copies | `InMemoryStore` stores and returns copies of mutable aggregates, so a use case that forgets to save fails in the fake exactly as it would against the database. |

Fakes never live in main sources, and use-case tests build the service with `new`, its fakes and `Clock.fixed(...)`.

## 11. Frontend architecture (short)

- Feature folders (`catalog`, `cart`, `checkout`, `account`, `admin`), each with its components, hooks and API calls.
- API client generated from the OpenAPI contract; server state with TanStack Query; no business rules in components (the server is the source of truth for prices, discounts, shipping and stock).
- Mobile-first components, shared design tokens for the warm pastel palette, accessible drawer and wizard patterns.
- Texts through i18n resource bundles (Spanish).
- **TypeScript only.** Frontend and tooling: TypeScript only (.ts/.tsx). No .js/.jsx/.mjs/.cjs files, including configs and scripts. If a tool cannot read a TS config, use JSON. Node >= 22.18 or tsx for scripts.
  - TypeScript is pinned to 5.9: typescript-eslint supports `<6.1` and openapi-typescript `^5`, so TypeScript 6 and 7 wait until they do.
  - `tsconfig` with `strict: true`, `allowJs: false` and `noUncheckedIndexedAccess: true`.
  - Tool configs in `.ts` (for example `vite.config.ts`, `playwright.config.ts`).
  - The API client is generated from the OpenAPI contract with `openapi-typescript`.
  - End-to-end tests (Playwright) and load tests (k6) are written in TypeScript too.
  - CI fails if `git ls-files` returns any `.js`, `.jsx`, `.mjs` or `.cjs` file outside `node_modules` and `dist` (added with the CI in `T-E0-05`).

### Design tokens

Taken as-is from the prototype (`docs/design/prototype/index.html`, read-only reference, never deployed) and defined once in `frontend/src/styles/tokens.css` as CSS variables with the same names. Styling uses plain CSS variables (no Tailwind), as the E0 plan says.

| Token | Value | Use |
|---|---|---|
| `--bg` | `#FFF8F0` | Page background (cream) |
| `--surface` | `#FFFFFF` | Cards, drawers, forms |
| `--ink` | `#4A3A3A` | Main text |
| `--ink-soft` | `#7A6A66` | Secondary text (4.89:1 on `--bg`; only 4.26:1 on `--rose-soft`, so not for small text there) |
| `--peach` / `--peach-soft` | `#FFD9C7` / `#FFEDE3` | Backgrounds, image placeholder |
| `--rose` / `--rose-soft` | `#F7C6CF` / `#FCE4E8` | Backgrounds, highlights |
| `--accent` | `#C2506A` | Buttons with white text (4.52:1), large text, focus ring. **Not for small text or links** (4.29:1 on `--bg`) |
| `--accent-hover` | `#A83F57` | Hover of `--accent` |
| `--accent-soft` | `#FCE4E8` | Soft accent background |
| `--accent-text` *(correction)* | `= --accent-hover` | Small text and links (5.67:1 on `--bg`, 4.95:1 on `--rose-soft`) |
| `--line` | `#F0DCD0` | Decorative separators only (1.26:1) |
| `--line-strong` *(correction)* | `#958178` | Borders of form controls (3.50:1 on `--bg`, 3.69:1 on `--surface`; WCAG 1.4.11 asks 3:1) |
| `--danger` | `#B84332` | Errors (5.13:1 on `--bg`; 4.48:1 on `--rose-soft`, avoid small text there) |
| `--radius-s` / `--radius-m` | `12px` / `16px` | Corners |
| `--maxw` | `1180px` | Maximum content width |
| `--ff-display` / `--ff-body` | `'Quicksand', sans-serif` | Weights 400, 500, 600, 700, self-hosted with `@fontsource/quicksand` (never Google Fonts: no visitor IP sent to a third party, no external dependency on the critical path) |

Breakpoints of the prototype: 640, 860 and 960 px, mobile-first. Motion respects `prefers-reduced-motion`, and hover effects go inside `@media (hover: hover)`. Product photos use the prototype's blurred placeholder (`.ph`) through the `BlurredImage` component, the same idea as the blurred placeholder of UC-06. `tokens.test.ts` checks the prototype values and the contrast ratios above.


## 12. Decisions that differ from the earlier Spanish specs

1. **English naming** for code, database and routes (see the glossary). The Spanish specs keep their business wording; the glossary maps both.
2. **Stock moves out of `variant`** into the `inventory` module: `stock_level` (`on_hand` and `reserved`) and the append-only `stock_movement`. The earlier `stock` and `stock_reservado` columns disappear from the variant table. There is no reservation table: an order's `RESERVATION`, `DEDUCTION` and `RELEASE` movements are the ledger and the order holds the expiry (T-E3-01).
3. **New `backoffice` and `platform` modules** to avoid cycles and to hold the shared kernel.
4. Base package is `com.pijamaparty.store`.
