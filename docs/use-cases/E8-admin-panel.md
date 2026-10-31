# E8 – The complete admin panel

**Status:** Draft (E8a scope approved on 2026-10-20) · **Use cases (E8a):** UC-33, UC-34, UC-36 and UC-37, and changes to UC-06, UC-07 and UC-18 · **Spanish counterpart:** `docs/specs/E8-panel-admin` (spec, plan and tasks)

**Modules involved:** `backoffice` (dashboard and customer profile composition), two new modules, `activity` (UC-37) and `settings` (UC-36), plus read additions in `ordering`, `catalog`, `accounts`, `reviews` and `notifications`, and one activity entry per admin write in every module that has one.

## Goal

Run the whole shop from `/admin`. Signing in opens a summary of the business (UC-33) that links to every section, the owner can find any order, product, variant or customer in seconds, the store's contact data is edited without a deploy, and every change made from the back office is recorded.

E8 has three phases (see the Spanish spec). This file covers **E8a**. E8b (owner alerts, payments, size or color exchanges, reports) and E8c (refunds, "before" price, featured products, review replies) get their use cases when each phase starts.

**Out of scope for E8:** back-office users, roles and permissions. The back office keeps a single administrator, the owner, with the second factor of UC-04 (D-E8-01, dropped on 2026-10-20). UC-38 is not used. The rest of the out-of-scope list is in the Spanish spec.

## Decisions

| ID | Topic | Status | Decision |
|---|---|---|---|
| D-E8-01 | Back-office users and roles | Dropped (2026-10-20) | Single administrator (UC-04). No UC-38. |
| D-E8-03 | Low-stock threshold | Decided (2026-10-20) | One for the whole shop, 3 units by default, editable in "Datos de la tienda" (UC-36). |
| D-E8-04 | Refunds | Decided (2026-10-20) | Full refunds through MercadoPago from the order (UC-42, E8c). UC-18 keeps sending refunds to the provider's panel until then. |
| D-E8-10 | Packing slip | Decided (2026-10-20) | A printable page from the order detail, without prices. |
| D-E8-11 | Activity retention | Decided (2026-10-20) | 2 years. |

## Shared rules (E8a)

- **Sales:** an order counts as a sale when its status is `PAID`, `PREPARING`, `SHIPPED` or `DELIVERED`. `AWAITING_PAYMENT`, `EXPIRED` and `CANCELLED` never count, so an order cancelled after payment leaves the figures. The amount is `total_cents` (products after the discount, plus shipping).
- **Dates** are Argentina dates (R-07): a day runs from 00:00 to 24:00 `America/Argentina/Buenos_Aires`, and an order belongs to the day of its `created_at`. The reservation lasts 30 minutes (UC-16), so the purchase and the payment are almost always on the same day. "Now" always comes from the injected `Clock`.
- **Personal data** (R-05, RNF-27): names, emails, phones, national IDs and addresses show only in the back office. They never reach logs, events, analytics or the activity log. Search text is personal data too: it is never logged.
- **Authorization:** every endpoint of this file is under `/api/admin`. It needs the `ADMIN` actor: a customer gets `403` and an anonymous caller `401`. The use cases check the actor and RLS enforces it again.
- **Lists** have 50 rows per page from page 0, with `hasMore`, as the existing admin lists. A page out of 0 to 10,000 is `400`.

---

## UC-33 Business summary (dashboard)

**Actor:** Administrator · **Module:** `backoffice` (reads `ordering`, `catalog`, `inventory`, `accounts`, `reviews`, `notifications`, `settings`) · **Stage:** E8a

**Preconditions:** the administrator is signed in (UC-04).

**Main flow**
1. The administrator signs in, or opens `/admin`.
2. The system shows the summary: "Para hacer hoy", sales, the sales chart, low stock, best sellers, recent orders and the totals.
3. Each count and row links to its screen, already filtered.

**Alternative flows**
- No sales in a period: its figures show `$0` and `0 pedidos`, and the variation reads "—".
- A block cannot be loaded: that block shows an error with "Reintentar" and the others still show.
- No variant at or under the threshold: the low-stock block reads "Sin productos con stock bajo".

**Business rules**
- **Para hacer hoy:**
  - `PAID` orders, waiting to be prepared;
  - `PREPARING` orders;
  - withdrawal requests `RECEIVED` (UC-29);
  - `PENDING` reviews (UC-25);
  - open orders with an email alert: orders in `PAID`, `PREPARING` or `SHIPPED` whose buyer email is `BOUNCED` or `FAILED` (UC-17, UC-18);
  - `SHIPPED` orders without a tracking number: the buyer's tracking page shows it only once there is one (UC-18);
  - sold-out variants: active variants of published products with no available stock (UC-07), linking to the Stock screen filtered by sold out.
  Each count links to its list with that filter. A count of 0 still shows, without a link.
- **Sales:** amount and number of orders for today, the last 7 days (today and the 6 days before) and this month (from the 1st to today). Each shows its variation in percent against the period before: yesterday, the 7 days before those, and the same days of the previous month (the 1st up to today's day number, or that month's last day if it is shorter). With nothing in the earlier period, the variation is "—". Variations are rounded to whole percents.
- **Average ticket of the month:** the month's amount divided by its number of orders, rounded down to the cent; "—" without orders.
- **Chart:** sales per day for the last 30 days (today included), or per month for the last 12 months (this month included). Days or months without sales show as 0, not as gaps. The view switches between days and months.
- **Low stock:** active variants of published products whose available stock (UC-07: on hand minus reserved) is at or under the threshold of UC-36, sold-out ones first, then by available stock and product name. At most 10, with the number of all of them and a link to the Stock screen filtered by low stock. Each row shows the product, size, color and available units.
- **Best sellers:** the 5 products with the most units sold in the last 30 days, among orders that count as sales, grouped by product (every size and color together). Each row shows the units and the amount. Ties are broken by amount, then by name.
- **Recent orders:** the 5 newest orders of any status, with number, date, buyer name, status and total.
- **Totals:** published products; registered customers, with how many registered this month.
- The figures are read when the page opens. "Actualizar" reads them again, and nothing refreshes by itself.

**Acceptance criteria**
- Given an administrator who signs in, when the back office opens, then it shows the summary, not the product list; `/admin` shows it too.
- Given one paid and one cancelled order today, when the summary opens, then today's sales count only the paid one.
- Given an order created at 23:30 Argentina time, when the summary opens the next day, then it is in yesterday's sales, not today's.
- Given an `AWAITING_PAYMENT` order and an `EXPIRED` one, when the summary opens, then neither counts as a sale.
- Given $100,000 yesterday and $150,000 today, when the summary opens, then today shows +50%.
- Given nothing sold yesterday, when the summary opens, then today's variation reads "—".
- Given a day without sales within the last 30, when the chart shows days, then that day is a 0 bar.
- Given a threshold of 3, and published variants with 2, 3 and 4 available units, when the summary opens, then the first two show in "Stock bajo" and the third does not.
- Given a variant with 5 on hand and 3 reserved, and a threshold of 3, when the summary opens, then it shows with 2 available.
- Given a variant of a hidden product with 0 units, when the summary opens, then it is not in "Stock bajo".
- Given 12 variants at or under the threshold, when the summary opens, then 10 show and the link reads 12.
- Given a product sold in two sizes, 3 and 2 units, when the summary opens, then the best sellers show it once with 5 units.
- Given 2 `PAID` orders, when "Para hacer hoy" is opened from its count, then the order list shows the `PAID` filter.
- Given a `SHIPPED` order with a bounced email, when the summary opens, then it counts in the email alerts; once `DELIVERED`, it does not.
- Given two `SHIPPED` orders, one with a tracking number, when the summary opens, then "Para hacer hoy" shows 1 shipped order without tracking; once it gets its number, 0.
- Given a published product with one variant at 0 available units and one with 2, and a hidden product with a variant at 0, when the summary opens, then "Para hacer hoy" shows 1 sold-out variant.
- Given a customer session, when it calls `GET /api/admin/dashboard`, then it gets `403`.

**Design notes**
- **Inbound port:** `backoffice.application.GetDashboard` (`Query(actor, chart: DAYS | MONTHS)`; result `Shown(Dashboard)` or `Forbidden`). It is one read-only transaction as `ADMIN`. `Dashboard` is a record of the blocks above. The periods are computed by `backoffice.domain.SalesPeriods` from the `Clock`'s date in Argentina (pure, unit tested: month ends, a 31st against a 30-day month, a leap year). The variation and the average ticket are computed by `backoffice.domain.Variation` and `Money`.
- **Outbound ports of `backoffice`, each backed by an `api` of its owner:**
  - `SalesFigures`, backed by a new `ordering.api.SalesFigures`: `totals(from, to)` → amount and count; `perDay(from, to)` and `perMonth(from, to)` → series; `unitsByVariant(from, to)` → variant id, units and amount. These are SQL aggregates with `status IN (...)` from an enum-built constant list, and Argentina dates through `created_at AT TIME ZONE 'America/Argentina/Buenos_Aires'`. Days without rows are filled in the domain, not in SQL.
  - `OrderWorkload`, backed by a new `ordering.api.OrderWorkload` (T-E8-05 built its first part as `backoffice.application.OrderCounts.paidWaiting()`, the menu's badge; UC-33 grows that port rather than adding a second one over the same data): counts by status, `SHIPPED` orders without a tracking number, `RECEIVED` withdrawals, the 5 newest orders, and the open orders among a set of ids.
  - `EmailAlerts`, backed by a new method on `notifications.api.BuyerEmailStatus`: the ids of orders whose buyer email is `BOUNCED` or `FAILED`.
  - `LowStock`, backed by a new `catalog.api.LowStockVariants`: `list(threshold, limit)` and `count(threshold)` (`count(0)` gives the sold-out variants of "Para hacer hoy"), over the catalog tables joined to `inventory.v_variant_availability` (ARCHITECTURE.md rule 4). Shared with the Stock screen (changes to UC-07).
  - `ProductNames`, backed by `catalog.api.VariantDescriptions` and `ProductNames`: groups the best sellers' variants by product.
  - `CustomerCounts`, backed by a new `accounts.api.CustomerCounts`: total, and registered since a date (needs the RLS change of UC-34).
  - `PendingReviews`, backed by a new `reviews.api.PendingReviews.count()`.
  - `LowStockThreshold`, backed by `settings.api.StoreSettings` (UC-36).
- **Module graph:** `backoffice` adds `accounts`, `reviews` and `settings` to its dependencies. Nobody depends on `backoffice`, so no cycle.
- **Endpoint:** `GET /api/admin/dashboard?chart=DAYS|MONTHS` → `200` with the blocks (amounts in cents); `400` for another `chart`. OpenAPI is updated.
- **Indexes:** `orders (created_at)` for the period sums (the existing index leads with `status`). At under 20 orders a day, 12 months are under 10,000 rows, so no materialized view.
- **Page:** `DashboardPage` at `/admin` (the index route, replacing the redirect to `/admin/productos`). After signing in, the administrator lands on `/admin` unless they were sent to sign in from another admin page. The chart is a hand-written SVG bar chart (no chart library in the admin chunk), with a visually hidden table of the same values for screen readers. Blocks are cards: one column at 360 px, two from 860 px (RNF-26). Amounts are formatted with the shared ARS formatter.

---

## UC-34 Customers

**Actor:** Administrator · **Module:** `accounts` (list) and `backoffice` (profile, with `ordering`) · **Stage:** E8a

**Preconditions:** the administrator is signed in (UC-04).

**Main flow**
1. The administrator opens "Clientes".
2. The system lists registered customers, newest first.
3. The administrator searches by name or email, or filters by registration date.
4. Opens a customer and sees the profile.

**Alternative flows**
- No customer matches: "No encontramos clientes con esa búsqueda".
- The customer was deleted meanwhile (UC-28): the profile answers `404` and the page says the account no longer exists.

**Business rules**
- Only users with role `CUSTOMER`; the administrator does not list itself.
- **List row:** name, email, registration date, number of orders.
- **Search** (`q`): 2 to 100 characters, trimmed. It matches customers whose name or email contains it, ignoring case and accents. `%`, `_` and `\` are literal characters, never wildcards. A shorter `q` is `400`.
- **Registration filter:** `from` and `to`, optional Argentina dates, both inclusive; `from` after `to` is `400`.
- **Profile:** name, email (and whether it is verified), phone, registration date, linked sign-in providers (Google, Facebook), saved addresses (main one first), and the customer's orders, newest first, 20 per page, each with number, date, status and total linked to the order detail. Also the number of purchases, the total spent (sum of the orders that count as sales) and the date of the last purchase.
- Orders a guest made with the same email are not added: only the orders linked to the account count (UC-21, UC-28).
- Read-only. The customer edits or deletes their own account (UC-19, UC-28).
- Opening a profile adds an activity entry `CUSTOMER_VIEWED` with the customer's id, never their name or email (UC-37).

**Acceptance criteria**
- Given 3 customers and the administrator, when "Clientes" opens, then the 3 customers show, newest first, and the administrator does not.
- Given customers "Ana Gómez" and "Juan Pérez", when searching "gómez", "GÓMEZ" or "gomez", then only Ana shows.
- Given a customer whose email is `ana_1@x.com` and another `ana11@x.com`, when searching `ana_1`, then only the first shows.
- Given a one-character search, when sent, then the API answers `400`.
- Given a registration filter from 2026-10-01 to 2026-10-15, when applied, then only customers registered on those days, Argentina time, show.
- Given a customer with a `DELIVERED` order of $30,000, a `CANCELLED` one of $10,000 and an `AWAITING_PAYMENT` one of $5,000, when the profile opens, then it shows 3 orders, 1 purchase and $30,000 spent.
- Given a profile opened, when "Actividad" opens, then it shows `CUSTOMER_VIEWED`, and the entry has neither the name nor the email.
- Given a customer session, when it calls the customer list or a profile, then it gets `403`; a customer can still read only their own user (RLS).
- Given a search, when the request is logged, then the log line has no `q` value.

**Design notes**
- **RLS (one migration in `accounts`):** a new policy `app_user_admin_customer_select` lets `ADMIN` read the `CUSTOMER` rows of `app_user`. `app_user_admin_select` keeps the administrator's own row. Nothing else widens: no update, no other role. `address_admin_select` already exists. `external_identity` gets an `ADMIN` select policy, and the profile returns only the provider, never the external subject or its email. The security review (security-reviewer agent) checks the policies before the commit.
- **`accounts`:**
  - `ListCustomers` (`Query(actor, q?, from?, to?, page)`; result `Listed(rows, hasMore)`, `Invalid(field)` or `Forbidden`), read port `CustomerPages`. The SQL is constant: `lower(unaccent(name)) LIKE :pattern ESCAPE '\'` (and the same on the email), with the pattern lowercased, unaccented and escaped in Java (RNF-28). It returns users only: `ordering` depends on `accounts`, so `accounts` cannot ask `ordering` for order counts.
  - `accounts.api.CustomerProfiles` (new): `find(userId)` → name, email, verified, phone, created at, providers, addresses.
- **`backoffice`:** `ListCustomersForAdmin` (wraps the `accounts` list, exposed as `accounts.api.CustomerDirectory`, and adds the order counts from a new `ordering.api.CustomerOrderCounts`, one grouped query for the page's ids) and `GetCustomerProfile` (`Query(actor, userId, ordersPage)`; result `Shown(profile)`, `NotFound` or `Forbidden`). They compose `accounts.api.CustomerProfiles` with a new `ordering.api.CustomerOrders`: page, count, purchases, spent, last purchase, for a user id. `GetCustomerProfile` records `CUSTOMER_VIEWED` through `activity.api.ActivityLog` in the same transaction.
- **Endpoints:** `GET /api/admin/customers?q=&from=&to=&page=` → `200` `{items, hasMore}`; `400`. `GET /api/admin/customers/{id}?ordersPage=` → `200`; `404`. Both are `backoffice.adapter.in.web`. The request logging filter drops `q` from the logged query string.
- **Pages:** `/admin/clientes` (list with the search box and the date filter, both kept in the URL) and `/admin/clientes/:customerId` (profile). The orders link to `/admin/pedidos/:orderId`. Cards at 360 px, columns from 860 px.

---

## UC-36 Store settings

**Actor:** Administrator (edits); Visitor (reads the public part) · **Module:** `settings` (new) · **Stage:** E8a

**Preconditions:** the administrator is signed in (UC-04).

**Main flow**
1. The administrator opens "Datos de la tienda".
2. Edits the contact email, the WhatsApp number, the Instagram, Facebook and TikTok links, the announcement text and the low-stock threshold.
3. Saves. The storefront shows the new data on its next load.

**Alternative flows**
- Invalid data: `422` with the failing fields, and nothing is saved.
- Another save got in first: `409`, and the page reloads the settings and says they changed.

**Business rules**
- There is one row, created by the migration with everything empty and a threshold of 3.
- **Contact email:** optional, at most 254 characters, the format of UC-13's buyer email, stored in lowercase.
- **WhatsApp:** optional, digits only, 10 to 15, in international format as `wa.me` takes it (e.g. `5493424000000`). Spaces, `+` and dashes are removed before checking.
- **Social links:** optional, at most 200 characters. Each must be `https://` and its host must be its network: `instagram.com` or `www.instagram.com`; `facebook.com`, `www.facebook.com` or `m.facebook.com`; `tiktok.com` or `www.tiktok.com`. It must have a path after the host and no user info or port. Anything else is refused, so a link can never send shoppers to another site.
- **Announcement:** optional, 0 to 80 characters, plain text, trimmed, without line breaks or control characters. When it is set, the header strip shows it. When it is empty, the strip shows "Envío gratis desde $X" as today (UC-31), or nothing without a threshold.
- **Low-stock threshold** (D-E8-03): an integer from 0 to 1,000. 0 means only sold-out variants count as low.
- An empty field is saved as "none", and its link is not shown in the store (UC-30, UC-31 as today).
- The seller data of the footer (RNF-30.2) and the free-shipping threshold (UC-12) are not part of this screen.
- Saving adds an activity entry with the fields that changed, before and after (UC-37). These are business data, not personal data.

**Acceptance criteria**
- Given a WhatsApp number `+54 9 342 400-0000`, when saved, then it is stored as `5493424000000` and the contact section links to `https://wa.me/5493424000000`.
- Given an Instagram link `https://evil.example/instagram.com`, `http://instagram.com/x` or `https://instagram.com.evil.example/x`, when saved, then it is refused with `422`.
- Given an announcement "Envío gratis a todo Santa Fe", when the store opens, then the strip shows it instead of the free-shipping amount.
- Given an empty announcement and a $50,000 free-shipping threshold, when the store opens, then the strip reads "Envío gratis desde $50.000".
- Given an empty TikTok link, when the footer shows, then it has no TikTok link.
- Given a threshold of 0, when the summary opens, then only sold-out variants show in "Stock bajo".
- Given a threshold of 1,001 or -1, when saved, then it is refused with `422`.
- Given two tabs that saved the same version, when the second saves, then it gets `409` and nothing of it is saved.
- Given a visitor, when it calls `PUT /api/admin/store/settings`, then it gets `401`; a customer gets `403`.

**Design notes**
- **New module `settings`** (no dependencies). `settings.domain.StoreSettings` aggregate with value objects `ContactEmail`, `WhatsAppNumber`, `SocialLink` (one per network, with its allowed hosts), `Announcement` and `LowStockThreshold`, plus a `version` for the optimistic check. The table is `store_settings` (one migration): a single row with an `id` fixed by a check constraint, the columns above, `version` and `updated_at`, and check constraints backing the domain rules. It has no personal data, so there is no RLS and it joins the public list of `RowLevelSecurityCoverageTest` with that reason. Insert and delete are revoked from `tienda_app`, as `shop_setting`.
- **Inbound ports:** `GetPublicStoreContact` (no actor; the contact links and the announcement), `GetStoreSettings` (admin), `UpdateStoreSettings` (`Command(actor, version, fields...)`; result `Updated(settings)`, `Invalid(fields)`, `VersionChanged(current)` or `Forbidden`).
- **`settings.api.StoreSettings`:** `lowStockThreshold()`, for `backoffice` and the Stock screen.
- **Endpoints:**
  - `GET /api/store/contact` → `200` `{email, whatsAppNumber, instagramUrl, facebookUrl, tiktokUrl, announcement}`, with nulls for empty values (catalog rate-limit policy).
  - `GET /api/admin/store/settings` → `200`, everything plus `version`.
  - `PUT /api/admin/store/settings` with every field and `version` → `200`; `409` `SETTINGS_CHANGED`; `422` with the fields.
- **Storefront:** `storeContact.ts` stops being the source. `useStoreContact()` (TanStack Query, one shared query, `staleTime` 5 minutes) feeds `ContactSection`, `SiteFooter` (Facebook joins Instagram and TikTok in "Redes") and the header strip. While it loads, or when it fails, nothing that depends on it is shown, as today with empty values. The test helper `frontend/src/test/storeContact.ts` mocks the query instead.
- **Page:** `/admin/datos-de-la-tienda`, a one-column form at 360 px with the strip preview.

---

## UC-37 Activity log

**Actor:** Administrator (reads); System (writes, inside each admin use case) · **Module:** `activity` (new) · **Stage:** E8a

**Preconditions:** the administrator is signed in (UC-04).

**Main flow**
1. The administrator changes something in the back office.
2. In the same transaction, the system records what changed.
3. The administrator opens "Actividad" and sees the entries, newest first, filtered by type and date if wanted.

**Alternative flows**
- The change is rejected or makes no change (a validation error, a `409`, a retried idempotent request): nothing is recorded.
- The change fails after the entry was written: both roll back, so there is never an entry without its change or a change without its entry.

**Business rules**
- **What is recorded:** every write of the back office.

  | Area | Actions |
  |---|---|
  | `CATALOG` | Categories (UC-05): created, renamed, reordered, deactivated, activated, deleted. Products (UC-06): created, details updated, published, hidden; variants generated; a variant's price or active state changed; photos uploaded, reordered, deleted. Size guide saved or deleted (RNF-31.5). |
  | `STOCK` | A stock adjustment (UC-07), with the quantity and the stock before and after. |
  | `ORDERS` | Status changed, with from and to, and whether the stock was put back. Tracking number set or corrected. Withdrawal resolved (UC-29). |
  | `PROMOTIONS` | Coupons (UC-11, UC-23): created, updated, activated, deactivated, deleted. |
  | `REVIEWS` | Approved, rejected, deleted (UC-25). |
  | `SHIPPING` | Zone costs changed, with the provinces that changed; free-shipping threshold changed (UC-12). |
  | `CONTENT` | Home banner updated, published, hidden, photo replaced (UC-32). |
  | `SETTINGS` | Store settings changed (UC-36). |
  | `CUSTOMERS` | Customer profile viewed (UC-34). |
  | `ACCOUNT` | Administrator signed in. Second factor enrolled. Recovery code used (UC-04). |

- **Each entry:** when (from the `Clock`); the administrator's user id; the area and the action; the subject (its type and id, plus a label that is never personal data: product or category name, SKU, coupon code, order number `#1001`, withdrawal code, province); and the changed fields, each with its before and after value as display text, at most 20 fields of at most 200 characters each.
- **Never recorded:** buyer or customer names, emails, phones, national IDs or addresses; the free-text note of a stock adjustment (it may contain personal data, UC-07); review texts; passwords, second-factor secrets, codes, tokens or idempotency keys; photo file names.
- Entries are append-only: nothing edits or deletes them, except the retention job.
- **Retention** (D-E8-11): a daily job at 03:00 Argentina time deletes the entries older than 2 years.
- **Screen:** "Actividad" lists 50 per page, newest first, filterable by area and by a date range (Argentina dates, both inclusive). Each row shows the date and time, the action in Spanish, the subject label linked to its screen (a product or an order opens itself; a category, a coupon, a review, a withdrawal, the shipping table and the banner open their section; a variant, the settings, a customer and the administrator are not links until their screens exist), and the changed fields as "antes → después".

**Acceptance criteria**
- Given a variant whose price changes from $20,000 to $22,000, when saved, then "Actividad" shows the change with the SKU, $20.000 → $22.000 and the time.
- Given a stock adjustment of +5 with the note "devolución de Ana", when saved, then the entry shows +5 and the stock before and after, and never the note.
- Given an order moved to `SHIPPED` with a tracking number, when "Actividad" opens, then the entry shows `#1001`, PREPARANDO → ENVIADO and the tracking number, without the buyer's name.
- Given a stock adjustment retried with the same `Idempotency-Key`, when it is answered from the stored result, then there is still one entry.
- Given a coupon update rejected with `422`, when "Actividad" opens, then there is no entry for it.
- Given an order status change whose conditional update finds the order changed (`409`), when it ends, then there is no entry.
- Given entries of 2 years and 1 day ago and of 1 year ago, when the retention job runs, then only the older one is deleted.
- Given the filter `STOCK` and 2026-10-01 to 2026-10-15, when applied, then only stock entries of those Argentina days show.
- Given a customer session, when it calls `GET /api/admin/activity`, then it gets `403`; and through RLS a `CUSTOMER` or `ANONYMOUS` context reads no row of the table.
- Given any entry, when its stored JSON is checked by the test that covers every action, then it contains no email, phone, national ID or buyer name of the fixture order.

**Design notes**
- **New module `activity`** (no dependencies, so every module may depend on its `api` without cycles).
  - `activity.api.ActivityLog`: `record(ActivityEntry)`. It must be called inside the caller's transaction. It is a plain synchronous call, not an event: an event would carry the changed values through `event_publication`, and a synchronous call shares the transaction (ARCHITECTURE.md section 3, rule 5, applies to events only).
  - `ActivityEntry(UserId actor, Area area, Action action, Subject subject, List<FieldChange> changes)`. `Action` is an enum per area. `Subject(type, id, label)`. `FieldChange(field, before, after)`, where field is a stable key the frontend translates (`price`, `status`, `trackingNumber`...). The domain enforces the size limits.
  - `ListActivity` (`Query(actor, area?, from?, to?, page)`; result `Listed(entries, hasMore)`, `Invalid(field)` or `Forbidden`), read port `ActivityPages`.
  - `PurgeOldActivity` (system, `@Scheduled`, `callAsSystem`), port `ActivityRetention`.
- **Table** `activity_entry` (migration): `id`, `occurred_at`, `actor_id` (uuid, no FK, rule 6), `area`, `action`, `subject_type`, `subject_id`, `subject_label`, `changes` (`jsonb`, an array of `{field, before, after}`), indexed on `(occurred_at DESC)` and `(area, occurred_at DESC)`. **RLS:** `ADMIN` inserts and selects; `SYSTEM` inserts only `ACCOUNT` entries (sign-in runs before there is an `ADMIN` actor, UC-04) and deletes rows older than 2 years (a policy with `occurred_at < now() - interval '2 years'`); nobody updates. `UPDATE` and `TRUNCATE` are revoked from `tienda_app`.
- **Recording:** each admin use case builds its entry from the before and after state it already holds and calls `ActivityLog` after its own write succeeds, inside its transaction. Results that change nothing (`Invalid`, `NotFound`, `StatusChanged`, a replayed idempotent result) record nothing. Each module gets one small outbound port (`<Module>Activity`) with an adapter over `activity.api`, so use-case tests assert entries through an in-memory fake (contract test per port, CLAUDE.md L). A shared test, `ActivityPersonalDataTest`, runs every recorded action against fixtures with known personal data and fails if any of it appears in `changes` or `subject_label`.
- **Catalog and content entries** (T-E8-02, decided on 2026-10-23). Values are display text: money as `$22.000` (`$22.000,50` with cents, `activity.api.ActivityValues`), a yes/no as `true`/`false` and a choice as its enum name (`SUMMER`, `WOMEN`), which the screen translates along with the field names; an empty text (a banner without body) is no value; a description is cut to 200 characters like any value. An entry without a label (the banner, the category order) shows "Banner de la portada" or "Categorías" on the screen, linked to its section.

  | Action | Subject (label) | Changed fields |
  |---|---|---|
  | `CATEGORY_CREATED` | category (name) | `name`: none → new |
  | `CATEGORY_RENAMED` | category (new name) | `name`: old → new |
  | `CATEGORIES_REORDERED` | `CATEGORY` without an id or label | `order`: the names in the old order → in the new order, comma-separated |
  | `CATEGORY_ACTIVATED`, `CATEGORY_DEACTIVATED`, `CATEGORY_DELETED` | category (name) | none |
  | `PRODUCT_CREATED`, `PRODUCT_PUBLISHED`, `PRODUCT_HIDDEN` | product (name) | none |
  | `PRODUCT_UPDATED` | product (new name) | only the ones that changed: `name`, `description`, `category` (names), `material`, `season`, `audience` |
  | `VARIANTS_GENERATED` | product (name) | `variants`: none → how many were created; `sizes` and `colors`: none → those of the new combinations, comma-separated |
  | `VARIANT_UPDATED` | variant (SKU) | only the ones that changed: `price`, `active` |
  | `PRODUCT_IMAGE_UPLOADED`, `PRODUCT_IMAGES_REORDERED`, `PRODUCT_IMAGE_DELETED` | product (name) | none (never a file name) |
  | `SIZE_GUIDE_SAVED`, `SIZE_GUIDE_DELETED` | category (name) | none |
  | `HOME_BANNER_UPDATED` | `HOME_BANNER` | only the ones that changed: `title`, `body`, `linkText`, `linkPath` |
  | `HOME_BANNER_PUBLISHED`, `HOME_BANNER_HIDDEN`, `HOME_BANNER_PHOTO_REPLACED` | `HOME_BANNER` | none |

  Nothing is recorded when the write changes nothing: publishing a published product or banner, hiding a hidden one, activating an active category or deactivating an inactive one, renaming to the same name, saving the same details, variant values, banner texts or size guide, generating no new combination, a photo order that moves nothing, deleting a size guide that does not exist, and a photo upload answered from an earlier one with the same key.
- **Entries of the other areas** (T-E8-03, decided on 2026-10-24). Same value rules as the catalog; a date is `01/10/2026` (`ActivityValues.date`), a count its number, and a stock adjustment its signed quantity (`+5`, `-3`). An entry without a label (the free-shipping threshold, a review of a product the catalog no longer has) shows "Envíos" or "Reseñas" on the screen, linked to its section; a province label, an order status and a coupon type are translated.

  | Action | Subject (label) | Changed fields |
  |---|---|---|
  | `STOCK_ADJUSTED` | variant (SKU) | `quantity`: none → the signed quantity; `stock`: on hand before → after. Never the note |
  | `ORDER_STATUS_CHANGED` | order (`#1001`) | `status`: from → to; `trackingNumber`: none → the number, when shipped; `restock`: none → `true`/`false`, when cancelling a paid or preparing order |
  | `TRACKING_NUMBER_CORRECTED` | order (`#1001`) | `trackingNumber`: old → new |
  | `WITHDRAWAL_RESOLVED` | withdrawal (code, `ARR-...`) | none |
  | `COUPON_CREATED`, `COUPON_ACTIVATED`, `COUPON_DEACTIVATED`, `COUPON_DELETED` | coupon (code) | none |
  | `COUPON_UPDATED` | coupon (new code) | only the ones that changed: `code`, `type` (`PERCENTAGE`, `FIXED_AMOUNT`), `value` (`15%` or `$5.000`), `validFrom`, `validUntil`, `maxUses` (none: unlimited), `minimumPurchase` (none: no minimum), and the scope as counts, `scopeProducts` and `scopeCategories` (both `0`: the whole cart), each recorded when its set changed even if the count did not |
  | `REVIEW_APPROVED`, `REVIEW_REJECTED`, `REVIEW_DELETED` | review (its product's name; none when the catalog no longer has it) | none (never the text, the rating or the author) |
  | `SHIPPING_ZONES_CHANGED` | `SHIPPING` without an id, labelled with the province (`SANTA_FE`); one entry per province whose cost changed | `cost`: before → after (none: not served) |
  | `FREE_SHIPPING_THRESHOLD_CHANGED` | `SHIPPING` without an id or label | `threshold`: before → after (none: no free shipping) |
  | `ADMIN_SIGNED_IN` | `ADMINISTRATOR` (the administrator's id, no label) | none |
  | `RECOVERY_CODE_USED`, `SECOND_FACTOR_ENROLLED` | `ADMINISTRATOR` | none; recorded next to the `ADMIN_SIGNED_IN` of the same sign-in, so every sign-in has one |

  Nothing is recorded when the write changes nothing: an adjustment answered from an earlier one with the same key, correcting a tracking number to the same one, resolving a resolved withdrawal, saving a coupon with the same terms, activating an active coupon or deactivating an inactive one, approving an approved review or rejecting a rejected one, saving the same zone costs or threshold. A refused write (a `409`, a `422`, deleting a coupon that has uses) records nothing either.
- **Variant SKUs for stock entries:** `inventory` cannot depend on `catalog` (`catalog` already depends on `inventory::api`), so `inventory.api.VariantSkus` declares the lookup and `catalog` implements it over its own store (dependency inversion, ARCHITECTURE.md section 3, rule 3).
- **Sign-in entries** are recorded by `accounts` after a successful sign-in as `ADMIN`, never for failed attempts: those stay in the lock counter of UC-04.
- **Endpoint:** `GET /api/admin/activity?area=&from=&to=&page=` → `200` `{items, hasMore}`; `400`.
- **Page:** `/admin/actividad`. Action and field names are translated in `es.ts`. Money values are stored as display text already formatted (`$22.000`), so old entries read the same after any format change.

---

## Changes to UC-18 (orders: search and packing slip)

**Business rules (new)**
- **Search** (`q`): when it is an order number (`1001` or `#1001`, the `OrderNumberText` rule of UC-29), it finds that order. Otherwise, 2 to 100 characters, it matches orders whose buyer first name, last name or email contains it, ignoring case and accents, with `%`, `_` and `\` literal.
- **Date filter:** `from` and `to`, optional Argentina dates on `created_at`, both inclusive.
- Search, dates and status combine. The list keeps its order (newest first) and its 50 per page, and all three are kept in the URL.
- **Packing slip** (D-E8-10): "Imprimir remito" on the order detail, for orders in `PAID`, `PREPARING`, `SHIPPED` or `DELIVERED`. It opens a print page with the store name, order number and date, the lines (product, size, color, quantity, SKU), the buyer's name and phone, the shipping address, and the tracking number when there is one. It has no prices, so it can go inside the parcel. The print dialog opens on load, and the page has a "Volver al pedido" link.

**Acceptance criteria**
- Given orders #1001 and #1002, when searching `#1002` or `1002`, then only #1002 shows.
- Given a buyer "María José" "Pérez", when searching `pérez`, then her orders show.
- Given a date filter from 2026-10-10 to 2026-10-10 and an order at 23:59 Argentina time that day, when applied, then it shows; one at 00:00 on the 11th does not.
- Given a search and the `PAID` filter, when both are applied, then only paid orders matching the search show.
- Given a `PREPARING` order, when "Imprimir remito" opens, then the page shows the lines, name, phone and address, and no amount.
- Given an `AWAITING_PAYMENT` order, when its detail opens, then there is no "Imprimir remito".
- Given a search, when the request is logged, then the log line has no `q` value.

**Design notes**
- `ListOrders.Query` gains `q`, `from` and `to`. `OrderPages` builds one constant SQL text with optional conditions (`(:number IS NULL OR number = :number)`, `(:pattern IS NULL OR lower(unaccent(customer_first_name)) LIKE :pattern ESCAPE '\' OR ...)`), never by concatenation (RNF-28, the Semgrep gate). A new index on `orders (created_at DESC)` is shared with UC-33.
- `GET /api/admin/orders?status=&q=&from=&to=&page=` → `200`; `400` for an invalid `q`, a bad date or `from` after `to`.
- The packing slip needs no endpoint: `/admin/pedidos/:orderId/remito` reads the existing `GET /api/admin/orders/{id}` and uses print CSS (`@media print`: no admin menu, black on white, A4 and a 10 cm label width). It is not recorded as activity, because it changes nothing.

---

## Changes to UC-06 (products: search and filters)

**Business rules (new)**
- The product list gains:
  - search (`q`, 2 to 100 characters): name contains it, ignoring case and accents; or a variant's SKU equals it, ignoring case;
  - category filter;
  - state: published or hidden;
  - stock, one of three that never overlap: `OUT` (no active variant has available units), `LOW` (not `OUT`, and some active variant is at or under the threshold, sold-out ones included) or `IN_STOCK` (every active variant is above the threshold).
- The filters combine, are kept in the URL, and the list keeps its order and 50 per page.

**Acceptance criteria**
- Given a product "Pijama Corazón" and a search `corazon`, when applied, then it shows.
- Given a variant SKU `PP-AB12CD34`, when searching `pp-ab12cd34`, then its product shows.
- Given the filter `OUT` and a product whose only variant has 2 on hand and 2 reserved, when applied, then it shows.
- Given a hidden product and the published filter, when applied, then it does not show.

**Design notes**
- `ListAllProducts.Query` gains `q`, `categoryId`, `state` and `stock` (enums). `ProductSummaries` uses one constant SQL text with optional conditions, the name compared with `lower(unaccent(...))` (the extension of UC-26, V10), and `inventory.v_variant_availability` for stock. The threshold comes from `settings.api.StoreSettings`, so `catalog` gains a dependency on `settings` (it has none of its own, so no cycle).
- `GET /api/admin/catalog/products?q=&category=&state=&stock=&page=`; `400` for unknown enum values.

---

## Changes to UC-07 (stock: the Stock screen)

**Business rules (new)**
- A "Stock" screen lists every active variant of every product: product, size, color, SKU, on hand, reserved, available, and whether the product is published. It is ordered by available stock, then product name, size and color in display order.
- Filters: `LOW` (available at or under the threshold, above 0), `OUT` (available 0) or all; product name or SKU search as in UC-06; category.
- **Quick adjustment:** each row has "+" and "−" and a quantity field with an optional note. It saves through the same stock adjustment of UC-07, with its own `Idempotency-Key` per attempt, its rules (stock never under the reserved units) and its history. The row then shows the new values.
- The link of each row opens the variant's stock history (UC-07).

**Acceptance criteria**
- Given the filter `LOW`, a threshold of 3 and variants with 0, 2 and 5 available, when applied, then only the one with 2 shows; with `OUT`, only the one with 0.
- Given a row with 4 on hand and 3 reserved, when −2 is saved, then it is refused as UC-07 refuses it, and the row does not change.
- Given +3 saved twice with the same key (a retry), when it ends, then the stock grew by 3 and there is one movement.
- Given a saved adjustment, when the variant's history opens, then the movement is there with its note.

**Design notes**
- Read side in `catalog`: `ListStockOverview` (`Query(actor, filter, q?, categoryId?, page)`), read port `StockOverviewPages`, joining the catalog tables with `inventory.v_variant_availability`, the same reading as `catalog.api.LowStockVariants` (UC-33). It is a constant SQL text with an enum for the filter.
- `GET /api/admin/catalog/stock?filter=ALL|LOW|OUT&q=&category=&page=` → `200`. The writes keep using `POST /api/admin/stock-adjustments`.
- Page: `/admin/stock`, rows as cards at 360 px with the 44 px buttons of RNF-26.

---

## Back-office navigation (E8a)

**Business rules**
- The side menu is grouped:
  - Resumen;
  - Ventas: Pedidos, Arrepentimientos, Clientes;
  - Catálogo: Productos, Categorías, Stock;
  - Marketing: Cupones, Reseñas, Portada;
  - Envíos;
  - Configuración: Datos de la tienda, Actividad;
  - then "Ver tienda" and "Salir".
  E8b and E8c add their screens to these groups when they arrive.
- The current page is marked with `aria-current="page"`.
- Under 860 px the menu is closed behind a "Menú" button, in a side panel. It traps the focus, Escape closes it and gives the focus back to the button, and choosing a link closes it. From 860 px it is always visible.
- "Pedidos" shows the number of `PAID` orders waiting as a badge, from the same read as UC-33's "Para hacer hoy" (a count, refreshed when the summary or the order list loads).

**Acceptance criteria**
- Given a 360 px screen, when "Menú" is pressed, then the panel opens with every group, and Escape closes it and returns the focus to "Menú".
- Given `/admin/stock`, when the menu shows, then "Stock" is marked as the current page.
- Given 2 `PAID` orders, when the back office opens, then "Pedidos" shows 2.

**Design notes**
- `AdminLayout` gets `AdminNav` (the groups as a data list, so a phase adds an entry without editing the component's logic) and `AdminMenuPanel` (a modal `dialog`, like the store's `MobileMenu` of UC-31). The badge reads `GET /api/admin/dashboard/counts`, a small part of UC-33 (`backoffice.application.GetWorkloadCounts`), so the menu does not load the whole summary.
