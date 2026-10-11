# E1 – Tareas

- [x] **T-E1-01** Migraciones: `categoria`, `producto`, `variante`, `imagen`, `movimiento_stock`, `usuario` (CU-04 a CU-07). En el código: `category`, `product`, `variant`, `product_image`, `stock_level`, `stock_movement` y `app_user` (el stock sale de la variante, ARCHITECTURE.md sección 12).
- [x] **T-E1-02** Autenticación de administrador con sesión y CSRF, y bloqueo por intentos (CU-04).
- [x] **T-E1-03** ABM de categorías en backend (CU-05). La pantalla del backoffice va en T-E1-12.
- [x] **T-E1-04** ABM de productos y generación de la matriz de variantes (CU-06).
- [x] **T-E1-05** Subida y procesamiento de imágenes: WebP, miniaturas y placeholder (CU-06).
- [x] **T-E1-06** Ajustes de stock e historial de movimientos (CU-07).
- [x] **T-E1-07** API pública: lista con filtros, orden y paginación (CU-01, CU-02).
- [x] **T-E1-08** API pública: detalle de producto con stock disponible por variante (CU-03).
- [x] **T-E1-09** Storefront: grilla de productos, "Agotado" y "Ver más" (CU-01).
- [x] **T-E1-10** Storefront: panel de filtros mobile y filtros en la URL (CU-02).
- [x] **T-E1-11** Storefront: página de detalle, selector de talle y color, galería y blur-up (CU-03).
- [x] **T-E1-12** Backoffice: pantallas de login, categorías, productos y stock (CU-04 a CU-07).
- [x] **T-E1-13** Tests de los criterios de aceptación de CU-01 a CU-07. El criterio de la foto de 8 MB reducida en el celular (CU-06) se prueba en T-E1-22.
- [x] **T-E1-14** Límite de 2 conversiones de imagen simultáneas (RNF-21).
- [x] **T-E1-15** Activar las políticas de rate limiting de login, catálogo y búsqueda (RNF-22).
- [ ] **T-E1-16** *Propuesto:* segundo factor TOTP para el administrador (RNF-24).
- [x] **T-E1-17** Migración de búsqueda: extensión `unaccent`, columna `busqueda` e índice GIN (CU-26).
- [ ] **T-E1-18** Backend: búsqueda y sugerencias, y recálculo de `busqueda` al guardar productos y variantes (CU-26).
- [ ] **T-E1-19** Storefront: barra de búsqueda con sugerencias y pantalla de búsqueda en celular (CU-26).
- [ ] **T-E1-20** Tests de los criterios de aceptación de CU-26.
- [ ] **T-E1-21** Backoffice responsive desde 360 px en todas las pantallas (RNF-26).
- [ ] **T-E1-22** Carga de fotos desde el celular: selección múltiple, reducción en el navegador, progreso y reintento idempotente (CU-06, RNF-26).
- [ ] **T-E1-23** Banner de consentimiento y cargador de analítica (GA4 y Meta Pixel) que solo se activa tras aceptar (RNF-25).
- [ ] **T-E1-24** Eventos de analítica del catálogo: `view_item_list`, `view_item` y `search` (RNF-25).

**Terminado cuando:** el dueño carga un producto con su matriz de variantes y fotos desde el celular, un visitante lo encuentra filtrando o buscando por texto, y una variante sin stock figura agotada.
