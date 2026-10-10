# E6 – Plan técnico

## Pruebas

- **Extremo a extremo:** Playwright en TypeScript, sobre un entorno de prueba con el proveedor de pagos en modo sandbox.
- **Carga:** k6 con los scripts en TypeScript.
- **Rendimiento:** Lighthouse en modo móvil; revisar tamaño de imágenes, caché y carga diferida.
- **Seguridad:** cabeceras (HSTS, CSP, X-Content-Type-Options), límite de intentos en logins, revisión de que ningún log incluya DNI, dirección o email.

## Operación

- **Backups:** `pg_dump` diario más copia del volumen de imágenes a un almacenamiento externo; retención sugerida de 14 días; documentar y probar la restauración.
- **Monitoreo:** chequeo de disponibilidad externo sobre `/api/health` y la portada.
- **Logs:** rotación y nivel adecuado para producción.
- **Runbook:** pasos para desplegar, restaurar un backup, renovar accesos y reiniciar servicios.

## Salida a producción

- Pasar de credenciales de prueba a credenciales reales de MercadoPago (y MODO si está listo) y hacer una compra real de bajo monto.
- Cargar el catálogo (según la opción acordada en el documento de alcance, punto 2.1).
- Plan de salida de Treinta: actualizar enlaces en Instagram y WhatsApp hacia el nuevo dominio.
- Decidir los *previews* al compartir enlaces de producto por Instagram y WhatsApp (etiquetas Open Graph): prerenderizado de las fichas o un pequeño endpoint que las genere.
- Entrega de accesos y documentación al titular; acta de aceptación firmada.

## Riesgos

- Un único servidor: documentar qué hacer si el proveedor tiene una falla.
- Diferencias entre sandbox y producción de los pagos: dejar un día de margen.
