# E8 – Panel de administración completo

**Estado:** Borrador; alcance de E8a aprobado el 2026-10-20 · **Casos de uso:** CU-33 a CU-37 y CU-39 a CU-45 (CU-38 descartado), y cambios a CU-06, CU-07 y CU-18 · **Contraparte en inglés:** `docs/use-cases/E8-admin-panel.md` (E8a)

## Objetivo

Que el negocio se maneje entero desde `/admin`, como en una tienda profesional: al entrar se ve un resumen del negocio (CU-33) y desde ahí se llega a cada sección. Hoy el backoffice ya tiene diez pantallas (pedidos, arrepentimientos, productos, variantes y stock, categorías y guía de talles, cupones, reseñas, envíos y portada), pero entra directo a "Productos", no muestra números del negocio ni clientes, los reembolsos y los cambios se hacen por fuera y los datos de contacto de la tienda están fijos en el código.

El punto de partida es la lista de secciones que pasó el dueño el 2026-10-20 (22 secciones, pensada para una tienda de ropa genérica). El 2026-10-20 el dueño pidió ampliar el alcance a lo recomendable para un sitio profesional. Esta spec cruza la lista con lo que ya está hecho y con las decisiones de `00-dominio-y-reglas.md`, y la reparte en tres fases que se pueden publicar por separado.

## Lo que ya existe

| Sección pedida | Dónde está hoy |
|---|---|
| Productos: alta, edición, fotos (hasta 8), descripción, precio por variante, talles y colores, matriz de variantes, categoría, publicar u ocultar | CU-06, `/admin/productos` |
| Categorías: alta, edición, orden, activar o desactivar, borrar si no tiene productos | CU-05, `/admin/categorias` |
| Stock por talle y color, ajustes con nota, historial de movimientos, sin venta con stock 0 | CU-07 y R-03 |
| Pedidos: lista por estado, detalle con productos, talle, color, comprador, pago y envío, cambio de estado, cancelación con o sin reponer stock, número de seguimiento | CU-18, `/admin/pedidos` |
| Cupones: porcentaje o monto fijo, vigencia, usos máximos, compra mínima, por productos o categorías, automáticos por reglas (temporadas, Black Friday, fin de temporada) | CU-11 y CU-23, `/admin/cupones` |
| Reseñas: aprobar o rechazar | CU-25, `/admin/resenas` |
| Envíos: costo por provincia y envío gratis desde un monto | CU-12, `/admin/envios` |
| Contenido: banner de personajes de la portada | CU-32, `/admin/portada` |
| Arrepentimientos (devoluciones exigidas por ley) | CU-29, `/admin/arrepentimientos` |
| Seguridad: ingreso con contraseña y segundo factor, bloqueo por intentos, sesión de 60 minutos, permisos verificados en el servidor, RLS sobre los datos de clientes | CU-04, RNF-24, RNF-27 |
| Backoffice usable en celular, tablet y computadora | RNF-26 |
| Respaldos | E6 (T-E6) |

## Fases

| Fase | Contenido | Para qué | Estimación inicial |
|---|---|---|---|
| **E8a – Control diario** | CU-33 Resumen, CU-34 Clientes, CU-36 Datos de la tienda, CU-37 Actividad; búsquedas y lista de stock (cambios a CU-06, CU-07 y CU-18); remito para imprimir; menú nuevo | Ver el negocio de un vistazo y encontrar cualquier cosa en segundos | 2 sem |
| **E8b – Operación** | CU-39 Avisos al dueño, CU-40 Pagos, CU-41 Cambios de talle o color, CU-35 Reportes (con costo y ganancia) | Enterarse de lo importante sin entrar, registrar los cambios y medir el negocio | 1,5 sem |
| **E8c – Vender mejor** | CU-42 Reembolsos desde el pedido, CU-43 Precio anterior, CU-44 Destacados, CU-45 Respuesta a reseñas | Ofertas y vidriera como una tienda grande, y devoluciones de dinero sin salir del panel | 1,5 sem |

Cada fase deja el backoffice completo y usable; E8b y E8c dependen del registro de actividad de E8a.

El backoffice sigue con un único administrador, el dueño (CU-04): los usuarios y roles (CU-38) se descartaron el 2026-10-20 (D-E8-01).

## E8a – Control diario

### CU-33 Resumen del negocio (dashboard)

Página inicial de `/admin`; reemplaza la redirección a "Productos".

- **Para hacer hoy:** pedidos `PAGADO` sin preparar, pedidos `PREPARANDO`, arrepentimientos `RECIBIDA`, cambios abiertos (E8b), reseñas `PENDIENTE`, pedidos con alerta de correo rebotado, pedidos `ENVIADO` sin número de seguimiento y variantes agotadas. Cada número lleva a la lista ya filtrada.
- **Ventas:** total vendido y cantidad de pedidos de hoy, de los últimos 7 días y del mes, con la variación contra el período anterior, y un gráfico de ventas por día (30 días) o por mes (12 meses). Cuenta como venta un pedido `PAGADO` o posterior, sin los cancelados.
- **Ticket promedio** del mes.
- **Stock bajo:** variantes activas de productos publicados con disponible igual o menor que el umbral de la tienda (CU-36), y las agotadas.
- **Más vendidos:** los 5 productos con más unidades vendidas en los últimos 30 días.
- **Últimos pedidos:** los 5 más recientes.
- **Totales:** productos publicados y clientes registrados, con los nuevos del mes.

### CU-34 Clientes

- Lista de clientes registrados, 50 por página, con búsqueda por nombre o email y filtro por fecha de alta.
- Ficha del cliente: nombre, email, teléfono, fecha de alta, direcciones, sus pedidos, cantidad de compras, total gastado y fecha de la última compra.
- Solo lectura: el cliente se da de baja él mismo (CU-28).
- Los datos personales siguen RNF-27 y R-05: se ven solo en el backoffice, nunca se registran en logs y la búsqueda no se manda a la analítica. Abrir la ficha queda en el registro de actividad.

### CU-36 Datos de la tienda

Lo que hoy está fijo en `storeContact.ts` (D-15 de E7) pasa a editarse desde el backoffice, más algunos ajustes de operación:

- Contacto: email, WhatsApp, Instagram, Facebook y TikTok. La tienda los lee del servidor; un dato vacío no se muestra, como hoy. Los datos del vendedor del pie (RNF-30.2) no cambian.
- Franja de anuncios del encabezado: texto opcional de hasta 80 caracteres (por ejemplo "Envío gratis a todo Santa Fe"); vacía, se muestra la franja de envío gratis de hoy.
- Umbral de stock bajo: uno para toda la tienda, 3 unidades por defecto.

### CU-37 Registro de actividad

Cada cambio hecho desde el backoffice queda registrado: precios, stock, publicar u ocultar, estados de pedidos, cupones, reseñas, envíos, portada y datos de la tienda (y, cuando lleguen, reembolsos y cambios). Se guardan la fecha, la acción, sobre qué fue y qué cambió (antes y después), sin datos personales del comprador. Se ve en "Actividad", 50 por página, filtrable por tipo y fecha. Las filas no se editan ni se borran; se conservan 2 años.

### Cambios a casos existentes

- **CU-18:** buscar un pedido por número (`1001` o `#1001`), por nombre o email del comprador, y filtrar por rango de fechas además del estado. Botón "Imprimir remito" en el detalle: una página para imprimir con número de pedido, productos, talles, colores, cantidades, nombre, teléfono y dirección de envío, sin precios.
- **CU-06:** buscar productos por nombre o SKU, y filtrar por categoría, estado (publicado u oculto) y stock (con stock, bajo, agotado).
- **CU-07:** pantalla "Stock" con todas las variantes, filtrable por stock bajo o agotado, con ajuste rápido en la misma fila (mismas reglas e idempotencia de CU-07).
- **Navegación:** menú lateral agrupado en Resumen; Ventas (pedidos, pagos, arrepentimientos, cambios, clientes); Catálogo (productos, categorías, stock); Marketing (cupones, destacados, reseñas, portada); Envíos; Reportes; y Configuración (datos de la tienda, actividad), más "Salir". Cada pantalla se suma al menú cuando llega su fase. En el celular, el menú se abre desde un botón.

## E8b – Operación

### CU-38 Usuarios del backoffice y roles (descartado)

Descartado por el dueño el 2026-10-20 (D-E8-01): sigue un único administrador. El número no se reutiliza.

### CU-39 Avisos al dueño

Correos al email del dueño, que se carga en "Datos de la tienda" (CU-36 suma el campo en E8b), por el outbox de correos existente:

- Pedido pagado: número, total y productos, sin DNI ni dirección.
- Resumen diario a las 8 (hora de Argentina): variantes que quedaron con stock bajo o agotadas, arrepentimientos y cambios abiertos, reseñas pendientes y pedidos pagados sin preparar desde hace más de 2 días hábiles.
- Arrepentimiento recibido (tiene plazo legal).
- Cada aviso se puede apagar desde "Datos de la tienda". Son idempotentes (un aviso por pedido o por día). No cambian la regla de un solo correo al comprador.

### CU-40 Pagos

- Lista de pagos, 50 por página, filtrable por estado (pendiente, aprobado, rechazado, reembolsado), proveedor y fecha: pedido, proveedor, monto, estado, referencia del proveedor y fecha.
- Los rechazados muestran el motivo que informa el proveedor.
- Alerta de pagos aprobados sobre pedidos vencidos o cancelados (hoy está solo en el detalle del pedido).
- Solo lectura; las credenciales siguen siendo secretos de Docker y nunca pasan por la web.

### CU-41 Cambios de talle o color

En pijamas el cambio de talle es lo más común, y hoy se resuelve por WhatsApp sin registro.

- Desde el detalle de un pedido `ENTREGADO`, el administrador registra un cambio: qué línea, cuántas unidades, y por qué variante del mismo producto (otro talle o color, mismo precio).
- Estados: `ABIERTO` → `RECIBIDO` (llegó la prenda: vuelve al stock, si el administrador indica que está en condiciones) → `ENVIADO` (sale la nueva, con número de seguimiento: se descuenta del stock) → `CERRADO`; o `ABIERTO` → `RECHAZADO`.
- Si la variante nueva no tiene stock, no se puede registrar.
- Motivo del cambio de una lista fija (talle chico, talle grande, color, falla, otro) más una nota; alimenta el reporte de productos más cambiados.
- Sin diferencia de precio: un cambio a otro producto o con otro precio es una devolución (CU-29 o CU-42) y una compra nueva.

### CU-35 Reportes

- Ventas por día, semana o mes en un rango de fechas: pedidos, unidades, total vendido, descuentos, envío y ticket promedio.
- Ventas por producto, categoría, talle y color (más y menos vendidos).
- Ganancia bruta por producto y por período: necesita el **costo** de cada variante (cambio a CU-06: campo opcional, solo en el backoffice); sin costo cargado, el producto aparece como "sin costo".
- Stock: unidades por variante, valor a precio de venta y a costo.
- Clientes: nuevos por mes y porcentaje que vuelve a comprar.
- Cancelaciones, arrepentimientos, cambios (con sus motivos) y reembolsos del período.
- Cupones: usos y descuento otorgado por cupón.
- Exportar cada reporte a CSV (Excel lo abre). Ningún CSV incluye DNI ni direcciones; exportar queda en el registro de actividad.

## E8c – Vender mejor

### CU-42 Reembolsos desde el pedido

- Desde el detalle de un pedido con pago aprobado, el administrador reembolsa el **total** por MercadoPago, con confirmación. El pedido pasa a `CANCELADO` (si no lo estaba, con la decisión de reponer stock de CU-18) y el pago a `REEMBOLSADO`. El estado del pedido no cambia de diagrama: el reembolso es del pago.
- Idempotente: lleva `Idempotency-Key` y el proveedor recibe una clave propia, así un reintento nunca devuelve dos veces.
- Un arrepentimiento resuelto ofrece el reembolso desde su pantalla.
- Reembolsos parciales y MODO quedan afuera hasta decidir T-E3b-06; mientras tanto el detalle sigue indicando hacerlo desde el panel del proveedor.

### CU-43 Precio anterior (tachado)

- Cada variante puede tener un precio anterior mayor que su precio; la tienda muestra el anterior tachado y el porcentaje de descuento en la tarjeta y en la ficha, y un filtro "En oferta" en el listado.
- Es solo para mostrar: el carrito y el checkout cobran el precio, y los cupones aplican sobre él como hoy.
- El precio anterior debe haber sido el precio real de venta (validar el texto y la práctica con un profesional, RNF-30); por eso se carga con vigencia (desde y hasta) y vuelve a vacío al vencer.

### CU-44 Destacados

- El administrador elige hasta 12 productos publicados y su orden para una sección "Destacados" de la portada, debajo del banner de personajes.
- Un producto oculto o agotado sale de la sección sin perder su lugar.

### CU-45 Respuesta a reseñas

- El administrador responde una reseña aprobada con un texto de hasta 500 caracteres; la ficha muestra la respuesta como "Respuesta de Pijama Party" debajo de la reseña.
- Se puede editar o borrar; queda en el registro de actividad.

## Fuera de alcance

| Pedido | Por qué no entra |
|---|---|
| Usuarios del backoffice, roles y permisos | Descartado por el dueño (D-E8-01): sigue un único administrador con segundo factor (CU-04) |
| Lista de deseos y aviso de reposición | Son funciones de la tienda, no del panel: van en una etapa de tienda con sus propios casos de uso. Sus reportes llegan con ellas |
| Tickets de soporte y mensajes | La tienda atiende por email y WhatsApp (D-07, D-09 de E7); un sistema de tickets no rinde con este volumen |
| Carrito abandonado y newsletter | Requieren consentimiento y contradicen la regla de un solo correo al comprador |
| Subcategorías, imágenes de categoría y marcas | La navegación es por público y temporada (CU-31); la tienda vende su propia marca |
| Borrar productos | Se ocultan: el historial de stock y las reseñas los referencian (CU-06) |
| Logo, carrusel, diseño de la portada | Fijos en el frontend (D-03); cambiarlos es un cambio de diseño |
| Páginas legales y preguntas frecuentes editables | Los textos los valida un profesional (RNF-30, RNF-31.6); un cambio pasa por esa validación |
| Moneda, idioma, zona horaria, impuestos | Fijos: ARS, español, Buenos Aires (R-01, R-07); el IVA va contenido en el precio (RNF-30) |
| Configurar medios de pago y credenciales | Secretos de Docker (RNF-29) |
| Facturas | Factura electrónica (ARCA) pendiente con el contador |
| Desactivar cuentas de clientes | El cliente elimina su cuenta (CU-28); bloquear clientes no es necesario hoy |

## Decisiones

| ID | Tema | Estado | Propuesta |
|---|---|---|---|
| D-E8-01 | Usuarios y roles | Descartado (2026-10-20) | Sigue un único administrador, el dueño (CU-04). No hay CU-38. |
| D-E8-02 | Lista de deseos | Recomendado | En una etapa de tienda posterior, no en E8. |
| D-E8-03 | Umbral de stock bajo | Decidido (2026-10-20) | Uno para toda la tienda, 3 unidades por defecto, editable (CU-36). |
| D-E8-04 | Reembolsos | Decidido (2026-10-20) | Total, por MercadoPago, desde el pedido (CU-42, E8c). Anotado en `00-dominio-y-reglas.md`; CU-18 cambia con E8c. |
| D-E8-05 | Desactivar clientes | Recomendado | No. |
| D-E8-06 | Costo y ganancia | Recomendado | Costo opcional por variante, solo en el backoffice (CU-35). |
| D-E8-07 | Cambios de talle o color | Recomendado | Registrados desde el pedido, mismo producto y precio (CU-41). |
| D-E8-08 | Avisos al dueño | Recomendado | Por correo, apagables (CU-39). |
| D-E8-09 | Precio anterior y destacados | Recomendado | Sí, con vigencia y validación legal del texto (CU-43, CU-44). |
| D-E8-10 | Remito para imprimir | Decidido (2026-10-20) | Sí, desde el detalle del pedido, sin precios. |
| D-E8-11 | Conservación del registro de actividad | Decidido (2026-10-20) | 2 años. |

El dueño aprobó el alcance de E8a el 2026-10-20, con D-E8-03, D-E8-10 y D-E8-11, que E8a usa. Las demás se confirman antes de empezar E8b y E8c.

## Criterios de aceptación (borrador)

Los de E8a están completos en `docs/use-cases/E8-admin-panel.md`; los de E8b y E8c se completan al empezar cada fase. Algunos ejemplos:

- Dado un administrador que ingresa, cuando abre `/admin`, entonces ve el resumen y no la lista de productos.
- Dado un pedido pagado y uno cancelado hoy, cuando el resumen muestra las ventas de hoy, entonces suma solo el pagado.
- Dada una variante publicada con 2 unidades y un umbral de 3, cuando se abre el resumen, entonces aparece en "Stock bajo".
- Dado un cliente con dos pedidos, cuando se abre su ficha, entonces muestra los dos y la suma de sus totales.
- Dado un reporte exportado a CSV, cuando se abre, entonces no contiene DNI ni direcciones.
- Dado un cambio de precio, cuando se abre "Actividad", entonces aparece con el precio anterior, el nuevo y la fecha.
- Dado un usuario `CLIENTE`, cuando pide cualquier endpoint del backoffice, entonces recibe `403`.
- Dado un reembolso reintentado con la misma `Idempotency-Key`, cuando el proveedor ya lo hizo, entonces no se devuelve dos veces.
- Dado un cambio `RECIBIDO` con la prenda en condiciones, cuando se registra, entonces la variante devuelta suma una unidad y el movimiento queda en su historial.
- Dado un pedido pagado, cuando se confirma el pago, entonces al dueño le llega un aviso, uno solo aunque el webhook se repita.
- Dado un precio anterior vencido, cuando se abre la ficha, entonces no se muestra tachado.
