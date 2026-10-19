# E6 – Endurecimiento y publicación

**Estado:** Borrador · **Casos de uso:** ninguno (etapa técnica y de cierre)

## Objetivo

Dejar la tienda lista para producción: probada de punta a punta, respaldada, monitoreada y aceptada por el negocio.

## Requisitos no funcionales

- **RNF-10** Los flujos críticos tienen pruebas de extremo a extremo automáticas.
- **RNF-11** La tienda carga en menos de 3 segundos en un celular con conexión 4G (medición con Lighthouse).
- **RNF-12** Hay un backup diario de la base y de las imágenes guardado fuera del servidor, con una restauración probada.
- **RNF-13** Hay monitoreo de disponibilidad con aviso por correo o mensaje.
- **RNF-14** El sitio muestra política de privacidad, términos y política de cambios y devoluciones, y cumple los requisitos legales de RNF-30.
- **RNF-15** Hay cabeceras de seguridad, límite de intentos de login y ningún dato personal en logs.
- **RNF-16** Los valores de RNF-21 y RNF-22 se validan con una prueba de carga y se ajustan según los resultados.
- **RNF-17** SEO, accesibilidad y páginas de error según RNF-31.

## Criterios de aceptación

**Pruebas de extremo a extremo**
- Compra como invitado con tarjeta aprobada, de punta a punta.
- Pago rechazado: el pedido conserva la reserva para reintentar el pago; al vencer la reserva, el stock se libera.
- Última unidad comprada por dos personas a la vez: solo una compra.
- Cupón inválido, vencido y agotado.
- Webhook repetido: no duplica stock ni correos.
- Alta de producto con variantes y compra del mismo producto.

**Seguridad de datos**
- Dado un cliente A con sesión, cuando intenta leer o modificar el pedido o la dirección de un cliente B por su identificador, entonces recibe un error y la base de datos no devuelve filas.
- Dado un invitado, cuando usa el token de su pedido, entonces solo ve ese pedido.
- Dada una prueba con sqlmap contra los filtros, la búsqueda, el checkout, el cupón y el login del entorno de prueba, entonces no hay hallazgos.
- Dado el rol de la aplicación, cuando se revisan sus privilegios, entonces no puede crear ni modificar tablas.

**Correos**
- Dado un pago aprobado, cuando se confirma, entonces el comprador recibe un único correo que llega a la bandeja principal, con SPF, DKIM y DMARC en verde.
- Dadas las direcciones de prueba del proveedor para rebote y queja (verificar en su documentación), cuando se envía una confirmación, entonces el pedido muestra la alerta en el backoffice.

**Analítica**
- Dado un visitante que rechaza el banner, cuando navega, entonces no se carga ningún script de Google ni de Meta.
- Dada una compra aprobada, cuando se completa, entonces Google Analytics y Meta Pixel registran un solo `purchase` con el importe correcto.
- Dados los eventos de una compra, cuando se inspeccionan, entonces no contienen DNI, email, nombre ni dirección.

**Backoffice en celular**
- Dados un Android y un iPhone reales, cuando el dueño crea un producto con 3 fotos tomadas con la cámara, entonces completa el flujo sin errores ni desplazamiento horizontal.

**Prueba de carga (k6)**
- Con 50 usuarios navegando a la vez, el p95 de las consultas de catálogo es menor a 400 ms.
- Con 10 checkouts simultáneos no hay errores y no se pierde ni se duplica stock.
- Con 20 intentos simultáneos sobre la última unidad de una variante, exactamente uno compra con éxito.
- Reenviar el mismo `Idempotency-Key` y el mismo webhook no produce efectos duplicados.
- Al superar los límites de cada ruta se recibe `429`, y bajo saturación extrema `503`, sin que el servicio se caiga.
- El uso de memoria de la JVM y de PostgreSQL se mantiene dentro del presupuesto de RNF-21.

**Pruebas del negocio (7 días hábiles)**
- Se usa la columna "Cómo sabemos que está bien" del documento de alcance (punto 3) como lista de control.
- Los errores se informan por escrito y se corrigen antes de la aceptación.
- La aceptación queda confirmada por escrito.

**Legales, SEO y accesibilidad**
- Dada la página de inicio en un celular, cuando se busca el botón de arrepentimiento, entonces está visible sin iniciar sesión y lleva al formulario de CU-29.
- Dado el pie del sitio, cuando se revisa, entonces muestra los datos del vendedor, Data Fiscal y el enlace a Defensa del Consumidor.
- Dada una ficha de producto, cuando se valida con la prueba de resultados enriquecidos de Google, entonces `Product` y `Offer` no tienen errores.
- Dada una URL de producto de Treinta, cuando se abre, entonces redirige con `301` a la ficha nueva.
- Dados inicio, listado, ficha y checkout, cuando se miden con Lighthouse, entonces la accesibilidad es de 90 o más.
- Dado un celular con la app de Instagram, cuando se abre el sitio desde su navegador integrado y se elige ingresar con Google, entonces el ingreso funciona o se muestra el aviso para abrirlo en el navegador.

**Restauración**
- Dado un backup de ayer, cuando se restaura en un servidor de prueba, entonces la tienda funciona con esos datos.
