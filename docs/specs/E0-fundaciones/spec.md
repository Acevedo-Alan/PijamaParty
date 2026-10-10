# E0 – Fundaciones

**Estado:** Borrador · **Casos de uso:** ninguno (etapa técnica)

## Objetivo

Dejar el esqueleto del sistema desplegado en el servidor real, para detectar problemas de infraestructura al principio y no al final.

## Requisitos no funcionales

- **RNF-01** El sitio se sirve por HTTPS en el dominio .com.ar.
- **RNF-02** Un deploy completo se hace con un solo comando reproducible.
- **RNF-03** El esquema de base de datos se versiona con migraciones y se aplica solo al arrancar.
- **RNF-04** Los logs no contienen datos personales.
- **RNF-05** Los errores de la API usan un formato único (`application/problem+json`).
- **RNF-06** Zona horaria y moneda según las reglas R-01 y R-07.
- **RNF-07** Pools de conexiones, hilos y timeouts según RNF-21 (`01-requisitos-transversales.md`).
- **RNF-08** Rate limiting en la aplicación según RNF-22.

## Criterios de aceptación

- Dado el servidor configurado, cuando se abre el dominio en el celular, entonces carga la página inicial por HTTPS.
- Dado un cambio en `main`, cuando termina la integración continua, entonces compila y pasan los tests.
- Dada una base vacía, cuando arranca el backend, entonces las migraciones crean el esquema sin intervención.
- Dado un request inválido a la API, cuando responde con error, entonces el cuerpo respeta `problem+json`.
- Dado el pool de conexiones agotado, cuando llega un request que necesita la base, entonces la API responde `503` con `Retry-After` y no queda colgada.
- Dado un cliente que supera el límite de una ruta protegida, cuando envía otro request, entonces recibe `429` con `Retry-After` en `problem+json`.
- Dado un request con cabecera de IP falsificada desde fuera del proxy, cuando llega, entonces el límite se aplica a la IP real de la conexión.
- Dada una consulta sin contexto de seguridad sobre una tabla protegida, cuando se ejecuta, entonces no devuelve filas (RNF-27).
- Dado el rol de la aplicación, cuando se verifican sus atributos, entonces no es dueño de las tablas ni tiene `BYPASSRLS`.
- Dado código con SQL concatenado, cuando corre el CI, entonces falla (RNF-28).
