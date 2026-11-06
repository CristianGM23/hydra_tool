# Resultados de la fase 2 (certificación incremental V3)

Medido el 2026-10-21 sobre los ficheros oficiales (`test-evidence-matrix.json`, `test-catalog.json`, `test-execution-state.json`) tras ejecutar el runner V3 y sincronizar Archify. Punto de partida: 497 etapas STRONG (instantánea `phase2-baseline-497.json`).

## Matriz exacta por las 19 categorías (antes → después)

Categoría = capacidad del proceso; `cross` = procesos transversales. STRONG(PASS) = etapas cuyo mejor test directo es STRONG **con evidencia PASS vigente** (sin contar evidencia obsoleta).

| Categoría | Etapas | STRONG antes | STRONG después | STRONG(PASS) | Δ | MEDIUM | LIMITED | Sin test | No STRONG |
|---|---|---|---|---|---|---|---|---|---|
| audit | 4 | 1 | 4 | 4 | +3 | 0 | 0 | 0 | 0 |
| cross | 174 | 89 | 138 | 138 | +49 | 15 | 4 | 17 | 36 |
| events | 89 | 59 | 79 | 79 | +20 | 7 | 2 | 1 | 10 |
| external_repositories | 36 | 15 | 17 | 17 | +2 | 17 | 1 | 1 | 19 |
| gamification | 66 | 32 | 60 | 60 | +28 | 5 | 0 | 1 | 6 |
| generated_projects | 15 | 0 | 2 | 2 | +2 | 13 | 0 | 0 | 13 |
| grammar | 100 | 44 | 95 | 95 | +51 | 4 | 0 | 1 | 5 |
| integration | 40 | 17 | 34 | 34 | +17 | 1 | 4 | 1 | 6 |
| metamodel | 52 | 26 | 43 | 43 | +17 | 3 | 3 | 3 | 9 |
| model_editor | 57 | 36 | 50 | 50 | +14 | 3 | 0 | 4 | 7 |
| persistence | 6 | 5 | 6 | 6 | +1 | 0 | 0 | 0 | 0 |
| portability | 23 | 19 | 22 | 22 | +3 | 0 | 0 | 1 | 1 |
| project | 42 | 13 | 29 | 29 | +16 | 6 | 3 | 4 | 13 |
| publication | 43 | 21 | 36 | 36 | +15 | 5 | 2 | 0 | 7 |
| recovery | 36 | 11 | 25 | 25 | +14 | 8 | 3 | 0 | 11 |
| security | 7 | 6 | 7 | 7 | +1 | 0 | 0 | 0 | 0 |
| toolchains | 23 | 16 | 19 | 19 | +3 | 0 | 0 | 4 | 4 |
| transformation | 88 | 44 | 86 | 86 | +42 | 0 | 0 | 2 | 2 |
| validation | 79 | 43 | 75 | 75 | +32 | 1 | 2 | 1 | 4 |
| **TOTAL (19)** | **980** | **497** | **827** | **827** | **+330** | 88 | 24 | 41 | 153 |

Evolución global de las 980 etapas: antes de V2 STRONG 5 → tras V2 271 → inicio de la fase 2 497 → **final 827 STRONG, 88 MEDIUM, 24 LIMITED, 41 sin test**. Procesos con todas sus etapas STRONG: 58.

## Resultados de la campaña (266 tareas del manifiesto, todas PASS)

| Origen | Tareas | Veredicto |
|---|---|---|
| Reutilizadas de V2 (evidencia adoptada, sin re-ejecutar) | 35 | PASS 35 |
| Playwright real con backend real (53 specs + humo) | 54 | PASS 54 |
| Clases JUnit nuevas de V3 (fase 1 y fase 2) | 91 | PASS 91 |
| Regresión de tests existentes sobre código corregido | 86 | PASS 86 |

Registros del catálogo (1059): java PASS 485 (16 descubiertos sin ejecución propia y 2 omitidos), Playwright PASS 57, Vitest PASS 265, puertas de release 72 PASS / 48 con evidencia arrastrada (STALE) / 1 FAIL obsoleto, scripts 95 y familias 18 sin ejecución (no son pruebas ejecutables por el runner).

## Qué se implementó en la fase 2

* **Análisis fresco** de las 483 etapas pendientes sobre la matriz vigente (8 paquetes, sin reutilizar el desglose histórico de 709).
* **129 mapeos honestos** (REMAP/DERIVED) de tests ya PASS a etapas, tras verificación mecánica (test PASS, STRONG, sin externo simulado, línea de aserción existente) y exclusión explícita de 16 casos parciales.
* **unas 36 clases JUnit de backend nuevas (91 clases nuevas de V3 en total menos las 55 de la fase 1)** de la segunda tanda (publicación y release, update-site y recuperación, integración y transformación, gramática, textual, validación y gamificación) y **7 clases de regresión** de defectos corregidos.
* **Playwright real**: entorno propio (navegador real + `frontend/dist` + backend Spring Boot real con almacenamiento temporal), integrado en el runner y en Archify; **53 especificaciones de jornada + 1 de humo, todas PASS**, con oráculo independiente (disco/API) y rechazos.
* **Vitest**: 4 ficheros nuevos y 3 ampliados de regresión del frontend (flujos de eventos, gamificación, normalización de integración y de editores, diálogo de generación de código).
* **Defectos de producción corregidos con regresión**: #2 a #6, #10 a #16 (ronda previa) y #18 a #29 (esta ronda): ver `HALLAZGOS_PRODUCCION.md`.
* **Runner V3 ampliado sin infraestructura paralela**: tareas `playwright` y `vitest` con evidencia inmutable por fichero, `--include-stale`, `sync --restage`, `audit-evidence`, cuarentena válida entre lotes, caché de huellas y `kill` de procesos E2E huérfanos.

## Etapas que siguen sin ser STRONG (153) y por qué

Decisión del análisis fresco para esas etapas:

| Causa | Etapas |
|---|---|
| E2E_REAL_BACKEND: Jornada de navegador real aún no escrita, descartada o solo parcial (arrastre entre puertos, pasos que dependen de proveedores o de la interfaz) | 51 (sin test 9, LIMITED 15, MEDIUM 27) |
| BLOCKED_EXTERNAL: Requieren credenciales/red reales (OAuth, GitHub), toolchain nativa (Maven/JDK/npm con red, `-Dhydra.native.validation=true`) o aprovisionamiento deshabilitado en el entorno E2E | 30 (sin test 6, LIMITED 4, MEDIUM 20) |
| DERIVED_INDIVIDUAL: Nodos decisión/artefacto cuyo test solo prueba una rama o el paso vecino | 18 (sin test 8, LIMITED 1, MEDIUM 9) |
| REMAP_EXISTING: Tests existentes MEDIUM o con repositorio/proveedor simulado o con cobertura parcial | 16 (sin test 4, LIMITED 1, MEDIUM 11) |
| NEW_BACKEND_TEST: Test de backend pendiente o descartado por no poder construir el fixture sin ejecutar (guardado textual con validación compartida) o por coste/riesgo | 16 (sin test 2, LIMITED 1, MEDIUM 13) |
| NOT_TESTABLE: Nodos descriptivos sin resultado comprobable | 11 (sin test 11, LIMITED 0, MEDIUM 0) |
| PRODUCTION_DEFECT: Defecto de producción no corregible sin tocar un control de seguridad (`standalone-web` usa `cmd`/`sh`), o hallazgo documentado pendiente | 11 (sin test 1, LIMITED 2, MEDIUM 8) |

Retenida a propósito: `HydraReleaseGateFiveRealChecksIntegrationTests` queda MEDIUM (`external: mocked`) porque simula la red y el análisis de la instantánea; solo pasará a STRONG cuando ambos sean reales.
Anomalías heredadas sin tocar: 10 registros STRONG sin ejecución PASS (2 omitidos y 8 scripts descubiertos; ninguna etapa depende solo de ellos), la puerta `backend-spanish-method-documentation` en FAIL obsoleto (hoy PASS 10043/10043 al ejecutar el verificador) y 48 puertas con evidencia arrastrada (requieren el pipeline de release oficial).

## Verificaciones finales

`portal-check`: 0 errores (48 avisos preexistentes). `build --check`: 1059 registros, 0 errores, 0 pendientes. Idempotencia de la fusión: IDENTICAL. `verify-universe`: 980 etapas del modelo = 980 en la matriz. `audit-evidence`: 0 problemas (266 PASS ↔ evidencia Surefire/Playwright/Vitest inmutable íntegra). `audit-strong`: 0 STRONG de tipo UNIT/STATIC/con externo simulado/sin justificación. 16 pruebas unitarias del runner OK. Sin commits ni pushes.
