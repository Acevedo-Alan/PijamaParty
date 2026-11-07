# Guía del backoffice

**Estado:** Borrador (T-E6-08, 2026-11-07) · **Para:** el dueño de Pijama Party Santa Fe · **Se entrega en:** T-E6-12

Esta guía explica, paso a paso, cómo manejar la tienda desde el backoffice. Usa los mismos nombres que ves en pantalla. Funciona igual en el celular, la tablet y la computadora; en el celular el menú se abre con el botón de arriba.

## 1. Entrar

1. Abrí `https://TU-DOMINIO/admin/ingresar` (la dirección exacta te la damos en la entrega).
2. Escribí tu email y tu contraseña, y tocá **Ingresar**.
3. Escribí el código de 6 números que muestra la app de autenticación del teléfono y tocá **Verificar**.

**La primera vez** el backoffice te pide activar la verificación en dos pasos: escaneá el código QR con Google Authenticator, Microsoft Authenticator u otra app, escribí el código que muestra y tocá **Activar**. Después te muestra los **códigos de recuperación**: tocá **Descargar los códigos** y guardalos en un lugar seguro. Cada uno te deja entrar una sola vez si perdés el teléfono, y no se vuelven a mostrar.

- Si te equivocás 5 veces con la contraseña, la cuenta se bloquea por seguridad; la pantalla te dice hasta qué hora.
- La sesión dura 60 minutos. Si vence, ingresás de nuevo y seguís.
- Si perdiste el teléfono **y** los códigos de recuperación, avisale a quien administra el servidor: tiene un procedimiento para que actives la verificación de nuevo.
- Para salir, tocá **Cerrar sesión** (abajo del menú). **Ver tienda** abre la tienda como la ve un cliente.

## 2. Moverte por el backoffice

El menú tiene estas secciones:

| Grupo | Secciones |
|---|---|
| (solo) | **Resumen** |
| **Ventas** | Pedidos, Arrepentimientos, Clientes |
| **Catálogo** | Productos, Categorías, Stock |
| **Marketing** | Cupones, Reseñas, Portada |
| **Tienda** | Envíos, Datos de la tienda, Actividad |

- Al lado de **Pedidos** aparece un número: son los pedidos pagados que todavía no preparaste.
- **Buscar en todo** (arriba; en la computadora, `Ctrl K`) encuentra pedidos por número, productos y clientes por nombre o email, desde cualquier pantalla.
- Todos los cambios que hacés quedan guardados en **Actividad** (ver sección 12).

## 3. Resumen: cómo está la tienda hoy

Es la primera pantalla al entrar.

- **Para hacer hoy** lista lo que espera una acción: pedidos pagados por preparar, pedidos en preparación, arrepentimientos sin resolver, reseñas por moderar, pedidos con email rebotado, variantes agotadas y con stock bajo. Cada número te lleva a la lista ya filtrada. Empezá siempre por lo **Urgente**.
- **Ventas** muestra lo vendido y la cantidad de pedidos en el período que elijas (**Hoy**, **7 días**, **Este mes** o **Fechas**, hasta 366 días), con la variación contra el período anterior y el ticket promedio. El gráfico se ve por **Días** o por **Meses**. Una venta es un pedido pagado o posterior, sin los cancelados.
- También ves **Stock bajo**, **Más vendidos** (últimos 30 días), **Pedidos recientes**, **Actividad reciente** y los **Totales**.
- **Actualizar** vuelve a leer los números; **Actualizar cada 5 minutos** lo hace solo mientras la pantalla está abierta.
- **Accesos rápidos**: Nuevo producto, Cupones y Ver pedidos.

## 4. Pedidos: lo que más vas a usar

**Pedidos** lista todos los pedidos, con filtro por **Estado** y búsqueda por número (`1001` o `#1001`), nombre, apellido o email del comprador, y por fechas (**Desde** y **Hasta**).

Abrí un pedido para ver el comprador, el DNI, la dirección de envío, los productos (talle, color y cantidad), el pago y el estado.

**El recorrido de un pedido pagado:**

1. Cuando el pago se acredita, el pedido queda **pagado** y el comprador recibe un email con el enlace de seguimiento. Vos recibís un aviso de pedido pagado en el email de la tienda.
2. Tocá **Imprimir remito** para sacar el remito (sin precios) que va dentro del paquete, y preparalo. Tocá **Preparar**.
3. Cuando lo despachás por Correo Argentino, tocá **Marcar como enviado**, escribí el **Número de seguimiento del correo** y tocá **Confirmar envío**. El comprador lo ve en su enlace de seguimiento. Si te equivocaste, **Corregir número**.
4. Cuando llega, **Marcar como entregado**.

**Cancelar:** **Cancelar pedido** te pregunta **¿Reponer el stock?**. Elegí "Sí, volver a poner los productos a la venta" si la mercadería está con vos, o "No, el stock queda como está". Después **Confirmar cancelación**.

**Avisos en rojo dentro del pedido, y qué hacer:**
- *El comprador pidió arrepentirse*: **no lo envíes**, coordiná la devolución (sección 5).
- *El pago se aprobó después de que el pedido venció o se canceló*: hacé el reembolso desde el panel de MercadoPago (por ahora no se reembolsa desde acá).
- *No pudimos enviarle el correo* o *el correo rebotó*: llamá o escribile al comprador al teléfono que muestra el aviso, para confirmar sus datos.

Un pedido que nadie pagó vence solo a los 30 minutos y los productos vuelven a estar a la venta; no tenés que hacer nada.

## 5. Arrepentimientos

Por ley, el comprador tiene 10 días corridos desde que recibe su compra para arrepentirse. **Arrepentimientos** lista las solicitudes (**Pendiente** o **Resuelta**), con el pedido, quién la hizo y cuándo; una pedida pasados los 10 días aparece como **Fuera de término**.

Cuando coordinaste la devolución y devolviste el dinero con el comprador, tocá **Marcar resuelta**. Mirá esta lista todos los días: es un trámite con plazo legal.

## 6. Clientes

**Clientes** lista a quienes crearon una cuenta (50 por página). Buscá por nombre o email, o por fecha de alta. La ficha muestra el contacto (con botones para escribir un email o abrir WhatsApp), las direcciones, los pedidos hechos con su cuenta, cuántas compras y cuánto gastó. Es solo de lectura: cada cliente puede eliminar su cuenta desde "Mi cuenta". Los pedidos hechos sin cuenta están en **Pedidos**.

Los datos personales son confidenciales: no los compartas ni los copies fuera de lo necesario para atender el pedido. Abrir una ficha queda registrado en **Actividad**.

## 7. Productos

**Productos** lista todo el catálogo. Buscá por nombre o SKU y filtrá por **Categoría**, **Estado** (**Publicados** u **Ocultos**) y **Stock** (**Con stock**, **Stock bajo**, **Agotados**).

**Cargar un producto nuevo:** necesitás tener al menos una categoría (sección 8).
1. **Nuevo producto**: completá **Nombre**, **Descripción**, **Material**, **Temporada**, **Público** y **Categoría**, y tocá **Crear producto**.
2. En **Variantes**, escribí los **Talles** y los **Colores** separados por coma (por ejemplo `S, M, L`) y tocá **Generar variantes**: se crea una variante por cada combinación.
3. A cada variante ponele su **Precio** y tocá **Guardar precio**. Una variante sin precio no se puede vender.
4. En **Fotos**, tocá **Agregar fotos** (JPG, PNG o WebP; hasta 8 por producto, de hasta 10 MB cada una). Podés elegir varias o sacarlas con la cámara. Si una foto es de un color en particular, elegí ese color en **Color de las fotos**; si es de todo el producto, dejá **Todo el producto**. Ordenalas con las flechas (o arrastrando, en la computadora).
5. Cargá el stock de cada variante (sección 9).
6. Tocá **Publicar**. Para publicar hace falta al menos una variante activa con precio. **Ocultar** lo saca de la tienda sin borrarlo, y **Ver en la tienda** abre la ficha.

Si algo falla al subir una foto, tocá **Reintentar**: la foto no se duplica.

Desactivar una variante (el interruptor **Activa**) la saca de la venta; no podés desactivar la última variante activa con precio de un producto publicado: primero ocultá el producto.

Con la sesión abierta en el backoffice, la ficha del producto en la tienda muestra un enlace **Editar** solo para vos.

## 8. Categorías y guía de talles

**Categorías**: **Nueva categoría** → **Crear**. Cada una tiene **Renombrar**, subir o bajar (para cambiar el orden), **Desactivar** y **Eliminar**. Una categoría con productos no se puede eliminar: desactivala. Desactivada, no aparece en la tienda.

**Guía de talles** (dentro de cada categoría): armá una tabla de medidas con hasta 6 columnas (por ejemplo "Busto (cm)", "Cintura (cm)" o "Edad") y una fila por talle (hasta 20), con una nota opcional sobre cómo medirse. Se muestra en la ficha de cada producto de la categoría y en la página "Guía de talles". Una categoría sin guía no muestra el enlace. Una buena guía reduce los cambios por talle.

## 9. Stock

**Stock** lista todas las variantes activas, de menos a más disponibles. Para cada una ves **En mano** (lo que hay), **Reservadas** (apartadas por pedidos que todavía no se pagaron) y **Disponibles** (lo que se puede vender: en mano menos reservadas). Filtrá por **Stock bajo** o **Agotadas**.

- **Ajuste rápido:** escribí la **Cantidad** y tocá **+ Sumar** o **− Restar**, con un **Motivo** opcional (por ejemplo "llegó mercadería" o "contado en el inventario"). Si te equivocás, **Deshacer**.
- No podés restar más de lo que hay, ni de lo que está reservado por pedidos pendientes de pago.
- **Ver movimientos** muestra el historial de la variante: cargas, ajustes, ventas, reservas y reposiciones por cancelación.
- Una variante con stock 0 no se puede comprar. El umbral de "stock bajo" lo elegís en **Datos de la tienda** (3 unidades al comienzo).

## 10. Marketing

**Cupones.** **Nuevo cupón** y completá:
- **Código** (de 3 a 20 letras, números o guiones; se guarda en mayúsculas), **Tipo** (**Porcentaje** o **Monto fijo**) y el descuento.
- Opcionales: **Vale desde** y **Vale hasta**, **Usos máximos** y **Compra mínima**.
- **Automático**: el descuento se aplica solo cuando el carrito cumple las condiciones, sin que el cliente escriba nada (por ejemplo una oferta de temporada). No se puede cambiar después de crearlo.
- **Alcance** (solo en los automáticos): categorías o productos a los que aplica; sin elegir, descuenta todo el carrito.

En la lista ves cuántos usos lleva cada cupón. **Desactivar** lo apaga sin borrarlo; un cupón que ya se usó no se puede eliminar, solo desactivar.

**Reseñas.** Las opiniones de los clientes esperan tu aprobación. En **Pendientes**, leé cada una y tocá **Aprobar** (se publica en la ficha del producto) o **Rechazar**. **Eliminar** la borra para siempre.

**Portada.** El banner de personajes de la página de inicio: **Título**, **Texto** opcional, **Texto del enlace** y **Destino del enlace** (una página de la tienda que empieza con `/`, por ejemplo `/categoria/kigurumis`), más la **Foto**. Mirá la **Vista previa**, tocá **Guardar** y después **Publicar**; **Ocultar** lo saca sin borrarlo. Se publica lo que está guardado: guardá antes de publicar.

## 11. Tienda: envíos y datos

**Envíos.**
- **Costo de envío por provincia**, en pesos. Una provincia vacía significa que no hay envío ahí; $ 0 es envío gratis a esa provincia. Tocá **Guardar costos**.
- **Envío gratis desde**: el monto de compra desde el cual el envío es gratis (se compara con el subtotal ya con descuento). Vacío: no hay envío gratis. Tocá **Guardar envío gratis**.

El comprador ve el costo antes de pagar, así que revisá estos números con cuidado.

**Datos de la tienda.** Lo que la tienda muestra para contactarte: email, WhatsApp (con código de país y de área), horario, dirección, Instagram, Facebook y TikTok (enlaces completos). Un dato vacío no aparece. Acá también está el **Anuncio** de la franja de arriba (hasta 80 caracteres; vacío, muestra el envío gratis) y el **Umbral de stock bajo**. Tocá **Guardar**.

Los datos legales del vendedor (razón social, CUIT, domicilio y Data Fiscal) del pie de la tienda no se editan acá.

## 12. Actividad

**Actividad** es el registro de todo lo que se cambió desde el backoffice: precios, stock, publicar u ocultar, estados de pedidos, cupones, reseñas, envíos, portada y datos de la tienda, con fecha, qué cambió ("antes → después") y a qué sección corresponde. Filtrá por **Sección** y por fechas. No se puede editar ni borrar, y se conserva 2 años. Sirve para saber quién cambió qué y cuándo.

## 13. Si algo no anda

| Qué pasa | Qué hacer |
|---|---|
| "No pudimos cargar los datos" o una pantalla en blanco | Tocá **Reintentar** o recargá la página. Si sigue, avisá a quien administra el servidor. |
| "Tu sesión venció" | Ingresá de nuevo; no se pierde lo ya guardado. |
| "El pedido cambió mientras lo mirabas" | Se actualizó solo: revisalo y repetí la acción. |
| Un cliente dice que pagó y el pedido no aparece como pagado | Puede tardar unos minutos en acreditarse. Si sigue igual, avisá a quien administra el servidor con el número de pedido. |
| La tienda muestra "mantenimiento" | Es una pausa breve por una actualización o un problema técnico; volvé a probar en unos minutos. |
| Perdiste el teléfono de la verificación | Usá un código de recuperación (entra una sola vez). Si no los tenés, avisá a quien administra el servidor. |

Qué datos conviene tener a mano para avisar de un problema: la hora, la pantalla, el número de pedido y el mensaje que ves.
