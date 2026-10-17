# E5 – Promociones y reseñas

**Estado:** Borrador · **Casos de uso:** CU-23 a CU-25

> Las reglas de cupones automáticos y de reseñas (documento de alcance, punto 12) se cerraron con el dueño en T-E5-01 (2026-10-17).

## CU-23 Aplicar cupón automático por reglas

**Actor:** Sistema (configurado por el Administrador en CU-11)

**Flujo principal**
1. El administrador crea un cupón marcado como automático con una regla.
2. Cuando el carrito cumple la regla, el sistema aplica el descuento sin que el cliente escriba un código.
3. El carrito y el checkout muestran el descuento y su motivo.

**Tipos de regla:** monto mínimo de compra y rango de fechas (los campos que ya tiene todo cupón, CU-11), más un alcance opcional de productos o categorías específicos.

**Reglas**
- Un cupón con alcance de productos o categorías descuenta solo sobre los ítems alcanzados, y su monto mínimo se compara con el subtotal de esos ítems.
- Si varias reglas aplican, se usa la de mayor descuento; no se acumulan.
- Un cupón automático y uno por código no se acumulan: entre los automáticos aplicables y el código ingresado se usa el que más descuenta, y el carrito muestra cuál y por qué.

**Criterios de aceptación**
- Dada una regla "10% desde $30.000", cuando el carrito supera $30.000, entonces el descuento aparece sin ingresar código.
- Dado un carrito que baja del mínimo al quitar un ítem, cuando se recalcula, entonces el descuento se retira.
- Dadas dos reglas aplicables, cuando se calcula, entonces se usa la que más descuenta.
- Dada una regla fuera de su vigencia, cuando se calcula, entonces no se aplica.

## CU-24 Dejar una reseña

**Actor:** Cliente

**Flujo principal**
1. El cliente abre un producto que compró y elige un puntaje de 1 a 5, con un texto opcional.
2. El sistema guarda la reseña como PENDIENTE hasta que se modere (CU-25).
3. Una vez aprobada, aparece en la ficha del producto y suma al promedio.

**Reglas**
- Solo pueden opinar clientes con un pedido PAGADO o posterior (PREPARANDO, ENVIADO o ENTREGADO) que incluya el producto.
- Una reseña por cliente y por producto; se puede editar, y vuelve a moderación.

**Criterios de aceptación**
- Dado un cliente que no compró el producto, cuando intenta reseñarlo, entonces se le informa que solo pueden opinar quienes lo compraron.
- Dada una reseña recién enviada, cuando se abre la ficha, entonces no se muestra hasta ser aprobada.
- Dado un producto con reseñas aprobadas de 4 y 5, cuando se abre la ficha, entonces el promedio es 4,5 y se muestra la cantidad.

## CU-25 Moderar reseñas

**Actor:** Administrador

**Flujo principal:** ver las reseñas pendientes, aprobarlas, rechazarlas o eliminarlas.

**Criterios de aceptación**
- Dada una reseña rechazada, cuando se consulta la ficha, entonces no aparece ni cuenta para el promedio.
- Dadas reseñas pendientes, cuando el administrador abre el panel, entonces las ve ordenadas de la más antigua a la más reciente.
