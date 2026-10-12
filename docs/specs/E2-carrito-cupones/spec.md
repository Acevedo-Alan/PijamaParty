# E2 – Carrito y cupones por código

**Estado:** Borrador · **Casos de uso:** CU-08 a CU-11

## CU-08 Agregar producto al carrito

**Actor:** Visitante o Cliente

**Precondición:** variante elegida con stock disponible (CU-03).

**Flujo principal**
1. El usuario toca "Agregar al carrito".
2. El sistema agrega la variante (o suma la cantidad si ya estaba).
3. Se abre automáticamente el carrito como panel lateral, con el fondo atenuado (overlay).
4. En el celular se produce una vibración leve; el ícono del carrito muestra el contador con una animación de rebote.

**Reglas**
- No se muestra toast: el panel abierto es la confirmación.
- La cantidad en el carrito no puede superar el stock disponible.

**Criterios de aceptación**
- Dada una variante con stock 3 y 2 unidades ya en el carrito, cuando se agregan 2 más, entonces se rechaza e informa "Solo quedan 3".
- Dado un agregado exitoso, cuando termina, entonces el panel lateral se abre solo y el contador se actualiza.
- Dado un dispositivo sin soporte de vibración, cuando se agrega, entonces el resto del feedback funciona igual.
- Dado un visitante sin carrito, cuando agrega el primer producto, entonces se crea un carrito asociado a su navegador.

## CU-09 Modificar el carrito

**Actor:** Visitante o Cliente

**Flujo principal**
1. El usuario cambia la cantidad de un ítem o lo quita.
2. El sistema revalida stock y precios y recalcula los totales.

**Reglas**
- Si el precio o el stock cambiaron desde que se agregó el ítem, se avisa antes de continuar.
- Un ítem cuya variante quedó sin stock se marca como no disponible y no se puede comprar.

**Criterios de aceptación**
- Dado un ítem en el carrito, cuando se cambia la cantidad a 0, entonces se elimina.
- Dada una variante que se agotó mientras estaba en el carrito, cuando se abre el carrito, entonces se muestra como no disponible.
- Dado un cambio de precio en el catálogo, cuando se abre el carrito, entonces el total usa el precio nuevo y se avisa del cambio.

## CU-10 Aplicar cupón por código

**Actor:** Visitante o Cliente

**Flujo principal**
1. El usuario escribe un código en el carrito (o en el checkout).
2. El sistema valida el cupón y muestra el descuento.
3. El usuario puede quitar el cupón.

**Reglas**
- Un solo cupón por carrito.
- Se valida: activo, dentro de la vigencia, usos disponibles y monto mínimo de compra.
- El descuento se calcula siempre en el servidor. Un descuento por porcentaje se redondea hacia abajo al centavo: la tienda nunca descuenta más que el porcentaje indicado.
- Si un cupón aplicado deja de cumplir las condiciones (el carrito baja del mínimo, o el cupón vence, se desactiva o se agota), queda en el carrito sin descuento y con el motivo. Vuelve a aplicarse solo si el carrito vuelve a cumplirlas.

**Criterios de aceptación**
- Dado un cupón del 10% válido, cuando se aplica a un carrito de $20.000, entonces el descuento es $2.000.
- Dado un cupón vencido, cuando se aplica, entonces se muestra "El cupón venció".
- Dado un carrito por debajo del mínimo, cuando se aplica, entonces se informa cuánto falta para llegar al mínimo.
- Dado un cupón agotado (usos completos), cuando se aplica, entonces se rechaza.
- Dado un cupón ya aplicado, cuando se aplica otro, entonces lo reemplaza.
- Dados más de 10 intentos de cupón por minuto en el mismo carrito, cuando llega otro, entonces recibe `429` con `Retry-After`.

## CU-11 Gestionar cupones

**Actor:** Administrador

**Flujo principal:** crear, editar, activar y desactivar cupones; ver cuántas veces se usó cada uno.

**Datos del cupón:** código, tipo (porcentaje o monto fijo), valor, vigencia, usos máximos y monto mínimo de compra.

**Reglas**
- El código es único y no distingue mayúsculas de minúsculas.
- Un cupón nuevo nace activo.
- Todos los datos se pueden editar, también en un cupón con usos: los usos se conservan y cada pedido confirmado mantiene el descuento que tuvo.
- La vista de usos es el número de usos de cada cupón en el listado ("3 de 10", o "3" sin máximo).
- Un cupón sin usos se puede eliminar. Un cupón usado no se elimina: se desactiva.
- Las reglas automáticas se agregan en E5 (CU-23).

**Criterios de aceptación**
- Dado un código ya existente, cuando se crea otro igual, entonces se rechaza.
- Dado un cupón con usos registrados, cuando se intenta eliminar, entonces solo se permite desactivarlo.
