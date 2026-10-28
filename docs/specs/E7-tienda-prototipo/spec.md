# E7 – La tienda como el prototipo

**Estado:** Borrador · **Casos de uso:** CU-30, CU-31 y CU-32, y cambios a CU-01, CU-02, CU-03, CU-08, CU-13 y CU-19

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
| D-15 | Materiales del negocio | Pendiente del negocio | El negocio entrega el logo, las fotos del carrusel (hasta 3), las 3 de "Elegí para quién", la primera foto del banner de personajes (después la cambia desde el backoffice), el email y el WhatsApp de contacto y los enlaces de Instagram y TikTok. Sin ellas, cada lugar muestra el fondo degradado del prototipo. Recibido el 2026-10-23: el logo. El rótulo recortado va en el encabezado (fondo transparente, sin los pedacitos de personajes de las esquinas, 48 px de alto); la ilustración completa es la primera foto del banner de personajes y se sube desde "Portada". |
| D-16 | Animaciones | Decidido | **Con la librería Motion** (`motion`, para React), cargada de forma diferida para no demorar la primera carga, sobre los tokens de la sección "Animaciones". |
| D-17 | Tipografía | Decidido | **Inter** en lugar de Quicksand, en títulos y textos, en la tienda y el backoffice (el dueño, 2026-10-24). Alojada en la tienda, nunca desde Google Fonts. |

## Animaciones

El prototipo se siente amable porque responde a lo que hace el comprador: el carrito "salta" al agregar, los paneles se deslizan, los avisos suben. La tienda adopta ese mismo lenguaje como un sistema, no efecto por efecto.

**Principios**
- **El movimiento responde a una acción** (agregar, abrir, elegir, avanzar de paso) y muestra qué cambió. Nada se mueve solo, salvo el carrusel (D-14).
- **Entradas:** el texto de bienvenida de la portada aparece una vez al cargar; las tarjetas de producto aparecen cuando se suman a la grilla (la primera tanda, "Ver más", un filtro u orden nuevo), nunca por bajar hasta ellas; al cambiar de página, el contenido nuevo entra, pero no en la primera carga de la tienda. Las secciones no aparecen de a una al bajar.
- **Rápido y suave:** entre 120 y 450 ms (la entrada de la portada, hasta 700 ms; la espera de los botones de agregar y pagar, al menos 600 ms), con curvas que frenan al llegar.
- **Accesible:** con `prefers-reduced-motion` no hay desplazamientos ni escalas, solo cambios de opacidad cortos; los efectos de hover solo con `@media (hover: hover)`.
- **Con Motion, sin demorar la carga (D-16):** las animaciones de entrada y salida, los pasos y los rebotes usan la librería Motion, que se carga después de mostrar la página; hasta entonces, todo se ve en su lugar sin animar. Los efectos simples (hover, colores) siguen en CSS. Solo se anima `transform` y `opacity`, nunca el tamaño o la posición. La foto principal de la portada no se anima, para no demorar el LCP (RNF-11).

**Catálogo de animaciones**

| Dónde | Qué se mueve |
|---|---|
| Portada | El título, el texto y "Descubrí la colección" suben y aparecen en cascada una vez; las fotos del carrusel cambian con un fundido. |
| Tarjeta de producto | En computadora, la foto se acerca apenas al pasar el mouse; si el producto tiene una segunda foto, aparece con un fundido. |
| Encabezado (celular) | Al bajar por la página se esconde hacia arriba; al subir, vuelve. |
| Círculos de color y talles | El elegido se marca con un anillo que crece. |
| Agregar al carrito y "Confirmar y pagar": esperando | Un arcoíris pastel llena todo el botón de izquierda a derecha a saltitos, como una carga real (un empujón, una pausa, otro empujón), y sigue avanzando despacio si el servidor tarda, con un brillo que lo recorre. Una chibi en pijama y gorro de dormir (dibujo propio) corre sobre el borde del botón en la punta de la barra. La espera dura al menos 0,6 s aunque el servidor conteste antes, para que la barra se vea. Con movimiento reducido, el arcoíris solo aparece con un fundido, sin brillo ni chibi. |
| Agregar al carrito y "Confirmar y pagar": listo | La barra se completa y la chibi salta al final. En agregar, además salen unos destellos del botón y muestra "Agregado" con un tilde un momento; el ícono del carrito da un salto y su número se actualiza; el aviso aparece debajo del título del carrito, a todo el ancho del panel, corriendo los productos hacia abajo sin taparlos. En pagar, la barra queda llena mientras se abre el pago. Con movimiento reducido, sin destellos. |
| Agregar al carrito y "Confirmar y pagar": rechazado o con error | La barra se pone rojo suave y se vacía hacia la izquierda y la chibi desaparece; el mensaje junto al botón dice por qué. |
| Panel del carrito, menú, filtros, vista ampliada | Se deslizan desde su borde con el fondo oscureciéndose; al cerrar, el camino inverso. Lo que sube desde abajo (el menú del celular, la vista ampliada) se abre más despacio y frena suave. |
| Enlaces del encabezado, lupa, ícono de usuario y carrito (con mouse) | Al pasar el mouse, un arcoíris pastel llena el fondo desde abajo hacia arriba; al salir, baja. Con movimiento reducido, solo aparece con un fundido. |
| Contenido del menú del celular | Cada enlace sube un poco y aparece, uno tras otro, mientras sube el menú. |
| Tarjetas que se suman a la grilla | Suben un poco y aparecen, una tras otra; las que ya estaban no se mueven. |
| Barra de envío gratis | Se llena con suavidad; al llegar al umbral, el mensaje "¡Tenés envío gratis!" aparece con un pequeño rebote. |
| Secciones desplegables de la ficha | Se abren y cierran deslizando su altura. |
| Pasos del checkout | El contenido del paso nuevo sube y aparece. |
| Cargando | Las tarjetas y textos en espera laten suavemente (esqueletos). |
| Cambio de página | El contenido nuevo sube un poco y aparece con un fundido; no en la primera carga. |

**Criterios de aceptación**
- Dado un visitante con movimiento reducido, cuando agrega un producto, abre el carrito o cambia de paso, entonces nada se desplaza ni cambia de escala.
- Dada la grilla con productos, cuando "Ver más" suma otros, entonces solo los nuevos entran; con movimiento reducido, solo aparecen con un fundido.
- Dado el menú del celular, cuando se abre, entonces sube desde abajo con el botón de cerrar arriba y los enlaces entran uno tras otro.
- Dado un producto agregado desde la ficha, cuando la respuesta llega, entonces el ícono del carrito salta una vez y el aviso aparece.
- Dado un agregado desde la ficha, cuando el servidor contesta antes de 0,6 s, entonces el botón sigue en "Agregando…" con la barra de arcoíris hasta cumplir 0,6 s, y recién ahí dice "Agregado".
- Dado un agregado exitoso, cuando la barra se completa, entonces salen destellos del botón una vez; con movimiento reducido, no. Dado un agregado rechazado o con error, entonces la barra se pone rojo suave y se vacía, sin destellos.
- Dado "Confirmar y pagar", cuando espera la respuesta, entonces el botón muestra la barra de arcoíris con la chibi corriendo encima; un rechazo la vacía en rojo suave.
- Dada la portada, cuando se mide con Lighthouse, entonces el LCP sigue debajo de 3 s y el "Cumulative Layout Shift" debajo de 0,1.
- Dado un teléfono, cuando se toca una tarjeta, entonces no queda trabado ningún efecto de hover.

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
2. En el celular, la navegación, "Ingresar" y "Crear cuenta" van en un menú que se abre con un botón: una hoja con bordes redondeados que sube desde abajo, con un botón redondo blanco para cerrar centrado arriba de ella; los enlaces en letra grande y mayúscula, después Instagram y TikTok y, tras una línea, la cuenta. En la tablet, el mismo menú sale desde la izquierda.
3. Sobre el encabezado, una franja dice "Envío gratis desde $X" cuando hay umbral de envío gratis.
4. El pie es una franja rosa clara que ocupa todo el ancho de la pantalla, con el contenido dentro del ancho de la página; no repite el nombre de la tienda en letras grandes. Empieza con el bloque de la tienda: "Pijamas para toda la familia" y, más abajo, Redes sociales (Instagram y TikTok como íconos en círculos, en fila; al pasar el mouse o con el foco del teclado, el círculo se llena con un degradé rosa y el ícono se vuelve blanco). Después, cuatro columnas: Tienda (los mismos enlaces de la navegación del encabezado), Ayuda y legales (preguntas frecuentes, Cómo comprar, Guía de talles, Cambios y devoluciones, Términos y condiciones y el botón de arrepentimiento), Contacto (el número de WhatsApp, que abre el chat, el email, que abre un mensaje nuevo, el horario de atención y la dirección, que abre Google Maps, cada uno con su ícono) y Medios de pago (un ícono por cada medio habilitado, en fila, y debajo "Envíos" con el ícono de Correo Argentino). Los títulos de las columnas van en el color de acento. En el celular y la tablet, el bloque de la tienda arriba y las columnas de a dos (en el celular, Medios de pago y Envíos lado a lado, con los títulos a la misma altura); en la computadora, todo en una fila, con una línea vertical después del bloque. Debajo, separada por una línea, una franja con "Cookies" y el año con el nombre de la tienda.
5. Con el número de WhatsApp, todas las páginas públicas salvo el checkout y la cuenta muestran un botón redondo de WhatsApp fijo abajo a la derecha, "Escribinos por WhatsApp", que abre el chat en otra pestaña. En la ficha, en el celular, sube por encima de la barra fija de "Agregar al carrito" mientras esa barra se ve. Nunca tapa lo que tiene el foco del teclado ni el final del pie.
6. Solo en la portada (con o sin filtros), justo arriba del pie, una barra de beneficios con tres bloques iguales, cada uno con un ícono a la izquierda y, a su derecha, un título en negrita y mayúsculas y un texto: "Enviamos tu compra" (camión; "Entregas dentro de Santa Fe Capital y todo el país"), "Todas las formas de pago" (tarjeta; "Pagá con" y los medios habilitados, por ejemplo "Pagá con MercadoPago o MODO") y "Comprá con seguridad" (candado; "Tus datos siempre protegidos"). En la computadora, los bloques van en fila separados por líneas verticales suaves; en el celular, uno debajo de otro, sin líneas.

**Reglas**
- Cada enlace de la navegación abre el listado filtrado: Mujer, Hombre y Niños por público; Temporada Invierno por temporada. En la portada, bajan hasta "Todos los productos".
- Con sesión, "Mi cuenta" abre una lista con el saludo, "Mis pedidos", "Mis direcciones", "Mi cuenta" y "Salir". En el menú del celular, esos enlaces se ven directamente.
- La franja no muestra cuotas ni descuentos (D-01, D-02). Sin umbral, no hay franja.
- Los enlaces del pie a páginas que todavía no existen (T-E6-06) no se muestran hasta que existan.
- Un dato de contacto o una red social que el negocio todavía no pasó (D-15) no se muestra. Sin email, WhatsApp, horario ni dirección, no hay columna Contacto; Redes sociales muestra siempre Instagram y TikTok: la red que el negocio todavía no pasó aparece como marcador (el ícono apagado, en un círculo con borde punteado, sin enlace), para completarla en el futuro. En el menú del celular solo aparecen las que tienen enlace. Sin medios de pago habilitados, la columna Medios de pago muestra solo "Envíos".
- La barra de beneficios no promete medios de pago, cuotas, descuentos ni recargos que el checkout no tiene (D-01, D-02): el bloque de pagos nombra solo los medios habilitados, y sin ellos (todavía no se saben o no se pudieron leer) ese bloque no se muestra.
- Los íconos de medios de pago son dibujos propios de la tienda (una tarjeta para MercadoPago, un celular para MODO), no los logos de las marcas, con el nombre del medio como texto para lectores de pantalla.
- Los datos del vendedor de RNF-30.2 se suman al pie con T-E6-21.
- En el celular, el encabezado queda fijo arriba, pero se esconde mientras el visitante baja por la página y vuelve apenas sube, para dejar más lugar a los productos. No se esconde cerca del principio de la página, con el menú, la búsqueda, el carrito o "Mi cuenta" abiertos, ni con el foco del teclado dentro de él. En la computadora no se esconde nunca.
- Una vez que la página bajó, el fondo del encabezado se vuelve un poco transparente y difuminado, así se ve lo que pasa por detrás sin perder la lectura del logo y los íconos. Arriba de todo, el fondo es el de siempre.
- En la computadora, una vez que la página bajó, el encabezado se convierte en una píldora flotante: mantiene el logo, la navegación, la búsqueda, la cuenta y el carrito, un poco más bajo y más angosto que la página, con los bordes completamente redondeados, separado unos milímetros del borde de arriba, con el mismo fondo transparente y difuminado y una sombra suave. Al volver arriba, vuelve a ser la barra de todo el ancho. El cambio es una animación suave (sin animación para quien pidió reducir el movimiento) y la página no salta. En el celular no cambia.

**Criterios de aceptación**
- Dado un umbral de envío gratis de $50.000, cuando se abre cualquier página pública, entonces la franja dice "Envío gratis desde $50.000"; sin umbral, no hay franja.
- Dado el checkout o la página del pedido, cuando se ve el encabezado, entonces tiene el logo, el usuario y el carrito, pero no la navegación, el menú ni la búsqueda.
- Dado un celular, cuando el visitante baja por la página, entonces el encabezado se esconde; cuando sube, vuelve. En la computadora siempre se ve.
- Dada una página bajada, cuando se ve el encabezado, entonces su fondo es un poco transparente; al volver arriba, es el de siempre.
- Dada una página bajada en la computadora, cuando se ve el encabezado, entonces es una píldora flotante con el logo, la navegación y los íconos; al volver arriba, es la barra de todo el ancho.
- Dado un celular, cuando se abre el menú, entonces muestra la navegación y el ingreso, se navega con teclado, y Escape lo cierra y devuelve el foco al botón.
- Dado el enlace "Temporada Invierno", cuando se toca, entonces la grilla muestra solo productos de invierno.
- Dado el pie, cuando se revisa, entonces el botón de arrepentimiento sigue visible (RNF-30.1).
- Dados MercadoPago y MODO habilitados, cuando se revisa el pie, entonces Medios de pago muestra sus dos íconos en fila, cada uno con su nombre para lectores de pantalla.
- Dados el email y el WhatsApp de contacto, cuando se revisa el pie, entonces Contacto muestra el número, que abre el chat de WhatsApp, y el email, que abre un mensaje nuevo; sin ninguno, no hay columna Contacto.
- Dados Instagram y TikTok, cuando se revisa el pie, entonces Redes sociales muestra sus dos íconos, que abren cada red en otra pestaña; sin enlace, la red aparece como marcador sin enlace.
- Dada cualquier página pública, cuando se revisa el pie, entonces dice "Pijamas para toda la familia", y Tienda tiene los cinco enlaces de la navegación del encabezado.
- Dados el horario y la dirección, cuando se revisa el pie, entonces Contacto muestra el horario y la dirección, que abre Google Maps en otra pestaña; sin ellos, no aparecen.
- Dada cualquier página pública, cuando se revisa el pie, entonces Medios de pago muestra "Envíos" con el ícono de Correo Argentino, aunque los medios de pago no se puedan leer.
- Dada cualquier página pública, cuando se revisa el pie, entonces Ayuda y legales lleva a Preguntas frecuentes, Cómo comprar, Guía de talles, Cambios y devoluciones, y Términos y condiciones.
- Dadas guías de talles cargadas en el backoffice, cuando se abre Guía de talles, entonces muestra la de cada categoría activa que tenga una, en el orden de las categorías; sin guías, avisa que todavía no hay.
- Dadas las páginas Cómo comprar, Cambios y devoluciones, y Términos y condiciones, cuando se abren, entonces muestran sus textos; Cambios y devoluciones lleva al botón de arrepentimiento, y los términos llevan a las dos.
- Dado el número de WhatsApp, cuando se abre la portada, una ficha o las preguntas frecuentes, entonces el botón flotante abre el chat en otra pestaña; en el checkout y en la cuenta no aparece; sin número, no aparece en ninguna página.
- Dados MercadoPago y MODO habilitados, cuando se abre la portada, entonces arriba del pie se ven "Enviamos tu compra", "Todas las formas de pago" con "Pagá con MercadoPago o MODO" y "Comprá con seguridad"; si los medios de pago no se pueden leer, se ven solo el primero y el último. En cualquier otra página, como "Ingresar", no hay barra de beneficios.
- Dado un visitante en la computadora, cuando se ve el encabezado, entonces el ícono de usuario lleva a "Ingresar" y no hay "Crear cuenta".

## Cambios a CU-19 (ingresar y crear cuenta)

**Reglas nuevas**
- Ingresar, Crear cuenta, Recuperar contraseña y Elegir contraseña nueva van en una tarjeta blanca centrada, con el título y una frase de introducción adentro. Ingresar dice "Ingresá para ver tus pedidos y comprar más rápido."; Elegir contraseña nueva, "Elegí la contraseña con la que vas a ingresar de ahora en más.".
- Recuperar contraseña termina con "¿Te acordaste de tu contraseña?" y "Volver a ingresar"; la contraseña nueva también tiene el ojo.
- En Ingresar, "¿Olvidaste tu contraseña?" va a la derecha de "Contraseña". Abajo, separado por una línea, "¿Primera vez acá?" y un botón "Crear cuenta" a todo el ancho. Crear cuenta termina igual, con "¿Ya tenés cuenta?" e "Ingresar".
- La contraseña tiene un botón con un ojo dentro del campo ("Mostrar contraseña" para lectores de pantalla), que la muestra u oculta.
- El botón de enviar no se apaga mientras falten datos: si se envía con un campo vacío, no se consulta al servidor, el campo muestra debajo qué falta ("Escribí tu email.") y el cursor va al primero que falta.
- Los mensajes, los pasos y el ingreso con Google o Facebook no cambian.

**Criterios de aceptación**
- Dado Ingresar sin email, cuando se toca "Ingresar", entonces no se envía nada, el email dice "Escribí tu email." y tiene el cursor.
- Dada una contraseña escrita, cuando se toca "Mostrar contraseña", entonces se lee; tocándolo otra vez, se oculta.
- Dado Ingresar, cuando se abre, entonces "Crear cuenta" lleva a la página de registro.
- Dado Recuperar contraseña sin email, o Elegir contraseña nueva sin contraseña, cuando se envía, entonces no se envía nada y el campo dice qué falta y tiene el cursor.

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
- La foto se convierte como las de producto (CU-06): WebP en 400, 800 y 1200 px y su versión borrosa. La foto anterior se borra al reemplazarla.
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
- Galería: foto principal con miniaturas y una vista ampliada al tocar la foto. En la computadora las miniaturas van en una columna a la izquierda de la foto; en el celular, en una fila debajo.
- Color con círculos (D-04) y el nombre del color elegido; talle con botones cuadrados; selector de cantidad.
- En el celular, cuando el botón de agregar sale de la pantalla, una barra fija abajo muestra el precio y el botón.
- Íconos de confianza (D-12), que reemplazan al resumen de envíos y pagos de RNF-31.6 debajo del botón para no repetirlo: "Envío gratis desde $X" (solo con umbral), los plazos de entrega y "Pagá con …" con los medios de pago habilitados (MercadoPago, MODO), con el enlace a preguntas frecuentes.
- El precio se destaca sobre la descripción: más grande y en negrita. El color elegido lleva un anillo grueso y el talle elegido se rellena con el color de la tienda.
- El costo de envío por provincia (D-07) va en una sola fila ("Calculá el envío" y la provincia), con el costo debajo, sin recuadro. Sigue siendo por provincia, no por código postal. Secciones desplegables: guía de talles (RNF-31.5), composición (material y temporada) y cambios y devoluciones (enlace a preguntas frecuentes).
- Orden de la columna de la información: nombre, precio, descripción, talle y color, cantidad y botón de agregar; después las secciones desplegables; al final el costo de envío por provincia y los íconos de confianza. En la computadora esa columna queda fija al bajar, así el botón de agregar sigue a la vista mientras se recorre la foto.
- "Completá tu look" (D-08), a todo el ancho debajo de la ficha y arriba de las opiniones (CU-24).

**Criterios de aceptación**
- Dada una ficha, cuando se elige cantidad 2 y se agrega, entonces el carrito suma 2 unidades de esa variante.
- Dada una cantidad mayor al stock disponible, cuando se agrega, entonces se muestra el aviso de stock de CU-08.
- Dada una ficha en el celular, cuando se baja hasta que el botón de agregar no se ve, entonces aparece la barra fija con el precio y el botón.
- Dada una provincia sin envío, cuando se elige en la ficha, entonces dice que no hay envío a esa provincia.
- Dada una categoría con otros 6 productos, cuando se abre una ficha, entonces "Completá tu look" muestra los 4 más nuevos sin el producto abierto.
- Dada una categoría sin otros productos, cuando se abre una ficha, entonces "Completá tu look" no aparece.

## Cambios a CU-08 (panel del carrito)

**Reglas nuevas**
- Con umbral de envío gratis, una barra muestra cuánto falta ("Te faltan $X para envío gratis") o "¡Tenés envío gratis!". Se calcula sobre el subtotal con descuento que manda el servidor. El costo final lo sigue decidiendo el checkout (CU-12).
- Al agregar desde la ficha, un aviso breve dice "Agregado al carrito".
- Vacío, el panel muestra arriba un ícono de bolsa de compras sobre "Tu carrito está vacío.", y "Empezar a comprar" ocupa todo el ancho del panel. Debajo queda reservado el lugar de "Productos sugeridos", que llega en una tarea futura; mientras tanto no muestra nada.

**Criterios de aceptación**
- Dado un umbral de $50.000 y un subtotal con descuento de $24.999, cuando se abre el panel, entonces dice "Te faltan $25.001 para envío gratis".
- Dado un subtotal con descuento de $50.000 o más, cuando se abre el panel, entonces dice "¡Tenés envío gratis!".
- Sin umbral, el panel no muestra la barra.
- Dado un carrito vacío, cuando se abre el panel, entonces se ve el ícono, "Tu carrito está vacío." y "Empezar a comprar" a todo el ancho, que cierra el panel.

## Cambios a CU-13 (checkout)

**Reglas nuevas**
- Desde 640 px, los campos cortos van de a dos por fila (DNI y teléfono; ciudad y provincia; número, piso y departamento), como en el prototipo.
- Sin sesión, el paso 1 ofrece "¿Ya tenés una cuenta? Iniciar sesión", que vuelve al checkout después de ingresar.
- **"Nombre y apellido" pasa a ser dos campos, "Nombre" y "Apellido" (D-13),** cada uno de 1 a 50 caracteres, sin espacios al principio ni al final. El pedido guarda los dos por separado (R-04). Los demás campos, validaciones y pasos de CU-13 no cambian.
- Con sesión, el checkout se completa con el nombre de la cuenta: la primera palabra en "Nombre" y el resto en "Apellido", y el cliente puede corregirlos. La cuenta sigue con un solo nombre.
- El saludo del correo de pago confirmado y del arrepentimiento usa el "Nombre" tal cual se cargó (por ejemplo "María José"). El seguimiento sigue mostrando solo el nombre (CU-14: el enlace se puede reenviar), ahora el "Nombre" tal cual se cargó; el backoffice muestra "Nombre Apellido".

**Criterios de aceptación**
- Dado un invitado en el paso 1, cuando toca "Iniciar sesión" e ingresa, entonces vuelve al checkout con su carrito.
- Dado un comprador "María José" "Pérez", cuando paga, entonces el correo lo saluda "Hola, María José".
- Dado un "Nombre" o "Apellido" vacío o de más de 50 caracteres, cuando avanza del paso 1, entonces se marca el campo con su error; el servidor lo rechaza con `422`.
- Dado un cliente con la cuenta a nombre de "Ana María Gómez", cuando abre el checkout, entonces "Nombre" dice "Ana" y "Apellido" "María Gómez".
- Dado el checkout con un producto, cuando se mide con Lighthouse, entonces el "Cumulative Layout Shift" queda debajo de 0,1: mientras carga el carrito, el pie no aparece en la primera pantalla ni salta cuando llega el formulario.
- Dado un enlace de ingreso con una dirección de vuelta fuera de la tienda (otro dominio o `//host`), cuando el cliente ingresa, entonces va a `/`.
