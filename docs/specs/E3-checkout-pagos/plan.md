# E3 – Plan técnico

## Modelo de datos nuevo

`pedido`, `pedido_item`, `pago`, `costo_envio_zona`, `config_tienda`, `solicitud_arrepentimiento` (`pedido_id` con restricción única para solicitudes activas, `codigo`, `estado` RECIBIDA o RESUELTA, `fuera_de_termino`, `creado_en`, `resuelto_en`). Se agrega al nivel de stock del módulo de inventario el campo `reservado` (`stock_level.reserved`; el stock salió de `variante` en E1) y a `pedido` los campos `reserva_vence_en` y `entregado_en` (para calcular el plazo de CU-29).

## API

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/checkout/shipping-cost?province=` | Subtotal, descuento, costo de envío y total para el paso 1 (CU-12, CU-13) |
| POST | `/api/checkout` | Crea el pedido, reserva stock y devuelve la URL de pago (exige `Idempotency-Key`) |
| GET | `/api/pedidos/{token}` | Seguimiento por enlace (invitado o cliente) |
| POST | `/api/pedidos/{token}/reintentar-pago` | Nuevo intento de pago |
| PATCH | `/api/pedidos/{token}/email` | Corregir el email y reenviar la confirmación (CU-17) |
| POST | `/api/arrepentimiento` | Solicitud de arrepentimiento con número de pedido y email (CU-29) |
| GET | `/api/admin/arrepentimientos` | Lista de solicitudes; `POST /{id}/resolver` la marca como resuelta (CU-29) |
| POST | `/api/correos/webhook` | Rebotes y quejas del proveedor de correo (CU-17) |
| POST | `/api/pagos/mercadopago/webhook` | Notificaciones (CU-15) |
| POST | `/api/payments/modo/webhook` | Notificaciones de MODO (CU-15, E3b) |
| GET | `/api/checkout/payment-providers` | Medios de pago habilitados (MODO se activa por configuración; E3b) |
| GET | `/api/admin/pedidos` | Lista con filtro por estado (CU-18) |
| GET | `/api/admin/pedidos/{id}` | Detalle |
| POST | `/api/admin/pedidos/{id}/estado` | Cambio de estado |
| GET | `/api/admin/shipping` | Costos por provincia y umbral de envío gratis (CU-12) |
| PUT | `/api/admin/shipping/zones` | Reemplaza todos los costos por provincia (CU-12) |
| PUT | `/api/admin/shipping/free-shipping` | Umbral de envío gratis (CU-12) |

## Decisiones técnicas

- **Reserva atómica:** `UPDATE stock_level SET reserved = reserved + :n WHERE variant_id = :id AND on_hand - reserved >= :n`; si no afecta ninguna fila, no hay stock suficiente. Todo el checkout corre en una sola transacción.
- **Vencimiento:** tarea programada cada minuto que expira reservas vencidas y libera stock.
- **Webhook:** se responde rápido con 200; se verifica la firma si el proveedor la ofrece y siempre se consulta el pago a su API; clave de idempotencia `proveedor + externalId + estado`.
- **Pagos:** MercadoPago y MODO, los dos con redirección a la página de pago del proveedor (los datos de tarjeta nunca tocan el servidor). MODO, en E3b: ver la sección "MODO (E3b)".
- **Mails:** envío asíncrono con reintentos; proveedor SMTP o servicio transaccional a definir.
- **Seguimiento sin cuenta:** `token_seguimiento` aleatorio y no adivinable por pedido (al menos 128 bits, guardado hasheado; RNF-29.6).
- **Botón de arrepentimiento:** el código de trámite se genera aleatorio y legible (por ejemplo `ARR-` más 8 caracteres). El correo usa el outbox con tipo propio, así que también es idempotente (RNF-20.7). *Decidido en T-E3-25:* solo se aceptan pedidos pagados (PAGADO a ENTREGADO); uno pendiente de pago, vencido o cancelado se rechaza sin registrar nada. El plazo se cuenta en días corridos, hora de Argentina, hasta el fin del décimo día posterior a la compra o a la entrega, la más tardía. El administrador recibe un aviso por correo con el código y la marca de fuera de término. Un pedido con una solicitud resuelta puede recibir otra. *Decidido en T-E3-26:* el backoffice lista las solicitudes de la más nueva a la más vieja, filtrables por estado (pendiente o resuelta), con el pedido, el comprador y la marca de fuera de término. Cualquier solicitud pendiente se puede marcar resuelta, sea cual sea el estado del pedido; no cambia el pedido ni envía correos. Mientras un pedido tiene una solicitud pendiente, su detalle muestra primero un aviso con el código para no enviarlo.
- **Datos personales:** el DNI se guarda solo en el pedido y no se registra en logs (R-05).

## Riesgos

- Los webhooks necesitan una URL pública con HTTPS: en desarrollo usar un túnel.
- Diferencias entre el entorno de prueba (sandbox) del proveedor y producción: dejar un día para pruebas con credenciales reales en E6.
- MODO requiere una cuenta en un gateway de tarjetas (Payway, Fiserv, Getnet o Line) y el alta en MODO, que tarda 48 horas hábiles; las devoluciones de MODO van por su API (ver "MODO (E3b)").

## Idempotencia (RNF-20)

- `POST /api/checkout` exige `Idempotency-Key`. En **una sola transacción** se toma un bloqueo consultivo (advisory lock) sobre la clave y su alcance sin esperar, se crea el pedido, se reserva el stock y se guarda la fila de `idempotency_key` con la respuesta. Un request concurrente con la misma clave recibe `409` con `Retry-After` mientras la primera sigue en curso, y la respuesta guardada una vez confirmada. Una repetición recibe un token de seguimiento nuevo del mismo pedido: el token solo se guarda como hash (RNF-29.6).
- El hash se calcula sobre el cuerpo del checkout (datos del comprador, dirección, medio de pago y términos) en una codificación canónica. El carrito no entra: el pedido lo vacía, y el alcance (el token de carrito o el usuario) ya ata la clave al carrito. Las claves vencen a las 24 horas y se borran a diario.
- La creación del pago en el proveedor ocurre **fuera** de la transacción, con la clave de idempotencia del proveedor derivada del número de pedido. Si un reintento encuentra el pedido sin pago creado, lo crea.
- Webhook: se inserta en `pago_evento` con `ON CONFLICT DO NOTHING ... RETURNING`; solo si devuelve una fila se procesa. El cambio de estado usa `UPDATE ... WHERE estado = :esperado`.
- Stock: `movimiento_stock` con restricción única `(pedido_id, variante_id, tipo)`.

## Correos (RNF-23)

- Interfaz `EmailSender` con una implementación para el servicio transaccional elegido (propuesta: Resend).
- Tabla `email_outbox` con restricción única `(tipo, referencia)`; el correo se encola en la misma transacción que el cambio de estado.
- Proceso programado cada minuto con reintentos a 1, 5, 15 y 60 minutos, hasta 5 intentos.
- Subdominio de envío con SPF, DKIM y DMARC: depende de tener el dominio registrado (Parte B de E0).
- Alerta al administrador al superar 80 correos en un día.

## Facturación

Decisión pendiente (ver `00-dominio-y-reglas.md`). La compra ya recopila DNI y dirección completa, datos que servirían a una integración futura con ARCA. Para la primera versión se propone que el negocio emita las facturas por fuera del sistema.

## Correo al comprador, rebotes y corrección (CU-17)

- Un único tipo de correo al cliente (`PAGO_CONFIRMADO`) y uno al administrador (`PAGO_CONFIRMADO_ADMIN`), ambos en la tabla de salida con restricción única `(tipo, referencia)`.
- **Corrección de email:** `PATCH /api/pedidos/{token}/email` valida el formato, actualiza el pedido y encola un nuevo correo con una versión (columna `version`, restricción única `(tipo, referencia, version)`), de modo que la restricción única siga evitando duplicados. Política de límite: 3 por hora por pedido. *Decidido en T-E3-23:* con el pedido pendiente de pago solo se cambia el email (la confirmación irá ahí al pagarse); de PAGADO en adelante se cambia y se reenvía la confirmación; un pedido CANCELADO o VENCIDO no se corrige. Si la confirmación todavía no se intentó enviar, no se encola otra: sale a la dirección nueva.
- **Rebotes:** `POST /api/correos/webhook` verifica la firma del proveedor, registra el evento en `email_evento` (restricción única `(proveedor, id_evento)`), marca el correo como REBOTADO y levanta una alerta en el pedido. *Decidido en T-E3-22:* solo un rebote permanente o una dirección suprimida por el proveedor marcan el correo como REBOTADO; un rebote temporal y una queja (el cliente recibió el correo y lo marcó como spam) solo se registran. Si rebota el aviso al administrador, se marca y se registra como error, sin alerta en el pedido.
- **Backoffice:** el detalle del pedido muestra la alerta con el teléfono del cliente. *Propuesto:* permitir al administrador corregir el email y reenviar la confirmación.
- **Página de resultado y seguimiento:** un enlace "¿Escribiste mal tu email?" abre el formulario para corregirlo. *Decidido en T-E3-23:* la página no muestra el email, como ya decidió T-E3-08, porque el enlace se puede reenviar.
- **Prevención:** sugerencia de dominios comunes mal escritos en el paso de envío del checkout. *Decidido en T-E3-24:* solo en el navegador, sin bloquear; una lista fija de dominios comunes en Argentina (Gmail, Hotmail, Outlook, Yahoo, Live, iCloud) y una sugerencia cuando el dominio está a 2 errores de tipeo o menos de uno de ellos. Se muestra al salir del campo y al tocar "Continuar a pago"; el primer toque se detiene en la sugerencia y el segundo avanza con el email como lo escribió.
- El plan gratuito del proveedor permite un solo endpoint de webhook; se usa para rebotes y quejas.
- Sin correo de pedido recibido, quien cierra la pestaña antes de pagar no recibe el enlace para reintentar; la reserva de stock vence a los 30 minutos y el pedido pasa a EXPIRADO.

## Row Level Security (RNF-27)

Se crean las tablas `pedido`, `pedido_item`, `pago`, `pago_checkout` (E3b), `pago_evento`, `idempotency_key`, `email_outbox` y `email_evento` con RLS activo y forzado, y las políticas de la tabla de `01-requisitos-transversales.md`. El checkout y los webhooks se ejecutan con el contexto que corresponda (cliente, anónimo o `SISTEMA`).

## MODO (E3b)

*Averiguado en T-E3b-01* con la documentación para desarrolladores de MODO (`merchants.modo.com.ar/docs`, botón de pago SDK v2, API v2), consultada el 15/10/2026.

### Cómo funciona

- MODO no procesa pagos: es un botón de pago que cursa la transacción por el gateway de tarjetas del comercio. Para un desarrollo propio admite Payway (Decidir Plus o Decidir 2.0), Fiserv (Posnet IPG), Getnet y Line. MODO no cobra comisión: se paga la del gateway, que acredita en la cuenta bancaria dada de alta en él.
- Alta: cuenta en un gateway, formulario de MODO y, a las 48 horas hábiles, un correo con `username`, `password` y los `processor_code`. Para desarrollo hay credenciales genéricas de preproducción (sin webhook configurado: se indica uno por pago) y una app de prueba solo para Android.
- El comprador paga desde la app de su banco o la app MODO, con una tarjeta de crédito o débito guardada o con dinero en cuenta.
- Flujo vigente (el modal con el QR embebido en la tienda deja de funcionar el 31/10/2026):
  1. El backend obtiene un token (`POST /v2/stores/companies/token`) y crea una *payment request* (`POST /v2/payment-requests/`).
  2. La tienda redirige a `https://site.modo.com.ar/checkout?id=…&callbackSuccess=…&callback=…`. En escritorio el sitio muestra el QR; en el celular abre la app del banco. No se puede embeber en un iframe, un modal ni un popup.
  3. MODO devuelve al comprador a `callbackSuccess` (pago aprobado) o a `callback` (rechazo, error o abandono).
  4. El resultado lo confirma el webhook; el regreso del navegador no confirma nada.

### Diferencias con MercadoPago que cambian el diseño

| Tema | MercadoPago | MODO | Consecuencia |
|---|---|---|---|
| Vencimiento del pago | El de la reserva (30 minutos) | De 5 a 10 minutos; cada `id` sirve para un solo intento | "Pagar" crea una payment request nueva cuando la anterior venció |
| Referencia del pedido | `external_reference`, el número de pedido | `external_intention_id`, distinto en cada payment request; la consulta del pago no lo devuelve | Cada intento se guarda en `pago_checkout`, con `{número de pedido}-{intento}` |
| Idempotencia hacia el proveedor | `X-Idempotency-Key` | No documentada | El intento se registra antes de llamar a MODO; si se pierde la respuesta, la payment request huérfana vence sin que nadie tenga su URL (RNF-20.8) |
| Firma del webhook | HMAC en una cabecera | JWS plano en el campo `signature` del cuerpo, verificado con las JWK que publica MODO | Verificador propio (RNF-29.3) |
| Reintentos del webhook | Durante horas | 3, en 14 segundos, solo ante errores 5xx | Conciliación cada minuto |
| Consulta del pago | `GET /v1/payments/{id}` | `GET /v2/payment-requests/{id}/data`, solo pagos aprobados, rechazados o devueltos; límite de 600 cada 10 minutos | Mismo verificador que hoy (R-06) |
| Monto informado | El cobrado | El cobrado, con los intereses de las cuotas | Solo un pago sin cuotas |
| Devoluciones | Desde el panel de MercadoPago | Todas por la API de MODO; si no, MODO no anula el reintegro de las promociones bancarias | Pendiente (ver abajo) |
| Credenciales | Access token fijo | Token de una semana; como máximo 10 pedidos de token cada 10 minutos | Token en memoria, renovado antes de vencer |

### Decisiones

- *Decidido en T-E3b-01:* las tarjetas (Visa, Mastercard y las demás) siguen por MercadoPago, cuyo checkout ya las acepta sin cuenta de MercadoPago. No se integra un tercer proveedor. Solo cambia el texto de la opción a "Tarjetas de crédito y débito, dinero en cuenta".
- *Decidido en T-E3b-01:* el gateway todavía no está elegido, y la integración no depende de él: el `processor_code` es configuración. Sugerencias para el negocio: Payway Decidir Plus (alta online, la que propone MODO), o Fiserv si el local ya usa Posnet o Clover. Getnet exige un mínimo de $400 por pago. Line exige números de establecimiento por marca, así que solo se contempla si se elige.
- *Propuesto:* un solo pago, sin cuotas (`cc_code` `1CSI`, configurable). En cuotas con interés MODO informa un monto mayor que el total del pedido y el pago no se aceptaría (CU-15). Las cuotas sin interés las paga el comercio.
- *Propuesto:* la payment request lleva solo `description` ("Pedido #1001 - Pijama Party"), `amount`, `currency` ARS, `cc_code`, `processor_code`, `external_intention_id`, `expiration_date` y `webhook_notification`. No se envían `customer`, `shipping_address` ni `items`, que son opcionales: `customer` exige el DNI, que no se comparte (R-05).
- Vencimiento de cada payment request: el menor entre 10 minutos y lo que le queda a la reserva, nunca menos de 5 minutos (el mínimo de MODO). Si un pago se aprueba después de vencida la reserva, se aplica la regla de CU-15: el pedido sigue EXPIRADO y queda el aviso para devolver el dinero.
- MODO se activa por configuración (`store.payments.modo.enabled`, apagado por defecto), así que se puede salir a producción solo con MercadoPago. Apagado, el checkout rechaza MODO y la tienda no muestra la opción.
- El medio de pago queda fijo por pedido, como hoy: el botón "Pagar" del seguimiento usa el que se eligió en el checkout. *Propuesto para después:* permitir cambiarlo desde el seguimiento.
- Regreso: `callbackSuccess` es `{public-url}/checkout/result?status=approved` y `callback` es `{public-url}/checkout/result?status=failure`. La página de resultado ya lee `status`, y el token de seguimiento sigue sin salir de la tienda.
- Intentos: tabla nueva `pago_checkout` (`payment_checkout`), una fila por checkout creado en el proveedor, con el pago, el número de intento, la referencia externa, el id y la URL del checkout y su vencimiento. Se escribe también para MercadoPago (un intento por pedido), así que la búsqueda por checkout es una sola para los dos proveedores. `pago` sigue siendo uno por pedido y proveedor.
- Conciliación: como MODO reintenta solo 14 segundos, una tarea programada cada minuto, como SISTEMA, consulta los intentos de MODO sin resultado que se crearon hace más de 1 minuto y vencieron hace menos de 15. Cada respuesta se procesa como una notificación: el mismo caso de uso y la misma idempotencia de `pago_evento`. Con menos de 20 pedidos por día queda muy lejos del límite de 600 consultas cada 10 minutos.

### Devoluciones (pendiente del negocio)

MODO exige que toda devolución pase por su API. Hoy las devoluciones de MercadoPago se hacen desde su panel (pago aprobado de un pedido EXPIRADO, CU-15; arrepentimiento, CU-29). Opciones: (a) un botón "Devolver" en el backoffice para pagos de MODO (caso de uso nuevo); (b) devolver desde el portal del gateway, aceptando que MODO no anule el reintegro de las promociones. Se decide antes de pasar a producción (T-E3b-06).

### A verificar en preproducción

- La zona horaria de `expiration_date`, que no la indica (`YYYY-MM-DDTHH:MM:SS`).
- Qué responde `GET /data` para una payment request todavía sin pagar (se asume `404` o un estado sin pago, tratado como pendiente).
- Si MODO rechaza un `external_intention_id` repetido (una base de desarrollo recreada repite números de pedido).
- El algoritmo y el `kid` de las JWK, y si el cuerpo firmado trae el `external_intention_id`.

### Requisitos del negocio para producción

Elegir el gateway y darse de alta, completar el formulario de MODO, pedir credenciales de prueba propias (formulario de testing) y cargar los secretos `MODO_USERNAME` y `MODO_PASSWORD`. Las credenciales genéricas de preproducción alcanzan para desarrollar.
