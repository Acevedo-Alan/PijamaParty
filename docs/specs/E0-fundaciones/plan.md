# E0 – Plan técnico

## Estructura del repositorio

```text
/backend      Spring Boot (Java 21)
/frontend     Vite + React + TypeScript
/deploy       docker-compose.yml, Caddyfile, scripts de deploy y backup
/docs/specs   Estas specs
CLAUDE.md     Contexto y convenciones para Claude Code
```

## Decisiones

- **Un solo dominio.** Caddy sirve el frontend compilado en `/`, enruta `/api/*` al backend y `/uploads/*` a un volumen. Así no hay CORS y las cookies son del mismo sitio.
- **Contenedores:** `api`, `db` (PostgreSQL) y `caddy` en un único `docker-compose.yml`.
- **Backend por módulos:** `catalogo`, `carrito`, `cupones`, `pedidos`, `pagos`, `envios`, `usuarios`, `resenas`, cada uno con su paquete.
- **Migraciones:** Flyway, con `V1__baseline.sql`.
- **Errores:** `ProblemDetail` de Spring.
- **API documentada:** springdoc-openapi.
- **Salud:** Spring Boot Actuator con `/api/health`.
- **Frontend:** React Router, estilos con variables CSS (tokens de la paleta cálida/pastel), componentes mobile-first.
- **Perfiles:** `dev` y `prod`; secretos por variables de entorno, nunca en el repositorio.

## Riesgos

- Memoria limitada en el servidor: ajustar la memoria de la JVM (`-Xmx`) desde el principio.
- Primer certificado HTTPS: el DNS debe apuntar al servidor antes de arrancar Caddy.

## Pools y límites (RNF-21)

- HikariCP, Tomcat y PostgreSQL con los valores de `01-requisitos-transversales.md`, definidos en `application.yml` y en `docker-compose.yml`.
- Un manejador de excepciones convierte el agotamiento del pool en `503` con `Retry-After` (5 s, configurable con `store.saturation.retry-after`), tanto dentro de Spring MVC como en errores fuera de él.
- La cola de RNF-21 se implementa como `max-connections` = 140 (40 hilos + 100 en espera dentro de Tomcat); `accept-count` 100 es el backlog del sistema operativo. Cuando todo eso se llena, la conexión se rechaza o se descarta antes de llegar a la aplicación: Caddy, con un `dial_timeout` acotado, convierte ese fallo del backend en `503` con `Retry-After` en `problem+json` (T-E0-06).
- La JVM arranca con `-Xmx768m` desde el Dockerfile.

## Rate limiting (RNF-22)

- Filtro (`OncePerRequestFilter`) que corre antes de la seguridad, con Bucket4j y una caché con expiración por inactividad (por ejemplo Caffeine).
- Políticas configurables por propiedades (ruta, clave, capacidad y reposición), para ajustarlas sin recompilar. Se aplican todas las políticas que coinciden con el request y se rechaza si cualquiera está agotada (por ejemplo login por IP y, aparte, por cuenta: RNF-29.5). Una política cuya clave no se puede resolver no aplica, así que cada ruta lleva además una política por IP como base. Las IPv6 se agrupan por su prefijo /64.
- La IP real se obtiene con `server.forward-headers-strategy=native`; Caddy sobrescribe la cabecera `X-Forwarded-For` en lugar de agregarla.
- Esta etapa deja la infraestructura y una ruta de prueba; las políticas de cada ruta se activan en la etapa que crea el endpoint.

## Roles de base de datos y Row Level Security (RNF-27)

- Script de inicialización que crea `tienda_owner`, `tienda_app` y `tienda_backup`, con privilegios mínimos para la aplicación. Flyway se conecta como `tienda_owner`; la aplicación, como `tienda_app`.
- Un interceptor de transacciones (AOP de Spring) fija el contexto con `set_config('app.rol', ..., true)` y el resto de las variables al iniciar cada transacción. Una anotación o un servicio permite ejecutar un bloque con rol `SISTEMA` (webhooks, correos, vencimiento de reservas, autenticación).
- Utilidad de pruebas para ejecutar consultas con un contexto dado (cliente A, cliente B, invitado, administrador, sistema).
- Las políticas de cada tabla se crean en la etapa que crea la tabla.

## Protección contra SQL injection (RNF-28)

- Regla de análisis estático en el CI (Semgrep o find-sec-bugs) que falla ante SQL concatenado o consultas nativas con valores interpolados.
- Revisión de dependencias con Dependabot o OWASP Dependency-Check.
