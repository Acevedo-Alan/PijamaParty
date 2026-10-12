# Dominio y reglas de negocio

## Glosario

| Término | Significado |
|---|---|
| Producto | Artículo padre (ej. "Pijama corto de algodón"). Agrupa variantes. |
| Variante / SKU | Combinación talle + color de un producto. Tiene su propio stock y precio. |
| Stock disponible | `stock − stockReservado` de una variante. |
| Reserva | Cantidad apartada al iniciar un pago, con vencimiento. |
| Pedido | Compra confirmada por el cliente; guarda copia (snapshot) de precios y datos. |
| Invitado | Cliente que compra sin cuenta. |

## Actores

Visitante, Cliente registrado, Administrador (dueño, en el backoffice) y Sistema (procesos automáticos y webhooks).

## Entidades principales

| Entidad | Campos clave |
|---|---|
| Categoria | id, nombre, slug, orden, activa |
| Producto | id, nombre, slug, descripcion, material, temporada, publico, categoriaId, activo |
| Variante | id, productoId, sku, talle, ordenTalle, color, ordenColor, precioCentavos?, activa (el orden de talles y colores es el de carga en el producto) |
| NivelStock | varianteId, enMano (módulo de inventario; la reserva se suma en E3) |
| Imagen | id, productoId, color? (uno de los colores del producto), claveArchivo (nombre generado por el servidor; la URL se deriva de ella, RNF-29.4), placeholder, orden, claveSubida y hash del archivo (reintentos sin duplicar) |
| MovimientoStock | id, varianteId, tipo (CARGA_INICIAL, AJUSTE, RESERVA, DESCUENTO, LIBERACION), cantidad (con signo), nota? (texto libre del administrador), pedidoId?, usuarioId?, claveAjuste? (reintentos sin duplicar), fecha |
| Carrito / CarritoItem | id, hashToken? (SHA-256 del `cart_token`) o usuarioId? (uno de los dos), cuponId?, actualizadoEn, items(varianteId, cantidad, ultimoPrecioVisto) |
| Cupon | id, codigo, tipo (PORCENTAJE / MONTO_FIJO), valor, vigenciaDesde?/Hasta? (fechas), usosMax?, minimoCompra?, automatico (E5), regla (E5), activo |
| UsoCupon | cuponId, pedidoId, fecha (una fila por pedido confirmado; los usos del cupón se cuentan de acá) |
| Usuario | id, email, emailVerificado, passwordHash?, nombre, rol (CLIENTE / ADMIN), intentosFallidos, bloqueadoHasta? |
| IdentidadExterna | id, usuarioId, proveedor (GOOGLE / FACEBOOK), sujeto, email, creadoEn |
| Direccion | id, usuarioId, calle, numero, piso, depto, ciudad, provincia, codigoPostal, principal |
| Pedido | id, numero, usuarioId?, email, nombre, telefono, dni, direccionEnvio (copia), subtotal, descuento, envio, total, estado, cuponCodigo, tokenSeguimiento, creadoEn, entregadoEn |
| SolicitudArrepentimiento | id, pedidoId, codigo, estado (RECIBIDA / RESUELTA), fueraDeTermino, creadoEn, resueltoEn |
| PedidoItem | pedidoId, varianteId, nombre, talle, color, precioCentavos, cantidad |
| Pago | id, pedidoId, proveedor, externalId, estado, montoCentavos, creadoEn |
| CostoEnvioZona | provincia/zona, costoCentavos |
| ConfigTienda | envioGratisDesdeCentavos |
| IdempotencyKey | id, clave, alcance, hashRequest, estado, codigoRespuesta, cuerpoRespuesta, creadoEn, venceEn |
| PagoEvento | id, proveedor, idExterno, estado, payload, procesadoEn |
| EmailOutbox | id, tipo, referencia, destinatario, asunto, cuerpo, estado, intentos, proximoIntento |
| EmailEvento | id, proveedor, idEvento, tipo, emailOutboxId, procesadoEn |
| Resena | id, productoId, usuarioId, puntaje, texto, estado, creadoEn |

**Largos máximos (RNF-28):** los valida el dominio, los repite Bean Validation y la base es la última defensa. Categoría: nombre 80 y slug 100. Producto: nombre 120, slug 140, descripción 5000 y material 80. Variante: SKU 40, talle 20 y color 30. Usuario: email 254 y nombre 100. Nota de un movimiento de stock: 200. Los textos se guardan sin espacios al principio ni al final.

## Reglas globales

- **R-01** Moneda única: ARS. Los importes se guardan como enteros en centavos.
- **R-02** El servidor es la fuente de verdad de precios, descuentos, envío y stock. El frontend nunca envía montos.
- **R-03** El stock se maneja por SKU. Con stock disponible 0 la variante no se puede comprar.
- **R-04** El pedido guarda copia de nombre, talle, color, precio y dirección; cambios posteriores del catálogo no lo alteran.
- **R-05** El DNI y la dirección solo se guardan en el pedido (o la cuenta) y nunca se registran en logs ni se envían a herramientas de analítica.
- **R-06** Los webhooks de pago son idempotentes y se verifican consultando al proveedor.
- **R-07** Zona horaria `America/Argentina/Buenos_Aires`.
- **R-08** Mobile-first: todo flujo debe poder completarse con una mano en un celular.
- **R-09** Toda operación con efectos externos o sobre dinero y stock es idempotente (ver RNF-20).
- **R-10** Los endpoints públicos tienen límite de tasa (ver RNF-22) y responden `429` con `Retry-After`.
- **R-11** Pools, hilos y timeouts son acotados y configurables (ver RNF-21); ante saturación la API responde `503`, no se cuelga.
- **R-12** Las tablas con datos de clientes o de uso interno tienen Row Level Security; sin contexto de seguridad no se ve ninguna fila (ver RNF-27).
- **R-13** Todo acceso a datos usa consultas parametrizadas; la concatenación de SQL está prohibida y el CI la detecta (ver RNF-28).
- **R-14** El administrador ingresa solo con contraseña y segundo factor; el ingreso con Google o Facebook es exclusivo de clientes (CU-27).
- **R-15** Los pedidos se conservan aunque el cliente elimine su cuenta; se desvinculan de ella (CU-28).

## Estados del pedido

```
PENDIENTE_PAGO → PAGADO → PREPARANDO → ENVIADO → ENTREGADO
PENDIENTE_PAGO → EXPIRADO | CANCELADO
PAGADO | PREPARANDO → CANCELADO
```

## Decisiones

| Tema | Estado | Detalle |
|---|---|---|
| Variantes, stock por SKU, guest checkout, wizard de 3 pasos | Decidido | Documento de alcance v1.2 |
| Costo de envío por zona, etiqueta manual | Decidido | Documento de alcance v1.2, punto 5.1 |
| Vencimiento de reserva de stock: 30 minutos | Propuesto | Confirmar antes de E3 |
| Solo medios de pago inmediatos en v1 | Propuesto | El efectivo diferido retendría stock por días |
| Descuento definitivo de stock al confirmar el pago | Propuesto | Reserva al iniciar el pago |
| Integración con MODO | Pendiente | Averiguar documentación y acuerdo comercial |
| Reglas de reseñas | Pendiente | Documento de alcance, punto 12 |
| Reglas de cupones automáticos | Pendiente | Documento de alcance, punto 12 |
| Guardar DNI en la cuenta del cliente | Pendiente | Propuesta: pedirlo en cada compra |
| Volumen esperado: menos de 20 pedidos por día | Decidido | Dimensiona pools y pruebas de carga |
| Identificadores: UUID generados por la aplicación | Decidido | Ids tipados (`UserId`, `ProductId`...) sobre UUID; columnas `uuid` en PostgreSQL |
| Claves foráneas entre módulos | Decidido | Solo dentro de un módulo. Entre módulos se guarda el uuid sin FK (por ejemplo `stock_level.variant_id`); la consistencia la dan los eventos, o una llamada a la `api` del otro módulo en la misma transacción cuando la dependencia ya va en ese sentido (el catálogo crea el `stock_level` de cada variante nueva con `inventory.api`) |
| Orden de talles y colores | Decidido | Texto libre por producto, ordenado según la carga en la matriz de variantes |
| SKU de las variantes | Decidido | Código generado por el sistema: `PP-` y 8 caracteres aleatorios sin caracteres parecidos; no se edita (CU-06) |
| Fotos de producto | Decidido | Se asocian a un color (o a todo el producto), no a una variante: una foto muestra un color en todos sus talles. Conversión con libvips por línea de comandos, fuera de la JVM (CU-06) |
| Matriz de variantes | Decidido | Generación aditiva: crea solo las combinaciones nuevas y nunca borra variantes; hasta 20 talles y 20 colores por producto (CU-06) |
| Motivo de los movimientos de stock | Decidido | Tipo fijo (enum) más una nota libre opcional del administrador. El tipo no se elige: el primer movimiento de una variante es la carga inicial y los siguientes son ajustes (CU-07) |
| Ajustes de stock idempotentes | Decidido | Cada ajuste lleva un `Idempotency-Key` por variante: un reintento con la misma clave no cambia el stock dos veces (CU-07, R-09) |
| Rate limiting solo en la app (Bucket4j en memoria) | Decidido | Sin Cloudflare ni plugin de Caddy |
| Concurrencia: pool de hilos clásico y acotado | Recomendado | Delegado por el usuario; hilos virtuales como opción futura |
| Backoffice: un único administrador (el dueño) | Decidido | Sin gestión de roles ni permisos |
| Correos: servicio transaccional (Resend) con outbox | Recomendado | Delegado por el usuario; verificar límites del plan gratuito |
| Segundo factor (TOTP) para el administrador | Recomendado como obligatorio | La cuenta accede a datos personales de clientes (RNF-29.8) |
| Factura electrónica (ARCA) | Pendiente | Consultar al contador del negocio; propuesta para v1: la emite el negocio por fuera del sistema |
| Usos de un cupón | Decidido | Una fila por pedido confirmado (`coupon_redemption`), única por cupón y pedido: un reintento no cuenta dos veces y un cupón con usos no se puede borrar (CU-11, E3) |
| Vigencia de un cupón | Decidido | Fechas opcionales, ambas inclusive, en hora de Argentina (R-07) (CU-10, CU-11) |
| Código y valor de un cupón | Decidido | Código de 3 a 20 letras, números o guiones, guardado en mayúsculas; porcentaje entero de 1 a 100 o monto fijo en centavos mayor a 0, nunca mayor que el subtotal; usos máximos y monto mínimo opcionales (CU-10, CU-11) |
| Búsqueda por texto en la primera versión | Decidido | PostgreSQL full-text en español, sin motor externo (CU-26) |
| Analítica: Google Analytics 4 y Meta Pixel | Decidido | Detalle en RNF-25 |
| Banner de consentimiento antes de cargar la analítica | Propuesto | Validar el texto con un profesional |
| API de Conversiones de Meta desde el servidor | Propuesto | Fase 2, opcional |
| Backoffice usable desde celular y computadora | Decidido | RNF-26 |
| Row Level Security solo en tablas con datos de clientes o de uso interno | Recomendado | Delegado por el usuario; el catálogo no lleva RLS (RNF-27) |
| Protección contra SQL injection: consultas parametrizadas, rol con privilegios mínimos, análisis estático y sqlmap | Decidido | RNF-28 |
| Al comprador solo le llega el correo de pago confirmado | Decidido | Sin correo de pedido recibido, de pedido enviado ni recordatorios |
| Correo rebotado o mal escrito: alerta en el backoffice y corrección desde el enlace de seguimiento | Decidido | CU-17 y CU-18 |
| Sugerencia ante dominios de email mal escritos (por ejemplo gmial.com) | Propuesto | No bloquea el avance |
| Registro e ingreso con Google y Facebook | Propuesto | CU-27; Apple en una fase posterior. La vinculación con una cuenta existente exige email verificado |
| Eliminar cuenta y descargar datos | Propuesto | CU-28; pedidos conservados por obligación fiscal (confirmar plazo con el contador) |
| Botón de arrepentimiento | Obligatorio | CU-29 y RNF-30.1; validar el texto y el procedimiento con un profesional |
| Requisitos legales de la tienda (Data Fiscal, Defensa del Consumidor, IVA contenido, AAIP) | Pendiente | RNF-30; validar con abogado y contador antes de E6 |
| SEO, datos estructurados y redirecciones desde Treinta | Recomendado | RNF-31.1 a RNF-31.3 |
| Guía de talles por categoría | Propuesto | RNF-31.5 |
| Fuera de la primera versión | Propuesto | Favoritos, aviso de reposición de stock, correo de carrito abandonado (requiere consentimiento y contradice la regla de un solo correo), newsletter, tarjetas de regalo, reembolsos desde el sistema e ingreso con Apple |
