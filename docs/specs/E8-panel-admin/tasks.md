# E8 – Tareas (fase E8a)

Las tareas de E8b y E8c se agregan cuando empiece cada fase.

- [x] **T-E8-01** Módulo `activity`: migración V41 (`activity_entry` con RLS), `ActivityLog` en `activity.api` (dentro de la transacción del cambio), `ListActivity`, tarea diaria de conservación de 2 años, endpoint `GET /api/admin/activity` y pantalla "Actividad". Actualiza ARCHITECTURE.md (CU-37, D-E8-11).
- [ ] **T-E8-02** Registrar actividad en catálogo y contenido: categorías, productos, variantes, fotos, guía de talles y portada (CU-37 sobre CU-05, CU-06, CU-32 y RNF-31.5). Crea `ActivityPersonalDataTest`, que corre cada acción registrada con datos personales conocidos y falla si alguno aparece.
- [ ] **T-E8-03** Registrar actividad en el resto: stock, pedidos, arrepentimientos, cupones, reseñas, envíos e ingreso del administrador (CU-37 sobre CU-04, CU-07, CU-11, CU-12, CU-18, CU-23, CU-25 y CU-29), con sus acciones sumadas a `ActivityPersonalDataTest`.
- [ ] **T-E8-04** Módulo `settings`: migración V42, agregado `StoreSettings`, endpoints público y del backoffice, pantalla "Datos de la tienda"; la tienda lee contacto, redes y franja de anuncios del servidor en lugar de `storeContact.ts`, y Facebook se suma al pie (CU-36, D-E8-03).
- [ ] **T-E8-05** Menú agrupado del backoffice, panel del menú en el celular y número de pedidos pagados (`GET /api/admin/dashboard/counts`) (navegación de E8a).
- [ ] **T-E8-06** Búsqueda de pedidos por número, comprador y fechas (cambio a CU-18), con el índice `orders (created_at DESC)` en su propia migración.
- [ ] **T-E8-07** Remito para imprimir desde el detalle del pedido (cambio a CU-18, D-E8-10).
- [ ] **T-E8-08** Búsqueda y filtros de productos: texto, SKU, categoría, estado y stock (cambio a CU-06).
- [ ] **T-E8-09** Pantalla "Stock" con filtros y ajuste rápido, y `catalog.api.LowStockVariants` (cambio a CU-07).
- [ ] **T-E8-10** Clientes: política de RLS de V43 (con revisión de seguridad), lista con búsqueda y filtro por fecha de alta, pantalla "Clientes" (CU-34).
- [ ] **T-E8-11** Ficha del cliente: datos, direcciones, pedidos, compras, total gastado y registro de la consulta (CU-34).
- [ ] **T-E8-12** Resumen, backend: `SalesPeriods`, lecturas de ventas, pendientes, más vendidos, stock bajo y totales, y `GET /api/admin/dashboard` (CU-33).
- [ ] **T-E8-13** Resumen, pantalla: bloques, gráfico de días y meses en SVG, página inicial de `/admin` y llegada después de ingresar (CU-33).
- [ ] **T-E8-14** Cierre: verificar que cada endpoint de escritura de `/api/admin` registra actividad, e2e del backoffice, recorrido de 360 px de todas las pantallas nuevas y revisiones de arquitectura y seguridad.

**Orden:** T-E8-01 primero, porque todo lo que escribe registra actividad. Después T-E8-02 y T-E8-03 (los casos que ya existen) y T-E8-04 (el umbral lo leen T-E8-08, T-E8-09 y T-E8-12). T-E8-05 va antes de las pantallas nuevas, que se suman al menú. T-E8-09 va antes de T-E8-12 (el resumen usa `LowStockVariants`), y T-E8-10 antes de T-E8-11 y T-E8-12 (los conteos de clientes necesitan V43). T-E8-06 y T-E8-07 no dependen de nada más que de T-E8-01.

**Terminado cuando:** al ingresar se ve el resumen con números correctos contra pedidos de prueba, cualquier pedido, producto, variante o cliente se encuentra con una búsqueda, los datos de contacto se cambian sin deploy, cada cambio del backoffice aparece en "Actividad" sin datos personales, y todas las pantallas funcionan desde 360 px.
