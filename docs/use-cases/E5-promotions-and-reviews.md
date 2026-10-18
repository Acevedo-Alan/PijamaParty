# E5 – Promotions and reviews

**Status:** Draft · **Use cases:** UC-23 to UC-25 (and the UC-11 and UC-28 changes they bring) · **Spanish counterpart:** `docs/specs/E5-promos-resenas/spec.md`

**Modules involved:** `promotions`, `cart`, `ordering`, `reviews`, `catalog.api`, `accounts.api`, `platform`.

Automatic coupons apply on their own when the cart meets their rule; they never stack with another discount. Customers who bought a product can rate it; a review shows on the product page only once the administrator approves it.

**Decisions settled before the stage (T-E5-01, with the owner):**
- **Rule types:** an automatic coupon uses the fields every coupon already has (validity period, minimum purchase, maximum uses, UC-11), plus an optional **scope** of specific products and categories. No other rule types in v1.
- **Scope:** a scoped coupon discounts only the matching lines (a line matches when its product is one of the scope's products or belongs to one of its categories). Its minimum purchase is compared with the subtotal of those lines, and a fixed amount takes at most that subtotal. Only automatic coupons have a scope.
- **No stacking:** among the applicable automatic coupons and the cart's code coupon, only the largest discount applies. On a tie the code coupon wins; between automatic coupons the oldest one (`created_at`, then `id`) wins.
- **Automatic coupons keep a code:** a unique code in the format of UC-11 that works as their label ("Descuento VERANO10" in the cart, the order, the emails and analytics), so orders keep `coupon_code` as today. Typing an automatic coupon's code in the cart is refused as unknown. Whether a coupon is automatic is fixed when it is created.
- **Maximum uses:** optional on automatic coupons too, counted when the order is paid (UC-15) as for code coupons; a used-up automatic coupon stops applying.
- **Who can review:** a customer with an order in `PAID`, `PREPARING`, `SHIPPED` or `DELIVERED` that includes a variant of the product. One review per customer and product. Editing it sends it back to `PENDING`, so it leaves the product page and the average until approved again.
- **Review content:** a rating from 1 to 5 and an optional text of at most 1,000 characters. The product page shows the author's first name only: the first word of the account's name when the review was last submitted.
- **Deleting the account (UC-28) deletes the customer's reviews** (this replaces the *Proposed* anonymization of UC-28). The data download includes them.

**Technical decisions (T-E5-01):**
- **The average is computed when read, not stored on `product`.** `product` belongs to `catalog` and `reviews` may not write it (ARCHITECTURE.md section 3, rule 4); the catalog listing does not show ratings, so only the product page needs the average, and an aggregate over an index on `(product_id, status)` is enough at this volume (<20 orders a day). This replaces the derived columns of the plan.
- **The scope is a table, not a JSON `rule` column:** each target is a row with a real type and a uuid, so it is validated by the schema and needs no JSON parsing in the domain.

**Schema (T-E5-02, V37):**
- `coupon` (`promotions` module) gains `automatic` (boolean, not null, false for every existing coupon).
- `coupon_scope` (`promotions` module): `coupon_id` (foreign key to `coupon`, deleted with it), `target_type` (`PRODUCT` or `CATEGORY`), `target_id` (refers to `catalog`, no foreign key, ARCHITECTURE.md section 3, rule 6). Primary key `(coupon_id, target_type, target_id)`. Only an automatic coupon has rows (enforced by the domain; a trigger backs it). No Row Level Security, as `coupon`. A target the catalog no longer has simply matches no line.
- `review` (`reviews` module): `id`, `product_id` (refers to `catalog`, no foreign key), `user_id` (refers to `accounts`, no foreign key), `rating` (1 to 5), `text` (null, or 1 to 1,000 characters, trimmed), `author_name` (1 to 100 characters), `status` (`PENDING`, `APPROVED` or `REJECTED`), `created_at`, `updated_at` (the last submission), `moderated_at` (null while pending). Unique on `(user_id, product_id)`. Indexed by `(product_id, status)` for the product page and by `(status, updated_at)` for moderation.
- Row Level Security on `review`, enabled and forced (RNF-27). The policy tests with the five actors are T-E5-10.

| Table | Customer | Guest (`ANONYMOUS`) | Administrator | System |
|---|---|---|---|---|
| `review` | Reads the approved ones and all of its own; creates its own; updates its own (only `rating`, `text`, `author_name`, `updated_at`, and `status` back to `PENDING`); deletes its own (only when its account is deleted, UC-28) | Reads the approved ones | Reads all; updates `status` and `moderated_at`; deletes | – |

---

## UC-23 Apply an automatic coupon by rules

**Actor:** System (configured by the Administrator in UC-11) · **Module:** `promotions` + `cart` + `ordering` · **Stage:** E5

**Preconditions:** the administrator created an automatic coupon (UC-11).

**Main flow**
1. Every time a cart is priced (UC-09), the system evaluates every active automatic coupon against the cart's lines.
2. When one applies, its discount appears without the customer typing a code.
3. The cart and the checkout show the discount and its coupon's code.

**Alternative flows**
- The cart also holds a code coupon (UC-10) that applies: the larger discount applies, the code coupon on a tie. When the automatic coupon wins, the cart says the typed code is not added because a better discount already applies.
- The cart stops meeting the rule (an item is removed, the coupon expires, is deactivated or is used up): the discount is withdrawn on the next pricing.

**Business rules**
- An automatic coupon applies when it is active, within its validity, has uses left and the cart (or, for a scoped coupon, its matching lines) reaches its minimum purchase. The checks and the discount calculation are those of UC-10, on the matching subtotal.
- Only `AVAILABLE` lines count, as for the subtotal (UC-09).
- Discounts never stack: one coupon discounts, the one that takes the most.
- A use is counted only for the coupon that discounted, when the order is paid (UC-15).
- The checkout validates everything again, as for a code coupon (UC-13). A held code coupon that does not apply still refuses the checkout (UC-13), even when an automatic coupon applies.

**Acceptance criteria**
- Given a rule "10% from $30,000", when the cart exceeds $30,000, then the discount appears without entering a code.
- Given a cart that falls below the minimum when an item is removed, when it is priced again, then the discount is withdrawn.
- Given two applicable rules, when the cart is priced, then the one that discounts more is used.
- Given a rule outside its validity period, when the cart is priced, then it does not apply.
- Given an automatic coupon scoped to a category, when the cart holds items of that category and others, then the discount is calculated only on the items of that category.
- Given an applicable automatic coupon and a typed code that discounts more, when the cart is priced, then only the code's discount applies.
- Given an automatic coupon and a typed code with the same discount, when the cart is priced, then the code applies.
- Given an order paid with an automatic coupon, when its use is recorded, then the order keeps the coupon's code and the coupon has one more use.

**Design notes**
- **Engine (T-E5-03, `promotions.domain`):** each rule type is a `DiscountRule` implementation (validity, maximum uses, minimum purchase, scope), so a new type is a new class (O of the SOLID checklist). A coupon applies when all its rules pass; a domain service picks the best discount among the candidates with the tie rules above. The code coupon's evaluation and statuses of UC-10 stay as they are.
- **Lines to price:** the scope needs each line's product and category. `cart` gets them from `catalog.api` (`SellableVariants` returns them with the price) and passes `promotions.api` the `AVAILABLE` lines (`productId`, `categoryId`, amount); `promotions` depends on no module.
- **`promotions.api` (T-E5-04):** a new read-only `CartDiscounts` port takes the lines and the cart's code coupon id (optional) and answers the code coupon's evaluation as today (`CouponDiscount`, its status computed on the matching subtotal) and the **applied discount**: the winning coupon's id, code, whether it is automatic and its amount, or none. `CouponDiscounts.byCode` ignores automatic coupons (they are unknown), and `byId` of an automatic coupon answers empty.
- **Cart responses (T-E5-04):** `coupon` stays the held code coupon (UC-10); a new `appliedDiscount` is `null` or `{code, automatic}`, and `discountCents` is its amount. When the held code coupon is `APPLICABLE` but an automatic coupon won, `coupon.status` stays `APPLICABLE` and the panel shows "Tu cupón PIJAMA10 no se suma: ya tenés un descuento mejor.". The discount line is labeled with `appliedDiscount.code` ("Descuento VERANO10").
- **Checkout (T-E5-04):** `cart.api.CheckoutCart.Ready` carries the applied discount instead of only the held coupon; the order stores the winning coupon's id and code (`orders.coupon_id`, `coupon_code`, no schema change), and `CouponRedemptions.redeem` counts that coupon when the order is paid. The refusal of a held code coupon that does not apply (`CouponNotApplicable`) stays. Analytics sends the applied code (`coupon` of `begin_checkout` and `purchase`), as the emails and the tracking page already show the order's `coupon_code`.

---

## UC-11 Manage coupons (changes of E5)

**Actor:** Administrator · **Module:** `promotions` · **Stage:** E5 (T-E5-05)

**Business rules added**
- A coupon is created as automatic or as a code coupon, and it cannot change later.
- An automatic coupon may have a scope: at most 100 targets among products and categories. A code coupon has none.
- Every other rule of UC-11 applies to automatic coupons: unique code, value, validity, maximum uses, minimum purchase, activating, deactivating, and deleting only without uses.

**Acceptance criteria**
- Given an automatic coupon, when it is created with a scope of one category, then the list shows it as automatic with that category.
- Given a code coupon, when a scope is sent for it, then it is refused.

**Design notes**
- Endpoints under `/api/admin/coupons` as UC-11. `POST` adds `automatic` (optional, false when absent) and `scope` (optional: `{productIds, categoryIds}`); `PUT` takes `scope` and ignores `automatic`. Every coupon in the answers adds `automatic` and `scope`. A scope on a code coupon, or of more than 100 targets, gets `422` with `SCOPE` in `fields`.
- The ids are stored as given: `promotions` cannot check them against the catalog. The back office picks them from the catalog's own admin lists, and shows a target the catalog no longer has as "Eliminado".
- Back office `/admin/cupones`: the form adds "Automático" (only when creating) and, for an automatic coupon, pickers of categories and products. The list marks automatic coupons and shows their scope ("Todo el carrito" without one).

---

## UC-24 Leave a review

**Actor:** Customer · **Module:** `reviews` + `ordering.api` + `catalog.api` + `accounts.api` · **Stage:** E5

**Preconditions:** the customer is signed in.

**Main flow**
1. The customer opens a product it bought and picks a rating from 1 to 5, with an optional text.
2. The system saves the review as `PENDING` until it is moderated (UC-25).
3. Once approved, it appears on the product page and counts toward the average.

**Alternative flows**
- The customer did not buy the product: it is told that only customers who bought it can review it.
- The customer already reviewed the product: it edits its review instead, which goes back to `PENDING`.
- A visitor who is not signed in sees the reviews and an invitation to sign in to leave one.

**Business rules**
- Only a customer with an order in `PAID`, `PREPARING`, `SHIPPED` or `DELIVERED` that includes a variant of the product can review it; it is checked on every submission (creating and editing). A review already submitted stays if that order is cancelled later.
- One review per customer and product.
- Rating: a whole number from 1 to 5. Text: optional, trimmed, at most 1,000 characters; blank is none.
- The author's name on the page is the first word of the account's name when the review was last submitted.
- The average is the mean of the approved ratings, shown with one decimal ("4,5"), with the number of approved reviews.

**Acceptance criteria**
- Given a customer who did not buy the product, when it tries to review it, then it is told that only customers who bought it can review it.
- Given a review just submitted, when the product page is opened, then it is not shown until it is approved.
- Given a product with approved reviews of 4 and 5, when the product page is opened, then the average is 4.5 and the count is shown.
- Given an approved review, when its author edits it, then it leaves the product page and the average until it is approved again.
- Given a customer who deletes its account (UC-28), when the product page is opened, then its reviews no longer show or count.

**Design notes**
- Inbound ports (T-E5-06): `SubmitReview` (creates or replaces the customer's review of a product) and `GetMyReview` (whether the customer may review the product, and its review with its status). Product page reads (T-E5-07): `ListApprovedReviews`.
- Outbound ports: `PurchasedProducts` (backed by a new `ordering.api` query: whether the customer has an order in those statuses with any of the given variants, read under its own Row Level Security), `ProductVariants` (backed by `catalog.api`: a product's variant ids, by its id), `ListedProducts` (backed by `catalog.api`: the id of the listed product with a slug, T-E5-07), `CustomerNames` (backed by `accounts.api`: the signed-in customer's name), `LoadReview`, `SaveReview`, `ApprovedReviews` (read side, with the average and count).
- Endpoints:
  - `GET /api/account/reviews/{productId}` → `200 {eligible, review}`, `review` `null` or `{rating, text, status, updatedAt}`.
  - `PUT /api/account/reviews/{productId} {rating, text?}` → `201` the first time, `200` after; `422` `NOT_A_BUYER` (the message "Solo pueden opinar quienes compraron este producto."), `422` `INVALID_REVIEW` with `fields`; `404` unknown product.
  - `GET /api/catalog/products/{slug}/reviews?page=` → `200 {average, count, reviews: [{authorName, rating, text, createdAt}]}`, approved ones, newest first (`createdAt`, then the id), 10 per page from page 0; `average` is `null` without approved reviews; `404` when no listed product has the slug, as `GET /api/catalog/products/{slug}`. Served by `reviews`, which resolves the slug through `catalog.api`. Rate-limit policy: catalog (120 per minute per IP).
- The product page (T-E5-07) shows the average and count near the price, and the reviews below the description; signed in, a "Tu opinión" form with the customer's review and its status ("En revisión", "No publicada" when rejected). The product's structured data adds `aggregateRating` when it has approved reviews (RNF-31.2); the product page has no structured data yet, so it ships with the product's `Product` and `Offer` in T-E6-23, from this endpoint's `average` and `count`.
- **Account deletion (UC-28):** `reviews` listens to `CustomerAccountDeleted` in the same transaction (the exception of rule 5 of ARCHITECTURE.md, idempotent) and deletes the customer's reviews. It implements the `REVIEWS` section of `accounts.api.CustomerDataContributor` (`productName`, `rating`, `text`, `status`, `createdAt`, `updatedAt`), passing its shared contract test.

---

## UC-25 Moderate reviews

**Actor:** Administrator · **Module:** `reviews` · **Stage:** E5

**Preconditions:** the administrator is signed in.

**Main flow:** see the pending reviews and approve, reject or delete them.

**Business rules**
- Approving shows the review and counts it toward the average; rejecting hides it and takes it out of the average. Either can be done on a review in any status.
- Deleting removes the review; its author can write a new one.
- Editing a review sends it back to `PENDING` (UC-24).

**Acceptance criteria**
- Given a rejected review, when the product page is read, then it does not appear or count toward the average.
- Given pending reviews, when the administrator opens the panel, then it sees them from the oldest to the newest.

**Design notes**
- Inbound ports (T-E5-08): `ListReviewsForModeration`, `ApproveReview`, `RejectReview`, `DeleteReview`; each command carries the `Actor`, and any actor other than `ADMIN` gets `Forbidden`.
- Outbound ports (T-E5-08): `ReviewsForModeration` (read side, one page of a status), `LockReview` (a review by id, locked until the decision is saved, so an edit of its author never interleaves with it), `SaveModeration` (only the status and the moderation time), `RemoveReview`, `ProductLinks` (backed by a new `catalog.api` query: the name and slug of products, published or not).
- Endpoints under `/api/admin/reviews` (every write requires the CSRF token):
  - `GET ?status=PENDING|APPROVED|REJECTED&page=` (`PENDING` when absent) → `200 {items, hasMore}`, 50 per page from page 0, as the other back-office lists; each review with its `id`, product (`id`, `name`, `slug`; `name` and `slug` `null` when the catalog no longer has it), `authorName`, `rating`, `text`, `status`, `updatedAt`; pending ones from the oldest submission to the newest (`updatedAt`, then the id), the others newest first. `400` for an unknown status or a page out of range.
  - `POST /{id}/approve`, `POST /{id}/reject` → `200` with the review; `404`. Repeating a decision changes nothing (its moderation time stays).
  - `DELETE /{id}` → `204`; `404`.
- Back office `/admin/resenas`: tabs by status, pending first, with approve, reject and delete (after a confirmation). Rate-limit policy: authenticated back office (300 per minute per user).
