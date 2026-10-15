# E4 – Cuenta de cliente

**Estado:** Borrador · **Casos de uso:** CU-19 a CU-22, CU-27 y CU-28

> Registrarse es opcional: comprar como invitado sigue siendo el flujo principal.

## CU-19 Registrarse e iniciar sesión

**Actor:** Visitante

**Flujo principal**
1. El visitante se registra con email, contraseña y nombre.
2. El sistema envía un correo para verificar el email, con un enlace de un solo uso que vence a las 24 horas.
3. Una vez verificado, puede iniciar sesión.
4. Si tenía un carrito de invitado, se une al carrito de su cuenta.

**Alternativos**
- Olvidó su contraseña: recibe por correo un enlace de un solo uso que vence a los 30 minutos.
- Prefiere no crear una contraseña: se registra o inicia sesión con Google o Facebook (CU-27).

**Reglas**
- El email es único.
- La contraseña se guarda con hash seguro; mínimo 8 caracteres.
- Tras 5 intentos fallidos seguidos, el acceso se bloquea 15 minutos.

**Criterios de aceptación**
- Dado un email ya registrado, cuando alguien se registra con él, entonces se rechaza con un mensaje que no expone datos de la cuenta.
- Dado un usuario con un carrito de invitado, cuando inicia sesión, entonces los ítems de ambos carritos se unen sin superar el stock.
- Dado un enlace de recuperación ya usado, cuando se abre otra vez, entonces se rechaza.
- Dados más de 3 intentos de registro o de recuperación por hora desde la misma IP, cuando llega otro, entonces recibe `429` con `Retry-After`.

## CU-20 Gestionar libreta de direcciones

**Actor:** Cliente

**Flujo principal:** agregar, editar y eliminar direcciones (calle, número, piso, departamento, ciudad, provincia y código postal) y marcar una como principal.

**Reglas**
- Hasta 10 direcciones por cliente.
- Como máximo una dirección principal.
- Los campos tienen los formatos de la dirección del pedido (CU-13).

**Criterios de aceptación**
- Dada una dirección marcada como principal, cuando se marca otra, entonces la anterior deja de serlo.
- Dada una dirección usada en pedidos anteriores, cuando se elimina, entonces los pedidos conservan su copia de la dirección.
- Dado un cliente con 10 direcciones, cuando agrega otra, entonces se rechaza.

## CU-21 Comprar con datos precargados

**Actor:** Cliente

**Flujo principal**
1. En el paso de envío, el sistema ofrece las direcciones guardadas y precarga nombre, email y teléfono.
2. El cliente elige una dirección o carga una nueva.
3. El resto del checkout es igual al CU-13.

**Reglas**
- El DNI se pide en cada compra y no se guarda en la cuenta, para guardar menos datos personales (*Decidido*).

**Criterios de aceptación**
- Dado un cliente con una dirección principal, cuando abre el checkout, entonces esa dirección aparece seleccionada.
- Dado un cliente que completa una compra, cuando termina, entonces el pedido queda asociado a su cuenta.

## CU-22 Ver mis pedidos y seguimiento

**Actor:** Cliente

**Flujo principal**
1. El cliente abre "Mis pedidos" y ve la lista con estado y total.
2. Abre un pedido y ve ítems, dirección, estado, número de seguimiento y un enlace al seguimiento de Correo Argentino.

**Reglas**
- Los pedidos hechos como invitado con el mismo email se asocian a la cuenta una vez verificado el email. Un pedido desvinculado al eliminar una cuenta (CU-28) no se vuelve a asociar.

**Criterios de aceptación**
- Dado un cliente con dos pedidos, cuando abre "Mis pedidos", entonces ve ambos, ordenados del más reciente al más antiguo.
- Dado un cliente, cuando intenta abrir el pedido de otra persona, entonces recibe acceso denegado.
- Dado un pedido de invitado con el mismo email verificado, cuando el cliente se registra, entonces el pedido aparece en su lista.

## CU-27 Registrarse e iniciar sesión con Google o Facebook

**Actor:** Visitante

**Flujo principal**
1. En la pantalla de ingreso o registro, el visitante elige "Continuar con Google" o "Continuar con Facebook".
2. El sistema lo redirige al proveedor, que le pide autorizar el acceso a su nombre y email.
3. Al volver, el sistema identifica la cuenta externa (identificador del proveedor y email).
4. Si esa cuenta externa ya está vinculada, inicia sesión.
5. Si no está vinculada y no hay ninguna cuenta con ese email, crea una cuenta nueva con nombre y email del proveedor, sin contraseña.
6. Si tenía un carrito de invitado, se une al carrito de su cuenta (igual que en CU-19).

**Alternativos**
- **Ya existe una cuenta con ese email:** si el proveedor confirma que el email está verificado (Google lo informa), la cuenta externa se vincula a la existente. Si no lo confirma (Facebook), el sistema envía un correo a ese email con un enlace de un solo uso, que vence a las 24 horas, para confirmar la vinculación; hasta entonces no inicia sesión.
- **El proveedor no devuelve email** (por ejemplo, una cuenta de Facebook registrada con teléfono): el sistema pide el email y lo verifica por correo antes de crear la cuenta.
- **El visitante cancela o el proveedor falla:** vuelve a la pantalla de ingreso con un mensaje, sin crear nada.
- **Desde "Mi cuenta"**, el cliente puede vincular o desvincular Google y Facebook, y crear una contraseña si no tenía.

**Reglas**
- Proveedores de la primera versión: Google y Facebook. Apple queda para una fase posterior (requiere cuenta paga de Apple Developer).
- Se piden solo los permisos mínimos: nombre y email. No se publica nada en nombre del cliente y no se guardan los tokens de acceso del proveedor; solo su identificador de cuenta.
- Un email que llega verificado por Google cuenta como verificado (habilita la asociación de pedidos de invitado de CU-22). Un email de Facebook se trata como no verificado hasta confirmarlo por correo.
- No se puede desvincular el último medio de ingreso: tiene que quedar una contraseña u otro proveedor.
- El administrador no puede ingresar con Google ni Facebook: solo con contraseña y segundo factor (RNF-24).
- La compra como invitado sigue siendo el flujo principal; el ingreso social no se ofrece dentro del checkout.

**Criterios de aceptación**
- Dado un visitante sin cuenta, cuando continúa con Google, entonces se crea una cuenta con su nombre y email marcado como verificado, y queda con sesión iniciada.
- Dado un cliente con cuenta y contraseña, cuando continúa con Google usando el mismo email verificado, entonces la cuenta externa se vincula a la existente y no se crea otra.
- Dado un cliente con cuenta, cuando continúa con Facebook usando el mismo email, entonces no inicia sesión hasta confirmar la vinculación con el enlace enviado por correo.
- Dado un cliente cuyo único medio de ingreso es Google, cuando intenta desvincularlo, entonces se rechaza y se le ofrece crear una contraseña.
- Dada una respuesta del proveedor con un `state` que no coincide, cuando llega al sistema, entonces se rechaza sin crear ni vincular cuentas.
- Dada la cuenta del administrador, cuando se intenta ingresar con Google o Facebook usando su email, entonces no se da acceso al backoffice.

## CU-28 Eliminar mi cuenta y descargar mis datos

**Actor:** Cliente

**Flujo principal**
1. Desde "Mi cuenta", el cliente pide descargar sus datos o eliminar su cuenta.
2. **Descarga:** recibe un archivo con su perfil, direcciones, pedidos y reseñas.
3. **Eliminación:** confirma con su contraseña o volviendo a ingresar con su proveedor. El sistema elimina el usuario, sus direcciones, sus cuentas externas vinculadas y su carrito, y cierra todas sus sesiones.

**Reglas**
- Responde a los derechos de acceso y supresión de la Ley 25.326 de protección de datos personales.
- Los pedidos se conservan porque el negocio debe guardarlos por obligaciones contables y fiscales: se desvinculan de la cuenta, pero conservan la copia de nombre, DNI y dirección durante el plazo legal (*confirmar el plazo con el contador*).
- Las reseñas quedan publicadas sin nombre (*Propuesto*).
- La página de instrucciones de eliminación de datos que exige Meta para el ingreso con Facebook apunta a este caso de uso.

**Criterios de aceptación**
- Dado un cliente con pedidos, cuando elimina su cuenta, entonces ya no puede ingresar, sus direcciones no existen y sus pedidos siguen visibles en el backoffice sin vínculo a la cuenta.
- Dado un cliente, cuando pide descargar sus datos, entonces recibe sus datos y ninguno de otro cliente.
- Dado un cliente que elimina su cuenta y se vuelve a registrar con el mismo email, cuando ingresa, entonces no ve los pedidos anteriores.
