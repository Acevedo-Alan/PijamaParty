# E1 – Catalog, search and product management

**Status:** Draft · **Use cases:** UC-01 to UC-07 and UC-26 · **Spanish counterpart:** `docs/specs/E1-catalogo/spec.md`

**Modules involved:** `catalog`, `inventory`, `accounts`, `platform`.

---

## UC-01 Browse the catalog

**Actor:** Visitor · **Module:** `catalog` (query side) · **Stage:** E1

**Preconditions:** active products exist.

**Main flow**
1. The visitor opens the shop or a category.
2. The system shows a grid of active products with photo, name and "from" price.
3. Products load 24 at a time with a "Show more" button.

**Alternative flows**
- Category without products: show a message and a link to the full catalog.
- Product with no stock in any variant: show it with a "Sold out" label.

**Business rules**
- Inactive products never appear.
- The "from" price is the lowest price among the product's active variants.

**Acceptance criteria**
- Given an inactive product, when the catalog is listed, then it does not appear.
- Given a product whose variants are all out of stock, when listed, then it appears with "Sold out".
- Given more than 24 matches, when the catalog opens, then 24 are shown and a button to show more.

**Design notes**
- Inbound query port: `ListProducts` (`catalog.application`). Outbound read port: `ProductListingQuery`.
- Adapter: `JdbcProductListingQuery` (`adapter.out.persistence`), native SQL with named parameters. Availability comes from the view `inventory.v_variant_availability`.
- Result record: `ProductListingItem(slug, name, thumbnailUrl, placeholder, fromPrice: Money, soldOut: boolean)`; the page record carries `hasMore`.
- Endpoint: `GET /api/catalog/products?category=&page=`. Offset pagination with page size 24; revisit if the catalog exceeds a few thousand products.
- Rate-limit policy: catalog (120 per minute per IP).

---

## UC-02 Filter and sort products

**Actor:** Visitor · **Module:** `catalog` · **Stage:** E1

**Preconditions:** none.

**Main flow**
1. The visitor opens the filters (a sliding panel on mobile).
2. Chooses values for price range, size, color, material, season (`SUMMER`, `MID_SEASON`, `WINTER`) and audience (`WOMEN`, `MEN`, `KIDS`).
3. The system updates the grid and the URL with the applied filters.
4. The visitor can sort by price (ascending or descending) or by newest.

**Alternative flows**
- No matches: show a message and an option to clear the filters.

**Business rules**
- Different filters combine with AND; several values of the same filter combine with OR.
- A size or color only counts when the variant has available stock.
- The sort key comes from an enum, never from raw request text.

**Acceptance criteria**
- Given the filters size M and season summer, when applied, then only products with an M variant in stock and season summer appear.
- Given an applied filter, when the URL is shared, then whoever opens it sees the same filters.
- Given filters with no matches, when applied, then a message and a "clear filters" option are shown.

**Design notes**
- Inbound query ports: `ListProducts` (extended with `ProductFilter`) and `GetFilterOptions`.
- `ProductFilter` is a record in `catalog.domain` (value object). `ProductSort` is an enum mapped to whitelisted SQL expressions in the adapter.
- A small `ProductFilterSql` builder in the adapter turns the filter into SQL conditions with named parameters. New filter = new condition class, no edits to a central `switch` (Open/Closed).
- Endpoints: `GET /api/catalog/products?size=&color=&material=&season=&audience=&minPrice=&maxPrice=&sort=&page=` and `GET /api/catalog/filters`.

---

## UC-03 View a product and pick a variant

**Actor:** Visitor · **Module:** `catalog` + `inventory.api` · **Stage:** E1

**Preconditions:** the product exists and is active.

**Main flow**
1. The visitor opens a product.
2. The system shows gallery, description, material, season and price.
3. The visitor picks a size and a color; the system shows the available stock of that combination.
4. With an available combination, the "Add to cart" button is enabled (UC-08).

**Alternative flows**
- Unknown or inactive slug: `404` page with a link to the shop.

**Business rules**
- Combinations without stock are shown disabled.
- "Add to cart" stays disabled until both size and color are chosen.
- Available stock = on hand − reserved.

**Acceptance criteria**
- Given a product with pink M in stock and light-blue M out of stock, when the detail opens, then only light-blue M is shown as sold out.
- Given a combination with available stock 0, when selected, then the buy button is disabled.
- Given the detail open, when the color changes, then price and photos update to that variant.
- Given a slug that does not exist, when opened, then a 404 page with a link to the shop is shown.

**Design notes**
- Inbound query port: `GetProductDetail`; sealed result `Found(ProductDetail)` or `NotFound`.
- `catalog.application` depends on the port `VariantAvailabilityLookup`, implemented by an adapter that calls `inventory.api.StockAvailability`.
- Endpoint: `GET /api/catalog/products/{slug}`; `NotFound` maps to `404` `problem+json`.

---

## UC-04 Sign in to the back office

**Actor:** Administrator · **Module:** `accounts` · **Stage:** E1

**Preconditions:** an administrator account exists (created from environment variables at deploy time).

**Main flow**
1. The administrator enters email and password.
2. The system validates the credentials and opens a session.

**Alternative flows**
- Wrong credentials: a generic message that does not reveal which field failed.
- Locked account: a message with the unlock time.

**Business rules**
- Only users with role `ADMIN` reach `/admin`.
- After 5 consecutive failures the account is locked for 15 minutes.
- There is a single administrator (the owner); no role or user management in the back office.
- Session expires after 60 minutes of inactivity.
- Proposed: optional second factor (TOTP).

**Acceptance criteria**
- Given correct credentials, when signing in, then the administrator reaches the panel.
- Given wrong credentials, when trying, then a generic message is shown.
- Given a user with role `CUSTOMER`, when opening `/admin`, then access is denied.
- Given more than 5 sign-in attempts in a minute from the same IP, when another arrives, then it receives `429` with `Retry-After`.

**Design notes**
- Inbound port: `SignInAdministrator`; sealed result `SignedIn`, `InvalidCredentials`, `Locked(until)`.
- Outbound ports: `UserRepository`, `PasswordHasher` (BCrypt or Argon2 adapter), `LoginAttemptRegistry`, `SessionIssuer`; optional `TotpVerifier`.
- The account lock rule lives in the `User` aggregate (`registerFailedAttempt(now)`), with time from the injected `Clock`.
- Endpoints: `POST /api/admin/auth/login`, `POST /api/admin/auth/logout`, `GET /api/admin/auth/me`. Session cookie `HttpOnly`, `Secure`, `SameSite=Lax`, CSRF protection on.

---

## UC-05 Manage categories

**Actor:** Administrator · **Module:** `catalog` · **Stage:** E1

**Main flow:** create, edit, reorder and deactivate categories.

**Business rules**
- The slug is unique.
- A category with products cannot be deleted; it can only be deactivated.

**Acceptance criteria**
- Given an existing category name, when another with the same slug is created, then it is rejected with a clear message.
- Given a category with products, when deletion is attempted, then only deactivation is allowed.

**Design notes**
- One use case per class (Single Responsibility): `CreateCategory`, `RenameCategory`, `ReorderCategories`, `DeactivateCategory`.
- `Category` aggregate with the `Slug` value object (normalized, unique); uniqueness is enforced by a database constraint and surfaced as a `SlugAlreadyUsed` result.
- Endpoints: `/api/admin/catalog/categories` (REST).

---

## UC-06 Manage products and variants

**Actor:** Administrator · **Module:** `catalog` (+ `inventory` through events) · **Stage:** E1

**Preconditions:** the administrator is signed in.

**Main flow**
1. The administrator creates a product with name, description, material, season, audience and category.
2. Defines the sizes and colors; the system generates the variant matrix.
3. Sets the price of each variant.
4. Uploads photos; the system converts them to WebP and generates thumbnails and a blurred placeholder.
5. Publishes the product.

**Alternative flows**
- Upload interrupted by a bad connection: the administrator can retry without duplicating the photo.

**Business rules**
- No two variants of a product share the same size and color.
- The SKU is unique across the shop.
- A product without at least one priced variant cannot be published.
- Photos: JPG, PNG or WebP up to 10 MB, at most 8 per product (proposed). The device shrinks each photo (max 1600 px per side) before upload.
- Initial stock is recorded through UC-07 (reason `INITIAL_LOAD`), so every stock change leaves a movement.

**Acceptance criteria**
- Given 3 sizes and 2 colors, when the matrix is generated, then 6 variants are created.
- Given a 4 MB JPG, when uploaded, then lighter WebP versions and a placeholder are stored.
- Given a hidden product, when published, then it appears in the catalog.
- Given an 8 MB photo chosen on a phone, when uploaded, then it is shrunk on the device first and processed by the server like any other.
- Given an upload cut by a lost connection, when the signal returns, then it can be retried without duplicating the photo.

**Design notes**
- Use cases: `CreateProduct`, `UpdateProduct`, `GenerateVariantMatrix`, `UpdateVariantPrice`, `UploadProductImage`, `ReorderProductImages`, `PublishProduct`, `HideProduct`.
- `Product` aggregate owns its `Variant` entities and enforces uniqueness of size + color and the publish rule. `VariantMatrix` is a domain service that builds variants from sizes × colors.
- Outbound ports: `ProductRepository`, `ImageProcessor` (WebP conversion, thumbnails, placeholder; at most 2 conversions at a time), `ImageStore` (volume served by Caddy under `/uploads`), `SkuGenerator`.
- Events (in `catalog.api`): `VariantCreated(variantId)`, `ProductChanged(productId)`. `inventory` listens to `VariantCreated` and creates a `StockLevel` with 0; the search index (UC-26) listens to `ProductChanged`.
- Upload endpoint accepts an `Idempotency-Key` so retries do not duplicate photos.
- Endpoints: `/api/admin/catalog/products`, `POST /api/admin/catalog/products/{id}/variants/generate`, `PATCH /api/admin/catalog/variants/{id}`, `POST /api/admin/catalog/products/{id}/images`.

---

## UC-07 Manage stock

**Actor:** Administrator · **Module:** `inventory` · **Stage:** E1

**Preconditions:** the variant exists (a `StockLevel` was created when it was).

**Main flow**
1. The administrator picks a variant and records an adjustment (add or subtract) with a reason.
2. The system updates the stock and appends a movement to the history.

**Alternative flows**
- Adjustment that would make stock negative: rejected with the current stock.

**Business rules**
- Stock never goes negative.
- Every stock change creates a movement (append-only).
- On mobile, the screen offers − and + buttons as well as a numeric field.

**Acceptance criteria**
- Given stock 3, when subtracting 5, then it is rejected.
- Given a valid adjustment, when saved, then the history shows date, user, amount and reason.

**Design notes**
- Inbound port: `AdjustStock`; sealed result `Adjusted(newOnHand)`, `WouldGoNegative(currentOnHand)`, `UnknownVariant`.
- `StockLevel` aggregate: `adjust(delta, reason, actor, now)` enforces the non-negative invariant. `StockMovement` is an immutable record.
- Outbound ports: `StockLevelRepository`, `StockMovementLog`.
- Public API for other modules (`inventory.api`): `StockAvailability` (read) and, from E3, `StockReservations` (reserve, commit, release). Keep the two interfaces separate (Interface Segregation).
- Endpoints: `POST /api/admin/stock-adjustments`, `GET /api/admin/variants/{id}/stock-movements`.
- Concurrency: conditional `UPDATE` (`WHERE on_hand + :delta >= 0`) plus a unique movement constraint, as in RNF-20.4.

---

## UC-26 Search products by text

**Actor:** Visitor · **Module:** `catalog` · **Stage:** E1

**Preconditions:** none.

**Main flow**
1. The visitor types in the search bar (on mobile, an icon opens a full-screen search).
2. From 2 characters on, the system suggests up to 5 products with photo and name.
3. On confirmation, it shows a grid of results sorted by relevance, with the same filters as UC-02.

**Alternative flows**
- No results: a message and links to the categories.

**Business rules**
- The search ignores accents and case and understands singular and plural in Spanish.
- It searches name, category, material, description and variant colors; the name weighs most.
- Only active products appear.
- Queries shorter than 2 characters are not executed; text is capped at 100 characters.

**Acceptance criteria**
- Given the product "Pijama de algodón", when searching "algodon" without the accent, then it appears.
- Given the same product, when searching "pijamas", then it appears.
- Given products matching by name and others only by description, when searching, then the name matches come first.
- Given a search with no results, when shown, then there is a message and links to the categories.
- Given a search combined with size M, when applied, then only products with an M variant in stock appear.
- Given an inactive product, when searching its name, then it does not appear.
- Given text with quotes, symbols or injection attempts, when searching, then it is treated as plain text and causes no error.
- Given more than 60 searches per minute from one IP, when another arrives, then it receives `429` with `Retry-After`.

**Design notes**
- Inbound ports: `SearchProducts`, `SuggestProducts`. Outbound read port: `ProductSearchQuery` (PostgreSQL full text, `spanish` configuration, `unaccent`).
- Index maintenance: an application-level listener `RefreshSearchVector` reacts to `ProductChanged` and recomputes the `search_vector` column (weights: name A, category and material B, description and variant colors C) with a GIN index. `unaccent` is not immutable, so a generated column is not possible.
- Query: `websearch_to_tsquery('spanish', unaccent(:q))`, ranked with `ts_rank`; suggestions use prefix matching on the last word.
- Out of v1: typo tolerance (`pg_trgm`).
- Endpoints: `GET /api/catalog/products?q=` and `GET /api/catalog/suggestions?q=`.
