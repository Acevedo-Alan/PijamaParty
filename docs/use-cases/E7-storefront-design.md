# E7 – The storefront as in the prototype

**Status:** Draft · **Use cases:** UC-30 and UC-31, and changes to UC-01, UC-02, UC-03, UC-08 and UC-13 · **Spanish counterpart:** `docs/specs/E7-tienda-prototipo` (spec, plan and tasks)

**Modules involved:** `catalog` (three read changes), `shipping` (read only, unchanged); the rest is the frontend.

## Goal

Make the storefront look and flow like the approved prototype (`docs/design/prototype/index.html`). E1 to E6 took its tokens and a few layouts but not its sections. The side-by-side screenshots of 2026-10-19 (home, listing, product, cart, checkout; phone and desktop) show the gap.

No business rule changes except the ones listed under *Open decisions*. Nothing in the prototype that promises something to the buyer (installments, discounts, free exchanges) is shown until the owner decides it.

**Out of scope:** the prototype's code (inline scripts, Google Fonts, Unsplash photos: the CSP of T-E6-03, the self-hosted font of ARCHITECTURE.md section 11, image licenses); the wishlist heart (no use case); editing the home content from the back office.

## Open decisions

Each has a proposal. The owner confirms or changes it in `T-E7-01`, and it is written into `docs/specs/00-dominio-y-reglas.md` before the task that uses it. Nothing below that depends on one is coded until then.

| ID | Topic | Proposal |
|---|---|---|
| D-01 | "3 cuotas sin interés" | Not shown in v1: MercadoPago sets the installments, the shop pays their cost, and RNF-30.4 requires showing the total financial cost when installments are shown. |
| D-02 | "10% OFF por transferencia" | Not shown: bank transfer is not a payment method (immediate methods only: MercadoPago and MODO). |
| D-03 | Source of the home texts and photos | Fixed in the frontend: the shop's own photos in the build, texts in `es.ts`. A change needs a deploy. |
| D-04 | Color swatches | A frontend map from color name to tone; an unknown color keeps its text label. Alternative: a tone per color in the back office (migration). |
| D-05 | "Nuevo" label | Products created in the last 30 days, computed on the server with the injected `Clock`. |
| D-06 | The card's "+" button | Dropped: adding needs a size and a color, and the card already opens the product. |
| D-07 | Shipping by postal code (product page) | A province select showing that province's cost: shipping is priced per province (UC-12). |
| D-08 | "Completá tu look" | Up to 4 products of the same category, newest first, without the open product, from the existing listing. |
| D-09 | "Contactanos" | Email and WhatsApp links, no form (a form needs an endpoint, an email to the owner, rate limiting and stored personal data). |
| D-10 | Numbered pagination | Keep "Ver más" (UC-01). |
| D-11 | "N productos" count | Added: the listing returns the total of matches. |
| D-12 | Trust icons | "Envío gratis desde $X" only with a threshold; "Primer cambio sin cargo" only if the owner confirms it as policy; "Compra protegida" becomes "Pagá con MercadoPago". |
| D-13 | First and last name split in checkout | Keep the single "Nombre y apellido" field (UC-13). |
| D-14 | Home carousel | Up to 3 slides, auto-advancing every 6 s with a pause button (WCAG 2.2.2), no motion under `prefers-reduced-motion`. |
| D-15 | Business materials | The owner provides the logo, up to 3 carousel photos, the character-banner photo, the 3 audience photos and the Instagram and TikTok links. Missing photos show the prototype's gradient background. |
| D-16 | Motion | The motion system of the "Motion" section, CSS only. Alternative: an animation library (such as Motion), which adds load weight. |

---

## Motion (D-16)

The prototype feels friendly because it answers the shopper: the cart icon bumps on add, panels slide, notices rise. E7 makes that one system with shared tokens instead of one-off effects. Part of it exists already (the cart bump and slide-in in `cart.css`, the skeleton pulse and filter slide-in in `catalog.css`); those move onto the tokens.

**Rules**
- **Motion answers an action** (add, open, choose, go to the next step) and shows what changed. Nothing moves by itself except the carousel (D-14).
- **One entrance moment:** the home welcome text enters once on load. Sections do not fade in on scroll.
- **Short and soft:** 120 to 450 ms (the home entrance up to 700 ms), easing that slows on arrival.
- **Accessible:** movement and scaling only under `@media (prefers-reduced-motion: no-preference)`; under `reduce`, at most a short opacity change. Hover effects only under `@media (hover: hover)`.
- **No load cost:** CSS only (the Web Animations API where a class toggle is not enough), no animation library. Only `transform` and `opacity` are animated, never layout properties. The home LCP image is never animated (RNF-11).

**Catalog**

| Where | Motion | Tokens |
|---|---|---|
| Home welcome | Title, text and button rise 12 px and fade in, staggered 80 ms, once | `--dur-entrance`, `--ease-out` |
| Carousel | Crossfade between slides | `--dur-slow`, `--ease-standard` |
| Product card (hover devices) | Photo scales to 1.03 | `--dur-base`, `--ease-out` |
| Swatches and size buttons | The selection ring grows in | `--dur-fast`, `--ease-pop` |
| Add to cart | Button shows "Agregado" with a check for 1.5 s; cart icon bumps once; the notice rises from the bottom | `--dur-base`, `--ease-pop` |
| Cart panel, menu, filters, lightbox | Slide from their edge over a fading overlay; reverse on close | `--dur-panel`, `--ease-standard` |
| Free-shipping bar | Fills smoothly (`transform: scaleX`); reaching the threshold pops the "¡Tenés envío gratis!" message | `--dur-slow`, `--ease-pop` |
| Product page sections | Expand and collapse (`grid-template-rows: 0fr → 1fr`) | `--dur-base`, `--ease-standard` |
| Checkout steps | The new step rises 12 px and fades in | `--dur-base`, `--ease-out` |
| Loading | Skeletons pulse | 1.2 s loop |
| Route change | New page content fades in | `--dur-fast` |

**Acceptance criteria**
- Given reduced motion, when the shopper adds a product, opens the cart or changes checkout step, then nothing translates or scales.
- Given a product added from the product page, when the API answers `200`, then the cart icon bumps once and the notice appears; a rejected add does neither.
- Given the home page, when Lighthouse measures it, then LCP stays under 3 s and Cumulative Layout Shift under 0.1.
- Given a touch device, when a card is tapped, then no hover effect sticks.

**Design notes**
- Tokens in `tokens.css`, listed in ARCHITECTURE.md section 11: `--dur-fast` 120 ms, `--dur-base` 240 ms, `--dur-panel` 320 ms, `--dur-slow` 450 ms, `--dur-entrance` 700 ms; `--ease-out` `cubic-bezier(.2, .7, .2, 1)`, `--ease-standard` `cubic-bezier(.4, 0, .2, 1)`, `--ease-pop` `cubic-bezier(.34, 1.56, .64, 1)`. The first two come from the prototype.
- Closing animations finish before the `dialog` closes (`animationend`), so focus returns only after.
- `tokens.test.ts` checks the token values; component tests check the classes under both `prefers-reduced-motion` values (`matchMedia` mocked).

---

## UC-30 Home page

**Actor:** Visitor · **Module:** frontend (reads `catalog` and `shipping`) · **Stage:** E7

**Preconditions:** none.

**Main flow**
1. The visitor opens `/`.
2. The system shows, in this order: the welcome carousel, the character banner, "Elegí para quién", "Todos los productos" (the UC-01 grid with the UC-02 filters), the withdrawal block (UC-29) and "Contactanos".
3. "Descubrí la colección" scrolls to "Todos los productos" and moves the focus to its heading.
4. Each "Elegí para quién" card (Mujer, Hombre, Niños) opens the listing filtered by that audience.

**Alternative flows**
- A photo is missing (D-15): its place shows the gradient background, with the same size, so nothing shifts.
- The URL carries filters or a sort: the page opens scrolled to "Todos los productos".

**Business rules**
- Texts and photos are fixed in the frontend (D-03).
- The audience cards set the UC-02 URL key `publico` (`mujer`, `hombre`, `infantil`) and keep the sort.
- `/categoria/{slug}` keeps showing only the listing, without the home sections.
- The carousel follows D-14.

**Acceptance criteria**
- Given the home page on a phone, when it loads, then the carousel, the character banner, "Elegí para quién", the grid, the withdrawal block and the contact section appear in that order.
- Given the home page, when "Mujer" is chosen in "Elegí para quién", then the grid is filtered by audience `WOMEN` and the URL has `publico=mujer`.
- Given a visitor with reduced motion, when the home page opens, then the carousel does not advance by itself.
- Given the carousel advancing, when the pause button is pressed, then it stops; pressing it again resumes it.
- Given the home page, a listing, a product page and the checkout, when Lighthouse measures them, then RNF-11 (LCP under 3 s) and RNF-31.4 (accessibility 90 or more) still hold.

**Design notes**
- `HomePage` (`features/catalog`) at `/`, composing `HeroCarousel`, `CharacterBanner`, `AudienceCards`, the existing listing of `CatalogPage` (extracted so both routes use it), `WithdrawalHomeBlock` and `ContactSection`. It stays in the main bundle (T-E6-02).
- Photos in `frontend/src/assets/home/` as WebP at 640, 1280 and 1920 px, with `srcset` and `sizes`. The first slide is `fetchpriority="high"` and preloaded; the others are lazy. The carousel is a `section` with `aria-roledescription="carrusel"`, slides labeled "n de 3", and the pause button first in tab order.
- No API change.

---

## UC-31 Header, menu and footer

**Actor:** Visitor · **Module:** frontend (reads `shipping`) · **Stage:** E7

**Preconditions:** none.

**Main flow**
1. Every public page shows the header: logo (link to `/`), navigation (Todo, Mujer, Hombre, Niños, Temporada Invierno), search (UC-26), "Ingresar" and "Crear cuenta" (or the account entry with a session, UC-19) and the cart button.
2. From 860 px the navigation sits in the header; below, a menu button opens it with the account links in a side panel.
3. With a free-shipping threshold, a strip above the header says "Envío gratis desde $X".
4. The footer has three columns: Ayuda (FAQ, exchanges and returns, terms), Redes (Instagram, TikTok) and Medios de pago (the enabled providers); below them, the withdrawal link (RNF-30.1) and "© {year} Pijama Party Santa Fe".

**Alternative flows**
- No threshold, or `GET /api/shipping/rates` fails: no strip.
- A footer page that does not exist yet (T-E6-06): its link is not shown.

**Business rules**
- Navigation links: Todo → `/`; Mujer, Hombre, Niños → `/?publico=mujer|hombre|infantil`; Temporada Invierno → `/?temporada=invierno`. The current one is marked with `aria-current="page"`.
- The strip shows no installments or discounts (D-01, D-02).
- Seller data (RNF-30.2) joins the footer with T-E6-21; it is not part of this use case.

**Acceptance criteria**
- Given a free-shipping threshold of $50,000, when any public page opens, then the strip reads "Envío gratis desde $50.000"; without a threshold, there is no strip.
- Given a phone, when the menu opens, then it shows the navigation and the account links, the focus stays inside it, and Escape closes it and returns the focus to the menu button.
- Given "Temporada Invierno", when chosen, then the grid shows only `WINTER` products.
- Given any public page, when the footer is checked, then the withdrawal link is visible without signing in.

**Design notes**
- `SiteHeader`, `MobileMenu` (a modal `dialog`, like the filter panel of UC-02) and `SiteFooter` replace the header and `StoreFooter` of `StoreLayout`. The back office keeps its own layout.
- The threshold comes from `GET /api/shipping/rates` (RNF-31.6), one TanStack Query shared by the strip, the product page and the cart panel.
- Payment provider icons from `GET /api/checkout/payment-providers`; social links from `es.ts` (D-15).

---

## Changes to UC-01 and UC-02 (grid and filters)

**Business rules (new)**
- A card shows the product's colors with available stock as swatches (D-04), at most 5 plus "+N", and the "Nuevo" label (D-05). The "Sold out" label of UC-01 stays.
- The filters open from a bar of buttons (Público, Talle, Color, Material, Temporada, Precio). Each button opens the UC-02 panel at its group; a button whose group has values chosen says how many. The UC-02 rules, panel and URL keys do not change. Buttons of groups without options are not shown.
- The listing shows the number of matches (D-11): "1 producto" or "N productos".
- The visitor can switch between grid and list views. The choice is per visit and does not change the URL.
- "Ver más" stays (D-10).

**Acceptance criteria**
- Given a product with stock in pink and none in beige, when listed, then its card shows only the pink swatch.
- Given a product created 10 days ago and one created 40 days ago, when listed, then only the first carries "Nuevo".
- Given 30 products matching the filters, when the listing opens, then it reads "30 productos", and 24 cards show with "Ver más".
- Given the "Talle" button, when pressed, then the filter panel opens with the focus on the size group.

**Design notes**
- `ProductListingItem` gains `colors: List<String>` (active priced variants with available stock, in display order, the same rule as the UC-02 color filter) and `isNew: boolean`. `Product.isNew(Instant now)` holds the 30-day rule in `catalog.domain`; `ListProducts` and `SearchProducts` read `now` from the injected `Clock`.
- `ProductListingQuery` returns the total with the page: a `count(*)` over the same constant SQL conditions (RNF-28), in the same read-only transaction.
- Endpoints: `GET /api/catalog/products` and `GET /api/catalog/search` → `200` `{items, hasMore, total}`, each item adding `colors` and `isNew`. OpenAPI and the typed client are updated.
- Frontend: `ColorSwatches` (shared with UC-03), `FilterBar`, the view toggle. The color map normalizes names (lowercase, no accents) and is unit tested, including the swatch border contrast against the card background (3:1).

---

## Changes to UC-03 (product page)

**Business rules (new)**
- Breadcrumbs: Inicio / {category} / {product}, and "Volver al catálogo" (back in history when the visitor came from the store, otherwise `/`).
- Gallery: main photo, thumbnails, and an enlarged view (the 1200 px file) when the photo is pressed.
- Color as swatches (D-04) with the chosen color's name; sizes as square buttons; a quantity input from 1 to the available stock of the chosen variant.
- On phones, when the add button leaves the screen, a fixed bar at the bottom shows the price and the add button.
- Trust icons (D-12); the shipping cost by province (D-07); collapsible sections: size guide (RNF-31.5, moved from its current place), composition (material and season) and exchanges and returns (link to the FAQ).
- "Completá tu look" (D-08). Reviews (UC-24) stay below.
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

**Acceptance criteria**
- Given a $50,000 threshold and a $24,999 subtotal after discount, when the panel opens, then it reads "Te faltan $25.001 para envío gratis".
- Given a subtotal after discount of $50,000 or more, when the panel opens, then it reads "¡Tenés envío gratis!".
- Given no threshold, when the panel opens, then there is no bar.

**Design notes**
- `FreeShippingProgress` reads the shared rates query; the amounts are `Money` from the API, never recomputed prices.

---

## Changes to UC-13 (checkout)

**Business rules (new)**
- From 640 px, short fields share a row: DNI and phone; city and province; number, floor and apartment.
- Without a session, step 1 offers "¿Ya tenés una cuenta? Iniciar sesión". After signing in, the customer comes back to `/checkout` with the cart (UC-08 merges the guest cart).
- Fields, validations and steps of UC-13 do not change (D-13).

**Acceptance criteria**
- Given a guest on step 1, when they choose "Iniciar sesión" and sign in, then they return to the checkout with their cart.
- Given a sign-in link with a return path outside the store (another origin or `//host`), when the customer signs in, then they go to `/`.

**Design notes**
- `SignInPage` today always goes to `/`. It gains a `volver` query key, accepted only when it is a path of this store (starts with one `/`, not `//`), so it cannot be used as an open redirect.
