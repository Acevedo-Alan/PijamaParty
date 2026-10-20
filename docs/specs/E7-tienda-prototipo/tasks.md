# E7 – Tareas

- [ ] **T-E7-01** Decisiones con el dueño y materiales del negocio. Decidido el 2026-10-19: D-03 a D-14 y D-16 (ver la tabla de la spec y `00-dominio-y-reglas.md`); el banner de personajes se edita desde el backoffice (CU-32), el checkout separa nombre y apellido (D-13) y las animaciones usan la librería Motion (D-16). Pendiente: D-01 (cuotas) y D-02 (transferencia), que no se muestran hasta decidirlas, y D-15 (logo, fotos, email, WhatsApp y redes del negocio). *Tarea del negocio.*
- [x] **T-E7-12** Animaciones: agregar Motion (versión fija) con `LazyMotion` y `MotionConfig`, los tokens en `tokens.css` y `motion.ts`, las animaciones existentes pasadas al sistema y las de respuesta (agregar al carrito, paneles, aviso, secciones desplegables). Las demás llegan con la tarea de cada pantalla (sección "Animaciones", D-16). El aviso queda listo como componente (`Toast`); T-E7-09 lo usa para "Agregado al carrito".
- [x] **T-E7-02** Encabezado con logo, navegación por público y temporada, menú del celular y franja de envío gratis (CU-31).
- [ ] **T-E7-03** Pie en tres columnas y sección de contacto con email y WhatsApp (CU-31, D-09).
- [x] **T-E7-04** Portada: carrusel, banner de personajes y "Elegí para quién", con la grilla de CU-01 debajo (CU-30).
- [ ] **T-E7-05** API del listado: `colors`, `isNew` y `total`; tarjeta con círculos de color y "Nuevo", y contador de productos (cambios a CU-01).
- [ ] **T-E7-06** Barra de filtros en botones y vista de cuadrícula o lista (cambios a CU-02).
- [ ] **T-E7-07** Ficha: `category` en la API, migas de pan, galería con vista ampliada, círculos de color, cantidad, barra fija en el celular, íconos de confianza, envío por provincia y secciones desplegables (cambios a CU-03).
- [ ] **T-E7-08** Ficha: "Completá tu look" (cambios a CU-03, D-08).
- [ ] **T-E7-09** Panel del carrito: barra de envío gratis y aviso "Agregado al carrito" (cambios a CU-08).
- [ ] **T-E7-10** Checkout: campos de a dos por fila, nombre y apellido separados en el checkout, la API, el pedido (migración) y los correos, e "Iniciar sesión" para invitados (cambios a CU-13, D-13).
- [x] **T-E7-13** Banner de personajes editable: tabla y agregado `HomeBanner` (migración), endpoints públicos y del backoffice, pantalla "Portada" del backoffice y sección de la portada (CU-32).
- [ ] **T-E7-11** Cierre: Lighthouse y accesibilidad sobre el diseño nuevo, e2e actualizados y capturas comparativas contra el prototipo.

**Orden:** T-E7-01 primero (sus decisiones cambian T-E7-02, T-E7-05, T-E7-07, T-E7-09 y T-E7-12), después T-E7-12 (las pantallas usan sus tokens). T-E7-13 va antes de T-E7-04 (la portada muestra el banner). T-E7-02 a T-E7-04 no esperan las fotos: usan los fondos degradados hasta que lleguen.

**Terminado cuando:** las capturas de inicio, listado, ficha, carrito y checkout coinciden con el prototipo, salvo lo que las decisiones dejaron afuera, y Lighthouse sigue en verde.
