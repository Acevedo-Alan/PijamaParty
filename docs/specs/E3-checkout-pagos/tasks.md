# E3 – Tareas

- [x] **T-E3-01** Migraciones: `pedido`, `pedido_item`, `pago`, `costo_envio_zona`, `config_tienda`, `variante.stock_reservado`. En el código: `orders`, `order_item`, `payment`, `shipping_zone_rate`, `shop_setting` y `stock_level.reserved`; `orders`, `order_item` y `payment` nacen con Row Level Security activo y forzado, con sus políticas (RNF-27).
- [x] **T-E3-02** Backoffice y API: costos de envío por zona y envío gratis (CU-12).
- [x] **T-E3-03** Cálculo de envío en el servidor según provincia y subtotal (CU-12, CU-13).
- [x] **T-E3-04** Reserva atómica de stock y proceso de vencimiento (CU-16).
- [x] **T-E3-05** `POST /api/checkout`: validaciones, creación del pedido y reserva (CU-13, CU-14).
- [x] **T-E3-06** Integración con MercadoPago: creación del pago y redirección (CU-14).
- [x] **T-E3-07** Webhook idempotente con verificación del pago y transiciones de estado (CU-15).
- [x] **T-E3-08** Página de seguimiento por token y reintento de pago (CU-14).
- [x] **T-E3-09** Correo de pago confirmado al cliente y aviso al administrador, con tabla de salida en PostgreSQL, reintentos y servicio transaccional detrás de `EmailSender` (CU-17, RNF-23).
- [x] **T-E3-10** Backoffice: lista y detalle de pedidos, cambios de estado y número de seguimiento (CU-18).
- [x] **T-E3-11** Storefront: checkout de 3 pasos con layout de dos columnas en escritorio (CU-13).
- [x] **T-E3-12** Test de concurrencia: dos compras simultáneas de la última unidad (CU-16).
- [x] **T-E3-13** Tests de los criterios de aceptación de CU-12 a CU-18. Los criterios de idempotencia del checkout (CU-14) se prueban en T-E3-14; el `429` del checkout (CU-13) en T-E3-19; la compra registrada una sola vez (CU-14) en T-E3-20; los rebotes (CU-17, CU-18) en T-E3-22; la corrección del email y su `429` (CU-17) en T-E3-23; la sugerencia de dominios (CU-13) en T-E3-24.
- [x] **T-E3-14** Tabla `idempotency_key` y mecanismo de idempotencia para `POST /api/checkout`, con tests de repetición y de concurrencia (RNF-20.1, RNF-20.2).
- [x] **T-E3-15** Tabla `pago_evento` con restricción única, procesamiento idempotente del webhook y test con dos notificaciones simultáneas (RNF-20.3).
- [ ] **T-E3-16** Restricciones únicas de `movimiento_stock` y transiciones de estado condicionales (RNF-20.4, RNF-20.5).
- [ ] **T-E3-17** Cabecera de idempotencia hacia el proveedor de pagos derivada del número de pedido (RNF-20.8).
- [ ] **T-E3-18** Registros SPF, DKIM y DMARC del subdominio de envío y alerta de volumen diario de correos (RNF-23). *Requiere el dominio registrado.*
- [ ] **T-E3-19** Activar la política de rate limiting del checkout (RNF-22).
- [ ] **T-E3-20** Eventos de analítica `begin_checkout`, `add_payment_info` y `purchase`, este último una sola vez por pedido y solo con el pedido PAGADO (RNF-25).
- [ ] **T-E3-21** Tests de las políticas de Row Level Security de las tablas de pedidos, pagos, idempotencia y correos con cliente A, cliente B, invitado, administrador y sistema (RNF-27). Las políticas nacen con cada tabla: T-E3-01, T-E3-09, T-E3-14, T-E3-15 y T-E3-22.
- [ ] **T-E3-22** Webhook de rebotes y quejas del proveedor de correo, tabla `email_evento` y alerta en el pedido del backoffice (CU-17, CU-18).
- [ ] **T-E3-23** Corrección del email desde la página de resultado y el enlace de seguimiento, con reenvío y rate limiting (CU-17).
- [ ] **T-E3-24** Sugerencia de dominios de email mal escritos en el paso de envío (CU-13, *Propuesto*).
- [ ] **T-E3-25** Botón de arrepentimiento: enlace en inicio y pie, formulario, código de trámite, correo y aviso al administrador (CU-29).
- [ ] **T-E3-26** Backoffice: lista de solicitudes de arrepentimiento, marca de fuera de término y resolución (CU-29).
- [ ] **T-E3-27** Tests de los criterios de aceptación de CU-29, Row Level Security de `solicitud_arrepentimiento` y política de rate limiting (RNF-22, RNF-27).
- [x] **T-E3-28** Activar la política de rate limiting del backoffice autenticado: 300 solicitudes por minuto por usuario en `/api/admin/**`, para cupones, envíos y lo que se sume (RNF-22).
- [ ] **T-E3b-01** Averiguar la integración de MODO y escribir su plan antes de implementarla.
- [ ] **T-E3b-02** Integrar MODO como segundo medio de pago (CU-14).

**Terminado cuando:** una compra completa funciona en el entorno de prueba del proveedor, la última unidad no se vende dos veces, el webhook es idempotente y el pedido aparece en el backoffice.
