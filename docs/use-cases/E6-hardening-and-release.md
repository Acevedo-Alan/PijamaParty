# E6 – Hardening and release

**Status:** Draft · **Use cases:** none (technical and closing stage) · **Spanish counterpart:** `docs/specs/E6-publicacion` (spec, plan and tasks)

## Goal

Make the store ready for production: tested end to end, backed up, monitored and accepted by the business (RNF-10 to RNF-17). No new business behavior: every task here checks, hardens or publishes what E0 to E5 built. When a task finds that a use case must change, the use case is updated first, as in every stage.

## Task groups

The tasks keep the IDs and wording of `docs/specs/E6-publicacion/tasks.md`. They split by what they need:

1. **In the repository, now** (no server, no business input beyond the decisions noted per task):
   `T-E6-01` end-to-end tests, `T-E6-02` performance, `T-E6-03` security headers and logs, `T-E6-07` Open Graph previews, `T-E6-14` load test scripts, `T-E6-18` cross-access (IDOR) and Row Level Security tests, `T-E6-23` SEO, `T-E6-25` accessibility and error pages, plus the code side of `T-E6-06` (legal pages, texts from the business) and `T-E6-24` (redirects, mapping table from the business).
2. **Needs the real server** (blocked by `T-E0-07` to `T-E0-10`, still open in Part B of E0):
   `T-E6-04` backups and restore drill, `T-E6-05` uptime monitoring, `T-E6-09` test environment for the business, `T-E6-14` running the load test against the server, `T-E6-15`, `T-E6-16`, `T-E6-17`, `T-E6-19`, `T-E6-20`, `T-E6-26`.
3. **Business and closing** (the owner, a lawyer and an accountant; code only where noted):
   `T-E6-10` bug fixing during the 7 working days of business testing, `T-E6-11` real catalog and payment credentials, `T-E6-12` go-live, `T-E6-13` signed acceptance, `T-E6-21` legal validation and seller data in the footer, `T-E6-22` AAIP registration.

Each task's open decisions are settled with the owner when the task starts and written here, as T-E5-01 did for E5. Nothing below them is to be invented in code.

---

## T-E6-01 End-to-end tests of the critical flows (RNF-10)

**Decided on 2026-10-18:** the payment flows run against a **local stub of the MercadoPago API**, not the real sandbox. The real sandbox is exercised by hand in `T-E6-11` (real purchase) and `T-E6-15` (real server).

**Scenarios** (spec, "Pruebas de extremo a extremo"):
1. A guest buys with an approved card, end to end: catalog → product → cart → 3-step checkout → MercadoPago (stub) → result page → tracking page shows `PAID`; exactly one confirmation email.
2. **Rejected payment** (see *Spec conflict* below): the order stays `AWAITING_PAYMENT` with its stock reserved and the buyer can pay again from the same checkout; once the reservation expires, the order becomes `EXPIRED` and the stock is available again.
3. Two buyers check out the last unit of a variant at the same time: exactly one order is placed; the other buyer sees the out-of-stock refusal of UC-13.
4. Coupons: an unknown code, an expired coupon and a used-up coupon are each refused with their UC-10 message, and the total does not change.
5. A repeated webhook (the same MercadoPago notification sent twice): one `PAID` transition, one stock deduction, one confirmation email.
6. The administrator creates a category, a product with two variants, their prices and stock in the back office UI (UC-05 to UC-07) and publishes it; a guest, in another browser, buys one of those variants (as scenario 1).

**Design notes**
- **Where the tests live:** a new top-level `e2e/` project (Playwright, TypeScript only, its own `package.json`, `tsconfig.json` and `playwright.config.ts`), so the frontend's dependencies stay unchanged. Mobile viewport by default (mobile-first), Chromium only in v1.
- **The stack under test:** the real backend and the real frontend, no mocked API.
  - Backend: an `E2eStoreApplication` launcher in `backend/src/test` (like `TestStoreApplication`), with a throwaway PostgreSQL container and the profiles `dev,e2e`. The `e2e` profile lives in the test resources only, so nothing of it reaches the production jar. It sets:
    - `store.payments.mercadopago.base-url` and `store.notifications.resend.base-url` to the stubs, with a known access token, webhook secret and API key;
    - the outbox cron to every few seconds, so emails are sent within the test's timeout;
    - the initial administrator's password, known to the tests.
  - Frontend: the Vite build served by `vite preview` with `/api` proxied to the backend, so the tests see the production bundle.
  - Playwright's `webServer` starts the stubs, the backend and the frontend. Running everything with one command is part of the task.
- **The MercadoPago stub** (TypeScript, in `e2e/`) implements only what the real adapters call:
  - creating the preference (UC-14). Its `init_point` points to a stub checkout page with "approve" and "reject" buttons.
  - `GET /v1/payments/{id}` for `MercadoPagoPaymentVerifier` (UC-15).
  - The stub checkout page sends a webhook signed like MercadoPago's (`x-signature`, `x-request-id`) to `/api/payments/mercadopago/webhook`, then redirects to the `back_urls`. A test can ask the stub to send a notification again (scenario 5).

  The real `MercadoPagoGateway`, `MercadoPagoPaymentVerifier` and `MercadoPagoSignature` run unchanged. This needs no new provider and no change to production code; their contract tests already cover the port side.
- **The Resend stub** records every email it receives (recipient and subject in memory, never written to disk or logs), so scenarios 1 and 5 can count the confirmations.
- **Time** (scenario 2): the reservation lasts 30 minutes (`StockHold.LENGTH`). The `e2e` launcher replaces the `Clock` bean with an adjustable one, moved forward through a test-only endpoint that exists only in the `e2e` launcher (test sources). Production code keeps reading the injected `Clock` (CLAUDE.md, D).
- **The administrator's second factor** (scenario 6): the test enrolls the initial administrator and computes the TOTP codes itself from the secret shown at enrollment (RFC 6238 with `node:crypto`, no dependency).
- **Rate limits are not raised for the tests.** The suite signs in once and reuses the session (Playwright `storageState`), so it stays within the 5 sign-ins a minute of RNF-22.
- **CI:** a new `e2e` job in `.github/workflows/ci.yml` after the backend and frontend jobs, with the Playwright report uploaded as an artifact on failure.
- **Test data:** each test creates the data it needs through the back office API (as the administrator) or the UI. Nothing depends on the order the tests run in.

**As built (2026-10-18)**
- Run it from `e2e/` with `npm ci`, `npx playwright install chromium`, then `npm test`. It needs Docker for Testcontainers and the frontend's `npm ci`. Outside CI it reuses servers that are already running.
- Ports: the frontend on `4173`, the backend on `8080`, the MercadoPago stub on `8091`, the Resend stub on `8092` and the clock control on `127.0.0.1:8093`.
- `E2eStoreApplication`, `E2eConfiguration` and `AdjustableClock` live in `backend/src/test/java/com/pijamaparty/store`; the profile is `backend/src/test/resources/config/application-e2e.yml`.
  - The profile turns the expiry and outbox schedules back on, every 2 seconds, and turns the rate limits back on, all of which the integration tests' `config/application.yml` turns off.
  - The adjustable clock is a `@Primary` `Clock`. A JDK `HttpServer` on the loopback interface moves it, outside Spring Security and the application's port.
- The stubs live in `e2e/stubs`.
  - The checkout URL MercadoPago returns must be https (`Checkout.isValid`). So the stub hands out `https://mercadopago.e2e.test/...`, and each browser context routes that host to the stub's port.
  - The stub numbers payments from the current time, so a backend reused across runs never sees an id it already processed (T-E3-15 would rightly ignore it).
- The administrator signs in once (`tests/admin.setup.ts`, playing the authenticator app) and the tests reuse the saved session from the git-ignored `e2e/.auth`.
- **Observed while writing it:** on step 1 of the checkout, "Continuar a pago" does nothing while the shipping cost of the chosen province is still loading, with no message. The test waits for step 2. Whether the button should show it is busy is a UX decision for UC-13, not changed here.

**Spec conflict (fixed on 2026-10-18):** the spec said "Pago rechazado: el stock se libera". Since T-E3-07, a rejected payment keeps the reservation so the buyer can try another card, and the stock is released when the reservation expires (UC-15, UC-16). `docs/specs/E6-publicacion/spec.md` now reads *"Pago rechazado: el pedido conserva la reserva para reintentar el pago; al vencer la reserva, el stock se libera."* Scenario 2 tests that behavior.

**Acceptance criteria**
- Given the six scenarios above, when the suite runs locally with one command, then all pass without network access to MercadoPago or Resend.
- Given a push to `main`, when CI runs, then the `e2e` job runs the same suite and fails the build on any failure.
- Given the production profile, when the application starts, then none of the `e2e` beans (stubs' URLs, adjustable clock, test endpoint) exist, since they live in test sources only.

---

## Remaining tasks

Written when each one starts, with its decisions. Known inputs so far:

- **T-E6-02:** Lighthouse in mobile mode on home, listing, product page and checkout; the 3-second target is RNF-11. `/assets/*` already has a one-year immutable cache (Caddyfile).
- **T-E6-03:** HSTS, CSP, `X-Content-Type-Options`, `Referrer-Policy` and `Permissions-Policy` already exist in `deploy/caddy/Caddyfile` (RNF-29.9). The CSP still says "E3 adds the payment provider ones": check it against MercadoPago's and MODO's redirects. Sign-in rate limits exist (RNF-22). The task reviews them and audits every log statement for personal data.
- **T-E6-06:** the texts come from the business (RNF-30, validated in T-E6-21); `store.ordering.terms-version` changes whenever they change (RNF-30.6).
- **T-E6-07** and **T-E6-23** share one solution (RNF-31.2): prerendered product pages or a small endpoint that renders the tags. To decide when T-E6-07 starts.
- **T-E6-14:** k6 scripts in TypeScript, scenarios from the spec ("Prueba de carga (k6)"); the scripts can be written and tried locally before the server exists.
- **T-E6-24:** the mapping table of Treinta URLs comes from the business.
- **T-E6-25:** the router already has its own 404 (`NotFoundPage`); 500 and the maintenance page served by Caddy are missing (RNF-31.7).
