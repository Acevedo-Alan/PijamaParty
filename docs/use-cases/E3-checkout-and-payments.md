# E3 – Checkout, payments and orders

**Status:** Draft · **Use cases:** UC-12 to UC-18 and UC-29 · **Spanish counterpart:** `docs/specs/E3-checkout-pagos/spec.md`

**Modules involved:** `ordering`, `payments`, `shipping`, `inventory`, `cart.api`, `promotions.api`, `notifications`, `backoffice`, `platform`.

The highest-risk stage: stock must never be sold twice and a payment must never be charged twice. The cart (E2) did not reserve stock; the reservation starts when the buyer confirms the checkout and lasts 30 minutes. Stock is deducted for good only when the provider approves the payment. MODO is the sub-stage E3b (T-E3b-01, T-E3b-02); E3 delivers MercadoPago.

**Decisions settled before the stage (T-E3-01):**
- Reservation of 30 minutes, final deduction on an approved payment, and only immediate payment methods in v1: the MercadoPago checkout excludes cash slips and ATM payments (`ticket`, `atm`), which would hold stock for days.
- The national ID is a plain column of `orders`, protected by Row Level Security, the least-privilege role, encrypted backups and no logging (R-05, RNF-29.7). No column-level encryption.
- Shipping is one cost per province (the 24 jurisdictions); a province without a cost has no shipping.
- The order records the version of the terms the buyer accepted (RNF-30.6). The invoiced mark (RNF-30.7) waits for the invoicing decision.
- The checkout transaction runs as the buyer, not as the system (RNF-27): the guest or customer creates its own order, bound to the order's tracking token hash, so Row Level Security also guards the checkout path. Everything that does not belong to the buyer (the payment row, the provider call, the webhook, the expiry) runs as `SYSTEM`.

**Schema (T-E3-01, V14 to V17):**
- `orders` (`ordering` module; `order` is a reserved word): `id`; `number` (from the sequence `order_number_seq`, starting at 1001, unique, RNF-20.6; shown as `#1001`); `user_id` (a customer's order, E4; refers to `accounts`, no foreign key; set to null when the account is deleted, R-15); `tracking_token_hash` (SHA-256 of the tracking token, lowercase hex, unique; the token itself is never stored, RNF-29.6); `status` (the seven states of the glossary); the buyer's snapshot `customer_name`, `customer_email`, `customer_phone`, `national_id`; the shipping address snapshot `street`, `street_number`, `floor`, `apartment`, `city`, `province`, `postal_code` (R-04); the amounts `subtotal_cents`, `discount_cents`, `shipping_cents`, `total_cents` (total = subtotal − discount + shipping); `coupon_id` and `coupon_code` (both or neither; the coupon refers to `promotions`, no foreign key); `payment_provider` (`MERCADOPAGO`, later `MODO`); `terms_version`; `reservation_expires_at`; `shipment_tracking_number` (required from `SHIPPED` on); `created_at`, `updated_at` and `delivered_at` (set exactly when `DELIVERED`, for the UC-29 term). Indexed by status and date (UC-18 list), by customer, and by expiry of the orders awaiting payment (UC-16 job).
- `order_item` (`ordering` module): `id`, `order_id` (foreign key), `position` (the cart order), `variant_id` (refers to `catalog`, no foreign key) and the snapshot `sku`, `product_name`, `size`, `color`, `unit_price_cents` and `quantity` (R-04). One line per variant. Immutable: the application can neither update nor delete lines.
- `payment` (`payments` module): one row per order and provider: `id`, `order_id` (refers to `ordering`, no foreign key), `provider`, `amount_cents`, `status` (`PENDING`, `APPROVED`, `REJECTED`), `checkout_id` and `checkout_url` (the provider's checkout, a MercadoPago preference; null until it is created, outside the checkout transaction), `external_payment_id` (the provider's id of the last verified payment), `created_at`, `updated_at`. Each notification received is recorded in `payment_event` (T-E3-15).
- `shipping_zone_rate` (`shipping` module, no Row Level Security: no personal data): `province` (primary key), `cost_cents` (0 to 1,000,000,000), `updated_at`.
- `shop_setting` (`shipping` module, no Row Level Security): a single row with `free_shipping_threshold_cents` (null: no free shipping) and `updated_at`.
- `stock_level.reserved` (`inventory` module): the units reserved by orders awaiting payment, 0 ≤ `reserved` ≤ `on_hand`. The view `v_variant_availability` now returns `on_hand − reserved`. There is no reservation table: the movements `RESERVATION`, `DEDUCTION` and `RELEASE` of an order (unique per order, variant and type, RNF-20.4) are the ledger, and `orders.reservation_expires_at` is the expiry. Signs: `RESERVATION` and `DEDUCTION` negative, `RELEASE` positive.
- Row Level Security on `orders`, `order_item` and `payment`, enabled and forced (RNF-27), with the policies of T-E3-01 below. `idempotency_key` (T-E3-14), `payment_event` (T-E3-15), `email_outbox` and `order_tracking_token` (T-E3-09) and `email_event` (T-E3-22) bring their own policies with their tables. The full policy test matrix is T-E3-21.

**Row Level Security policies (T-E3-01):**

| Table | Customer | Guest (`ANONYMOUS`) | Administrator | System |
|---|---|---|---|---|
| `orders` | Reads its own (by `user_id`) and the one of its tracking token; creates its own, awaiting payment and bound to the tracking token of its context | Reads the one of its tracking token; creates one without a user, awaiting payment and bound to its tracking token | Reads all; updates only the status, the shipment tracking number and the delivery date | Reads all; updates only the status, the shipment tracking number and the delivery date |
| `order_item` | Follows its orders: reads them, adds lines to an order it can see | Same as the customer | Reads | Reads |
| `payment` | – | – | Reads | Reads, creates and updates |

Nobody deletes orders or payments (R-15). Correcting the email (UC-17) adds its own update policy for customers and guests in T-E3-23.

---

## UC-12 Configure shipping

**Actor:** Administrator · **Module:** `shipping` · **Stage:** E3

**Preconditions:** the administrator is signed in.

**Main flow**
1. The administrator sets a shipping cost per province.
2. Optionally sets the purchase amount from which shipping is free.

**Business rules**
- One cost per province, among the 24 jurisdictions of Argentina. A province without a cost has no shipping.
- A cost goes from $0 to $10,000,000 (0 to 1,000,000,000 cents). $0 is a free shipping to that province.
- The free-shipping threshold is optional; when set it goes from 1 to 1,000,000,000 cents. It compares with the subtotal after the discount.

**Acceptance criteria**
- Given a province without a cost, when a buyer picks it in the checkout, then it is told there is no shipping to that zone.
- Given free shipping from $50,000, when the subtotal after the discount is $50,000 or more, then the shipping cost is $0.

**Design notes**
- Aggregates `ShippingZoneRate` and `ShippingSettings` (`shipping.domain`). `Province` is an enum of the 24 jurisdictions: `BUENOS_AIRES`, `CIUDAD_DE_BUENOS_AIRES`, `CATAMARCA`, `CHACO`, `CHUBUT`, `CORDOBA`, `CORRIENTES`, `ENTRE_RIOS`, `FORMOSA`, `JUJUY`, `LA_PAMPA`, `LA_RIOJA`, `MENDOZA`, `MISIONES`, `NEUQUEN`, `RIO_NEGRO`, `SALTA`, `SAN_JUAN`, `SAN_LUIS`, `SANTA_CRUZ`, `SANTA_FE`, `SANTIAGO_DEL_ESTERO`, `TIERRA_DEL_FUEGO`, `TUCUMAN`. The UI shows their Spanish names.
- `ShippingZoneRates` (`shipping.domain`) is the set of costs: one per province at most, each from 0 to 1,000,000,000 cents. `ShippingSettings` holds the optional free-shipping threshold, from 1 to 1,000,000,000 cents.
- One use case per class (T-E3-02): `GetShippingConfiguration`, `ReplaceShippingZoneRates`, `SetFreeShippingThreshold`. Each one carries the `Actor`; any actor other than `ADMIN` gets `Forbidden`. Outbound ports: `LoadShippingZoneRates`, `SaveShippingZoneRates`, `LoadShippingSettings` and `SaveShippingSettings`, read and write sides separated, reused by the cost calculation (T-E3-03).
- Replacing the costs is a whole replacement: the listed provinces get their cost and every other province is left without shipping. An unchanged cost keeps its `updated_at`.
- Endpoints under `/api/admin/shipping` (errors as `problem+json`; every write requires the CSRF token):
  - `GET` → `200` with the configuration: `zones`, the 24 provinces in the order of the `Province` enum, each with `province` and `costCents` (null: no shipping), and `freeShippingThresholdCents` (null: no free shipping).
  - `PUT /zones {zones: [{province, costCents}]}` → `200` with the configuration; `422` with the refused provinces in `provinces` (a cost outside 0 to 1,000,000,000 cents, or a province listed twice); `400` for a body that is not JSON of that shape, an unknown province, an entry without cost, or more than 24 entries. An empty list leaves every province without shipping.
  - `PUT /free-shipping {thresholdCents}` → `200` with the configuration; `422` for a threshold outside 1 to 1,000,000,000 cents; `400` for a body that is not JSON of that shape. A null threshold removes free shipping.
- Back office (T-E3-02): `/admin/envios`, "Envíos" in the back-office menu. A list of the 24 provinces by their Spanish name, each with a cost field in pesos (as prices, UC-06); an empty field means no shipping to that province, and "$ 0" a free shipping to it. One "Guardar costos" button saves them all, and the provinces of a `422` are marked. Below, the "Envío gratis desde ($)" field, empty for none, with its own save button.
- Cost calculation (T-E3-03): `ShippingCostCalculator` strategy (`shipping.application`, ARCHITECTURE.md section 5) gives the base cost to a province, empty for no shipping: `ZoneTableShippingCostCalculator` over `LoadShippingZoneRates` now, a carrier API later. Free shipping is the shop's rule over any calculator: `ShippingSettings` turns the base cost into $0 when the subtotal after the discount reaches the threshold; a province without shipping stays without it. Published to `ordering` through `shipping.api`: `ShippingCosts.quote(province, subtotalAfterDiscount)` returns `ShippingQuote.Cost` (the amount) or `ShippingQuote.NoShipping`; `shipping.api.Province` repeats the 24 constants of the domain enum.
- Rate-limit policy: authenticated back office (300 per minute per user).

---

## UC-13 Complete the checkout in 3 steps

**Actor:** Visitor (buys as a guest) or Customer · **Module:** `ordering` + `cart.api` + `shipping.api` · **Stage:** E3

**Preconditions:** a cart with at least one available item.

**Main flow**
1. **Shipping:** name, email, phone, national ID and full address (street, number, floor, apartment, city, province and postal code). The system calculates the shipping cost.
2. **Payment:** chooses MercadoPago (MODO in E3b).
3. **Review:** sees the summary (items, discount, shipping and total), accepts the terms and confirms.

**Business rules**
- No account required.
- On desktop the summary is a second column.
- The floating WhatsApp button is hidden during the checkout.
- The server calculates every amount (R-01, R-02).
- Buyer data, validated by the domain, repeated by Bean Validation, with the database as the last defense (RNF-28):
  - name: 1 to 100 characters, trimmed;
  - email: up to 254 characters, trimmed and in lower case;
  - phone: stored as digits only, 8 to 15 digits (spaces, `+`, `-` and parentheses are dropped);
  - national ID (DNI): digits only, 7 or 8 digits (dots and spaces are dropped);
  - street 1 to 100 characters; number 1 to 10; floor and apartment optional, up to 10; city 1 to 80; all trimmed;
  - province: one of the 24 jurisdictions (UC-12);
  - postal code: 4 digits or an Argentine CPA (a letter, 4 digits and 3 letters, such as `S3000ABC`), in upper case.

**Acceptance criteria**
- Given an empty required field, when trying to go on, then it does not go on and the missing field is shown.
- Given a national ID with an invalid format, when validated, then it is rejected.
- Given a cart with a variant out of stock, when the checkout opens, then it is announced and the item must be removed to go on.
- Given a buyer who goes back a step, when the province changes, then the shipping cost and the total are recalculated.
- Given more than 5 checkout attempts per minute from the same cart and IP, when another arrives, then it receives `429` with `Retry-After`.
- Given an email with a commonly misspelled domain (for example `gmial.com`), when the buyer goes on, then a correction is suggested without blocking (*Proposed*, T-E3-24).

**Design notes**
- Endpoints: `GET /api/checkout/shipping-cost?province=` for step 1; `POST /api/checkout` confirms (UC-14, contract in its design notes, T-E3-05).
- Shipping cost of step 1 (T-E3-03): `GetShippingCost` (`ordering.application`) with the `Actor` and a `Province`, read only, as the buyer (Row Level Security of the cart). It takes the actor's cart priced as UC-09 (only `AVAILABLE` lines count in the subtotal, the coupon evaluated again) through `cart.api.CartSummaries`, and the shipping cost through `shipping.api.ShippingCosts` with the subtotal after the discount. Outbound ports of `ordering`: `CartAmounts` and `ShippingQuotes`, each with its fake and its contract test. Results: `Quoted`, `NoShipping`, `EmptyCart` (no cart, or none of its lines available; also the administrator and a guest without a cart cookie).
  - `GET /api/checkout/shipping-cost?province=SANTA_FE` → `200` with `province`, `subtotalCents`, `discountCents`, `shippingCents` and `totalCents` (total = subtotal − discount + shipping), so the client shows the step-1 summary without calculating anything; `409` problem `EMPTY_CART`; `422` problem `NO_SHIPPING` with `province`; `400` without `province` or with an unknown one. Errors as `problem+json`.
  - No rate-limit policy of its own: RNF-22 does not list it, and the checkout policy counts confirmations (`POST /api/checkout`, T-E3-19).
- The checkout refuses a cart with items that are not `AVAILABLE` or with unacknowledged price changes (UC-09), and validates the coupon again (UC-10): a coupon that no longer applies refuses the checkout until the buyer removes it (T-E3-05).
- A placed order empties the cart (T-E3-05).
- The order keeps the version of the terms the buyer accepted (`terms_version`, RNF-30.6).
- Rate-limit policy: checkout (5 per minute per cart and IP), T-E3-19.
- Analytics: `begin_checkout` and `add_payment_info` (T-E3-20, RNF-25).

---

## UC-14 Pay with MercadoPago or MODO

**Actor:** Visitor or Customer · **Module:** `ordering` + `payments` + `inventory.api` · **Stage:** E3 (MODO in E3b)

**Main flow**
1. On confirmation, the system creates the order in `AWAITING_PAYMENT` and reserves the stock (UC-16).
2. It creates the payment at the chosen provider and redirects the buyer.
3. The buyer pays and comes back to a page that shows the order status.
4. The webhook confirms the final result (UC-15).

**Alternative flows**
- The buyer cancels or the payment fails: the order stays `AWAITING_PAYMENT` until the reservation expires, and the payment can be retried from the tracking link.

**Business rules**
- Card data never goes through the store.
- Only immediate payment methods in v1 (decided): no cash slips or ATM payments.
- MODO is the sub-stage E3b, once its integration is resolved.

**Acceptance criteria**
- Given a confirmed order, when the payment is created, then the buyer is redirected to the provider with the exact amount calculated by the server.
- Given a buyer who comes back without completing the payment, when opening the tracking link, then the order shows as pending with the option to retry.
- Given the same `Idempotency-Key` sent twice (double tap or network retry), when the second is processed, then the same order is returned without creating another or reserving stock again.
- Given the same `Idempotency-Key` with a different cart, when it arrives, then it is rejected with `422`.
- Given two simultaneous requests with the same `Idempotency-Key`, when they arrive together, then a single order is created and the other receives the stored response or `409` with `Retry-After`.
- Given a checkout without `Idempotency-Key`, when it arrives, then it is rejected with `400`.
- Given a `PAID` order, when the buyer comes back to the store and reloads the page, then analytics records a single purchase (RNF-25).

**Design notes**
- **One transaction, as the buyer (T-E3-05, T-E3-14):** the idempotency key, the order and its items, and the stock reservation. The controller binds the new order's tracking token in the actor (`CurrentActor.callAs`) before the transaction begins, as the cart does with a new `cart_token` (T-E2-04), so Row Level Security accepts the order.
- **Placing the order (T-E3-05):** `PlaceOrder` (`ordering.application`), as the buyer, in one transaction and in this order:
  1. The buyer data and the address are validated by the domain (`BuyerDetails`, the rules of UC-13); every field that breaks a rule is named in `InvalidBuyerData(fields)`. The terms must be accepted (`TermsNotAccepted`).
  2. The actor's cart is locked until the transaction ends, so a second checkout of the same cart (a double tap) waits and then finds it empty. No cart or no items: `EmptyCart` (also the administrator and the system, who cannot own a cart).
  3. Every item must be `AVAILABLE` with no unacknowledged price change (UC-09): otherwise `CartNotReady`; the client reads the cart again, which says why.
  4. A coupon in the cart that no longer applies refuses the checkout with `CouponNotApplicable(status)`, so the buyer never pays a total it did not see; a coupon that no longer exists counts as none (UC-10).
  5. The shipping cost to the province, with the subtotal after the discount: `NoShipping(province)` without one.
  6. The stock of every item is reserved (UC-16, `inventory.api.StockReservations`), before any order is written: `InsufficientStock(variantId, available)` leaves nothing written.
  7. The order is created `AWAITING_PAYMENT`: its number from `order_number_seq`, its tracking token hash, `user_id` for a customer, the buyer and address snapshot, the item snapshots in the cart's order (variant, SKU, product name, size, color, unit price, quantity), the amounts (total = subtotal − discount + shipping), the coupon's id and code when there is one, the provider, the current terms version and `reservation_expires_at` 30 minutes after `created_at` (the injected `Clock`).
  8. The cart is emptied (its items and coupon); the order is now the snapshot, and paying again goes through the tracking link.
  - Result `Placed(orderId, number, total, reservationExpiresAt)`.
- Ports of `ordering` (T-E3-05), each with its fake and contract test: `CheckoutCartLock` and `ClearCart` (over `cart.api`), `ShippingQuotes` (T-E3-03), `ReserveOrderStock` (over `inventory.api`), `OrderNumbers` (the sequence) and `SaveOrders` (inserts the order and its items), both in `JdbcOrderStore`, and `CurrentTerms` (the version of the terms in force, the property `store.ordering.terms-version`). Ids through `IdGenerator`. Domain: `CheckoutForm` validates and normalizes into `BuyerDetails` and `ShippingAddress`, whose `toString()` hides the personal data; `OrderLine`, `OrderAmounts`, `PaymentProvider` and `StockHold` (30 minutes).
- `cart.api` grows (T-E3-05): `CheckoutCarts.lock(actor)` returns the actor's cart locked and priced as UC-09, as `CheckoutCart.Empty`, `CheckoutCart.NotReady` or `CheckoutCart.Ready(lines, subtotal, discount, coupon)`, each line with the variant, its SKU, product name, size and color, its unit price and quantity, and the coupon (id, code and `promotions.api.CouponStatus`) when the cart holds one that exists. `CartClearing.clear(actor)` removes the items and the coupon of the actor's cart. Both join the caller's transaction. `catalog.api.VariantDescription` gains the variant's `sku`.
- **Endpoint (T-E3-05):** `POST /api/checkout` (errors as `problem+json`; requires the CSRF token), header `Idempotency-Key` (a UUID per checkout attempt, repeated on retries), body `{buyer: {name, email, phone, nationalId}, address: {street, number, floor?, apartment?, city, province, postalCode}, paymentProvider, acceptedTerms}`, `paymentProvider` only `MERCADOPAGO` until E3b:
  - `201` with `number`, `trackingToken` (the token itself; only its hash is stored), `status` (`AWAITING_PAYMENT`), `totalCents`, `reservationExpiresAt` and `paymentUrl` (T-E3-06): the provider's checkout the client redirects to, or `null` when the provider could not be reached; then the client opens the tracking page with the token, where the payment is retried (T-E3-08).
  - `400`: a missing `Idempotency-Key` or one that is not a UUID; a body that is not JSON of that shape, a missing field, a text longer than 300 characters, an unknown province or provider.
  - `409` problem `EMPTY_CART`; `CART_NOT_READY`; `COUPON_NOT_APPLICABLE` with `couponStatus`; `INSUFFICIENT_STOCK` with `variantId` and `available`.
  - `422` problem `INVALID_BUYER_DATA` with `fields` (among `name`, `email`, `phone`, `nationalId`, `street`, `number`, `floor`, `apartment`, `city`, `postalCode`); `TERMS_NOT_ACCEPTED`; `NO_SHIPPING` with `province`.
  - Until T-E3-14 stores the key, a repeated request is only guarded by the cart lock: the second finds the cart empty (`409 EMPTY_CART`). T-E3-14 answers it with the stored response instead.
- **After the commit, as `SYSTEM` (T-E3-06, T-E3-17):** the `payment` row and the provider's checkout, with the provider's idempotency header derived from the order number (RNF-20.8, T-E3-17). A retry that finds the order without a checkout creates it.
- **Opening the payment (T-E3-06):** once `PlaceOrder` committed, the controller runs `OpenOrderPayment` (`ordering.application`) with the order id, outside any transaction and as the same buyer, still bound to the new order's tracking token. No database connection is held while the provider answers (RNF-21: 8 connections, 10 s of read timeout):
  1. The order to pay is read as the buyer (`OrdersToPay`, in `JdbcOrderStore`, in its own short transaction; Row Level Security lets it read only its own order): number, status, total, provider, buyer name and email, and reservation expiry. Not found, not `AWAITING_PAYMENT`, or with its reservation already over: `NotPayable`.
  2. The payment is opened through `payments.api.PaymentCheckouts` (the port `OpenPayments` and its adapter over `payments.api`): `Opened(paymentUrl)` or `ProviderUnavailable`. The `payment` table belongs to the system, so that adapter is the one step that runs as `SYSTEM` (`CurrentActor.callAsSystem`); the web layer never does (`ArchitectureTest`). The checkout answers `201` either way, with `paymentUrl` or `null`: the order exists and its stock is reserved.
  - T-E3-08 reuses `OpenOrderPayment` for the retry from the tracking link. Whether an order can still be paid (`AWAITING_PAYMENT` and its reservation not over at the injected `Clock`) is one domain rule, `OrderPayability`, shared by both.
- **`payments.api.PaymentCheckouts.open(CheckoutRequest)` (T-E3-06):** the request carries the order id and number, the amount, the provider (`payments.api.PaymentProvider`), the payer's name and email (hidden by `toString()`) and the reservation expiry; the answer is `CheckoutOpening.Opened(checkoutUrl)` or `CheckoutOpening.ProviderUnavailable`. Implemented by `OpenCheckoutService` (`payments.application`), not transactional; each step of its ports runs in its own short transaction as the caller (`SYSTEM`):
  1. A payment of the order and provider that already has a checkout answers its `checkout_url` (a retry or a repeated call).
  2. Otherwise the `PENDING` payment is inserted with the amount, once per order and provider (`payment_order_provider_uk`; a concurrent insert that loses is ignored).
  3. The provider's `PaymentGateway` creates the checkout, outside any transaction: `Created(checkoutId, checkoutUrl)` or `Unavailable`.
  4. The checkout is recorded in the payment only while it has none (`WHERE checkout_id IS NULL`); when a concurrent call recorded one first, that one is answered.
  - Ports of `payments`, each with its fake and contract test: `LoadPayments` and `SavePayments` (`JdbcPaymentStore`), and `PaymentGateway`. Ids through `IdGenerator`, time through `Clock`.
- `PaymentGateway` strategy (`payments.application`, ARCHITECTURE.md section 5): MercadoPago now, MODO in E3b, both under `PaymentGatewayContractTest`. The service picks the gateway by its `provider()`; a new provider is a new class.
- **MercadoPago Checkout Pro (T-E3-06):** `MercadoPagoGateway` (`payments.adapter.out.mercadopago`) creates a preference with `POST {base-url}/checkout/preferences` and `Authorization: Bearer {access-token}`, through Spring `RestClient` on its own JDK `HttpClient` (connection timeout 3 s, read timeout 10 s, no retries: it is a creation, not a read; RNF-21). The preference (anti-corruption layer: MercadoPago's JSON never leaves the adapter):
  - One item, so the amount is exactly the server's (R-02): `id` the order number, `title` `Pedido #1001 - Pijama Party`, `quantity` 1, `unit_price` the order total in pesos with two decimals, `currency_id` `ARS`.
  - `payer` with the buyer's `name` and `email` (MercadoPago is the payment processor; never logged). `external_reference` the order number.
  - `back_urls` (`success`, `failure` and `pending`) all `{public-url}/checkout/result`, with `auto_return` `approved`: the tracking token never goes to the provider; the storefront keeps it from the `201` (T-E3-11).
  - `notification_url` `{public-url}/api/payments/mercadopago/webhook?source_news=webhooks` (UC-15, T-E3-07: only signed Webhooks notifications).
  - `payment_methods.excluded_payment_types` `ticket` and `atm` (only immediate methods), `expires` `true` and `expiration_date_to` the reservation expiry.
  - The answer's `id` and `init_point` are the checkout id and URL. A non-2xx answer, a timeout, an I/O error, or an answer without an `id` or an `https` `init_point` is `Unavailable`, logged with the order number and the HTTP status only (no payer data, no response body).
  - Properties: `store.public-url` (the storefront's origin; `https://${DOMAIN}` in production), `store.payments.mercadopago.base-url` (`https://api.mercadopago.com`) and `store.payments.mercadopago.access-token` (the Docker secret `MERCADOPAGO_ACCESS_TOKEN`; the production profile does not start without it; empty elsewhere, which answers `Unavailable` without calling). Test credentials give the sandbox.
- The tracking link carries a token of at least 128 bits from `SecureRandom`; only its SHA-256 hash is stored (RNF-29.6). Endpoints `GET /api/orders/{token}` and `POST /api/orders/{token}/retry-payment` (T-E3-08).
- **Tracking page (T-E3-08):** the token is a bearer secret that can be forwarded or stay in a browser's history, so the page shows what following the order needs and masks the rest (decided): never the national ID, the email, the phone or the street address.
  - The controller (`OrderTrackingController`, `ordering.adapter.in.web`) binds the path's token in the actor (`CurrentActor.callAs`, as the checkout does) before the use case's transaction begins, so Row Level Security (`orders_tracking_select`) lets a guest or a customer read only that order; the query also filters by the token's hash, so a customer never gets another of its own orders. A token that is not 43 characters of unpadded base64url (`TokenGenerator.LENGTH`) answers `404` without reaching the database. Staff carry no token, but read every order anyway, so the hash filter alone finds it for them (harmless).
  - `GetTrackedOrder` (`ordering.application`), read-only, as the buyer: through `TrackedOrders` (in `JdbcOrderStore`; `find(TrackingToken)`) the order number, status, creation date, the buyer's first name (`BuyerDetails.firstNameOf`: the first word of the name), city and province, the items in the cart's order (product name, size, color, quantity, unit price), subtotal, discount, coupon code, shipping, total, reservation expiry and shipment tracking number. Results `Found(TrackedOrder, payable)` (`payable` by `OrderPayability`) or `NotFound`.
  - `RetryOrderPayment` (`ordering.application`), not transactional, as the buyer: finds the order through `TrackedOrders` (`NotFound`), then runs `OpenOrderPayment` with its id: `Opened(paymentUrl)`, `ProviderUnavailable` or `NotPayable`. A payment that already has a checkout answers the same `checkout_url` (T-E3-06, step 1): MercadoPago lets the buyer try another card on the same checkout until it expires with the reservation.
  - `GET /api/orders/{token}` (public): `200` with `number`, `status`, `createdAt`, `buyerFirstName`, `items` (`productName`, `size`, `color`, `quantity`, `unitPriceCents`, `lineTotalCents`), `subtotalCents`, `discountCents`, `couponCode` (or `null`), `shippingCents`, `totalCents`, `city`, `province`, `reservationExpiresAt`, `shipmentTrackingNumber` (or `null`) and `canRetryPayment`; `Cache-Control: no-store`. `404` problem `ORDER_NOT_FOUND` for an unknown or malformed token.
  - `POST /api/orders/{token}/retry-payment` (public; requires the CSRF token; no body): `200` with `paymentUrl`; `404` problem `ORDER_NOT_FOUND`; `409` problem `NOT_PAYABLE` (no longer awaiting payment, or its reservation is over); `503` problem `PAYMENT_PROVIDER_UNAVAILABLE` with `Retry-After` `30`.
  - Rate limiting (RNF-22): `order-tracking` per IP, 30 per minute, on both routes; `payment-retry` per tracking token (its SHA-256, `TrackingKeyResolver`) and IP, 5 per minute, on the retry, since each one may call the provider.
  - Logs: the token travels in the path, so Caddy's error log redacts `/api/orders/{token}` and `/pedido/{token}` as it already redacts query strings; the rate-limit log writes the route pattern only. `Referrer-Policy` `strict-origin-when-cross-origin` (Caddy) keeps the path from reaching MercadoPago or analytics.
  - Storefront (`features/orders`): `/pedido/:token` shows the order number, a status label, the items and amounts, the city and province, and the shipment tracking number once there is one. An order `AWAITING_PAYMENT` that is still payable shows "Pago pendiente", until when its products are reserved, and a "Pagar" button that calls the retry and redirects to `paymentUrl`; a `503` says MercadoPago did not answer and to try again in a moment; a `409` reloads the order. One awaiting payment but no longer payable says the reservation expired. An unknown token shows "No encontramos tu pedido". The checkout (T-E3-11) and the confirmation email (T-E3-09) link here.
- Analytics: `purchase`, once per order and only when `PAID` (T-E3-20).

---

## UC-15 Confirm the payment (webhook)

**Actor:** System · **Module:** `payments` + `ordering` · **Stage:** E3

**Main flow**
1. The provider notifies a status change.
2. The system queries the payment at the provider to verify its real status.
3. Depending on the status: approved → order `PAID` and stock deducted for good; rejected → the payment is recorded as rejected and the order keeps its reservation, so the buyer can try again until it expires (UC-16); pending → no change.

**Business rules**
- Idempotent: the same notification repeated produces no double effects (RNF-20.3, RNF-20.5).
- Every notification received is recorded in `payment_event`, and the payment's verified status in `payment`.
- The signature is verified before anything is processed (RNF-29.3).

**Acceptance criteria**
- Given an approved payment, when the notification arrives, then the order becomes `PAID` and the coupon used gets one more use.
- Given the same notification received twice, when the second is processed, then no stock is deducted and no email is sent twice.
- Given a rejected payment, when the notification arrives, then the payment is recorded as rejected and the order stays `AWAITING_PAYMENT` with its stock reserved until the reservation expires (decided in T-E3-07: Checkout Pro lets the buyer try another card on the same checkout).
- Given a payment approved after its order expired, when the notification arrives, then the order stays `EXPIRED`, its stock is not touched, and a warning with the order number asks for a refund from the provider's panel (decided in T-E3-07; the back-office alert arrives with T-E3-10).
- Given a notification for a payment that does not exist, when it arrives, then it is ignored and a warning without personal data is logged.
- Given two identical notifications arriving at once, when processed, then only one has effects.

**Design notes**
- **Endpoint (T-E3-07):** `POST /api/payments/mercadopago/webhook?type=payment&data.id={id}`, public, excluded from CSRF and rate limiting, protected by the signature. The body is not read: MercadoPago's notification only carries ids, and the query carries the ones the signature covers. Answers carry no body:
  - `401` problem when the signature is missing, invalid or out of date (RNF-29.3), logged without personal data or the headers' values; nothing else is looked at.
  - `200` when processed, and also for what is ignored: a type other than `payment`, a `data.id` that is not 1 to 20 digits, a payment MercadoPago does not know, or one this store has no payment for (a warning with the provider's payment id only).
  - `503` problem with `Retry-After` when MercadoPago cannot be asked for the payment (or there is no access token): MercadoPago delivers the notification again (it retries every 15 minutes on anything that is not `200` or `201`).
  - The preference's `notification_url` gains `?source_news=webhooks` (T-E3-06), so MercadoPago sends only signed Webhooks notifications, not the unsigned IPN ones.
- **Signature (T-E3-07, RNF-29.3):** header `x-signature: ts={ts},v1={hmac}` and header `x-request-id`. The manifest is `id:{data.id};request-id:{x-request-id};ts:{ts};` (a part whose value is missing is left out), signed with HMAC-SHA256 under the webhook secret and written in lowercase hex; `v1` is compared in constant time. `ts` is in milliseconds (the documentation also shows seconds: a value below 10^11 is read as seconds); a notification more than 24 hours old (`store.payments.mercadopago.webhook-max-age`; MercadoPago retries for hours, and whether a retry keeps its `ts` is not documented) or more than 5 minutes in the future is out of date. Replaying a signed notification within that window is harmless: it only makes the store ask MercadoPago again. `MercadoPagoSignature` (`payments.adapter.in.webhook`) checks it; the secret is `store.payments.mercadopago.webhook-secret` (the Docker secret `MERCADOPAGO_WEBHOOK_SECRET`, the secret key of the application's Webhooks in "Tus integraciones"; the production profile does not start without it; empty elsewhere, which refuses every notification with `401` and a warning).
- **`ConfirmPayment` (`payments.application`, T-E3-07), as `SYSTEM`:** the webhook controller binds `SYSTEM` (a background adapter, `ArchitectureTest`) and runs it with the provider and the provider's payment id. Not transactional as a whole, so no connection waits for the provider (RNF-21):
  1. The provider's payment is read through the `PaymentVerifier` strategy (`MercadoPagoPaymentVerifier`), outside any transaction: `Found(VerifiedPayment)` with the provider's payment id, the order number (`external_reference`), the status and the amount; `NotFound`; or `Unavailable`. MercadoPago's statuses map to `VerifiedStatus`: `approved` → `APPROVED`; `rejected` and `cancelled` → `REJECTED`; `pending`, `in_process`, `authorized` and `in_mediation` → `PENDING`; `refunded` and `charged_back` → `OTHER` (refunds are done in the provider's panel and are out of scope, UC-18).
  2. In one transaction: the payment of the order number and provider is locked (`FOR UPDATE`, so two notifications of the same payment run one after the other; `payment_event` joins with T-E3-15, RNF-20.3). None: `UnknownPayment`. `PaymentDecision` (`payments.domain`) decides with what was verified:
     - `APPROVED` with an amount other than the payment's: `AmountMismatch`, nothing changes, logged as an error with the order number and both amounts.
     - `APPROVED`: the payment becomes `APPROVED` with the provider's payment id, and `payments.api.PaymentApproved(orderId, provider)` is published in the same transaction. A payment already `APPROVED` changes nothing and publishes nothing (`AlreadyApproved`): an approved payment is final.
     - `REJECTED`: a `PENDING` or `REJECTED` payment becomes `REJECTED` with the provider's payment id (the last verified attempt); the order is not touched.
     - `PENDING` and `OTHER`: nothing changes (`Unchanged`).
  - Results: `Approved`, `AlreadyApproved`, `Rejected`, `Unchanged`, `AmountMismatch`, `UnknownPayment`, `NotFoundAtProvider`, `ProviderUnavailable` (→ `503`).
  - Ports of `payments`, each with its fake and contract test: `PaymentsToConfirm` (lock by order number and provider) and `PaymentConfirmations` (record the verified status), both in `JdbcPaymentStore`; `PaymentVerifier` (strategy per provider, apart from `PaymentGateway`: Interface Segregation); `PaymentEvents` (publishes `PaymentApproved`).
  - `payment` gains `order_number` (V18), stored when the payment is created (T-E3-06's `CheckoutRequest` already carries it), unique per provider: MercadoPago's `external_reference` is the order number, and `payments` does not read `orders` (ARCHITECTURE.md section 3, rule 4). No payment exists outside development yet, so the column is required; a development database with payments must be recreated.
- **MercadoPago payment (T-E3-07):** `GET {base-url}/v1/payments/{id}` with the access token, on the same kind of `RestClient` (3 s / 10 s, no retries: MercadoPago retries the notification). `404` → `NotFound`; any other non-2xx, a timeout, an I/O error, or an answer without an `id`, a numeric `external_reference`, a known `status`, `currency_id` `ARS` or a positive `transaction_amount` with at most two decimals → `Unavailable`, logged with the payment id and the HTTP status only (no payer data, no response body).
- **`ConfirmOrderPayment` (`ordering.application`, T-E3-07):** `PaymentApproved` reaches `ordering` through a plain listener (`ordering.adapter.in.event`), so it runs in the payment's transaction and the order, the stock and the coupon commit or roll back with the payment (as `SearchIndexListener`, UC-26). `ordering` already depends on `payments`, so the event keeps the graph acyclic (ARCHITECTURE.md section 3, rule 1). In that transaction:
  1. The order is locked (`ConfirmableOrders`, in `JdbcOrderStore`): its number, status and coupon id. None: an `IllegalStateException` (the payment exists, so the order must), which rolls the payment back and answers `500`, so MercadoPago delivers it again.
  2. `AWAITING_PAYMENT` → `PAID` with a conditional transition (RNF-20.5); the reservation becomes a deduction (`StockDeductions`, over `inventory.api.StockReservations.deduct`); the coupon, when the order has one, gets its use (`OrderCouponRedemptions`, over the new `promotions.api.CouponRedemptions.redeem(couponId, orderId, at)`, which joins the transaction and writes a `coupon_redemption` row through the port `SaveCouponRedemption` of `JdbcCouponStore`: once per coupon and order; a coupon deleted meanwhile is left without it). Result `Paid`.
  3. Already `PAID` or further on: `AlreadyPaid`, nothing changes.
  4. `EXPIRED` or `CANCELLED`: `PaidAfterClosing(number, status)`, nothing changes; the listener logs a warning with the order number and the status asking for a refund from MercadoPago's panel (decided: no `EXPIRED` → `PAID`; the back-office alert arrives with T-E3-10).
  - Ports of `ordering`, each with its fake and contract test: `ConfirmableOrders`, `OrderTransitions` (T-E3-04), `StockDeductions` (`InventoryModuleStockDeductions`) and `OrderCouponRedemptions` (`PromotionsModuleCouponRedemptions`).
- `payment_event` (every notification recorded, RNF-20.3) arrives with T-E3-15; the payment-confirmed emails with T-E3-09.
- State transitions are conditional updates (`WHERE status = :expected`); no row affected means already processed (RNF-20.5, T-E3-16).

---

## UC-16 Reserve and release stock

**Actor:** System · **Depends on:** UC-13, UC-15 · **Module:** `inventory` + `ordering` · **Stage:** E3

**Main flow**
1. When the payment starts, the system atomically reserves the quantity of every variant of the order.
2. The reservation expires after 30 minutes without an approved payment (decided).
3. With an approved payment, the reservation becomes a final deduction.
4. With an expired reservation, the stock is released and the order becomes `EXPIRED`. A rejected payment does not release it: the buyer can try again until the reservation expires (T-E3-07).

**Business rules**
- Available stock is on hand − reserved, never negative (R-03).
- A reservation is a conditional update: `reserved = reserved + :n WHERE on_hand − reserved >= :n`. No row affected means not enough stock.
- A deduction lowers `on_hand` and `reserved` by the same quantity; a release lowers `reserved`.
- Every reservation, deduction and release writes its movement, unique per order, variant and type, so a repeated step is a no-op (RNF-20.4).
- A stock adjustment of the administrator (UC-07) cannot take `on_hand` below `reserved` (T-E3-04).

**Acceptance criteria**
- Given a SKU with stock 1 and two buyers who start the payment at once, when both confirm, then only one gets the reservation and the other sees "not enough stock" naming the variant.
- Given an expired reservation without payment, when the cleanup runs, then the stock is available again and the order is `EXPIRED`.
- Given an "approved" webhook repeated, when it arrives a second time, then the stock is not deducted twice.
- Given a variant at 0, when its detail is viewed, then the buy button is disabled.

**Design notes**
- `inventory.api` grows reserve, deduct and release by order (T-E3-04); `ordering` calls it in the same transaction, since the dependency already points that way (ARCHITECTURE.md section 3, rule 6).
- `inventory.api.StockReservations` (T-E3-04), apart from `StockAvailability` (Interface Segregation). Every method joins the caller's transaction, which must exist:
  - `reserve(orderId, lines)`, one `StockRequest(variantId, quantity)` per variant, quantity from 1 to 1,000,000 → `ReservationResult.Reserved`, or `ReservationResult.InsufficientStock(variantId, available)` naming the first short variant. All or nothing: when a variant is short, nothing of the order stays reserved and the caller's transaction stays usable (a savepoint). Variants are reserved in id order, so two checkouts never wait on each other in a cycle. A variant without a stock level has 0 available. An order that already holds a reservation gets `Reserved` and nothing is reserved again.
  - `deduct(orderId)`: every reservation of the order not yet deducted or released becomes a `DEDUCTION` (`on_hand` and `reserved` lower by its quantity). `release(orderId)`: every such reservation gets its `RELEASE` (`reserved` lowers). Repeating either changes nothing; an order without such a reservation is left as it is. A payment approved after its reservation was released leaves the order `EXPIRED` and the stock as it is (UC-15, T-E3-07).
- Inside `inventory`: `StockMovement` carries the order exactly on `RESERVATION`, `DEDUCTION` and `RELEASE`, with the signs of V17. Outbound ports `ReserveStock` (all or nothing), `OrderReservations` (read: whether an order reserved anything, and its reservations not yet deducted or released) and `SettleReservations` (write the deductions and releases), all implemented by `JdbcStockLevelStore`.
- Stock adjustments (UC-07) after reservations: `StockLevel` knows `reserved`, and an adjustment that would leave `on_hand` below `reserved` (but not below 0) is `WouldGoBelowReserved(currentOnHand, reserved)` → `409` problem `WOULD_GO_BELOW_RESERVED` with `onHand` and `reserved`. The back office says "No se puede: hay {reserved} unidades reservadas por pedidos pendientes de pago. Hay {onHand}."
- Expiry (T-E3-04): `ExpireOverdueOrder` (`ordering.application`), as `SYSTEM`, one order per transaction: it locks the oldest order `AWAITING_PAYMENT` whose `reservation_expires_at` is not after now (`FOR UPDATE SKIP LOCKED`), moves it to `EXPIRED` with a conditional update (RNF-20.5) and releases its stock in the same transaction. Results `Expired(orderId)` and `NoneDue`. Outbound ports `OverdueOrders` (read and lock), `OrderTransitions` (conditional transition, false when the order is not in the expected status) and `StockReleases` (over `inventory.api`). `OrderStatus` (`ordering.domain`) holds the transition table of UC-18, and `OrderTransitions` refuses a pair outside it.
- The job `ReservationExpiry` (`ordering.adapter.in.scheduler`) runs every minute (`store.ordering.reservation-expiry.cron`, at second 0) and calls the use case until `NoneDue`, at most 100 orders per run. It logs only how many orders expired.
- Concurrency test of the last unit: T-E3-12.

---

## UC-17 Notify the payment by email

**Actor:** System (and the buyer, to correct its email) · **Module:** `notifications` + `ordering.api` · **Stage:** E3

**Main flow**
1. When the payment is confirmed, the system sends the buyer a confirmation email with the order summary, the total, the shipping address and the tracking link.
2. It also sends a notice to the administrator.
3. If the email bounces, the system marks it and shows an alert on the order in the back office.
4. The buyer can correct its email from the result page or the tracking link, and the confirmation is sent again.

**Business rules**
- An email failure never interrupts the order: it is retried in the background.
- Each type of email is sent once per order, even if the process repeats (RNF-20.7).
- In v1 the buyer only receives the payment-confirmed email: no order-received, order-shipped or reminder emails (decided).
- The shipment tracking number shows on the tracking link, and the confirmation email says so.

**Acceptance criteria**
- Given an approved payment, when confirmed, then the buyer and the administrator receive an email.
- Given the mail server down, when an order is confirmed, then the order is confirmed anyway and the email is retried.
- Given an order whose confirmation is processed twice, when it finishes, then the buyer received a single email of each type.
- Given an order that becomes `PAID`, when it finishes, then the buyer receives a single email with summary, total, shipping address and tracking link.
- Given an order created that expires unpaid, when it finishes, then the buyer receives no email.
- Given an order that becomes `SHIPPED`, when the shipment tracking number is saved, then no email is sent and the number shows on the tracking link.
- Given an email the provider reports as bounced, when the notification arrives, then the email is marked `BOUNCED` and the order shows an alert in the back office.
- Given a repeated bounce notification, when it arrives a second time, then it is not processed again.
- Given a buyer with a misspelled email, when it corrects it from the tracking link, then it is validated, the order is updated and the confirmation is sent again.
- Given more than 3 email corrections per hour on the same order, when another arrives, then it receives `429` with `Retry-After`.

**Design notes**
- `EmailSender` port with a transactional service behind it (Resend, decided in T-E3-09) and `EmailSenderContractTest`; outbox `email_outbox` unique on `(type, reference)`, enqueued in the same transaction as the state change, sent by a job every minute with retries at 1, 5, 15 and 60 minutes, up to 5 attempts (T-E3-09, RNF-23).
- **Tracking link in the email (decided in T-E3-09):** the order keeps only the hash of its checkout's tracking token (RNF-29.6), so the email cannot rebuild that link. Each time the job sends the buyer's email it mints a new tracking token (`TokenGenerator`, 256 bits) for the same order and stores only its SHA-256 in `order_tracking_token` (`ordering` module, V19): `token_hash` (primary key, lowercase hex), `order_id` (foreign key to `orders`), `created_at`. The plaintext lives only in memory and in the email. The checkout's token keeps working; the tracking page, the payment retry and Row Level Security accept either (`orders_tracking_select` also matches an `order_tracking_token` row of the order with the context's hash). A resend (T-E3-23) mints another one the same way. Policies of `order_tracking_token`: the system inserts and reads; a guest or a customer reads only the row of its own token hash; nobody updates or deletes.
- **Enqueueing (T-E3-09):** `ConfirmOrderPayment`, when the order becomes `PAID` (result `Paid` only), publishes `ordering.api.OrderPaid(orderId, number)` through the port `OrderEvents` (`SpringOrderEvents`). `notifications` listens with a plain listener (`notifications.adapter.in.event`), so it runs in the payment's transaction as `SYSTEM`, and `EnqueueOrderPaidEmails` (`notifications.application`, transaction mandatory) inserts `ORDER_PAID_CUSTOMER` and `ORDER_PAID_ADMIN`, both with the order id as reference, `ON CONFLICT DO NOTHING` (RNF-20.7). An already paid order publishes nothing, and a repeated event inserts nothing. An order that expires, or a payment that fails, enqueues nothing.
- **`email_outbox` (`notifications` module, V19):** `id`, `type` (`ORDER_PAID_CUSTOMER`, `ORDER_PAID_ADMIN`), `reference` (the order id; refers to `ordering`, no foreign key), `status` (`PENDING`, `SENT`, `FAILED`; `BOUNCED` arrives with T-E3-22), `attempts` (0 to 5), `next_attempt_at`, `provider_message_id` (the provider's id of the sent email, for the bounces of T-E3-22), `created_at`, `sent_at` (exactly when `SENT`), `updated_at`. Unique `(type, reference)`; indexed by `next_attempt_at` where `PENDING`. No personal data: the recipient and the content are composed when sending, from the order. Row Level Security enabled and forced: the system reads, inserts and updates; the administrator reads (T-E3-10, T-E3-22); nobody deletes.
- **Sending (T-E3-09):** the job `EmailOutboxDispatcher` (`notifications.adapter.in.scheduler`, `store.notifications.outbox.cron`, every minute at second 30, so it does not run with the reservation expiry) runs `SendNextEmail` as `SYSTEM` until `NoneDue`, at most 50 emails per run, and logs only how many were sent, retried and failed. `SendNextEmail` (`notifications.application`) is not transactional as a whole, so no connection waits for the provider (RNF-21):
  1. **Claim** (`ClaimNextEmail`, `notifications.application`, its own short transaction): `LockDueEmails` locks the oldest `PENDING` email whose `next_attempt_at` is not after now (`FOR UPDATE SKIP LOCKED`), and `RecordEmailOutcomes` stores the claim. `OutboxEmail.claim` (`notifications.domain`) counts the attempt and moves `next_attempt_at` by `RetrySchedule` (1, 5, 15, 60 minutes after the 1st to 4th attempt), so a crash while sending leaves it to be retried then; an email that already had its 5 attempts becomes `FAILED` instead and is not sent. None due: `NoneDue`.
  2. **Compose** (`EmailComposer`, a strategy per type, Open/Closed): `Composed(EmailMessage)` (recipient, subject, HTML and plain text; `toString()` hides the recipient) or `Gone` (the order does not exist; `FAILED` at once). Composers live in `notifications.adapter.out.ordering` and read the order through `ordering.api.PaidOrders.find(orderId)` (the buyer, the address, the lines and the amounts; its own read transaction); the buyer's composer also mints the link through `ordering.api.TrackingLinks.issue(orderId)` (its own transaction). Texts in Spanish from `messages.properties`, prices as `$ 44.500,00`.
     - Buyer (`ORDER_PAID_CUSTOMER`), to the order's email: subject "Recibimos tu pago - Pedido #1001"; greeting with the first name; the lines (product, size, color, quantity, line total), subtotal, discount with the coupon code, shipping and total; the shipping address; the tracking link `{public-url}/pedido/{token}`, saying that the shipment tracking number will show there once the order is shipped; and that replying reaches the store.
     - Administrator (`ORDER_PAID_ADMIN`), to `store.notifications.admin-recipient`: subject "Nuevo pedido pagado #1001"; the buyer's name, the city and province, the lines and the total, and that the order is in the back office. Without a recipient configured: `Gone`, logged as a warning.
  3. **Send** (`EmailSender`, outside any transaction): `Sent(providerMessageId)`, `Rejected` (the provider refuses this email for good: an invalid recipient or content) or `Unavailable` (anything that may pass: timeout, I/O error, rate limit, server error, wrong credentials, no API key).
  4. **Record** (`RecordEmailOutcomes`, its own short transaction): `Sent` → `SENT` with `sent_at` and `provider_message_id`; `Rejected` → `FAILED`; `Unavailable` → stays `PENDING` until its `next_attempt_at`, or `FAILED` after the 5th attempt. `FAILED` is logged as an error with the type and the order id (no personal data): the administrator contacts the buyer (the back-office alert is T-E3-22).
  - Results: `Sent`, `Retrying`, `Failed`, `NoneDue`.
  - At least once: if the process stops after the provider accepted the email and before `SENT` is recorded, or the provider accepted it but its answer timed out (`Unavailable`), the email is sent again at its next attempt. Each attempt has a new link (step 2), and every link minted keeps working. No provider idempotency key: the content differs between attempts.
  - Ports, each with its fake and contract test: `EnqueueEmails`, `LockDueEmails` and `RecordEmailOutcomes` (`JdbcEmailOutbox`), `EmailComposer`, `EmailSender`; in `ordering`, `OrderEvents`, `PaidOrderDetails` and `SaveTrackingLinks` (`JdbcOrderStore`).
- **Resend (T-E3-09):** `POST {base-url}/emails` with `Authorization: Bearer {api-key}` and `from`, `to` (one recipient), `reply_to` (when configured), `subject`, `html` and `text`; the answer's `id` is the provider's message id. Its own `RestClient` (3 s / 10 s, no redirects, no retries: the outbox retries). `400` and `422` → `Rejected`; any other non-2xx (`401`, `403`, `429`, `5xx`), a timeout, an I/O error or an answer without `id` → `Unavailable`. Logs carry the HTTP status only: never the recipient, the content or the answer's body.
  - Properties: `store.notifications.resend.base-url` (`https://api.resend.com`), `store.notifications.resend.api-key` (the Docker secret `RESEND_API_KEY`; the production profile does not start without it; empty elsewhere, which answers `Unavailable` without calling and logs a warning), `store.notifications.from` (`MAIL_FROM`, e.g. `Pijama Party <pedidos@notificaciones.<domain>>`, required in production), `store.notifications.reply-to` (`MAIL_REPLY_TO`, the business's email, RNF-23; optional), `store.notifications.admin-recipient` (`NOTIFICATIONS_ADMIN_EMAIL`, required in production).
- Bounces: `POST /api/mail/webhook`, signature verified, `email_event` unique on `(provider, event_id)` (T-E3-22).
- Email correction: `PATCH /api/orders/{token}/email`, with a versioned reference so the outbox stays unique (T-E3-23).

---

## UC-18 Manage orders

**Actor:** Administrator · **Module:** `ordering` + `backoffice` · **Stage:** E3

**Main flow**
1. The administrator sees the list of orders, filterable by status, and opens the detail.
2. Changes the status: `PREPARING`, `SHIPPED` (entering the shipment tracking number), `DELIVERED` or `CANCELLED`.

**Business rules**
- Only the transitions of the state diagram are allowed:
  - `AWAITING_PAYMENT` → `PAID` → `PREPARING` → `SHIPPED` → `DELIVERED`
  - `AWAITING_PAYMENT` → `EXPIRED` or `CANCELLED`
  - `PAID` or `PREPARING` → `CANCELLED`
- When cancelling a `PAID` or `PREPARING` order, the administrator decides whether to put the stock back: every line or none.
- Cancelling an `AWAITING_PAYMENT` order releases its reservation, as expiring does.
- The shipment tracking number is entered with `SHIPPED` and can be corrected while the order is `SHIPPED`; `DELIVERED` freezes it.
- Refunds are done from the payment provider's panel (out of scope for this version).

**Acceptance criteria**
- Given an `AWAITING_PAYMENT` order, when moving it to `SHIPPED` is attempted, then it is rejected.
- Given an order that becomes `SHIPPED`, when the shipment tracking number is saved, then the buyer sees it on its tracking link.
- Given the list of orders, when filtered by `PAID`, then only paid orders appear.
- Given an order with a bounced email, when the administrator opens the detail, then an alert shows the buyer's phone to contact it.
- Given an order whose buyer's email `FAILED` (decided in T-E3-10), when the administrator opens the detail, then the same alert shows.
- Given an order that expired or was cancelled before its payment was approved, when the administrator opens the detail, then an alert says the payment must be refunded from the provider's panel (decided in T-E3-10).
- Given a `PAID` order cancelled with the stock put back, when it finishes, then every line's units are available again; cancelled without it, the stock does not change.
- Given an order that changed status after the administrator opened it, when the administrator changes it from the status seen, then it is rejected and the order is not touched.

**Design notes**
- `OrderStatus` transition table inside the `Order` aggregate (ARCHITECTURE.md section 6); each transition is a conditional update (RNF-20.5).
- Putting the stock back on a cancelled paid order is a new order movement type of the inventory, added with T-E3-10.
- The order detail with the bounce alert is composed in `backoffice`, which depends on `ordering` and `notifications` (ARCHITECTURE.md section 3, rule 3).
- Rate-limit policy: authenticated back office (300 per minute per user).
- **Endpoints (T-E3-10),** as `ADMIN`, `application/problem+json` on errors:
  - `GET /api/admin/orders?status=&page=` (`ordering.adapter.in.web`): 50 per page from page 0, newest first (`created_at`, then `number`); `status` optional, one of the seven. Each row: `id`, `number`, `createdAt`, `status`, `buyerName`, `city`, `province`, `totalCents`. `400` for an unknown status or a page out of 0 to 10,000.
  - `GET /api/admin/orders/{id}` (`backoffice.adapter.in.web`): `number`, `status`, `createdAt`, `updatedAt`, `deliveredAt`; buyer `name`, `email`, `phone`, `nationalId`; the shipping address; the lines (product, size, color, quantity, unit price, line total); `subtotalCents`, `discountCents`, `couponCode`, `shippingCents`, `totalCents`; `paymentProvider`; `shipmentTrackingNumber`; `nextStatuses` (what `OrderStatus` allows from here, so the page only offers those); `alerts`: `BUYER_EMAIL_FAILED` (with the phone; `BUYER_EMAIL_BOUNCED` joins with T-E3-22) and `PAID_AFTER_CLOSING`. `404` for no such order.
  - `POST /api/admin/orders/{id}/status` (`ordering.adapter.in.web`) with `{from, to, shipmentTrackingNumber?, restock?}` → `200` with the order's new `status`. `from` is the status the administrator saw: the transition applies only if the order is still in it. `shipmentTrackingNumber` (1 to 60 characters, trimmed) is required for `SHIPPED` and refused otherwise; `restock` is required when cancelling a `PAID` or `PREPARING` order and refused otherwise. `404` no such order; `409` `order-status-changed` (the order is no longer in `from`) or `invalid-transition` (`from` → `to` is not in the diagram); `422` for a missing or refused field.
  - `PUT /api/admin/orders/{id}/shipment-tracking-number` (`ordering.adapter.in.web`) with `{shipmentTrackingNumber}` → `200`; `409` `invalid-transition` unless the order is `SHIPPED`; `422` for an invalid number.
- **Use cases (T-E3-10),** `ordering.application`, each in one transaction: `ListOrders` (read port `AdminOrders`), `GetAdminOrder` (exposed to `backoffice` as `ordering.api.AdminOrderDetails`), `ChangeOrderStatus` and `CorrectShipmentTrackingNumber` (write port `OrderTransitions`, a conditional update `WHERE id = ? AND status = ?`). Results: `Changed(status)`, `NotFound`, `StatusChanged(current)`, `InvalidTransition`, `MissingTrackingNumber`, `RestockDecisionRequired`. Side effects in the same transaction:
  - `AWAITING_PAYMENT` → `CANCELLED`: `inventory.api.StockReservations.release(orderId)`.
  - `PAID` or `PREPARING` → `CANCELLED` with `restock: true`: `StockReservations.restock(orderId)`, new movement type `RESTOCK` (positive; unique per order, variant and type like the others, RNF-20.4), one per deducted line; with `restock: false`, nothing. The coupon use stays.
  - → `DELIVERED`: `delivered_at` (UC-29).
  - No email for any of them (UC-17).
- **Paid after closing (T-E3-10):** when `ConfirmOrderPayment` answers `PaidAfterClosing`, it also sets `orders.paid_after_closing_at` (V20) if it is still null, in the payment's transaction; the WARN log stays.
- **Composition (T-E3-10):** `backoffice.application.GetOrderDetail` reads `ordering.api.AdminOrderDetails` and `notifications.api.BuyerEmailStatus` (`FAILED`, from `email_outbox`, which the administrator may read), and adds the alerts.
- **Schema (V20):** `orders.paid_after_closing_at`; `RESTOCK` in the stock movement type and sign checks; index `orders (status, created_at DESC)` for the filtered list.
- **Pages (T-E3-10):** `/admin/pedidos`, the list with a status filter and pages; `/admin/pedidos/:id`, the detail with its alerts first and a button per status in `nextStatuses`: Enviado asks for the tracking number, Cancelado for a paid or preparing order asks "¿Reponer el stock?". On `409` the page reloads the order and says it changed.

---

## UC-29 Withdrawal button ("botón de arrepentimiento")

**Actor:** Customer or guest · **Module:** `ordering` · **Stage:** E3 · **Origin:** legal obligation (Law 24,240, art. 34, and Resolution 424/2020; *to be validated with a professional*)

**Main flow**
1. From a "Botón de arrepentimiento" link on the home page and in the footer of every page, the person opens the form. No account needed.
2. Enters the order number and the purchase email.
3. The system records the request, shows a procedure code and emails it.
4. The administrator gets a notice and sees the request in the back office, with its order.
5. The administrator arranges the return, cancels the order (UC-18), refunds from the provider's panel and marks the request as resolved.

**Business rules**
- The legal term is 10 calendar days from the delivery or the purchase, whichever is later. The system **does not refuse** by term: it records the date and flags the request as out of term, so the administrator decides.
- Withdrawing costs the buyer nothing: the business pays the return.
- One active request per order; a repeated request shows the code already issued.
- The form never reveals whether an order number exists when the email does not match.
- The email with the code is an exception, required by the regulation, to the single-email rule (RNF-23).
- It does not change the order status by itself; the administrator does.

**Acceptance criteria**
- Given a `PAID` order, when the form is sent with its number and email, then a procedure code is shown, emailed, and the request appears in the back office.
- Given an order number with an email that does not match, when the form is sent, then a generic message is answered and nothing is recorded.
- Given an order with an active request, when another is sent, then the same code is shown and no second request is created.
- Given an order `DELIVERED` more than 10 days ago, when the form is sent, then the request is recorded flagged as out of term.
- Given more than 3 submissions per hour for the same order and IP, when another arrives, then it receives `429` with `Retry-After`.

**Design notes**
- Table `withdrawal_request` (`ordering` module, Row Level Security), with its schema and policies in T-E3-25: `order_id` unique among active requests, `code` (`ARR-` and 8 readable random characters), `status` (`RECEIVED` or `RESOLVED`), `out_of_term`, `created_at`, `resolved_at`. The term uses `orders.delivered_at` and `orders.created_at`.
- Endpoints (plan): `POST /api/withdrawals`, `GET /api/admin/withdrawals`, `POST /api/admin/withdrawals/{id}/resolve` (T-E3-25, T-E3-26).
