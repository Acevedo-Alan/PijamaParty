# E3 – Checkout, pagos y pedidos

**Estado:** Borrador · **Casos de uso:** CU-12 a CU-18 y CU-29

> Etapa de mayor riesgo. Las decisiones marcadas como *Propuesto* o *Pendiente* en `00-dominio-y-reglas.md` deben cerrarse antes de empezar.

## CU-12 Configurar envío

**Actor:** Administrador

**Flujo principal**
1. El administrador carga un costo de envío por provincia o zona.
2. Opcionalmente define un monto de compra a partir del cual el envío es gratis.

**Reglas**
- Un costo por provincia, entre las 24 jurisdicciones del país; una provincia sin costo cargado no tiene envío.
- Un costo va de $0 a $10.000.000; $0 es envío gratis a esa provincia.
- El umbral de envío gratis es opcional y va de $0,01 a $10.000.000; se compara con el subtotal con descuento.
- En el backoffice (`/admin/envios`) se ven las 24 provincias con su costo en pesos: un campo vacío es "sin envío". Un botón guarda todos los costos juntos y marca las provincias con un costo inválido. Debajo, el campo "Envío gratis desde ($)", vacío si no hay, con su propio botón.

**Criterios de aceptación**
- Dada una provincia sin costo cargado, cuando un cliente la elige en el checkout, entonces se informa que no hay envío disponible a esa zona.
- Dado un envío gratis desde $50.000, cuando el subtotal con descuento es de $50.000 o más, entonces el costo de envío es $0.

## CU-13 Completar el checkout en 3 pasos

**Actor:** Visitante (compra como invitado)

**Precondición:** carrito con al menos un ítem disponible.

**Flujo principal**
1. **Envío:** nombre, email, teléfono, DNI y dirección completa (calle, número, piso, departamento, ciudad, provincia y código postal). El sistema calcula el costo de envío.
2. **Pago:** elige MercadoPago o MODO.
3. **Revisión:** ve el resumen (ítems, descuento, envío y total) y confirma.

**Reglas**
- No se exige crear cuenta.
- En escritorio el resumen se muestra en una segunda columna.
- No se muestra el botón flotante de WhatsApp durante el checkout.
- Los totales los calcula el servidor.
- Al elegir la provincia, el servidor devuelve el subtotal, el descuento, el envío y el total del carrito (solo cuentan los ítems disponibles). Si no hay envío a esa provincia, se informa; si el carrito no tiene ítems disponibles, no se puede seguir.
- Al confirmar, el checkout se rechaza si algún ítem no está disponible, si hay un cambio de precio sin aceptar o si el cupón del carrito ya no aplica: el cliente nunca paga un total que no vio. Para seguir, quita el cupón o corrige el carrito.
- Para confirmar hay que aceptar los términos; el pedido guarda la versión vigente.
- Al crearse el pedido, el carrito se vacía: el pedido es la copia de lo comprado y el pago se reintenta desde el enlace de seguimiento.

**Criterios de aceptación**
- Dado un dato obligatorio vacío, cuando se intenta avanzar, entonces no se avanza y se indica cuál falta.
- Dado un DNI con formato inválido, cuando se valida, entonces se rechaza.
- Dado un carrito con una variante sin stock, cuando se abre el checkout, entonces se avisa y se debe quitar el ítem para continuar.
- Dado un cliente que vuelve al paso anterior, cuando cambia la provincia, entonces el costo de envío y el total se recalculan.
- Dados más de 5 intentos de checkout por minuto desde el mismo carrito e IP, cuando llega otro, entonces recibe `429` con `Retry-After`.
- Dado un email con un dominio comúnmente mal escrito (por ejemplo `gmial.com`), cuando el cliente avanza, entonces se le sugiere corregirlo sin bloquear el avance (*Propuesto*).

## CU-14 Pagar con MercadoPago o MODO

**Actor:** Visitante o Cliente

**Flujo principal**
1. Al confirmar, el sistema crea el pedido en estado PENDIENTE_PAGO y reserva el stock (CU-16).
2. Crea el pago en el proveedor elegido y redirige al cliente.
3. El cliente paga y vuelve a la tienda, a una página que muestra el estado del pedido.
4. El resultado definitivo lo confirma el webhook (CU-15).

**Alternativos**
- El cliente cancela o falla el pago: el pedido sigue PENDIENTE_PAGO hasta vencer la reserva y puede reintentar el pago desde el enlace de seguimiento.

**Reglas**
- Los datos de tarjeta nunca pasan por la tienda.
- En la primera versión solo se habilitan medios de pago inmediatos (*Propuesto*).
- MODO se implementa en una sub-etapa **E3b**, una vez resuelta la integración (*Pendiente*).

**Criterios de aceptación**
- Dado un pedido confirmado, cuando se crea el pago, entonces el cliente es redirigido al proveedor con el monto exacto calculado por el servidor.
- Dado un cliente que vuelve sin completar el pago, cuando abre su enlace de seguimiento, entonces ve el pedido pendiente y la opción de reintentar.
- Dado el mismo `Idempotency-Key` enviado dos veces (doble toque o reintento de red), cuando se procesa el segundo, entonces devuelve el mismo pedido sin crear otro ni reservar stock de nuevo.
- Dado el mismo `Idempotency-Key` con un carrito distinto, cuando llega, entonces se rechaza con `422`.
- Dados dos requests simultáneos con el mismo `Idempotency-Key`, cuando llegan a la vez, entonces se crea un solo pedido y el otro recibe la respuesta guardada o un `409` con `Retry-After`.
- Dado un checkout sin `Idempotency-Key`, cuando llega, entonces se rechaza con `400`.
- Dado un pedido PAGADO, cuando el cliente vuelve a la tienda y recarga la página, entonces la analítica registra una sola compra (RNF-25).

## CU-15 Confirmar el pago (webhook)

**Actor:** Sistema

**Flujo principal**
1. El proveedor notifica un cambio de estado.
2. El sistema consulta el pago al proveedor para verificar el estado real.
3. Según el estado: aprobado → pedido PAGADO y stock descontado definitivamente; rechazado → se libera el stock; pendiente → no cambia.

**Reglas**
- Idempotente: la misma notificación repetida no produce efectos dobles.
- Se registra cada pago recibido en la tabla `pago`.

**Criterios de aceptación**
- Dado un pago aprobado, cuando llega la notificación, entonces el pedido pasa a PAGADO y el cupón usado suma un uso.
- Dada la misma notificación recibida dos veces, cuando se procesa la segunda, entonces no se descuenta stock ni se envían correos duplicados.
- Dado un pago rechazado, cuando llega la notificación, entonces el stock reservado se libera.
- Dada una notificación de un pago inexistente, cuando llega, entonces se ignora y se registra un aviso sin datos personales.
- Dadas dos notificaciones idénticas que llegan a la vez, cuando se procesan, entonces solo una produce efectos.

## CU-16 Reservar y liberar stock

**Actor:** Sistema · **Depende de:** CU-13, CU-15

**Flujo principal**
1. Al iniciar el pago, el sistema reserva de forma atómica la cantidad de cada variante del pedido.
2. La reserva vence a los 30 minutos si no hay pago aprobado (*Propuesto*).
3. Con pago aprobado, la reserva pasa a descuento definitivo.
4. Con pago rechazado o reserva vencida, el stock se libera y el pedido queda EXPIRADO.

**Reglas de negocio**
- Un ajuste de stock del administrador (CU-07) no puede dejar el stock por debajo de las unidades reservadas por pedidos pendientes de pago: se rechaza indicando cuántas hay reservadas.
- Si alguna variante del pedido no alcanza, no se reserva nada del pedido.

**Criterios de aceptación**
- Dado un SKU con stock 1 y dos clientes que inician el pago a la vez, cuando ambos confirman, entonces solo uno obtiene la reserva y el otro ve "sin stock suficiente" indicando la variante.
- Dada una reserva vencida sin pago, cuando corre el proceso de limpieza, entonces el stock vuelve a estar disponible y el pedido queda EXPIRADO.
- Dado un webhook "aprobado" repetido, cuando llega por segunda vez, entonces no descuenta el stock dos veces.
- Dada una variante en 0, cuando se consulta el detalle, entonces el botón de compra está deshabilitado.

## CU-17 Notificar el pago por mail

**Actor:** Sistema (y Cliente, para corregir su email)

**Flujo principal**
1. Al confirmarse el pago, el sistema envía al cliente un correo de confirmación con el resumen del pedido, el total, la dirección de envío y el enlace de seguimiento.
2. Envía además un aviso al administrador.
3. Si el correo rebota, el sistema lo marca y muestra una alerta en el pedido del backoffice.
4. El cliente puede corregir su email desde la página de resultado o el enlace de seguimiento, y la confirmación se reenvía.

**Reglas**
- Un fallo de envío de correo no interrumpe el pedido: se reintenta en segundo plano.
- Cada tipo de correo se envía una sola vez por pedido, aunque el proceso se repita (RNF-20.7).
- En la primera versión el cliente solo recibe el correo de pago confirmado: no hay correo de pedido recibido, de pedido enviado ni recordatorios (decidido).
- El número de seguimiento del envío se informa en el enlace de seguimiento, y el correo de confirmación lo aclara.

**Criterios de aceptación**
- Dado un pago aprobado, cuando se confirma, entonces el cliente y el administrador reciben un correo.
- Dado un servidor de correo caído, cuando se confirma un pedido, entonces el pedido se confirma igual y el correo se reintenta.
- Dado un pedido cuya confirmación se procesa dos veces, cuando termina, entonces el cliente recibió un solo correo de cada tipo.
- Dado un pedido que pasa a PAGADO, cuando termina, entonces el cliente recibe un único correo con resumen, total, dirección de envío y enlace de seguimiento.
- Dado un pedido que se crea y vence sin pagarse, cuando termina, entonces el cliente no recibe ningún correo.
- Dado un pedido que pasa a ENVIADO, cuando se guarda el número de seguimiento, entonces no se envía ningún correo y el número se ve en el enlace de seguimiento.
- Dado un correo que el proveedor informa como rebotado, cuando llega la notificación, entonces el correo queda marcado como REBOTADO y el pedido muestra una alerta en el backoffice.
- Dada una notificación de rebote repetida, cuando llega por segunda vez, entonces no se procesa de nuevo.
- Dado un cliente con un email mal escrito, cuando lo corrige desde el enlace de seguimiento, entonces se valida, se actualiza el pedido y se reenvía la confirmación.
- Dados más de 3 intentos de corrección de email por hora sobre el mismo pedido, cuando llega otro, entonces recibe `429` con `Retry-After`.

## CU-18 Gestionar pedidos

**Actor:** Administrador

**Flujo principal**
1. El administrador ve la lista de pedidos, filtrable por estado, y abre el detalle.
2. Cambia el estado: PREPARANDO, ENVIADO (cargando el número de seguimiento), ENTREGADO o CANCELADO.

**Reglas**
- Solo se permiten las transiciones del diagrama de estados.
- Al cancelar un pedido PAGADO o PREPARANDO, el administrador decide si repone el stock.
- Los reembolsos se hacen desde el panel del proveedor de pagos (fuera de alcance de esta versión).

**Criterios de aceptación**
- Dado un pedido PENDIENTE_PAGO, cuando se intenta pasar a ENVIADO, entonces se rechaza.
- Dado un pedido que pasa a ENVIADO, cuando se guarda el número de seguimiento, entonces el cliente lo ve en su enlace de seguimiento.
- Dada la lista de pedidos, cuando se filtra por PAGADO, entonces solo aparecen pedidos pagados.
- Dado un pedido con el correo rebotado, cuando el administrador abre el detalle, entonces ve una alerta con el teléfono del cliente para contactarlo.

## CU-29 Botón de arrepentimiento

**Actor:** Cliente o invitado · **Origen:** obligación legal (Ley 24.240, art. 34, y Resolución 424/2020 de la Secretaría de Comercio Interior; *validar con un profesional*)

**Flujo principal**
1. Desde un enlace "Botón de arrepentimiento" visible en la página de inicio y en el pie de todas las páginas, la persona abre el formulario. No necesita cuenta.
2. Ingresa el número de pedido y el email de la compra.
3. El sistema registra la solicitud, le muestra un código de trámite y se lo envía por correo.
4. El administrador recibe un aviso y ve la solicitud en el backoffice, con el pedido asociado.
5. El administrador coordina la devolución, cancela el pedido (CU-18), hace el reembolso desde el panel del proveedor de pagos y marca la solicitud como resuelta.

**Reglas**
- El plazo legal es de 10 días corridos desde la entrega o desde la compra, lo que ocurra último. El sistema **no rechaza** por plazo: registra la fecha y avisa al administrador si la solicitud está fuera de término, para que decida.
- La revocación no tiene costo para el cliente: el costo de la devolución lo asume el negocio.
- Una sola solicitud activa por pedido; si se repite, se muestra el código ya emitido.
- El formulario no revela si un número de pedido existe cuando el email no coincide.
- El correo con el código es una excepción, exigida por la norma, a la regla de "un solo correo al comprador" (RNF-23).
- No cambia el estado del pedido por sí sola; el cambio lo hace el administrador.

**Criterios de aceptación**
- Dado un pedido PAGADO, cuando se envía el formulario con su número y email, entonces se muestra un código de trámite, llega por correo y la solicitud aparece en el backoffice.
- Dado un número de pedido con un email que no coincide, cuando se envía el formulario, entonces se responde con un mensaje genérico y no se registra nada.
- Dado un pedido con una solicitud activa, cuando se envía otra, entonces se muestra el mismo código y no se crea una segunda.
- Dado un pedido ENTREGADO hace más de 10 días, cuando se envía el formulario, entonces la solicitud se registra marcada como fuera de término.
- Dados más de 3 envíos por hora para el mismo pedido e IP, cuando llega otro, entonces recibe `429` con `Retry-After`.
