# Análisis de las 709 etapas no STRONG (V3)

Análisis estático de solo lectura por 12 paquetes de trabajo (≈60 etapas cada uno). Cada decisión es una **hipótesis** hasta que el test PASA y la puerta STRONG la valida. Datos por etapa: `ANALISIS_709_ETAPAS.csv/.json` (incluye la fuerza antes y después de V3); grupos de test: `PLAN_GRUPOS_NEW_TEST.json`.

| Decisión | Etapas | STRONG hoy | MEDIUM | LIMITED | sin test | Significado |
|---|---|---|---|---|---|---|
| NEW_TEST | 316 | 217 | 80 | 13 | 6 | se diseñó un test real nuevo |
| UI_E2E_ONLY | 172 | 0 | 43 | 42 | 87 | solo existe en el diseñador React; STRONG exigiría E2E de navegador contra backend real (el Playwright del repo usa `mockHydraApi`) |
| DERIVED_BY_STEP | 166 | 9 | 55 | 16 | 86 | nodo decisión/artefacto cuyo resultado lo establece el paso adyacente; se mapea solo si ese test afirma la rama |
| BLOCKED_EXTERNAL | 23 | 0 | 10 | 4 | 9 | requiere credenciales, red, toolchain nativa o instalador no disponibles localmente |
| COVERED_LINK | 21 | 0 | 9 | 3 | 9 | un test PASS ya existente lo cubre pero no estaba mapeado a la etapa (re-mapeo honesto pendiente de revisión) |
| NOT_TESTABLE | 10 | 0 | 0 | 0 | 10 | nodo descriptivo sin resultado comprobable |
| NO_BACKEND | 1 | 0 | 0 | 0 | 1 | sin implementación de producción encontrada |

## NEW_TEST que aún no son STRONG (99)

Causas: clases certificadas como MEDIUM con proveedor simulado (repositorios con servidor loopback), hallazgos de producción que impiden la ruta real (update-site, Sirius), grupos L descartados por riesgo (`language-to-published-editor-pipeline`, `g-codegen-compile`) y etapas que el test reclamó solo como indirectas.

- repos-file-transfer-loopback: 11
- update-site-recovery-real-http-lifecycle: 10
- gamification-catalog-policy: 9
- generated-projects-real-process-lifecycle: 9
- release-gate-real-collaborators-attestation: 6
- language-to-published-editor-pipeline: 6
- repos-oauth-loopback: 5
- grammar-draft-analysis-pipeline: 5
- grammar-look-customization-persistence: 5
- t2m-preview-real-grammar: 4
- g-recovery-import-e2e: 4
- native-parity-certify-real-path: 3
- event-model-authoring-persistence: 2
- visual-import-metamodel-association-real: 2
- g-session-event-reward: 2
- g-capability-layer-apply: 2
- transformation-export-parity: 2
- session-event-publication: 1
- integration-profile-delivery-http: 1
- metamodel-generation-gate-real: 1
- runtime-execute-draft: 1
- native-export-real: 1
- validation-rule-definition-rejections: 1
- request-hardening-filter-http: 1
- native-parity-request-policy: 1
- textual-model-session-real-compilation: 1
- g-standalone-dist-e2e: 1
- g-codegen-compile: 1
- g-generated-project-register: 1
