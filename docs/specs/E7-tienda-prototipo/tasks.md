# E7 – Tareas

- [ ] **T-E7-01** Decisiones con el dueño y materiales del negocio. Decidido el 2026-10-19: D-03 a D-14 y D-16 (ver la tabla de la spec y `00-dominio-y-reglas.md`); el banner de personajes se edita desde el backoffice (CU-32), el checkout separa nombre y apellido (D-13) y las animaciones usan la librería Motion (D-16). Pendiente: D-01 (cuotas) y D-02 (transferencia), que no se muestran hasta decidirlas, y D-15 (logo, fotos, email, WhatsApp y redes del negocio). *Tarea del negocio.*
- [x] **T-E7-12** Animaciones: agregar Motion (versión fija) con `LazyMotion` y `MotionConfig`, los tokens en `tokens.css` y `motion.ts`, las animaciones existentes pasadas al sistema y las de respuesta (agregar al carrito, paneles, aviso, secciones desplegables). Las demás llegan con la tarea de cada pantalla (sección "Animaciones", D-16). El aviso queda listo como componente (`Toast`); T-E7-09 lo usa para "Agregado al carrito".
- [x] **T-E7-02** Encabezado con logo, navegación por público y temporada, menú del celular y franja de envío gratis (CU-31).
- [x] **T-E7-03** Pie en tres columnas y sección de contacto con email y WhatsApp (CU-31, D-09). El email, el WhatsApp y las redes quedan vacíos en `storeContact.ts` hasta que el negocio los pase (D-15); mientras tanto no se muestran.
- [x] **T-E7-04** Portada: carrusel, banner de personajes y "Elegí para quién", con la grilla de CU-01 debajo (CU-30).
- [x] **T-E7-05** API del listado: `colors`, `isNew` y `total`; tarjeta con círculos de color y "Nuevo", y contador de productos (cambios a CU-01).
- [x] **T-E7-06** Barra de filtros en botones y vista de cuadrícula o lista (cambios a CU-02).
- [x] **T-E7-07** Ficha: `category` en la API, migas de pan, galería con vista ampliada, círculos de color, cantidad, barra fija en el celular, íconos de confianza, envío por provincia y secciones desplegables (cambios a CU-03).
- [x] **T-E7-08** Ficha: "Completá tu look" (cambios a CU-03, D-08).
- [x] **T-E7-09** Panel del carrito: barra de envío gratis y aviso "Agregado al carrito" (cambios a CU-08).
- [x] **T-E7-10** Checkout: campos de a dos por fila, nombre y apellido separados en el checkout, la API, el pedido (migración) y los correos, e "Iniciar sesión" para invitados (cambios a CU-13, D-13).
- [x] **T-E7-13** Banner de personajes editable: tabla y agregado `HomeBanner` (migración), endpoints públicos y del backoffice, pantalla "Portada" del backoffice y sección de la portada (CU-32).
- [x] **T-E7-14** Segunda foto en la tarjeta al pasar el mouse en la computadora: `secondImage` en el listado (API, OpenAPI y cliente tipado) y la tarjeta (cambios a CU-01).
- [x] **T-E7-15** Encabezado que se esconde al bajar y vuelve al subir en el celular (cambios a CU-31).
- [x] **T-E7-16** Encabezado con fondo un poco transparente y difuminado una vez que la página bajó (cambios a CU-31).
- [x] **T-E7-17** Logo de la tienda arriba del menú del celular, y animaciones de entrada: el contenido del menú, las tarjetas que se suman a la grilla y el cambio de página (cambios a CU-31 y a "Animaciones").
- [x] **T-E7-18** Tipografía Inter en lugar de Quicksand en toda la tienda y el backoffice (D-17), con su precarga en `vite.config.ts`.
- [x] **T-E7-19** Panel del carrito vacío: ícono de bolsa, "Empezar a comprar" a todo el ancho y el lugar reservado para "Productos sugeridos" (cambios a CU-08).
- [x] **T-E7-20** Pie: columnas Ayuda y legales, Contacto y redes (con íconos) y Medios de pago (íconos en fila), franja inferior separada por una línea; sin el bloque del botón de arrepentimiento en la portada, que queda en el pie (cambios a CU-29, CU-30, CU-31 y RNF-30.1).
- [x] **T-E7-21** Barra de beneficios arriba del pie: envíos, formas de pago (los medios habilitados) y compra segura (cambios a CU-31).
- [x] **T-E7-22** Ficha: precio destacado, anillo más grueso en el color elegido y envío por provincia en una sola fila (cambios a CU-03, D-07 sin cambios).
- [x] **T-E7-23** Ícono de usuario en lugar de "Ingresar" y "Crear cuenta" en el encabezado, barra de beneficios solo en la portada, y páginas de ingreso, registro, recuperar y elegir contraseña en tarjeta, con "Mostrar contraseña" y los datos faltantes marcados en cada campo (cambios a CU-31 y CU-19).
- [x] **T-E7-24** Pie: "Contacto y redes" se separa en Contacto (WhatsApp y email con sus íconos) y Redes sociales (íconos en círculos con degradé rosa al pasar el mouse) (cambios a CU-31).
- [x] **T-E7-25** Pie: bloque de la tienda (logo, "Pijamas para toda la familia" y las redes) y columna Tienda con los enlaces de la navegación (cambios a CU-31).
- [x] **T-E7-26** Pie: horario de atención y dirección en Contacto, y Correo Argentino como medio de envío (cambios a CU-31).
- [x] **T-E7-27** Botón flotante de WhatsApp en la tienda, salvo en el checkout y la cuenta (cambios a CU-31).
- [x] **T-E7-28** Páginas de ayuda: Cómo comprar, y borradores de Términos y condiciones y de Cambios y devoluciones (D-18) para revisar con el abogado en T-E6-21 (adelanta parte de T-E6-06; cambios a CU-31).
- [x] **T-E7-29** Página Guía de talles con las guías que se cargan en el backoffice para cada categoría activa: `GET /api/catalog/size-guides` (público) y la página `/guia-de-talles` (cambios a CU-31 y RNF-31.5).
- [x] **T-E7-30** Encabezado en forma de píldora flotante en la computadora una vez que la página bajó, con una animación suave (cambios a CU-31).
- [x] **T-E7-31** Ficha: las fotos se deslizan al elegir otra (miniatura, dedo, mouse o flechas del teclado), y la vista ampliada pasa de una foto a otra con un fundido, botones anterior y siguiente, "n / total" y zoom al tocar la foto (cambios a CU-03).
- [x] **T-E7-32** Tarjeta en el celular: deslizar con el dedo entre la primera y la segunda foto, con dos puntos que marcan cuál se ve (cambios a CU-01).
- [x] **T-E7-11** Cierre: Lighthouse y accesibilidad sobre el diseño nuevo, e2e actualizados y capturas comparativas contra el prototipo.

**Orden:** T-E7-01 primero (sus decisiones cambian T-E7-02, T-E7-05, T-E7-07, T-E7-09 y T-E7-12), después T-E7-12 (las pantallas usan sus tokens). T-E7-13 va antes de T-E7-04 (la portada muestra el banner). T-E7-02 a T-E7-04 no esperan las fotos: usan los fondos degradados hasta que lleguen.

**Terminado cuando:** las capturas de inicio, listado, ficha, carrito y checkout coinciden con el prototipo, salvo lo que las decisiones dejaron afuera, y Lighthouse sigue en verde.
