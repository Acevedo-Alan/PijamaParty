# E5 – Plan técnico

## Modelo de datos nuevo

`resena`; se amplía `cupon` con `automatico` y `regla` (columna JSON con tipo y parámetros). En `producto`, campos derivados `puntaje_promedio` y `cantidad_resenas`.

## API

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/catalogo/productos/{slug}/resenas` | Reseñas aprobadas |
| POST | `/api/cuenta/resenas` | CU-24 |
| PUT | `/api/cuenta/resenas/{id}` | Edición |
| GET | `/api/admin/resenas?estado=` | CU-25 |
| POST | `/api/admin/resenas/{id}/moderar` | Aprobar o rechazar |

El motor de reglas se integra en el cálculo de totales del carrito; no requiere endpoints nuevos para el cliente.

## Decisiones técnicas

- **Motor de reglas:** interfaz `ReglaDescuento` con una implementación por tipo; se evalúan todas y se elige la de mayor descuento.
- **Promedio:** se recalcula al aprobar o rechazar una reseña, y se guarda en el producto para no calcularlo en cada listado.
- **Moderación:** estados PENDIENTE, APROBADA y RECHAZADA.

## Riesgos

- Las reglas cambian con frecuencia: mantener el motor simple y con pocos tipos al principio.
- Reseñas con contenido ofensivo: por eso pasan por moderación antes de publicarse.
