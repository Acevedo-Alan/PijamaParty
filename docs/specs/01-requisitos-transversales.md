# Requisitos no funcionales transversales

**Estado:** Borrador · **Aplica a:** todas las etapas

Los valores numéricos son un punto de partida para un servidor de 4 GB y menos de 20 pedidos por día. Se validan con la prueba de carga de E6 y se ajustan si hace falta.

## RNF-20 Idempotencia en PostgreSQL

| ID | Requisito |
|---|---|
| RNF-20.1 | **Checkout:** `POST /api/checkout` exige la cabecera `Idempotency-Key` (UUID generado por el frontend en cada intento de compra). La tabla `idempotency_key` tiene restricción única sobre `(clave, alcance)`; el alcance es el token de carrito o el usuario. Guarda un hash del cuerpo y la respuesta. |
| RNF-20.2 | Misma clave y mismo contenido: se devuelve la respuesta guardada sin repetir efectos. Misma clave y contenido distinto: `422`. Misma clave mientras la primera sigue en curso: `409` con `Retry-After`. Las claves vencen a las 24 horas y se limpian a diario. |
| RNF-20.3 | **Webhooks:** la tabla `pago_evento` tiene restricción única sobre `(proveedor, id_externo, estado)` y se escribe con `INSERT ... ON CONFLICT DO NOTHING`. Solo se procesa el evento si la inserción efectivamente ocurrió. |
| RNF-20.4 | **Stock:** `movimiento_stock` tiene restricción única sobre `(pedido_id, variante_id, tipo)` para los tipos RESERVA, DESCUENTO y LIBERACION. Las reservas usan `UPDATE` condicional. |
| RNF-20.5 | **Estados del pedido:** toda transición se escribe como `UPDATE ... SET estado = :nuevo WHERE id = :id AND estado = :esperado`. Si no afecta ninguna fila, se trata como ya procesada. |
| RNF-20.6 | **Número de pedido:** generado por una secuencia, con restricción única. |
| RNF-20.7 | **Correos:** la tabla `email_outbox` tiene restricción única sobre `(tipo, referencia)`: un correo de cada tipo por pedido. |
| RNF-20.8 | **Hacia el proveedor de pagos:** se envía su cabecera de idempotencia, derivada del número de pedido, para que un reintento no genere un cobro doble (verificar el nombre exacto en su documentación). |

**Verificación:** cada punto tiene un test que repite la operación y otro que la ejecuta de forma concurrente con dos hilos.

## RNF-21 Pools y timeouts

| Componente | Valor inicial |
|---|---|
| Tomcat (requests entrantes) | 40 hilos máximo, 5 mínimos, cola de espera de 100, timeout de conexión de 10 s. Hilos clásicos acotados; los hilos virtuales quedan como opción futura. |
| HikariCP (conexiones a PostgreSQL) | 8 máximo, 2 mínimas, timeout de adquisición de 3 s, vida máxima de 25 min, detección de fugas solo en `dev`. |
| PostgreSQL | `max_connections` en 30, `statement_timeout` de 10 s, `idle_in_transaction_session_timeout` de 30 s, unos 512 MB de `shared_buffers`. |
| JVM | `-Xmx` en 768 MB. |
| Procesamiento de imágenes | Máximo 2 conversiones simultáneas. |
| Cliente HTTP saliente (pagos, correo) | Pool propio, timeout de conexión de 3 s y de lectura de 10 s; reintentos solo en consultas de lectura. |

- Si el pool de conexiones se agota o la cola se llena, la API responde `503` con `Retry-After` en `problem+json`; nunca queda colgada.
- Presupuesto aproximado de memoria en 4 GB: sistema operativo y Caddy ~0,6 GB, JVM ~1,1 GB, PostgreSQL ~1 GB, margen ~1,3 GB.

## RNF-22 Rate limiting

Implementado en la aplicación con Bucket4j en memoria. Los contadores se reinician si la aplicación se reinicia, algo aceptable con una sola instancia.

| Ruta | Clave | Límite |
|---|---|---|
| Login (cliente y administrador) | IP y cuenta (RNF-29.5) | 5 por minuto |
| Registro y recuperación de contraseña | IP | 3 por hora |
| Ingreso con Google o Facebook (vuelta del proveedor) | IP | 10 por minuto |
| Botón de arrepentimiento | Pedido + IP | 3 por hora |
| Aplicar cupón | Carrito + IP | 10 por minuto |
| Checkout | Carrito + IP | 5 por minuto |
| Corregir el email de un pedido | Pedido + IP | 3 por hora |
| Modificar carrito | IP | 60 por minuto |
| Catálogo | IP | 120 por minuto |
| Búsqueda y sugerencias | IP | 60 por minuto |
| Backoffice autenticado | Usuario | 300 por minuto |
| Webhooks (pagos y correo) y `/api/health` | Excluidos | Se protegen con verificación del proveedor |

- Respuesta `429` con `Retry-After` en `problem+json`.
- La IP real se toma de la cabecera que escribe Caddy; solo se confía en esa cabecera cuando viene del proxy.
- Los buckets inactivos expiran para que la memoria no crezca.
- Los logs registran la ruta y la política excedida, nunca la IP en claro.
- Muchos clientes navegan desde datos móviles con IP compartida: por eso el catálogo es holgado y los endpoints sensibles combinan IP con el token de carrito.

## RNF-23 Correos transaccionales

- Servicio transaccional por API (propuesta: Resend, cuyo plan gratuito, según fuentes de mediados de 2026, permite 3.000 correos por mes con tope de 100 por día y un dominio verificado; revisar antes de contratar). La integración va detrás de una interfaz `EmailSender`, así que cambiar de proveedor no toca el resto del sistema.
- Envío desde un subdominio propio (por ejemplo `notificaciones.<dominio>`), con registros SPF, DKIM y DMARC configurados.
- El correo se encola en `email_outbox` dentro de la misma transacción que el cambio de estado. Un proceso programado lo envía con reintentos (1, 5, 15 y 60 minutos, hasta 5 intentos).
- Un fallo de correo nunca interrumpe un pedido. El `Reply-To` apunta al correo del negocio.
- Alerta al administrador si en un día se superan 80 correos (cerca del tope gratuito).
- **Correos al comprador (*decidido*):** en la primera versión el cliente recibe un solo correo por compra, el de **pago confirmado**: resumen del pedido, total, dirección de envío y enlace de seguimiento, con la aclaración de que el número de seguimiento del envío aparecerá en ese enlace. No hay correo de pedido recibido, de pedido enviado ni recordatorios. El administrador recibe un aviso por cada pago confirmado. Excepciones, porque responden a una acción del propio cliente: verificación de email, recuperación de contraseña y confirmación de vinculación (CU-19 y CU-27), y el código de trámite del botón de arrepentimiento (CU-29).
- **Rebotes y quejas:** el proveedor avisa por webhook (verificado por firma, excluido del rate limiting y registrado en `email_evento` con restricción única). El correo queda marcado como REBOTADO y el pedido muestra una alerta en el backoffice, junto con el teléfono del cliente para contactarlo.
- **Corrección del email:** desde la página de resultado y el enlace de seguimiento, el cliente puede corregir su email (validado, máximo 3 veces por hora); al corregirlo se reenvía la confirmación.
- **Prevención (*Propuesto*):** campo de tipo email, con recorte de espacios y minúsculas, y sugerencia no bloqueante ante dominios comunes mal escritos.

## RNF-24 Acceso del administrador

- Hay un único administrador (el dueño). No hay gestión de roles ni de otros usuarios del backoffice; el rol ADMIN solo separa el backoffice de la cuenta de cliente.
- *Recomendado como obligatorio (RNF-29.8):* segundo factor (TOTP) para el administrador, porque su cuenta da acceso a datos personales de clientes.
- Sesión del backoffice con vencimiento por inactividad de 60 minutos.
- Recuperación de contraseña por correo.

## RNF-25 Analítica y consentimiento

- **Herramientas:** Google Analytics 4 y Meta Pixel.
- **Consentimiento (*Propuesto*):** banner con "Aceptar" y "Rechazar". Ningún script de analítica se carga antes de aceptar; la elección se recuerda 12 meses y se puede cambiar desde un enlace "Cookies" en el pie.
- **Eventos:**

| Momento | Google Analytics 4 | Meta Pixel |
|---|---|---|
| Lista de productos | `view_item_list` | – |
| Búsqueda | `search` | `Search` |
| Ficha de producto | `view_item` | `ViewContent` |
| Agregar al carrito | `add_to_cart` | `AddToCart` |
| Inicio del checkout | `begin_checkout` | `InitiateCheckout` |
| Medio de pago elegido | `add_payment_info` | `AddPaymentInfo` |
| Pago aprobado | `purchase` | `Purchase` |

- **Compra:** `purchase` se envía una sola vez por pedido, solo cuando el pedido está PAGADO, con el número de pedido como `transaction_id` y un identificador de evento igual en ambas herramientas para evitar duplicados. Se evita el reenvío al recargar la página con una marca por pedido.
- **Datos:** importes en ARS (`currency: "ARS"`) y SKU como identificador de ítem. Ningún evento lleva DNI, email, nombre ni dirección (regla R-05).
- **Configuración:** los identificadores de GA4 y de Pixel van en variables de entorno del build, no en el código.
- **Privacidad:** la política menciona estas herramientas y que Google y Meta procesan esos datos. Conviene que el negocio valide el texto del banner y de la política con un profesional.
- **Rendimiento y seguridad:** los scripts se cargan diferidos para no afectar RNF-11, y la política de seguridad de contenido (CSP) permite solo los dominios necesarios.
- **Fase 2 (opcional):** API de Conversiones de Meta desde el servidor, para medir mejor las ventas cuando el navegador bloquea el Pixel.
- **Verificación:** Meta Pixel Helper y DebugView de GA4 en el entorno de prueba.

## RNF-26 Backoffice en celular y computadora

- Todas las pantallas del backoffice se usan sin desplazamiento horizontal desde 360 px de ancho, con controles táctiles de al menos 44 px y formularios de una columna en pantallas chicas.
- Cargar un producto completo con fotos se puede hacer de punta a punta desde el celular.
- **Fotos:** JPG, PNG o WebP de hasta 10 MB y un máximo de 8 por producto (*Propuesto*). Se pueden sacar con la cámara o elegir de la galería, de a varias. El navegador las reduce (máximo 1600 px de lado) antes de enviarlas, con barra de progreso y reintento seguro.
- Se prueba en al menos un Android y un iPhone reales durante E6.

## RNF-27 Row Level Security (PostgreSQL)

Es una defensa en profundidad: no reemplaza la autorización de la aplicación, sino que limita el daño ante un error de código (por ejemplo, acceso a pedidos ajenos).

- **Alcance (*Recomendado*):** las tablas con datos de clientes o de uso interno: `pedido`, `pedido_item`, `pago`, `pago_evento`, `direccion`, `usuario`, `carrito`, `carrito_item`, `resena`, `idempotency_key`, `email_outbox`, `email_evento`, `identidad_externa` y `solicitud_arrepentimiento`. El catálogo no lleva RLS: es público y no contiene datos personales.
- **Roles de base de datos:** `tienda_owner` (dueño de las tablas, usado por Flyway), `tienda_app` (la aplicación, sin ser dueño y sin `BYPASSRLS`) y `tienda_backup` (para `pg_dump`). Los roles de migraciones y de backup tienen `BYPASSRLS`; el de la aplicación no. Las tablas protegidas usan `FORCE ROW LEVEL SECURITY`.
- **Contexto por transacción:** al empezar cada transacción, la aplicación fija con `set_config(..., true)` el rol (`ANONIMO`, `CLIENTE`, `ADMIN` o `SISTEMA`), el `usuario_id`, el `token` del pedido y el `token` del carrito. Al ser local a la transacción, no se filtra entre requests del pool. Toda operación sobre tablas protegidas corre dentro de una transacción, lecturas incluidas. En el código los roles se llaman `ANONYMOUS`, `CUSTOMER`, `ADMIN` y `SYSTEM`, y las variables son `app.role`, `app.user_id`, `app.cart_token_hash` y `app.tracking_token_hash`: los tokens viajan como hash SHA-256, igual que se guardan (RNF-29.6), nunca en claro. Las políticas los leen con las funciones `app_role()`, `app_user_id()`, `app_cart_token_hash()` y `app_tracking_token_hash()`.
- **Cierre por defecto:** sin contexto, ninguna consulta devuelve filas.
- **Límite (*Decidido*):** cualquier rol puede ejecutar `set_config`, así que un SQL injection que corra como `tienda_app` podría cambiar su propio contexto. RLS contiene errores de lógica de la aplicación (por ejemplo, olvidar un filtro por cliente), no el SQL injection: contra eso la defensa es RNF-28 (solo consultas parametrizadas y análisis estático en el CI).
- **Fuera de alcance, con motivo (*Decidido*):**
  - `spring_session` y `spring_session_attributes`: Spring Session las lee y escribe fuera de la transacción con contexto de seguridad, así que RLS no es aplicable. Para que no guarden datos personales, el `principal_name` es el id interno del usuario (UUID), nunca el email (se aplica en E4, al crear el ingreso). `tienda_app` solo tiene permisos sobre filas, sin DDL.
  - `event_publication` y `event_publication_archive`: los eventos de dominio llevan solo ids y valores no personales, nunca datos personales ni tokens (`ARCHITECTURE.md` sección 6, verificado por `ArchitectureTest`). Los eventos completados se borran.

| Tabla | Cliente | Anónimo (invitado) | Administrador | Sistema |
|---|---|---|---|---|
| `pedido`, `pedido_item` | Lee los propios y corrige su email | Lee el pedido de su token y corrige su email | Lee y actualiza el estado | Crea y actualiza |
| `pago`, `pago_evento` | – | – | Lee | Crea y actualiza |
| `direccion` | Todo sobre las propias | – | Lee | – |
| `usuario` | Lee y actualiza el propio | – | Lee el propio | Crea el administrador inicial, lee para autenticar y actualiza intentos fallidos y bloqueo |
| `identidad_externa` | Lee y desvincula las propias | – | – | Crea y lee para autenticar |
| `solicitud_arrepentimiento` | – | – | Lee y resuelve | Crea (validando pedido y email) |
| `carrito`, `carrito_item` | Los propios | Los de su token de carrito | – | Limpieza |
| `resena` | Crea y edita las propias; lee las aprobadas | Lee las aprobadas | Modera | – |
| `idempotency_key` | – | – | – | Todo |
| `email_outbox`, `email_evento` | – | – | Lee | Todo |

- **Verificación:** cada política se prueba con cliente A, cliente B, invitado, administrador y sistema. Un test comprueba que el rol de la aplicación no es dueño ni tiene `BYPASSRLS`, y que las tablas protegidas tienen RLS activo y forzado.

## RNF-28 Protección contra SQL injection

- Solo consultas parametrizadas (JPA, Spring Data y `NamedParameterJdbcTemplate`). Está prohibido concatenar valores en SQL, también en consultas nativas.
- Los filtros dinámicos usan Specifications. El `ORDER BY` y cualquier nombre de columna provienen de una lista blanca (enum), nunca del request. El texto de la búsqueda siempre entra como parámetro y se escapan los comodines de `LIKE`.
- Validación de entrada con Bean Validation: tipos, largos máximos y formatos.
- El rol `tienda_app` tiene privilegios mínimos: solo `SELECT`, `INSERT`, `UPDATE` y `DELETE` sobre las tablas necesarias, sin DDL ni superusuario.
- Los errores de SQL nunca llegan al cliente; se responde con `problem+json` genérico.
- El CI falla ante SQL concatenado (análisis estático con Semgrep o find-sec-bugs) y revisa dependencias vulnerables.
- En E6 se corre sqlmap contra el entorno de prueba, sobre las rutas con parámetros: filtros, búsqueda, checkout, cupón y login.

## RNF-29 Seguridad de la aplicación y de la infraestructura

Pendientes detectados en la revisión de seguridad. Complementan RNF-22, RNF-24, RNF-27 y RNF-28.

| ID | Requisito |
|---|---|
| RNF-29.1 | **Spring Security:** se suma al stack, con su cliente OAuth2 para el ingreso con Google y Facebook (CU-27). Gestiona la sesión (cookie `HttpOnly`, `Secure`, `SameSite`; nuevo id de sesión al iniciar sesión), la protección CSRF para el frontend SPA y las cabeceras de seguridad de la aplicación. Los webhooks quedan fuera de CSRF y se protegen con la verificación de firma (RNF-29.3). |
| RNF-29.2 | **Rol de PostgreSQL de la aplicación:** `tienda_app` no es superusuario, no es dueño de las tablas y no tiene `BYPASSRLS`. Si fuera dueño o superusuario, RLS se saltearía sin avisar. Lo verifica el test de RNF-27, que corre en el CI y en el arranque contra el entorno real. |
| RNF-29.3 | **Webhook de MercadoPago:** además de la idempotencia (RNF-20.3), se verifica la firma HMAC del proveedor (cabecera de firma, con comparación en tiempo constante y control de antigüedad del evento) antes de procesar nada. Firma inválida: `401` y registro sin datos personales. Verificar el formato exacto en la documentación del proveedor. |
| RNF-29.4 | **Subida de imágenes:** el tipo se valida por el contenido real (magic bytes), nunca por el `Content-Type` ni la extensión. Se rechazan archivos que no sean JPG, PNG o WebP. El procesamiento a WebP y miniaturas es superficie de ataque: librería actualizada, límites de dimensiones y píxeles antes de decodificar (contra bombas de descompresión), metadatos (EXIF) eliminados, nombres de archivo generados por el servidor y como máximo 2 conversiones simultáneas (RNF-21). |
| RNF-29.5 | **Rate limit de login:** además del límite por IP (RNF-22), hay un límite por cuenta (email normalizado), para frenar ataques distribuidos desde muchas IP. La respuesta no revela si la cuenta existe. |
| RNF-29.6 | **Tokens del enlace de seguimiento** (y demás tokens de acceso sin cuenta, como recuperación de contraseña): generados con `SecureRandom`, con al menos 128 bits de entropía, y guardados en la base como hash (por ejemplo SHA-256). El token en claro solo viaja en el enlace. |
| RNF-29.7 | **DNI y backups:** *Pendiente de decisión* si el DNI va cifrado en la base (cifrado a nivel de columna). Los backups van cifrados siempre, y la clave se guarda fuera del servidor. |
| RNF-29.8 | **2FA del administrador:** el segundo factor TOTP (RNF-24) pasa de *Propuesto* a *Recomendado como obligatorio*: con un único administrador dueño de la tienda, su cuenta da acceso a todos los datos de clientes. |
| RNF-29.9 | **Cabeceras en Caddy:** HSTS (`Strict-Transport-Security`) y política de seguridad de contenido (CSP) que permita solo los dominios necesarios (incluidos los de RNF-25 y MercadoPago). |
| RNF-29.10 | **Secretos:** el `.env` y cualquier credencial quedan fuera de git (`.gitignore`); el repositorio solo incluye un `.env.example` sin valores reales. |
| RNF-29.11 | **CI:** revisión de dependencias vulnerables (Dependabot u OWASP dependency-check) y escaneo de secretos (secret scanning) en cada build, junto con el análisis estático de RNF-28. |

**Verificación:** tests de firma inválida en el webhook, de archivos con `Content-Type` falso, del límite de login por cuenta, del rol de base de datos (RNF-27) y de las cabeceras de respuesta en el entorno de prueba de E6.

## RNF-30 Requisitos legales del comercio electrónico en Argentina

Obligaciones habituales de una tienda online argentina. **Todo este bloque se valida con un profesional (abogado y contador) antes de E6**; los textos legales los aporta el negocio.

| ID | Requisito |
|---|---|
| RNF-30.1 | **Botón de arrepentimiento** en la página de inicio y en el pie, con acceso directo y sin necesidad de cuenta (CU-29). |
| RNF-30.2 | **Identificación del vendedor** en el pie: razón social o nombre, CUIT, domicilio, email y teléfono de contacto, y el formulario **Data Fiscal** de ARCA (código QR enlazado). |
| RNF-30.3 | **Defensa del consumidor:** enlace visible al sitio oficial de Defensa de las y los Consumidores y a la Ventanilla Federal de reclamos. |
| RNF-30.4 | **Precios:** precio final en ARS con impuestos incluidos y, según el Régimen de Transparencia Fiscal al Consumidor (Ley 27.743), el IVA contenido informado en la ficha, el carrito y el resumen del checkout. El costo de envío y el total final se ven antes de pagar. Si se muestran cuotas, se informa el costo financiero total. |
| RNF-30.5 | **Datos personales (Ley 25.326):** política de privacidad que nombre al responsable, los datos que se recogen, para qué, con quién se comparten (proveedor de pagos, de correo, Google y Meta) y cómo ejercer los derechos de acceso, rectificación y supresión (CU-28). Inscripción de la base de clientes en el Registro Nacional de Bases de Datos de la AAIP. |
| RNF-30.6 | **Términos y política de cambios y devoluciones** enlazados en el checkout antes de confirmar; el pedido guarda la versión de los términos vigente al comprar. |
| RNF-30.7 | **Factura:** sigue la decisión pendiente de `00-dominio-y-reglas.md`. Si la emite el negocio por fuera, el backoffice permite marcar el pedido como facturado y cargar el número de comprobante (*Propuesto*). |

## RNF-31 SEO, accesibilidad y experiencia de compra

| ID | Requisito |
|---|---|
| RNF-31.1 | **SEO:** título y descripción propios por página, URL canónica, `sitemap.xml` generado desde las categorías y productos activos, y `robots.txt` que excluye backoffice, cuenta, checkout, carrito y enlaces de seguimiento (estas páginas llevan además `noindex`). |
| RNF-31.2 | **Datos estructurados:** las fichas publican `Product` y `Offer` de schema.org (nombre, imagen, SKU, precio, moneda ARS y disponibilidad) y, desde E5, el puntaje de reseñas. Se comparte la solución de prerenderizado con las etiquetas Open Graph de E6. |
| RNF-31.3 | **Migración desde Treinta:** las URLs de productos y categorías de la tienda anterior que estén indexadas o compartidas redirigen con `301` a sus equivalentes nuevas; una tabla de correspondencia se carga en E6. |
| RNF-31.4 | **Accesibilidad:** nivel WCAG 2.1 AA como objetivo: contraste suficiente, navegación completa con teclado, etiquetas en todos los campos, mensajes de error asociados al campo y texto alternativo en las fotos (por defecto nombre, color y vista del producto). Lighthouse de accesibilidad de 90 o más en inicio, listado, ficha y checkout. |
| RNF-31.5 | **Guía de talles** (*Propuesto*): tabla de medidas en centímetros por categoría, editable desde el backoffice y visible desde la ficha junto al selector de talle. En ropa de dormir reduce cambios y devoluciones. |
| RNF-31.6 | **Información antes de comprar:** costos de envío por zona, plazos estimados de entrega y medios de pago visibles en una página de preguntas frecuentes y resumidos en la ficha. |
| RNF-31.7 | **Páginas de error y mantenimiento:** 404 y 500 propias con enlace a la tienda y al contacto, y una página de mantenimiento que Caddy sirve mientras se despliega o restaura. |
