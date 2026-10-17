# E5 – Plan técnico

## Modelo de datos nuevo

`resena` (con el nombre de pila del autor guardado al enviarla); se amplía `cupon` con `automatico`, y el alcance de un cupón automático va en la tabla `cupon_alcance` (producto o categoría y su id, sin FK al catálogo). El promedio y la cantidad de reseñas se calculan al leer la ficha: `producto` es del módulo `catalog` y `reviews` no puede escribirlo (ARCHITECTURE.md, sección 3, regla 4); con el volumen esperado alcanza un índice por producto y estado.

## API

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/catalog/products/{slug}/reviews` | Reseñas aprobadas, promedio y cantidad |
| GET | `/api/account/reviews/{productId}` | Si el cliente puede opinar y su reseña (CU-24) |
| PUT | `/api/account/reviews/{productId}` | Crear o editar la reseña (CU-24) |
| GET | `/api/admin/reviews?status=` | CU-25 |
| POST | `/api/admin/reviews/{id}/approve`, `/reject` | Aprobar o rechazar |
| DELETE | `/api/admin/reviews/{id}` | Eliminar |

El motor de reglas se integra en el cálculo de totales del carrito; no requiere endpoints nuevos para el cliente. Los cupones automáticos se crean con los endpoints de CU-11, que suman `automatic` y `scope`.

## Decisiones técnicas

- **Motor de reglas:** interfaz `DiscountRule` con una implementación por tipo (vigencia, usos, monto mínimo, alcance); se evalúan todos los cupones candidatos y se elige el de mayor descuento.
- **Promedio:** se calcula al leer, solo sobre las reseñas aprobadas.
- **Moderación:** estados PENDIENTE, APROBADA y RECHAZADA.

## Riesgos

- Las reglas cambian con frecuencia: mantener el motor simple y con pocos tipos al principio.
- Reseñas con contenido ofensivo: por eso pasan por moderación antes de publicarse.
