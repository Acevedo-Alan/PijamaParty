# E4 – Customer account

**Status:** Draft · **Use cases:** UC-19 to UC-22, UC-27 and UC-28 · **Spanish counterpart:** `docs/specs/E4-cuenta-cliente/spec.md`

**Modules involved:** `accounts`, `cart.api`, `ordering.api`, `notifications`, `reviews` (E5), `platform`.

Registering is optional: buying as a guest stays the main flow. An account saves the buyer's data and addresses, lists its orders, and can be deleted with its data downloaded first.

**Decisions settled before the stage (T-E4-01):**
- The national ID is **not** stored on the account: the checkout asks for it on every purchase and it lives only on the order (UC-21, R-05). The account and its address book hold less personal data.
- Single-use email links: the email verification link lasts 24 hours, the password reset link 30 minutes and the Facebook link confirmation (UC-27) 24 hours. Their tokens follow RNF-29.6: 32 random bytes from `SecureRandom`, stored only as SHA-256.
- The address book holds at most 10 addresses per customer; an 11th is refused with `422`.
- The customer's phone is an optional column of the account (8 to 15 digits, as on the order). Each purchase of the customer leaves the order's phone on the account, for the next prefilled checkout (decided in T-E4-06).

**Schema (T-E4-01, V29):**
- `app_user` (V5, `accounts` module) already has `email_verified`, the optional `password_hash` (accounts that only sign in with Google or Facebook, UC-27) and `name`. V29 adds `phone` (null, or 8 to 15 digits).
- `address` (`accounts` module): `id`, `user_id` (foreign key to `app_user`, deleted with it, UC-28), `street`, `street_number`, `floor`, `apartment`, `city`, `province`, `postal_code` (the same formats as the order's address, UC-13), `is_primary`, `created_at`, `updated_at`. At most one primary address per customer (partial unique index). The limit of 10 belongs to the address book (UC-20, T-E4-05). An order keeps its own copy of the address (R-04), so deleting an address never touches an order.
- `user_token` (`accounts` module): `id`, `user_id` (foreign key to `app_user`, deleted with it), `purpose` (`EMAIL_VERIFICATION` or `PASSWORD_RESET`; `LINK_CONFIRMATION` arrives with the external identities in T-E4-13), `token_hash` (SHA-256 of the token, lowercase hex, unique; the token itself is never stored, RNF-29.6), `created_at`, `expires_at` (exactly 24 hours or 30 minutes after `created_at`, by purpose), `used_at` (set once, before the expiry). Indexed by user and by expiry, for the cleanup.
- Row Level Security on `address` and `user_token`, enabled and forced (RNF-27), with the policies below. The policy tests with the five actors are T-E4-11.

**Row Level Security policies (T-E4-01):**

| Table | Customer | Guest (`ANONYMOUS`) | Administrator | System |
|---|---|---|---|---|
| `app_user` | Reads and updates its own: only `name`, `phone`, `password_hash` and `updated_at` | – | Reads its own | Reads to authenticate; records failed attempts, the lock and the second factor; creates the initial administrator and unverified customers with a password; marks an email verified or replaces a customer's password only while consuming a token of that user (below) |
| `address` | Everything on its own | – | Reads | – |
| `user_token` | – | – | – | Creates, reads, marks used and deletes (cleanup) |

- **Token-bound changes:** the email verification and the password reset have no session, so they run as `SYSTEM`. A bug there must not verify or change the password of any account, so the `app_user` update guard lets `SYSTEM` set `email_verified` to true only when the transaction's `app.account_token_hash` (read by `app_account_token_hash()`) is the hash of a used `EMAIL_VERIFICATION` token of that user, and replace `password_hash` only with a used `PASSWORD_RESET` token of that user. The use case marks the token used first, in the same transaction. The application sets the variable through the Row Level Security context, like the cart and tracking tokens (T-E4-02 and T-E4-03).
- A token row only changes from unused to used: nothing else of it can be updated.
- Deleting the account (UC-28, T-E4-16) brings its own policy (V36); its addresses, tokens and external identities go with it through their foreign keys.

**Schema of social sign-in (T-E4-13, V34, UC-27):**
- `external_identity` (`accounts` module): `id`, `user_id` (foreign key to `app_user`, deleted with it, UC-28), `provider` (`GOOGLE` or `FACEBOOK`), `subject` (the provider's account identifier, printable ASCII without spaces, at most 255 characters), `email` (the one the provider gave when it was linked, normalized as `app_user.email`), `created_at`. Unique on `(provider, subject)`: a provider account belongs to one customer; unique on `(user_id, provider)`: a customer links at most one account of each provider. No access token of the provider is stored. A link is never updated: it is unlinked and linked again.
- `user_token` gains the `LINK_CONFIRMATION` purpose (24 hours) and the optional `provider` and `subject` columns: a link confirmation always names the external account it links once used, and no other purpose names one (decided). The update guard keeps them unchanged, as the rest of the token.
- Row Level Security on `external_identity`, enabled and forced (RNF-27):

| Table | Customer | Guest (`ANONYMOUS`) | Administrator | System |
|---|---|---|---|---|
| `external_identity` | Reads and unlinks (deletes) its own | – | – | Reads to authenticate; links them, only to customers (R-14) |

- `app_user` changes for UC-27:
  - The system also creates customers without a password: verified (Google) or unverified (Facebook). A customer with a password is still created unverified (UC-19).
  - The system may also verify a customer's email when the transaction consumed a `LINK_CONFIRMATION` token of that user, or when a Google identity with the user's email is linked to it (Google vouches for its emails, Facebook does not). The use case links the identity first, in the same transaction.
  - **An unverified account verified by social sign-in loses its password (decided):** the same update that verifies it through Google or a link confirmation drops `password_hash`. Whoever set that password never proved it owned the inbox, so an account pre-registered with someone else's email cannot be taken over; the customer can create a password from "Mi cuenta". A verified account keeps its password when Google is linked to it.
- The provider that returns no email (alternative flow of UC-27) needs a token before any account exists; it is designed with the sign-in itself (T-E4-14), with its own migration if it needs one (decided). It needs none: an unverified customer without a password and without external accounts gets the link confirmation (see "Sign-in with a provider (T-E4-14)" in UC-27). V35 adds `pending_link`, the external account waiting for its confirmation email to be sent.

---

## UC-19 Register and sign in

**Actor:** Visitor · **Module:** `accounts` + `cart.api` + `notifications` · **Stage:** E4

**Main flow**
1. The visitor registers with email, password and name.
2. The system sends an email to verify the address.
3. Once verified, the visitor can sign in.
4. If it had a guest cart, it is merged into the account's cart.

**Alternative flows**
- Forgotten password: the customer receives by email a single-use link that expires in 30 minutes.
- Prefers not to create a password: registers or signs in with Google or Facebook (UC-27).

**Business rules**
- The email is unique.
- The password is stored with a secure hash (BCrypt, as the administrator's, V6); at least 8 characters.
- After 5 consecutive failed attempts the account is locked for 15 minutes (the `User` aggregate rule of UC-04).
- The recovery answer is always the same, whether the email exists or not, so it never reveals accounts.

**Acceptance criteria**
- Given an email already registered, when someone registers with it, then it is refused with a message that exposes no account data.
- Given a user with a guest cart, when it signs in, then the items of both carts are merged without exceeding the stock.
- Given a recovery link already used, when it is opened again, then it is refused.
- Given more than 3 registration or recovery attempts per hour from the same IP, when another arrives, then it receives `429` with `Retry-After`.

**Design notes**
- Customer session: the same mechanism as the administrator (Spring Session JDBC, cookie `HttpOnly`, `Secure`, `SameSite=Lax`, a new session id on sign-in, CSRF on), with the `CUSTOMER` role. The session's `principal_name` is the user id, never the email (RNF-27).
- Endpoints: `POST /api/account/register`, `POST /api/account/verify-email`, `POST /api/account/login`, `POST /api/account/logout`, `POST /api/account/password-recovery`, `POST /api/account/password-reset`. Details in T-E4-02 and T-E4-03.
- Tokens: `user_token` with purposes `EMAIL_VERIFICATION` (24 hours) and `PASSWORD_RESET` (30 minutes) (T-E4-01).
- **Registration, verification and sign-in (T-E4-02):**
  - `POST /api/account/register` (`email`, `password`, `name`): `201` with no body. The email is normalized (trimmed, lower case) and the name trimmed, 1 to 100 characters. A customer's password has 8 characters at least and 72 bytes at most (the BCrypt limit); the administrator keeps 12 (UC-04). An email already registered, the administrator's included, gets `409` `EMAIL_ALREADY_REGISTERED`, which says nothing else about that account. The account is created as `SYSTEM`, unverified, and the verification email is enqueued in the same transaction.
  - The verification email (`EMAIL_VERIFICATION` in the outbox, the user as reference) carries the link `/cuenta/verificar#{token}`: the token goes in the fragment, which the browser never sends, so it stays out of server logs, the `Referer` and the page views sent to analytics. The token is created when the email is sent, as the tracking links (T-E3-09), so its 24 hours count from the sending. The email is not sent to an account already verified or deleted.
  - `POST /api/account/verify-email` (`token`): `204`, and the account is verified. A token that is unknown, used, expired or of another purpose gets `410` `INVALID_LINK`. It runs as `SYSTEM` bound to the hash of the token (`app.account_token_hash`): it marks the token used, then the email verified. Verifying does not sign in: the page asks to sign in.
  - `POST /api/account/login` (`email`, `password`): `200` with the customer (`id`, `email`, `name`); a new session id and CSRF token, with the `CUSTOMER` role. A wrong password, an unknown email and the administrator's account all get the same `401`; the lock is `423` with `lockedUntil`, as UC-04. A right password of an unverified account gets `403` `EMAIL_NOT_VERIFIED`, opens no session and enqueues a new verification email, unless one is still waiting to be sent. It never resets or counts attempts.
  - `POST /api/account/logout`: `204`. `GET /api/account/me`: the signed-in customer, or `401`. The storefront header uses it.
  - Signing in acts on the account's own cart, which the guest cart joins (T-E4-04, below).
- **Password recovery and reset (T-E4-03):**
  - `POST /api/account/password-recovery` (`email`): always `204` with no body, whatever the email is: unknown, malformed, the administrator's or a customer's. Only a customer account gets the reset email, verified or not, with a password or without one (UC-27). It is enqueued (`PASSWORD_RESET` in the outbox, the user as reference) as `SYSTEM` in the transaction that finds the account, unless one is still waiting to be sent. The administrator never gets one.
  - The reset email carries the link `/cuenta/restablecer#{token}`, with the token in the fragment as the verification link. The token is created when the email is sent, so its 30 minutes count from the sending. The email is not sent to an account deleted or that is not a customer's.
  - `POST /api/account/password-reset` (`token`, `password`): `204`. The password follows the customer's rules (`422` `PASSWORD_TOO_SHORT` or `PASSWORD_TOO_LONG`); it is checked and hashed before the token is touched (RNF-21), so an invalid password does not use up the link. A token that is unknown, used, expired or of another purpose gets `410` `INVALID_LINK`. It runs as `SYSTEM` bound to the hash of the token: it marks the token used, then every other unused and unexpired reset token of that user (an older link stops working), replaces the password, clears the failed attempts and the lock, and marks the email verified, because opening the link proves the customer owns the inbox (V31 lets the reset token verify, widening the guard of V29). After the commit it ends every session of the account (Spring Session, by principal name). Resetting does not sign in: the page asks to sign in.
  - The sign-in page links to `/cuenta/recuperar`, which asks for the email and always answers with the same message.
- **Cart merge (T-E4-04):**
  - Every sign-in of a customer (password now, an external provider from T-E4-14) joins the browser's guest cart, the one of its `__Host-cart_token` cookie, to the account's cart. The `cart` module does it through a sign-in listener of `platform` (`SignInListener`), which runs after the credentials are checked and before the session opens, so the `accounts` module knows nothing about carts. The administrator's sign-in leaves the guest cart alone.
  - A variant in both carts adds the quantities, capped at the available stock (on hand − reserved), on the account's line, which keeps its last seen price; a line already above the stock is never lowered. A variant only in the guest cart moves with its line (id, last seen price and when it was added), capped at the available stock. A guest line of a variant the shop no longer sells, or with no units available, is left out; the account's own lines stay as they are. Lines keep the order they were added in.
  - The account's cart keeps its coupon; it takes the guest's only when it has none. Whether it applies is decided each time the cart is priced, as UC-10.
  - Without an account cart, the customer gets a new one with the guest's lines and coupon; an empty guest cart without a coupon creates none.
  - The guest cart is deleted, and the sign-in response expires the cookie (`Max-Age=0`): signing out starts a new, empty guest cart. A failed sign-in touches neither the cart nor the cookie. The merge never fails the sign-in for a line it cannot bring and answers nothing about it: the storefront reads the cart again after signing in.
  - Row Level Security (V32): the customer carrying the browser's cart token reads the guest cart of that token with its items and deletes it, and never changes it. V32 splits the item policy of V12: reads follow every cart the actor reads, writes only its own cart (`app_owns_cart`).
- Rate limits (T-E4-10, RNF-22): customer sign-in 5 per minute per IP and per account (RNF-29.5); registration and recovery 3 per hour per IP.
  - The customer sign-in shares the administrator's buckets (`login-ip` and `login-account`), as the single "Login (cliente y administrador)" row of RNF-22: one IP gets 5 sign-in attempts a minute in all, and an email 5 whichever sign-in it goes through. The `429` comes before the sign-in, so it never says whether the account exists.
  - Registration and password recovery share one bucket per IP (`account-email`): the fourth of either in an hour gets `429`. Each may send an email.
  - Verifying the email and resetting the password have no policy of their own: they only use a 128-bit token and send nothing.

---

## UC-20 Manage the address book

**Actor:** Customer · **Module:** `accounts` · **Stage:** E4

**Main flow:** add, edit and delete addresses (street, number, floor, apartment, city, province and postal code) and mark one as primary.

**Business rules**
- At most 10 addresses per customer.
- At most one primary address; marking another one unmarks the previous one.
- The address fields have the formats of the order's address (UC-13).

**Acceptance criteria**
- Given an address marked as primary, when another one is marked, then the previous one stops being primary.
- Given an address used in previous orders, when it is deleted, then the orders keep their copy of the address.
- Given a customer with 10 addresses, when it adds another, then it is refused.

**Design notes**
- `Address` in `accounts.domain`; the address book rules (limit, single primary) belong to the domain (T-E4-05).
- Endpoints: `GET`, `POST /api/account/addresses`, `PUT`, `DELETE /api/account/addresses/{id}`. Details in T-E4-05.
- **Address book (T-E4-05):**
  - Only the signed-in customer, on its own addresses (`CUSTOMER` role, as the rest of `/api/account/**`); the writes require the CSRF token. Row Level Security (V29) shows a customer only its own addresses, so another customer's address is simply not found.
  - An address has `street` (1 to 100 characters), `number` (1 to 10), optional `floor` and `apartment` (1 to 10; blank is none), `city` (1 to 80), `province` (one of the 24 jurisdictions of UC-12) and `postalCode` (4 digits or a CPA, normalized to upper case). The texts are trimmed. These are the rules of the order's address (UC-13, `CheckoutForm`), applied by `accounts.domain`, which keeps its own `Province` with the same constants, because `accounts` depends only on `platform`.
  - A book that is not empty always has exactly one primary address. The first address becomes primary whatever the request says. Marking another one primary unmarks the previous one; the primary cannot be unmarked by itself, only replaced. Deleting the primary makes the oldest remaining address primary.
  - `GET /api/account/addresses`: `200` with the addresses (`id`, `street`, `number`, `floor`, `apartment`, `city`, `province`, `postalCode`, `primary`), the primary first and then from the oldest to the newest; an empty list when there are none.
  - `POST /api/account/addresses` (the fields, and an optional `primary`, false when absent): `201` with the new address and its `Location`. An 11th address gets `422` `ADDRESS_BOOK_FULL`. Fields that break a rule get `422` `INVALID_ADDRESS` with `fields`, the names of the invalid fields in the request's own names (an unknown province is a `400`, as in the checkout).
  - `PUT /api/account/addresses/{id}` (the same body): `200` with the address. It replaces every field; `primary: true` marks it primary, and false or absent leaves it as it was. An unknown address, or another customer's, gets `404` `ADDRESS_NOT_FOUND`; invalid fields get `422` `INVALID_ADDRESS`.
  - `DELETE /api/account/addresses/{id}`: `204`, or `404` `ADDRESS_NOT_FOUND`. Orders are not touched: they keep their own copy of the address (R-04) and have no foreign key to `address`.
  - The address book is the aggregate: a change locks the customer's `app_user` row (`SELECT ... FOR UPDATE`) before reading the addresses, so two concurrent changes of one book run one after the other and can neither pass 10 addresses nor leave two primaries (the partial unique index of V29 backs the second).
  - Storefront: `/cuenta/direcciones` lists the addresses, adds, edits, deletes (after a confirmation) and marks one as primary. The header shows a "Mis direcciones" link to it while signed in. A visitor who is not signed in is sent to `/cuenta/ingresar`.

---

## UC-21 Buy with prefilled data

**Actor:** Customer · **Module:** `ordering` + `accounts.api` · **Stage:** E4

**Main flow**
1. In the shipping step, the system offers the saved addresses and prefills name, email and phone.
2. The customer picks an address or enters a new one.
3. The rest of the checkout is the same as UC-13.

**Business rules**
- The national ID is asked on every purchase and is not stored on the account (decided, T-E4-01).
- The prefilled name and email can be changed for one order; the order is linked to the account anyway (decided, T-E4-06).
- Each purchase leaves its phone on the account; a new address is saved in the book only if the customer asks (decided, T-E4-06).

**Acceptance criteria**
- Given a customer with a primary address, when it opens the checkout, then that address is selected.
- Given a customer who completes a purchase, when it finishes, then the order is linked to its account.

**Design notes**
- The order already has `user_id` and the customer insert policy (T-E3-01).
- **Prefilled checkout (T-E4-06):**
  - `GET /api/account/me` (and the sign-in answer, which share the format) also answers `phone`, the account's, or null when it has none.
  - Storefront: for a signed-in customer, `/checkout` prefills name, email and phone from `/api/account/me`, once, only into fields still empty; every field stays editable. The national ID always starts empty (R-05). A visitor who is not signed in sees the checkout of UC-13 unchanged.
  - With saved addresses (`GET /api/account/addresses`), the shipping step offers them as choices plus "Otra dirección", with the primary selected. A saved address fills the address fields, which stay hidden while it is chosen; the request still carries the whole address, as typed, and the order keeps its own copy (R-04), with no reference to the book. "Otra dirección", or a book without addresses, shows the address fields of UC-13 and a "Guardar en mis direcciones" checkbox, unchecked, which is not shown when the book already has 10.
  - `POST /api/checkout` gains an optional `saveAddress` (false when absent), ignored for a guest. It counts in the request hash of the `Idempotency-Key` only when true, so the hash of every request without it stays as before T-E4-06.
  - In the checkout transaction, as the customer (RNF-27), once the order is inserted, `PlaceOrderService` calls its port `RememberCheckoutDetails` (`ordering.adapter.out.accounts`, over `accounts.api.CheckoutDetails`, so `ordering → accounts.api`, which keeps the graph acyclic). It locks the customer's `app_user` row, as the address book does (UC-20), sets the account's phone to the order's normalized phone when it differs, and, with `saveAddress`, adds the address to the book with the rules of UC-20: never primary unless it is the first, and a full book skips it without failing the order. A customer whose account no longer exists keeps its order and nothing is remembered. A repeated `Idempotency-Key` answers the first order and remembers nothing again.

---

## UC-22 View my orders and tracking

**Actor:** Customer · **Module:** `ordering` + `accounts.api` · **Stage:** E4

**Main flow**
1. The customer opens "Mis pedidos" and sees the list with status and total.
2. It opens an order and sees the items, the address, the status, the shipment tracking number and a link to Correo Argentino's tracking.

**Business rules**
- Orders placed as a guest with the same email are linked to the account once its email is verified.

**Acceptance criteria**
- Given a customer with two orders, when it opens "Mis pedidos", then it sees both, newest first.
- Given a customer, when it tries to open another person's order, then it gets access denied.
- Given a guest order with the same verified email, when the customer registers, then the order shows in its list.

**Design notes**
- Endpoints: `GET /api/account/orders`, `GET /api/account/orders/{id}` (T-E4-07).
- **My orders (T-E4-07):**
  - Only the signed-in customer, on its own orders (`CUSTOMER` role, as the rest of `/api/account/**`); read-only, as the customer (RNF-27). Row Level Security (`orders_customer_select`, V14) already shows a customer only the orders of its `user_id`, and the queries also filter by it, so the tracking token a session may carry never adds a guest order to the list.
  - `GET /api/account/orders?page=0` (`ListMyOrders`, through `CustomerOrderPages`): `200` with `orders` (`id`, `number`, `createdAt`, `status`, `totalCents`), newest first (then the highest number), 20 per page from page 0, and `hasMore`; an empty list when there are none. A page out of 0 to 1000 gets `400`. Over `orders_user_created_idx` (V14).
  - `GET /api/account/orders/{id}` (`GetMyOrder`, through `CustomerOrderViews`): `200` with `id`, `number`, `status`, `createdAt`, `items` (`sku`, `productName`, `size`, `color`, `quantity`, `unitPriceCents`, `lineTotalCents`, in the cart's order), `subtotalCents`, `discountCents`, `couponCode` (or `null`), `shippingCents`, `totalCents`, `address` (`street`, `number`, `floor`, `apartment`, `city`, `province`, `postalCode`; the order's own copy, R-04) and `shipmentTrackingNumber` (or `null`). The owner sees its full address, but never the national ID, which the account does not keep (UC-21). Paying again stays on the tracking link (UC-14): the detail has no "Pagar".
  - Another customer's order, a guest order and an unknown id all get `404` `ORDER_NOT_FOUND` (decided): Row Level Security hides them, so the answer never tells whether the order exists. That is the "access denied" of the acceptance criterion.
  - Both answer `Cache-Control: no-store`, and `401` without a customer session.
  - Correo Argentino's tracking (decided): Correo has no stable public link that carries the number, so the detail links to its tracking page (`https://www.correoargentino.com.ar/formularios/e-commerce`, new tab) next to the number and a "Copiar" button. The link lives in the storefront.
  - Storefront: `/cuenta/pedidos` lists the orders (number, date, status, total) with "Ver más" while `hasMore`; `/cuenta/pedidos/:id` shows the detail. The header shows a "Mis pedidos" link while signed in. A visitor who is not signed in is sent to `/cuenta/ingresar`; an order not found shows "No encontramos tu pedido".
- Linking guest orders happens only after the email is verified, so nobody sees someone else's orders by registering an email that is not theirs (T-E4-08).
- Only orders placed as a guest are linked: an order unlinked by an account deletion (UC-28) is never linked again, even when the same email registers again.
- **Linking guest orders (T-E4-08):**
  - When (decided): at every sign-in of a customer whose email is verified (password now, an external provider from T-E4-14), through a sign-in listener of `platform` (`SignInListener`) that `ordering` implements, as the cart merge does (T-E4-04). Verifying the email or resetting the password never signs in (T-E4-02, T-E4-03), so the customer sees the orders from its first sign-in after verifying; linking at each sign-in also brings the guest orders placed later with that email, for example while signed out. An unverified account (a Facebook one before it confirms, UC-27) links nothing. The administrator's sign-in links nothing.
  - Which orders: every order without a user, in any status, whose buyer email, as last corrected (UC-17), is the account's email; both are normalized (trimmed, lower case). Linking sets the order's `user_id` and `updated_at`, and changes nothing else. It is idempotent: a linked order is no longer a guest order.
  - How the order records an unlinking (decided): V33 adds `orders.unlinked_at`, null until the account deletion of UC-28 (T-E4-16) sets `user_id` to null and `unlinked_at` to that instant. Only orders with `user_id` and `unlinked_at` both null are linked. Until T-E4-16 nobody may change `unlinked_at`.
  - It runs as `SYSTEM` (`LinkGuestOrders`, in its own transaction, before the session opens, from the inbound adapter `ordering.adapter.in.signin`: a sign-in hook, like authentication itself, not a customer-facing controller, RNF-27): `ordering` reads the account's verified email through `accounts.api.VerifiedEmails` and updates the orders over the partial index `orders_guest_email_idx` (V33). The update guard of `orders` (V14, widened by V33) lets `SYSTEM` change `user_id` only from null to a verified customer whose email is the order's, on an order never unlinked, with nothing else but `updated_at` changing, so a bug cannot attach orders to another account. Buyers still never change `user_id` (V24).
  - A failure of the linking fails the sign-in, as the cart merge's, and leaves no session open; nothing is answered about the orders linked: "Mis pedidos" reads them.

---

## UC-27 Register and sign in with Google or Facebook

**Actor:** Visitor · **Module:** `accounts` + `cart.api` + `notifications` · **Stage:** E4

**Main flow**
1. On the sign-in or registration screen, the visitor picks "Continuar con Google" or "Continuar con Facebook".
2. The system redirects it to the provider, which asks it to authorize access to its name and email.
3. On return, the system identifies the external account (provider identifier and email).
4. If that external account is already linked, it signs in.
5. If it is not linked and no account has that email, it creates a new account with the provider's name and email, without a password.
6. If it had a guest cart, it is merged into the account's cart (as in UC-19).

**Alternative flows**
- **An account with that email already exists:** if the provider confirms the email is verified (Google does), the external account is linked to the existing one. If not (Facebook), the system emails that address a single-use link (24 hours) to confirm the link; until then it does not sign in.
- **The provider returns no email** (for example a Facebook account registered with a phone): the system asks for the email and verifies it by email before creating the account. If an account already has that email, nothing is linked: its owner is told by email that someone tried, and can link the provider from "Mi cuenta" (decided, T-E4-14: a typed email proves nothing, so it never links an existing account).
- **The visitor cancels or the provider fails:** it goes back to the sign-in screen with a message, creating nothing.
- **From "Mi cuenta"**, the customer can link or unlink Google and Facebook, and create a password if it had none.

**Business rules**
- Providers of the first version: Google and Facebook. Apple comes in a later phase.
- Only the minimum permissions are asked: name and email. Nothing is published on the customer's behalf and the provider's access tokens are not stored; only its account identifier.
- An email that arrives verified by Google counts as verified (it enables linking guest orders, UC-22). A Facebook email is unverified until confirmed by email.
- The last sign-in method cannot be unlinked: a password or another provider must remain.
- The administrator cannot sign in with Google or Facebook: only with password and second factor (R-14, RNF-24).
- Social sign-in is not offered inside the checkout.

**Acceptance criteria**
- Given a visitor without an account, when it continues with Google, then an account is created with its name and its email marked as verified, and it is signed in.
- Given a customer with an account and a password, when it continues with Google using the same verified email, then the external account is linked to the existing one and no other account is created.
- Given a customer with an account, when it continues with Facebook using the same email, then it does not sign in until it confirms the link with the emailed link.
- Given a Facebook account without email, when the visitor types the email of an existing account, then nothing is linked and that account's owner gets a notice email.
- Given a link confirmation email, when its link is opened, then nothing is linked until the button of the page is pressed.
- Given a customer whose only sign-in method is Google, when it tries to unlink it, then it is refused and offered to create a password.
- Given a provider response with a `state` that does not match, when it reaches the system, then it is refused without creating or linking accounts.
- Given the administrator's account, when someone tries to sign in with Google or Facebook using its email, then the back office is not reached.

**Design notes**
- Spring Security OAuth2 Client (`oauth2Login`), Authorization Code with `state` and PKCE; for Google the OpenID Connect ID token is validated (signature, `aud`, `iss`, `nonce`) and `email_verified` is used. The email lookup uses the normalized email.
- Schema: `external_identity` (unique on `(provider, subject)` and on `(user_id, provider)`), the `LINK_CONFIRMATION` token purpose with the external account it links, and the `app_user` changes of social sign-in (T-E4-13, V34; see "Schema of social sign-in" above).
- Linking Google or confirming a Facebook link verifies an unverified account and drops its password (decided, T-E4-13).
- Endpoints: `GET /api/account/oauth2/authorization/{provider}`, `GET /api/account/oauth2/code/{provider}`, `GET /api/account/oauth2/providers`, `POST /api/account/oauth2/email`, `POST /api/account/link/confirm`, `POST /api/account/oauth2/link/{provider}`, `GET /api/account/identities`, `DELETE /api/account/identities/{provider}`, `PUT /api/account/password` (T-E4-14, T-E4-15).
- Rate limit: provider return 10 per minute per IP (RNF-22).
- **Sign-in with a provider (T-E4-14):**
  - Routes (decided): under `/api`, so Caddy and the Vite proxy reach them without changes: `GET /api/account/oauth2/authorization/{provider}` starts it and `GET /api/account/oauth2/code/{provider}` is the provider's return (`provider` is `google` or `facebook`). The redirect URI registered at each provider (T-E4-12) is `{PUBLIC_URL}/api/account/oauth2/code/{provider}`.
  - Spring Security `oauth2Login`, Authorization Code with `state` and PKCE (S256) for both providers. Google is OpenID Connect (`openid`, `profile`, `email`): the ID token is validated (signature, `iss`, `aud`, expiry, `nonce`) and its claims `sub`, `email`, `email_verified` and `name` are used. Facebook asks `public_profile` and `email` and reads `id`, `name` and `email` from the Graph API. The provider calls have 3 s to connect and 10 s to read (RNF-21). Nothing of the provider is kept: no access token (the authorized client is not stored) and the provider's authentication never becomes the session; only the customer's own session of UC-19 does.
  - Each provider is enabled by its client id and secret (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `FACEBOOK_CLIENT_ID`, `FACEBOOK_CLIENT_SECRET`); without them it is off, its start route gets `404`, and `GET /api/account/oauth2/providers` (`{"providers": ["GOOGLE"]}`, public) leaves it out, so the storefront shows only the buttons that work (decided: the Meta review can take longer than Google's).
  - Which email counts as verified: only Google's with `email_verified` true. Facebook's never does (UC-27).
  - On return, as `SYSTEM` in one transaction (`SignInWithExternalAccount`):
    1. The external account `(provider, subject)` is linked: its customer signs in, verified or not. The identity keeps the email it was linked with; a later change at the provider changes nothing.
    2. Not linked, no account has the email: a customer is created with the provider's name (trimmed, cut to 100 characters; the part of the email before `@` when the provider gives none) and the email, without a password, and the external account is linked to it. With Google's verified email it is created verified and signs in. With Facebook's (decided) it is created unverified and signs in, and the verification email of UC-19 is enqueued: until it is verified it links no guest orders (UC-22).
    3. Not linked, a customer has the email and has no account of that provider: with Google's verified email the external account is linked and the customer signs in; an unverified account is verified and loses its password in that same update (T-E4-13). With Facebook's, nothing is linked and it does not sign in: the link confirmation email is enqueued (below) and the visitor is told to open it.
    4. The account of the email is the administrator's: nothing is created or linked, and the visitor gets the same answer as a provider failure (R-14). A customer that already has another account of that provider gets `already-linked`.
    5. No email (a Facebook account registered with a phone): the external account (provider, subject and name) waits in the session for 15 minutes and the visitor is sent to `/cuenta/completar-email`.
  - A sign-in runs the `SignInListener`s of UC-19 (cart merge, guest orders) and opens the same session as the password sign-in, with a new session id and CSRF token. The provider's lock rule does not apply: the lock of UC-19 counts wrong passwords, and a provider sign-in neither is refused by it nor clears it (decided).
  - Where the browser goes (the storefront reads `social` from the query): signed in, `/`; link confirmation sent, `/cuenta/ingresar?social=confirm-link`; email needed, `/cuenta/completar-email`; the visitor cancelled at the provider, `/cuenta/ingresar?social=cancelled`; another account of the provider already linked, `/cuenta/ingresar?social=already-linked`; anything else (an invalid `state`, a provider error, the administrator's email, a race between two sign-ins), `/cuenta/ingresar?social=failed`, creating and linking nothing.
  - **Link confirmation (decided):** the external account to link waits in `pending_link` (V35: one row per customer, `user_id` the key and the foreign key, with `provider`, `subject` and `requested_at`), so the token is minted when the email is sent, as UC-19's, and its 24 hours count from the sending. The email (`LINK_CONFIRMATION` in the outbox, the user as reference, a new version for each new request unless one is waiting) carries the link `/cuenta/vincular#{token}`. Row Level Security: only `SYSTEM` reads, writes and deletes `pending_link`, and only for customers.
  - **The first request wins (decided, security review of T-E4-14):** while a customer's pending link is less than 24 hours old (`requested_at`), a request for another external account changes nothing and sends nothing, with the same answer; a request for the same external account refreshes `requested_at` and enqueues the email again. An older pending link is replaced. Whoever starts a request for someone else's email can at most delay the owner's own request, never swap the account it links: the email's token names the account of the pending link it was minted from.
  - `POST /api/account/oauth2/email` (`email`), public, with the CSRF token, in the `account-email` rate limit of registration and recovery (it sends an email): the email of the external account waiting in the session. `204`, the same whatever the email, which never says whether an account has it; `422` `INVALID_EMAIL`; `410` `EXTERNAL_SIGN_IN_EXPIRED` without a waiting external account. As `SYSTEM` (decided, security review of T-E4-14: an email typed by the visitor proves nothing, so it never links an existing account):
    1. No account has the email: an unverified customer without a password is created with the provider's name, with no external account yet, a pending link of that external account and the link confirmation, so nobody signs in until the email is confirmed.
    2. A customer has the email and a pending link of that same external account (the visitor repeats step 1): the first request wins (above) refreshes it and the link confirmation is enqueued again.
    3. Any other customer with the email: nothing is linked or kept waiting. The customer gets the link attempt notice (`LINK_ATTEMPT_NOTICE` in the outbox, the user as reference, a new version for each attempt unless one is waiting; the type joins `LINK_CONFIRMATION` in V35): someone tried to connect a Facebook account to its email; if it was the customer, it signs in and links it from "Mi cuenta" (T-E4-15), and otherwise it can ignore the email. It carries no token, only a link to `/cuenta/ingresar`. A customer that already has an account of that provider gets nothing.
    4. The administrator's email gets nothing (R-14).
    In every case the session forgets the external account.
  - `POST /api/account/link/confirm` (`token`), public, with the CSRF token: `204`; `410` `INVALID_LINK` for an unknown, used, expired token or one of another purpose; `409` `EXTERNAL_ACCOUNT_ALREADY_LINKED` when the external account is linked to someone else or the customer already has another account of that provider. As `SYSTEM` bound to the token's hash: it marks the token used, links the external account with the account's email, verifies an unverified account and drops its password in that same update (T-E4-13), and forgets the pending link. A second confirmation of an account already linked to that customer is `204`. It does not sign in (decided): `/cuenta/vincular` says the account is linked and offers "Continuar con Facebook".
  - **`/cuenta/vincular` asks before linking (decided, security review of T-E4-14):** opening the page never uses the token, so neither a mail scanner that runs scripts nor a curious click links anything. The page says that a Facebook account will be connected to the store account of this email, with the warning that whoever did not ask for it should not continue, and only the "Vincular mi cuenta de Facebook" button sends `POST /api/account/link/confirm`. Only Facebook sends link confirmations (Google vouches for its emails), so the page names Facebook without asking the server; the token in the fragment is never sent until the button is pressed.
  - Storefront: `/cuenta/ingresar` and `/cuenta/registro` show "Continuar con Google" and "Continuar con Facebook" for the enabled providers, and the message of each `social` value; `/cuenta/completar-email` asks for the email and then always says the same thing: check the inbox of that email; `/cuenta/vincular` confirms the link with the button. Not in the checkout (UC-27).
  - Known risk (accepted for v1): an account created by Facebook's unverified email keeps its external account when the email's real owner later recovers the password; Facebook only returns confirmed emails, and T-E4-15 lets the owner unlink it. The same holds for an account created without email (case 1 above) whose owner presses the button of a link confirmation it did not ask for, despite the warning.
- **"Mi cuenta" (T-E4-15):**
  - Only the signed-in customer, on its own account (`CUSTOMER` role, as the rest of `/api/account/**`); the writes require the CSRF token.
  - `GET /api/account/identities`: `200` with `hasPassword` and `identities` (`provider`, `email`, the one it was linked with, and `linkedAt`), Google first; `Cache-Control: no-store`. Read as the customer: Row Level Security (V34) shows it only its own.
  - **Linking from "Mi cuenta" (decided):** the session proves the store account and the provider proves the external account, so the provider's email does not have to be the account's. `POST /api/account/oauth2/link/{provider}` (`google` or `facebook`): `204`, and the session remembers for 15 minutes that this customer wants to link that provider; `404` `PROVIDER_NOT_ENABLED` for a provider that is not enabled. Only then does the storefront navigate to `/api/account/oauth2/authorization/{provider}`. The intent is a `POST` with the CSRF token, so another site can never make a signed-in browser link an account. On the provider's return, a waiting intent of the same provider whose customer is still the one signed in links instead of signing in (`LinkExternalAccount`, as `SYSTEM` in one transaction, as the sign-in); the session stays as it was, and the intent is forgotten in every case. Otherwise the return is the sign-in of T-E4-14.
    1. The external account is already linked to this customer: nothing changes, `linked`.
    2. Linked to another customer, or the customer already has another account of that provider: nothing changes, `already-linked`.
    3. Otherwise it is linked with the provider's email, or the account's when the provider gives none (as `POST /api/account/link/confirm`): `linked`. Google's verified email that is the account's verifies an unverified account, which loses its password in that same update (the rule of T-E4-13).
    Where the browser goes: `/cuenta?social=linked`, `already-linked`, `cancelled` (at the provider) or `failed` (anything else, or the customer's account no longer exists).
  - `DELETE /api/account/identities/{provider}` (`google` or `facebook`): `204`. `404` `IDENTITY_NOT_FOUND` when the customer has none of that provider; `409` `LAST_SIGN_IN_METHOD` when it is the only way in left: no password and no other provider. A password counts even when the email is unverified (decided): its sign-in sends a new verification email (UC-19). As the customer (V34 lets it unlink its own), after locking its `app_user` row, as the address book does (UC-20), so two concurrent unlinks never leave the account without a way in. Unlinking never ends a session.
  - `PUT /api/account/password` (`password`): creates the password of an account without one (decided: only creates; changing it stays with the recovery of UC-19). `204`; `409` `PASSWORD_ALREADY_SET`; `422` `PASSWORD_TOO_SHORT` or `PASSWORD_TOO_LONG`, the customer's rules of UC-19. The session is enough: no other credential is asked. It is checked and hashed before the account row is locked; as the customer (V29 lets it set its own password, never remove it). It does not verify the email.
  - Storefront: `/cuenta` ("Mi cuenta") shows the name and email and the sign-in methods: the password ("Crear contraseña" while there is none) and each provider, linked ("Desvincular") or not ("Vincular", only for the enabled providers; a linked provider that is no longer enabled can still be unlinked). Unlinking asks for a confirmation; the `409` `LAST_SIGN_IN_METHOD` says why and offers to create a password. It shows the message of each `social` value. The header shows a "Mi cuenta" link while signed in; a visitor who is not signed in is sent to `/cuenta/ingresar`.

---

## UC-28 Delete my account and download my data

**Actor:** Customer · **Module:** `accounts` + `ordering` + `reviews` · **Stage:** E4

**Main flow**
1. From "Mi cuenta", the customer asks to download its data or to delete its account.
2. **Download:** it receives a file with its profile, addresses, orders and reviews.
3. **Deletion:** it confirms with its password or by signing in again with its provider. The system deletes the user, its addresses, its linked external accounts and its cart, and ends all its sessions.

**Business rules**
- It answers the rights of access and deletion of Law 25,326 on personal data protection.
- Orders are kept because the shop must keep them for accounting and tax duties: they are unlinked from the account, but keep their copy of name, national ID and address for the legal term (*confirm the term with the accountant*).
- Reviews are deleted with the account (decided in T-E5-01; it replaces the proposed anonymization).
- The data deletion instructions page that Meta requires for Facebook Login points to this use case.

**Acceptance criteria**
- Given a customer with orders, when it deletes its account, then it can no longer sign in, its addresses do not exist and its orders are still visible in the back office without a link to the account.
- Given a customer, when it asks to download its data, then it receives its data and none of another customer.
- Given a customer who deletes its account and registers again with the same email, when it signs in, then it does not see the previous orders.

**Design notes**
- `accounts` publishes `CustomerAccountDeleted` (the user id only); `ordering` unlinks the orders (`user_id` to null) and `reviews` deletes the reviews (E5), each with an idempotent handler.
- Endpoints: `GET /api/account/data`, `POST /api/account/oauth2/reauthenticate/{provider}`, `DELETE /api/account` (T-E4-16).
- Only the signed-in customer, on its own account (`CUSTOMER` role, as the rest of `/api/account/**`); the writes require the CSRF token. The administrator's account is never deleted or exported this way.
- **Data download (T-E4-16):**
  - `GET /api/account/data`: `200` `application/json` with `Content-Disposition: attachment; filename="mis-datos.json"` and `Cache-Control: no-store`; one JSON file (decided). It holds `exportedAt`, `profile` (`email`, `name`, `phone`, `emailVerified`, `hasPassword`, `createdAt`), `identities` (`provider`, `email`, `linkedAt`), `addresses` (as UC-20), `orders` and `reviews`.
  - **Sections of other modules (decided):** `accounts` depends on no module, so it cannot ask `ordering` or `reviews`. `accounts.api` publishes the port `CustomerDataContributor` (a section of `CustomerDataSection`, `ORDERS` or `REVIEWS`, and a list of JSON-ready records for a customer); `ordering` implements `ORDERS` now and `reviews` will implement `REVIEWS` in E5. Every section is always in the file, as an empty list when no module contributes it, so until E5 `reviews` is `[]`. The dependencies point to `accounts`, as `ordering → accounts` already does.
  - `orders`: every order of the customer, newest first, as the detail of UC-22: number, date, status, items, amounts, coupon code, the copy of name, email, phone, national ID and address, the shipment tracking number and the delivery date. The national ID goes in the file: it is the customer's own data (right of access).
  - Everything is read in one read-only transaction as the customer, so Row Level Security shows only its own rows (acceptance criterion 2). Nothing about the file is logged. The lines of every order are read in one query. Rate limit (RNF-22): 5 per minute per customer (`account-data`, the session's user id).
  - Every contributor passes the shared contract of `CustomerDataContributor` (a fixed section, a list never null, only the given customer's records under its own Row Level Security), so the one of `reviews` in E5 inherits it.
- **Confirming the deletion (T-E4-16):** with the password, or by signing in again with a linked provider (decided). Either works for an account that has both.
  - Provider: `POST /api/account/oauth2/reauthenticate/{provider}` (`google` or `facebook`): `204`, and the session remembers for 15 minutes that this customer wants to confirm with that provider; `404` `PROVIDER_NOT_ENABLED`; `404` `IDENTITY_NOT_FOUND` when the customer has no account of that provider. Then the storefront navigates to `/api/account/oauth2/authorization/{provider}`, as linking does (T-E4-15). On the return, a waiting intent of the same provider whose customer is still the one signed in is a reauthentication, not a sign-in or a link: when the external account that came back is the one linked to this customer, the session records it for 5 minutes and the browser goes to `/cuenta?social=reauthenticated`; any other account, or the provider failing, goes to `/cuenta?social=reauthentication-failed`, and a cancellation to `reauthentication-cancelled` (their own values, so the page never shows the messages of a link). Nothing is linked or changed, and the session stays as it was. A waiting link intent and a waiting reauthentication replace each other. Which providers it may confirm with is decided by the use case `RequestReauthentication`, not the controller.
    - **A fresh sign-in at the provider (decided):** a reauthentication asks the provider for the credentials again, even when the browser is already signed in there: Google with `prompt=login` and `max_age=0`, Facebook with `auth_type=reauthenticate`. Only for this intent: linking and signing in stay as they were.
  - `DELETE /api/account` (`password`, optional): with a `password`, it must be the account's (`403` `WRONG_PASSWORD`); BCrypt runs before any row is locked. Without it, the session must hold a reauthentication of less than 5 minutes, used up by the deletion (`403` `REAUTHENTICATION_REQUIRED`). A wrong password does not count toward the lock of UC-19, so a stolen session cannot lock the owner out; it is rate-limited instead (below).
- **Deletion (T-E4-16):**
  - One transaction as the customer: it locks its `app_user` row, deletes it and publishes `CustomerAccountDeleted` (`platform.domain.DomainEvent`, the user id only). Its addresses, tokens, external identities and waiting link go with it through their foreign keys (`ON DELETE CASCADE`).
  - **Listeners in the same transaction (decided):** the exception of rule 5 of ARCHITECTURE.md, as every listener of the codebase: deleting the account, unlinking the orders and deleting the cart commit together or not at all. Each is idempotent: a second run finds nothing to change.
    - `ordering` unlinks every order of the customer: `user_id` to null and `unlinked_at` to now (the `Clock`), whatever its status. An unlinked order is never linked again (T-E4-08), so a new account with the same email does not see it (acceptance criterion 3). The back office still shows it, with its copy of the buyer's data.
    - `cart` deletes the customer's cart with its items.
    - `reviews` deletes the customer's reviews when it arrives in E5 (decided in T-E5-01, UC-24).
  - `204` with no body: the response signs out (as `POST /api/account/logout`) and, after the commit, every other session of the account ends (Spring Session, by principal name, as the password reset). The emails still waiting in the outbox for the account are not sent (the rule of UC-19 for an account deleted).
  - Row Level Security (V36):
    - `app_user`: the customer deletes its own row, only a customer's (`role = 'CUSTOMER'`).
    - `orders`: no policy lets a customer update its orders (once `user_id` is null the row would no longer pass its select policy, which PostgreSQL also checks on an updated row). The function `unlink_deleted_customer_orders`, owned by `tienda_owner` (BYPASSRLS, as `lock_withdrawable_order` of V26), unlinks them: only the orders of the transaction's customer, and only once its `app_user` row no longer exists, which it checks seeing every user; so a customer can never unlink its orders and keep its account. The buyer guard of V24 lets that update change only `user_id` to null and `unlinked_at` to a value. The guard of the administrator and the system (V33) still keeps `unlinked_at` unchanged.
  - Rate limit (RNF-22, RNF-29.5): `DELETE /api/account` 5 per minute per IP (`account-deletion`) and 5 every 15 minutes per customer (`account-deletion-user`, the session's user id, never the email), for the password guesses: a stolen session that rotates its IP is still limited.
  - The account row the use case deletes must be there: deleting none (the account vanished in between) answers `NotSignedIn` and publishes nothing.
- Storefront: "Mi cuenta" gets "Tus datos", with "Descargar mis datos" (it saves the file) and "Eliminar mi cuenta". Deleting opens a confirmation that explains what is deleted and that the orders are kept for the shop's legal duties without the account; it asks for the password when the account has one and offers "Confirmar con Google" or "Confirmar con Facebook" for each linked provider that is enabled. Back from the provider with `social=reauthenticated`, the confirmation opens ready to delete without a password. After the deletion the store goes to the home page with the message "Tu cuenta fue eliminada".
- The data deletion instructions page that Meta requires is published with the legal pages (T-E4-12).
