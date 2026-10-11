# E1 – Catálogo y carga de productos

**Estado:** Borrador · **Casos de uso:** CU-01 a CU-07 y CU-26

## CU-01 Explorar catálogo por categoría y público

**Actor:** Visitante

**Flujo principal**
1. El visitante entra a la tienda o a una categoría.
2. El sistema muestra una grilla de productos activos con foto, nombre y precio "desde".
3. Los productos se cargan de a 24, con un botón "Ver más".

**Alternativos**
- Categoría sin productos: se muestra un mensaje y un enlace al catálogo completo.
- Producto sin stock en ninguna variante: se muestra con la etiqueta "Agotado".

**Criterios de aceptación**
- Dado un producto inactivo, cuando se lista el catálogo, entonces no aparece.
- Dado un producto con todas las variantes sin stock, cuando se lista, entonces aparece con "Agotado".
- Dadas más de 24 coincidencias, cuando se abre el catálogo, entonces se muestran 24 y un botón para ver más.

## CU-02 Filtrar y ordenar productos

**Actor:** Visitante

**Flujo principal**
1. El visitante abre los filtros (en el celular, un panel deslizable).
2. Elige uno o más valores de: precio (rango), talle, color, material, temporada (verano, media estación, invierno) y público (mujer, hombre, infantil).
3. El sistema actualiza la grilla y la URL con los filtros aplicados.
4. Puede ordenar por precio (menor o mayor) o por novedades.

**Reglas**
- Entre filtros distintos se combinan con "y"; dentro de un mismo filtro (varios talles) con "o".
- Un talle o color solo cuenta si la variante tiene stock disponible.

**Criterios de aceptación**
- Dados los filtros talle M y temporada verano, cuando se aplican, entonces solo aparecen productos con una variante M con stock y temporada verano.
- Dado un filtro aplicado, cuando se comparte la URL, entonces quien la abre ve los mismos filtros.
- Dados filtros sin coincidencias, cuando se aplican, entonces se muestra un mensaje y la opción de limpiar filtros.

## CU-03 Ver detalle y elegir variante

**Actor:** Visitante

**Flujo principal**
1. El visitante abre un producto.
2. El sistema muestra galería, descripción, material, temporada y precio.
3. El visitante elige talle y color; el sistema muestra el stock disponible de esa combinación.
4. Con una combinación disponible, se habilita el botón de agregar al carrito (CU-08).

**Reglas**
- Las combinaciones sin stock se muestran deshabilitadas.
- Hasta elegir talle y color, el botón de agregar está deshabilitado.

**Criterios de aceptación**
- Dado un producto rosa M con stock y celeste M sin stock, cuando se abre el detalle, entonces solo el celeste M figura agotado.
- Dada una combinación con stock disponible 0, cuando se selecciona, entonces el botón de compra está deshabilitado.
- Dado el detalle abierto, cuando se cambia de color, entonces el precio se actualiza según la variante y la galería muestra primero las fotos de ese color.
- Dado un slug inexistente, cuando se abre, entonces se muestra una página 404 con enlace a la tienda.

## CU-04 Iniciar sesión en el backoffice

**Actor:** Administrador

**Flujo principal**
1. El administrador ingresa email y contraseña.
2. El sistema valida las credenciales y abre una sesión.

**Reglas**
- Solo usuarios con rol ADMIN acceden a `/admin`.
- Hay un único administrador (el dueño); no hay gestión de roles ni de otros usuarios del backoffice (RNF-24).
- *Propuesto:* segundo factor (TOTP) para el administrador.
- La sesión del backoffice vence tras 60 minutos de inactividad.
- Tras 5 intentos fallidos seguidos, el acceso se bloquea 15 minutos.
- El primer administrador se crea con variables de entorno al desplegar: al arrancar, si no existe ningún administrador, se crea con el email, el nombre y la contraseña inicial configurados (secreto de Docker). Si ya existe, esos valores se ignoran.
- Contraseña de al menos 12 caracteres, guardada con BCrypt.
- Con la cuenta bloqueada, el mensaje indica la hora de desbloqueo (esto revela que el email existe; se acepta porque hay un único administrador). Un ingreso correcto reinicia el contador de intentos fallidos.

**Criterios de aceptación**
- Dadas credenciales correctas, cuando inicia sesión, entonces accede al panel.
- Dadas credenciales incorrectas, cuando intenta entrar, entonces ve un mensaje genérico que no revela qué dato falló.
- Dado un usuario CLIENTE, cuando entra a `/admin`, entonces recibe acceso denegado.
- Dado más de 5 intentos de login en un minuto desde la misma IP, cuando llega otro, entonces recibe `429` con `Retry-After`.

## CU-05 Gestionar categorías

**Actor:** Administrador

**Flujo principal:** crear, renombrar, ordenar, desactivar, reactivar y eliminar categorías.

**Reglas**
- El `slug` es único.
- El `slug` se genera a partir del nombre (minúsculas, sin tildes y con guiones) y cambia al renombrar la categoría.
- Una categoría nueva queda última en el orden.
- Una categoría con productos no se elimina: se desactiva. Una categoría sin productos sí se puede eliminar.
- Una categoría desactivada se puede volver a activar.

**Criterios de aceptación**
- Dado un nombre de categoría ya existente, cuando se crea, entonces se rechaza con un mensaje claro.
- Dada una categoría con productos, cuando se intenta eliminar, entonces solo se permite desactivarla.

## CU-06 Gestionar productos y variantes

**Actor:** Administrador

**Flujo principal**
1. El administrador crea un producto con nombre, descripción, material, temporada, público y categoría.
2. Define los talles y colores; el sistema genera la matriz de variantes.
3. Carga precio y stock inicial de cada variante.
4. Sube las fotos; el sistema las convierte a WebP y genera miniaturas y un placeholder borroso.
5. Publica el producto.

**Reglas**
- No puede haber dos variantes con el mismo talle y color en un producto.
- El SKU es único en toda la tienda. Lo genera el sistema al crear la variante (por ejemplo `PP-7K3M9QX2`) y no se edita.
- El `slug` del producto sigue las reglas de las categorías: sale del nombre, es único y cambia al renombrar.
- Un producto nuevo queda oculto y sin variantes.
- Generar la matriz es aditivo: crea solo las combinaciones que faltan y nunca borra variantes. Hasta 20 talles y 20 colores por producto.
- Una variante se puede desactivar y volver a activar. El precio se puede cambiar, pero no quitar.
- Un producto sin al menos una variante activa con precio no se puede publicar, y a un producto publicado no se le puede desactivar la última variante activa con precio (primero se oculta).
- Los productos no se eliminan: se ocultan.
- Se aceptan fotos JPG, PNG y WebP de hasta 10 MB, con un máximo de 8 por producto (*Propuesto*).
- Desde el celular se puede sacar la foto o elegirla de la galería, y se pueden elegir varias a la vez.
- El dispositivo reduce cada foto (máximo 1600 px de lado) antes de enviarla, para ahorrar datos móviles.
- Una foto puede ser de uno de los colores del producto (se muestra en todos los talles de ese color) o de todo el producto. El color se elige al subirla; para cambiarlo, se borra y se sube de nuevo.
- Las fotos se pueden reordenar y borrar. Una foto nueva va al final.
- El servidor guarda cada foto en WebP de 400, 800 y 1200 px de ancho (nunca la agranda), sin metadatos y con la orientación aplicada, más un placeholder borroso.

**Criterios de aceptación**
- Dados 3 talles y 2 colores, cuando se genera la matriz, entonces se crean 6 variantes.
- Dada una foto JPG de 4 MB, cuando se sube, entonces se guardan versiones WebP más livianas y un placeholder.
- Dado un producto oculto, cuando se publica, entonces aparece en el catálogo.
- Dada una foto de 8 MB elegida desde el celular, cuando se sube, entonces se reduce en el dispositivo antes de enviarse y el servidor la procesa como cualquier otra.
- Dada una subida que se corta por falta de conexión, cuando vuelve la señal, entonces se puede reintentar sin duplicar la foto.
- Dado un archivo llamado `.jpg` cuyo contenido no es JPG, PNG ni WebP, cuando se sube, entonces se rechaza.
- Dado un producto con 8 fotos, cuando se sube otra, entonces se rechaza.
- Dada una foto, cuando se borra, entonces deja de figurar en el producto y se eliminan sus archivos.

## CU-07 Gestionar stock

**Actor:** Administrador

**Flujo principal**
1. El administrador elige una variante y registra un ajuste (suma o resta) con un motivo.
2. El sistema actualiza el stock y guarda un movimiento en el historial.

**Reglas**
- El stock nunca queda negativo.
- Todo cambio de stock genera un movimiento.

**Criterios de aceptación**
- Dado un stock de 3, cuando se resta 5, entonces se rechaza.
- Dado un ajuste válido, cuando se guarda, entonces el historial muestra fecha, usuario, cantidad y motivo.

## CU-26 Buscar productos por texto

**Actor:** Visitante

**Flujo principal**
1. El visitante escribe en la barra de búsqueda (en el celular, un ícono abre una pantalla de búsqueda).
2. Desde 2 caracteres, el sistema sugiere hasta 5 productos con foto y nombre.
3. Al confirmar, muestra una grilla de resultados ordenada por relevancia, con los mismos filtros del CU-02.

**Reglas**
- La búsqueda ignora tildes y mayúsculas y entiende singular y plural en español.
- Busca en nombre, categoría, material, descripción y colores de las variantes; el nombre pesa más.
- Solo aparecen productos activos.
- Las consultas de menos de 2 caracteres no se ejecutan, y el texto se limita a 100 caracteres.

**Criterios de aceptación**
- Dado un producto "Pijama de algodón", cuando se busca "algodon" sin tilde, entonces aparece.
- Dado el mismo producto, cuando se busca "pijamas", entonces aparece.
- Dados productos que coinciden por nombre y otros solo por descripción, cuando se busca, entonces los del nombre aparecen primero.
- Dada una búsqueda sin resultados, cuando se muestra, entonces hay un mensaje y enlaces a las categorías.
- Dada una búsqueda combinada con el filtro de talle M, cuando se aplican, entonces solo salen productos con talle M con stock.
- Dado un producto inactivo, cuando se busca por su nombre, entonces no aparece.
- Dado un texto con comillas, símbolos o intentos de inyección, cuando se busca, entonces se trata como texto y no produce error.
- Dadas más de 60 búsquedas por minuto desde la misma IP, cuando llega otra, entonces recibe `429` con `Retry-After`.
