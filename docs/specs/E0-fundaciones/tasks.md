# E0 – Tareas

Los identificadores son estables: las tareas nuevas se agregan con el siguiente número aunque queden fuera de orden.

## Parte A – Entorno local (se puede hacer ya)

- [ ] **T-E0-01** Crear el repositorio con la estructura del plan y un `CLAUDE.md` inicial que incluya las reglas R-09 a R-11.
- [x] **T-E0-02** Backend: proyecto Spring Boot con Java 21, Actuator, springdoc y manejo de errores `problem+json`.
- [x] **T-E0-03** Base de datos: PostgreSQL local con Docker Compose, `V1__baseline.sql` con Flyway y HikariCP configurado según RNF-21.
- [ ] **T-E0-04** Frontend: proyecto Vite + React + TypeScript, router y tokens de diseño. Solo TypeScript, también en configs y scripts: `tsconfig` con `strict`, `allowJs: false` y `noUncheckedIndexedAccess`; configs en `.ts` (o JSON si la herramienta no lee TS); cliente de API generado con `openapi-typescript`.
- [ ] **T-E0-05** Integración continua: compilar y correr tests de backend y frontend. Incluye un check que falla si `git ls-files` devuelve archivos `.js`, `.jsx`, `.mjs` o `.cjs` fuera de `node_modules` y `dist`.
- [ ] **T-E0-06** Contenedores de producción: Dockerfile del backend con `-Xmx768m`, build del frontend y `Caddyfile` que sobrescribe `X-Forwarded-For`, y que ante un backend que rechaza o no atiende la conexión (con `dial_timeout` acotado) responde `503` con `Retry-After` en `problem+json` (RNF-21). Caddy tiene una IP fija en la red de compose (la de `TRUSTED_PROXY_REGEX`, RNF-22) y el puerto de la API no se publica en el host. Flyway corre como paso aparte (contenedor de una sola ejecución) con las credenciales de `tienda_owner`, que nunca llegan al contenedor de la API, y las contraseñas se pasan como secretos de Docker, no como variables de entorno (RNF-27, RNF-29.10).
- [x] **T-E0-11** Límites de Tomcat según RNF-21, manejo de agotamiento de pools con `503` y test de saturación.
- [x] **T-E0-12** Infraestructura de rate limiting con Bucket4j: filtro, caché con expiración, IP real, `429` en `problem+json`, políticas por propiedades y tests con una ruta de prueba (RNF-22).
- [x] **T-E0-13** Roles de base de datos (`tienda_owner`, `tienda_app`, `tienda_backup`) con privilegios mínimos; Flyway y la aplicación con roles distintos (RNF-27, RNF-28).
- [ ] **T-E0-14** Contexto de seguridad por transacción para RLS, ejecución con rol `SISTEMA` y utilidad de pruebas de políticas (RNF-27).
- [ ] **T-E0-15** Análisis estático contra SQL concatenado y revisión de dependencias en el CI (RNF-28).

## Parte B – Cuando esté contratado el servidor y registrado el dominio

- [ ] **T-E0-07** Servidor: contratar y configurar el servidor, Docker, firewall, acceso por clave SSH.
- [ ] **T-E0-08** DNS y HTTPS: apuntar el dominio y verificar el certificado.
- [ ] **T-E0-09** Script de deploy de un solo comando y primer despliegue del "hola mundo".
- [ ] **T-E0-10** Backup diario de la base (`pg_dump`) a un almacenamiento fuera del servidor.

**Parte A terminada cuando:** el backend y el frontend arrancan con un solo comando en local, la base migra sola, la API responde `503` bajo saturación y `429` al superar un límite, y pasa la integración continua, que también rechaza SQL concatenado.

**Parte B terminada cuando:** la URL pública responde, la base migra sola y un deploy se hace con un solo comando.
