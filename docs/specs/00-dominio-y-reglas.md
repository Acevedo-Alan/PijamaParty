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
| Categoria | id, nombre, slug, activa |
| Producto | id, nombre, slug, descripcion, material, temporada, publico, categoriaId, activo |
| Variante | id, productoId, sku, talle, color, precioCentavos, stock, stockReservado, activa |
| Imagen | id, productoId, varianteId?, url, placeholder, orden |
| MovimientoStock | id, varianteId, tipo, cantidad, motivo, pedidoId?, usuarioId?, fecha |
| Carrito / CarritoItem | id, token, usuarioId?, cuponId?, items(varianteId, cantidad) |
| Cupon | id, codigo, tipo, valor, vigenciaDesde/Hasta, usosMax, usosActuales, minimoCompra, automatico, regla, activo |
| Usuario | id, email, emailVerificado, passwordHash?, nombre, rol (CLIENTE / ADMIN) |
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
| Rate limiting solo en la app (Bucket4j en memoria) | Decidido | Sin Cloudflare ni plugin de Caddy |
| Concurrencia: pool de hilos clásico y acotado | Recomendado | Delegado por el usuario; hilos virtuales como opción futura |
| Backoffice: un único administrador (el dueño) | Decidido | Sin gestión de roles ni permisos |
| Correos: servicio transaccional (Resend) con outbox | Recomendado | Delegado por el usuario; verificar límites del plan gratuito |
| Segundo factor (TOTP) para el administrador | Recomendado como obligatorio | La cuenta accede a datos personales de clientes (RNF-29.8) |
| Factura electrónica (ARCA) | Pendiente | Consultar al contador del negocio; propuesta para v1: la emite el negocio por fuera del sistema |
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
