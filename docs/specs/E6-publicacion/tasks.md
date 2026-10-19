# E6 – Tareas

- [x] **T-E6-01** Pruebas de extremo a extremo de los flujos críticos con Playwright (en TypeScript). Decidido el 2026-10-18: MercadoPago y Resend se reemplazan por stubs locales (los adaptadores reales corren sin cambios) y la compra real en sandbox queda para T-E6-11 y T-E6-15; el pago rechazado conserva la reserva hasta que vence (se corrigió la spec). En el código: el proyecto `e2e/` (Playwright, celular, Chromium) con los seis escenarios de la spec, `E2eStoreApplication` con el perfil `e2e` y un reloj ajustable solo en las fuentes de test, y el job `e2e` de CI. Ver `docs/use-cases/E6-hardening-and-release.md`. Sin migraciones.
- [x] **T-E6-02** Medición con Lighthouse y optimización de imágenes, caché y carga diferida. Decidido el 2026-10-18: Lighthouse en modo celular corre en CI sobre inicio, listado, ficha y checkout contra la imagen de Caddy de producción, y exige "Largest Contentful Paint" menor a 3 segundos (RNF-11) y accesibilidad de 90 o más (RNF-31.4). En el código: caché de un año para `/uploads/*` y `no-cache` para `index.html`; las primeras fotos del listado y la foto principal de la ficha cargan primero; checkout, seguimiento, arrepentimiento, preguntas frecuentes y cuenta se cargan a demanda; se precarga la fuente del texto. El comando es `npm run lighthouse` en `e2e/` y el job `lighthouse` de CI. Ver `docs/use-cases/E6-hardening-and-release.md`. Sin migraciones.
- [x] **T-E6-03** Cabeceras de seguridad, límites de intentos y revisión de logs sin datos personales. Decidido el 2026-10-18: la CSP no necesita los dominios de MercadoPago ni de MODO (se llega a ellos navegando la página entera, algo que la CSP no restringe); los límites de ingreso ya cumplen RNF-22 y RNF-29.5. En el código: todo log, también el de las librerías, pasa por el enmascarado de datos personales (`logback-spring.xml`); se corrigieron los tres errores del ingreso con Google y Facebook, que podían registrar el email; las cabeceras y la ausencia de violaciones de la CSP se prueban contra la imagen de Caddy de producción en `npm run lighthouse`. Ver `docs/use-cases/E6-hardening-and-release.md`. Sin migraciones.
- [ ] **T-E6-04** Backup diario de base e imágenes fuera del servidor y prueba de restauración.
- [ ] **T-E6-05** Monitoreo de disponibilidad con alertas.
- [ ] **T-E6-06** Páginas legales: privacidad, términos, cambios y devoluciones, y uso de cookies y analítica.
- [ ] **T-E6-07** Resolver los previews de enlaces de producto (Open Graph).
- [ ] **T-E6-08** Runbook de operación y guía de uso del backoffice.
- [ ] **T-E6-09** Entorno de prueba para el negocio y envío de la lista de control.
- [ ] **T-E6-10** Período de pruebas del negocio: corrección de errores informados.
- [ ] **T-E6-11** Carga del catálogo y credenciales reales de pago; compra real de prueba.
- [ ] **T-E6-12** Publicación, cambio de enlaces desde Treinta y entrega de accesos.
- [ ] **T-E6-13** Acta de aceptación firmada y cierre del proyecto.
- [ ] **T-E6-14** Prueba de carga con k6 (scripts en TypeScript) según la spec y ajuste de los valores de RNF-21 y RNF-22.
- [ ] **T-E6-15** Verificar en el servidor real la idempotencia, los límites de tasa y la entrega de correos (SPF, DKIM y DMARC en verde).
- [ ] **T-E6-16** Verificar la analítica con Meta Pixel Helper y DebugView de GA4, la política de seguridad de contenido y el impacto en Lighthouse (RNF-25).
- [ ] **T-E6-17** Probar el backoffice en un Android y un iPhone reales (RNF-26).
- [ ] **T-E6-18** Pruebas de acceso cruzado (IDOR) y de Row Level Security con dos clientes, un invitado y el administrador (RNF-27).
- [ ] **T-E6-19** Prueba con sqlmap, revisión de privilegios del rol de la aplicación y de dependencias vulnerables (RNF-28).
- [ ] **T-E6-20** Prueba de rebote y queja con las direcciones de prueba del proveedor de correo (CU-17).
- [ ] **T-E6-21** Validar con abogado y contador los requisitos de RNF-30; cargar datos del vendedor, Data Fiscal y enlace a Defensa del Consumidor en el pie, e IVA contenido en precios.
- [ ] **T-E6-22** Inscribir la base de clientes en el Registro Nacional de Bases de Datos (AAIP) (RNF-30.5). *Trámite del negocio.*
- [ ] **T-E6-23** `sitemap.xml`, `robots.txt`, canónicas, `noindex` en páginas privadas y datos estructurados de producto, con el puntaje de reseñas de CU-24 (RNF-31.1, RNF-31.2).
- [ ] **T-E6-24** Tabla de correspondencia de URLs de Treinta y redirecciones `301` (RNF-31.3).
- [ ] **T-E6-25** Revisión de accesibilidad (WCAG 2.1 AA) y páginas 404, 500 y de mantenimiento (RNF-31.4, RNF-31.7).
- [ ] **T-E6-26** Probar el ingreso con Google y Facebook desde los navegadores integrados de Instagram y Facebook en Android y iPhone (CU-27).

**Terminado cuando:** hay acta de aceptación y la tienda recibe compras reales.
