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
- Row Level Security on `orders`, `order_item` and `payment`, enabled and forced (RNF-27), with the policies of T-E3-01 below. `idempotency_key` (T-E3-14), `payment_event` (T-E3-15), `email_outbox` (T-E3-09) and `email_event` (T-E3-22) bring their own policies with their tables. The full policy test matrix is T-E3-21.

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
- Endpoints (plan): `GET /api/checkout/shipping-cost?province=` for step 1; `POST /api/checkout` confirms (UC-14). The contract of `POST /api/checkout` is written with T-E3-05.
- Shipping cost of step 1 (T-E3-03): `GetShippingCost` (`ordering.application`) with the `Actor` and a `Province`, read only, as the buyer (Row Level Security of the cart). It takes the actor's cart priced as UC-09 (only `AVAILABLE` lines count in the subtotal, the coupon evaluated again) through `cart.api.CartSummaries`, and the shipping cost through `shipping.api.ShippingCosts` with the subtotal after the discount. Outbound ports of `ordering`: `CartAmounts` and `ShippingQuotes`, each with its fake and its contract test. Results: `Quoted`, `NoShipping`, `EmptyCart` (no cart, or none of its lines available; also the administrator and a guest without a cart cookie).
  - `GET /api/checkout/shipping-cost?province=SANTA_FE` → `200` with `province`, `subtotalCents`, `discountCents`, `shippingCents` and `totalCents` (total = subtotal − discount + shipping), so the client shows the step-1 summary without calculating anything; `409` problem `EMPTY_CART`; `422` problem `NO_SHIPPING` with `province`; `400` without `province` or with an unknown one. Errors as `problem+json`.
  - No rate-limit policy of its own: RNF-22 does not list it, and the checkout policy counts confirmations (`POST /api/checkout`, T-E3-19).
- The checkout refuses a cart with items that are not `AVAILABLE` or with unacknowledged price changes (UC-09), and validates the coupon again (UC-10).
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
- **After the commit, as `SYSTEM` (T-E3-06, T-E3-17):** the `payment` row and the provider's checkout, with the provider's idempotency header derived from the order number (RNF-20.8). A retry that finds the order without a checkout creates it.
- `PaymentGateway` strategy (`payments.application`, ARCHITECTURE.md section 5): MercadoPago now, MODO in E3b, both under `PaymentGatewayContractTest`.
- The tracking link carries a token of at least 128 bits from `SecureRandom`; only its SHA-256 hash is stored (RNF-29.6). Endpoints `GET /api/orders/{token}` and `POST /api/orders/{token}/retry-payment` (T-E3-08).
- Analytics: `purchase`, once per order and only when `PAID` (T-E3-20).

---

## UC-15 Confirm the payment (webhook)

**Actor:** System · **Module:** `payments` + `ordering` · **Stage:** E3

**Main flow**
1. The provider notifies a status change.
2. The system queries the payment at the provider to verify its real status.
3. Depending on the status: approved → order `PAID` and stock deducted for good; rejected → the reserved stock is released; pending → no change.

**Business rules**
- Idempotent: the same notification repeated produces no double effects (RNF-20.3, RNF-20.5).
- Every notification received is recorded in `payment_event`, and the payment's verified status in `payment`.
- The signature is verified before anything is processed (RNF-29.3).

**Acceptance criteria**
- Given an approved payment, when the notification arrives, then the order becomes `PAID` and the coupon used gets one more use.
- Given the same notification received twice, when the second is processed, then no stock is deducted and no email is sent twice.
- Given a rejected payment, when the notification arrives, then the reserved stock is released.
- Given a notification for a payment that does not exist, when it arrives, then it is ignored and a warning without personal data is logged.
- Given two identical notifications arriving at once, when processed, then only one has effects.

**Design notes**
- Endpoint: `POST /api/payments/mercadopago/webhook`, excluded from CSRF and rate limiting, protected by the signature (T-E3-07, T-E3-15).
- An approved payment records the coupon use in `coupon_redemption` (T-E2-01) through `promotions.api`.
- State transitions are conditional updates (`WHERE status = :expected`); no row affected means already processed (RNF-20.5, T-E3-16).

---

## UC-16 Reserve and release stock

**Actor:** System · **Depends on:** UC-13, UC-15 · **Module:** `inventory` + `ordering` · **Stage:** E3

**Main flow**
1. When the payment starts, the system atomically reserves the quantity of every variant of the order.
2. The reservation expires after 30 minutes without an approved payment (decided).
3. With an approved payment, the reservation becomes a final deduction.
4. With a rejected payment or an expired reservation, the stock is released and the order becomes `EXPIRED`.

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
  - `deduct(orderId)`: every reservation of the order not yet deducted or released becomes a `DEDUCTION` (`on_hand` and `reserved` lower by its quantity). `release(orderId)`: every such reservation gets its `RELEASE` (`reserved` lowers). Repeating either changes nothing; an order without such a reservation is left as it is. What a payment approved after its reservation was released does is settled with T-E3-07.
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
- `EmailSender` port with a transactional service behind it (proposed: Resend) and `EmailSenderContractTest`; outbox `email_outbox` unique on `(type, reference)`, enqueued in the same transaction as the state change, sent by a job every minute with retries at 1, 5, 15 and 60 minutes, up to 5 attempts (T-E3-09, RNF-23).
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
- When cancelling a `PAID` or `PREPARING` order, the administrator decides whether to put the stock back.
- Refunds are done from the payment provider's panel (out of scope for this version).

**Acceptance criteria**
- Given an `AWAITING_PAYMENT` order, when moving it to `SHIPPED` is attempted, then it is rejected.
- Given an order that becomes `SHIPPED`, when the shipment tracking number is saved, then the buyer sees it on its tracking link.
- Given the list of orders, when filtered by `PAID`, then only paid orders appear.
- Given an order with a bounced email, when the administrator opens the detail, then an alert shows the buyer's phone to contact it.

**Design notes**
- `OrderStatus` transition table inside the `Order` aggregate (ARCHITECTURE.md section 6); each transition is a conditional update (RNF-20.5).
- Putting the stock back on a cancelled paid order is a new order movement type of the inventory, added with T-E3-10.
- The order detail with the bounce alert is composed in `backoffice`, which depends on `ordering` and `notifications` (ARCHITECTURE.md section 3, rule 3).
- Endpoints (plan): `GET /api/admin/orders?status=`, `GET /api/admin/orders/{id}`, `POST /api/admin/orders/{id}/status`. The detailed contract is written with T-E3-10.
- Rate-limit policy: authenticated back office (300 per minute per user).

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
