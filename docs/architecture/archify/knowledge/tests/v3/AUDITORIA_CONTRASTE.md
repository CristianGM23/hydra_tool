# Contraste de la auditoría independiente con los ficheros oficiales de Archify

Fecha: 2026-10-19. Regla aplicada: prevalecen siempre los resultados verificables del repositorio. Cada afirmación se
comprobó con scripts de solo lectura sobre `knowledge/tests/*.json` y sobre `target/archify-certificacion-final/`
(matriz anterior extraída de `HYDRA_ENTRADA_CERTIFICACION_FUERTE.zip` en un directorio vacío).

## 1. Afirmaciones de la auditoría confirmadas por el repositorio

| Afirmación | Resultado verificado |
|---|---|
| Fuerza de etapas tras V2 | STRONG 271 / MEDIUM 256 / LIMITED 113 / NONE_MAPPED 340 (antes: 5 / 190 / 114 / 671) |
| Comparación sobre las mismas 980 etapas | 405 mejoran, 1 empeora (`project_open_organize.a4`, MEDIUM→LIMITED), 574 igual, 266 nuevas STRONG, 331 siguen sin test |
| V2 ejecutó la suite completa por tarea | Manifiesto V2: 35 tareas, todas `["mvnw.cmd","-B","test"]` sin `-Dtest` → 35 ejecuciones de la suite completa; ninguna tarea tiene `pom.xml` en sus entradas |
| Catálogo | 911 registros: 733 PASS, 125 DISCOVERED, 49 STALE_EVIDENCE, 2 SKIPPED, 1 NOT_EXECUTED, 1 FAIL |
| Procesos sin evidencia | `x_grammar_to_text_model` (15 etapas, solo evidencia indirecta), `gamification_author_policy`, `project_author_areas`, `recovery_author_governance` |
| `integration_native_parity.nat` | `best` NONE_MAPPED con estado `partial`: su test directo `NativeGeneratedProjectAcceptanceTests` está SKIPPED |

## 2. Errores del informe de la campaña V2 (corregidos aquí)

El informe V2 decía «404 mejoradas / 265 FUERTES / 228 antes». Los números reales son **405 mejoradas / 266 nuevas
STRONG / 309 antes** (la cifra de 228 no coincide con los datos de la matriz previa). Los valores reales son los
de la tabla anterior; no se reescribió el manifiesto V2 (`status: FROZEN`).

## 3. Causa raíz de la redundancia Maven

`pom.xml` liga `maven-clean-plugin:clean` (ejecución `hydra-clean-stale-test-classpath`) a la fase `initialize`, de modo
que **cada invocación de Maven borra `build/hydra-maven/surefire-reports`**. El descubrimiento de Archify leía solo ese
directorio volátil, así que cualquier ejecución focalizada convertía todas las demás pruebas en NOT_EXECUTED. Por eso V2
tuvo que correr la suite completa por tarea.

Corrección V3:
1. **Almacén inmutable de evidencia** (`target/archify-v3/evidence/`): los XML de Surefire se copian (solo lectura) justo
   después de cada ejecución y se indexan en `evidence-index.json`.
2. `discover-tests.js` lee ese índice y gana la evidencia más reciente por clase; `build-test-evidence.js` calcula la
   obsolescencia con `start` de la evidencia. Comprobado: con el almacén, catálogo, matriz y caché salen **idénticos**.
3. El runner lanza **una sola invocación Maven focalizada por lote** (`-Dtest=<FQNs>`), no la suite completa.
4. Las tareas con la misma huella (entradas + `pom.xml` + `.mvn` + `mvnw.cmd` + JAVA_HOME) y evidencia íntegra se
   **reutilizan**: las 35 tareas de V2 se adoptaron sin re-ejecutarlas (`capture-baseline` + `adopt`).

## 4. Hallazgos adicionales de este contraste

* **Puerta `gate:readiness:51:backend-spanish-method-documentation` (el único FAIL del catálogo)**: el resultado es del
  11/10 (`documented=10024/10029`). Re-ejecutado hoy, `verify_backend_spanish_method_docs.py` da
  `PASS documented=10029/10029`. El FAIL del catálogo está **obsoleto**; pasará a PASS cuando se regenere
  `release-readiness.json` por el pipeline oficial.
* **STRONG sin ejecución PASS**: 14 registros STRONG no tienen PASS (2 SKIPPED: `NativeGeneratedProjectAcceptanceTests`,
  `LiveRemoteRepositoryRoundTripTests`; 8 scripts DISCOVERED `bd3/bd4/bd5/bd67_real_e2e.py`,
  `ExternalToolProcessRunnerStressHarness.java`, `fix180_real_editor_migration_e2e.py`,
  `HydraProcessTreeTerminationStressHarness.java`, `verify_fix180_generated_model_lifecycle.py`; 4 puertas
  STALE_EVIDENCE). **Ninguna etapa depende solo de esos registros** (verificado con `anomalies.js`), así que no inflan la
  matriz; quedan señalados por `audit-strong` y no se editaron en silencio.
* **El BACKLOG de impedimentos era demasiado pesimista** en tres procesos (verificado por lectura de código):
  `project_author_areas` sí tiene backend (`PUT /api/projects/active/configuration`,
  `LocalDirectoryProjectStorage.configure`, `ProjectCapabilityDependencies.resolve`); la política de gamificación sí tiene
  implementación (`GamificationFeedbackPolicyResolver`, `UserFeedbackPresentationPolicy`); `recovery_author_governance` sí
  tiene backend (`HydraReleaseGateService`, `/api/release-gates`).
* **Mapeo del catálogo sin respaldo en el código**: «guardar renueva la marca de validez» (`metamodel_export_generate.fix`):
  solo `validateStored` escribe el receipt, no el guardado. El test V3 `MetamodelCompleteProjectExportGateIntegrationTests`
  comprueba que guardar deja el receipt idéntico y solo `validation/run` lo renueva.
* **Tests que no probaban lo que su nombre sugiere**: el test existente de enlaces de eventos señalado en el análisis (paquete 1) no demuestra la
  desactivación del enlace (solo que llegan los dos eventos de origen); `MetamodelValidationGateTests` y
  `MetamodelCompleteProjectExportContractTests` mockeaban el servicio canónico y el gate.

## 5. Qué NO se confirmó / límites

* La auditoría no pudo identificar la puerta FAIL; aquí está identificada y es obsoleta (arriba).
* Las 49 evidencias `STALE_EVIDENCE` son arrastre histórico de puertas de release; no se re-ejecutaron (requieren el
  pipeline de release completo).
* `portal-check` mantiene 48 avisos (no errores); no se ha identificado el origen de la diferencia con los 77 de
  `build --check` (son verificaciones distintas).
