# Pijama Party Santa Fe – Specs de la tienda online

Enfoque **spec-driven**: primero se escribe y aprueba la spec, después el plan técnico, después las tareas, y recién entonces se implementa. La implementación se verifica contra los criterios de aceptación de la spec.

Este material desarrolla el *Documento de alcance y requisitos v1.2* (puntos 3 a 5, 8 y 11).

## Flujo de trabajo por etapa

1. **spec.md** – casos de uso (CU) con flujo, reglas y criterios de aceptación (Dado / Cuando / Entonces).
2. **plan.md** – modelo de datos, contrato de API, decisiones técnicas y riesgos.
3. **tasks.md** – tareas pequeñas y ordenadas, cada una enlazada a un CU.
4. **Implementación** – una tarea por vez, con tests que cubren los criterios de aceptación.
5. **Verificación** – se recorren los criterios de la spec; si algo cambia, se actualiza la spec *antes* que el código.

## Etapas

| Etapa | Nombre | Casos de uso | Estimación |
|---|---|---|---|
| E0 | Fundaciones | – (técnica) | 1 sem |
| E1 | Catálogo, búsqueda y carga de productos | CU-01 a CU-07 y CU-26 | 2 sem |
| E2 | Carrito y cupones por código | CU-08 a CU-11 | 1 sem |
| E3 | Checkout, pagos y pedidos | CU-12 a CU-18 y CU-29 | 2 sem |
| E4 | Cuenta de cliente | CU-19 a CU-22, CU-27 y CU-28 | 1 sem |
| E5 | Promociones y reseñas | CU-23 a CU-25 | 1 sem |
| E6 | Endurecimiento y publicación | – (técnica) | 1 sem + 7 días hábiles de pruebas del negocio |

Las estimaciones suman 9 semanas, una más que las 8 semanas de programación del documento de alcance: la búsqueda por texto, la analítica, el backoffice para celular y la seguridad de datos (RLS) se sumaron después. Hay que ajustar el plazo del documento a 9 semanas o absorberlo en E5, que es la etapa que mejor absorbe retrasos.

## Convenciones

- IDs: casos de uso `CU-xx`, requisitos no funcionales `RNF-xx`, tareas `T-Ex-yy`.
- Estado de cada spec en su encabezado: `Borrador` → `Aprobada` → `Implementada`.
- Commits y PRs mencionan el CU o la tarea (`feat(CU-03): ...`).
- Todo cambio de esquema va en una migración Flyway; todo cambio de API actualiza OpenAPI.

## Definición de terminado

**De una tarea:** compila, tests en verde, criterios de aceptación del CU cubiertos por tests, sin datos personales en logs.

**De una etapa:** todos los CU en estado `Implementada`, flujo demostrable en el entorno desplegado, OpenAPI y specs al día.

## Archivos

- `00-dominio-y-reglas.md` – glosario, entidades, reglas de negocio y decisiones.
- `01-requisitos-transversales.md` – idempotencia, pools, rate limiting, correos, acceso del administrador, analítica, backoffice en celular, Row Level Security, SQL injection y seguridad de la aplicación, requisitos legales, SEO y accesibilidad (RNF-20 a RNF-31).
- `E0` … `E6` – una carpeta por etapa con `spec.md`, `plan.md` y `tasks.md`.
- `PROMPTS.md` – plantillas de prompts para Claude Code (en inglés).
