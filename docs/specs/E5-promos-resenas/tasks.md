# E5 – Tareas

- [x] **T-E5-01** Cerrar con el dueño las reglas de cupones automáticos y de reseñas (decisiones pendientes). Cerradas el 2026-10-17: ver CU-23, CU-24, la tabla de decisiones de `00-dominio-y-reglas.md` y los casos de uso en inglés (`docs/use-cases/E5-promotions-and-reviews.md`).
- [x] **T-E5-02** Migraciones: `resena`, ampliación de `cupon` y `cupon_alcance` (el promedio se calcula al leer, ver el plan).
- [x] **T-E5-03** Motor de reglas de descuento y tipos de regla acordados (CU-23).
- [x] **T-E5-04** Integrar el motor en el cálculo de totales del carrito y el checkout (CU-23).
- [x] **T-E5-05** Backoffice: crear cupones automáticos con su regla (CU-11, CU-23).
- [x] **T-E5-06** API y pantalla para dejar y editar reseñas con las reglas de elegibilidad (CU-24).
- [x] **T-E5-07** Mostrar reseñas y promedio en la ficha del producto (CU-24).
- [x] **T-E5-08** Backoffice: moderación de reseñas (CU-25).
- [ ] **T-E5-09** Tests de los criterios de aceptación de CU-23 a CU-25.
- [ ] **T-E5-10** Políticas de Row Level Security de `resena` y sus tests (RNF-27).
- [ ] **T-E5-11** Guía de talles por categoría: edición en el backoffice y acceso desde la ficha (RNF-31.5, *Propuesto*).
- [ ] **T-E5-12** Página de preguntas frecuentes con envíos, plazos, medios de pago y cambios (RNF-31.6).

**Terminado cuando:** un cupón automático se aplica solo al cumplirse su regla y una reseña aprobada aparece en la ficha con el promedio actualizado.
