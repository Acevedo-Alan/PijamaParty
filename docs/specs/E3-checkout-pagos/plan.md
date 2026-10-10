# E3 – Plan técnico

## Modelo de datos nuevo

`pedido`, `pedido_item`, `pago`, `costo_envio_zona`, `config_tienda`, `solicitud_arrepentimiento` (`pedido_id` con restricción única para solicitudes activas, `codigo`, `estado` RECIBIDA o RESUELTA, `fuera_de_termino`, `creado_en`, `resuelto_en`). Se agrega a `variante` el campo `stock_reservado` (ya previsto en el dominio) y a `pedido` los campos `reserva_vence_en` y `entregado_en` (para calcular el plazo de CU-29).

## API

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/checkout/costo-envio?provincia=` | Costo de envío para el paso 1 |
| POST | `/api/checkout` | Crea el pedido, reserva stock y devuelve la URL de pago (exige `Idempotency-Key`) |
| GET | `/api/pedidos/{token}` | Seguimiento por enlace (invitado o cliente) |
| POST | `/api/pedidos/{token}/reintentar-pago` | Nuevo intento de pago |
| PATCH | `/api/pedidos/{token}/email` | Corregir el email y reenviar la confirmación (CU-17) |
| POST | `/api/arrepentimiento` | Solicitud de arrepentimiento con número de pedido y email (CU-29) |
| GET | `/api/admin/arrepentimientos` | Lista de solicitudes; `POST /{id}/resolver` la marca como resuelta (CU-29) |
| POST | `/api/correos/webhook` | Rebotes y quejas del proveedor de correo (CU-17) |
| POST | `/api/pagos/mercadopago/webhook` | Notificaciones (CU-15) |
| GET | `/api/admin/pedidos` | Lista con filtro por estado (CU-18) |
| GET | `/api/admin/pedidos/{id}` | Detalle |
| POST | `/api/admin/pedidos/{id}/estado` | Cambio de estado |
| GET/PUT | `/api/admin/envio/zonas` | Costos por zona (CU-12) |
| PUT | `/api/admin/config/envio-gratis` | Umbral de envío gratis |

## Decisiones técnicas

- **Reserva atómica:** `UPDATE variante SET stock_reservado = stock_reservado + :n WHERE id = :id AND stock - stock_reservado >= :n`; si no afecta ninguna fila, no hay stock suficiente. Todo el checkout corre en una sola transacción.
- **Vencimiento:** tarea programada cada minuto que expira reservas vencidas y libera stock.
- **Webhook:** se responde rápido con 200; se verifica la firma si el proveedor la ofrece y siempre se consulta el pago a su API; clave de idempotencia `proveedor + externalId + estado`.
- **Pagos:** MercadoPago con redirección a su página de pago (los datos de tarjeta nunca tocan el servidor). MODO en E3b, según la documentación que se consiga.
- **Mails:** envío asíncrono con reintentos; proveedor SMTP o servicio transaccional a definir.
- **Seguimiento sin cuenta:** `token_seguimiento` aleatorio y no adivinable por pedido (al menos 128 bits, guardado hasheado; RNF-29.6).
- **Botón de arrepentimiento:** el código de trámite se genera aleatorio y legible (por ejemplo `ARR-` más 8 caracteres). El correo usa el outbox con tipo propio, así que también es idempotente (RNF-20.7).
- **Datos personales:** el DNI se guarda solo en el pedido y no se registra en logs (R-05).

## Riesgos

- Los webhooks necesitan una URL pública con HTTPS: en desarrollo usar un túnel.
- Diferencias entre el entorno de prueba (sandbox) del proveedor y producción: dejar un día para pruebas con credenciales reales en E6.
- MODO puede requerir un acuerdo comercial o una integración distinta a la prevista.

## Idempotencia (RNF-20)

- `POST /api/checkout` exige `Idempotency-Key`. En **una sola transacción** se inserta la fila de `idempotency_key` (`ON CONFLICT DO NOTHING`), se crea el pedido, se reserva el stock y se guarda la respuesta. Un request concurrente con la misma clave espera en el índice único hasta que la primera transacción confirma, y después recibe la respuesta guardada.
- El hash se calcula sobre el contenido relevante del checkout (carrito, datos y medio de pago) normalizado. El alcance es el token de carrito o el usuario; las claves vencen a las 24 horas.
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
- **Corrección de email:** `PATCH /api/pedidos/{token}/email` valida el formato, actualiza el pedido y encola un nuevo correo con una referencia que incluye una versión (`pedido_id:n`), de modo que la restricción única siga evitando duplicados. Política de límite: 3 por hora por pedido.
- **Rebotes:** `POST /api/correos/webhook` verifica la firma del proveedor, registra el evento en `email_evento` (restricción única `(proveedor, id_evento)`), marca el correo como REBOTADO y levanta una alerta en el pedido.
- **Backoffice:** el detalle del pedido muestra la alerta con el teléfono del cliente. *Propuesto:* permitir al administrador corregir el email y reenviar la confirmación.
- **Página de resultado y seguimiento:** muestra a qué email se envió la confirmación, con un botón para corregirlo.
- **Prevención:** sugerencia de dominios comunes mal escritos en el paso de envío del checkout.
- El plan gratuito del proveedor permite un solo endpoint de webhook; se usa para rebotes y quejas.
- Sin correo de pedido recibido, quien cierra la pestaña antes de pagar no recibe el enlace para reintentar; la reserva de stock vence a los 30 minutos y el pedido pasa a EXPIRADO.

## Row Level Security (RNF-27)

Se crean las tablas `pedido`, `pedido_item`, `pago`, `pago_evento`, `idempotency_key`, `email_outbox` y `email_evento` con RLS activo y forzado, y las políticas de la tabla de `01-requisitos-transversales.md`. El checkout y los webhooks se ejecutan con el contexto que corresponda (cliente, anónimo o `SISTEMA`).
