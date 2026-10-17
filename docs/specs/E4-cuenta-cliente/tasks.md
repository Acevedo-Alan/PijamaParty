# E4 – Tareas

- [x] **T-E4-01** Migraciones: `direccion`, ampliación de `usuario` y `token_usuario`. En el código: `address`, `app_user.phone` y `user_token` (V29); `address` y `user_token` nacen con Row Level Security activo y forzado, con sus políticas, y se amplían las de `app_user` (RNF-27).
- [x] **T-E4-02** Registro, verificación de email y login de clientes (CU-19). En el código: `POST /api/account/register`, `/verify-email`, `/login`, `/logout` y `GET /api/account/me`; el correo de verificación (`EMAIL_VERIFICATION`, V30) lleva el token en el fragmento del enlace (`/cuenta/verificar#token`); un login con la contraseña correcta y el email sin verificar se rechaza y envía un enlace nuevo. Pantallas `/cuenta/registro`, `/cuenta/ingresar` y `/cuenta/verificar`, y el acceso en el encabezado.
- [ ] **T-E4-04** Unión del carrito de invitado con el de la cuenta al iniciar sesión (CU-19).
- [x] **T-E4-03** Recuperación y restablecimiento de contraseña (CU-19). En el código: `POST /api/account/password-recovery`, que responde siempre `204` y solo envía el correo (`PASSWORD_RESET`, V31) a una cuenta de cliente, y `POST /api/account/password-reset`, que usa el enlace una vez, invalida los enlaces anteriores, quita el bloqueo, verifica el email (V31 amplía la guarda de V29) y cierra todas las sesiones de la cuenta. El token va en el fragmento (`/cuenta/restablecer#token`). Pantallas `/cuenta/recuperar` y `/cuenta/restablecer`, y el enlace "¿Olvidaste tu contraseña?" en el ingreso.
- [ ] **T-E4-05** Libreta de direcciones: API y pantallas (CU-20).
- [ ] **T-E4-06** Checkout con datos y direcciones precargados (CU-21).
- [ ] **T-E4-07** "Mis pedidos" y detalle con seguimiento (CU-22).
- [ ] **T-E4-08** Asociación de pedidos de invitado tras verificar el email (CU-22).
- [ ] **T-E4-09** Tests de los criterios de aceptación de CU-19 a CU-22.
- [ ] **T-E4-10** Activar las políticas de rate limiting de registro, login de clientes y recuperación (RNF-22).
- [ ] **T-E4-11** Tests de las políticas de Row Level Security de `direccion`, `usuario` y `token_usuario` con cliente A, cliente B, invitado, administrador y sistema, y test de acceso a pedidos y direcciones de otro cliente (RNF-27). Las políticas nacen con cada tabla: T-E4-01, T-E4-13 y T-E4-16.
- [ ] **T-E4-12** Alta de las apps en Google Cloud y Meta for Developers (consentimiento, dominios, URLs de privacidad, términos y eliminación de datos) e inicio de la revisión de Meta (CU-27). *Requiere el dominio y las páginas legales publicadas.*
- [ ] **T-E4-13** Migraciones: `identidad_externa` y el propósito de confirmación de vinculación de `token_usuario` (CU-27). El `password_hash` opcional ya estaba desde E1 (V5).
- [ ] **T-E4-14** Ingreso y registro con Google y Facebook, vinculación por email y unión de carritos (CU-27).
- [ ] **T-E4-15** Pantalla "Mi cuenta": vincular y desvincular proveedores y crear contraseña (CU-27).
- [ ] **T-E4-16** Descarga de datos y eliminación de cuenta, con desvinculación de pedidos y anonimización de reseñas (CU-28).
- [ ] **T-E4-17** Tests de los criterios de aceptación de CU-27 y CU-28, incluido el rechazo del ingreso social para el administrador.

**Terminado cuando:** un cliente se registra (con contraseña, Google o Facebook), guarda una dirección, compra con datos precargados, ve su pedido y su seguimiento, y puede eliminar su cuenta.
