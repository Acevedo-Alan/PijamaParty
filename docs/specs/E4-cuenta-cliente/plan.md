# E4 – Plan técnico

## Modelo de datos nuevo

`direccion` (hasta 10 por cliente, una principal como máximo); se amplía `usuario` con `telefono` opcional (`email_verificado`, `nombre` y el `password_hash` opcional ya estaban desde E1); el DNI no se guarda en la cuenta; tabla `identidad_externa` (`usuario_id`, `proveedor`, `sujeto`, `email`, `creado_en`, restricción única sobre `(proveedor, sujeto)`); tabla `token_usuario` para verificación de email y recuperación de contraseña (con tipo, vencimiento y uso; tipos: verificación, 24 horas; recuperación, 30 minutos; y confirmación de vinculación, 24 horas, que llega con T-E4-13). Los tokens se guardan hasheados (RNF-29.6). En el código: `app_user.phone`, `address` y `user_token` (V29), con RLS activo y forzado y sus políticas; la verificación y la recuperación corren como SISTEMA atadas al hash del token que consumen (`app.account_token_hash`). Detalle en `docs/use-cases/E4-customer-account.md`. `pedido.usuario_id` pasa a usarse.

## API

| Método | Ruta | Uso |
|---|---|---|
| POST | `/api/cuenta/registro` | CU-19 |
| POST | `/api/cuenta/verificar-email` | CU-19 |
| POST | `/api/cuenta/login` y `/logout` | CU-19 |
| POST | `/api/cuenta/recuperar` y `/restablecer` | CU-19 |
| GET/POST/PUT/DELETE | `/api/cuenta/direcciones` | CU-20 |
| GET | `/api/cuenta/pedidos` y `/pedidos/{id}` | CU-22 |
| GET | `/api/cuenta/oauth2/authorization/{proveedor}` | Inicio del ingreso social; `proveedor` es `google` o `facebook` (CU-27). Bajo `/api`, así Caddy y el proxy de Vite lo alcanzan sin cambios |
| GET | `/api/cuenta/oauth2/code/{proveedor}` | Vuelta desde el proveedor (CU-27) |
| GET | `/api/cuenta/oauth2/proveedores` | Proveedores habilitados, para mostrar solo sus botones (CU-27) |
| POST | `/api/cuenta/oauth2/email` | Email de una cuenta de Facebook que no lo informa (CU-27) |
| POST | `/api/cuenta/vincular/confirmar` | Confirmar la vinculación por correo (CU-27) |
| GET/DELETE | `/api/cuenta/identidades` | Ver y desvincular cuentas externas (CU-27) |
| PUT | `/api/cuenta/password` | Crear o cambiar la contraseña (CU-27) |
| GET | `/api/cuenta/datos` | Descargar mis datos (CU-28) |
| DELETE | `/api/cuenta` | Eliminar mi cuenta (CU-28) |

## Decisiones técnicas

- **Sesión de cliente** con el mismo mecanismo que el administrador (cookie `HttpOnly`, CSRF), con rol CLIENTE.
- **Hash de contraseñas** con BCrypt o Argon2.
- **Unión de carritos** al iniciar sesión: se suman cantidades limitadas por el stock disponible.
- **Asociación de pedidos de invitado:** por email, solo después de verificarlo, para que nadie pueda ver pedidos ajenos registrando un email que no es suyo.
- **Respuestas de recuperación:** siempre iguales exista o no el email, para no revelar cuentas.
- **Ingreso social:** Spring Security OAuth2 Client (`oauth2Login`), flujo Authorization Code con `state` y PKCE; para Google se valida el ID token de OpenID Connect (firma, `aud`, `iss`, `nonce`) y se usa el claim `email_verified`. Facebook Login no informa si el email está verificado: se confirma por correo antes de vincular. Al terminar, se crea la misma sesión con cookie que en el login con contraseña. Las claves de cliente de cada proveedor van en variables de entorno (RNF-29.10).
- **Vinculación por email:** la búsqueda de la cuenta existente usa el email normalizado (recorte y minúsculas).
- **Eliminación de cuenta:** `accounts` publica `CustomerAccountDeleted`; `ordering` desvincula los pedidos (`usuario_id` en nulo) y `reviews` anonimiza las reseñas, cada uno con un handler idempotente.
- **Requisitos de los proveedores:** pantalla de consentimiento de Google y app de Meta en modo "en vivo", ambas con URL de política de privacidad y términos; Meta exige además una URL con instrucciones de eliminación de datos (CU-28). Los dominios de los proveedores se suman a la CSP (RNF-29.9).

## Riesgos

- Entrega de correos (verificación y recuperación): depende del proveedor de correo definido en E3.
- La revisión de la app de Meta y la verificación de la marca en Google pueden demorar días: iniciarlas al comienzo de la etapa.
- Ingreso social dentro de navegadores integrados (Instagram o Facebook abren los enlaces en su propio navegador): Google bloquea OAuth en algunos de ellos. Probar en E6 y, si falla, mostrar un aviso para abrir el sitio en el navegador del celular.
