# E7 – La tienda como el prototipo

**Estado:** Borrador · **Casos de uso:** CU-30, CU-31 y CU-32, y cambios a CU-01, CU-02, CU-03, CU-08 y CU-13

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
| D-14 | Carrusel de la portada | Decidido | Hasta 3 imágenes. **Pasan solas cada 6 segundos, con botón de pausa** (WCAG 2.2.2) y sin movimiento con `prefers-reduced-motion`. |
| D-15 | Materiales del negocio | Pendiente del negocio | El negocio entrega el logo, las fotos del carrusel (hasta 3), las 3 de "Elegí para quién", la primera foto del banner de personajes (después la cambia desde el backoffice), el email y el WhatsApp de contacto y los enlaces de Instagram y TikTok. Sin ellas, cada lugar muestra el fondo degradado del prototipo. Recibido el 2026-10-23: el logo. El rótulo recortado va en el encabezado (fondo transparente, sin los pedacitos de personajes de las esquinas, 48 px de alto); la ilustración completa es la primera foto del banner de personajes y se sube desde "Portada". |
| D-16 | Animaciones | Decidido | **Con la librería Motion** (`motion`, para React), cargada de forma diferida para no demorar la primera carga, sobre los tokens de la sección "Animaciones". |

## Animaciones

El prototipo se siente amable porque responde a lo que hace el comprador: el carrito "salta" al agregar, los paneles se deslizan, los avisos suben. La tienda adopta ese mismo lenguaje como un sistema, no efecto por efecto.

**Principios**
- **El movimiento responde a una acción** (agregar, abrir, elegir, avanzar de paso) y muestra qué cambió. Nada se mueve solo, salvo el carrusel (D-14).
- **Un solo momento de entrada:** el texto de bienvenida de la portada aparece una vez al cargar. Las secciones no aparecen de a una al bajar.
- **Rápido y suave:** entre 120 y 450 ms (la entrada de la portada, hasta 700 ms), con curvas que frenan al llegar.
- **Accesible:** con `prefers-reduced-motion` no hay desplazamientos ni escalas, solo cambios de opacidad cortos; los efectos de hover solo con `@media (hover: hover)`.
- **Con Motion, sin demorar la carga (D-16):** las animaciones de entrada y salida, los pasos y los rebotes usan la librería Motion, que se carga después de mostrar la página; hasta entonces, todo se ve en su lugar sin animar. Los efectos simples (hover, colores) siguen en CSS. Solo se anima `transform` y `opacity`, nunca el tamaño o la posición. La foto principal de la portada no se anima, para no demorar el LCP (RNF-11).

**Catálogo de animaciones**

| Dónde | Qué se mueve |
|---|---|
| Portada | El título, el texto y "Descubrí la colección" suben y aparecen en cascada una vez; las fotos del carrusel cambian con un fundido. |
| Tarjeta de producto | En computadora, la foto se acerca apenas al pasar el mouse; si el producto tiene una segunda foto, aparece con un fundido. |
| Círculos de color y talles | El elegido se marca con un anillo que crece. |
| Agregar al carrito | El botón muestra "Agregado" con un tilde un momento; el ícono del carrito da un salto y su número se actualiza; aparece el aviso desde abajo. |
| Panel del carrito, menú, filtros, vista ampliada | Se deslizan desde su borde con el fondo oscureciéndose; al cerrar, el camino inverso. |
| Barra de envío gratis | Se llena con suavidad; al llegar al umbral, el mensaje "¡Tenés envío gratis!" aparece con un pequeño rebote. |
| Secciones desplegables de la ficha | Se abren y cierran deslizando su altura. |
| Pasos del checkout | El contenido del paso nuevo sube y aparece. |
| Cargando | Las tarjetas y textos en espera laten suavemente (esqueletos). |
| Cambio de página | El contenido nuevo aparece con un fundido corto. |

**Criterios de aceptación**
- Dado un visitante con movimiento reducido, cuando agrega un producto, abre el carrito o cambia de paso, entonces nada se desplaza ni cambia de escala.
- Dado un producto agregado desde la ficha, cuando la respuesta llega, entonces el ícono del carrito salta una vez y el aviso aparece.
- Dada la portada, cuando se mide con Lighthouse, entonces el LCP sigue debajo de 3 s y el "Cumulative Layout Shift" debajo de 0,1.
- Dado un teléfono, cuando se toca una tarjeta, entonces no queda trabado ningún efecto de hover.

## CU-30 Portada

**Actor:** Visitante

**Flujo principal**
1. El visitante entra a `/`.
2. El sistema muestra, en orden: el carrusel de bienvenida, el banner de personajes, "Elegí para quién", "Todos los productos" (la grilla de CU-01 con los filtros de CU-02), el bloque del botón de arrepentimiento y "Contactanos".
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
- Dada la portada en un celular, cuando carga, entonces se ven el carrusel, el banner de personajes, "Elegí para quién", la grilla, el botón de arrepentimiento y el contacto, en ese orden.
- Dada la portada, cuando se toca "Mujer" en "Elegí para quién", entonces se ve la grilla filtrada por público mujer y la URL tiene `publico=mujer`.
- Dado un visitante con movimiento reducido, cuando abre la portada, entonces el carrusel no pasa solo.
- Dado el carrusel pasando solo, cuando se toca pausa, entonces se detiene.
- Dados inicio, listado, ficha y checkout, cuando se miden con Lighthouse, entonces siguen cumpliendo RNF-11 y RNF-31.4.

## CU-31 Encabezado, menú y pie

**Actor:** Visitante

**Flujo principal**
1. En todas las páginas públicas, el encabezado muestra el logo, la navegación (Todo, Mujer, Hombre, Niños, Temporada Invierno), la búsqueda, "Ingresar" y "Crear cuenta" (o "Mi cuenta" con sesión) y el carrito.
2. En el celular, la navegación, "Ingresar" y "Crear cuenta" van en un menú que se abre con un botón.
3. Sobre el encabezado, una franja dice "Envío gratis desde $X" cuando hay umbral de envío gratis.
4. El pie tiene tres columnas: Ayuda (preguntas frecuentes, cambios y devoluciones, términos), Redes (Instagram, TikTok) y Medios de pago (los habilitados); debajo, el botón de arrepentimiento y el año con el nombre de la tienda.

**Reglas**
- Cada enlace de la navegación abre el listado filtrado: Mujer, Hombre y Niños por público; Temporada Invierno por temporada. En la portada, bajan hasta "Todos los productos".
- Con sesión, "Mi cuenta" abre una lista con el saludo, "Mis pedidos", "Mis direcciones", "Mi cuenta" y "Salir". En el menú del celular, esos enlaces se ven directamente.
- La franja no muestra cuotas ni descuentos (D-01, D-02). Sin umbral, no hay franja.
- Los enlaces del pie a páginas que todavía no existen (T-E6-06) no se muestran hasta que existan.
- Una red social que el negocio todavía no pasó (D-15) no se muestra; sin ninguna, no hay columna Redes. Sin medios de pago habilitados, no hay columna Medios de pago.
- Los datos del vendedor de RNF-30.2 se suman al pie con T-E6-21.

**Criterios de aceptación**
- Dado un umbral de envío gratis de $50.000, cuando se abre cualquier página pública, entonces la franja dice "Envío gratis desde $50.000"; sin umbral, no hay franja.
- Dado un celular, cuando se abre el menú, entonces muestra la navegación y el ingreso, se navega con teclado, y Escape lo cierra y devuelve el foco al botón.
- Dado el enlace "Temporada Invierno", cuando se toca, entonces la grilla muestra solo productos de invierno.
- Dado el pie, cuando se revisa, entonces el botón de arrepentimiento sigue visible (RNF-30.1).

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
- Migas de pan: Inicio / categoría / producto, y "Volver al catálogo".
- Galería: foto principal con miniaturas y una vista ampliada al tocar la foto.
- Color con círculos (D-04) y el nombre del color elegido; talle con botones cuadrados; selector de cantidad.
- En el celular, cuando el botón de agregar sale de la pantalla, una barra fija abajo muestra el precio y el botón.
- Íconos de confianza (D-12), que reemplazan al resumen de envíos y pagos de RNF-31.6 debajo del botón para no repetirlo: "Envío gratis desde $X" (solo con umbral), los plazos de entrega y "Pagá con …" con los medios de pago habilitados (MercadoPago, MODO), con el enlace a preguntas frecuentes.
- El costo de envío por provincia (D-07) y secciones desplegables: guía de talles (RNF-31.5), composición (material y temporada) y cambios y devoluciones (enlace a preguntas frecuentes).
- "Completá tu look" (D-08), en la columna de la información, debajo de las secciones desplegables, como en el prototipo. Las opiniones (CU-24) se mantienen debajo.

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

**Criterios de aceptación**
- Dado un umbral de $50.000 y un subtotal con descuento de $24.999, cuando se abre el panel, entonces dice "Te faltan $25.001 para envío gratis".
- Dado un subtotal con descuento de $50.000 o más, cuando se abre el panel, entonces dice "¡Tenés envío gratis!".
- Sin umbral, el panel no muestra la barra.

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
