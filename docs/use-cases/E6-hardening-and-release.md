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

## T-E6-02 Lighthouse, images, cache and lazy loading (RNF-11)

**Decided on 2026-10-18:**
- **The measure is a CI gate, not a one-off run.** Lighthouse (mobile, its default simulated slow 4G and mid-range phone) runs in CI on the home page, a category listing, a product page and the checkout with one product in the cart.
- **"Loads in under 3 seconds" means Largest Contentful Paint under 3,000 ms** on each of the four pages. The same run also asserts accessibility of 90 or more, the Lighthouse criterion of RNF-31.4. The rest of T-E6-25 is unchanged.
- **The run measures what production serves.** The production Caddy image (`deploy/caddy/Dockerfile`, with the real Caddyfile and the frontend build) sits in front of the e2e backend, so compression, HTTP/2 and the cache headers are measured too. Each product has a real uploaded photo converted by libvips, because the photo is the largest paint of the listing and the product page.
- **Already in place, kept:** photos as WebP at 400, 800 and 1200 px with `srcset` and `sizes` (UC-06); lazy photos with the blurred placeholder; the main photo of the product page loaded eagerly; the back office in its own chunk; a one-year immutable cache for `/assets/*`.
- **Optimizations:**
  1. **Cache:** `/uploads/*` gets a one-year immutable cache, since every upload gets a new server-generated file name (`StorageKey`, RNF-29.4) and a file never changes. Everything else in the frontend build except `/assets/*` (that is, `index.html`) gets `no-cache`, so a deploy shows at once.
  2. **Largest paint first:** the first four cards of a listing load their photos eagerly. The first two (the first row on a phone) and the main photo of the product page also get `fetchpriority="high"`.
  3. **Smaller first download:** the checkout, its result page, order tracking, the withdrawal form, the FAQ and every customer account page load on demand, as the back office already does. The catalog, the product page and search stay in the main bundle, since shoppers arrive on those pages from links.
  4. **Fonts:** `index.html` preloads the Latin file of Quicksand 500, the body text weight. The 400 weight is dropped: one back office rule asked for it and now asks for 500, and form controls, which default to 400, show 500.

**Design notes**
- **Where it lives:** the `e2e/` project, as a second Playwright configuration (`playwright.lighthouse.config.ts`, `npm run lighthouse`) with the `lighthouse` npm package. It reuses the administrator setup and `AdminApi`, and leaves `npm test` (T-E6-01) unchanged.
- **Servers:** the e2e backend (`E2eStoreApplication`) and the Caddy image, started with `DOMAIN=localhost` on `https://localhost:8443`. The container reaches the backend on the host as `api`, and serves the backend's e2e uploads directory as `/uploads`. Caddy's local certificate is accepted only by this test browser.
- **The photo** is a PNG the test draws itself (a gradient with noise, about as hard to compress as a real photo), uploaded through the back office API, so no binary file lives in the repository.
- **Cold load:** before each page is measured the test clears the browser cache. It does not clear cookies, so the checkout keeps its cart.
- **libvips:** the CI job installs it, as the backend job does. Locally the Lighthouse run needs `vips` on the PATH or `VIPS_BIN_DIR`, like `VipsImageProcessorContractTest`.
- **Reports:** the HTML report of each page goes to `e2e/lighthouse-report/` and becomes a CI artifact on every run.

**As built (2026-10-18)**
- First local run (Windows, Docker Desktop, libvips 8.18.7), against the production Caddy image:

  | Page | LCP | Accessibility | Performance |
  |---|---|---|---|
  | Home | 2,215 ms | 100 | 94 |
  | Category listing | 2,206 ms | 100 | 96 |
  | Product page | 2,413 ms | 100 | 95 |
  | Checkout, one product | 1,865 ms | 100 | 94 |

- The main bundle went from 159 KB to about 151 KB gzipped. Most of what remains is React, the router and the query library.
- The frontend tests' `renderRoute` loads the on-demand pages up front, so page tests render synchronously. `routes.test.tsx` checks which routes are on demand and opens one by URL with the real routes.
- `@photo file` in the Caddyfile limits the immutable header to photos that exist; a missing photo's `404` is not cached.

**Acceptance criteria**
- Given the home page, a category listing, a product page and the checkout with one product, when Lighthouse measures them in mobile mode against the production Caddy image, then each one has Largest Contentful Paint under 3,000 ms and accessibility of 90 or more.
- Given a push to `main`, when CI runs, then the `lighthouse` job runs those measures and fails the build if any is missed.
- Given an uploaded photo, when Caddy serves it under `/uploads/`, then it carries `Cache-Control: public, max-age=31536000, immutable`; given `index.html`, then it carries `Cache-Control: no-cache`.
- Given a listing, when it renders, then the photos of the first four cards load eagerly and those of the first two have `fetchpriority="high"`; the rest load lazily.
- Given a shopper who opens the home page, when the main bundle loads, then it holds no code of the checkout, order tracking, withdrawal, FAQ or account pages; those pages still work when opened directly by URL.

---

## T-E6-03 Security headers, sign-in limits and logs without personal data (RNF-15)

**Review (2026-10-18):**
- **Headers:** HSTS, CSP, `X-Content-Type-Options`, `Referrer-Policy` and `Permissions-Policy` are set by Caddy on every answer, error pages included (RNF-29.9).
- **CSP and the payment providers:** none is needed. The storefront sends the buyer to MercadoPago's and MODO's checkout with a top-level navigation (`window.location.assign`), and to Google and Facebook in the same way (UC-27). CSP does not restrict top-level navigation, and the store loads no provider script, frame or form. A provider SDK, if one is ever added, needs its domains then.
- **Sign-in limits:** the `login-ip`, `login-account`, `account-email` and `social-sign-in` policies match RNF-22 and RNF-29.5. Nothing changes.
- **Log statements:** all 80 were read. They log order numbers, provider ids, HTTP statuses and exception types. One exception: the three failures of `ExternalSignInHandlers` logged the exception as it was, and a database error there may quote the customer's email.
- **Log retention:** the Docker `json-file` driver already bounds each container's logs to 5 files of 10 MB (`deploy/docker-compose.yml`).

**Decided on 2026-10-18:**
- **Logs: a safety net under every log line, not only the store's.** The console layout (`logback-spring.xml`) scrubs every message with `Pii.scrub` and every exception with `Pii.scrubException`, so Spring's, Hibernate's and Tomcat's lines go through the same masking as the store's. The three call sites above pass their exception through `Pii.scrubException` too. Addresses still cannot be detected in free text, so the rule of never logging them stays.
- **The headers are tested against the production Caddy image**, in the Lighthouse run of T-E6-02, which already starts it: the headers on `index.html`, the API and a photo, and no CSP violation on the home page, a listing, a product page and the checkout. The 503 page of an unreachable backend is not tested there, since the run needs the backend up. The analytics domains are not exercised: the test image is built without analytics ids, and their check is T-E6-16.
- **CSP:** unchanged; the Caddyfile's comment now says why the payment providers need no entries.

**Acceptance criteria**
- Given a response of the storefront, of the API or of a photo through Caddy, when it arrives, then it carries `Strict-Transport-Security`, the `Content-Security-Policy`, `X-Content-Type-Options: nosniff`, `Referrer-Policy` and `Permissions-Policy`, and no `Server` header.
- Given the home page, a listing, a product page and the checkout served by Caddy, when they load, then the browser reports no CSP violation.
- Given a log line whose message or exception (with its causes) holds an email or a national ID, when it is written, then they are masked, whichever library wrote it.
- Given a sign-in with Google or Facebook that fails with a database error quoting the email, when it is logged, then the email is not in the log.

---

## T-E6-07 Link previews of products (Open Graph, RNF-31.2)

**Problem:** WhatsApp, Facebook, Instagram, X and Telegram build the preview of a shared link from the page's HTML without running JavaScript. Every path of the single-page app answers the same `index.html`, so a shared product shows no name, photo or price.

**Decided on 2026-10-19:**
- **Only the link-preview bots get a different answer.** Caddy recognizes them by their user agent and sends `/producto/{slug}` to a small public endpoint of the API that answers a minimal HTML page with the tags. Shoppers keep getting the single-page app straight from Caddy, so the product page's speed (T-E6-02) does not change.
- **Not prerendering at deploy time:** the owner edits products, prices and photos every day, and prerendered pages would be stale until the next deploy.
- **Not every product request through the API:** it adds a backend round trip to every visit and ties the API to the frontend build's `index.html`.
- **Google is not a preview bot.** It runs JavaScript, so it keeps getting the single-page app; the structured data of T-E6-23 is decided there.

**Design notes**
- **Endpoint:** `GET /api/catalog/products/{slug}/preview`, `text/html; charset=UTF-8`, public, under the `catalog` rate-limit policy. It reads the product through UC-03 (`GetProductDetail`), so it previews exactly what a visitor can see; the controller only translates the result into HTML.
- **The tags** of a product visitors can see (`200`):
  - `<title>` and `og:title`: `{name} | Pijama Party Santa Fe`; `og:site_name`: `Pijama Party Santa Fe`; `og:type`: `product`; `og:locale`: `es_AR`.
  - `og:description` and `description`: the price, then the product's description cut at a word to at most 200 characters with `…`. The price is the lowest of the sellable variants, written as the storefront writes it (`$ 12.500`, cents only when there are some), with `Desde` in front when the variants' prices differ (as the product page shows it before a size and color are picked).
  - `og:url` and `<link rel="canonical">`: `{store.public-url}/producto/{slug}`.
  - `og:image`: the absolute URL of the product's first photo, in the 1200 px file (the largest one if the photo is smaller), with `og:image:alt` set to the product's name; no image tag without photos. `twitter:card` is `summary_large_image` with a photo and `summary` without one.
  - `product:price:amount` (pesos with two decimals, `12500.00`) and `product:price:currency` `ARS`.
  - The body has the name, the price and a link to the product's URL, for a visitor whose browser happens to carry a bot's user agent.
- **A product visitors cannot see** (unknown, hidden, of an inactive category or unsellable; UC-03's `NotFound`): `404` with the store's name as title and `og:title`, and `og:url` of the home page, so the preview shows the store instead of an error.
- Every text is escaped as HTML, and the Spanish texts come from `messages.properties`. No personal data is involved.
- **Caddy** (`deploy/caddy/Caddyfile`): a path `^/producto/{slug}/?$` (the slug format of `Slug`) whose `User-Agent` holds `facebookexternalhit`, `facebookcatalog`, `WhatsApp`, `Twitterbot` or `TelegramBot` is rewritten to the endpoint and proxied like the rest of the API. Instagram's previews come from `facebookexternalhit`. The in-app browsers of Instagram and Facebook carry `Instagram`, `FBAN` or `FBAV`, not those names, so shoppers inside those apps get the store as usual. Answers under `/producto/` carry `Vary: User-Agent`.
- **Photos are WebP only (RNF-29.4).** Whether Facebook and WhatsApp show a WebP preview is not checked here: it needs the real domain and real phones (T-E6-16). If one of them does not show it, a JPEG file only for previews is the fix, decided then.
- **Not covered:** the home page and the category listings keep the general tags of `index.html`; the request was product links.

**As built (2026-10-19)**
- `ProductPreviewController` in `catalog.adapter.in.web`; its texts are the `catalog.preview.*` keys of `messages.properties`. The tests are in `CatalogApiTest`.
- The Caddyfile's proxy settings moved into an `(api)` snippet, shared by `/api/*` and the previews, so the client-IP and timeout rules (RNF-21, RNF-22) stay in one place.
- `e2e/lighthouse/previews.spec.ts` runs in `npm run lighthouse`, against the production Caddy image. It sends the user agents of Facebook's and WhatsApp's link fetchers, and of Chrome and the Instagram and Facebook in-app browsers on a phone.

**Acceptance criteria**
- Given a listed product with a photo and variants of different prices, when its preview is requested, then the answer is `200` HTML whose tags carry its name, `Desde` and the lowest price, its description cut at 200 characters at most, its canonical URL and the absolute URL of its first photo's 1200 px file.
- Given a product whose variants share one price, when its preview is requested, then the price has no `Desde`; given a product without photos, then there is no `og:image` and `twitter:card` is `summary`.
- Given a name or description with `<`, `>`, `&` or quotes, when the preview is rendered, then they are escaped.
- Given a hidden or unknown product, when its preview is requested, then the answer is `404` HTML with the store's name and the home page as `og:url`.
- Given the production Caddy image, when `/producto/{slug}` is requested with the user agent of Facebook's or WhatsApp's link fetcher, then the answer is the preview; with a phone browser's user agent or Instagram's in-app browser's, then it is the single-page app's `index.html`.

---

## T-E6-14 Load test with k6 (RNF-16)

**Scope:** the repository part. The scripts are written and run locally now. Running them on the real server, measuring the memory budget of RNF-21 and adjusting the values of RNF-21 and RNF-22 from the results are blocked by `T-E0-07` to `T-E0-10` (task group 2), so the task stays open.

**Decided on 2026-10-19:**
- **The local run uses the Lighthouse stack:** the production Caddy image on `https://localhost:8443` in front of the e2e backend (`E2eStoreApplication`), with the MercadoPago and Resend stubs of T-E6-01. The checkout and webhook scenarios need no network, and Caddy turns a full Tomcat queue into the `503` of RNF-21.
- **Every shopper has its own IP.** From one machine, every virtual user would share one IP, and the `catalog` limit (120 a minute per IP, RNF-22) would answer most of the 50 browsing shoppers with `429`. So the browsing, checkout, webhook and saturation scenarios call the backend directly, each shopper with its own `X-Forwarded-For` from the benchmarking range `198.18.0.0/15` (RFC 2544). The dev profile trusts that header from private addresses, as Tomcat does by default. The rate limits stay on as configured. Only the rate-limit scenario goes through Caddy, which overwrites the header, so every request it sends counts for one IP. No production code or configuration changes.
- **k6 runs from its Docker image, pinned by digest** (`grafana/k6:2.3.0`), which runs the TypeScript scripts itself: nothing to install. **Not in CI:** shared runners make latencies noisy, and the figures that tune RNF-21 and RNF-22 come from the server.

**Scenarios** (spec, "Prueba de carga (k6)"); each one is a k6 script in `e2e/load/k6`:
1. **Browsing** (`browse.ts`): 50 shoppers at once for 2 minutes (20 seconds of ramp-up). Each one opens the home page (categories and products), a category listing with its filters (sorted by price now and then), a product with its reviews and the shipping costs, and one in four also searches (suggestions, then results). Between pages a shopper waits 1 to 3 seconds. Threshold: p95 of the catalog requests under 400 ms, and no failed request.
2. **10 simultaneous checkouts** (`checkout.ts`): 10 buyers, each with its own IP and a cart prepared beforehand, confirm at the same moment. Each sends its confirmation twice at once with the same `Idempotency-Key`, as a double tap does (RNF-20.1, RNF-20.2). Each answer must be the order (`201`) or `409 IDEMPOTENCY_KEY_IN_USE`. The second kind is repeated after its `Retry-After`, as the storefront does, and must get the same order. Afterwards, there are exactly 10 orders awaiting payment and 10 units reserved: available stock down by 10, units on hand unchanged.
3. **The same webhook** (`webhooks.ts`): the MercadoPago stub approves the payment of each of those 10 orders without notifying the store and hands over the signed notification. k6 delivers each one 5 times at the same moment. Every copy is answered `200`. Afterwards, the 10 orders are `PAID` and the units on hand went down by exactly 10, with available stock unchanged since the reservation. Each buyer gets exactly one confirmation email (RNF-20.3 to RNF-20.5, RNF-20.7).
4. **The last unit** (`last-unit.ts`): 20 buyers, each with the only unit of one variant in its cart, confirm at the same moment. Exactly one gets `201`; the other 19 get `409` (`CART_NOT_READY` or `INSUFFICIENT_STOCK`). Afterwards, available stock is 0 and the unit on hand is the one reserved.
5. **Extreme saturation** (`saturation.ts`): for 30 seconds, 300 shoppers ask the API directly for listings with no pause, each from its own IP so no rate limit stops them first. Meanwhile 50 more do the same through Caddy. A direct answer must be `200` or `503`, and a connection refused once Tomcat's queue is full is counted apart. Through Caddy, the answer must be `200`, `429` (that client's own IP limit) or `503`. Every `503` must carry `Retry-After` in problem+json: Caddy's for a refused or timed-out backend, the API's for an exhausted connection pool (RNF-21). The `503`s and refused connections are counted, not required (see *As built*). Once the queued requests have drained, `/api/health` must be up within 30 seconds and a listing must be served in under a second.
6. **Rate limits through Caddy** (`rate-limits.ts`): one client goes over each per-minute limit a visitor reaches (coupon, checkout, cart, payment retry, tracking link, search, catalog, shipping costs, sign-in, return from Google or Facebook) and gets `429` with `Retry-After` in problem+json, within the policy's capacity. Some policies share buckets (a search also counts for the catalog), so the order of the routes makes each one reach its own limit first. The client sends a forged `X-Forwarded-For`, which Caddy replaces. Left out, with backend tests of their own (`RateLimitPoliciesTest`, `BackOfficeRateLimitTest`): the hourly policies, which would lock the IP out for an hour, and the ones keyed by a signed-in user.
- **Memory (RNF-21):** not measured locally. The e2e backend runs without `-Xmx` and its PostgreSQL container has the image defaults, so the figures would say nothing about the server. It is measured in the server run, below.

**Design notes**
- **Where it lives:** the `e2e/` project, as a third Playwright configuration (`playwright.load.config.ts`, `npm run load`). Playwright starts the stubs, the backend and the Caddy container (reusing `lighthouse/caddy.setup.ts`), signs the administrator in (`tests/admin.setup.ts`) and seeds the catalog through the back office: three categories of six products, plus one product for the checkouts (100 units) and one with a single unit. `load/load.spec.ts` runs one k6 script per scenario, in order, and after each one checks what the database kept through the back office and the stubs: orders, stock and emails. k6 sees the answers, not the stock or the emails.
- **k6 in Docker** (`load/k6.ts`) joins the network of the Caddy container, so Caddy is `https://localhost` and the backend is `http://api:8080`, the same address Caddy uses. A missed threshold or check makes k6 exit with a non-zero code, and the scenario fails.
- **Cookies by hand** (`load/k6/shopper.ts`): the backend marks the cart and CSRF cookies `Secure`, and k6's own jar does not send them to the backend over plain http. So each shopper keeps its cookies and sends them itself, with the CSRF token in `X-XSRF-TOKEN` (RNF-29.1). Shoppers prepared in a script's `setup()` are handed to the virtual users with their cookies, so the confirmations really start at the same moment.
- **The k6 scripts typecheck** with `@types/k6` and their own `load/k6/tsconfig.json` (no Node types), as part of `npm run typecheck`.
- **The MercadoPago stub** gains `POST /__e2e/payments`: it approves the payment of an order's latest preference and answers the signed notification (path, headers, body) without sending it.

**As built (2026-10-19)**
- Local runs (Windows, Docker Desktop, the e2e backend on the host):

  | Scenario | Result |
  |---|---|
  | Browsing, 50 shoppers, 2 minutes | 8,159 requests (53 a second); catalog p95 26 ms (max 79 ms); no failure |
  | 10 checkouts, each sent twice | 10 orders; 9 of the 20 confirmations got `409 IDEMPOTENCY_KEY_IN_USE` and their retry got the same order; 10 units reserved |
  | 10 notifications × 5 copies | 50 × `200`; 10 orders `PAID`, 10 units deducted, one email per buyer |
  | Last unit, 20 buyers | 1 order, 19 × `409` |
  | Saturation, 300 direct + 50 through Caddy, 30 s | about 23,000 requests; 11,000 refused connections, the rest `200` or `503`; served again once drained |
  | Rate limits through Caddy | `429` with `Retry-After` on each of the 10 routes |

- **Saturation, what the API does:** the listings are too fast to exhaust the 8 connections of the pool, so the API itself never answered `503` in these runs. Its `503` stays covered by `SaturationTest`. The overload shows up as Tomcat's full queue instead: direct connections are refused, and a request that got into the queue may wait up to 40 seconds for its answer. That is longer than Caddy's `response_header_timeout` of 30 seconds, so behind Caddy the shopper gets the `503` sooner. Through Caddy, the result depends on its open connections to the API. On a fresh start every request of the flood got Caddy's `503`. When Caddy already had open connections, it reused them, its requests were answered, and that one client IP got `429`. So the `503`s are counted, not required. The server run reads `saturated_503` and `refused_connections`.
- **Refill:** the buckets refill gradually (`catalog` gets a token back every half second), so a policy may let a few requests past its capacity while the test sends them. As `RateLimitPoliciesTest` does, the `429` may come up to 10 requests late.
- **Observed once:** in one 2-minute browsing run, 7 of about 8,000 requests timed out while connecting to the backend through Docker Desktop's host gateway, during the ramp-up. They never reached the API, and the run after it was clean. The threshold stays strict (no failed request); if this comes back on the server, it is a finding there.
- The k6 runner hides the progress bars and sums up k6's "Request Failed" warnings (thousands under saturation) after the first five. The shoppers do not follow redirects, since an API answer is the answer.

**Acceptance criteria**
- Given 50 shoppers browsing at once, each from its own IP, when the catalog is measured, then its p95 is under 400 ms and no request fails.
- Given 10 buyers who confirm at the same moment, each twice with one `Idempotency-Key`, when the confirmations end, then there are exactly 10 orders, one per key, and exactly 10 units reserved.
- Given 20 buyers confirming the last unit of a variant at once, when the confirmations end, then exactly one order exists and the other 19 got `409`.
- Given the notification of each of those 10 orders delivered 5 times at once, when they are processed, then every copy gets `200`, each order is `PAID` once, 10 units are deducted and each buyer gets one confirmation email.
- Given a client over the limit of a per-minute route a visitor reaches, through the production Caddy image, when it sends one more request, then the answer is `429` with `Retry-After` in problem+json.
- Given far more shoppers than the pools hold, when they flood the API, then every answer is `200`, `429` (through Caddy) or `503` with `Retry-After` in problem+json, and once the flood has drained the API is up and serves a listing in under a second.

**On the server (pending, group 2):** the same scripts against the test environment of `T-E6-09`, with `docker stats` of `api` and `db` sampled during the run for the memory budget of RNF-21 (JVM about 1.1 GB, PostgreSQL about 1 GB). How the shoppers' own IPs reach the API there (Caddy overwrites the header, and the API publishes no port) is decided then. Then RNF-21 and RNF-22 are adjusted from the results.

---

## T-E6-18 Cross-access (IDOR) and Row Level Security tests (RNF-27)

**Scope:** every route that takes an identifier or a token of something a buyer owns, tried by the people who do not own it, plus a guard over the tables. No production code changes: when a case fails, the fix goes in the use case of that route first.

**Decided on 2026-10-19:**
- **One backend test over real HTTP** (`CrossAccessApiTest`), with real sessions, the CSRF token of the SPA, Row Level Security and PostgreSQL (Testcontainers), as `CustomerOrdersApiTest` does. It runs in `./mvnw verify`, so in CI. Not in Playwright: none of it needs the storefront.
- **Four people:** customers Ana and Bruno (password sign-in), a guest with its own cart and the tracking link of its order, and the administrator (password and second factor, enrolled in the test).
- **"The database returns no rows" is proved with the query the application would run without its owner filter.** For each case, the test runs, as the intruder and through the application's transaction manager (`RlsPolicyTester`), a select, update or delete of the victim's row **by its identifier only**. It must touch 0 rows. So a route that forgot its filter would still leak nothing.
- **The answers stay as they are:** another person's order, address or cart item answers `404` problem+json (the same as one that does not exist, so its existence is not revealed); a wrong tracking token answers `404 ORDER_NOT_FOUND`; not signed in on `/api/account/**` or `/api/admin/**` answers `401`; signed in with the other role answers `403`. The test makes them uniform across the routes below.
- **A guard over the tables** (`RowLevelSecurityCoverageTest`): the tables with `ENABLE` and `FORCE ROW LEVEL SECURITY` must be exactly the protected list below, and every other table of the schema must be on the public list, each with its reason. A new table fails the build until it is put on one of the two lists on purpose.

**Cases** (spec, "Seguridad de datos"):
1. **Orders:** Ana opens Bruno's order at `/api/account/orders/{id}` → `404 ORDER_NOT_FOUND`, without any of Bruno's data; her list does not show it. The guest's order, opened by Ana by its id, is not found either. As Ana and as the guest, the database sees none of Bruno's `orders` or `order_item` rows, nor updates his order. (The checkout's token lives in `orders`; the extra tokens of `order_tracking_token` have their own test, `RepeatedCheckoutTrackingRowLevelSecurityTest`.)
2. **Addresses:** Ana edits and deletes Bruno's address at `/api/account/addresses/{id}` → `404 ADDRESS_NOT_FOUND`, and Bruno's address is unchanged. As Ana, the database reads, updates and deletes 0 of Bruno's `address` rows.
3. **Cart items:** Ana changes and removes an item of Bruno's cart at `/api/cart/items/{id}` → `404`, and the guest, with a cart of its own, does the same with an item of Ana's cart; every cart is unchanged. (A guest with no cart at all gets `403`: it cannot have one yet.) As the intruder, the database sees none of the victim's `cart` or `cart_item` rows.
4. **Reviews:** Bruno has a review waiting for moderation. Ana asks for her review of that product at `/api/account/reviews/{productId}` and gets none. As Ana, the database does not see Bruno's `review` row.
5. **Tracking link:** the guest's token opens the guest's order and no other. A well-formed token that is not the order's, on the read, the payment retry and the email correction, answers `404 ORDER_NOT_FOUND`, and the order's email is unchanged. Ana's session does not open Bruno's order without its token.
6. **Payments and checkout keys:** as Ana, the guest and the administrator, the database sees none of the `idempotency_key` rows of the others. As Ana and the guest, none of the `payment` rows.
7. **The two sides:** Ana and the guest on `/api/admin/orders/{id}` → `403` and `401`; the administrator on `/api/account/orders/{id}` → `403`. The administrator opens Bruno's order in the back office (`200`): reading every order is its job (RNF-27 table). As the administrator, the database sees no `cart` or `cart_item` row.

**Protected tables** (`FORCE ROW LEVEL SECURITY`): the RNF-27 list (`orders`, `order_item`, `payment`, `payment_event`, `address`, `app_user`, `cart`, `cart_item`, `review`, `idempotency_key`, `email_outbox`, `email_event`, `external_identity`, `user_token`, `withdrawal_request`) plus `admin_recovery_code`, `order_tracking_token`, `payment_checkout`, `pending_link` and `activity_entry` (the back-office activity log, UC-37: only the administrator reads it, T-E8-01).

**Public tables, with their reason:**
- `category`, `product`, `variant`, `product_image`, `size_guide`, `home_banner`, `stock_level`, `shipping_zone_rate`, `shop_setting`: the catalog, the home page's banner (UC-32) and the store's settings, public and without personal data (RNF-27).
- `coupon`, `coupon_scope`, `coupon_redemption`: coupons and their uses, by order id only, without personal data.
- `stock_movement`: quantities and the administrator's note, by variant, order and administrator id, without customer data.
- `spring_session`, `spring_session_attributes`, `event_publication`, `event_publication_archive`: out of scope with their reasons in RNF-27.
- `flyway_schema_history`: the application has no privilege on it (`DatabaseRolesTest`).

---

## T-E6-23 Search engines: sitemap, robots, canonical links and structured data (RNF-31.1, RNF-31.2)

**Decided on 2026-10-20:**
- **The structured data lives in the single-page app only.** Google runs JavaScript (T-E6-07), so the product page writes it; the link-preview bots do not read it, so the preview of T-E6-07 stays as it is. RNF-31.2's "shared prerendering" was dropped by T-E6-07's decision not to prerender; there is nothing to share.
- **One `Product` with one `Offer` per sellable variant**, so each variant's SKU, price and stock are there as RNF-31.2 lists them; not `AggregateOffer`, which loses them, nor `ProductGroup`, more code for the same data.
- **The sitemap comes from the API, `robots.txt` from Caddy.** The sitemap changes with the catalog, so the backend writes it from the same rules as the listing; `robots.txt` is fixed but for the domain, which Caddy knows.
- **The descriptions are drafted by the developer** in `es.ts` and reviewed by the owner; changing their wording later is a text change, not a decision.

**Design notes**
- **Sitemap:** `GET /api/catalog/sitemap` answers `application/xml` (sitemaps.org 0.9), public, under the `catalog` rate-limit policy; Caddy rewrites `/sitemap.xml` to it. It lists, as absolute URLs of `store.public-url`: the home page, the FAQ (`/preguntas-frecuentes`), every active category (`/categoria/{slug}`, by position) and every listed product (`/producto/{slug}`, newest first). Listed means what UC-01 lists: published, of an active category, with an active priced variant. No `lastmod`: `product.updated_at` does not move when a variant's price or stock does, and a wrong date is worse than none. At most 49,000 products (the format allows 50,000 URLs per file; the shop has a few hundred). Read through a use case (`ListSitemapPages`) and a read port of its own (`ListedProductSlugs`).
- **`robots.txt`** (Caddy, `deploy/caddy/Caddyfile`): `User-agent: *`, `Disallow` for `/admin`, `/cuenta`, `/checkout`, `/pedido`, `/buscar` and `/api/`, and `Sitemap: https://{$DOMAIN}/sitemap.xml`. The cart is a side panel without a path, so there is nothing to exclude for it.
- **`noindex`** (`<meta name="robots" content="noindex">`): the store layout adds it on every path under the excluded prefixes above (the back office already has it), and the 404 page has it. Search results are excluded because each query would be a thin duplicate of the listing.
- **Title, description and canonical on every indexable page** (home, category, product, FAQ, withdrawal): the title stays as it is now, but for a category, whose tab title was the generic "Productos" and becomes `{name} | Pijama Party Santa Fe` once the categories are loaded (the heading on the page does not change); the description and `<link rel="canonical">` (the page's origin and path, without the query string, so filters, sort and pages point to the bare listing) are rendered by one component. The descriptions:
  - Home: a fixed text about the store.
  - Category: a template with the category's name.
  - Product: as its preview (T-E6-07): the price, with `Desde` when the variants' prices differ, then the description on one line cut at a word to at most 200 characters with `…`.
  - FAQ and withdrawal: fixed texts.
- **Structured data of a product** (`<script type="application/ld+json">`, rendered with the page once the product is found; the content security policy does not apply to it, since it is not run):
  - `Product`: `name`, `description` (the full description on one line), `url` (canonical), `image` (absolute URLs of the 1200 px file of every photo, or its largest one; left out without photos), `brand` `Pijama Party Santa Fe`.
  - `offers`: one `Offer` per variant of the product page, with `sku`, `price` (pesos with two decimals, `12500.00`), `priceCurrency` `ARS`, `availability` `https://schema.org/InStock` when `available` > 0 and `https://schema.org/OutOfStock` otherwise, and `url` (canonical).
  - `aggregateRating` (`ratingValue`, `reviewCount`) from UC-24's `average` and `count`, only when there is at least one approved review; not while the reviews are loading or failed to load.
  - Every text goes through `JSON.stringify`, and `<` is written as `<`, so a name or description cannot close the script element.

**Acceptance criteria**
- Given an active and an inactive category and a listed, a hidden and an unsellable product, when the sitemap is requested, then it is `200` XML listing the home page, the FAQ, the active category and the listed product as absolute URLs, and neither the inactive category nor the other two products.
- Given the production Caddy image, when `/robots.txt` is requested, then it disallows the back office, account, checkout, tracking, search and API paths and points to the sitemap; when `/sitemap.xml` is requested, then the API's sitemap answers.
- Given an account, checkout, tracking or search page, or an unknown path, when it renders, then it carries `noindex`; given the home, a category, a product, the FAQ or the withdrawal page, then it carries its description and a canonical URL without the query string, and no `noindex`.
- Given a product with two variants of different prices, one with stock and one without, and approved reviews, when its page renders, then its structured data is a `Product` with two `Offer`s carrying their SKUs, prices, `ARS` and `InStock` and `OutOfStock`, and an `aggregateRating` with the average and the count; without reviews, then there is no `aggregateRating`.
- Given a product name with `</script>`, when its page renders, then the structured data stays one script element whose JSON reads back the same name.

**As built (2026-10-20)**
- Backend: `ListSitemapPages` and its port `ListedProductSlugs` (`JdbcListedProductSlugs`; the in-memory fake is `InMemoryProductListing`) in `catalog`, and `SitemapController`. Tests: `ListedProductSlugsContractTest` (both implementations), `ListSitemapPagesServiceTest` and the sitemap case of `CatalogApiTest`.
- Frontend: `features/seo` (`NoIndex`, now shared with the back office, `PageMeta`, `privatePaths.ts`, `productMeta.ts` and `ProductStructuredData`); the texts are `es.seo`. The category page and the search's links share `useShopCategories`. Tests: `features/seo/seo.test.tsx`.
- `e2e/lighthouse/seo.spec.ts` runs in `npm run lighthouse`, against the production Caddy image: `robots.txt`, `/sitemap.xml`, the product page's tags and structured data, and `noindex` on private paths.

---

## Remaining tasks

Written when each one starts, with its decisions. Known inputs so far:
- **T-E6-06:** the texts come from the business (RNF-30, validated in T-E6-21); `store.ordering.terms-version` changes whenever they change (RNF-30.6).
- **T-E6-24:** the mapping table of Treinta URLs comes from the business.
- **T-E6-25:** the router already has its own 404 (`NotFoundPage`); 500 and the maintenance page served by Caddy are missing (RNF-31.7).
