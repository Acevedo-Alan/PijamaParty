# E2 – Cart and code coupons

**Status:** Draft · **Use cases:** UC-08 to UC-11 · **Spanish counterpart:** `docs/specs/E2-carrito-cupones/spec.md`

**Modules involved:** `cart`, `promotions`, `catalog.api`, `inventory.api`, `platform`.

In this stage the cart **does not reserve stock**; the reservation happens when the payment starts (E3). The cart lives on the server: guests are identified by the `cart_token` cookie, so the server validates prices and stock and the cart survives a closed browser.

**Schema (T-E2-01, V12 and V13):**
- `cart` (`cart` module): `id`, `token_hash` (SHA-256 of the guest's `cart_token`, lowercase hex, as `AccessToken.hash()`; the token itself is never stored, RNF-29.6), `user_id` (a customer's cart, E4; refers to `accounts`, no foreign key), `coupon_id` (the applied coupon; refers to `promotions`, no foreign key, ARCHITECTURE.md section 3, rule 6), `created_at` and `updated_at` (last activity, read by the daily cleanup of T-E2-04). Exactly one owner: a token hash or a user. Unique token hash and unique user, so a browser or a customer has one cart. Indexed by `updated_at`.
- `cart_item` (`cart` module): `id`, `cart_id` (foreign key, deleted with its cart), `variant_id` (refers to `catalog`, no foreign key), `quantity` (1 to 1,000,000, the stock maximum of UC-07), `last_seen_price_cents` (the variant price the customer last saw, so UC-09 can warn about a price change) and `created_at` (items show in the order they were added). One line per variant: unique on `(cart_id, variant_id)`.
- Row Level Security on `cart` and `cart_item`, enabled and forced (RNF-27): a customer reaches only its own cart (by `user_id`), a guest only the cart of its token hash, the system reads and deletes carts for the cleanup, and the administrator and a missing context reach nothing. Items follow their cart. The full policy test matrix is T-E2-13.
- `coupon` (`promotions` module, no Row Level Security: no personal data and outside the RNF-27 list): `id`, `code`, `type` (`PERCENTAGE` or `FIXED_AMOUNT`), `discount_value`, `valid_from`, `valid_until`, `max_uses`, `minimum_purchase_cents`, `active`, `created_at`, `updated_at`. E5 adds `automatic` and `rule` (UC-23).
- `coupon_redemption` (`promotions` module): `coupon_id` (foreign key), `order_id` (refers to `ordering`, no foreign key), `redeemed_at`. Primary key `(coupon_id, order_id)`, so a repeated confirmation counts once (RNF-20). Filled when an order is confirmed (E3); the number of uses and the "has uses" rule of UC-11 derive from it, and its foreign key refuses deleting a used coupon.

**Guest cart token and cleanup (T-E2-04):**
- The `cart_token` cookie identifies a guest's cart: 32 random bytes from `SecureRandom` (256 bits, RNF-29.6) in unpadded base64url (43 characters), through the `platform.application.TokenGenerator` port. It is named `__Host-cart_token`, with `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/` and `Max-Age` of 30 days, the cleanup period. The `__Host-` prefix (required `Secure`, `Path=/`, no `Domain`) makes browsers accept it only from this host over HTTPS, so a sibling subdomain or a plain-HTTP response cannot plant a token in a shopper's browser (cookie fixation); a cookie without the prefix is ignored.
- Every request with a well-formed cookie acts as a guest with that token, unless a session identifies the actor first (the administrator; the customer in E4). A malformed cookie is ignored: the request is a guest without a token. A well-formed token nobody issued simply finds no cart.
- A guest without a token gets one when it adds its first product (UC-08): the request runs with the new token, so the cart is created for it, and the response sets the cookie. A rejected add (`409`, `404`, `400`) sets no cookie. The other cart changes of a guest without a token keep answering `403`, and `GET /api/cart` an empty cart.
- Every successful cart change of a guest (`POST`, `PATCH`, `DELETE` and the price acknowledgment) sets the cookie again, so it lives 30 days from the last change, like the cart. `GET /api/cart` never sets it.
- **Daily cleanup:** a scheduled job, every day at 04:00 in `America/Argentina/Buenos_Aires` (property `store.cart.cleanup.cron`), deletes as `SYSTEM` (RNF-27) the carts whose `updated_at` is more than 30 days old, guests' and customers' alike; their items go with them. Inbound port `RemoveIdleCarts`, outbound port `DeleteIdleCarts`. It logs only the number of deleted carts.

---

## UC-08 Add a product to the cart

**Actor:** Visitor or Customer · **Module:** `cart` + `catalog.api` + `inventory.api` · **Stage:** E2

**Preconditions:** a variant with available stock is chosen (UC-03).

**Main flow**
1. The user taps "Add to cart".
2. The system adds the variant, or adds the quantity if it was already in the cart.
3. The cart opens automatically as a side panel, with the background dimmed (overlay).
4. On a phone there is a light vibration; the cart icon shows the counter with a bounce animation.

**Alternative flows**
- The quantity in the cart would exceed the available stock: rejected, telling how many are left.

**Business rules**
- No toast: the open panel is the confirmation.
- The quantity in the cart can never exceed the available stock (on hand − reserved).

**Acceptance criteria**
- Given a variant with stock 3 and 2 units already in the cart, when 2 more are added, then it is rejected with "Solo quedan 3".
- Given a successful add, when it finishes, then the side panel opens by itself and the counter updates.
- Given a device without vibration support, when adding, then the rest of the feedback works the same.
- Given a visitor without a cart, when adding the first product, then a cart tied to their browser is created.

**Design notes**
- Inbound port: `AddItemToCart` (`cart.application`, ARCHITECTURE.md section 7); `Command(actor, variantId, quantity)`; sealed result `Added(CartView)`, `InsufficientStock(variantId, available)`, `VariantUnavailable(variantId)` or `Forbidden`.
- Outbound ports: `LoadCart`, `SaveCart`, and `VariantAvailability` (backed by `catalog.api` and `inventory.api`). The `Cart` aggregate holds the stock rule (`add(variantId, quantity, available)`).
- Endpoint: `POST /api/cart/items {variantId, quantity}` → `200` with the full cart; `409` `problem+json` for insufficient stock (with `variantId` and `available`); `404` for an unknown or unavailable variant; `400` invalid body (quantity from 1 to 1,000,000); `403` for an actor that cannot own a cart.
- A variant can be added when the catalog sells it (published product of an active category, variant active and priced: `catalog.api.SellableVariants`); its available stock comes from `inventory.api.StockAvailability`. Adding a variant already in the cart adds to its line and records the current price as its `last_seen_price_cents`.
- The cart's owner comes from the actor: a customer by its user id, a guest by the hash of its `cart_token`. An actor that cannot own a cart (the administrator, a guest without a token) gets `403` on every cart change and an empty cart on `GET /api/cart`: Row Level Security gives them no cart (T-E2-01).
- A guest without a token gets one, and its `cart_token` cookie, with its first product (T-E2-04).
- Cart responses: the items in the order they were added, each with `id`, `variantId` and `quantity`, priced with the current catalog prices and stock, with the cart totals (UC-09, T-E2-03).
- Rate-limit policy: cart changes (60 per minute per IP), activated in T-E2-04 (brought forward from T-E2-11: a client without a cookie creates a cart with each add, so the policy also bounds cart creation per IP).
- Analytics: `add_to_cart` (T-E2-12, RNF-25).

---

## UC-09 Change the cart

**Actor:** Visitor or Customer · **Module:** `cart` · **Stage:** E2

**Main flow**
1. The user changes the quantity of an item or removes it.
2. The system revalidates stock and prices and recalculates the totals.

**Business rules**
- If the price or the stock changed since the item was added, the user is warned before going on.
- An item whose variant ran out of stock is marked unavailable and cannot be bought.
- Totals are always calculated by the server (R-01, R-02): subtotal, discount and total.

**Acceptance criteria**
- Given an item in the cart, when its quantity is changed to 0, then it is removed.
- Given a variant that sold out while in the cart, when the cart opens, then it is shown as unavailable.
- Given a price change in the catalog, when the cart opens, then the total uses the new price and the change is announced.

**Design notes**
- Inbound ports: `GetCart`, `ChangeItemQuantity`, `RemoveItem` (one class per use case).
- Endpoints: `GET /api/cart` (current cart with totals), `PATCH /api/cart/items/{id} {quantity}`, `DELETE /api/cart/items/{id}`. Every response returns the full cart state.
- `PATCH` answers like `POST /api/cart/items`: `200` with the full cart; `409` `problem+json` when the new quantity is above the available stock (with `variantId` and `available`); `404` for an item that is not in the actor's cart; `400` for a quantity outside 0 to 1,000,000; `403` as in UC-08. A variant the catalog no longer sells has 0 available, so it can only be set to 0 or removed. Quantity 0 removes the item without checking stock. `DELETE` answers `200` with the full cart, `404` or `403`.
- Two tabs changing the same cart: the last action wins, and the server always returns the full state. A change locks the cart row, so two changes of one cart run one after the other.
- The price warning compares the current variant price with `cart_item.last_seen_price_cents` (T-E2-03).
- Every cart response (T-E2-03) prices the cart with the current catalog prices and stock. Each item has `unitPriceCents` (the current price; the last seen price when the catalog no longer sells it), `previousUnitPriceCents` (the last seen price when the current one differs, otherwise `null`), `available` (the units that can be bought now) and `status`:
  - `AVAILABLE`: the catalog sells it and its quantity is within the stock.
  - `INSUFFICIENT_STOCK`: some units are left, but fewer than its quantity ("Solo queda 1"). The quantity is kept; the customer lowers it.
  - `UNAVAILABLE`: sold out, or the catalog no longer sells it.
- The cart has `subtotalCents` (the `AVAILABLE` items at their current price), `discountCents` (0 until the coupons of T-E2-05) and `totalCents` (subtotal − discount). Only `AVAILABLE` items can be bought; the checkout (E3) refuses a cart with other items or with unacknowledged price changes.
- A price change is announced until the customer acknowledges it: `POST /api/cart/acknowledge-prices` (inbound port `AcknowledgePriceChanges`) records the current price of every item the catalog sells as its last seen price and answers `200` with the full cart, or `403` as in UC-08. Without a cart it answers an empty cart. `GET /api/cart` never writes.
- Rate-limit policy: cart changes (60 per minute per IP), activated in T-E2-04 (brought forward from T-E2-11: a client without a cookie creates a cart with each add, so the policy also bounds cart creation per IP).

---

## UC-10 Apply a coupon code

**Actor:** Visitor or Customer · **Module:** `cart` + `promotions.api` · **Stage:** E2

**Main flow**
1. The user types a code in the cart (or in the checkout).
2. The system validates the coupon and shows the discount.
3. The user can remove the coupon.

**Business rules**
- One coupon per cart; applying another replaces it.
- The coupon must be active, within its validity period, with uses left and with the cart at or above its minimum purchase.
- The discount is always calculated on the server. A `FIXED_AMOUNT` discount never exceeds the subtotal.
- Validity is by date, both bounds inclusive and optional, in `America/Argentina/Buenos_Aires` (R-07).
- The code is matched ignoring case.

**Acceptance criteria**
- Given a valid 10% coupon, when applied to a $20,000 cart, then the discount is $2,000.
- Given an expired coupon, when applied, then "El cupón venció" is shown.
- Given a cart below the minimum, when applied, then the user is told how much is missing to reach it.
- Given a used-up coupon (all uses taken), when applied, then it is rejected.
- Given a coupon already applied, when another is applied, then it replaces it.
- Given more than 10 coupon attempts per minute on the same cart, when another arrives, then it receives `429` with `Retry-After`.

**Design notes**
- Inbound ports: `ApplyCoupon` and `RemoveCoupon` (`cart.application`); the validation and the discount come from `promotions.api` (T-E2-05). Each failure is a sealed result variant (unknown, inactive, not started, expired, used up, below minimum with the missing amount).
- Endpoints: `POST /api/cart/coupon {code}`, `DELETE /api/cart/coupon`.
- A use is counted only when the order is confirmed (E3, `coupon_redemption`), not when applied.
- Rate-limit policy: coupon (10 per minute per cart and IP), activated in T-E2-11.

---

## UC-11 Manage coupons

**Actor:** Administrator · **Module:** `promotions` · **Stage:** E2

**Preconditions:** the administrator is signed in.

**Main flow:** create, edit, activate and deactivate coupons; see how many times each one was used.

**Coupon data:** code, type (percentage or fixed amount), value, validity period, maximum uses and minimum purchase.

**Business rules**
- The code is unique and case-insensitive: 3 to 20 characters among letters, digits and hyphen, stored in upper case.
- `PERCENTAGE`: a whole percent from 1 to 100. `FIXED_AMOUNT`: an amount in cents above 0.
- Validity: `valid_from` and `valid_until` are optional dates, inclusive; the start cannot be after the end.
- Maximum uses and minimum purchase are optional: empty means unlimited uses and no minimum. When present, they are above 0.
- A used coupon is not deleted: it is deactivated.
- Automatic rules are added in E5 (UC-23).

**Acceptance criteria**
- Given an existing code, when another with the same code is created, then it is rejected.
- Given a coupon with recorded uses, when deleting it is attempted, then only deactivating it is allowed.

**Design notes**
- `Coupon` aggregate in `promotions.domain` holds the rules above.
- Endpoints: `/api/admin/coupons` (create, edit, activate and deactivate, list with the number of uses), T-E2-06.
- Rate-limit policy: authenticated back office (300 per minute per user).
