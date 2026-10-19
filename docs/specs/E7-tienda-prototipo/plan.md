# E7 – Plan técnico

## Enfoque

Casi todo es del frontend: componentes y CSS nuevos sobre los tokens de `tokens.css`, sin copiar el código del prototipo. El backend cambia solo en tres lecturas del catálogo. No hay migraciones.

## Cambios en la API

| Endpoint | Cambio | Para |
|---|---|---|
| `GET /api/catalog/products` y `GET /api/catalog/search` | Cada ítem suma `colors` (los colores con stock, en orden de carga) y `isNew` (D-05). La página suma `total` (D-11). | Tarjetas y contador |
| `GET /api/catalog/products/{slug}` | Suma `category` `{slug, name}` | Migas de pan y "Completá tu look" |

- `isNew` es una regla de dominio (`Product` con el `Clock` inyectado), nunca `Instant.now()` en el código.
- `total` es un `count(*)` con los mismos filtros, como constante de texto (RNF-28).
- Se actualizan OpenAPI y el cliente tipado (`npm run api:generate`).

## Frontend

- **Contenido fijo de la portada (D-03):** textos en `es.ts`; fotos en `frontend/src/assets/home/`, en WebP de 640, 1280 y 1920 px con `srcset`. La primera foto del carrusel se precarga (es el LCP de la portada) y las demás cargan de forma diferida.
- **Mapa de colores (D-04):** un módulo de `catalog` con el nombre normalizado (minúsculas, sin acentos) y su tono, con tests de contraste del borde.
- **Componentes nuevos:** `HomePage` (portada), `HeroCarousel`, `AudienceCards`, `SiteHeader` con `MobileMenu` (un `dialog` modal), `SiteFooter`, `ContactSection`, `FilterBar`, `ColorSwatches`, `QuantityInput`, `StickyAddToCart` (con `IntersectionObserver`), `ProductLightbox`, `ShippingByProvince`, `RelatedProducts`, `FreeShippingProgress` y `Toast`.
- **Lecturas que ya existen:** el umbral de envío gratis y los costos por provincia salen de `GET /api/shipping/rates` (RNF-31.6), con una sola consulta compartida por TanStack Query para la franja, la ficha y el carrito.
- **Rutas:** `/` pasa a `HomePage`, que contiene la grilla de `CatalogPage`; `/categoria/{slug}` sigue en `CatalogPage`.
- **Animaciones (D-16):** tokens de duración y curva en `tokens.css` (ARCHITECTURE.md sección 11), solo CSS y la API de animaciones del navegador, sin librerías. Las animaciones que ya existen (salto del carrito, paneles, esqueletos) pasan a usar los tokens.
- **Movimiento y hover:** según ARCHITECTURE.md sección 11 (`prefers-reduced-motion`, `@media (hover: hover)`).

## Pruebas

- Tests de componentes (Vitest) por cada criterio de aceptación del frontend; tests de API y de dominio para `colors`, `isNew` y `total`.
- Los escenarios e2e de T-E6-01 se actualizan si cambian textos o pasos (por ejemplo, el ingreso por el menú del celular).
- Lighthouse de T-E6-02 sobre la portada nueva: LCP menor a 3 s y accesibilidad de 90 o más.
- Capturas comparativas contra el prototipo al cerrar la etapa (celular y computadora).

## Riesgos

- **El LCP de la portada:** el carrusel pone una foto grande arriba de todo. Se mitiga con WebP en varios tamaños, precarga de la primera foto y sin JavaScript bloqueante antes de ella.
- **Los materiales del negocio (D-15):** sin fotos propias, la portada queda con fondos degradados. La etapa puede avanzar y las fotos se suman cuando lleguen.
- **Las promesas comerciales del prototipo:** cuotas, descuento por transferencia y cambio sin cargo quedan afuera hasta que el dueño decida (D-01, D-02, D-12).
