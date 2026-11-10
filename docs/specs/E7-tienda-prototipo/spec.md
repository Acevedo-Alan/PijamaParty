# E7 – La tienda como el prototipo

**Estado:** Borrador · **Casos de uso:** CU-30, CU-31 y CU-32, y cambios a CU-01, CU-02, CU-03, CU-08, CU-13, CU-14 y CU-19

## Objetivo

Que la tienda se vea y se recorra como el prototipo aprobado (`docs/design/prototype/index.html`). Las etapas E1 a E6 tomaron del prototipo los colores, la tipografía y algunos diseños (el checkout en dos columnas), pero no sus secciones: la portada es hoy solo la grilla de productos. Las capturas comparativas del 2026-10-19 muestran la diferencia en inicio, listado, ficha, carrito y checkout.

Esta etapa **no cambia reglas de negocio** salvo donde se indica, y cada una de esas reglas está en "Decisiones". Nada del prototipo que prometa algo al comprador (cuotas, descuentos, cambios sin cargo) se muestra sin la decisión del dueño.

## Fuera de alcance

- El código del prototipo: sus scripts en línea, Google Fonts y las fotos de Unsplash no se usan (CSP de T-E6-03, fuente propia de ARCHITECTURE.md sección 11, licencia de las fotos).
- Favoritos (el corazón de la ficha): no hay caso de uso; queda para otra etapa.
- Editar desde el backoffice el carrusel, las fotos de "Elegí para quién" y el logo: son fijos en el frontend (D-03). Solo el banner de personajes se edita (CU-32).
- Cuotas y descuento por transferencia, hasta que el dueño los decida (D-01, D-02).

## Decisiones

Decididas con el dueño el 2026-10-19 y anotadas en `00-dominio-y-reglas.md`, salvo D-01 y D-02, que siguen pendientes.

| ID | Tema | Estado | Decisión |
|---|---|---|---|
| D-01 | "3 cuotas sin interés" (franja, tarjetas y ficha) | Pendiente | **No se muestra hasta decidirlo.** Las cuotas las define MercadoPago, su costo lo paga el negocio y RNF-30.4 exige informar el costo financiero total si se muestran. Se vuelve a ver antes de la salida a producción. |
| D-02 | "10% OFF por transferencia" | Pendiente | **No se muestra hasta decidirlo.** Hoy la transferencia no es un medio de pago (solo medios inmediatos: MercadoPago y MODO); sumarla sería un caso de uso nuevo. |
| D-03 | De dónde salen los textos y fotos de la portada | Decidido | **El banner de personajes se edita desde el backoffice** (CU-32). El carrusel, las fotos de "Elegí para quién" y el logo son fijos en el frontend: fotos dentro del build y textos en `es.ts`; cambiarlos requiere un despliegue. |
| D-04 | Muestras de color (círculos) | Decidido | **Un mapa de nombres de color a su tono en el frontend** (rosa, salvia, beige, crudo, chocolate, terracota, negro, blanco, gris, celeste, azul, rojo, bordó, verde, lila, amarillo, estampado…). Un color que no está en el mapa se muestra con su nombre en texto. |
| D-05 | Etiqueta "Nuevo" | Decidido | Un producto creado en los **últimos 30 días**, calculado en el servidor con el reloj de la aplicación. |
| D-06 | Botón "+" de la tarjeta | Decidido | **Se quita.** No se puede agregar sin elegir talle y color, y la tarjeta ya lleva a la ficha. |
| D-07 | Calcular el envío por código postal (ficha) | Decidido | **Un selector de provincia** que muestra el costo de esa provincia. El envío se cobra por provincia (CU-12), no por código postal. |
| D-08 | "Completá tu look" | Decidido | **Hasta 4 productos de la misma categoría**, los más nuevos, sin el producto abierto. Sale del listado que ya existe, sin cambios en la API. |
| D-09 | Sección "Contactanos" | Decidido | **Email y WhatsApp como enlaces, sin formulario.** El negocio da el email y el número. |
| D-10 | Paginación numerada del prototipo | Decidido | **Se mantiene "Ver más"** (CU-01). |
| D-11 | Contador "N productos" del listado | Decidido | **Se agrega:** el listado informa el total de coincidencias. |
| D-12 | Íconos de confianza de la ficha | Decidido | "Envío gratis desde $X" solo cuando hay umbral (CU-12) y "Pagá con MercadoPago". **"Primer cambio sin cargo" no se muestra:** no es una política confirmada. |
| D-13 | Nombre y apellido en el checkout | Decidido | **Dos campos, "Nombre" y "Apellido"**, en el checkout, la API y el pedido (cambia lo decidido en CU-13; ver "Cambios a CU-13"). El saludo de los correos usa el nombre tal cual se cargó, también los compuestos ("María José"). |
| D-14 | Carrusel de la portada | Decidido | Hasta 3 imágenes. **Pasan solas cada 4 segundos, con botón de pausa** (WCAG 2.2.2) y sin movimiento con `prefers-reduced-motion`. |
| D-15 | Materiales del negocio | Pendiente del negocio | El negocio entrega el logo, las fotos del carrusel (hasta 3), las 3 de "Elegí para quién", la primera foto del banner de personajes (después la cambia desde el backoffice), el email y el WhatsApp de contacto y los enlaces de Instagram y TikTok. Sin ellas, cada lugar muestra el fondo degradado del prototipo. Recibido el 2026-10-23: el logo. El rótulo recortado va en el encabezado (fondo transparente, sin los pedacitos de personajes de las esquinas, 48 px de alto); la ilustración completa es la primera foto del banner de personajes y se sube desde "Portada". Recibido el 2026-10-28: el WhatsApp, +54 9 342 530-4507. Recibido el 2026-10-29: el Instagram, https://www.instagram.com/pijamaparty.zzz/. |
| D-16 | Animaciones | Decidido | **Con la librería Motion** (`motion`, para React), cargada de forma diferida para no demorar la primera carga, sobre los tokens de la sección "Animaciones". |
| D-17 | Tipografía | Decidido | **Inter** en lugar de Quicksand, en títulos y textos, en la tienda y el backoffice (el dueño, 2026-10-24). Alojada en la tienda, nunca desde Google Fonts. |

## Animaciones

El prototipo se siente amable porque responde a lo que hace el comprador: el carrito "salta" al agregar, los paneles se deslizan, los avisos suben. La tienda adopta ese mismo lenguaje como un sistema, no efecto por efecto.

**Principios**
- **El movimiento responde a una acción** (agregar, abrir, elegir, avanzar de paso) y muestra qué cambió. Nada se mueve solo, salvo el carrusel (D-14), el cielo de las páginas de la cuenta (la chibi respira y parpadea despacio mientras espera, y las estrellas se prenden y se apagan; T-E7-47) la chibi dormida del carrito vacío, que respira (T-E7-48), la de los estados vacíos, que respira, parpadea o mece su "?" (T-E7-49), las estrellitas que giran mientras se espera un pago (T-E7-50), las estrellas de la franja de noche del pie, que titilan, sus nubes, que flotan, y su conejo de la luna, que parpadea (T-E7-55), los pétalos de sakura de la portada en primavera (T-E7-56), y la luna de las esperas grandes, a la que le salen "z" mientras espera (T-E7-54).
- **Entradas:** el texto de bienvenida de la portada aparece una vez al cargar; las tarjetas de producto aparecen cuando se suman a la grilla (la primera tanda, "Ver más", un filtro u orden nuevo), nunca por bajar hasta ellas; al cambiar de página, el contenido nuevo entra, pero no en la primera carga de la tienda. Las secciones no aparecen de a una al bajar.
- **Rápido y suave:** entre 120 y 450 ms (la entrada de la portada, hasta 700 ms; la espera del botón de pagar, al menos 600 ms), con curvas que frenan al llegar.
- **Accesible:** con `prefers-reduced-motion` no hay desplazamientos ni escalas, solo cambios de opacidad cortos; los efectos de hover solo con `@media (hover: hover)`.
- **Con Motion, sin demorar la carga (D-16):** las animaciones de entrada y salida, los pasos y los rebotes usan la librería Motion, que se carga después de mostrar la página; hasta entonces, todo se ve en su lugar sin animar. Los efectos simples (hover, colores) siguen en CSS. Solo se anima `transform` y `opacity`, nunca el tamaño o la posición. La foto principal de la portada no se anima, para no demorar el LCP (RNF-11).

**Catálogo de animaciones**

| Dónde | Qué se mueve |
|---|---|
| Portada | El título, el texto y "Descubrí la colección" suben y aparecen en cascada una vez; las fotos del carrusel cambian con un fundido. |
| Tarjeta de producto | En computadora, la foto se acerca apenas al pasar el mouse; si el producto tiene una segunda foto, aparece con un fundido. |
| Encabezado (celular) | Al bajar por la página se esconde hacia arriba; al subir, vuelve. |
| Círculos de color y talles | El elegido se marca con un anillo que crece. |
| Agregar al carrito (T-E7-41) | En tres tiempos. **Apretar (0–120 ms):** el botón se hunde un poco y sigue así mientras dice "Agregando…". **Transformación (120–350 ms):** lo que dice el botón (bolsa, "Agregar al carrito" y subtotal) sube y desaparece; desde abajo entra con rebote elástico un tilde que se dibuja solo y "¡Listo! En tu bolsa", sobre un fondo verde salvia, durante 1,5 s. **Pulso (350 ms):** la bolsa del encabezado da un saltito inclinada y su número hace un "pop"; a los 450 ms se abre el panel del carrito. Si el agregado es rechazado o falla, el botón se sacude una vez y conserva su texto. Con movimiento reducido, solo se cambia un texto por el otro, sin desplazamientos ni dibujo. |
| "Confirmar y pagar": esperando | Un arcoíris pastel llena todo el botón de izquierda a derecha a saltitos, como una carga real (un empujón, una pausa, otro empujón), y sigue avanzando despacio si el servidor tarda, con un brillo que lo recorre. Una chibi en pijama y gorro de dormir (dibujo propio) corre sobre el borde del botón en la punta de la barra. La espera dura al menos 0,6 s aunque el servidor conteste antes, para que la barra se vea (el botón de agregar ya no espera: T-E7-41). Con movimiento reducido, el arcoíris solo aparece con un fundido, sin brillo ni chibi. |
| "Confirmar y pagar": listo | La barra se completa y la chibi salta al final. Después de agregar (T-E7-41), el aviso aparece en el encabezado del carrito, sobre su título y hasta el botón de cerrar, sin tapar ni correr los productos (en el celular, el producto agregado sigue a la vista). En pagar, la barra queda llena mientras se abre el pago. |
| "Confirmar y pagar": rechazado o con error | La barra se pone rojo suave y se vacía hacia la izquierda y la chibi desaparece; el mensaje junto al botón dice por qué. |
| Panel del carrito, menú, filtros, vista ampliada | Se deslizan desde su borde con el fondo oscureciéndose; al cerrar, el camino inverso. Lo que sube desde abajo (el menú del celular, la vista ampliada) se abre más despacio y frena suave. |
| Enlaces del encabezado, lupa, ícono de usuario y carrito (con mouse) | Al pasar el mouse, un arcoíris pastel llena el fondo desde abajo hacia arriba; al salir, baja. Con movimiento reducido, solo aparece con un fundido. |
| Contenido del menú del celular | Cada enlace entra un poco desde la izquierda y aparece, uno tras otro, mientras entra el menú. Al abrir un nivel ("Todo", "Mi cuenta"), el actual se corre a la izquierda y se desvanece y el nuevo entra desde la derecha; "Volver" hace el camino inverso (T-E7-39). |
| Tarjetas que se suman a la grilla | Suben un poco y aparecen, una tras otra; las que ya estaban no se mueven. |
| Barra de envío gratis | Se llena con suavidad; al llegar al umbral, el mensaje "¡Tenés envío gratis!" aparece con un pequeño rebote. |
| Secciones desplegables de la ficha | Se abren y cierran deslizando su altura. |
| Pasos del checkout | El contenido del paso nuevo sube y aparece. |
| Cargando | Las tarjetas y textos en espera laten suavemente (esqueletos). |
| Agregar al carrito: destellos (T-E7-53) | Cuando el botón llega a "¡Listo! En tu bolsa", seis estrellitas (✦) saltan alrededor del tilde: crecen, se alejan un poco y se desvanecen, una tras otra, una sola vez. Con movimiento reducido no aparecen. |
| Franja de noche del pie (T-E7-54) | Sus estrellas titilan, cada una a su ritmo. Con movimiento reducido quedan prendidas. |
| Nubes y conejo del pie (T-E7-55) | Las nubes de la suerte flotan apenas de costado y el conejo de la luna parpadea cada pocos segundos. Con movimiento reducido quedan quietos. |
| Sakura (T-E7-56) | En primavera, ocho pétalos caen despacio sobre el carrusel de la portada, meciéndose y girando, cada uno a su ritmo. Con movimiento reducido no aparecen. |
| Luna de las esperas (T-E7-54) | La luna se mece apenas y le salen tres "z" que suben y se desvanecen, una tras otra, mientras espera. Con movimiento reducido queda quieta. |
| Cambio de página | El contenido nuevo sube un poco y aparece con un fundido; no en la primera carga. |
| Chibi en el pago del pedido (T-E7-50) | **Esperando** (cobrando la tarjeta, pago en proceso, confirmando el pago de MODO): mira hacia arriba con la boquita en "o" y tres estrellitas giran despacio sobre su cabeza, mientras dure. **Rechazo:** niega con la cabeza una vez y queda triste. Con movimiento reducido, las estrellitas quedan quietas y no niega. |
| Chibi en los estados vacíos (T-E7-49) | **Búsqueda sin resultados y 404:** la cabeza inclinada, mirando de costado con la boquita en "o", y el "?" que se mece despacio sobre su cabeza. **"Mis pedidos" sin pedidos:** respira y parpadea como en reposo, sin "zZz". Con movimiento reducido, quietas en su gesto. |
| Chibi en el pedido pagado y el carrito vacío (T-E7-48) | La misma chibi de la cuenta, sin cielo. **Pedido pagado:** al abrirse salta feliz, con corazoncitos y unas estrellitas que caen a su alrededor, una vez; después queda sonriendo. **Carrito vacío:** duerme abrazando una almohada, respira despacio y le salen "zZz". Con movimiento reducido, quedan quietas en su gesto. |
| Chibi de la cuenta (T-E7-47) | La chibi en pijama de "Confirmar y pagar" acompaña el formulario de Ingresar, Crear cuenta, Recuperar contraseña y Elegir contraseña nueva. **En reposo:** respira (sube y baja apenas), parpadea cada pocos segundos y le salen "zZz". **Siempre:** las estrellas del cielo se prenden y se apagan, cada una a su ritmo, y cada tanto cruza una estrella fugaz. **Según la página:** en Crear cuenta saluda con la mano mientras espera; en Recuperar contraseña tiene un "?" sobre la cabeza; en Elegir contraseña nueva sostiene una llavecita. **El cartel** acompaña: rebota cuando la chibi salta y se balancea cuando niega. **Al terminar** (cuenta creada, email de recuperación enviado) caen estrellitas sobre el cielo. **En un campo de texto** (email, nombre): abre bien los ojos y se inclina hacia el formulario. **En una contraseña:** se tapa los ojos con las manos; con la contraseña a la vista, espía entre los dedos. **Esperando al servidor:** mira hacia arriba. **Error** (datos incorrectos, email ya usado, demasiados intentos, enlace vencido): mueve la cabeza que no y pone carita triste, una vez. **Listo** (cuenta creada, email de recuperación enviado): salta feliz con corazoncitos. Al ingresar, la tienda pasa a la página siguiente sin esperar a la chibi. Con movimiento reducido no respira, no parpadea, no niega, no salta ni saluda, las estrellas quedan prendidas, no hay estrella fugaz ni lluvia de estrellitas, y el cartel queda quieto: cambia de gesto de golpe, sin animar (como el resto de la tienda con movimiento reducido). |

**Criterios de aceptación**
- Dado un visitante con movimiento reducido, cuando agrega un producto, abre el carrito o cambia de paso, entonces nada se desplaza ni cambia de escala.
- Dada la grilla con productos, cuando "Ver más" suma otros, entonces solo los nuevos entran; con movimiento reducido, solo aparecen con un fundido.
- Dado el menú del celular, cuando se abre, entonces entra desde la izquierda ocupando toda la pantalla y los enlaces entran uno tras otro (T-E7-39).
- Dado un producto agregado desde la ficha, cuando la respuesta llega, entonces el ícono del carrito salta una vez, su número hace un "pop" y se abre el panel con el aviso; un agregado rechazado no hace nada de eso (T-E7-41).
- Dado un agregado desde la ficha, cuando el servidor contesta, entonces el botón dice "Agregando…" mientras espera y, si todo sale bien, pasa a "¡Listo! En tu bolsa" con un tilde, sin espera mínima (T-E7-41).
- Dado un agregado exitoso, cuando el botón termina, entonces los lectores de pantalla solo leen su confirmación; con movimiento reducido, solo se cambia un texto por el otro. Dado un agregado rechazado o con error, entonces el botón se sacude una vez y conserva su texto (T-E7-41).
- Dado "Confirmar y pagar", cuando espera la respuesta, entonces el botón muestra la barra de arcoíris con la chibi corriendo encima; un rechazo la vacía en rojo suave.
- Dada la portada, cuando se mide con Lighthouse, entonces el LCP sigue debajo de 3 s y el "Cumulative Layout Shift" debajo de 0,1.
- Dado un teléfono, cuando se toca una tarjeta, entonces no queda trabado ningún efecto de hover.

## Toques kawaii (T-E7-52 a T-E7-56)

La chibi le dio a la tienda una voz de pijamada japonesa; estos tres toques la llevan a momentos a los que la chibi no llega. Van en los bordes de la compra (avisos, estados vacíos, la confirmación, el pie, las esperas): la grilla, la columna de la ficha y el formulario del checkout no llevan ninguno, para que manden la foto y el precio. Todo es decorativo: los lectores de pantalla no lo leen y ningún texto va encima.

**Reglas**
- **Kaomoji (T-E7-52):** una carita japonesa sigue a seis textos, en la misma línea, en el rosa de los textos de la tienda:

  | Texto | Kaomoji |
  |---|---|
  | "Agregado al carrito" (el aviso) | `(｡•ᴗ•｡)♡` |
  | "¡Tenés envío gratis!" | `＼(^o^)／` |
  | "Tu carrito está vacío." | `(－ω－) zzZ` |
  | La búsqueda sin resultados | `(・・?)` |
  | "No encontramos esta página" (404) | `` (´・ω・`) `` |
  | "¡Gracias por tu compra, {nombre}!" | `(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧` |

  Los lectores de pantalla leen solo el texto. Un navegador que no sabe dibujarlas muestra el texto solo.
- **Destellos al agregar (T-E7-53):** un agregado exitoso desde la ficha (también desde la barra fija de abajo) hace saltar seis estrellitas alrededor del tilde. Un agregado rechazado o con error no tiene destellos.
- **La noche (T-E7-54):**
  - **Franja de noche del pie:** una franja arriba del pie, dentro de su rosa, con una luna en cuarto creciente a la derecha y nueve estrellitas en rosa y dorado.
  - **El conejo de la luna y las nubes de la suerte (T-E7-55):** en China, Japón y Corea se cuenta que en la luna vive un conejo. La luna del pie tiene al conejo de la tienda sentado adentro: un dibujo propio en el estilo de la chibi, redondo como un mochi, con una oreja parada y otra caída (el "Mochi" del boceto del 10/11/2026). En la franja hay dos nubes de la suerte (祥云, las nubes enruladas del arte chino), blancas con borde lila.
  - **Sakura en primavera (T-E7-56):** del 1 de septiembre al 30 de noviembre, según la fecha de Argentina, ocho pétalos de cerezo caen sobre el carrusel de la portada, por encima de las fotos y debajo de los textos y botones, sin estorbar los toques. Las fechas son fijas; cambiarlas es un cambio en la tienda. Fuera de esas fechas, y con movimiento reducido, no hay pétalos.
  - **Luna de las esperas:** las cinco esperas grandes de la tienda muestran una lunita con tres "z" antes del texto de carga: el carrito, el checkout, la página del pedido, "Mis pedidos" y la primera visita a una página que todavía se está descargando. Los esqueletos del listado y de la ficha siguen igual, porque guardan el lugar del contenido y evitan saltos. Las esperas chicas (filtros, opiniones, preguntas frecuentes, guía de talles) siguen solo con su texto.

**Criterios de aceptación**
- Dado el carrito vacío, cuando se muestra, entonces "Tu carrito está vacío." lleva `(－ω－) zzZ` y el lector de pantalla lee solo "Tu carrito está vacío." (T-E7-52).
- Dado un pedido pagado, cuando se abre su página, entonces el título dice "¡Gracias por tu compra, Ana!" para los lectores de pantalla y lleva `(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧` en pantalla (T-E7-52).
- Dado un producto agregado desde la ficha, cuando el servidor contesta bien, entonces saltan seis estrellitas alrededor del tilde; si lo rechaza, no; con movimiento reducido, no aparecen (T-E7-53).
- Dada cualquier página pública, cuando se ve el pie, entonces su franja de noche tiene una luna y nueve estrellas, ocultas a los lectores de pantalla; con movimiento reducido no titilan (T-E7-54).
- Dado el carrito mientras carga, cuando se muestra, entonces la lunita con sus "z" está antes de "Cargando carrito…" y el lector de pantalla lee solo "Cargando carrito…" (T-E7-54).
- Dada cualquier página pública, cuando se ve el pie, entonces su luna tiene al conejo y la franja dos nubes, ocultos a los lectores de pantalla (T-E7-55).
- Dado el 15 de octubre en Argentina, cuando se abre la portada, entonces caen ocho pétalos sobre el carrusel; dado el 1 de diciembre o el 31 de agosto, no; dado el 30 de noviembre a las 23:30 de Argentina, todavía caen (T-E7-56).
- Dado un visitante con movimiento reducido en primavera, cuando abre la portada, entonces no hay pétalos (T-E7-56).

## CU-30 Portada

**Actor:** Visitante

**Flujo principal**
1. El visitante entra a `/`.
2. El sistema muestra, en orden: el carrusel de bienvenida, el banner de personajes, "Elegí para quién", "Todos los productos" (la grilla de CU-01 con los filtros de CU-02) y "Contactanos". El botón de arrepentimiento está en el pie, como en todas las páginas (RNF-30.1).
3. "Descubrí la colección" baja hasta "Todos los productos".
4. Cada tarjeta de "Elegí para quién" (Mujer, Hombre, Niños) abre el listado filtrado por ese público.

**Reglas**
- El carrusel y "Elegí para quién" tienen textos y fotos fijos (D-03); el banner de personajes es el que carga el administrador (CU-32). Una foto que falta muestra el fondo degradado.
- Sin banner publicado, la portada no muestra esa sección.
- Mientras el banner carga, su lugar muestra el fondo degradado del tamaño del banner, para que las secciones de abajo no se muevan cuando llega. Si no hay banner publicado o no se puede cargar, ese lugar se cierra.
- El email o el WhatsApp que el negocio todavía no pasó (D-15) no se muestra; sin ninguno de los dos, la portada no muestra "Contactanos".
- Con filtros u orden en la URL, la portada abre directamente en "Todos los productos".
- "Todos los productos" lleva debajo del título la línea "Filtrá por talle, color, temporada y más.", como en el prototipo.
- El banner de personajes ocupa todo el ancho de la página como una franja, con su contenido al ancho de la página, como en el prototipo.
- El listado de una categoría (`/categoria/{slug}`) no muestra las secciones de la portada.

**Criterios de aceptación**
- Dada la portada en un celular, cuando carga, entonces se ven el carrusel, el banner de personajes, "Elegí para quién", la grilla y el contacto, en ese orden, y el pie con el botón de arrepentimiento.
- Dada la portada, cuando se toca "Mujer" en "Elegí para quién", entonces se ve la grilla filtrada por público mujer y la URL tiene `publico=mujer`.
- Dado un visitante con movimiento reducido, cuando abre la portada, entonces el carrusel no pasa solo.
- Dado el carrusel pasando solo, cuando se toca pausa, entonces se detiene.
- Dados inicio, listado, ficha y checkout, cuando se miden con Lighthouse, entonces siguen cumpliendo RNF-11 y RNF-31.4.
- Dada la portada, cuando carga, entonces no hay un bloque aparte del botón de arrepentimiento: está en el pie.

## CU-31 Encabezado, menú y pie

**Actor:** Visitante

**Flujo principal**
1. En todas las páginas públicas, el encabezado muestra el logo, la navegación (Todo, Mujer, Hombre, Niños, Temporada Invierno), la búsqueda, un ícono de usuario (lleva a "Ingresar"; con sesión lleva un punto y abre "Mi cuenta") y el carrito. "Crear cuenta" ya no está en el encabezado: lo ofrece la página de ingreso. En el checkout y en la página del pedido, el encabezado muestra solo el logo, el ícono de usuario y el carrito, sin navegación, menú ni búsqueda, para que el comprador no abandone la compra sin querer; la franja de envío gratis se mantiene.
2. En el celular, la navegación va en un menú que se abre con un botón (T-E7-39): un panel que entra desde la izquierda y ocupa toda la pantalla (hasta 440 px de ancho; en la tablet se ve la página oscurecida a su derecha), sin márgenes ni bordes redondeados. Arriba, el título "Menú" (o "Volver" dentro de un nivel) y, a la derecha, el botón para cerrar. Debajo, en una columna que se desplaza si no entra: los enlaces en letra grande y mayúscula; tras una línea, los accesos rápidos en letra más chica, cada uno con su ícono: la cuenta ("Ingresar", o "Mi cuenta" con sesión), "Mi carrito" con la cantidad de unidades y "Guía de talles"; y al final, en letra chica, "¿Necesitás ayuda?" con las preguntas frecuentes y, si el negocio pasó el número, "Escribinos por WhatsApp", y después Instagram y TikTok. "Todo" (si hay categorías) y "Mi cuenta" abren un nivel propio que entra por encima del menú, como en el menú de dior.com.
3. Sobre el encabezado, una cinta de anuncios (T-E7-39) pasa de derecha a izquierda, sin cortes, los mensajes de la tienda, como en oudeam.com: "Envío gratis desde $X" (si hay umbral), "Envíos por Correo Argentino" y "Pagá con" los medios habilitados (por ejemplo "Pagá con MercadoPago o MODO").
4. El pie es una franja rosa clara que ocupa todo el ancho de la pantalla, con el contenido dentro del ancho de la página; no repite el nombre de la tienda en letras grandes. Empieza con el bloque de la tienda: "Pijamas para toda la familia" y, más abajo, Redes sociales (las redes que pasó el negocio, como íconos en círculos, en fila; al pasar el mouse o con el foco del teclado, un degradé rosa crece desde el centro del círculo hasta llenarlo y el ícono se vuelve blanco; para quien pidió reducir el movimiento, el degradé solo aparece; T-E7-40). En el menú del celular, los íconos hacen lo mismo. Después, cuatro columnas: Tienda (los mismos enlaces de la navegación del encabezado), Ayuda y legales (preguntas frecuentes, Cómo comprar, Guía de talles, Cambios y devoluciones, Términos y condiciones y el botón de arrepentimiento), Contacto (el número de WhatsApp, que abre el chat, el email, que abre un mensaje nuevo, el horario de atención y la dirección, que abre Google Maps, cada uno con su ícono) y Medios de pago (un ícono por cada medio habilitado, en fila, y debajo "Envíos" con el ícono de Correo Argentino). Los títulos de las columnas van en el color de acento. En el celular y la tablet, el bloque de la tienda arriba y las columnas de a dos (en el celular, Medios de pago y Envíos lado a lado, con los títulos a la misma altura); en pantallas de menos de 360 px, una columna debajo de la otra (T-E7-40); en la computadora, todo en una fila, con una línea vertical después del bloque. Debajo, separada por una línea, una franja con "Cookies" y el año con el nombre de la tienda.
5. Con el número de WhatsApp, todas las páginas públicas salvo el checkout y la cuenta muestran un botón redondo de WhatsApp fijo abajo a la derecha, "Escribinos por WhatsApp", que abre el chat en otra pestaña. En la ficha, en el celular, sube por encima de la barra fija de "Agregar al carrito" mientras esa barra se ve. Nunca tapa lo que tiene el foco del teclado ni el final del pie. Mientras la sección "Contactanos" de la portada está a la vista, el botón se esconde, porque esa sección trae su propio botón de WhatsApp (T-E7-42).
6. Solo en la portada (con o sin filtros), justo arriba del pie, una barra de beneficios con tres bloques iguales, cada uno con un ícono a la izquierda y, a su derecha, un título en negrita y mayúsculas y un texto: "Enviamos tu compra" (camión; "Entregas dentro de Santa Fe Capital y todo el país"), "Todas las formas de pago" (tarjeta; "Pagá con" y los medios habilitados, por ejemplo "Pagá con MercadoPago o MODO") y "Comprá con seguridad" (candado; "Tus datos siempre protegidos"). Cada bloque es una tarjeta (T-E7-43): blanca, con un borde fino, esquinas redondeadas y un hilo de arcoíris pastel de 3 px arriba; el ícono va en un círculo rosa suave, dibujado en el color de acento. La franja no tiene fondo propio ni línea arriba, así las tarjetas blancas se despegan de la página crema. En la computadora, las tres van en fila con 20 px entre ellas; en el celular, una debajo de otra a todo el ancho con 12 px entre ellas, y el borde y el espacio son el único separador. El logo de la tienda no se usa de fondo (es una letra cursiva con adornos que se vería borrosa bajo el texto y bajaría su contraste).

**Reglas**
- Cada enlace de la navegación abre el listado filtrado: Mujer, Hombre y Niños por público; Temporada Invierno por temporada. En la portada, bajan hasta "Todos los productos".
- El ícono de usuario se ve en todos los tamaños (T-E7-39): en el celular va entre la lupa y el carrito, con el logo siempre centrado. En pantallas muy angostas los íconos del encabezado se achican un poco para que entren los tres a la derecha del logo.
- Con sesión, "Mi cuenta" abre una lista con el saludo, "Mis pedidos", "Mis direcciones", "Mi cuenta" y "Salir". En el menú del celular, esos enlaces se ven directamente, en lugar de "Ingresar" y "Crear cuenta".
- Categorías en el menú del celular (T-E7-37, T-E7-39): si hay categorías, "Todo" lleva una flechita y abre su nivel: "Ver todo" (todos los productos) y las categorías activas en su orden, cada una con su página; la categoría que se está viendo aparece marcada. Sin categorías, o si no se pudieron leer, "Todo" abre directamente todos los productos. Al abrir un nivel, el foco pasa a "Volver"; al volver, el foco queda en lo que abrió el nivel. Escape o el botón de cerrar cierran todo el menú, que vuelve a abrirse en el primer nivel.
- Accesos rápidos del menú (T-E7-37, T-E7-39): primero la cuenta, con el ícono de persona del encabezado. Sin sesión, "Ingresar" lleva a la página de ingreso (que ofrece "Crear cuenta"); el menú ya no tiene los botones "Ingresar" y "Crear cuenta". Con sesión, "Mi cuenta" abre su nivel con el saludo, "Mis pedidos", "Mis direcciones", "Mi cuenta" y "Salir". "Mi carrito" muestra las unidades como el botón del carrito; al tocarlo se cierra el menú y se abre el carrito. No hay "Mis favoritos" (la lista de deseos está fuera de alcance) ni un bloque de promociones: no hay de dónde sacarlas y no se promete nada que la dueña no haya decidido (D-01, D-02).
- La cinta no muestra cuotas ni descuentos (D-01, D-02). Sin umbral no dice "Envío gratis desde $X", y sin medios de pago leídos no dice "Pagá con"; "Envíos por Correo Argentino" está siempre, así que la cinta siempre se ve y el encabezado no salta cuando llegan los datos. Se detiene con el mouse encima o con el foco del teclado dentro, y un botón chico a su derecha ("Pausar anuncios" / "Reanudar anuncios") la detiene y la vuelve a mover. Para quien pidió reducir el movimiento, no se mueve: muestra el primer mensaje centrado.
- Los enlaces del pie a páginas que todavía no existen (T-E6-06) no se muestran hasta que existan.
- Un dato de contacto o una red social que el negocio todavía no pasó (D-15) no se muestra. Sin email, WhatsApp, horario ni dirección, no hay columna Contacto; Redes sociales muestra solo las redes con enlace, en el pie y en el menú del celular; sin ninguna, no aparece (T-E7-40: ya no hay marcadores con borde punteado). Sin medios de pago habilitados, la columna Medios de pago muestra solo "Envíos".
- La barra de beneficios no promete medios de pago, cuotas, descuentos ni recargos que el checkout no tiene (D-01, D-02): el bloque de pagos nombra solo los medios habilitados, y sin ellos (todavía no se saben o no se pudieron leer) ese bloque no se muestra.
- Los íconos de medios de pago son dibujos propios de la tienda (una tarjeta para MercadoPago, un celular para MODO), no los logos de las marcas, con el nombre del medio como texto para lectores de pantalla.
- Los datos del vendedor de RNF-30.2 se suman al pie con T-E6-21.
- En el celular, el encabezado queda fijo arriba, pero se esconde mientras el visitante baja por la página y vuelve apenas sube, para dejar más lugar a los productos. No se esconde cerca del principio de la página, con el menú, la búsqueda, el carrito o "Mi cuenta" abiertos, ni con el foco del teclado dentro de él. En la computadora no se esconde nunca.
- Una vez que la página bajó, el fondo del encabezado se vuelve un poco transparente y difuminado, así se ve lo que pasa por detrás sin perder la lectura del logo y los íconos. Arriba de todo, el fondo es el de siempre.
- En la computadora, una vez que la página bajó, el encabezado se convierte en una píldora flotante: mantiene el logo, la navegación, la búsqueda, la cuenta y el carrito sobre un fondo con los bordes completamente redondeados, separado unos milímetros del borde de arriba, con el mismo fondo transparente y difuminado y una sombra suave; el logo se achica un poco. Al volver arriba, vuelve a ser la barra de todo el ancho. La barra se desvanece mientras la píldora aparece; solo se animan posiciones y transparencias, nunca tamaños ni el difuminado, para que se vea fluido también en Firefox (T-E7-39). Sin animación para quien pidió reducir el movimiento, y la página no salta. En el celular no cambia.

**Criterios de aceptación**
- Dado un umbral de envío gratis de $50.000 y MercadoPago habilitado, cuando se abre cualquier página pública, entonces la cinta muestra "Envío gratis desde $50.000", "Envíos por Correo Argentino" y "Pagá con MercadoPago"; sin umbral, los dos últimos (T-E7-39).
- Dada la cinta en movimiento, cuando se toca "Pausar anuncios", entonces se detiene; al tocarlo otra vez, sigue (T-E7-39).
- Dado el checkout o la página del pedido, cuando se ve el encabezado, entonces tiene el logo, el usuario y el carrito, pero no la navegación, el menú ni la búsqueda.
- Dado un celular, cuando el visitante baja por la página, entonces el encabezado se esconde; cuando sube, vuelve. En la computadora siempre se ve.
- Dada una página bajada, cuando se ve el encabezado, entonces su fondo es un poco transparente; al volver arriba, es el de siempre.
- Dada una página bajada en la computadora, cuando se ve el encabezado, entonces es una píldora flotante con el logo, la navegación y los íconos; al volver arriba, es la barra de todo el ancho.
- Dado un celular, cuando se abre el menú, entonces muestra la navegación y el ingreso, se navega con teclado, y Escape lo cierra y devuelve el foco al botón.
- Dadas las categorías "Pijamas" y "Camisones", cuando se toca "Todo" en el menú, entonces entra su nivel con "Ver todo" y las dos categorías con enlace a su página; al tocar "Volver", se ve otra vez el primer nivel (T-E7-37, T-E7-39).
- Dada la página de "Camisones", cuando se abre el nivel "Todo", entonces "Camisones" está marcada (T-E7-37, T-E7-39).
- Dado un visitante sin sesión, cuando abre el menú, entonces el primer acceso es "Ingresar" y no hay "Crear cuenta"; con sesión, es "Mi cuenta" y abre "Mis pedidos", "Mis direcciones", "Mi cuenta" y "Salir" (T-E7-39).
- Dado un carrito con 2 unidades, cuando se toca "Mi carrito" en el menú, entonces el menú se cierra y se abre el carrito (T-E7-37).
- Dado el enlace "Temporada Invierno", cuando se toca, entonces la grilla muestra solo productos de invierno.
- Dado el pie, cuando se revisa, entonces el botón de arrepentimiento sigue visible (RNF-30.1).
- Dados MercadoPago y MODO habilitados, cuando se revisa el pie, entonces Medios de pago muestra sus dos íconos en fila, cada uno con su nombre para lectores de pantalla.
- Dados el email y el WhatsApp de contacto, cuando se revisa el pie, entonces Contacto muestra el número, que abre el chat de WhatsApp, y el email, que abre un mensaje nuevo; sin ninguno, no hay columna Contacto.
- Dados Instagram y TikTok, cuando se revisa el pie, entonces Redes sociales muestra sus dos íconos, que abren cada red en otra pestaña; con solo Instagram, solo aparece Instagram; sin ninguna, no hay Redes sociales (T-E7-40).
- Dada cualquier página pública, cuando se revisa el pie, entonces dice "Pijamas para toda la familia", y Tienda tiene los cinco enlaces de la navegación del encabezado.
- Dados el horario y la dirección, cuando se revisa el pie, entonces Contacto muestra el horario y la dirección, que abre Google Maps en otra pestaña; sin ellos, no aparecen.
- Dada cualquier página pública, cuando se revisa el pie, entonces Medios de pago muestra "Envíos" con el ícono de Correo Argentino, aunque los medios de pago no se puedan leer.
- Dada cualquier página pública, cuando se revisa el pie, entonces Ayuda y legales lleva a Preguntas frecuentes, Cómo comprar, Guía de talles, Cambios y devoluciones, y Términos y condiciones.
- Dadas guías de talles cargadas en el backoffice, cuando se abre Guía de talles, entonces muestra la de cada categoría activa que tenga una, en el orden de las categorías; sin guías, avisa que todavía no hay.
- Dadas las páginas Cómo comprar, Cambios y devoluciones, y Términos y condiciones, cuando se abren, entonces muestran sus textos; Cambios y devoluciones lleva al botón de arrepentimiento, y los términos llevan a las dos.
- Dado el número de WhatsApp, cuando se abre la portada, una ficha o las preguntas frecuentes, entonces el botón flotante abre el chat en otra pestaña; en el checkout y en la cuenta no aparece; sin número, no aparece en ninguna página.
- Dados MercadoPago y MODO habilitados, cuando se abre la portada, entonces arriba del pie se ven "Enviamos tu compra", "Todas las formas de pago" con "Pagá con MercadoPago o MODO" y "Comprá con seguridad"; si los medios de pago no se pueden leer, se ven solo el primero y el último. En cualquier otra página, como "Ingresar", no hay barra de beneficios.
- Dado un visitante en la computadora o en el celular, cuando se ve el encabezado, entonces el ícono de usuario lleva a "Ingresar" y no hay "Crear cuenta" (T-E7-39 en el celular).

## La chibi en los estados vacíos (T-E7-49)

**Reglas nuevas**
- La chibi de la cuenta acompaña tres pantallas sin contenido, arriba de su texto: la búsqueda sin resultados (CU-26) sostiene una lupa y tiene un "?" sobre la cabeza, con la carita de duda; la página que no existe (404) sostiene un mapa abierto con el "?" y la misma carita; "Mis pedidos" sin pedidos (CU-22) sostiene una bolsita de compras vacía y sonríe. Los textos, los enlaces y los botones de cada pantalla no cambian.
- Es decorativa: los lectores de pantalla no la leen. No agrega fotos ni carga nada aparte, y ocupa su lugar desde el principio, así nada se corre.

**Criterios de aceptación**
- Dada una búsqueda sin resultados, cuando se muestra, entonces la chibi tiene la lupa y el "?" arriba de "No encontramos productos para…", y los lectores de pantalla no la leen (T-E7-49).
- Dada una dirección que no existe, cuando se abre, entonces la chibi tiene el mapa y el "?" arriba de "No encontramos esta página" (T-E7-49).
- Dado un cliente sin pedidos, cuando abre "Mis pedidos", entonces la chibi tiene la bolsita vacía arriba de "Todavía no hiciste ningún pedido." (T-E7-49).

## Cambios a CU-19 (ingresar y crear cuenta)

**Reglas nuevas**
- Ingresar, Crear cuenta, Recuperar contraseña y Elegir contraseña nueva van en una tarjeta blanca, con el título y una frase de introducción adentro. Ingresar dice "Ingresá para ver tus pedidos y comprar más rápido."; Elegir contraseña nueva, "Elegí la contraseña con la que vas a ingresar de ahora en más.".
- Junto a la tarjeta, la chibi en pijama (dibujo propio, la de "Confirmar y pagar", más grande y en el estilo chibi "super deformed": cabeza enorme y cuerpo chiquito, ojos como óvalos altos oscuros con brillo, contorno grueso, colores planos, pelo largo y un mechón que sale del gorro; inspirado en el estilo, sin copiar ningún personaje) en un cielo pastel de noche, con luna, estrellas, nubes y el cartel "Pijama Party" del logo de la tienda (T-E7-47). En la computadora (desde 860 px), el cielo y el formulario forman una sola tarjeta partida, con un único marco, el cielo a la izquierda y del mismo alto que el formulario; el cartel va en primer plano en la parte de abajo del cielo y la chibi se asoma por arriba de su marco, con la cara y las manos sobre el borde. En el celular, el cielo es una franja baja arriba, con el cartel a la izquierda y la chibi asomándose sobre el borde de la tarjeta; en un teléfono de 667 px de alto, el título y el primer campo se ven sin bajar. El dibujo es decorativo: los lectores de pantalla no lo leen y no tapa ni corre nada del formulario. Sus gestos están en "Animaciones".
- Recuperar contraseña termina con "¿Te acordaste de tu contraseña?" y "Volver a ingresar"; la contraseña nueva también tiene el ojo.
- En Ingresar, "¿Olvidaste tu contraseña?" va a la derecha de "Contraseña". Abajo, separado por una línea, "¿Primera vez acá?" y un botón "Crear cuenta" a todo el ancho. Crear cuenta termina igual, con "¿Ya tenés cuenta?" e "Ingresar".
- Los campos empiezan con un ícono de línea fina: un sobre en el email, un candado en la contraseña y una personita en el nombre; con el foco, el ícono y el borde toman el color de la tienda y un anillo rosado suave rodea el campo (T-E7-47).
- La contraseña tiene un botón con un ojo dentro del campo ("Mostrar contraseña" para lectores de pantalla), que la muestra u oculta.
- El botón de enviar no se apaga mientras falten datos: si se envía con un campo vacío, no se consulta al servidor, el campo muestra debajo qué falta ("Escribí tu email.") y el cursor va al primero que falta.
- Los mensajes, los pasos y el ingreso con Google o Facebook no cambian; sus botones llevan delante el logo de cada uno ("G" de colores de Google, "f" azul de Facebook), como piden sus guías de marca (T-E7-47).

**Criterios de aceptación**
- Dado Ingresar sin email, cuando se toca "Ingresar", entonces no se envía nada, el email dice "Escribí tu email." y tiene el cursor.
- Dada una contraseña escrita, cuando se toca "Mostrar contraseña", entonces se lee; tocándolo otra vez, se oculta.
- Dado Ingresar, cuando se abre, entonces "Crear cuenta" lleva a la página de registro.
- Dado Recuperar contraseña sin email, o Elegir contraseña nueva sin contraseña, cuando se envía, entonces no se envía nada y el campo dice qué falta y tiene el cursor.
- Dada una computadora, cuando se abre Ingresar, Crear cuenta, Recuperar contraseña o Elegir contraseña nueva, entonces el cielo con el cartel y la chibi está a la izquierda, del mismo alto que la tarjeta, y la tarjeta a la derecha; en un celular, la chibi se asoma en una franja arriba de la tarjeta (T-E7-47).
- Dada cualquiera de esas páginas, cuando se abre, entonces el cartel "Pijama Party" está en el cielo, sin texto para lectores de pantalla y con su tamaño reservado, así nada se corre al cargar (T-E7-47).
- Dado Ingresar, cuando el cursor entra a la contraseña, entonces la chibi se tapa los ojos; al tocar "Mostrar contraseña", espía entre los dedos; al volver al email, lo mira (T-E7-47).
- Dado Ingresar con una contraseña incorrecta, cuando el servidor la rechaza, entonces la chibi mueve la cabeza que no una vez y queda triste hasta que se vuelve a escribir (T-E7-47).
- Dado Crear cuenta, cuando la cuenta se crea, entonces la chibi salta feliz con corazoncitos; dado Ingresar, cuando el ingreso funciona, entonces la página siguiente se abre sin esperar a la chibi (T-E7-47).
- Dado un visitante con movimiento reducido, cuando abre Ingresar y escribe, entonces la chibi no respira, no parpadea, no niega ni salta: solo cambia de gesto, sin animar (T-E7-47).
- Dado un lector de pantalla, cuando recorre Ingresar, entonces no lee nada de la chibi ni de su cielo (T-E7-47).
- Dadas Crear cuenta, Recuperar contraseña y Elegir contraseña nueva, cuando se abren, entonces la chibi saluda, tiene el "?" o sostiene la llave, respectivamente; en Ingresar, ninguna de esas cosas (T-E7-47).

## CU-32 Editar el banner de personajes

**Actor:** Administrador

**Flujo principal**
1. El administrador abre "Portada" en el backoffice.
2. Carga o cambia la foto, el título, el texto, el texto del enlace y su destino.
3. Ve una vista previa como en la portada y guarda.
4. Puede publicar u ocultar el banner.

**Alternativos**
- Datos inválidos: se marcan los campos con su error y no se guarda nada.
- Foto que no es una imagen o es demasiado grande: se rechaza con el mismo aviso que las fotos de producto (CU-06).

**Reglas**
- Hay un solo banner. Nace oculto y sin datos.
- Título de 1 a 60 caracteres; texto de 0 a 200; texto del enlace de 1 a 30.
- El destino es una dirección de la tienda (empieza con una sola `/`, por ejemplo `/categoria/kigurumi`), nunca otro sitio.
- Para publicarlo hacen falta la foto, el título, el texto del enlace y el destino.
- La foto se convierte como las de producto (CU-06): WebP en 400, 600, 800 y 1200 px y su versión borrosa. La foto anterior se borra al reemplazarla.
- La portada muestra el banner publicado en la próxima carga.

**Criterios de aceptación**
- Dado un banner con foto, título, texto del enlace y destino, cuando se publica, entonces la portada lo muestra.
- Dado un banner oculto, cuando se abre la portada, entonces no aparece la sección.
- Dado un banner sin foto, cuando se intenta publicar, entonces se rechaza y se indica que falta la foto.
- Dado un destino `https://otro-sitio.com` o `//otro-sitio.com`, cuando se guarda, entonces se rechaza.
- Dado un visitante, cuando pide editar el banner, entonces recibe un error y nada cambia.

## Cambios a CU-01 y CU-02 (grilla y filtros)

**Reglas nuevas**
- La tarjeta muestra los colores con stock del producto como círculos (D-04), hasta 5 y "+N" si hay más, y la etiqueta "Nuevo" (D-05).
- Los filtros se abren desde una barra de botones (Público, Talle, Color, Material, Temporada, Precio). Cada botón abre el panel de filtros de CU-02 en ese grupo; las reglas de CU-02 no cambian. Un botón con valores elegidos lo indica. Un grupo sin opciones no tiene botón, salvo que un enlace compartido haya elegido un valor en él.
- El listado muestra la cantidad de productos que coinciden (D-11), a la derecha de la barra de filtros, como en el prototipo. En el celular queda a la vista mientras los botones se desplazan.
- El visitante puede ver la grilla en cuadrícula o en lista. La elección no cambia la URL.
- En la computadora, al pasar el mouse sobre una tarjeta, se ve la segunda foto del producto, si tiene. En el celular y en la vista de lista se ve solo la primera, y la segunda no se descarga.

**Criterios de aceptación**
- Dado un producto con stock en rosa y sin stock en beige, cuando se lista, entonces su tarjeta muestra solo el círculo rosa.
- Dado un producto con dos fotos en la computadora, cuando se pasa el mouse sobre su tarjeta, entonces se ve la segunda foto; en el celular, no se descarga.
- Dado un producto creado hace 10 días y otro hace 40, cuando se listan, entonces solo el primero lleva "Nuevo".
- Dados 30 productos que coinciden con los filtros, cuando se abre el listado, entonces dice "30 productos".

## Cambios a CU-03 (ficha)

**Reglas nuevas**
- Migas de pan: Inicio / categoría / producto. Sin un enlace aparte "Volver al catálogo": las migas ya llevan de vuelta.
- En el celular (T-E7-51), las migas son píldoras pastel en una sola línea: Inicio es una casita en una píldora blanca, la categoría va en una píldora rosada y el producto en negrita, cortado con "…" si es largo; entre ellas, en lugar de "/", una estrellita rosa. Si no entran, la línea se desliza de costado y nunca se parte en renglones. Los lectores de pantalla siguen leyendo "Inicio", la categoría y el producto. En la computadora quedan como están.
- Galería: foto principal con miniaturas y una vista ampliada al tocar la foto. En la computadora las miniaturas van en una columna a la izquierda de la foto; en el celular, en una fila debajo.
- Color con círculos (D-04) y el nombre del color elegido; talle con botones cuadrados; selector de cantidad en píldora, de la misma altura que el botón de agregar (52 px); con una sola unidad disponible no se muestra y el botón ocupa todo el ancho. El botón de agregar lleva la bolsa del encabezado, "Agregar al carrito" y, con una variante elegida, el subtotal (T-E7-41).
- En el celular, cuando el botón de agregar sale de la pantalla, una barra fija abajo muestra el precio y el botón.
- Íconos de confianza (D-12), que reemplazan al resumen de envíos y pagos de RNF-31.6 debajo del botón para no repetirlo: "Envío gratis desde $X" (solo con umbral), los plazos de entrega y "Pagá con …" con los medios de pago habilitados (MercadoPago, MODO), con el enlace a preguntas frecuentes.
- El precio se destaca sobre la descripción: más grande y en negrita. El color elegido lleva un anillo grueso y el talle elegido se rellena con el color de la tienda.
- El costo de envío por provincia (D-07) lleva "Calculá el envío" arriba y la provincia debajo (T-E7-41), con el costo debajo, sin recuadro. Sigue siendo por provincia, no por código postal. Secciones desplegables: composición (material y temporada) y cambios y devoluciones (enlace a preguntas frecuentes). La guía de talles se abre desde "Talle" (T-E7-36).
- Orden de la columna de la información: nombre, precio, descripción, talle y color, cantidad y botón de agregar; después las secciones desplegables; al final el costo de envío por provincia y los íconos de confianza. En la computadora esa columna queda fija al bajar, así el botón de agregar sigue a la vista mientras se recorre la foto.
- "Completá tu look" (D-08), a todo el ancho debajo de la ficha y arriba de las opiniones (CU-24).
- Guía de talles (T-E7-36, RNF-31.5): si la categoría tiene guía, un enlace "Guía de talles" a la derecha de "Talle" la abre (la misma tabla y nota de `/guia-de-talles`) en una ventana que sube desde abajo; se cierra con Escape, tocando afuera o con su botón, y el foco vuelve al enlace. Sin guía no hay enlace.
- Talle o color sin elegir (T-E7-36): el botón de agregar sigue habilitado. Si se toca sin elegirlos, no agrega, dice qué falta debajo de cada grupo ("Elegí un talle", "Elegí un color") y lleva el foco al primer grupo que falta (primero el talle). Cada aviso se va al elegir en su grupo; el del talle no aparece si con el color elegido no hay ningún talle para elegir, porque el aviso de stock ya dice por qué. El botón de la barra fija hace lo mismo. Sigue deshabilitado mientras agrega y cuando nada de lo elegido tiene stock (el talle y el color elegidos, o un color agotado en todos los talles). Con un color agotado en todos los talles elegido y todavía sin talle, debajo de los talles dice "Sin stock en este color", para que se entienda por qué no se puede agregar.
- Un color sin stock en el talle elegido (T-E7-36) se tacha con una línea en diagonal además de verse más claro, para no depender solo del tono.

**Criterios de aceptación**
- Dada una ficha, cuando se elige cantidad 2 y se agrega, entonces el carrito suma 2 unidades de esa variante.
- Dada una cantidad mayor al stock disponible, cuando se agrega, entonces se muestra el aviso de stock de CU-08.
- Dada una ficha en el celular, cuando se baja hasta que el botón de agregar no se ve, entonces aparece la barra fija con el precio y el botón.
- Dada una provincia sin envío, cuando se elige en la ficha, entonces dice que no hay envío a esa provincia.
- Dada una categoría con otros 6 productos, cuando se abre una ficha, entonces "Completá tu look" muestra los 4 más nuevos sin el producto abierto.
- Dada una categoría sin otros productos, cuando se abre una ficha, entonces "Completá tu look" no aparece.
- Dada una categoría con guía de talles, cuando se toca "Guía de talles", entonces una ventana muestra la tabla y la nota; al cerrarla, el foco vuelve al enlace (T-E7-36).
- Dada una ficha sin talle ni color elegidos, cuando se toca el botón de agregar, entonces no se agrega nada, aparecen "Elegí un talle" y "Elegí un color" y el foco pasa a los talles; al elegir un talle, su aviso se va (T-E7-36).
- Dada una categoría sin guía de talles, cuando se abre una ficha, entonces no hay enlace "Guía de talles" (T-E7-36).
- Dado un color agotado en todos los talles, cuando se elige, entonces no se puede agregar y dice "Sin stock en este color" (T-E7-36).

## Cambios a CU-08 (panel del carrito)

**Reglas nuevas**
- Con umbral de envío gratis, una barra muestra cuánto falta ("Te faltan $X para envío gratis") o "¡Tenés envío gratis!". Se calcula sobre el subtotal con descuento que manda el servidor. El costo final lo sigue decidiendo el checkout (CU-12).
- Al agregar desde la ficha, un aviso breve dice "Agregado al carrito".
- Vacío, el panel muestra arriba la chibi de la cuenta dormida, abrazando una almohada (T-E7-48), sobre "Tu carrito está vacío.", y "Empezar a comprar" ocupa todo el ancho del panel. Debajo queda reservado el lugar de "Productos sugeridos", que llega en una tarea futura; mientras tanto no muestra nada.

**Criterios de aceptación**
- Dado un umbral de $50.000 y un subtotal con descuento de $24.999, cuando se abre el panel, entonces dice "Te faltan $25.001 para envío gratis".
- Dado un subtotal con descuento de $50.000 o más, cuando se abre el panel, entonces dice "¡Tenés envío gratis!".
- Sin umbral, el panel no muestra la barra.
- Dado un carrito vacío, cuando se abre el panel, entonces se ve la chibi dormida con su almohada (que los lectores de pantalla no leen), "Tu carrito está vacío." y "Empezar a comprar" a todo el ancho, que cierra el panel.

## Cambios a CU-13 (checkout)

**Reglas nuevas**
- Desde 640 px, los campos cortos van de a dos por fila (DNI y teléfono; ciudad y provincia; número, piso y departamento), como en el prototipo.
- Sin sesión, el paso 1 ofrece "¿Ya tenés una cuenta? Iniciar sesión", que vuelve al checkout después de ingresar.
- **"Nombre y apellido" pasa a ser dos campos, "Nombre" y "Apellido" (D-13),** cada uno de 1 a 50 caracteres, sin espacios al principio ni al final. El pedido guarda los dos por separado (R-04). Los demás campos, validaciones y pasos de CU-13 no cambian.
- Con sesión, el checkout se completa con el nombre de la cuenta: la primera palabra en "Nombre" y el resto en "Apellido", y el cliente puede corregirlos. La cuenta sigue con un solo nombre.
- El saludo del correo de pago confirmado y del arrepentimiento usa el "Nombre" tal cual se cargó (por ejemplo "María José"). El seguimiento sigue mostrando solo el nombre (CU-14: el enlace se puede reenviar), ahora el "Nombre" tal cual se cargó; el backoffice muestra "Nombre Apellido".
- Distribución (T-E7-38): en la computadora, el checkout va centrado y más angosto, con el formulario a la izquierda y el resumen en una columna a la derecha, alineada con los pasos y fija mientras se baja. En el celular, una sola columna con el resumen después del formulario.
- Pasos (T-E7-38): cada paso es un círculo con su número y su nombre ("Envío", "Pago", "Revisión"), unidos por una línea. El actual va relleno y en negrita; el completado muestra un tilde; el que falta va apagado.
- Resumen (T-E7-38): fotos más grandes; debajo del nombre, el color y el talle y, en otra línea, "Cantidad: 4"; el importe de la línea a la derecha. Debajo del título, "Volver al carrito" abre el carrito para cambiar cantidades o el cupón, sin perder lo cargado. El total va separado por una línea, más grande y en negrita; el envío gratis dice "Gratis".
- Botón final (T-E7-38): en la revisión, "Confirmar y pagar · $219.600" con el total del resumen, a todo el ancho del formulario, y "Volver" debajo. Debajo del botón, en letra chica, dónde se paga: describe el paso siguiente, no promete seguridad. Con MercadoPago la tarjeta se carga en la página del pedido, dentro de la tienda (T-E3-29), así que dice "Vas a ingresar los datos de tu tarjeta en el paso siguiente, sin salir de la tienda."; con MODO, que lleva a su propia página, "Vas a completar el pago en MODO." (T-E7-46). El botón no se deshabilita antes de aceptar los términos: si se toca, marca la casilla con su mensaje y lleva el foco ahí, para que el comprador sepa qué falta. Mientras confirma, dice "Confirmando…" y no se puede volver a tocar.
- Revisión (T-E7-45): "Contacto y envío" mantiene un solo "Editar", pero separa sus datos en "Contacto" (nombre, email, teléfono y DNI) y "Entrega" (la dirección, la ciudad con la provincia y el código postal, y "Correo Argentino a domicilio · $5.000" o "· Gratis"). "Medio de pago" muestra el nombre y, debajo, la descripción del paso 2 ("Tarjetas de crédito y débito"; T-E7-46 sacó "dinero en cuenta", que el formulario de tarjeta no ofrece). Sin logos de marcas ni sellos de seguridad.
- Resumen (T-E7-45): el importe de cada producto va en la línea de "Cantidad", a la derecha.
- Términos (T-E7-45): la casilla se mantiene (RNF-30.6) y toda la frase la marca al tocarla, salvo los enlaces.

**Criterios de aceptación**
- Dada la revisión con un total de $219.600 y MercadoPago elegido, cuando se ve, entonces el botón dice "Confirmar y pagar · $219.600" y debajo "Vas a ingresar los datos de tu tarjeta en el paso siguiente, sin salir de la tienda." (T-E7-38, T-E7-46); con MODO, "Vas a completar el pago en MODO." (T-E7-46).
- Dado el paso 2, cuando se leen los pasos, entonces "Envío" está completado y "Pago" es el actual (T-E7-38).
- Dada la revisión con un envío de $5.000, cuando se ve, entonces "Contacto y envío" separa "Contacto" y "Entrega", y "Entrega" dice "Correo Argentino a domicilio · $5.000"; con envío gratis, "· Gratis" (T-E7-45).
- Dado MercadoPago elegido, cuando se ve la revisión, entonces "Medio de pago" dice "MercadoPago" y "Tarjetas de crédito y débito" (T-E7-45, T-E7-46).
- Dada la revisión, cuando se toca la frase de los términos, entonces se marca la casilla (T-E7-45).
- Dado el checkout con datos cargados, cuando se toca "Volver al carrito", entonces se abre el carrito y, al cerrarlo, los datos siguen ahí (T-E7-38).
- Dado un invitado en el paso 1, cuando toca "Iniciar sesión" e ingresa, entonces vuelve al checkout con su carrito.
- Dado un comprador "María José" "Pérez", cuando paga, entonces el correo lo saluda "Hola, María José".
- Dado un "Nombre" o "Apellido" vacío o de más de 50 caracteres, cuando avanza del paso 1, entonces se marca el campo con su error; el servidor lo rechaza con `422`.
- Dado un cliente con la cuenta a nombre de "Ana María Gómez", cuando abre el checkout, entonces "Nombre" dice "Ana" y "Apellido" "María Gómez".
- Dado el checkout con un producto, cuando se mide con Lighthouse, entonces el "Cumulative Layout Shift" queda debajo de 0,1: mientras carga el carrito, el pie no aparece en la primera pantalla ni salta cuando llega el formulario.
- Dado un enlace de ingreso con una dirección de vuelta fuera de la tienda (otro dominio o `//host`), cuando el cliente ingresa, entonces va a `/`.

## Cambios a CU-14 (página del pedido)

Al volver del pago se abre la página del pedido, así que también es la confirmación de la compra (T-E7-44). No cambia lo que el servidor manda: la página no muestra el email, el teléfono, el DNI ni la calle, porque el enlace se puede reenviar.

**Reglas nuevas**
- Solo un pedido pagado (Pagado, En preparación, Enviado o Entregado) agradece la compra. Al volver del medio de pago no alcanza: el pago lo confirma el aviso del medio de pago, así que hasta entonces sigue "Estamos confirmando tu pago". Un pedido pendiente, cancelado o expirado se ve como hoy.
- Encabezado del pedido pagado: la chibi de la cuenta festejando (T-E7-48), con un sello verde suave con un tilde que se dibuja una vez a su costado (sin movimiento para quien lo pidió reducir), "¡Gracias por tu compra, Alan!" y una línea según el estado: "Recibimos tu pago y vamos a preparar tu pedido.", "Ya estamos preparando tu pedido.", "Tu pedido está en camino." o "Tu pedido fue entregado.".
- Número: "Pedido #1001" con un botón "Copiar" que copia "#1001" y dice "Copiado" por 2 segundos. Si el navegador no permite copiar, no hay botón.
- Avance del pedido: cuatro pasos en fila, "Pagado", "En preparación", "Enviado" y "Entregado", con los anteriores marcados con un tilde, el actual relleno y en negrita (con un pulso suave, salvo con movimiento reducido) y los que faltan apagados.
- Bloque "Entrega": "Correo Argentino a domicilio en {ciudad}, {provincia}" y, mientras está pagado o en preparación, el plazo de las preguntas frecuentes: de 1 a 3 días hábiles a Santa Fe y de 3 a 7 al resto, desde que se acreditó el pago. Después, "Te enviamos la confirmación y el enlace de este pedido por email." sin mostrar la dirección, y la corrección del email de CU-17.
- Con el WhatsApp de la tienda, "¿Tenés alguna duda sobre tu pedido? Escribinos por WhatsApp" abre el chat con el mensaje "¡Hola! Hice el pedido #1001 y quería consultar…" (solo el número del pedido, ningún dato del comprador).
- Al final, "Volver a la tienda" y, con el enlace de Instagram, "Seguinos en Instagram".
- No se agrega un sello de "pago seguro" ni un plazo distinto del de las preguntas frecuentes.
- Mientras se paga (T-E7-50), una chibi chica acompaña el mensaje, a su izquierda: mira hacia arriba con tres estrellitas que giran sobre su cabeza desde que se toca "Pagar" en el formulario de la tarjeta hasta que el servidor contesta (con el mensaje "Procesando tu pago…"), mientras el pago queda "en proceso" y en "Estamos confirmando tu pago" al volver de MODO; si la tarjeta se rechaza o el cobro falla, niega una vez y queda triste junto al mensaje que dice por qué, con el formulario a la vista para probar otra. Cuando el pago se aprueba, el pedido pasa a pagado y festeja la chibi del encabezado. Es decorativa: el mensaje es lo que se lee.

**Criterios de aceptación**
- Dado un pedido pagado de "alan", cuando se abre, entonces dice "¡Gracias por tu compra, Alan!", "Pedido #1001" y "Pagado" es el paso actual (T-E7-44).
- Dado un pedido pagado, cuando se abre, entonces la chibi festeja con el sello del tilde a su costado, y los lectores de pantalla no leen ninguno de los dos; un pedido pendiente, cancelado o expirado no tiene la chibi que festeja (T-E7-48).
- Dado un pedido pendiente con el formulario de la tarjeta, cuando se toca "Pagar" y mientras el servidor no contesta, entonces la chibi mira hacia arriba con sus estrellitas junto al mensaje; si el pago queda en proceso, sigue así (T-E7-50).
- Dada una tarjeta rechazada, cuando el servidor contesta, entonces la chibi queda triste junto al motivo del rechazo y el formulario sigue a la vista (T-E7-50).
- Dado un pedido pendiente al volver de un pago aprobado de MODO, cuando se abre, entonces la chibi mira hacia arriba con sus estrellitas junto a "Estamos confirmando tu pago" (T-E7-50).
- Dado un pedido pendiente sin pago en curso, cuando se abre, entonces no hay chibi (T-E7-50).
- Dado un pedido pendiente al volver de un pago aprobado, cuando se abre, entonces no agradece y dice que se está confirmando el pago; cuando pasa a pagado, agradece (T-E7-44).
- Dado un pedido enviado, cuando se leen los pasos, entonces "Pagado" y "En preparación" están completados y "Enviado" es el actual (T-E7-44).
- Dado un pedido pagado a Santa Fe, cuando se abre, entonces el plazo es de 1 a 3 días hábiles; a Córdoba, de 3 a 7; enviado, sin plazo (T-E7-44).
- Dado "Copiar", cuando se toca, entonces "#1001" queda copiado y el botón dice "Copiado" (T-E7-44).
- Dado el WhatsApp de la tienda, cuando se abre el pedido, entonces el enlace lleva el número del pedido en el mensaje; sin WhatsApp, no hay enlace (T-E7-44).
- Dado cualquier pedido, cuando se abre, entonces no se ve el email, el teléfono, el DNI ni la calle (T-E7-44).
