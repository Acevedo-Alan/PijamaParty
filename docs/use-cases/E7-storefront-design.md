# E7 – The storefront as in the prototype

**Status:** Draft · **Use cases:** UC-30, UC-31 and UC-32, and changes to UC-01, UC-02, UC-03, UC-08, UC-13 and UC-14 · **Spanish counterpart:** `docs/specs/E7-tienda-prototipo` (spec, plan and tasks)

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
| D-14 | Home carousel | Decided | Up to 3 slides, auto-advancing every 4 s with a pause button (WCAG 2.2.2), no auto-advance under `prefers-reduced-motion`. |
| D-15 | Business materials | Open (owner) | The owner provides the logo, up to 3 carousel photos, the 3 audience photos, the first character-banner photo (then changed in the back office), the contact email and WhatsApp number, and the Instagram and TikTok links. Missing photos show the prototype's gradient background. Received on 2026-10-23: the logo. The cropped sign is the header logo (`src/assets/brand/logo-{48,96,144}.webp`: the white outside the frame made transparent and the bits of characters in its corners erased, 48 px tall as in the prototype, alternative text the store name); the full illustration is the first character-banner photo, uploaded in the back office ("Portada", UC-32). Received on 2026-10-28: the WhatsApp number, +54 9 342 530-4507 (`5493425304507` in `storeContact.ts`). Received on 2026-10-29: the Instagram link, https://www.instagram.com/pijamaparty.zzz/. |
| D-16 | Motion | Decided | The Motion library (`motion`, its React entry `motion/react`), loaded after first paint, on the tokens of the "Motion" section. |
| D-17 | Typography | Decided | Inter replaces Quicksand for headings and body text, in the store and the back office (owner, 2026-10-24), self-hosted with `@fontsource/inter` at weights 500, 600 and 700 (ARCHITECTURE.md section 11). |
| D-18 | Exchanges policy | Decided | Exchange for another size or color within 30 calendar days of delivery, subject to stock, with the garment unused, unwashed and with its tags; the buyer pays the shipping both ways, except for a manufacturing defect, whose shipping the shop pays (owner, 2026-10-25). Separate from the 10-day withdrawal right (UC-29). Draft until T-E6-21. |

---

## Motion (D-16)

The prototype feels friendly because it answers the shopper: the cart icon bumps on add, panels slide, notices rise. E7 makes that one system with shared tokens instead of one-off effects. Part of it exists already (the cart bump and slide-in in `cart.css`, the skeleton pulse and filter slide-in in `catalog.css`); those move onto the tokens.

**Rules**
- **Motion answers an action** (add, open, choose, go to the next step) and shows what changed. Nothing moves by itself except the carousel (D-14), the account pages' sky, where the chibi breathes and blinks slowly while idle and the stars turn on and off (T-E7-47), the empty cart's sleeping chibi, which breathes (T-E7-48), and the empty states' chibi, which breathes, blinks or bobs its "?" (T-E7-49), and the little stars that orbit while a payment is awaited (T-E7-50).
- **Entrances:** the home welcome text enters once on load; product cards enter when they join the grid (its first page, "Ver más", a new filter or sort), never because the page scrolled to them; a new page enters on a route change, never on the first load of the store. Sections do not fade in on scroll (T-E7-17).
- **Short and soft:** 120 to 450 ms (the home entrance up to 700 ms, the wait of the add and pay buttons at least 600 ms), easing that slows on arrival.
- **Accessible:** movement and scaling only under `@media (prefers-reduced-motion: no-preference)`; under `reduce`, at most a short opacity change. Hover effects only under `@media (hover: hover)`.
- **Motion, without delaying the first load (D-16):** enter and exit animations, step changes and pops use the Motion library, loaded after first paint; until it loads, everything shows in place, unanimated. Simple effects (hover, color changes) stay in CSS. Only `transform` and `opacity` are animated, never layout properties; the one exception is the tick of the add button, an 18 px stroke that draws itself with `stroke-dashoffset` (T-E7-41). The home LCP image is never animated (RNF-11).

**Catalog**

| Where | Motion | Tokens |
|---|---|---|
| Home welcome | Title, text and button rise 12 px and fade in, staggered 80 ms, once | `--dur-entrance`, `--ease-out` |
| Carousel | Crossfade between slides | `--dur-slow`, `--ease-standard` |
| Header links, search, account and cart icons (hover devices) | A pastel rainbow (`--rainbow`) fills the link's pill or the icon's circle from the bottom to the top; it drains back down when the pointer leaves. Under reduced motion it only fades in | `--dur-slow`, `--ease-standard` |
| Product card (hover devices) | Photo scales to 1.03; a second photo, if any, crossfades in (UC-01) | `--dur-base`, `--ease-out` |
| Header (below 860 px) | Slides up out of view while scrolling down, back while scrolling up (UC-31) | `--dur-base`, `--ease-standard` |
| Swatches and size buttons | The selection ring grows in | `--dur-fast`, `--ease-pop` |
| Add to cart (T-E7-41) | In three steps, transform and opacity only. **Press (0–120 ms):** the button sinks to `scale(0.97)` and stays so while it says "Agregando…". **Morph (120–350 ms):** its first face (bag, "Agregar al carrito", subtotal) slides up (`translateY(-100%)`) and fades; from below the second face comes in with a springy ease (`--ease-pop`): a tick that draws itself (`stroke-dashoffset`) and "¡Listo! En tu bolsa", on a `--success` (sage) background, for 1.5 s. **Pulse (350 ms):** the bag of the header hops (`scale(1.25)` and `rotate(-8deg)` back to rest) and its counter pops; the panel opens at 450 ms. Refused or failed: the button shakes once and keeps its name. Under reduced motion the two faces only trade places and nothing sinks, slides or draws | press 120 ms; morph 230 ms with `--ease-pop`; tick 260 ms |
| "Confirmar y pagar": waiting | The pastel rainbow (`--rainbow-across`) fills the whole button from left to right in jumps, as a real load does (a push, a pause, another push, to `scaleX` 0.85 in 600 ms), then creeps on to 0.95 while the answer has not come, with a shine sweeping across it, under "Confirmando…". A chibi in pajamas and nightcap (an original drawing, `ButtonProgress`) runs on the button's top edge at the tip of the bar, bobbing and swinging its legs. The wait lasts at least 600 ms even when the server answers sooner, so the bar is seen (the add button no longer does this: T-E7-41). Under reduced motion the full rainbow only fades in, with no shine and no chibi | 600 ms, `--ease-out`; creep 8 s; shine 1.2 s loop; steps 280 ms loop |
| "Confirmar y pagar": done | The bar completes and the chibi jumps at its end. After an add (T-E7-41), the notice drops into the cart panel's header over its title, up to the panel's close button, so it neither covers nor moves the items (on a phone the added product stays in sight). The pay button stays filled while the page goes to the payment | `--dur-base`, `--ease-pop`; jump and sparkles `--dur-slow` |
| "Confirmar y pagar": refused or failed | The bar turns soft red and drains back to the left while the chibi fades out; the message near the button says why. Under reduced motion it only fades out | `--dur-slow`, `--ease-standard` |
| Cart panel, menu, filters, lightbox | Slide from their edge over a fading overlay; reverse on close. What rises from the bottom (the phone menu below 700 px, the lightbox) opens slower and settles softly | `--dur-panel`, `--ease-standard`; from the bottom `--dur-slow`, `--ease-out` to open |
| Phone menu contents | Each navigation link comes 12 px in from the left and fades in, 40 ms apart, while the panel slides in (T-E7-17). Opening a level ("Todo", "Mi cuenta") slides the current one 30% to the left as it fades and brings the new one in from the right edge; "Volver" plays it backwards (T-E7-39) | `--dur-base`, `--ease-out`; levels `--dur-panel`, `--ease-standard`, a crossfade only under reduced motion |
| Product cards joining the grid | Rise 12 px and fade in, 40 ms apart within a page (at most 6 steps); cards already shown do not move (T-E7-17) | `--dur-base`, `--ease-out` |
| Free-shipping bar | Fills smoothly (`transform: scaleX`); reaching the threshold pops the "¡Tenés envío gratis!" message | `--dur-slow`, `--ease-pop` |
| Product page sections | Expand and collapse (`grid-template-rows: 0fr → 1fr`) | `--dur-base`, `--ease-standard` |
| Checkout steps | The new step rises 12 px and fades in | `--dur-base`, `--ease-out` |
| Loading | Skeletons pulse | 1.2 s loop |
| Route change | New page content rises 12 px and fades in; not on the first load (T-E7-17) | `--dur-base`, `--ease-out` |
| Chibi while paying (T-E7-50) | **`waiting` + `paying`:** eyes up, "o" mouth, and three little gold stars orbit over its head for as long as it waits. **`error`:** one head shake and the sad face. Under reduced motion the stars stay still and there is no shake | orbit 2.4 s linear loop; shake as T-E7-47 |
| Chibi in empty states (T-E7-49) | **`puzzled`** (search without results, 404): the head tilts −7°, the eyes look to the side and the "?" bobs over the head. **`orders`** (no orders yet): it breathes and blinks as when idle, with no "zZz". Under reduced motion, still in its pose | "?" 2.4 s loop; breath 3.2 s; blink as T-E7-47 |
| Chibi on the paid order and the empty cart (T-E7-48) | The shared `Chibi` (`components/chibi`, the drawing and gestures of T-E7-47 without the sky), each moment a `mood` and a `pose`. **Paid order** (`success`, `paid`): on mount it hops with its hearts and six little stars fall around it, once; then it keeps smiling. **Empty cart** (`asleep`, `cart`): eyes closed, hugging a pillow, it breathes and its "zZz" rise. Under reduced motion both keep their face and pose, still | hop and hearts as T-E7-47; stars 1.4 s once; breath 3.2 s loop |
| Account chibi (T-E7-47) | The pay button's chibi (the same original character, drawn larger as layered inline SVG in the super-deformed chibi style: a head about two thirds of its height on a tiny body, tall dark oval eyes with a highlight, a thick outline, flat colors and cel shadows, long hair and a stray lock out of the cap, mitten hands, a lilac shadow on the ground; the style only, no existing character) beside the card of the four account pages reacts to the form through one state: `idle`, `text`, `password`, `peek`, `waiting`, `error`, `success`. **idle:** breathes (`translateY` 2 px), blinks every few seconds (eyelids `scaleY`), "zZz" rise and fade. **Always:** the sky's 15 stars turn on and off (opacity down to 0.08 and back with a small sparkle, `scale`), each at its own pace (2.5–4.6 s) and delay, and a shooting star crosses the top of the sky for about a second every 13 s. **Per page** (`data-pose`, from `AuthCard`'s `pose`): on registration the right hand waves while idle; on recovery a "?" bobs over the head in place of the "zZz"; on the reset page the right hand holds a gold key. **The sign** hops after the chibi on **success** and sways once on **error**; on **success** ten little stars rain over the sky. **text** (focus in the email or name): eyes open wide, the head tilts toward the form. **password:** both hands rise over the eyes. **peek** (password field focused while shown): the hands part a little. **waiting** (request in flight): eyes look up. **error** (the server refused: wrong credentials, email taken, too many attempts, invalid link): one head shake and a sad face until a field is typed in again. **success** (account created, recovery email sent): a hop with a few hearts. A signed-in or reset page navigates away at once; nothing waits for the chibi. The gestures are CSS states of `data-mood` on the sky (`AuthChibi`), like the pay button's chibi, so they need no Motion load. Under reduced motion there is no loop (breath, blink, "zZz", stars, wave, "?" bob), shooting star, rain, shake or hop, and the sign stays still: the poses and faces change at once, as every transition of the store does under the base reduced-motion rule; the hearts simply show | breath 3.2 s loop; blink about 140 ms every 5 s; stars 3 s loop; poses `--dur-base`, `--ease-pop`; faces `--dur-fast`; shake and hop `--dur-slow`; hearts 900 ms |

**Acceptance criteria**
- Given reduced motion, when the shopper adds a product, opens the cart or changes checkout step, then nothing translates or scales.
- Given a product added from the product page, when the API answers `200`, then the cart icon hops once, its counter pops, and the panel opens with the notice; a rejected add does none of these (T-E7-41).
- Given an add from the product page, when the API answers, then the button says "Agregando…" while it waits and, on `200`, morphs to "¡Listo! En tu bolsa" with a tick, with no minimum wait (T-E7-41).
- Given a successful add, when the button is done, then only its confirmation face is read by screen readers; under reduced motion the faces only trade places. Given a rejected or failed add, then the button shakes once and keeps its name (T-E7-41).
- Given "Confirmar y pagar", when it waits for the answer, then the button shows the rainbow bar with the chibi running on it; a refusal drains the bar in soft red.
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
- A returning visitor sees "Vistos recientemente" between "Elegí para quién" and "Todos los productos" (T-E7-33, rules in *Changes to UC-03*).

**Acceptance criteria**
- Given the home page on a phone, when it loads, then the carousel, the character banner, "Elegí para quién", the grid and the contact section appear in that order, with no withdrawal block of their own: the withdrawal link is in the footer.
- Given the home page, when "Mujer" is chosen in "Elegí para quién", then the grid is filtered by audience `WOMEN` and the URL has `publico=mujer`.
- Given the character banner still loading, when the home page shows, then its place holds the banner's size, without texts or link.
- Given a visitor with reduced motion, when the home page opens, then the carousel does not advance by itself.
- Given the carousel advancing, when the pause button is pressed, then it stops; pressing it again resumes it.
- Given the carousel on a phone, when it is swiped left, then the next slide shows; swiped right, the previous one; a vertical move scrolls the page (with or without reduced motion).
- Given the carousel advancing, when the keyboard focus enters it on anything but the pause button, a finger or the mouse holds it, it leaves the screen or the browser tab is hidden, then it waits; when that ends, it resumes with the time the slide had left.
- Given the carousel advancing, when a slide shows, then the current pill fills over the slide's time and freezes while the carousel waits; under reduced motion there is no fill.
- Given the home page, a listing, a product page and the checkout, when Lighthouse measures them, then RNF-11 (LCP under 3 s) and RNF-31.4 (accessibility 90 or more) still hold.

**Design notes**
- `HomePage` (`features/catalog`) at `/`, composing `HeroCarousel`, `CharacterBanner`, `AudienceCards`, `CatalogListing` (the listing of `CatalogPage`, extracted so both routes use it) and `ContactSection` (added by T-E7-03). It stays in the main bundle (T-E6-02). On `/` the grid starts below the first screen, so its photos load lazily and the carousel's first photo is the only one loaded first.
- The fixed slides and audience cards are listed in `homeContent.ts`, each with its gradient tone and, once the business sends it (D-15), its photo. Opened with filters or a sort, the page scrolls to the listing, and again once the banner has its place above it.
- Photos in `frontend/src/assets/home/` as WebP at 640, 1280 and 1920 px, with `srcset` and `sizes`. The first slide is `fetchpriority="high"` and preloaded; the others are lazy. While no slide has a photo (D-15), the character banner's photo is the first photo on the screen and the page's LCP: it loads eagerly with `fetchpriority="high"` instead of lazily; once the first slide has a photo, the banner's goes back to lazy (RNF-11). The carousel is a `section` with `aria-roledescription="carrusel"`, slides labeled "n de 3", and the pause button first in tab order (not shown under reduced motion, where nothing advances). The slides crossfade in CSS (`opacity` only); the welcome text enters through `useActionMotion`.
- Following Apple's Human Interface Guidelines (swiping is a phone's way to move between pages; content does not move away while it is being looked at or used) and WCAG 2.2.2: the carousel is swiped with `useSwipe`, as the gallery (no wrap past the ends, the click that ends a drag is swallowed, `touch-action: pan-y pinch-zoom` so pinching still zooms). It waits while the keyboard focus is inside it other than on the pause button (the button that controls it would otherwise keep it waiting after "Reanudar"), a focus that comes within a second of a press on it being the press's and not the keyboard's (a phone focuses a tapped dot only after the finger lifts), while a pointer is pressed on it, while less than a third of it is on screen (`IntersectionObserver`) and while the tab is hidden (`visibilitychange`); a slide keeps the time it had left. The current pill holds a CSS fill (`transform: scaleX`, linear, the slide's time) restarted per slide and paused (`animation-play-state`) whenever the carousel waits, so the fill and the timer stay together. The pointer over it does not stop it: the pause button already does, without leaving a mouse user wondering why "Reanudar" did nothing.
- The banner comes from `GET /api/home/banner` (UC-32); the rest of the page needs no API change.

---

## UC-31 Header, menu and footer

**Actor:** Visitor · **Module:** frontend (reads `shipping`) · **Stage:** E7

**Preconditions:** none.

**Main flow**
1. Every public page shows the header: logo (link to `/`), navigation (Todo, Mujer, Hombre, Niños, Temporada Invierno), search (UC-26), the account icon (a person's outline drawn like the cart's bag: a link to the sign-in page named "Ingresar", or with a session the button that opens "Mi cuenta", UC-19, T-E7-23) and the cart button. The header has no "Crear cuenta": the sign-in page offers it. On the checkout (`/checkout` and below) and the order page (`/pedido` and below) the header keeps only the logo, the account icon and the cart button: no navigation, no menu button and no search, so the buyer does not leave the purchase by accident; the free-shipping strip stays.
2. From 860 px the navigation sits in the header; below, a menu button opens it (T-E7-39): a panel that slides in from the left, edge to edge and the full height of the screen, as wide as the screen up to 440 px (wider screens show the dimmed page at its right). Its top row holds the title "Menú" (or, inside a level, "Volver") on the left and the close button, named "Cerrar menú", on the right. Below it, in a column that scrolls when it does not fit: the navigation links in large uppercase type; after a line, the shortcuts in smaller type, each after a line icon: the account ("Ingresar", or "Mi cuenta" with a session; T-E7-39), "Mi carrito" with its units and "Guía de talles"; and last, in small type, "¿Necesitás ayuda?" with the FAQ link and, with the WhatsApp number (D-15), "Escribinos por WhatsApp", then Instagram and TikTok as icons (when the business sent them, D-15). "Todo" (when there are categories) and "Mi cuenta" open a level of their own that slides in over the menu (drill-down).
3. Above the header, an announcement ticker (T-E7-39) runs the store's messages from right to left in a loop: "Envío gratis desde $X" (with a free-shipping threshold), "Envíos por Correo Argentino" and "Pagá con" the enabled providers (e.g. "Pagá con MercadoPago o MODO").
4. The footer is a `--rose-soft` band across the whole width of the screen, its content inside the page's width; it does not repeat the store's name in large letters. It opens with the store's block (T-E7-25): "Pijamas para toda la familia" and, below, Redes sociales (the networks the business sent, as round icon buttons in a row; T-E7-24, T-E7-40). Then four columns: Tienda (the header's navigation links: Todo, Mujer, Hombre, Niños and Temporada Invierno; T-E7-25), Ayuda y legales (FAQ, "Cómo comprar", "Guía de talles" (T-E7-29), "Cambios y devoluciones", "Términos y condiciones" (T-E7-28), "Política de privacidad", "Política de cookies" (T-E6-06) and the withdrawal link (RNF-30.1)), Contacto (the WhatsApp number, to the chat, and the email, to a new message, each after its outline icon; T-E7-24; then the opening hours after a clock and the address after a pin, the address a link to Google Maps in a new tab; T-E7-26) and Medios de pago (one icon per enabled provider, in a row, and under "Envíos" the Correo Argentino icon; T-E7-26). The column titles are `--accent-text` and the links `--ink`. On a phone the store's block takes the whole row and the columns sit two by two (Contacto and Medios de pago across both, the latter with its providers and "Envíos" side by side, their titles on one line); below 360 px, where two columns of links would break most of them over two lines, every column takes the whole row (T-E7-40); from 640 px likewise; from 960 px the block and the four columns share one row, with a vertical line after the block. Below them, after a top border, a strip with "Cookies" and "© {year} Pijama Party Santa Fe" (T-E7-20).
5. With the WhatsApp number (D-15), every public page except the checkout (`/checkout` and below) and the account (`/cuenta` and below) shows a round WhatsApp button fixed at the bottom right corner, named "Escribinos por WhatsApp", that opens the chat in a new tab (T-E7-27). While the "Contactanos" section of the home page (UC-30) is on screen, the button hides (fades out and leaves the tab order), since that section carries its own WhatsApp button (T-E7-42).
6. Right above the footer of the home page (`/`, with or without filters in the query) and of no other page (T-E7-23), a benefits bar (T-E7-21) with three equal blocks, each an icon on the left and, on its right, a bold uppercase title over a regular text: "Enviamos tu compra" (a truck; "Entregas dentro de Santa Fe Capital y todo el país"), "Todas las formas de pago" (a card; "Pagá con" and the enabled providers, e.g. "Pagá con MercadoPago o MODO") and "Comprá con seguridad" (a closed padlock; "Tus datos siempre protegidos"). Each block is a card (T-E7-43): white, with a fine `--line` border, rounded corners and a 3 px pastel rainbow hairline (`--rainbow-across`) on top; its icon sits in a soft pink circle (`--rose-soft`) and is drawn in `--accent-text`. The band has no background of its own and no top line, so the white cards stand out from the cream page. From 860 px the three cards sit in a row, 20 px apart; below, they stack at the full width, 12 px apart, the border and the gap being the only separators. The store's logo is not used as a background (it is a script with ornaments that would blur under the text and lower its contrast).

**Alternative flows**
- No threshold, or `GET /api/shipping/rates` fails: the ticker leaves out "Envío gratis desde $X". No enabled provider yet, or `GET /api/checkout/payment-providers` fails: it leaves out "Pagá con". "Envíos por Correo Argentino" is always there, so the ticker always shows and the header never moves down when the reads arrive.
- A footer page that does not exist yet: its link is not shown (the privacy and cookies policies of T-E6-06 exist now, so theirs are).
- A contact or social link the business has not sent yet (D-15): it is not shown. Without the email, the WhatsApp number, the hours and the address, there is no Contacto column; Redes sociales shows only the networks with a link, in the footer as in the phone menu; without any, there is no Redes sociales block (T-E7-40: the dashed placeholders are gone).
- No enabled provider, or `GET /api/checkout/payment-providers` fails: the Medios de pago column shows only "Envíos", and the benefits bar shows only its shipping and security blocks.

**Business rules**
- Navigation links: Todo → `/`; Mujer, Hombre, Niños → `/?publico=mujer|hombre|infantil`; Temporada Invierno → `/?temporada=invierno`. The current one is marked with `aria-current="page"`.
- A navigation link with a filter takes the home page to "Todos los productos", as when it opens with filters (UC-30).
- The account icon shows at every width (T-E7-39): on a phone it sits between the search and the cart, the logo still centered. Below 374 px the header's icon buttons (menu, search, account, cart) are 40 px, and below 350 px 36 px, so the three on the right fit beside the logo.
- With a session, the account icon (named "Mi cuenta", drawn in `--accent-text` with a small `--accent` dot, so the session does not show by color alone) opens a list with the greeting, "Mis pedidos" (UC-22), "Mis direcciones" (UC-20), "Mi cuenta" (UC-27) and "Salir"; Escape, a click outside or choosing a link closes it. The phone menu lists the same links open, instead of "Ingresar" and "Crear cuenta".
- Categories in the phone menu (T-E7-37, T-E7-39): with active categories, "Todo" is a button with a chevron pointing right that opens the "Todo" level: "Ver todo" (a link to `/`) and the active categories by position (UC-01), each a link to `/categoria/{slug}`, the current one marked like the current navigation link (`aria-current="page"`, bolder, with a dot). Without categories, while they load, or when they cannot be read, "Todo" is a plain link to `/`. Opening a level moves the focus to its "Volver" button; "Volver" returns to the menu with the focus on the entry that opened the level; the level not shown is out of the tab order. Escape or the close button closes the whole menu, which opens again at its first level.
- Shortcuts in the phone menu (T-E7-37, T-E7-39): the account comes first, after the person icon of the header: signed out, "Ingresar", a link to the sign-in page (which offers "Crear cuenta"; the menu has no "Ingresar" and "Crear cuenta" buttons any more); with a session, "Mi cuenta" with a chevron, which opens the account level with the greeting, "Mis pedidos", "Mis direcciones", "Mi cuenta" and "Salir". "Mi carrito" shows the cart's units like the cart button and is named with them for screen readers ("Mi carrito, 3 productos"); choosing it closes the menu and then opens the cart panel (UC-08), whose close returns the focus to the menu button. "Guía de talles" opens `/guia-de-talles`. The menu has no "Mis favoritos" (the wishlist is out of scope) and no promotional block: there is no source of promotions, and nothing is promised that the owner has not decided (D-01, D-02).
- The ticker shows no installments or discounts (D-01, D-02). It moves at about 40 px per second and stops while the pointer is over it or the keyboard focus is inside it; a pause button at its right end, named "Pausar anuncios" (then "Reanudar anuncios"), stops and restarts it (WCAG 2.2.2). Under reduced motion it does not move and shows its first message centered, with no pause button. Screen readers read the messages once, as a list, not the repeated copy that makes the loop.
- Seller data (RNF-30.2) joins the footer with T-E6-21; it is not part of this use case.
- The benefits bar promises no payment method, installments, discount or surcharge the checkout does not have (D-01, D-02): its payment block names only the enabled providers. The uppercase titles are written in sentence case and uppercased in CSS, so screen readers do not spell them.
- The payment icons are the store's own drawings (a card for MercadoPago, a phone for MODO), not the brands' logos; each carries the provider's name as its accessible name. The social icons are simple outline drawings in 44 px circles with a `--line-strong` border, with the network's name as the link's accessible name; on hover or keyboard focus a pink gradient (`--rose`, starting outside the circle so the white drawing keeps 3:1, to `--accent-hover`) blooms from the center of the circle until it fills it (`scale` 0 to 1, `--dur-base`, `--ease-out`) and the drawing turns white; leaving, it shrinks back. Under reduced motion the gradient only fades in (T-E7-24, T-E7-40). The phone menu's icons, bare at rest, bloom the same circle behind them (T-E7-40).
- The WhatsApp number is shown as `+54 9` followed by the rest of the digits of `storeContact.ts` (T-E7-24).
- The hours and the address are texts the business writes as they should read (for example "Lunes a viernes de 9 a 18 h" and "San Martín 2000, Santa Fe"); the address opens `https://www.google.com/maps/search/?api=1&query={address}` (T-E7-26).
- The WhatsApp button (T-E7-27) is 56 px, `--accent` with a white drawing of a round speech bubble with a filled phone inside (the store's own, not WhatsApp's logo, drawn in its style) and a soft shadow; it pops in once when the page opens, and on hover it turns `--accent-hover` and grows slightly (not under reduced motion). It sits 16 px from the right and bottom edges plus the safe area, and rises above the product page's add-to-cart bar while that bar shows (below 860 px). While it shows, the page keeps its space free: keyboard focus scrolls clear of it (`scroll-padding-bottom`) and the footer ends above it. The open cart panel, the phone menu and the consent banner cover it.
- Help pages (T-E7-28), fixed texts of the storefront in `es.ts` like the FAQ (T-E5-12), each with its title, description and canonical link (T-E6-23):
  - `/como-comprar`: five numbered steps that follow the real flow: choose size and color on the product page (with its size guide), review the cart (quantities and coupon), complete the 3-step checkout without an account (data and shipping cost by province, payment method, review), pay with the provider, and follow the order from the link of the confirmation email, where Correo Argentino's tracking number appears once shipped.
  - `/cambios-y-devoluciones`: the exchanges policy of D-18 and, apart, the 10-day withdrawal right with the link to the withdrawal page (UC-29) and the refund through the same payment method; how to ask (WhatsApp or email with the order number).
  - `/terminos`: acceptance (the version in force is saved with the order, RNF-30.6), products and prices (final prices in ARS with taxes included, the price confirmed at payment is kept, photos and colors may vary slightly), purchase and payment (the methods offered when finishing the purchase, not named, since the enabled ones may change (D-01, D-02); stock is held 30 minutes while paying; the order is confirmed once the payment is approved; a failed payment can be retried from the order's link until the hold ends, then the order expires, UC-14), shipping (Correo Argentino, cost by province shown before paying, delivery times of the FAQ), exchanges and withdrawal (links to both pages), personal data (Ley 25.326, and deleting the account from Mi cuenta, UC-28; nothing else is promised until T-E6-21), applicable law (Argentina; the consumer may claim at the courts of their domicile) and contact. The seller's identification goes in the footer with T-E6-21 and is not repeated here.
  - Until T-E6-21 validates them, the policy and the terms are drafts: they change through `es.ts`, and `store.ordering.terms-version` changes with them (RNF-30.6).
- `/guia-de-talles` (T-E7-29, RNF-31.5): the size guides the administrator keeps per category in the back office, one section per active category that has one, by the categories' position: the category's name, the table (size first, then the columns, scrolling sideways inside its box) and the note. Without any guide, "Todavía no hay guías de talles." and the FAQ link; if they cannot be read, the error with "Reintentar". Read from `GET /api/catalog/size-guides` (public, the catalog's rate limit): `{ items: [{ category: { slug, name }, columns, rows: [{ size, values }], note }] }`, the same guide the product page shows. Use case `ListSizeGuides` (`catalog.application`) over the existing ports `LoadCategories` and `LoadSizeGuides`; listed in the sitemap.
- Correo Argentino is the only carrier (UC-21). Its icon is the store's own drawing of a parcel, not the carrier's logo, named "Correo Argentino" (T-E7-26).
- Below 860 px the header stays at the top of the screen but hides while the visitor scrolls down and comes back as soon as they scroll up (T-E7-15). It stays shown within its own height of the top of the page, while the phone menu, the search, the cart panel or the account list is open, and while the keyboard focus is inside it. From 860 px it never hides.
- Once the page has scrolled past the header's height, at any width, the header's background turns slightly transparent and blurred, so the page shows through it; within that height of the top it keeps its usual background (T-E7-16). The logo, the links and the icons keep their contrast.
- From 860 px, past that same height, the header turns into a floating pill (T-E7-30): it keeps everything (logo, navigation, search, account and cart) on a fully rounded, 52 px tall background about 12 px from the top of the screen, with the translucent blurred background of T-E7-16, a thin line around it and a soft shadow; the page shows around it, and the logo shrinks from 48 to 40 px. Back within that height of the top it opens again into the full-width bar. The bar fades out while the pill fades in from slightly larger, in about 450 ms with a soft ease-out; only `transform` and `opacity` animate, never sizes or the blur, so the change stays smooth in every browser, Firefox included (T-E7-39). With reduced motion the change is instant. The space the header takes in the page does not change, so the page never jumps. Below 860 px nothing changes (T-E7-15 and T-E7-16 stay).

**Acceptance criteria**
- Given a free-shipping threshold of $50,000 and MercadoPago enabled, when any public page opens, then the ticker lists "Envío gratis desde $50.000", "Envíos por Correo Argentino" and "Pagá con MercadoPago"; without a threshold, it lists the last two (T-E7-39).
- Given the ticker running, when "Pausar anuncios" is pressed, then it stops and the button reads "Reanudar anuncios"; pressed again, it runs again (T-E7-39).
- Given the checkout or the order page, when the header shows, then it has the logo, the account icon and the cart button, but no navigation, menu button or search.
- Given a phone, when the visitor scrolls down past the header, then it hides; when they scroll up, then it shows again. Given a computer, when the visitor scrolls down, then it stays.
- Given a hidden header on a phone, when the keyboard focus moves into it, then it shows.
- Given reduced motion, when the header hides or shows, then it does so without sliding.
- Given a page scrolled past the header, when the header shows, then its background is translucent; given the page back at the top, then it is the usual one.
- Given a phone, when the menu opens, then it shows the navigation and the account links, the focus stays inside it, and Escape closes it and returns the focus to the menu button.
- Given the active categories "Pijamas" and "Camisones", when "Todo" is chosen in the menu, then the "Todo" level slides in with "Ver todo" linking to `/` and both categories linking to `/categoria/{slug}`, and the focus is on "Volver"; when "Volver" is chosen, then the first level shows again with the focus on "Todo" (T-E7-37, T-E7-39).
- Given no categories, when the menu opens, then "Todo" is a link to `/` (T-E7-37, T-E7-39).
- Given `/categoria/camisones`, when the "Todo" level opens, then "Camisones" is the current page (T-E7-37, T-E7-39).
- Given a visitor, when the menu opens, then its first shortcut is "Ingresar", linking to `/cuenta/ingresar`, and there is no "Crear cuenta"; given a signed-in customer, then it is "Mi cuenta", which opens a level with "Mis pedidos", "Mis direcciones", "Mi cuenta" and "Salir" (T-E7-39).
- Given 2 units in the cart, when the menu opens, then "Mi carrito" is named "Mi carrito, 2 productos"; when it is chosen, then the menu closes and the cart panel opens (T-E7-37).
- Given the WhatsApp number, when the menu opens, then its foot links "Preguntas frecuentes" and "Escribinos por WhatsApp" to `https://wa.me/{number}` in a new tab; without the number, only the FAQ (T-E7-37).
- Given a visitor on a computer or a phone, when the header shows, then the account icon named "Ingresar" links to `/cuenta/ingresar`, and the header has no "Crear cuenta" (T-E7-39 on the phone).
- Given a signed-in customer on a computer, when "Mi cuenta" is opened, then it lists "Mis pedidos", "Mis direcciones", "Mi cuenta" and "Salir", and Escape closes it and returns the focus to "Mi cuenta".
- Given "Temporada Invierno", when chosen, then the grid shows only `WINTER` products.
- Given any public page, when the footer is checked, then the withdrawal link is visible without signing in.
- Given MercadoPago and MODO enabled, when the footer is checked, then Medios de pago shows their two icons in a row, named "MercadoPago" and "MODO".
- Given a contact email and WhatsApp number, when the footer is checked, then Contacto links the number to `https://wa.me/{number}` in a new tab and the email to `mailto:{email}`; without either, there is no Contacto column.
- Given Instagram and TikTok links, when the footer is checked, then Redes sociales shows two links named "Instagram" and "TikTok" that open in a new tab; given only Instagram, then only its link shows; given neither, then there is no Redes sociales (T-E7-40).
- Given any public page, when the footer is checked, then it shows "Pijamas para toda la familia", and Tienda has the header's five navigation links, each opening the same listing (T-E7-25).
- Given opening hours and an address, when the footer is checked, then Contacto shows the hours and links the address to Google Maps in a new tab; without them, neither shows (T-E7-26).
- Given any public page, when the footer is checked, then Medios de pago shows "Envíos" with an icon named "Correo Argentino", also when the payment providers fail (T-E7-26).
- Given any public page, when the footer is checked, then Ayuda y legales links "Preguntas frecuentes", "Cómo comprar", "Cambios y devoluciones" and "Términos y condiciones" (T-E7-28).
- Given an active category with a guide, an inactive one with a guide and an active one without, when `/guia-de-talles` opens, then it shows only the first, with its columns, its rows in the administrator's order and its note; the footer links it (T-E7-29).
- Given no guides, when `/guia-de-talles` opens, then it says there are none yet; given the request failing, then it shows the error and "Reintentar" (T-E7-29).
- Given `/como-comprar`, `/cambios-y-devoluciones` or `/terminos`, when it opens, then it shows its heading, its texts and its description for search engines; the exchanges page links the withdrawal page and the terms link both (T-E7-28).
- Given the WhatsApp number, when the home, a product or the FAQ page opens, then the "Escribinos por WhatsApp" button links to `https://wa.me/{number}` in a new tab; given `/checkout` or `/cuenta/pedidos`, then there is no button; without the number, there is none on any page (T-E7-27).
- Given the home page, when the "Contactanos" section comes on screen, then the floating WhatsApp button hides; when the section leaves the screen or the visitor moves to another page, then it shows again (T-E7-42).
- Given MercadoPago and MODO enabled, when the home page opens, then above the footer the benefits bar shows "Enviamos tu compra", "Todas las formas de pago" with "Pagá con MercadoPago o MODO" and "Comprá con seguridad"; given the providers failing, then only the first and the last. Given any other page, such as `/cuenta/ingresar`, when it opens, then there is no benefits bar.

**Design notes**
- `SiteHeader`, `MobileMenu` (a modal `dialog`, like the filter panel of UC-02) and `SiteFooter` replace the header and `StoreFooter` of `StoreLayout`. The back office keeps its own layout.
- The threshold comes from `GET /api/shipping/rates` (RNF-31.6), one TanStack Query shared by the strip, the product page and the cart panel.
- Payment provider names from `GET /api/checkout/payment-providers`, the query the checkout and the purchase summary already share. The contact email, the WhatsApp number, the opening hours, the address and the social links live in `storeContact.ts`, empty until the business sends them (D-15).
- `ContactSection` (D-09) closes the home page after the withdrawal block: "Contactanos" with a `mailto:` link and a `https://wa.me/{number}` link, no form.
- The footer keeps the "Cookies" link of RNF-25 next to the withdrawal link.
- Hiding and translucent header: `useHeaderScroll` (`features/search`) listens to `scroll` passively and reads the position once per frame; it hides after 8 px down past the header's height and shows after 8 px up, and does nothing while an open modal `dialog` (menu, search, cart panel) is on the page. It sets `data-scrolled` on `header.site` past the header's height (the background drops from 92% to 75% of `--bg` with a 12 px blur, a color change in CSS) and `data-hidden`; the CSS moves it with `transform: translateY(-100%)` only below 860 px, keeps it shown under `:focus-within`, and slides it only under `prefers-reduced-motion: no-preference`.
- Pill header (T-E7-30, T-E7-39): from 860 px, `data-scrolled` also turns the header into the pill, in CSS only. The full-width background, blur and bottom line are a `::before` of `header.site` that fades out, because a parent with `backdrop-filter` would keep the pill's own blur from reaching the page. The pill is a `::after` of `.header-row` (6 px from its top and bottom, so 52 px tall), fully rounded, with the translucent background, the blur, a 1 px ring and the shadow, hidden (`opacity: 0`, `scale(1.02, 1.08)`) until `data-scrolled`. `header.site` keeps its 64 px and `.header-row` its size; the row moves down 6 px (`translateY`) and the logo shrinks with `scale(0.84)`. Only `opacity` and `transform` transition, with `--dur-slow` and `--ease-out`, only under `prefers-reduced-motion: no-preference`: the first version also transitioned `max-width`, `height`, `padding` and `backdrop-filter`, which re-laid out the row and recomputed the blur on every frame and stuttered in Firefox.

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
- The photo goes through the UC-06 conversion: WebP at 400, 600, 800 and 1200 px and the blurred placeholder. Replacing it deletes the old files after the new ones are saved.
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
- A card shows the product's colors with available stock as swatches (D-04), at most 3 plus "+N", and the "Nuevo" label (D-05). The "Sold out" label of UC-01 stays. Both labels are minimal (Apple HIG: content first, few strong colors): 10 px semibold uppercase text, slightly spaced, on a frosted-glass `--surface` tag at 95 % with 4 px corners, a hairline edge and a faint inner highlight (both labels stay above 4.5:1 over a black photo, checked by a test), "Nuevo" in `--accent-text` after a small `--accent` dot with a soft halo, the store's identity, and "Agotado" in `--ink-soft`.
- A card shows the product's sizes on the row of its swatches, at its right end, "S · M · L" in Open Sans (T-E7-35); when both do not fit, as on a phone card with many colors, the sizes go whole to the next line, under the swatches, and neither group breaks inside: those of its active priced variants, in display order, at most 4 plus "+N", so on a phone card they keep to the swatches' row in almost every product. A size without available stock in any color is struck through and dimmed. Screen readers hear only the sizes with stock ("Talles: S y L"), as the swatches read only the colors with stock; with none, they hear nothing, since "Sold out" already says it. Open Sans loads only once a card's sizes come within 300 px of the screen, so on the home page, whose grid starts below the first screen, it never competes with the first photo (RNF-11); until it arrives the sizes show in the body font, and the swap keeps the row's height.
- The filters open from a bar of buttons (Público, Talle, Color, Material, Temporada, Precio). Each button opens the UC-02 panel at its group; a button whose group has values chosen says how many. The UC-02 rules, panel and URL keys do not change. Buttons of groups without options are not shown, unless a shared link chose a value in that group (the panel keeps it to uncheck, UC-02).
- The listing shows the number of matches (D-11): "1 producto" or "N productos", at the right end of the filter bar row, as in the prototype. On phones it stays in view while the buttons scroll (T-E7-11).
- The visitor can switch between grid and list views. The choice is per visit and does not change the URL.
- "Ver más" stays (D-10), as a tinted capsule button (Apple HIG: a secondary action is tinted, not filled; at least 44 pt tall): "Ver más productos" with a chevron down in `--accent-text` on `--rose-soft`. Above it, how far the visitor got: "Viste 24 de 134" over a thin bar filled in that proportion. While the next page loads the chevron turns into a spinner (none under reduced motion, where it stays still), the button says "Cargando…" and is `aria-busy`.
- Name and price on the card: the name 13 px medium (500) in `--ink`, the price 14 px semibold (600) in `--ink`, prices in whole pesos unless the price has cents (`formatMoney`).
- On a device with hover (`(hover: hover) and (pointer: fine)`), a card whose product has a second photo shows it while the pointer is over the card (T-E7-14). The list view shows the first photo only and never downloads the second.
- On a touch device (`(hover: none)`), in the grid view, a card whose product has a second photo shows two dots under its photo, and a horizontal swipe on the photo slides between the first and the second (T-E7-32), with the gallery's swipe rules (UC-03): the photo follows the finger, a short or slow swipe springs back, a vertical move scrolls the page, and the swipe does not open the product. The second photo is requested only when a finger first touches that card's photo. The dots are decorative; a tap still opens the product.

**Acceptance criteria**
- Given a product with stock in pink and none in beige, when listed, then its card shows only the pink swatch.
- Given a product with sizes S, M and L where M has no stock in any color, when listed, then its card shows "S · M · L" with M struck through, and screen readers hear "Talles: S y L".
- Given a product with two photos on a computer, when the pointer rests on its card, then the second photo shows over the first; given a product with one photo, then the first one stays.
- Given a phone, when a listing with products of two photos loads, then no second photo is requested.
- Given a phone and a product with two photos, when its card's photo is swiped to the left, then the second photo shows, its dot is marked and the product does not open; when a card's photo is tapped, then the product opens.
- Given a product created 10 days ago and one created 40 days ago, when listed, then only the first carries "Nuevo".
- Given 30 products matching the filters, when the listing opens, then it reads "30 productos", and 24 cards show with "Ver más".
- Given the "Talle" button, when pressed, then the filter panel opens with the focus on the size group.

**Design notes**
- `ProductListingItem` gains `colors: List<String>` (active priced variants with available stock, in display order, the same rule as the UC-02 color filter) and `isNew: boolean`. `Product.isNew(Instant now)` holds the 30-day rule in `catalog.domain`; `ListProducts` and `SearchProducts` read `now` from the injected `Clock`.
- `ProductListingQuery` and `ProductSearchQuery` gain `count`: a `count(*)` over the same constant SQL conditions as the page (one shared `matching` part, RNF-28), in the same read-only transaction. The use case skips it when the page is the last one and not past the end: its offset plus its size is the total.
- Endpoint: `GET /api/catalog/products`, with or without `q` (UC-26) → `200` `{items, hasMore, total}`, each item adding `colors` and `isNew`. OpenAPI and the typed client are updated.
- Second photo (T-E7-14): `ListedProduct` and `ProductListingItem` gain `secondImage` (the photo after the first, in the same order: position, then id); the listing query reads both in its lateral join. Each item of `GET /api/catalog/products` adds `secondImage` (null without one). The card renders it as a second `img` with `loading="lazy"`, `display: none` except on hover devices, empty alternative text (the first photo carries the name), faded in with `opacity`.
- Sizes (T-E7-35): `ListedProduct` and `ProductListingItem` gain `sizes: List<SizeOffer>`, `SizeOffer(size, inStock)`: the sizes of the active priced variants in display order (size position, then text, the order of the UC-02 size filter), `inStock` when one of them has available stock. The listing query reads them in the same statement as `colors`. Each item of `GET /api/catalog/products`, with or without `q`, adds `sizes: [{size, inStock}]`. The card does not trust that order alone: it shows the sizes as the filters' panel does (`sortBySize`, `sizeGroups.ts`): letters from XXS to XXXL (S · M · L · XL), then numbers ascending (4 · 12 · 14), then any other size ("Único", "S/M") as the API gave it.
- "Desde" only when it means something: `ListedProduct` and `ProductListingItem` gain `priceVaries: boolean`, true when the product's active priced variants have more than one price (`min <> max` in the listing query's `listed` part). Each item of `GET /api/catalog/products`, with or without `q`, adds `priceVaries`.
- Frontend: `ColorSwatches` (shared with UC-03), `FilterBar`, the view toggle (`ViewToggle`; the view is kept in `sessionStorage` for the visit, grid when storage is refused). The color map normalizes names (lowercase, no accents) and is unit tested, including the swatch border contrast against the card background (3:1).

---

## Changes to UC-03 (product page)

**Business rules (new)**
- Breadcrumbs: Inicio / {category} / {product}. No separate "Volver al catálogo" link: the breadcrumbs already lead back.
- Below 860 px (T-E7-51) the breadcrumbs are pastel pills on one line (`flex-wrap: nowrap`, `overflow-x: auto` with the scrollbar hidden, so a long trail scrolls sideways and the page never does): Inicio is a 36 px `--surface` pill with a house icon (`aria-hidden`; the link keeps "Inicio" as visually hidden text, so its name does not change), the category a `--rose-soft` pill in `--accent-text`, the product in bold `--ink`, at most 60% of the width and cut with "…". Between them, instead of "/", a 7 px four-point sparkle in `--spark-pink` drawn with `clip-path` (no text for screen readers). The pills are 36 px tall and each link's tap area reaches 44 px. From 860 px, the trail stays as before.
- Gallery: main photo, thumbnails, and an enlarged view (the 1200 px file) when the photo is pressed. From 860 px the thumbnails are a vertical strip to the left of the main photo; on phones they stay a row below it.
- Gallery motion (inspired by the ver.com.ar product page): the photos sit side by side and slide (`--dur-slow`, `--ease-out`) to the picked one, whether it is picked by thumbnail, by a swipe or a mouse drag that follows the finger, or by the arrow keys. A short or slow drag springs back. Past the first and the last photo the drag resists instead of looping. Vertical scrolling of the page is never captured. The picked thumbnail stays in sight inside its strip. Only the shown photo and its neighbours load eagerly.
- Enlarged view: it moves between the photos with previous and next buttons, a swipe and the arrow keys, fading one photo into the next, and shows "n / total". Pressing the photo zooms it in to 2x around the pressed point, and the zoomed photo follows the pointer or finger. Pressing it again zooms out. Moving to another photo or closing the view resets the zoom. The photo it shows is the main photo when it closes.
- With reduced motion the slide, the fade and the zoom are instant.
- Color as swatches (D-04) with the chosen color's name; sizes as square buttons; a quantity input from 1 to the available stock of the chosen variant, a pill like the add button, 52 px tall (T-E7-41). With one unit left ("Queda 1 disponible") there is no quantity to pick: the control is not shown and the add button takes the whole row. A disabled "−" or "+" shows at 30% opacity with `not-allowed`.
- The add button (T-E7-41) is a 52 px pill with a faint light edge on top and a soft shadow in `--accent`; its face holds the bag icon of the header, "Agregar al carrito" and, once a variant is picked, the subtotal (unit price × quantity, display only: the cart prices every add, R-01) after a thin divider. The subtotal is hidden from screen readers, so the button's name stays "Agregar al carrito". The fixed bar's button has no subtotal: the bar already shows the price.
- On phones, when the add button leaves the screen, a fixed bar at the bottom shows the price and the add button. Under the price it says what is picked ("Talle M · Rosa"; only the picked one of the two, nothing without picks), so the visitor knows what the button adds without scrolling back.
- The product's name is the page's largest text but not a wall: 24 px on phones (above the 22 px price) and 28 px from 860 px, line height 1.25, lines balanced (`text-wrap: balance`). It shows as the administrator typed it.
- Trust icons (D-12) take the place of the RNF-31.6 purchase summary below the add button, so its lines are not repeated: "Envío gratis desde $X" (only with a threshold), the delivery times, and "Pagá con …" naming the payment methods enabled now (MercadoPago, MODO), with the link to the FAQ. A line that could not be read is left out.
- The price stands out over the description: 22 px, bold, in `--ink`. The picked color carries a 3 px ring and a small `--accent` check badge, so the pick reads at a glance on any tone; the picked size is filled with `--accent` (T-E7-22). Swatches are 40 px and sizes at least 48 × 48 px, each a 48 px touch target (Apple HIG asks 44 pt).
- The shipping cost by province (D-07), the label "Calculá el envío" above the province select (at most 320 px wide) with the cost below, without a box (T-E7-22, T-E7-41); still by province, never by postal code. Collapsible sections: composition (material and season) and exchanges and returns (link to the FAQ). The size guide opens from the "Talle" label instead (T-E7-36).
- Order of the info column: name, price, description, picker, quantity and add button, then the collapsible sections, then the shipping cost by province and the trust icons last. From 860 px the info column is sticky (`position: sticky`, `align-self: flex-start`), so the add button stays on screen while the photo scrolls.
- "Completá tu look" (D-08), full width below the page layout, above the reviews (UC-24); no longer in the info column, which would always be taller than the photo and could not stick.
- The UC-03 variant rules do not change.
- Size guide (T-E7-36, RNF-31.5): when the product's category has a guide, a "Guía de talles" link sits at the right end of the "Talle" label and opens the guide (the same table and note as `/guia-de-talles`) in a modal dialog that rises from the bottom; Escape, a tap outside or its close button close it, and the focus returns to the link. Without a guide there is no link.
- Missing picks (T-E7-36): the add button stays enabled while the size or the color is not picked. Pressed without them, it adds nothing, says what is missing below each group ("Elegí un talle", "Elegí un color") and moves the focus to the first group missing a pick (the size first). Each message goes away as soon as its group is picked; the size one is not shown while no size can be picked for the chosen color, since the stock note says why. The fixed bar's button does the same. The button is still disabled while an add is in progress and when nothing that matches the picks has stock (the picked size and color, or a color sold out in every size). With a color sold out in every size picked and no size yet, the stock note below the picker says "Sin stock en este color", so the disabled button is explained.
- A color without stock in the picked size (T-E7-36) is crossed by a diagonal line besides being dimmed, so the sold-out state does not depend on its tone alone.
- "Vistos recientemente" (T-E7-33): every product page that loads remembers its slug in the browser (`localStorage`, at most 8, the newest first, the same product once). It holds no personal data and is never sent to the server or to analytics. Below "Completá tu look", it shows the last 4 viewed products other than the open one as cards, newest first; on the home page (UC-30) it shows the last 4, between "Elegí para quién" and "Todos los productos". Each card reads the product's current detail (`GET /api/catalog/products/{slug}`), so name, photos, price and stock are the server's: a product no longer listed is dropped from the list. These cards carry no "Nuevo" label (the detail does not say it). Nothing shows while they load, when there is none to show, or when the browser refuses its storage.

**Alternative flows**
- The category has no other listed products: no "Completá tu look".
- A province without shipping: "No hacemos envíos a esa provincia".

**Acceptance criteria**
- Given a product page, when quantity 2 is chosen and added, then the cart gets 2 units of that variant (UC-08).
- Given a quantity above the available stock, when added, then the UC-08 stock message shows.
- Given a product page on a phone, when scrolled until the add button is off screen, then the fixed bar with the price and the button appears; pressing it adds as the main button does.
- Given a variant with one unit available, when it is picked, then the quantity control is not shown and the add button takes the whole row (T-E7-41).
- Given a variant picked, when the quantity is 2, then the add button shows the subtotal of 2 units and its accessible name does not change (T-E7-41).
- Given a province without shipping, when chosen on the product page, then it says there is no shipping to it.
- Given a category with 6 other products, when a product page opens, then "Completá tu look" shows the 4 newest, without the open product.
- Given a category with no other products, when a product page opens, then "Completá tu look" is not shown.
- Given a category with a size guide, when "Guía de talles" is pressed, then a dialog shows its table and note; when it is closed, the focus returns to the link (T-E7-36).
- Given a category without a size guide, when a product page opens, then there is no "Guía de talles" link (T-E7-36).
- Given no size and no color picked, when the add button is pressed, then nothing is added, "Elegí un talle" and "Elegí un color" show and the focus moves to the sizes; when a size is picked, its message goes away (T-E7-36).
- Given a color sold out in every size, when it is picked, then the add button is disabled and the stock note says "Sin stock en este color" (T-E7-36).
- Given products A, B and C viewed in that order, when the home page opens, then "Vistos recientemente" shows C, B and A; when B's page opens, then it shows C and A.
- Given a viewed product no longer listed, when "Vistos recientemente" shows, then it is left out and forgotten.
- Given a first visit, when the home page opens, then "Vistos recientemente" is not shown.

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
- Empty, the panel shows the account pages' chibi (the shared `Chibi`, T-E7-48), 120 px, `asleep` and hugging a pillow (`cart` pose), above "Tu carrito está vacío.", and "Empezar a comprar" takes the panel's whole width inside its side padding. Below it, the rest of the panel's height is kept for "Productos sugeridos", a future task; until then that place renders nothing (T-E7-19).
- "Lo más nuevo" (T-E7-34) fills that place: the 4 newest listed products that are not sold out, as cards in two columns, from the first page of the listing sorted by newest (UC-01). Opening one closes the panel. The same block shows below the links of a search without results (UC-26). Nothing shows while it loads, when it fails or when there is no product to show.

**Acceptance criteria**
- Given an empty cart, when the panel opens, then it shows the sleeping chibi with its pillow (hidden from screen readers), "Tu carrito está vacío." and a full-width "Empezar a comprar" that closes the panel.
- Given an empty cart and 6 listed products, one of the 4 newest sold out, when the panel opens, then "Lo más nuevo" shows the 4 newest not sold out; opening one closes the panel and opens its page.
- Given a search without results, when its page shows, then "Lo más nuevo" shows below the category links.
- Given a $50,000 threshold and a $24,999 subtotal after discount, when the panel opens, then it reads "Te faltan $25.001 para envío gratis".
- Given a subtotal after discount of $50,000 or more, when the panel opens, then it reads "¡Tenés envío gratis!".
- Given no threshold, when the panel opens, then there is no bar.

**Design notes**
- `FreeShippingProgress` reads the shared rates query; the amounts are `Money` from the API, never recomputed prices. Before the threshold is known it shows nothing.
- The notice renders inside the cart panel, in its header over the title, as tall as the header's buttons (the prototype moves it to the top while the drawer is open): the modal dialog makes the page behind it inert, so a notice outside it could not be read or closed. Closing the panel closes the notice.
- The empty state is a column: the chibi, the texts and the button at the top, then `.cart-suggestions` with `flex: 1`, an empty `div` until "Productos sugeridos" fills it. The chibi is decorative (`aria-hidden`): the text says the cart is empty.

---

## Changes to UC-13 (checkout)

**Business rules (new)**
- From 640 px, short fields share a row: DNI and phone; city and province; number, floor and apartment.
- Without a session, step 1 offers "¿Ya tenés una cuenta? Iniciar sesión". After signing in, the customer comes back to `/checkout` with the cart (UC-08 merges the guest cart).
- **"Nombre y apellido" becomes two fields, "Nombre" and "Apellido" (D-13)**, each 1 to 50 characters, trimmed. The order keeps both (R-04). The other fields, validations and steps of UC-13 do not change.
- Signed in, the checkout prefills them from the account name: the first word in "Nombre", the rest in "Apellido", both editable. The account keeps a single name (UC-19).
- The paid-order and withdrawal emails greet the "Nombre" as typed ("Hola, María José"). Tracking keeps showing the first name only (UC-14: the link can be forwarded), now the "Nombre" as typed; the back office shows "Nombre Apellido".
- Layout (T-E7-38): from 900 px the checkout is centered, at most 1080 px wide; the form takes the remaining width and the summary a 380 px column 48 px to its right, its top level with the step indicator, sticky while the page scrolls. Below 900 px, one column with the summary after the form.
- Step indicator (T-E7-38): each step is a numbered circle followed by its name ("Envío", "Pago", "Revisión"), joined by a line. The current one is a filled `--ink` circle with its name in bold (`aria-current="step"`); a completed one shows a check in an `--accent` circle and says "completado" to screen readers; a later one is an outlined circle in `--ink-soft`. Not by color alone: the check and the bold name tell them apart.
- Summary (T-E7-38): each item's photo is 64 px wide (4:5); under the name, "{color} · Talle {size}" and, on its own line, "Cantidad: {n}"; the line amount on the right. Under the title, "Volver al carrito" opens the cart panel (UC-08), where quantities and the coupon change; the checkout keeps what was typed. The total sits after a line, larger and bolder than the other amounts; a free shipping reads "Gratis" in `--accent-text`. The amounts stay the server's (nothing is added up).
- Final action (T-E7-38): on step 3, "Confirmar y pagar · {total}" with the total the summary shows, as wide as the form column, and "Volver" below it. Under the button, in small type, where the payment happens for the chosen provider: a fact of the flow, not a security promise. Since T-E3-29 a MercadoPago buyer types the card on the store's order page, so the note reads "Vas a ingresar los datos de tu tarjeta en el paso siguiente, sin salir de la tienda."; MODO's still reads "Vas a completar el pago en MODO.", since MODO takes the buyer to its own page (T-E7-46). The button stays enabled before the terms are accepted: pressed, it marks the checkbox with its message and moves the focus to it, as UC-13 already does, so the buyer is told what is missing (like the add button of T-E7-36). While the request is in flight it shows "Confirmando…" with the progress bar and is disabled (UC-13).
- Review blocks (T-E7-45): "Contacto y envío" keeps its one "Editar" (to step 1) but groups its lines under two faint subtitles: "Contacto" (name, email, phone and DNI) and "Entrega" (the address, the city with province and postal code, and the method: "Correo Argentino a domicilio · {shipping}", the shipping cost of the summary, or "Gratis"). Correo Argentino is the only carrier (UC-21), so there is no method to choose. "Medio de pago" shows the provider's name and, under it, the hint of step 2 (e.g. "Tarjetas de crédito y débito"; T-E7-46 dropped "dinero en cuenta", which the card form of T-E3-29 does not offer). No brand logo (UC-31) and no security seal: the note under the button stays a fact of the flow (T-E7-38).
- Summary items (T-E7-45): the line amount sits on the "Cantidad: {n}" line, at its right, instead of floating beside the whole item.
- Terms (T-E7-45): the checkbox stays (RNF-30.6, `TermsNotAccepted`). The whole sentence is its label, so a tap anywhere on it (outside the two links) ticks it.

**Acceptance criteria**
- Given the review with a shipping of $5.000, when it shows, then "Contacto y envío" has "Contacto" with the buyer's lines and "Entrega" with the address and "Correo Argentino a domicilio · $5.000"; with free shipping, "Correo Argentino a domicilio · Gratis" (T-E7-45).
- Given MercadoPago chosen, when the review shows, then "Medio de pago" reads "MercadoPago" and "Tarjetas de crédito y débito" (T-E7-45, T-E7-46).
- Given the review, when the buyer taps the terms sentence, then the checkbox is ticked (T-E7-45).
- Given the review step with a total of $219.600 and MercadoPago chosen, when it shows, then the button reads "Confirmar y pagar · $219.600" and the note below it "Vas a ingresar los datos de tu tarjeta en el paso siguiente, sin salir de la tienda." (T-E7-38, T-E7-46); with MODO chosen, "Vas a completar el pago en MODO." (T-E7-46).
- Given step 2, when the step indicator is read, then "Envío" is completed, "Pago" is the current step and "Revisión" is neither (T-E7-38).
- Given the checkout with typed data, when "Volver al carrito" is chosen, then the cart panel opens and, closed again, the typed data is still there (T-E7-38).
- Given an item with 4 units, when the summary shows, then it reads "Cantidad: 4" and its line amount is 4 times the unit price as the server sent it (T-E7-38).
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

---

## Changes to UC-14 (order page)

The return from the payment (`/checkout/result`) opens the order page, `/pedido/{token}`, so this page is also the purchase's confirmation (T-E7-44). Nothing here changes the API: the page shows only what `GET /api/orders/{token}` sends, which leaves out the email, the phone, the national ID and the street because the link can be forwarded (UC-14, UC-17).

**Business rules (new)**
- A paid order is one in `PAID`, `PREPARING`, `SHIPPED` or `DELIVERED`. Only a paid order thanks the buyer: the celebration never shows on the return from the provider alone, since only the webhook confirms the payment (UC-15). While the order is `AWAITING_PAYMENT`, `CANCELLED` or `EXPIRED`, the page keeps "Hola, {Nombre}", "Pedido #{number}" and the status label of UC-14.
- Paid header: the account pages' chibi (the shared `Chibi`, T-E7-48), 120 px, in `success` with the `paid` pose, and at its lower right a 40 px seal: a soft `--success-bg` circle with a check drawn in `--success` (the stroke draws itself once in `--dur-slow` with `--ease-out`; under reduced motion it shows drawn); both decorative (`aria-hidden`), the heading "¡Gracias por tu compra, {Nombre}!" (the "Nombre" capitalized, as UC-14 greets) and below it one line by status: `PAID` "Recibimos tu pago y vamos a preparar tu pedido.", `PREPARING` "Ya estamos preparando tu pedido.", `SHIPPED` "Tu pedido está en camino.", `DELIVERED` "Tu pedido fue entregado.".
- Order number: "Pedido #{number}" in a badge, with a "Copiar" button that copies `#{number}` and then reads "Copiado" for 2 seconds (announced to screen readers). Without the browser's clipboard (`navigator.clipboard`), there is no button; a copy that fails says "No se pudo copiar".
- Progress, on a paid order instead of the status label: four steps in a row, "Pagado", "En preparación", "Enviado" and "Entregado" (the glossary states, as UC-14 names them). The steps before the order's state show a check in an `--accent` circle and say "completado" to screen readers; the current one is a filled `--ink` circle with its name in bold (`aria-current="step"`) and, under `prefers-reduced-motion: no-preference`, a soft pulse while it is not the last; the later ones are outlined in `--ink-soft`. Not by color alone: the check and the bold name tell them apart, as the checkout's steps (T-E7-38).
- Delivery, on a paid order, in its own block "Entrega" in the main column (the summary then leaves out the ship-to line): "Correo Argentino a domicilio en {city}, {province}" and, while `PAID` or `PREPARING`, the delivery time of the FAQ for the order's province: "Llega en 1 a 3 días hábiles desde que se acreditó el pago." for Santa Fe, "Llega en 3 a 7 días hábiles desde que se acreditó el pago." for the rest. Then "Te enviamos la confirmación y el enlace de este pedido por email." without the address (UC-17), followed by the email correction of UC-17 as today.
- WhatsApp, on any order the page finds, with the store's number (UC-36): a link "¿Tenés alguna duda sobre tu pedido? Escribinos por WhatsApp" to `https://wa.me/{number}?text=…` in a new tab, the message prefilled with "¡Hola! Hice el pedido #{number} y quería consultar…". Only the order number goes in the message, never the buyer's data. Without the number, no link.
- Closing, on any order the page finds: "Volver a la tienda" (to `/`) and, with the Instagram link (UC-36), "Seguinos en Instagram" to it in a new tab.
- Layout: one column on a phone (header, progress, payment, delivery, WhatsApp, then the summary, the email correction and the closing); from 900 px the main column on the left and the summary sticky on the right, as today.
- Paying (T-E7-50): a 72 px shared `Chibi` sits left of the payment message, in a row with it (`.tracking-pay-feedback`). Mood `waiting` with the `paying` pose (three little stars orbiting over its head) while the card is being charged (`POST /api/orders/{token}/card-payment` pending, with the status message "Procesando tu pago…"), while the answer is `inProcess` and in "Estamos confirmando tu pago" after an approved MODO return; mood `error` next to the alert when the card answer is a refusal (`rejected`, `chargeInProgress`, `providerUnavailable`, `tooManyAttempts`) or the request fails, with the card form still above it. No chibi while nothing is under way, nor on an `approved` answer: the order is read again and turns paid, and the header's chibi celebrates. `aria-hidden`; the message keeps its `role="status"` or alert.

**Acceptance criteria**
- Given a `PAID` order of "alan", when the page opens, then it reads "¡Gracias por tu compra, Alan!", "Recibimos tu pago y vamos a preparar tu pedido." and "Pedido #1001", and the progress has "Pagado" as the current step (T-E7-44).
- Given a paid order, when the page opens, then the header has the chibi in `success` with the `paid` pose and the check seal, both `aria-hidden`; a pending, cancelled or expired order has no celebrating chibi (T-E7-48).
- Given a pending order with the card form, when the card is sent and until the API answers, then a `waiting` chibi with the `paying` pose is beside the message; on an `inProcess` answer it stays (T-E7-50).
- Given a rejected card, when the API answers, then an `error` chibi is beside the refusal's alert and the card form is still there (T-E7-50).
- Given an approved MODO return, when the page opens on the pending order, then a `waiting` chibi with the `paying` pose is beside "Estamos confirmando tu pago" (T-E7-50).
- Given a pending order with nothing under way, when the page opens, then there is no chibi (T-E7-50).
- Given an order `AWAITING_PAYMENT` after an approved return, when the page opens, then there is no thanks and it says the payment is being confirmed; when the order turns `PAID`, then the thanks shows (T-E7-44).
- Given a `SHIPPED` order, when the progress is read, then "Pagado" and "En preparación" are completed, "Enviado" is current and "Entregado" is neither (T-E7-44).
- Given a `PAID` order to Santa Fe, when the page opens, then "Entrega" reads "Correo Argentino a domicilio en Santa Fe, Santa Fe" and "Llega en 1 a 3 días hábiles…"; to Córdoba, "Llega en 3 a 7 días hábiles…"; given a `SHIPPED` order, no delivery time (T-E7-44).
- Given "Copiar", when it is pressed, then `#1001` is in the clipboard and the button reads "Copiado" (T-E7-44).
- Given the store's WhatsApp number, when the page opens, then the WhatsApp link opens `https://wa.me/{number}` with the order number in its message; without the number, no link (T-E7-44).
- Given the Instagram link, when the page opens, then "Seguinos en Instagram" links to it in a new tab, and "Volver a la tienda" to `/` (T-E7-44).
- Given any order, when the page opens, then it shows no email, phone, national ID or street (T-E7-44).

---

## The chibi in empty states (T-E7-49)

The shared `Chibi` (T-E7-48) joins three screens with nothing to show, 120 px and centered above their text. Their texts, links and buttons do not change; `.empty-state`, which error and filter states share too, is not restyled: the chibi is its own element (`.empty-state-chibi`).

**Business rules (new)**
- Search without results (UC-26): mood `puzzled` (head tilted, eyes to the side, "o" mouth), pose `search`: a magnifier in the right hand and a "?" over the head.
- The 404 page (RNF-31.7): mood `puzzled`, pose `lost`: an open map held in both hands and the "?". The page's heading, text and link sit in the same centered column as the other empty states.
- "Mis pedidos" without orders (UC-22): mood `idle`, pose `orders`: a small empty shopping bag in the right hand, no "zZz".
- Decorative (`aria-hidden`, `focusable="false"`); inline SVG with a fixed box, so no request and no shift.

**Acceptance criteria**
- Given a search with no results, when it shows, then a `puzzled` chibi with the `search` pose (magnifier and "?") is above the message, hidden from screen readers (T-E7-49).
- Given an unknown path, when it opens, then a `puzzled` chibi with the `lost` pose (map and "?") is above "No encontramos esta página" (T-E7-49).
- Given a customer without orders, when "Mis pedidos" opens, then an `idle` chibi with the `orders` pose (the bag) is above "Todavía no hiciste ningún pedido." (T-E7-49).

---

## Changes to UC-19 (sign-in, registration and password pages)

The header no longer has "Crear cuenta" (UC-31), so the sign-in page carries it, and the sign-in, registration, password recovery and reset pages are redesigned (T-E7-23). The flows, messages and API of UC-19 and UC-27 do not change.

**Business rules (new)**
- The four pages sit in a card (`--surface`, `--radius-m`, a soft shadow) up to 440 px wide, with the title and a one-line intro inside it. Sign-in's intro reads "Ingresá para ver tus pedidos y comprar más rápido.", the reset page's "Elegí la contraseña con la que vas a ingresar de ahora en más."; registration and recovery keep their own. The confirmation and invalid-link messages show in the card instead of the intro.
- Beside the card, the chibi in a pastel night sky with a moon, stars, clouds and the store's "Pijama Party" sign (T-E7-47; its gestures are in "Motion"). The sign is cut out of the store's logo (`treinta-photos/_store-logo.jpg`) with its frame and cream inside, as a transparent WebP in `src/assets/account/` (about 30 KB, 407 × 345 px) with `width` and `height`. The sky's scene (gradient, stars, moon, clouds) is clipped to its own box; the chibi and the sign sit on a stage over it, the sign in front. From 860 px the page is one split card up to 960 px wide (the form card's border, `--radius-m` and shadow around both halves, `overflow: hidden`): the sky on the left, stretched to the form's height (at least 520 px), the form on the right in a 440 px column without a frame of its own; the moon is in the sky's top right corner. The stage is a box 76% of the sky wide (at most 380 px, so it fits the 520 px minimum of the short pages with room for the "zZz") near its bottom: the sign fills it in the foreground, and the chibi (60% of the stage wide, both sized from the stage so they keep fitting at any sky height) peeks over the sign's frame, its face and resting hands above the edge. Below 860 px the stage makes no box of its own (`display: contents`): the sky is a low band on top, with the sign on the left, the moon on the right and the chibi peeking over the card's top edge; on a 375 × 667 px phone the card's title and first field show without scrolling. The drawing is decorative (`aria-hidden`, no text), inline SVG with no image request, and neither covers nor moves the form: the band reserves its height, so nothing shifts when Motion loads.
- Recovery ends with "¿Te acordaste de tu contraseña?" and an outlined "Volver a ingresar"; the reset page's new password takes the eye button below and keeps its hint. The social buttons (UC-27) and the email form follow, as before.
- Sign-in: "¿Olvidaste tu contraseña?" sits on the password's label row, on the right. Below the form, after a divider, "¿Primera vez acá?" and a full-width outlined "Crear cuenta" button to `/cuenta/registro`. Registration ends the same way with "¿Ya tenés cuenta?" and an outlined "Ingresar".
- The email, password and name fields start with a decorative 18 px line icon (envelope, padlock, person; `aria-hidden`, the label names the field), in `--ink-soft`, `--accent-text` while the field has the focus (T-E7-47). A focused field drops the page's outline for a 1 px `--accent` edge and a 3 px `--focus-ring` around it, drawn as shadows so nothing moves; the resting border stays `--line-strong` (3.5:1, WCAG 1.4.11).
- "Continuar con Google" and "Continuar con Facebook" (UC-27) start with the provider's 18 px mark in its brand colors (Google's four-color "G", Facebook's blue "f"), as their sign-in branding guidelines ask; decorative (`aria-hidden`), since the text names the provider (T-E7-47).
- Password fields carry an eye icon button inside the field, on the right (accessible name "Mostrar contraseña", `aria-pressed` while shown; the eye is crossed out while shown), which switches it between hidden and shown. Email fields turn off spell checking and automatic capitals.
- The submit button stays enabled until the request starts, then reads "Ingresando…" / "Creando…" as before. Sending with an empty field asks nothing of the server: each empty field shows its message below it ("Escribí tu email.", "Escribí tu contraseña.", "Escribí tu nombre y apellido."), is marked `aria-invalid`, and the focus moves to the first one. Typing in a field clears its message.

**Acceptance criteria**
- Given the sign-in page with an empty email, when "Ingresar" is pressed, then no request is sent, and the email says "Escribí tu email." and has the focus.
- Given a typed password, when "Mostrar contraseña" is pressed, then the password shows as text; pressed again, it hides.
- Given the sign-in page, when it opens, then "Crear cuenta" links to `/cuenta/registro`.
- Given the recovery page with an empty email, or the reset page with an empty password, when it is sent, then no request is sent and the field says what is missing and has the focus.
- Given a 1280 px wide screen, when any of the four pages opens, then the chibi's sky is left of the card and as tall as it; given a 375 px wide screen, then the band is above the card (T-E7-47).
- Given any of the four pages, when it opens, then the "Pijama Party" sign is on the stage after the chibi (in front of it), with an empty `alt`, `width` and `height` (T-E7-47).
- Given the sign-in page, when the focus enters the password, then the chibi is in `password`; when "Mostrar contraseña" is pressed, `peek`; when the focus goes back to the email, `text`; when it leaves the form, `idle` (T-E7-47).
- Given the sign-in page, when the API refuses the credentials, then the chibi is in `error` until a field is typed in (T-E7-47).
- Given the registration page, when the account is created, then the chibi is in `success`; given the sign-in page, when the sign-in succeeds, then the next page opens with no wait for the chibi (T-E7-47).
- Given reduced motion, when the sign-in page opens and the shopper types, then the chibi has no loop, shake or hop, and its poses change at once, unanimated (T-E7-47).
- Given a screen reader, when it walks the sign-in page, then nothing of the chibi or its sky is read (T-E7-47).
- Given the sign-in, registration, recovery and reset pages, when they open, then the chibi's pose is `signIn`, `register`, `recover` and `reset`; only recovery shows the "?" and only reset the key (T-E7-47).
