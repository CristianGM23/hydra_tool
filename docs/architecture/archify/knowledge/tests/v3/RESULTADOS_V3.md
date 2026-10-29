# Resultados de la certificación incremental V3

Medido el 2026-10-19 sobre los ficheros oficiales (`test-evidence-matrix.json`, `test-catalog.json`,
`test-execution-state.json`) tras ejecutar el runner V3 y sincronizar Archify.

## Fuerza por etapa (980 etapas)

| | STRONG | MEDIUM | LIMITED | sin test |
|---|---|---|---|---|
| Antes de V2 | 5 | 190 | 114 | 671 |
| Tras V2 (línea base de V3) | 271 | 256 | 113 | 340 |
| **Tras V3** | **497** | 197 | 78 | 208 |

V3 sube 226 etapas a STRONG (99 desde MEDIUM, 36 desde LIMITED, 91 desde sin test), mejora otras 51 a MEDIUM/LIMITED y no
degrada ninguna. Procesos con todas sus etapas STRONG: 13. Registros del catálogo: 970 (112 STRONG, 352 MEDIUM, 506 LIMITED).

## Ejecución

* 90 tareas en el manifiesto: 35 importadas de V2 (reutilizadas, **0 re-ejecuciones**) y 55 clases de test nuevas
  (41 certificadas STRONG, 14 MEDIUM por usar proveedor simulado, hallazgo de producción o cobertura parcial).
* Las 90 tareas figuran en PASS. Ninguna ejecución recorrió la suite completa: cada ciclo fue **una sola invocación
  Maven focalizada** (37 → 14 → 19 → 4 → 1 → 1 clases). Los fallos de las primeras pasadas (14 de 37) se corrigieron y
  solo se re-ejecutó lo que falló.
* `portal-check`: 0 errores (48 avisos preexistentes). `build --check`: 970 registros, 0 errores, 0 pendientes.
  Idempotencia de la fusión: IDENTICAL. 15 pruebas unitarias del runner: OK.

## Qué falta (y por qué)

Quedan 483 etapas no STRONG (análisis completo en `ANALISIS_709_ETAPAS.md`):

* 172 `UI_E2E_ONLY`: exigen E2E de navegador contra el backend real; el Playwright del repositorio solo usa
  `mockHydraApi`. Camino: levantar Spring Boot real en `webServer` y escribir specs sin `page.route` (esfuerzo L).
* 166 `DERIVED_BY_STEP` (nodos decisión/artefacto): se pueden mapear cuando el test adyacente afirma la rama; ya lo
  son 9. Requiere revisar aserción por aserción, no un mapeo masivo.
* 99 `NEW_TEST` aún no STRONG: repositorios remotos con proveedor simulado (MEDIUM), update-site/Sirius (hallazgo de
  producción), `language-to-published-editor-pipeline` y `g-codegen-compile` (descartados por riesgo/coste).
* 23 `BLOCKED_EXTERNAL`, 21 `COVERED_LINK` (re-mapeo de tests PASS existentes, pendiente de revisión humana),
  10 `NOT_TESTABLE`, 1 `NO_BACKEND`.
* Anomalías heredadas sin editar: 14 registros STRONG sin ejecución PASS (ninguna etapa depende solo de ellos) y 49
  `STALE_EVIDENCE` de puertas de release.

Hallazgos de producción: `HALLAZGOS_PRODUCCION.md`. Contraste con la auditoría: `AUDITORIA_CONTRASTE.md`.
