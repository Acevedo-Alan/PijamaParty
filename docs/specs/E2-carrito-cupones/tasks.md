# E2 – Tareas

- [x] **T-E2-01** Migraciones: `carrito`, `carrito_item`, `cupon`. En el código: `cart`, `cart_item`, `coupon` y `coupon_redemption` (usos del cupón, se llena en E3); `cart` y `cart_item` nacen con Row Level Security activo y forzado, con sus políticas (RNF-27).
- [ ] **T-E2-02** Servicio de carrito: crear, agregar, modificar y quitar con validación de stock (CU-08, CU-09).
- [ ] **T-E2-03** Cálculo de totales en el servidor y aviso de cambios de precio o stock (CU-09).
- [ ] **T-E2-04** Cookie `cart_token` para invitados y limpieza diaria de carritos viejos.
- [ ] **T-E2-05** Servicio de cupones: validación y cálculo del descuento (CU-10).
- [ ] **T-E2-06** Backoffice: ABM de cupones y vista de usos (CU-11).
- [ ] **T-E2-07** Storefront: panel lateral del carrito con overlay, vibración y contador animado (CU-08).
- [ ] **T-E2-08** Storefront: edición de cantidades y aviso de ítems no disponibles (CU-09).
- [ ] **T-E2-09** Storefront: campo de cupón con mensajes de error claros (CU-10).
- [ ] **T-E2-10** Tests de los criterios de aceptación de CU-08 a CU-11.
- [ ] **T-E2-11** Activar las políticas de rate limiting de carrito y cupón (RNF-22).
- [ ] **T-E2-12** Evento de analítica `add_to_cart` en GA4 y Meta Pixel (RNF-25).
- [ ] **T-E2-13** Tests de las políticas de Row Level Security de `carrito` y `carrito_item` con cliente A, cliente B, invitado, administrador y sistema (RNF-27). Las políticas se crean en T-E2-01.

**Terminado cuando:** se puede armar un carrito, aplicar un cupón y recibir un error claro si una variante se agota.
