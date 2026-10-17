# E4 – Customer account

**Status:** Draft · **Use cases:** UC-19 to UC-22, UC-27 and UC-28 · **Spanish counterpart:** `docs/specs/E4-cuenta-cliente/spec.md`

**Modules involved:** `accounts`, `cart.api`, `ordering.api`, `notifications`, `reviews` (E5), `platform`.

Registering is optional: buying as a guest stays the main flow. An account saves the buyer's data and addresses, lists its orders, and can be deleted with its data downloaded first.

**Decisions settled before the stage (T-E4-01):**
- The national ID is **not** stored on the account: the checkout asks for it on every purchase and it lives only on the order (UC-21, R-05). The account and its address book hold less personal data.
- Single-use email links: the email verification link lasts 24 hours, the password reset link 30 minutes and the Facebook link confirmation (UC-27) 24 hours. Their tokens follow RNF-29.6: 32 random bytes from `SecureRandom`, stored only as SHA-256.
- The address book holds at most 10 addresses per customer; an 11th is refused with `422`.
- The customer's phone is an optional column of the account (8 to 15 digits, as on the order). How it gets filled for the prefilled checkout is settled in T-E4-06.

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
- Deleting the account (UC-28, T-E4-16) brings its own policy; its addresses and tokens go with it through their foreign keys.

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

**Acceptance criteria**
- Given a customer with a primary address, when it opens the checkout, then that address is selected.
- Given a customer who completes a purchase, when it finishes, then the order is linked to its account.

**Design notes**
- The order already has `user_id` and the customer insert policy (T-E3-01). Details in T-E4-06.

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
- Linking guest orders happens only after the email is verified, so nobody sees someone else's orders by registering an email that is not theirs (T-E4-08).
- Only orders placed as a guest are linked: an order unlinked by an account deletion (UC-28) is never linked again, even when the same email registers again. T-E4-08 settles how the order records it.

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
- **The provider returns no email** (for example a Facebook account registered with a phone): the system asks for the email and verifies it by email before creating the account.
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
- Given a customer whose only sign-in method is Google, when it tries to unlink it, then it is refused and offered to create a password.
- Given a provider response with a `state` that does not match, when it reaches the system, then it is refused without creating or linking accounts.
- Given the administrator's account, when someone tries to sign in with Google or Facebook using its email, then the back office is not reached.

**Design notes**
- Spring Security OAuth2 Client (`oauth2Login`), Authorization Code with `state` and PKCE; for Google the OpenID Connect ID token is validated (signature, `aud`, `iss`, `nonce`) and `email_verified` is used. The email lookup uses the normalized email.
- Schema: `external_identity` (unique on `(provider, subject)`) and the `LINK_CONFIRMATION` token purpose (T-E4-13).
- Endpoints: `GET /oauth2/authorization/{provider}`, `GET /login/oauth2/code/{provider}`, `POST /api/account/link/confirm`, `GET` and `DELETE /api/account/identities`, `PUT /api/account/password` (T-E4-14, T-E4-15).
- Rate limit: provider return 10 per minute per IP (RNF-22).

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
- Reviews stay published without a name (*Proposed*).
- The data deletion instructions page that Meta requires for Facebook Login points to this use case.

**Acceptance criteria**
- Given a customer with orders, when it deletes its account, then it can no longer sign in, its addresses do not exist and its orders are still visible in the back office without a link to the account.
- Given a customer, when it asks to download its data, then it receives its data and none of another customer.
- Given a customer who deletes its account and registers again with the same email, when it signs in, then it does not see the previous orders.

**Design notes**
- `accounts` publishes `CustomerAccountDeleted` (the user id only); `ordering` unlinks the orders (`user_id` to null) and `reviews` anonymizes the reviews, each with an idempotent handler.
- Endpoints: `GET /api/account/data`, `DELETE /api/account` (T-E4-16).
