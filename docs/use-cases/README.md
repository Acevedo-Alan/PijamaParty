# Use cases (English, implementation-facing)

These documents translate and sharpen the Spanish specs in `docs/specs` for implementation. **IDs are shared:** `UC-03` here is `CU-03` there. The Spanish specs stay the business-facing source; if they disagree with these files, fix the disagreement before coding.

## Actors

| Actor | Meaning |
|---|---|
| Visitor | A person browsing without signing in |
| Guest | A visitor who buys without an account |
| Customer | A registered user |
| Administrator | The shop owner (single user) |
| System | Scheduled jobs, webhooks and event handlers |

## Template

Every use case has the same sections:

1. **Header:** ID, name, actor, module, stage.
2. **Preconditions.**
3. **Main flow** (numbered).
4. **Alternative and exception flows.**
5. **Business rules.**
6. **Acceptance criteria** (Given / When / Then). Each one becomes at least one test.
7. **Design notes:** inbound port (use case interface), outbound ports, aggregates, events, endpoint, error mapping. These tie the use case to `ARCHITECTURE.md`.

## Conventions

- One use case = one inbound interface in `<module>.application` with a `Command` or `Query` record and a sealed `Result`.
- Expected business failures are `Result` variants; the web adapter maps them to HTTP (`404`, `409`, `422`) as `problem+json`.
- Commands carry the `Actor`. Authorization is checked in the use case and enforced again by Row Level Security.
- Every endpoint named here is subject to the rate-limit policy of its route (RNF-22).

## Files

| File | Stage | Use cases |
|---|---|---|
| `E0-foundation.md` | E0 | none (technical foundation and architecture skeleton) |
| `E1-catalog-and-product-management.md` | E1 | UC-01 to UC-07 and UC-26 |
| `E2-cart-and-coupons.md` | E2 | UC-08 to UC-11 |
| `E3-checkout-and-payments.md` | E3 | UC-12 to UC-18 and UC-29 |
| `E4-customer-account.md` | E4 | UC-19 to UC-22, UC-27 and UC-28 |
| `E5-promotions-and-reviews.md` | E5 | UC-23 to UC-25 (and changes to UC-11 and UC-28) |

Stage E6 follows the same format and is written next.
