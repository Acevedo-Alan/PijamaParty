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
- Unknown or inactive category: `404`, shown as the same message.
- Product with no stock in any variant: show it with a "Sold out" label.

**Business rules**
- A product is listed when it is published, its category is active and it has an active priced variant. Hidden products and products of inactive categories never appear.
- The "from" price is the lowest price among the product's active priced variants, with or without stock. The card says "Desde" before it only when those variants have different prices; with a single price it shows the price alone.
- A product is sold out when none of its active priced variants has available stock (on hand − reserved).
- Newest first by default (creation date of the product).
- The grid shows the product's first photo (any color); a product without photos shows none.

**Acceptance criteria**
- Given an inactive product, when the catalog is listed, then it does not appear.
- Given a product whose variants are all out of stock, when listed, then it appears with "Sold out".
- Given more than 24 matches, when the catalog opens, then 24 are shown and a button to show more.

**Design notes**
- Inbound query port: `ListProducts` (`catalog.application`); sealed result `Listed(items, hasMore)`, `UnknownCategory` or `InvalidFilter`. No actor: the listing is public. The category comes from `LoadCategories` by slug and must be active.
- Outbound read port: `ProductListingQuery`. Adapter: `JdbcProductListingQuery` (`adapter.out.persistence`), native SQL with named parameters. Availability comes from the view `v_variant_availability` (V9), published by `inventory` (rule 4 of ARCHITECTURE.md section 3): `available` is `on_hand` until E3 subtracts reservations. The view is read-only for `tienda_app`.
- Result record: `ProductListingItem(slug, name, image?, fromPrice: Money, soldOut: boolean)`; `image` carries the placeholder and the files of the first photo (400, 800 and 1200 px, for `srcset`).
- Endpoint: `GET /api/catalog/products?category=&page=` (category by slug) → `200` `{items, hasMore}`, each item with `slug`, `name`, `image` (or null: `placeholder` and `files` with `width` and `url`), `fromPriceCents` and `soldOut`; `400` invalid parameters; `404` unknown or inactive category. Offset pagination with page size 24, pages from 0 to 1,000; revisit if the catalog exceeds a few thousand products.
- Rate-limit policy: catalog (120 per minute per IP), activated in T-E1-15.
- Storefront pages (T-E1-09): the whole shop at `/` and a category at `/categoria/{slug}`; each card links to `/producto/{slug}` (UC-03). These public paths are Spanish because customers read and share them; API routes stay in English. An unknown or inactive category shows the empty-category message with a link to `/`. A failed "Show more" keeps the products already shown and lets the visitor retry.

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
- Size and color apply to the same variant: with size M and color pink, a product appears only when one of its active priced variants is M, pink and has available stock.
- The price range applies to the "from" price (UC-01), bounds included, in cents. A minimum above the maximum is invalid.
- Sizes, colors and materials match the exact text. At most 20 values per filter.
- Sort: `NEWEST` (default), `PRICE_ASC` or `PRICE_DESC` on the "from" price; ties go newest first.
- The sort key comes from an enum, never from raw request text.
- The filter options are those of the listed products of the category (or of the whole shop), not narrowed by the other filters: sizes and colors of active priced variants with available stock, materials, seasons and audiences, and the lowest and highest "from" price. Sizes and colors follow their smallest display position, then the text; materials go by text; seasons and audiences in the enum order.

**Acceptance criteria**
- Given the filters size M and season summer, when applied, then only products with an M variant in stock and season summer appear.
- Given an applied filter, when the URL is shared, then whoever opens it sees the same filters.
- Given filters with no matches, when applied, then a message and a "clear filters" option are shown.

**Design notes**
- Inbound query ports: `ListProducts` (with `ProductFilter` and `ProductSort`) and `GetFilterOptions` (sealed result `Found(FilterOptions)` or `UnknownCategory`). Outbound read ports: `ProductListingQuery` and `FilterOptionsQuery`.
- `ProductFilter` is a record in `catalog.domain` (value object) that enforces the limits above. `ProductSort` is an enum in `catalog.domain`.
- The SQL is one constant text block per query (RNF-28: Semgrep rejects SQL built at run time). Every filter is a condition that an empty array parameter switches off (`cardinality(:sizes) = 0 OR ...`), and the sort is a whitelisted `CASE` on the bound enum name. A new filter adds a condition and a parameter to that query.
- Endpoints: `GET /api/catalog/products?category=&size=&color=&material=&season=&audience=&minPrice=&maxPrice=&sort=&page=` (repeat a parameter for several values) and `GET /api/catalog/filters?category=` → `200` `{sizes, colors, materials, seasons, audiences, minPriceCents, maxPriceCents}` (prices null without listed products); `404` unknown or inactive category. Invalid values answer `400`.
- Storefront (T-E1-10): a "Filter" button opens the filters in a sliding panel (a modal `dialog`) at every width; the visitor picks values and confirms with "Show results", and "Clear" empties the panel. The sort is a select next to the button and applies at once. The URL is the source of truth, so a shared link shows the same filters and sort, and the browser's back button undoes the last change. Its query keys and values are Spanish, like the public paths: `talle`, `color`, `material` (repeated for several values, exact text), `temporada` (`verano`, `media-estacion`, `invierno`), `publico` (`mujer`, `hombre`, `infantil`), `precio-desde` and `precio-hasta` (whole pesos; the panel asks for pesos and the API receives cents) and `orden` (`menor-precio`, `mayor-precio`; absent means newest). Unknown values, values past the 20th of a filter and a minimum above the maximum are ignored when reading the URL; the panel refuses a minimum above the maximum. Groups without options are not shown, and the price group only appears when the options carry a price range. With filters and no matches the grid shows the no-matches message and "Clear filters", which keeps the sort. The panel lists its groups in the order of the filter bar (audience, size, color, material, season, price); each group opens and closes from its header, which shows the chosen values while closed, and the panel opens with the pressed button's group and every group with an applied value open. Sizes appear apart as letters (XXS to XXXL), numbers and the rest, each in natural order (2, 4, 10 rather than 10, 2, 4), with a heading per kind only when there are several. Above the grid, each applied value is a chip that removes it at once, and with more than one, "Clear all" removes them all; both keep the sort.

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
- A product opens when it is listed (UC-01): published, its category active and with an active priced variant. Otherwise its slug answers `404`, like an unknown one.
- Only the active priced variants are shown; the sizes and colors offered are theirs, in display order. The gallery holds the photos of the whole product and of the shown colors, by position.
- Each shown variant has its own price and its available stock (on hand − reserved) as an exact count.
- Combinations without stock are shown disabled.
- "Add to cart" stays disabled until both size and color are chosen.

**Acceptance criteria**
- Given a product with pink M in stock and light-blue M out of stock, when the detail opens, then only light-blue M is shown as sold out.
- Given a combination with available stock 0, when selected, then the buy button is disabled.
- Given the detail open, when the color changes, then the price updates to that variant and the gallery shows the photos of that color first.
- Given a slug that does not exist, when opened, then a 404 page with a link to the shop is shown.

**Design notes**
- Inbound query port: `GetProductDetail`; sealed result `Found(ProductDetail)` or `NotFound`. No actor: the detail is public. The product comes from `LoadProducts.findBySlug` (a text that is not a slug finds nothing), its category from `LoadCategories`; `Product.isListedIn(category)` holds the rule above and `Product.sellableVariants()` gives the active priced variants.
- `catalog.application` depends on the port `VariantAvailabilityLookup`, implemented by an adapter that calls `inventory.api.StockAvailability`. It answers every requested variant, 0 for one without a stock level.
- Storefront (T-E1-11): the page lives at `/producto/{slug}`; an unknown slug, and one the API rejects with `400`, shows the own 404 page. Color and size are two radio groups in the order of `colors` and `sizes`; a group with a single option starts picked (a size only when it has stock). A size without stock in the picked color (or in any color, before one is picked) is disabled. A color stays selectable, since it also orders the gallery, and is marked sold out when it has no stock in the picked size; picking it drops a size it has no stock in. The price is the picked variant's; before both are picked it is the lowest price of the variants that match the picks, prefixed "from" when they differ. With both picked, the exact available stock shows. The gallery shows a main photo (loaded eagerly, blurred placeholder until it loads) and thumbnails; a new color starts it again from that color's first photo. Alternative text is name, color (for photos of a color) and photo number. "Add to cart" does nothing until UC-08.
- `inventory.api.StockAvailability.available(variantIds)`: the available stock of each variant, read from the view `v_variant_availability` (V9) through the inventory port `AvailableStock`, so "on hand − reserved" is written only in the view. Joins the caller's transaction, read-only.
- Endpoint: `GET /api/catalog/products/{slug}` → `200` `{slug, name, description, material, season, audience, fromPriceCents, sizes, colors, variants, images}`; each variant with `id`, `sku`, `size`, `color`, `priceCents` and `available` (the `sku` is the item id of the analytics events from `add_to_cart` on, T-E2-12); each image with `color` (null for the whole product), `placeholder` and `files` (`width`, `url`). `NotFound` maps to `404` `problem+json`; a slug longer than 4 × 140 characters answers `400`.
- Rate-limit policy: catalog (120 per minute per IP), activated in T-E1-15.

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
- A second factor (TOTP) is mandatory for the administrator: the password alone never opens the back office (RNF-24, RNF-29.8).

**Acceptance criteria**
- Given correct credentials and a valid code of the authenticator app, when signing in, then the administrator reaches the panel.
- Given the right password and a wrong or already used code, when signing in, then the back office stays closed and the attempt counts as a failure.
- Given an administrator without a second factor, when the password is right, then the back office only opens after enrolling an authenticator app, and the recovery codes are shown once.
- Given an unused recovery code, when used instead of a code, then the administrator gets in once with it.
- Given wrong credentials, when trying, then a generic message is shown.
- Given a user with role `CUSTOMER`, when opening `/admin`, then access is denied.
- Given more than 5 sign-in attempts in a minute from the same IP, when another arrives, then it receives `429` with `Retry-After`.

**Design notes**
- Inbound ports: `SignInAdministrator` (the password; sealed result `PasswordAccepted(nextStep)`, `InvalidCredentials`, `Locked(until)`), then `VerifySecondFactor`, or `StartSecondFactorEnrollment` and `ConfirmSecondFactorEnrollment` (T-E1-16); `ResetAdministratorSecondFactor` at startup.
- Outbound ports: `LoadUser` and `SaveUser` (read and write), `UserSignInStore` (one `SYSTEM` transaction with the user's row locked), `PasswordHasher` (BCrypt adapter); the second-factor ports below (T-E1-16).
- The web adapter opens and closes the session through `platform.security.SessionAuthentication` (ARCHITECTURE.md section 8), so no session port reaches the application layer.
- Rate limits (T-E1-15, RNF-22): two policies on `POST /api/admin/auth/login`, each 5 per minute: `login-ip` (the `429` criterion) and `login-account` (RNF-29.5), keyed by the email of the body, normalized as `Email` does and stored as its SHA-256. The rate-limit filter reads the body before the controller through `PeekableRequest` (at most 8 KB) and replays it. The account is never looked up, so the `429` does not reveal whether it exists; a body without a usable email (not JSON, no email, over 8 KB) shares one bucket instead of escaping the limit.
- The account lock rule lives in the `User` aggregate (`registerFailedSignIn(now)`), with time from the injected `Clock`. `app_user` stores it in `failed_login_attempts` and `locked_until`. Sign-in runs without a signed-in actor, so reading the user and recording attempts happen as `SYSTEM`; RLS lets `SYSTEM` create the initial administrator, read users and update them (RNF-27).
- Endpoints: `POST /api/admin/auth/login`, `POST /api/admin/auth/logout`, `GET /api/admin/auth/me`. Session cookie `HttpOnly`, `Secure`, `SameSite=Lax`, CSRF protection on.
- Lock details (T-E1-02):
  - Only consecutive failures count: a successful sign-in resets the counter.
  - The 5th failure sets `locked_until = now + 15 minutes` and resets the counter, so after the lock the administrator has 5 attempts again.
  - While locked, the password is not checked and the attempt does not extend the lock.
- Responses:
  - Right password: `200` `{nextStep}`; the session opens (new session id and CSRF token) only after the second factor (T-E1-16).
  - `InvalidCredentials`: `401` generic `problem+json`, the same for an unknown email and a wrong password. An unknown email still costs one hash check, so timing does not reveal it.
  - `Locked`: `423` `problem+json` with `lockedUntil` (decided: this reveals that the email exists, acceptable with a single administrator).
  - Known risk: anyone who knows the administrator's email can keep the account locked with 5 attempts every 15 minutes, and the per-account limit of RNF-29.5 does not stop that. The limits of T-E1-15 slow that down but do not stop it (5 attempts per minute are enough). With the mandatory second factor (T-E1-16) a guessed or stolen password no longer opens the account, so this stays a nuisance, not a breach; counting failures per IP and account before locking remains an option if it happens.
  - `CUSTOMER` on `/api/admin/**`: `403`.
  - Rate limited (`429` with `Retry-After`): see the rate limits above.
- Passwords: BCrypt with cost 12, stored with the `{bcrypt}` prefix so the algorithm can change later. At most 72 bytes and at least 12 characters.
- Initial administrator:
  - Set with `ADMIN_EMAIL`, `ADMIN_NAME` and `ADMIN_INITIAL_PASSWORD` (a Docker secret).
  - At startup, if no administrator exists, it is created as `SYSTEM`. If one exists, these values are ignored and never overwrite it.
- Sign-in runs as `SYSTEM`, because nobody is signed in yet. RLS restricts what `SYSTEM` may change to the attempts and the lock (V5). The persistence adapter opens that system transaction, because use cases and web adapters never switch to `SYSTEM` (ARCHITECTURE.md section 9). `GET /me` runs as the `ADMIN` actor.
- Sessions expire after 60 minutes of inactivity (Spring Session JDBC). The session principal name is the user id, never the email (RNF-27).
- Back office (T-E1-12): the sign-in page is `/admin/ingresar`; `/admin` opens the product list. Every other back-office page asks `GET /me` first: without a session it opens the sign-in page and, once signed in, returns to the page asked for; a `CUSTOMER` sees an access-denied page. A `401` on a later call (expired session) opens the sign-in page again with a notice. Wrong credentials show one generic message, a lock shows the unlock time (Argentina time) and a `429` asks to wait. "Sign out" ends the session and opens the sign-in page. The back office has its own header (products, categories, view store, sign out) and is never indexed.
- Second factor (T-E1-16, RNF-24, RNF-29.8):
  - Mandatory: the back office never opens with the password alone. A right password answers `200` `{nextStep}` without opening the session: `SECOND_FACTOR` when the administrator has one, `ENROLLMENT` when not yet (the first sign-in after deploying, or after a reset). The session keeps a pending sign-in (user id only, plus the encrypted secret being enrolled) for 5 minutes; it is not authenticated and reaches nothing under `/api/admin/**` except the steps below.
  - The code is TOTP (RFC 6238: HMAC-SHA1, 6 digits, 30-second steps, one step of tolerance either side), from any authenticator app. A code is accepted once: the step of the last accepted code is stored and a code of that step or an earlier one is refused (replay).
  - `POST /api/admin/auth/second-factor {code}`: a TOTP code or an unused recovery code. Right: the session opens (new session id and CSRF token) and answers `200` with the administrator. Wrong: `401`, the same generic message, and it counts as a failed sign-in (same counter and 15-minute lock as a wrong password); locked: `423`; no pending sign-in or more than 5 minutes: `409`, start again with the password.
  - Enrollment: `GET /api/admin/auth/enrollment` answers `200` `{secret, otpauthUri, qrCode}` (the QR as a PNG `data:` URI, issuer "Pijama Party"); each call creates a new secret and keeps it, encrypted, in the pending sign-in. `POST /api/admin/auth/enrollment {code}` with a right code stores the second factor, creates 10 recovery codes, opens the session and answers `200` `{administrator, recoveryCodes}`, the only time the codes are shown. A wrong code answers `401` and counts as a failed sign-in; `409` as above, also when a second factor already exists.
  - Recovery codes: 10 codes of 10 characters from an alphabet without look-alikes, shown as `XXXXX-XXXXX`, stored only as SHA-256 and each usable once; case, spaces and dashes are ignored when typed. Enrolling again replaces them.
  - The secret is stored encrypted (AES-256-GCM) with the key `store.admin.second-factor-key` (base64 of 32 bytes; in production the Docker secret `SECOND_FACTOR_KEY`). Neither the secret nor a code ever reaches a log or an event.
  - Lost phone and lost recovery codes: start the API once with `ADMIN_RESET_SECOND_FACTOR=true`. At startup it deletes the administrator's second factor and recovery codes, so the next sign-in enrolls again; then remove the variable (runbook, T-E6-08).
  - Schema (V11): `app_user.totp_secret` (bytea, encrypted), `totp_enabled_at` and `totp_last_step`, all present or all absent; table `admin_recovery_code` (`user_id`, `code_hash`, `used_at`), unique on `(user_id, code_hash)`, with Row Level Security that lets only `SYSTEM` read and write it. The update guard of V5 also lets `SYSTEM` store a second factor only when there is none, move `totp_last_step` forward, or delete the second factor.
  - Ports: `TotpVerifier` (the step a code matches, if any), `TotpSecretGenerator`, `SecondFactorCipher` (encrypt and decrypt the secret), `RecoveryCodeGenerator`, `RecoveryCodes` (replace and consume, as `SYSTEM`) and `EnrollmentQrCode`; the sign-in store also finds and locks the user by id.
  - Rate limits: the `login-ip` policy (5 per minute per IP) also covers `POST /api/admin/auth/second-factor` and `POST /api/admin/auth/enrollment`.
  - Back office: after the password, the sign-in page asks for the 6-digit code (with a note that a recovery code also works). On enrollment it shows the QR, the secret as text for typing it by hand, and the code field; then the recovery codes, with "Download" (a `.txt` file) and a checkbox "I saved them" required before going on.
- Back office layout (T-E1-21, RNF-26): every screen works from 360 px wide without horizontal scrolling. Forms are one column on small screens; every link, button and switch is at least 44 px tall; long names, SKUs and notes wrap instead of widening the page. Lists are cards on small screens and, from 860 px, rows with their values aligned in columns (products: name, category and variants, state; stock history: quantity, reason, date and user). Photos go two per row at 360 px. Checked with a 360 px browser run of every screen (no horizontal overflow, no target under 44 px); the real devices come in T-E6-17.

---

## UC-05 Manage categories

**Actor:** Administrator · **Module:** `catalog` · **Stage:** E1

**Preconditions:** the administrator is signed in.

**Main flow:** list, create, rename, reorder, deactivate, reactivate and delete categories.

**Alternative flows**
- Name whose slug is already used by another category: rejected with a clear message.
- Category with products: deletion is rejected; the administrator can deactivate it instead.

**Business rules**
- The slug is unique.
- The slug is generated from the name: lower case, accents removed, every run of other characters turned into one hyphen, no hyphen at either end (`"Pijamas de Niñas"` → `pijamas-de-ninas`). Renaming regenerates it, so the URL always matches the name.
- The name is stored trimmed, 1 to 80 characters, without control or invisible characters, and must produce a slug of 1 to 100 characters.
- A category deleted while another change to it is in flight stays deleted (the change answers `404`).
- A new category goes last in the order.
- A category with products cannot be deleted; it can only be deactivated. A category without products can be deleted.
- Deactivation can be undone (reactivate).

**Acceptance criteria**
- Given an existing category name, when another with the same slug is created, then it is rejected with a clear message.
- Given a category with products, when deletion is attempted, then only deactivation is allowed.

**Design notes**
- One use case per class (Single Responsibility): `ListAllCategories`, `CreateCategory`, `RenameCategory`, `ReorderCategories`, `DeactivateCategory`, `ActivateCategory`, `DeleteCategory`. Each command carries the `Actor`; any actor other than `ADMIN` gets `Forbidden`.
- `Category` aggregate with the `CategoryName` and `Slug` value objects; uniqueness is enforced by a database constraint and surfaced as a `SlugAlreadyUsed` result.
- Outbound ports: `LoadCategories` (read), `SaveCategory` (write; returns `SLUG_ALREADY_USED` on the unique constraint) and `RemoveCategory` (returns `HAS_PRODUCTS` on the product foreign key). The catalog holds no personal data, so no Row Level Security (RNF-27).
- Reorder takes the complete list of category ids in the new order; a list that does not name every category exactly once is rejected (`InvalidOrder`).
- Endpoints under `/api/admin/catalog/categories` (errors as `problem+json`, the slug in the `slug` property of a `409`):
  - `GET` → `200` every category, active or not, by position.
  - `POST {name}` → `201` with the category; `409` slug already used; `422` invalid name.
  - `PATCH /{id} {name}` → `200`; `404`; `409`; `422`.
  - `PUT /order {categoryIds}` → `200` the reordered list; `422` invalid order.
  - `POST /{id}/deactivate`, `POST /{id}/activate` → `200`; `404`.
  - `DELETE /{id}` → `204`; `404`; `409` has products (deactivate it instead).
- Back office (T-E1-12): `/admin/categorias` lists every category by position with its state. A form above the list creates one. Each row renames in place, moves up or down (sending the whole new order), deactivates or reactivates, and deletes after a confirmation; a `409` on delete says the category has products and offers to deactivate it instead. A `409` on a name shows the slug already used.

---

## UC-06 Manage products and variants

**Actor:** Administrator · **Module:** `catalog` (+ `inventory.api`) · **Stage:** E1

**Preconditions:** the administrator is signed in.

**Main flow**
1. The administrator creates a product with name, description, material, season, audience and category.
2. Defines the sizes and colors; the system generates the variant matrix.
3. Sets the price of each variant.
4. Uploads photos; the system converts them to WebP and generates thumbnails and a blurred placeholder.
5. Publishes the product.

**Alternative flows**
- Upload interrupted by a bad connection: the administrator can retry without duplicating the photo.
- A wrong photo: the administrator deletes it.

**Business rules**
- No two variants of a product share the same size and color.
- Sizes and colors are free text per product. They are shown in the order the administrator entered them when generating the matrix (`size_position`, `color_position` on the variant).
- The SKU is unique across the shop. The system generates it when it creates the variant: `PP-` and 8 random characters from an alphabet without look-alikes (for example `PP-7K3M9QX2`). It cannot be edited. A clash is retried with new codes.
- The slug follows the same rules as a category's (UC-05): generated from the name (at most 140 characters), unique, regenerated on rename; a name whose slug another product uses is rejected.
- Name: trimmed, 1 to 120 characters, without control or invisible characters. Description: trimmed, up to 5000 characters, line breaks allowed, may be empty. Material: trimmed, 1 to 80 characters. Size: 1 to 20 characters; color: 1 to 30. Season and audience come from their enums. The category must exist (active or not).
- A new product is hidden and has no variants.
- Generating the matrix is additive: it creates only the size × color combinations the product does not have yet and never deletes a variant. The sizes and colors of the request come first in the display order, in the order given, followed by the product's other sizes and colors in their previous order. Repeating a generation creates nothing.
- A generation takes 1 to 20 sizes and 1 to 20 colors without repeated values (exact text after trimming), and a product has at most 20 sizes and 20 colors in total (400 variants).
- A new variant is active and has no price. The price is set in cents, from 1 to 1,000,000,000 (ARS 10,000,000), and can be changed but not removed.
- A variant can be deactivated and reactivated. Deactivating the last active priced variant of a published product is rejected: hide the product first.
- A product without at least one active priced variant cannot be published. Its category may be inactive (the public listing leaves those out). Publishing a published product or hiding a hidden one changes nothing.
- Products are not deleted: hiding takes them out of the shop.
- Photos: JPG, PNG or WebP up to 10 MB, at most 8 per product (proposed). The device shrinks each photo (max 1600 px per side) before upload.
- The type comes from the content (magic bytes), never from the `Content-Type` or the file name. Before decoding, each side must be at most 10,000 px and the photo at most 50 megapixels.
- Each photo is stored as WebP (quality 80) at 400, 600, 800 and 1200 px wide, never enlarged, plus a 10 px wide WebP placeholder kept as a `data:` URI. The EXIF orientation is applied, every metadata block is removed and colors are converted to sRGB. An animated image keeps only its first frame. The 600 px file (T-E6-27, RNF-11) is the one a phone picks for the product page's main photo (about 350 CSS px at 1.75x); without it the phone loaded the 800 px file. Photos uploaded before T-E6-27 have no 600 px file and are not converted again: the real catalog is loaded afterwards (T-E6-11), and a test photo is uploaded again.
- File names are generated by the server (a random storage key, `<key>-<width>.webp`), never taken from the upload.
- A photo may belong to one of the product's colors, so it is shown for every size of that color; a photo without a color belongs to the whole product. A color the product does not have is rejected. The color of a photo is chosen at upload; to change it, delete it and upload it again.
- A new photo goes last. Reordering takes the complete list of the product's photo ids in the new order (as for categories, UC-05). Deleting a photo closes the gap in the order.
- An upload carries an `Idempotency-Key` (UUID), scoped to the product. The same key with the same file (SHA-256) returns the stored photo without storing it again; the same key with another file is rejected. A key whose photo was deleted can upload again.
- Initial stock is recorded through UC-07 (reason `INITIAL_LOAD`), so every stock change leaves a movement.

**Acceptance criteria**
- Given 3 sizes and 2 colors, when the matrix is generated, then 6 variants are created.
- Given a 4 MB JPG, when uploaded, then lighter WebP versions and a placeholder are stored.
- Given a hidden product, when published, then it appears in the catalog.
- Given an 8 MB photo chosen on a phone, when uploaded, then it is shrunk on the device first and processed by the server like any other.
- Given an upload cut by a lost connection, when the signal returns, then it can be retried without duplicating the photo.
- Given a file named `.jpg` whose content is not JPG, PNG or WebP, when uploaded, then it is rejected.
- Given a product with 8 photos, when another is uploaded, then it is rejected.
- Given a photo, when deleted, then it leaves the product and its files are removed.

**Design notes**
- Use cases: `CreateProduct`, `UpdateProduct`, `GenerateVariantMatrix`, `UpdateVariant`, `PublishProduct`, `HideProduct`, `ListAllProducts`, `GetProductForAdmin` (T-E1-04); `UploadProductImage`, `ReorderProductImages`, `DeleteProductImage` (T-E1-05). Each command carries the `Actor`; any actor other than `ADMIN` gets `Forbidden`.
- `Product` aggregate owns its `Variant` and `ProductImage` entities and enforces uniqueness of size + color, the size and color limits, the publish rule, the last-priced-variant rule, and the photo limit, colors and order. `VariantMatrix` is a domain service that builds the missing variants from sizes × colors and the new display order.
- Outbound ports: `LoadProducts` (read, with a variant that locks the product row for writes), `SaveProduct` (write; returns `SLUG_ALREADY_USED`, `SKU_ALREADY_USED` or `UNKNOWN_CATEGORY` on the database constraints, keeping the caller's transaction usable), `ProductSummaries` (read side of the back-office list), `SkuGenerator`, `OpenStockLevels`; `ImageProcessor` (WebP conversion, thumbnails, placeholder; at most 2 conversions at a time, T-E1-14) and `ImageStore` (volume served by Caddy under `/uploads`) in T-E1-05.
- Images (T-E1-05):
  - `ImageProcessor` is implemented with the libvips command line (`vipsheader`, `vips thumbnail`), installed in the API image and run as a separate process with a timeout and `VIPS_BLOCK_UNTRUSTED`. A crash or a decompression bomb stays outside the JVM and its 768 MB heap. Without libvips on the developer's machine, the adapter's contract test is skipped; CI always runs it.
  - The domain detects the format from the first bytes (`ImageFormat`), before anything reaches libvips.
  - At most 2 conversions run at a time (T-E1-14, RNF-21): a decorator of `ImageProcessor` around the libvips adapter holds a semaphore of 2 permits (`store.images.conversions`). Another upload waits up to 10 s for a permit, then answers `503` with `Retry-After` (RNF-21) without converting anything; the back office keeps its `Idempotency-Key`, so "Retry" never duplicates the photo. The permit is released however the conversion ends.
  - The conversion runs outside the database transaction, so it never holds a pooled connection. The upload first checks the product, the key, the photo limit and the color (read only), then converts and writes the files, and then, in one transaction, locks the product, checks again and inserts the row. When the row is not inserted, the files are removed.
  - Deleting removes the row, and after the commit the files. A file that cannot be removed is logged (storage key only) and left behind.
  - `product_image` (V7): `variant_id` is replaced by `color` (text, optional), plus `upload_key` (UUID) and `content_sha256` (bytea), unique on `(product_id, upload_key)`.
- Write use cases lock the product row (`SELECT ... FOR UPDATE`), so two requests on the same product run one after the other.
- Stock levels: `OpenStockLevels` is implemented by an adapter that calls `inventory.api.StockLevels.open(variantIds)`, which creates a `StockLevel` with 0 for each new variant in the same transaction (idempotent: `INSERT ... ON CONFLICT DO NOTHING`). A synchronous call and not an event: `catalog` already depends on `inventory.api` (UC-03), so `inventory` listening to a `catalog.api` event would be a module cycle.
- Event (in `catalog.api`): `ProductChanged(productId)`, for the search index (UC-26). It is published from T-E1-18, together with its listener.
- Upload endpoint accepts an `Idempotency-Key` so retries do not duplicate photos.
- Endpoints under `/api/admin/catalog` (errors as `problem+json`):
  - `GET /products?page=` → `200` `{items, hasMore}`, 50 per page, every product published or not, newest first.
  - `POST /products {categoryId, name, description, material, season, audience}` → `201` with the product; `409` slug already used (`slug` property); `422` invalid fields (`fields` property) or unknown category.
  - `GET /products/{id}` → `200` the product with sizes, colors and variants; `404`.
  - `PUT /products/{id}` (same body) → `200`; `404`; `409`; `422`.
  - `POST /products/{id}/variants/generate {sizes, colors}` → `200` the product; `404`; `422` invalid sizes or colors, or too many.
  - `POST /products/{id}/publish` → `200`; `404`; `409` without an active priced variant. `POST /products/{id}/hide` → `200`; `404`.
  - `PATCH /variants/{id} {priceCents?, active?}` → `200` the product; `404`; `409` last active priced variant of a published product; `422` invalid price.
  - `GET /products/{id}` also returns `images`, by position: `id`, `color` (or null), `placeholder` and `files` (`width`, `url` under `/uploads/`).
  - `POST /products/{id}/images` (multipart: part `file`, optional field `color`; header `Idempotency-Key`) → `201` with the photo; `200` with the stored photo when the key was already used with the same file; `400` missing or invalid key or file; `404`; `409` already 8 photos; `413` over 10 MB; `422` with `problem`: `UNSUPPORTED_FORMAT`, `TOO_LARGE` (dimensions), `UNREADABLE`, `UNKNOWN_COLOR` or `KEY_REUSED` (same key, another file); `503` with `Retry-After` when 2 conversions have kept running for 10 s.
  - `PUT /products/{id}/images/order {imageIds}` → `200` the product; `404`; `422` invalid order.
  - `DELETE /products/{id}/images/{imageId}` → `204`; `404` unknown product or photo.
- Back office (T-E1-12):
  - `/admin/productos` lists the products newest first with category, state (published or hidden) and number of variants, with "See more" for the next 50. `/admin/productos/nuevo` creates one and opens it.
  - `/admin/productos/{id}` has four sections. Details: the same form as creation; the fields of a `422` are marked. State: publish or hide; a `409` on publish says a priced active variant is needed. Variants: sizes and colors are typed separated by commas to generate the matrix; the variants show by color and size in display order, each with its SKU, a price in pesos (up to 2 decimals, saved per variant), an active switch and a link to its stock. Photos: by position, each with its color (or "whole product"), move up or down, and delete after a confirmation.
  - Upload in this task: one file at a time with an optional color. Its `Idempotency-Key` is created when the file is chosen and sent again by "Retry" until it uploads or another file is chosen, so a retry never duplicates the photo. Selecting several files, shrinking on the device, progress and drag to reorder come in T-E1-22.
  - Upload from the phone (T-E1-22, RNF-26): the file field takes several photos at once (`multiple`, JPG, PNG or WebP, so the phone offers camera or gallery and iPhone turns HEIC into JPEG). The color chosen before picking applies to all of them. Each photo becomes a row of an upload list with its name, a progress bar and its state (shrinking, waiting, uploading, uploaded, failed); they upload one after the other.
  - Shrinking on the device: a photo larger than 1600 px on its longest side is drawn on a canvas at most 1600 px wide or tall, with its EXIF orientation applied, and saved as JPEG at quality 0.85 (PNG stays PNG). A smaller one, or one the browser cannot decode, is sent as it is and the server decides. The result is kept with its `Idempotency-Key`, so "Retry" sends the very same bytes and never duplicates the photo nor gets `KEY_REUSED`.
  - A lost connection or a `503` (busy converting) leaves the row failed with "Retry"; a rejection (`409` full, `413`, `422`) shows its reason and can only be removed from the list. Progress comes from `XMLHttpRequest` upload events, which `fetch` does not offer.
  - Reordering: drag and drop on screens with a mouse, and the up and down buttons everywhere. Dropping sends the whole new order, as the buttons do.

---

## UC-07 Manage stock

**Actor:** Administrator · **Module:** `inventory` (+ `accounts.api`) · **Stage:** E1

**Preconditions:** the administrator is signed in; the variant exists (a `StockLevel` was created when it was).

**Main flow**
1. The administrator picks a variant and records an adjustment (add or subtract) with a reason.
2. The system updates the stock and appends a movement to the history.

**Alternative flows**
- Adjustment that would make stock negative: rejected with the current stock.
- Adjustment retried after a lost response: the stock changes only once.

**Business rules**
- Stock never goes negative, and is at most 1,000,000 units.
- Every stock change creates a movement (append-only).
- A movement has a fixed type (`INITIAL_LOAD`, `ADJUSTMENT`, and from E3 `RESERVATION`, `DEDUCTION`, `RELEASE`), a signed quantity and an optional free-text note from the administrator. The history shows the note as the reason. The note is free text and may contain personal data, so it never reaches logs or events.
- The administrator does not choose the type: the first movement of a variant is `INITIAL_LOAD`, every later one `ADJUSTMENT`. Stock starts at 0, so the first movement always adds.
- Quantity: a whole number from −10,000 to 10,000, not 0. Note: trimmed, at most 200 characters, one line, without control or invisible characters; empty means no note.
- Any variant can be adjusted, active or not, of a published product or not: the inventory does not know those states.
- An adjustment carries an `Idempotency-Key` (UUID), scoped to the variant. The same key with the same quantity and note changes nothing and returns the current stock; the same key with another quantity or note is rejected.
- The history of a variant is shown newest first, 50 movements per page, together with its current stock. Each movement shows date, user (name), quantity and reason. The name comes from `accounts`; a user the administrator cannot see (Row Level Security shows it only its own user) has no name.
- On mobile, the screen offers − and + buttons as well as a numeric field.

**Acceptance criteria**
- Given stock 3, when subtracting 5, then it is rejected.
- Given a valid adjustment, when saved, then the history shows date, user, amount and reason.
- Given an adjustment whose response was lost, when retried with the same key, then the stock changes only once.
- Given a variant without movements, when stock is added, then the movement is the initial load.

**Design notes**
- Use cases: `AdjustStock` and `ListStockMovements`. Each command carries the `Actor`; any actor other than `ADMIN` gets `Forbidden`.
  - `AdjustStock`: sealed result `Adjusted(newOnHand)`, `AlreadyAdjusted(onHand)` (a retry), `WouldGoNegative(currentOnHand)`, `WouldGoBelowReserved(currentOnHand, reserved)` (from E3), `WouldExceedMaximum(currentOnHand)`, `InvalidQuantity`, `InvalidNote`, `KeyReused`, `UnknownVariant`, `Forbidden`.
  - `ListStockMovements`: `Listed(onHand, movements, hasMore)`, `UnknownVariant`, `Forbidden`.
- `StockLevel` aggregate: `adjust(...)` enforces the non-negative invariant and the maximum, and picks the movement type. `StockMovement` is an immutable record. `MovementNote` never prints its text.
- Outbound ports: `LoadStockLevels` (locks the level of a variant with `SELECT ... FOR UPDATE`, and finds the movement of an adjustment key), `SaveStockLevels` (write), `StockHistory` (read side: current stock and a page of movements), `UserNameLookup` (names of users, backed by `accounts.api.UserNames`). The existing `AddStockLevels` stays as it is.
- `inventory` depends on `accounts.api` (no cycle: `accounts` depends on nothing). `accounts.api.UserNames` returns the names of the users that the caller's security context shows, joining the caller's transaction.
- Public API for other modules (`inventory.api`): `StockAvailability` (read, T-E1-08) and, from E3, `StockReservations` (reserve, commit, release). Keep the two interfaces separate (Interface Segregation).
- Concurrency: an adjustment locks the stock level row, then checks the key, then writes: a conditional `UPDATE` (`WHERE on_hand + :delta >= 0`) and the insert of the movement, in the same transaction. Two requests with the same key run one after the other, and the second finds the movement of the first.
- `stock_movement` (V8): `adjustment_key` (UUID), present exactly on `INITIAL_LOAD` and `ADJUSTMENT`, unique on `(variant_id, adjustment_key)` as the last line of defense (RNF-20.4). `stock_level.on_hand` at most 1,000,000.
- Endpoints (errors as `problem+json`):
  - `POST /api/admin/stock-adjustments {variantId, quantity, note?}` (header `Idempotency-Key`) → `201` `{variantId, onHand}`; `200` the same with the current stock when the key was already used with the same adjustment; `400` missing or invalid key or body; `404` unknown variant; `409` with `problem` `WOULD_GO_NEGATIVE` or `WOULD_EXCEED_MAXIMUM` and the current stock in `onHand`, or, from E3, `WOULD_GO_BELOW_RESERVED` with `onHand` and the units reserved by orders awaiting payment in `reserved` (UC-16, T-E3-04); `422` with `problem` `INVALID_QUANTITY`, `INVALID_NOTE` or `KEY_REUSED`.
  - `GET /api/admin/variants/{id}/stock-movements?page=` → `200` `{variantId, onHand, items, hasMore}`, each item with `id`, `type`, `quantity`, `note` (or null), `userId` and `userName` (or null) and `createdAt`; `404` unknown variant.
- Back office (T-E1-12): each variant of the product page links to `/admin/productos/{id}/variantes/{variantId}/stock`. The page shows the product, size, color and SKU, the current stock, the adjustment form (a quantity with − and + buttons around the numeric field, and an optional note) and the history, 50 at a time with "See more". The `Idempotency-Key` belongs to one quantity and note: a retry after a lost answer sends it again, and changing the quantity or the note creates a new one. A `409` shows the current stock. The reason column shows the note, or the movement type when there is none; a user without a name shows a dash.

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
- Schema (T-E1-17): the trusted extension `unaccent` (created by the schema owner, no superuser), `product.search_vector TSVECTOR NOT NULL DEFAULT ''` (a product not indexed yet matches nothing) and the GIN index `product_search_vector_idx`. The weighting lives once in the SQL function `product_search_vector(product_id)`: the migration fills existing products with it and the listener of T-E1-18 runs `update product set search_vector = product_search_vector(id)`. Only active variants contribute colors, so an inactive variant's color does not match.
- Query: `websearch_to_tsquery('spanish', unaccent(:q))`, ranked with `ts_rank`; suggestions use prefix matching on the last word.
- Out of v1: typo tolerance (`pg_trgm`).
- Endpoints: `GET /api/catalog/products?q=` and `GET /api/catalog/suggestions?q=`.
- Search details (T-E1-18):
  - `SearchText` (value object in `catalog.domain`): the text trimmed, with control and invisible characters turned into spaces, cut to its first 100 characters. Under 2 characters it is not a search.
  - `SearchProducts` takes the text plus the filters and page of `ListProducts` (UC-02); sealed result `Listed(items, hasMore)`, `UnknownCategory`, `InvalidFilter` or `TextTooShort`. A product appears when it is listed (UC-01) and matches the text with the web search syntax (every word, `"quoted phrases"`, `or`, `-word`). Without a sort, results go by relevance, ties newest first; an explicit sort (`NEWEST`, `PRICE_ASC`, `PRICE_DESC`) replaces relevance.
  - `SuggestProducts`: up to 5 listed products (`slug`, `name`, first photo or none) by relevance, then newest. Every word but the last must match, the last one as a prefix. The words are the runs of letters and digits of the text, so nothing the visitor types reaches `to_tsquery` as syntax. A text under 2 characters, or without words, suggests nothing and runs no query.
  - The listing and the search share one constant SQL text block, where a null `:q` switches the text off (RNF-28).
  - `GET /api/catalog/products?q=` takes the filters, `sort` and `page` of UC-02 and answers like the listing; no `sort` means relevance; `400` for a text under 2 characters. `GET /api/catalog/suggestions?q=` → `200` `{items}`, each with `slug`, `name` and `image` (as in the listing, or null); a text under 2 characters gets an empty list.
  - A `q` longer than 4 × 100 characters answers `400` before reaching the use case (it only bounds the request, as for slugs).
  - Index maintenance: `CreateProduct`, `UpdateProduct`, `GenerateVariantMatrix` and `UpdateVariant` publish `ProductChanged(productId)`, and `RenameCategory` publishes `CategoryRenamed(categoryId)` because the category name is searchable; both events are in `catalog.api` and go out through the port `CatalogEvents`. On either event, `RefreshSearchVector` recomputes the vector of the product, or of every product of the category, through the port `SearchVectors`. The listener runs synchronously in the publishing transaction, so the index commits or rolls back with the change (the event publication registry is not involved).
- Storefront (T-E1-19):
  - Every public page has the store header: the store name (linking to `/`) and the search. From 960 px the search is a field in the header; below, a search icon opens a full-screen search (a modal `dialog`) with the field focused and a close button.
  - Suggestions: from 2 characters, 250 ms after the last keystroke, up to 5 with photo and name, in a listbox under the field (ARIA combobox: arrows move, Enter opens the highlighted one, Escape closes the list). A suggestion opens `/producto/{slug}`. A failed or slow suggestion request shows nothing; the field keeps working.
  - Enter without a highlighted suggestion opens `/buscar?q={text}` with no filters and the default sort; with fewer than 2 characters it does nothing. The full-screen search closes on either navigation.
  - `/buscar` shows "Results for \"{text}\"", the filters panel with the options of the whole shop, the sort (Relevance, the default and left out of the URL, then Newest as `orden=novedades`, Lowest and Highest price) and the grid with "Show more" as in UC-01. The text lives in the URL key `q`, cut to 100 characters, next to the filter keys of UC-02. The header field shows the text of the page.
  - No results without filters: a message with the text and links to the active categories, plus one to the whole catalog; with filters, the no-matches message and "Clear filters" of UC-02. `/buscar` without a text, or with fewer than 2 characters, asks for at least 2 characters and runs no search.
  - Public endpoint for those links: `GET /api/catalog/categories` → `200` `{items}`, the active categories by position, each with `slug` and `name` (inbound port `ListShopCategories`, no actor; rate-limit policy catalog).
  - A `q` in the URL of `/` or of a category page is ignored: only `/buscar` searches.
- Rate limits (T-E1-15, RNF-22): a search is a `GET` of either endpoint that carries `q`. The `search` policy allows 60 per minute per IP and the request also counts for the `catalog` policy (120 per minute per IP, every `GET /api/catalog/**`), so browsing without text is limited only by the latter. Buckets refill continuously (Bucket4j greedy refill): a burst of 60, then one search per second.

---

## Analytics and consent (RNF-25)

**Module:** frontend only (ARCHITECTURE.md section 8): the backend never receives or forwards analytics data. **Tasks:** T-E1-23 (consent and loader), T-E1-24 (catalog events).

**Consent and loader (T-E1-23)**
- The tools are Google Analytics 4 and Meta Pixel. Their ids come from the build variables `VITE_GA4_MEASUREMENT_ID` and `VITE_META_PIXEL_ID` (Docker build arguments taken from `.env`), never from the code. A tool without an id is never loaded; without either, no banner is shown.
- Every public page (not the back office) shows a banner at the bottom until the visitor chooses: a short text, "Aceptar" and "Rechazar", with the same weight. Nothing of either tool loads before "Aceptar".
- The choice is kept in the browser (`localStorage`, key `pp-consent`, with the date it was made) for 12 months; after that, or with an unreadable value, the banner asks again.
- The footer of every public page has a "Cookies" link that opens the banner again, with the current choice stated, to change it.
- Accepting loads `gtag.js` and `fbevents.js` asynchronously, after the page. Rejecting loads nothing. Changing from accepted to rejected deletes the cookies of both tools (`_ga`, `_ga_*`, `_fbp`) and reloads the page, so the loaded scripts go away.
- Page views are sent by the app on every route change (GA4 `page_view` with its automatic one turned off, Pixel `PageView`), with the address without query string or fragment: queries can hold search texts and, from E3, tracking tokens. No event carries a national ID, email, name or address (R-05).
- Caddy's content security policy allows only the domains these tools need (`www.googletagmanager.com`, `*.google-analytics.com`, `*.analytics.google.com`, `connect.facebook.net`, `www.facebook.com`). Verification with Pixel Helper and GA4 DebugView is T-E6-16.

**Catalog events (T-E1-24)**
- Sent only once the tools are loaded (after "Aceptar"); before that, or for a tool without an id, nothing is sent.
- `view_item_list` (GA4 only): each page of products shown, the first and every "Show more", with `item_list_id` and `item_list_name` (`all_products`, `category_{slug}` or `search_results`) and its `items`, each with `item_id`, `item_name`, `price` and `index` (its place in the whole list, from 0).
- `view_item` (GA4) and `ViewContent` (Pixel): once per product page opened, with `currency: "ARS"`, `value` (the "from" price) and the product as the single item (Pixel: `content_ids`, `content_type: "product"`, `value`, `currency`).
- `search` (GA4, `search_term`) and `Search` (Pixel, `search_string`): once per text searched, when its results page opens. A text with `@` or a run of 7 or more digits is not sent, because it may be an email or a national ID (R-05).
- Amounts go in pesos (`cents / 100`) with `currency: "ARS"`.
- Item id: RNF-25 asks for the SKU, but a listing or a product page has no chosen variant yet, so these events identify the product by its slug. The SKU is the item id from `add_to_cart` (E2) on, where a variant is chosen.
