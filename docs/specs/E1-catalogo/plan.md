# E1 – Plan técnico

## Modelo de datos nuevo

`categoria`, `producto`, `variante`, `imagen`, `movimiento_stock`, `usuario` (solo rol ADMIN por ahora). Campos en `00-dominio-y-reglas.md`.

Índices sugeridos: `variante(producto_id)`, `producto(slug)`, `producto(categoria_id, activo)`.

## API pública

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/catalogo/productos` | Lista con filtros (`q`, `categoria`, `precioMin`, `precioMax`, `talle`, `color`, `material`, `temporada`, `publico`, `orden`, `page`) |
| GET | `/api/catalogo/productos/{slug}` | Detalle con variantes y stock disponible |
| GET | `/api/catalogo/categorias` | Categorías activas |
| GET | `/api/catalogo/filtros` | Valores disponibles para cada filtro |
| GET | `/api/catalogo/sugerencias?q=` | Hasta 5 sugerencias mientras se escribe (CU-26) |

## API de administración

| Método | Ruta | Uso |
|---|---|---|
| POST | `/api/admin/auth/login` | CU-04 |
| POST | `/api/admin/auth/logout` | CU-04 |
| GET | `/api/admin/auth/me` | Usuario actual |
| CRUD | `/api/admin/categorias` | CU-05 |
| CRUD | `/api/admin/productos` | CU-06 |
| POST | `/api/admin/productos/{id}/variantes/generar` | Matriz de talles × colores |
| PATCH | `/api/admin/variantes/{id}` | Precio y estado |
| POST | `/api/admin/productos/{id}/imagenes` | Subida multipart |
| POST | `/api/admin/variantes/{id}/stock-ajustes` | CU-07 |
| GET | `/api/admin/variantes/{id}/movimientos` | Historial |

## Decisiones técnicas

- **Sesión con cookie** `HttpOnly`, `Secure`, `SameSite=Lax`, con protección CSRF activada.
- **Imágenes:** se guardan en un volumen de Docker servido por Caddy en `/uploads`; el volumen entra en el backup. Se generan versiones de 400, 800 y 1200 px en WebP y un placeholder de 10 px en base64 para el efecto blur-up. La conversión usa libvips por línea de comandos (`vips`, instalado en la imagen de la API), en un proceso aparte con timeout, fuera de la transacción y del heap de la JVM.
- **Filtros:** consulta con especificaciones de Spring Data JPA; los talles y colores filtran sobre variantes con stock disponible mayor a 0.
- **Frontend:** estado de los filtros en la URL (query params).

## Riesgos

- Rendimiento de los filtros con muchos productos: revisar índices y el plan de consulta si crece el catálogo.
- Conversión de imágenes con poca memoria: procesar de a una y limitar el tamaño de subida.

## Requisitos transversales aplicados

- **RNF-22:** políticas de login y de catálogo activas en esta etapa.
- **RNF-21:** semáforo de 2 conversiones de imagen simultáneas; si se supera, la subida espera en cola.
- **RNF-24:** administrador único; la tabla `usuario` mantiene el campo `rol` (ADMIN o CLIENTE) para separar áreas.

## Búsqueda por texto (CU-26)

- **Motor:** búsqueda de texto completo de PostgreSQL con la configuración `spanish` y la extensión `unaccent`. No hace falta un motor externo para un catálogo de este tamaño; si creciera mucho, se reevalúa.
- **Columna `busqueda` (tsvector) en `producto`** con pesos: nombre (A), categoría y material (B), descripción y colores de las variantes (C), con índice GIN.
- **Mantenimiento:** `unaccent` no es inmutable, así que no sirve en una columna generada. La columna se recalcula en el servicio al guardar un producto o sus variantes.
- **Consulta:** `websearch_to_tsquery('spanish', unaccent(:q))`, ordenada por `ts_rank` y, en empate, por novedad. Para las sugerencias se usa coincidencia por prefijo en la última palabra. La búsqueda se combina con los filtros del CU-02 en la misma consulta.
- **Entrada:** se recorta, se limita a 100 caracteres y siempre se pasa como parámetro.
- **Fuera de la v1:** tolerancia a errores de tipeo (extensión `pg_trgm`).
- **Frontend:** barra de búsqueda en el encabezado, con *debounce* de 250 ms en las sugerencias, y pantalla completa en el celular.

## Backoffice en celular y computadora (RNF-26)

- Diseño desde 360 px: listas como tarjetas en pantallas chicas y tablas en las grandes; formularios de una columna; controles táctiles de al menos 44 px.
- Stock con botones − y + además del campo numérico.
- Subida de fotos con `<input type="file" multiple accept="image/jpeg,image/png,image/webp">`. En iPhone, limitar los tipos hace que el sistema convierta HEIC a JPEG (verificar en un dispositivo real).
- Reducción en el navegador con canvas (máximo 1600 px, calidad aproximada de 0,85), barra de progreso y reintento con `Idempotency-Key` para no duplicar fotos.
- Reordenar fotos: arrastrar en computadora y botones de subir y bajar en celular.
