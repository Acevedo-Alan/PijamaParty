# E2 – Plan técnico

## Modelo de datos nuevo

`carrito`, `carrito_item`, `cupon`. En esta etapa el carrito **no reserva stock**; la reserva ocurre al iniciar el pago (E3).

## API

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/carrito` | Carrito actual con totales calculados |
| POST | `/api/carrito/items` | Agregar variante (CU-08) |
| PATCH | `/api/carrito/items/{id}` | Cambiar cantidad (CU-09) |
| DELETE | `/api/carrito/items/{id}` | Quitar ítem (CU-09) |
| POST | `/api/carrito/cupon` | Aplicar cupón (CU-10) |
| DELETE | `/api/carrito/cupon` | Quitar cupón |
| CRUD | `/api/admin/cupones` | CU-11 |

## Decisiones técnicas

- **Carrito en el servidor.** Para invitados se identifica con una cookie `cart_token` (`HttpOnly`, `Secure`, `SameSite=Lax`), con el prefijo `__Host-` para que nadie pueda plantarla desde otro subdominio ni por HTTP. Así el servidor valida precios y stock, y el carrito sobrevive si se cierra el navegador.
- **Totales:** siempre los calcula el servidor (reglas R-01 y R-02); la respuesta incluye subtotal, descuento y total.
- **Limpieza:** un proceso diario elimina carritos sin actividad hace más de 30 días.
- **Cupón:** el uso se cuenta recién al confirmar el pedido (E3), no al aplicarlo.
- **Frontend:** panel lateral accesible (foco atrapado, cierre con Escape), vibración con `navigator.vibrate` donde exista, animación del contador con CSS.

## Riesgos

- Dos pestañas modificando el mismo carrito: la última acción gana y el servidor devuelve siempre el estado completo.
