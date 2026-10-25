# E7 – The storefront as in the prototype

**Status:** Draft · **Use cases:** UC-30, UC-31 and UC-32, and changes to UC-01, UC-02, UC-03, UC-08 and UC-13 · **Spanish counterpart:** `docs/specs/E7-tienda-prototipo` (spec, plan and tasks)

**Modules involved:** `catalog` (three read changes and the home banner, UC-32), `ordering` and `notifications` (first and last name, D-13), `shipping` (read only, unchanged); the rest is the frontend.

## Goal

Make the storefront look and flow like the approved prototype (`docs/design/prototype/index.html`). E1 to E6 took its tokens and a few layouts but not its sections. The side-by-side screenshots of 2026-10-19 (home, listing, product, cart, checkout; phone and desktop) show the gap.

No business rule changes except the ones listed under *Decisions*. Nothing in the prototype that promises something to the buyer (installments, discounts, free exchanges) is shown until the owner decides it.

**Out of scope:** the prototype's code (inline scripts, Google Fonts, Unsplash photos: the CSP of T-E6-03, the self-hosted font of ARCHITECTURE.md section 11, image licenses); the wishlist heart (no use case); editing the carousel, the audience photos or the logo from the back office (D-03); installments and the transfer discount until decided (D-01, D-02).

## Decisions

Decided with the owner on 2026-10-19 and written into `docs/specs/00-dominio-y-reglas.md`, except D-01, D-02 and D-15, still open. Nothing that depends on an open one is coded until it is settled.

| ID | Topic | Status | Decision |
|---|---|---|---|
| D-01 | "3 cuotas sin interés" | Open | Not shown until decided. MercadoPago sets the installments, the shop pays their cost, and RNF-30.4 requires showing the total financial cost when installments are shown. Revisited before go-live. |
| D-02 | "10% OFF por transferencia" | Open | Not shown until decided. Bank transfer is not a payment method today (immediate methods only: MercadoPago and MODO); adding it would be a new use case. |
| D-03 | Source of the home texts and photos | Decided | The character banner is edited in the back office (UC-32). The carousel, the audience photos and the logo are fixed in the frontend (photos in the build, texts in `es.ts`); changing them needs a deploy. |
| D-04 | Color swatches | Decided | A frontend map from color name to tone; an unknown color keeps its text label. |
| D-05 | "Nuevo" label | Decided | Products created in the last 30 days, computed on the server with the injected `Clock`. |
| D-06 | The card's "+" button | Decided | Dropped: adding needs a size and a color, and the card already opens the product. |
| D-07 | Shipping by postal code (product page) | Decided | A province select showing that province's cost: shipping is priced per province (UC-12). |
| D-08 | "Completá tu look" | Decided | Up to 4 products of the same category, newest first, without the open product, from the existing listing. |
| D-09 | "Contactanos" | Decided | Email and WhatsApp links, no form. |
| D-10 | Numbered pagination | Decided | Keep "Ver más" (UC-01). |
| D-11 | "N productos" count | Decided | Added: the listing returns the total of matches. |
| D-12 | Trust icons | Decided | "Envío gratis desde $X" only with a threshold, and "Pagá con MercadoPago". "Primer cambio sin cargo" is not shown: it is not a confirmed policy. |
| D-13 | First and last name in checkout | Decided | Two fields, "Nombre" and "Apellido", through the checkout, the API and the order. This changes UC-13's decision; see *Changes to UC-13*. |
| D-14 | Home carousel | Decided | Up to 3 slides, auto-advancing every 6 s with a pause button (WCAG 2.2.2), no auto-advance under `prefers-reduced-motion`. |
| D-15 | Business materials | Open (owner) | The owner provides the logo, up to 3 carousel photos, the 3 audience photos, the first character-banner photo (then changed in the back office), the contact email and WhatsApp number, and the Instagram and TikTok links. Missing photos show the prototype's gradient background. Received on 2026-10-23: the logo. The cropped sign is the header logo (`src/assets/brand/logo-{48,96,144}.webp`: the white outside the frame made transparent and the bits of characters in its corners erased, 48 px tall as in the prototype, alternative text the store name); the full illustration is the first character-banner photo, uploaded in the back office ("Portada", UC-32). |
| D-16 | Motion | Decided | The Motion library (`motion`, its React entry `motion/react`), loaded after first paint, on the tokens of the "Motion" section. |
| D-17 | Typography | Decided | Inter replaces Quicksand for headings and body text, in the store and the back office (owner, 2026-10-24), self-hosted with `@fontsource/inter` at weights 500, 600 and 700 (ARCHITECTURE.md section 11). |

---

## Motion (D-16)

The prototype feels friendly because it answers the shopper: the cart icon bumps on add, panels slide, notices rise. E7 makes that one system with shared tokens instead of one-off effects. Part of it exists already (the cart bump and slide-in in `cart.css`, the skeleton pulse and filter slide-in in `catalog.css`); those move onto the tokens.

**Rules**
- **Motion answers an action** (add, open, choose, go to the next step) and shows what changed. Nothing moves by itself except the carousel (D-14).
- **Entrances:** the home welcome text enters once on load; product cards enter when they join the grid (its first page, "Ver más", a new filter or sort), never because the page scrolled to them; a new page enters on a route change, never on the first load of the store. Sections do not fade in on scroll (T-E7-17).
- **Short and soft:** 120 to 450 ms (the home entrance up to 700 ms), easing that slows on arrival.
- **Accessible:** movement and scaling only under `@media (prefers-reduced-motion: no-preference)`; under `reduce`, at most a short opacity change. Hover effects only under `@media (hover: hover)`.
- **Motion, without delaying the first load (D-16):** enter and exit animations, step changes and pops use the Motion library, loaded after first paint; until it loads, everything shows in place, unanimated. Simple effects (hover, color changes) stay in CSS. Only `transform` and `opacity` are animated, never layout properties. The home LCP image is never animated (RNF-11).

**Catalog**

| Where | Motion | Tokens |
|---|---|---|
| Home welcome | Title, text and button rise 12 px and fade in, staggered 80 ms, once | `--dur-entrance`, `--ease-out` |
| Carousel | Crossfade between slides | `--dur-slow`, `--ease-standard` |
| Product card (hover devices) | Photo scales to 1.03; a second photo, if any, crossfades in (UC-01) | `--dur-base`, `--ease-out` |
| Header (below 860 px) | Slides up out of view while scrolling down, back while scrolling up (UC-31) | `--dur-base`, `--ease-standard` |
| Swatches and size buttons | The selection ring grows in | `--dur-fast`, `--ease-pop` |
| Add to cart | Button shows "Agregado" with a check for 1.5 s; cart icon bumps once; the notice rises from the bottom | `--dur-base`, `--ease-pop` |
| Cart panel, menu, filters, lightbox | Slide from their edge over a fading overlay; reverse on close | `--dur-panel`, `--ease-standard` |
| Phone menu contents | The logo, then each navigation link, slide 12 px in from the left and fade in, 40 ms apart, while the panel opens (T-E7-17) | `--dur-base`, `--ease-out` |
| Product cards joining the grid | Rise 12 px and fade in, 40 ms apart within a page (at most 6 steps); cards already shown do not move (T-E7-17) | `--dur-base`, `--ease-out` |
| Free-shipping bar | Fills smoothly (`transform: scaleX`); reaching the threshold pops the "¡Tenés envío gratis!" message | `--dur-slow`, `--ease-pop` |
| Product page sections | Expand and collapse (`grid-template-rows: 0fr → 1fr`) | `--dur-base`, `--ease-standard` |
| Checkout steps | The new step rises 12 px and fades in | `--dur-base`, `--ease-out` |
| Loading | Skeletons pulse | 1.2 s loop |
| Route change | New page content rises 12 px and fades in; not on the first load (T-E7-17) | `--dur-base`, `--ease-out` |

**Acceptance criteria**
- Given reduced motion, when the shopper adds a product, opens the cart or changes checkout step, then nothing translates or scales.
- Given a product added from the product page, when the API answers `200`, then the cart icon bumps once and the notice appears; a rejected add does neither.
- Given the home page, when Lighthouse measures it, then LCP stays under 3 s and Cumulative Layout Shift under 0.1.
- Given a touch device, when a card is tapped, then no hover effect sticks.

**Design notes**
- Tokens in `tokens.css`, listed in ARCHITECTURE.md section 11: `--dur-fast` 120 ms, `--dur-base` 240 ms, `--dur-panel` 320 ms, `--dur-slow` 450 ms, `--dur-entrance` 700 ms; `--ease-out` `cubic-bezier(.2, .7, .2, 1)`, `--ease-standard` `cubic-bezier(.4, 0, .2, 1)`, `--ease-pop` `cubic-bezier(.34, 1.56, .64, 1)`. The first two come from the prototype.
- Motion setup: `motion/react` with `LazyMotion` and the `m` components, its `domAnimation` features imported on demand (`features={() => import(...)}`), so the first bundle grows by the `m` runtime only (budget: 17 kB gzipped in the first load; T-E6-02 checks LCP). The `m` core shares modules with the features, so about half of the shared code ships with it: measured on 2026-10-19, 16.7 kB in the first load and 14.5 kB in the features chunk, the same with 13.5.1 and 14.0.0. `MotionConfig reducedMotion="user"` turns transforms off under reduced motion and keeps opacity. Pinned at `motion` 13.5.1 by T-E7-12 (CLAUDE.md: pinned, not ranges).
- Until the features load (and in tests without `MotionProvider`), elements show in place, unanimated, and exits remove at once. Components animate through `useActionMotion` (enter from a hidden state and leave back to it; under reduced motion a short fade only), `PanelDialog` (side panels) and `Toast` (notices) in `frontend/src/components/motion`; collapsible sections through `Collapsible` in CSS.
- The tokens live once in CSS and once in `frontend/src/styles/motion.ts` (seconds and cubic-bezier arrays for Motion); `tokens.test.ts` checks that both agree.
- Exits run inside `AnimatePresence`; a `dialog` closes only on `onExitComplete`, so focus returns after the animation.
- Motion writes styles through the CSSOM (`element.style`), which the CSP of T-E6-03 allows; the CSP check of `npm run lighthouse` confirms no violation.
- `tokens.test.ts` checks the token values; component tests check the classes under both `prefers-reduced-motion` values (`matchMedia` mocked).

---

## UC-30 Home page

**Actor:** Visitor · **Module:** frontend (reads `catalog` and `shipping`) · **Stage:** E7

**Preconditions:** none.

**Main flow**
1. The visitor opens `/`.
2. The system shows, in this order: the welcome carousel, the character banner, "Elegí para quién", "Todos los productos" (the UC-01 grid with the UC-02 filters) and "Contactanos". The withdrawal link (UC-29) is in the footer, as on every page (RNF-30.1; the home page's own block was removed with T-E7-20).
3. "Descubrí la colección" scrolls to "Todos los productos" and moves the focus to its heading.
4. Each "Elegí para quién" card (Mujer, Hombre, Niños) opens the listing filtered by that audience.

**Alternative flows**
- A photo is missing (D-15): its place shows the gradient background, with the same size, so nothing shifts.
- The character banner is still loading: its place shows the gradient background at the banner's size, so the sections below do not move when it arrives. If none is published, or it cannot be loaded, the place closes (T-E7-11).
- The contact email or WhatsApp number has not been sent yet (D-15): its link is not shown; with neither, there is no "Contactanos" section.
- The URL carries filters or a sort: the page opens scrolled to "Todos los productos".

**Business rules**
- The carousel and the audience cards have fixed texts and photos (D-03); the character banner is the one the administrator publishes (UC-32). Without a published banner, its section is not shown.
- "Todos los productos" carries the line "Filtrá por talle, color, temporada y más." below its heading, as in the prototype (T-E7-11).
- The audience cards set the UC-02 URL key `publico` (`mujer`, `hombre`, `infantil`) and keep the sort.
- `/categoria/{slug}` keeps showing only the listing, without the home sections.
- The carousel follows D-14.

**Acceptance criteria**
- Given the home page on a phone, when it loads, then the carousel, the character banner, "Elegí para quién", the grid and the contact section appear in that order, with no withdrawal block of their own: the withdrawal link is in the footer.
- Given the home page, when "Mujer" is chosen in "Elegí para quién", then the grid is filtered by audience `WOMEN` and the URL has `publico=mujer`.
- Given the character banner still loading, when the home page shows, then its place holds the banner's size, without texts or link.
- Given a visitor with reduced motion, when the home page opens, then the carousel does not advance by itself.
- Given the carousel advancing, when the pause button is pressed, then it stops; pressing it again resumes it.
- Given the home page, a listing, a product page and the checkout, when Lighthouse measures them, then RNF-11 (LCP under 3 s) and RNF-31.4 (accessibility 90 or more) still hold.

**Design notes**
- `HomePage` (`features/catalog`) at `/`, composing `HeroCarousel`, `CharacterBanner`, `AudienceCards`, `CatalogListing` (the listing of `CatalogPage`, extracted so both routes use it) and `ContactSection` (added by T-E7-03). It stays in the main bundle (T-E6-02). On `/` the grid starts below the first screen, so its photos load lazily and the carousel's first photo is the only one loaded first.
- The fixed slides and audience cards are listed in `homeContent.ts`, each with its gradient tone and, once the business sends it (D-15), its photo. Opened with filters or a sort, the page scrolls to the listing, and again once the banner has its place above it.
- Photos in `frontend/src/assets/home/` as WebP at 640, 1280 and 1920 px, with `srcset` and `sizes`. The first slide is `fetchpriority="high"` and preloaded; the others are lazy. The carousel is a `section` with `aria-roledescription="carrusel"`, slides labeled "n de 3", and the pause button first in tab order (not shown under reduced motion, where nothing advances). The slides crossfade in CSS (`opacity` only); the welcome text enters through `useActionMotion`.
- The banner comes from `GET /api/home/banner` (UC-32); the rest of the page needs no API change.

---

## UC-31 Header, menu and footer

**Actor:** Visitor · **Module:** frontend (reads `shipping`) · **Stage:** E7

**Preconditions:** none.

**Main flow**
1. Every public page shows the header: logo (link to `/`), navigation (Todo, Mujer, Hombre, Niños, Temporada Invierno), search (UC-26), "Ingresar" and "Crear cuenta" (or "Mi cuenta" with a session, UC-19) and the cart button.
2. From 860 px the navigation sits in the header; below, a menu button opens it with the account links in a side panel, headed by the store's logo (T-E7-17).
3. With a free-shipping threshold, a strip above the header says "Envío gratis desde $X".
4. The footer has three columns from 860 px, stacked below: Ayuda y legales (FAQ, exchanges and returns, terms and conditions), Contacto y redes ("Contacto", to the home page's "Contactanos", and Instagram and TikTok with their icons) and Medios de pago (one icon per enabled provider, in a row). Below them, after a top border, a strip with the withdrawal link (RNF-30.1), "Cookies" and "© {year} Pijama Party Santa Fe" (T-E7-20).
5. Right above the footer, a benefits bar (T-E7-21) with three equal blocks, each an icon on the left and, on its right, a bold uppercase title over a regular text: "Enviamos tu compra" (a truck; "Entregas dentro de Santa Fe Capital y todo el país"), "Todas las formas de pago" (a card; "Pagá con" and the enabled providers, e.g. "Pagá con MercadoPago o MODO") and "Comprá con seguridad" (a closed padlock; "Tus datos siempre protegidos"). From 860 px the blocks sit in a row with subtle vertical dividers; below, they stack with no dividers.

**Alternative flows**
- No threshold, or `GET /api/shipping/rates` fails: no strip.
- A footer page that does not exist yet (T-E6-06): its link is not shown.
- A social link the business has not sent yet (D-15): it is not shown. "Contacto" shows only when the home page has "Contactanos" (an email or a WhatsApp number, UC-30). With none of the three, there is no Contacto y redes column.
- No enabled provider, or `GET /api/checkout/payment-providers` fails: no Medios de pago column, and the benefits bar shows only its shipping and security blocks.

**Business rules**
- Navigation links: Todo → `/`; Mujer, Hombre, Niños → `/?publico=mujer|hombre|infantil`; Temporada Invierno → `/?temporada=invierno`. The current one is marked with `aria-current="page"`.
- A navigation link with a filter takes the home page to "Todos los productos", as when it opens with filters (UC-30).
- With a session, "Mi cuenta" opens a list with the greeting, "Mis pedidos" (UC-22), "Mis direcciones" (UC-20), "Mi cuenta" (UC-27) and "Salir"; Escape, a click outside or choosing a link closes it. The phone menu lists the same links open.
- The strip shows no installments or discounts (D-01, D-02).
- Seller data (RNF-30.2) joins the footer with T-E6-21; it is not part of this use case.
- The benefits bar promises no payment method, installments, discount or surcharge the checkout does not have (D-01, D-02): its payment block names only the enabled providers. The uppercase titles are written in sentence case and uppercased in CSS, so screen readers do not spell them.
- The payment icons are the store's own drawings (a card for MercadoPago, a phone for MODO), not the brands' logos; each carries the provider's name as its accessible name. The social icons are simple outline drawings, with the network's name as the link's accessible name.
- Below 860 px the header stays at the top of the screen but hides while the visitor scrolls down and comes back as soon as they scroll up (T-E7-15). It stays shown within its own height of the top of the page, while the phone menu, the search, the cart panel or the account list is open, and while the keyboard focus is inside it. From 860 px it never hides.
- Once the page has scrolled past the header's height, at any width, the header's background turns slightly transparent and blurred, so the page shows through it; within that height of the top it keeps its usual background (T-E7-16). The logo, the links and the icons keep their contrast.

**Acceptance criteria**
- Given a free-shipping threshold of $50,000, when any public page opens, then the strip reads "Envío gratis desde $50.000"; without a threshold, there is no strip.
- Given a phone, when the visitor scrolls down past the header, then it hides; when they scroll up, then it shows again. Given a computer, when the visitor scrolls down, then it stays.
- Given a hidden header on a phone, when the keyboard focus moves into it, then it shows.
- Given reduced motion, when the header hides or shows, then it does so without sliding.
- Given a page scrolled past the header, when the header shows, then its background is translucent; given the page back at the top, then it is the usual one.
- Given a phone, when the menu opens, then it shows the navigation and the account links, the focus stays inside it, and Escape closes it and returns the focus to the menu button.
- Given a signed-in customer on a computer, when "Mi cuenta" is opened, then it lists "Mis pedidos", "Mis direcciones", "Mi cuenta" and "Salir", and Escape closes it and returns the focus to "Mi cuenta".
- Given "Temporada Invierno", when chosen, then the grid shows only `WINTER` products.
- Given any public page, when the footer is checked, then the withdrawal link is visible without signing in.
- Given MercadoPago and MODO enabled, when the footer is checked, then Medios de pago shows their two icons in a row, named "MercadoPago" and "MODO".
- Given a contact email or WhatsApp number, when the footer is checked, then "Contacto" links to `/#contactanos`; without either, it is not shown.
- Given MercadoPago and MODO enabled, when any public page opens, then above the footer the benefits bar shows "Enviamos tu compra", "Todas las formas de pago" with "Pagá con MercadoPago o MODO" and "Comprá con seguridad"; given the providers failing, then only the first and the last.

**Design notes**
- `SiteHeader`, `MobileMenu` (a modal `dialog`, like the filter panel of UC-02) and `SiteFooter` replace the header and `StoreFooter` of `StoreLayout`. The back office keeps its own layout.
- The threshold comes from `GET /api/shipping/rates` (RNF-31.6), one TanStack Query shared by the strip, the product page and the cart panel.
- Payment provider names from `GET /api/checkout/payment-providers`, the query the checkout and the purchase summary already share. The contact email, the WhatsApp number and the social links live in `storeContact.ts`, empty until the business sends them (D-15).
- `ContactSection` (D-09) closes the home page after the withdrawal block: "Contactanos" with a `mailto:` link and a `https://wa.me/{number}` link, no form.
- The footer keeps the "Cookies" link of RNF-25 next to the withdrawal link.
- Hiding and translucent header: `useHeaderScroll` (`features/search`) listens to `scroll` passively and reads the position once per frame; it hides after 8 px down past the header's height and shows after 8 px up, and does nothing while an open modal `dialog` (menu, search, cart panel) is on the page. It sets `data-scrolled` on `header.site` past the header's height (the background drops from 92% to 75% of `--bg` with a 12 px blur, a color change in CSS) and `data-hidden`; the CSS moves it with `transform: translateY(-100%)` only below 860 px, keeps it shown under `:focus-within`, and slides it only under `prefers-reduced-motion: no-preference`.

---

## UC-32 Edit the character banner

**Actor:** Administrator · **Module:** `catalog` · **Stage:** E7

**Preconditions:** the administrator is signed in to the back office (UC-04).

**Main flow**
1. The administrator opens "Portada" in the back office.
2. Uploads or replaces the photo and edits the title, the text, the link text and the link target.
3. Sees a preview as on the home page and saves.
4. Publishes or hides the banner.

**Alternative flows**
- Invalid data: `422` with the failing fields; nothing is saved.
- A file that is not a JPG, PNG or WebP, or is over 10 MB: refused as product photos are (UC-06).
- Publishing without a photo, title, link text or target: `409` naming what is missing; the banner stays hidden.

**Business rules**
- There is one banner. It starts hidden and empty.
- Title 1 to 60 characters; text 0 to 200; link text 1 to 30; all trimmed.
- The target is a path of this store: it starts with exactly one `/`, has no scheme, host, backslash or control characters, and is at most 200 characters. Anything else is refused, so the banner can never send shoppers to another site.
- Publishing needs the photo, the title, the link text and the target. Hiding needs nothing.
- The photo goes through the UC-06 conversion: WebP at 400, 800 and 1200 px and the blurred placeholder. Replacing it deletes the old files after the new ones are saved.
- The home page shows the published banner on its next load.

**Acceptance criteria**
- Given a banner with a photo, title, link text and target, when published, then the home page shows it.
- Given a hidden banner, when the home page opens, then its section is not shown.
- Given a banner without a photo, when publishing, then it is refused with `409` naming the photo.
- Given a target `https://other.site`, `//other.site` or `/\other.site`, when saved, then it is refused with `422`.
- Given a visitor or a customer session, when it calls the banner admin endpoints, then it gets `401` or `403` and nothing changes.
- Given a replaced photo, when saved, then the old files are deleted and the home page shows the new one.

**Design notes**
- `catalog.domain.HomeBanner` aggregate: holds the length rules, the target rule (`StorePath` value object) and the publish invariant. One row: `home_banner` (`catalog` module, a migration; `id` fixed by a check constraint, `title`, `body`, `link_text`, `link_path` (null together until first saved), the photo's `image_storage_key`, `image_placeholder`, `image_upload_key` and `image_content_sha256` (null together until a photo is uploaded), `published`, `updated_at`; created hidden and empty by the migration, with check constraints backing the domain's rules). No Row Level Security: no personal data (RNF-27), so it joins the public list of `RowLevelSecurityCoverageTest` with that reason.
- Inbound ports: `GetHomeBanner` (public query; result `Published(banner)` or `NotPublished`), `GetHomeBannerForAdmin`, `UpdateHomeBanner` (`Command(actor, title, body, linkText, linkPath)`; result `Updated`, `Invalid(fields)` or `Forbidden`), `PublishHomeBanner` / `HideHomeBanner` (result `Published`, `Incomplete(missing)`, `Hidden` or `Forbidden`), `ReplaceHomeBannerPhoto` (reuses the UC-06 `ImageProcessor` port and its libvips adapter, and the `ImageStore` files under `/uploads`; same `Idempotency-Key` rule as product photos, with only the current photo's key remembered: a retry is recognized until another photo replaces it).
- Endpoints: `GET /api/home/banner` → `200` `{title, body, linkText, linkPath, image}` or `204` when not published (catalog rate-limit policy); `GET` and `PUT /api/admin/home/banner`, `POST /api/admin/home/banner/publish`, `POST /api/admin/home/banner/hide`, `POST /api/admin/home/banner/photo` (multipart, `Idempotency-Key`). Errors as `problem+json`; OpenAPI updated.
- Back office: "Portada" in the menu, a form with the preview, as the product form (RNF-26: usable on a phone).
- Storefront: `CharacterBanner` reads `GET /api/home/banner`; its link is a router `Link`, never an `<a href>` built from the text. The photo is decorative (empty alternative text): the title carries the message. On `/` it sits between the carousel and "Elegí para quién" (UC-30), as a band across the whole page width with its content at the page width, as in the prototype (T-E7-11).

---

## Changes to UC-01 and UC-02 (grid and filters)

**Business rules (new)**
- A card shows the product's colors with available stock as swatches (D-04), at most 5 plus "+N", and the "Nuevo" label (D-05). The "Sold out" label of UC-01 stays.
- The filters open from a bar of buttons (Público, Talle, Color, Material, Temporada, Precio). Each button opens the UC-02 panel at its group; a button whose group has values chosen says how many. The UC-02 rules, panel and URL keys do not change. Buttons of groups without options are not shown, unless a shared link chose a value in that group (the panel keeps it to uncheck, UC-02).
- The listing shows the number of matches (D-11): "1 producto" or "N productos", at the right end of the filter bar row, as in the prototype. On phones it stays in view while the buttons scroll (T-E7-11).
- The visitor can switch between grid and list views. The choice is per visit and does not change the URL.
- "Ver más" stays (D-10).
- On a device with hover (`(hover: hover) and (pointer: fine)`), a card whose product has a second photo shows it while the pointer is over the card (T-E7-14). Touch devices and the list view show the first photo only and never download the second.

**Acceptance criteria**
- Given a product with stock in pink and none in beige, when listed, then its card shows only the pink swatch.
- Given a product with two photos on a computer, when the pointer rests on its card, then the second photo shows over the first; given a product with one photo, then the first one stays.
- Given a phone, when a listing with products of two photos loads, then no second photo is requested.
- Given a product created 10 days ago and one created 40 days ago, when listed, then only the first carries "Nuevo".
- Given 30 products matching the filters, when the listing opens, then it reads "30 productos", and 24 cards show with "Ver más".
- Given the "Talle" button, when pressed, then the filter panel opens with the focus on the size group.

**Design notes**
- `ProductListingItem` gains `colors: List<String>` (active priced variants with available stock, in display order, the same rule as the UC-02 color filter) and `isNew: boolean`. `Product.isNew(Instant now)` holds the 30-day rule in `catalog.domain`; `ListProducts` and `SearchProducts` read `now` from the injected `Clock`.
- `ProductListingQuery` and `ProductSearchQuery` gain `count`: a `count(*)` over the same constant SQL conditions as the page (one shared `matching` part, RNF-28), in the same read-only transaction. The use case skips it when the page is the last one and not past the end: its offset plus its size is the total.
- Endpoint: `GET /api/catalog/products`, with or without `q` (UC-26) → `200` `{items, hasMore, total}`, each item adding `colors` and `isNew`. OpenAPI and the typed client are updated.
- Second photo (T-E7-14): `ListedProduct` and `ProductListingItem` gain `secondImage` (the photo after the first, in the same order: position, then id); the listing query reads both in its lateral join. Each item of `GET /api/catalog/products` adds `secondImage` (null without one). The card renders it as a second `img` with `loading="lazy"`, `display: none` except on hover devices, empty alternative text (the first photo carries the name), faded in with `opacity`.
- Frontend: `ColorSwatches` (shared with UC-03), `FilterBar`, the view toggle (`ViewToggle`; the view is kept in `sessionStorage` for the visit, grid when storage is refused). The color map normalizes names (lowercase, no accents) and is unit tested, including the swatch border contrast against the card background (3:1).

---

## Changes to UC-03 (product page)

**Business rules (new)**
- Breadcrumbs: Inicio / {category} / {product}, and "Volver al catálogo" (back in history when the visitor came from the store, otherwise `/`).
- Gallery: main photo, thumbnails, and an enlarged view (the 1200 px file) when the photo is pressed.
- Color as swatches (D-04) with the chosen color's name; sizes as square buttons; a quantity input from 1 to the available stock of the chosen variant.
- On phones, when the add button leaves the screen, a fixed bar at the bottom shows the price and the add button.
- Trust icons (D-12) take the place of the RNF-31.6 purchase summary below the add button, so its lines are not repeated: "Envío gratis desde $X" (only with a threshold), the delivery times, and "Pagá con …" naming the payment methods enabled now (MercadoPago, MODO), with the link to the FAQ. A line that could not be read is left out.
- The price stands out over the description: 22 px, bold, in `--ink`. The picked color carries a 3 px ring; the picked size is filled with `--accent` (T-E7-22).
- The shipping cost by province (D-07), on one row ("Calculá el envío" and the province select) with the cost below, without a box (T-E7-22); still by province, never by postal code. Collapsible sections: size guide (RNF-31.5, moved from its current place), composition (material and season) and exchanges and returns (link to the FAQ).
- "Completá tu look" (D-08), in the info column below the collapsible sections, as in the prototype (T-E7-11). Reviews (UC-24) stay below the page layout.
- The UC-03 variant rules do not change.

**Alternative flows**
- The category has no other listed products: no "Completá tu look".
- A province without shipping: "No hacemos envíos a esa provincia".

**Acceptance criteria**
- Given a product page, when quantity 2 is chosen and added, then the cart gets 2 units of that variant (UC-08).
- Given a quantity above the available stock, when added, then the UC-08 stock message shows.
- Given a product page on a phone, when scrolled until the add button is off screen, then the fixed bar with the price and the button appears; pressing it adds as the main button does.
- Given a province without shipping, when chosen on the product page, then it says there is no shipping to it.
- Given a category with 6 other products, when a product page opens, then "Completá tu look" shows the 4 newest, without the open product.
- Given a category with no other products, when a product page opens, then "Completá tu look" is not shown.

**Design notes**
- `GET /api/catalog/products/{slug}` adds `category` `{slug, name}` (`ProductDetail` gets it from `LoadCategories`, already read by `GetProductDetail`).
- "Completá tu look" reads `GET /api/catalog/products?category={slug}` (first page, newest) and drops the open product: no new endpoint.
- The quantity goes in the existing `AddItemToCart` command; no cart change.
- `StickyAddToCart` uses `IntersectionObserver`; `ProductLightbox` is a modal `dialog`.

---

## Changes to UC-08 (cart panel)

**Business rules (new)**
- With a threshold, a bar shows "Te faltan $X para envío gratis" or "¡Tenés envío gratis!". X is the threshold minus the subtotal after discount as the server sent it. The checkout still decides the final cost (UC-12).
- Adding from the product page also shows a short "Agregado al carrito" notice (`role="status"`), closed by itself after 4 s or with its close button.
- Empty, the panel shows a shopping-bag icon above "Tu carrito está vacío.", and "Empezar a comprar" takes the panel's whole width inside its side padding. Below it, the rest of the panel's height is kept for "Productos sugeridos", a future task; until then that place renders nothing (T-E7-19).

**Acceptance criteria**
- Given an empty cart, when the panel opens, then it shows the icon, "Tu carrito está vacío." and a full-width "Empezar a comprar" that closes the panel.
- Given a $50,000 threshold and a $24,999 subtotal after discount, when the panel opens, then it reads "Te faltan $25.001 para envío gratis".
- Given a subtotal after discount of $50,000 or more, when the panel opens, then it reads "¡Tenés envío gratis!".
- Given no threshold, when the panel opens, then there is no bar.

**Design notes**
- `FreeShippingProgress` reads the shared rates query; the amounts are `Money` from the API, never recomputed prices. Before the threshold is known it shows nothing.
- The notice renders inside the cart panel, below its header (the prototype moves it to the top while the drawer is open): the modal dialog makes the page behind it inert, so a notice outside it could not be read or closed. Closing the panel closes the notice.
- The empty state is a column: the icon, the texts and the button at the top, then `.cart-suggestions` with `flex: 1`, an empty `div` until "Productos sugeridos" fills it. The icon is decorative (`aria-hidden`): the text says the cart is empty.

---

## Changes to UC-13 (checkout)

**Business rules (new)**
- From 640 px, short fields share a row: DNI and phone; city and province; number, floor and apartment.
- Without a session, step 1 offers "¿Ya tenés una cuenta? Iniciar sesión". After signing in, the customer comes back to `/checkout` with the cart (UC-08 merges the guest cart).
- **"Nombre y apellido" becomes two fields, "Nombre" and "Apellido" (D-13)**, each 1 to 50 characters, trimmed. The order keeps both (R-04). The other fields, validations and steps of UC-13 do not change.
- Signed in, the checkout prefills them from the account name: the first word in "Nombre", the rest in "Apellido", both editable. The account keeps a single name (UC-19).
- The paid-order and withdrawal emails greet the "Nombre" as typed ("Hola, María José"). Tracking keeps showing the first name only (UC-14: the link can be forwarded), now the "Nombre" as typed; the back office shows "Nombre Apellido".

**Acceptance criteria**
- Given a guest on step 1, when they choose "Iniciar sesión" and sign in, then they return to the checkout with their cart.
- Given a sign-in link with a return path outside the store (another origin or `//host`), when the customer signs in, then they go to `/`.
- Given a buyer "María José" "Pérez", when the payment is approved, then the email greets "Hola, María José".
- Given an empty "Nombre" or "Apellido", or one over 50 characters, when going on from step 1, then the field is marked with its message; the server refuses it with `422`.
- Given a customer whose account name is "Ana María Gómez", when the checkout opens, then "Nombre" reads "Ana" and "Apellido" "María Gómez".
- Given the checkout with one product, when Lighthouse measures it, then Cumulative Layout Shift stays under 0.1 (T-E7-11).

**Design notes**
- The checkout page is at least one screen tall, so the footer starts below the first screen while the cart loads and does not jump when the form arrives (T-E7-11).
- `SignInPage` today always goes to `/`. It gains a `volver` query key, accepted only when it is a path of this store (starts with one `/`, not `//`), so it cannot be used as an open redirect.
- Name split (D-13), one migration in `ordering`: `orders.customer_name` becomes `customer_first_name` and `customer_last_name` (each trimmed, 1 to 50, check constraints like the old one). The store has no real orders yet (it goes live in E6), so existing rows split at the first space, and a single word goes to the first name with `-` as the last name. A view or helper gives the full name where one is shown.
- `ordering.domain.BuyerDetails(firstName, lastName, email, phone, nationalId)`; `BuyerDetails.firstNameOf` and `OrderEmailWriter.firstName` (the first-word guess) are removed: the emails use the stored first name.
- API: `POST /api/checkout` takes `firstName` and `lastName` instead of `name`; tracking keeps `buyerFirstName` (now the stored first name, never the last name); `ordering.api` (`PaidOrders`, `WithdrawalNotices`, `AdminOrderDetails`) and the account data export (UC-28) carry both. OpenAPI and the typed client are updated; the e2e `fillCheckout` helper fills both fields.
- Analytics and logs never carry either name (RNF-25, CLAUDE.md).
