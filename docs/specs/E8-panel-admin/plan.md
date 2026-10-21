# E8 – Plan técnico (fase E8a)

E8b y E8c tienen su plan cuando empiece cada fase. Los casos de uso en inglés, con las notas de diseño, están en `docs/use-cases/E8-admin-panel.md`.

## Enfoque

E8a es casi todo lectura. El resumen, los clientes, las búsquedas y la lista de stock son consultas nuevas sobre tablas que ya existen. Hay dos escrituras nuevas, las dos chicas: los datos de la tienda (una fila) y el registro de actividad, que se agrega dentro de la transacción de cada caso de uso del backoffice que ya existe. El trabajo más extendido es justamente ese: unos 35 casos de uso de 8 módulos suman una línea de registro y su test.

## Módulos

| Módulo | Cambio |
|---|---|
| `activity` (nuevo) | Registro de actividad: tabla, `activity.api.ActivityLog`, lista, tarea de conservación. Sin dependencias. |
| `settings` (nuevo) | Datos de la tienda: contacto, redes, franja de anuncios, umbral de stock bajo. Sin dependencias. |
| `backoffice` | Resumen (CU-33), lista y ficha de clientes (CU-34). Suma dependencias de `accounts`, `reviews`, `settings` y `activity`. |
| `ordering` | Lecturas de ventas y pendientes, pedidos de un cliente, búsqueda y fechas en la lista (cambio a CU-18). |
| `catalog` | Stock bajo y pantalla "Stock" (sobre `inventory.v_variant_availability`), búsqueda y filtros de productos (cambio a CU-06). Depende de `settings` para el umbral. |
| `accounts` | Lista de clientes, ficha y conteos; política de RLS nueva. |
| `reviews`, `notifications` | Un conteo cada uno: reseñas pendientes y pedidos con correo rebotado o fallido. |
| Todos los que escriben desde el backoffice | Una entrada de actividad por cambio (CU-37). |

El grafo sigue sin ciclos: `activity` y `settings` no dependen de nadie, y nadie depende de `backoffice`. Las tablas de módulos de ARCHITECTURE.md (sección 3) y de `ArchitectureTest` se actualizan con T-E8-01 y T-E8-04.

## Migraciones

| Migración | Contenido |
|---|---|
| V41 | `activity_entry` con RLS: `ADMIN` inserta y lee; `SYSTEM` inserta solo entradas de `ACCOUNT` y borra las de más de 2 años; nadie actualiza. Índices por fecha y por área. |
| V42 | `store_settings`: una fila con contacto, redes, anuncio, umbral (3) y `version`, con checks. Sin RLS (no hay datos personales). |
| V43 | `app_user_admin_customer_select` (el administrador lee los usuarios `CUSTOMER`) y la lectura de `external_identity` para `ADMIN`. |
| Siguiente libre | Índice `orders (created_at DESC)` (T-E8-06; el número depende del orden en que se hagan las tareas). |

V43 amplía lo que ve el administrador, así que pasa por el agente `security-reviewer` antes del commit.

## API

| Endpoint | Para |
|---|---|
| `GET /api/admin/dashboard?chart=DAYS\|MONTHS` | Resumen (CU-33) |
| `GET /api/admin/dashboard/counts` | Número de pedidos pagados en el menú |
| `GET /api/admin/customers?q=&from=&to=&page=` y `GET /api/admin/customers/{id}?ordersPage=` | Clientes (CU-34) |
| `GET /api/store/contact` · `GET` y `PUT /api/admin/store/settings` | Datos de la tienda (CU-36) |
| `GET /api/admin/activity?area=&from=&to=&page=` | Actividad (CU-37) |
| `GET /api/admin/orders` suma `q`, `from`, `to` | Búsqueda de pedidos (cambio a CU-18) |
| `GET /api/admin/catalog/products` suma `q`, `category`, `state`, `stock` | Búsqueda de productos (cambio a CU-06) |
| `GET /api/admin/catalog/stock?filter=&q=&category=&page=` | Pantalla "Stock" (cambio a CU-07) |

Se actualizan OpenAPI y el cliente tipado (`npm run api:generate`) en cada tarea que toca la API.

## Decisiones técnicas

- **El registro de actividad es una llamada, no un evento.** Un evento llevaría los valores cambiados por `event_publication`, que no tiene RLS. Una llamada a `activity.api` comparte la transacción: o se guardan el cambio y su entrada, o ninguno.
- **Sin datos personales en la actividad:** la entrada guarda etiquetas que no son personales (nombre del producto, SKU, código del cupón, `#1001`). Un test recorre todas las acciones con datos de prueba conocidos y falla si alguno aparece.
- **Búsquedas:** SQL constante con condiciones opcionales (`:x IS NULL OR ...`), patrones escapados en Java, `lower(unaccent(...))` (la extensión ya está desde V10). Nunca concatenación (RNF-28, Semgrep). El texto buscado no se registra en logs.
- **Fechas:** todo en hora de Argentina a partir del `Clock` inyectado. Los días sin ventas se completan en el dominio (`SalesPeriods`), no en SQL.
- **Sin vistas materializadas ni caché:** con menos de 20 pedidos por día, un año son menos de 10.000 pedidos y las sumas tardan milisegundos.
- **Gráfico sin librería:** barras en SVG escritas a mano, con una tabla oculta para lectores de pantalla. No suma peso al chunk del backoffice.
- **Contacto desde el servidor:** `storeContact.ts` deja de ser la fuente y una sola consulta de TanStack Query alimenta el pie, el contacto y la franja.

## Pruebas

- Dominio: `SalesPeriods` (fin de mes, meses cortos, años bisiestos, medianoche en Argentina), `Variation`, los valores de `StoreSettings` (sobre todo los hosts permitidos de las redes) y los límites de `ActivityEntry`.
- Casos de uso con fakes en memoria, y contract tests por cada puerto nuevo (fake y JDBC).
- RLS con `RlsPolicyTester`: el cliente no ve otros usuarios ni la actividad; el administrador lee clientes; `SYSTEM` solo borra actividad vieja; nadie actualiza la actividad.
- Web slice: `400` de cada parámetro, `401`/`403`, `409` de los datos de la tienda, y que `q` no aparezca en el log.
- Frontend: tests de componentes por cada criterio, más el recorrido de 360 px de RNF-26 en cada pantalla nueva.
- e2e: el administrador entra, ve el resumen, busca un pedido, imprime el remito y cambia los datos de la tienda; la tienda los muestra.

## Riesgos

- **Ampliar lo que ve el administrador (V43):** hoy no puede leer otros usuarios. Se limita a los `CUSTOMER`, solo lectura, y pasa por la revisión de seguridad.
- **Olvidar registrar algún cambio:** la tarea de cierre recorre todos los endpoints de escritura de `/api/admin` y verifica que cada uno tenga su test de actividad.
- **Datos personales que se filtran a la actividad:** se cubre con el test de datos conocidos y con la regla de no guardar notas ni textos libres.
- **El volumen de T-E8-02 y T-E8-03:** son muchos casos de uso chicos. Se reparten en dos tareas por módulo para que cada commit sea revisable.
