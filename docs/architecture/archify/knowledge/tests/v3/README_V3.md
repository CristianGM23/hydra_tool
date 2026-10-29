# Certificación incremental V3 de Archify

Ejecutor reutilizable, reanudable y no excluyente: `scripts/archify/v3/hydra_archify_v3.py` (solo biblioteca estándar de
Python). Manifiesto: `MANIFEST_CERTIFICACION_V3.json` (35 tareas importadas de V2 + una tarea por clase de test nueva).

## Garantías

* **No repite lo superado**: una tarea con la misma huella (entradas, `pom.xml`, `.mvn`, `mvnw.cmd`, JAVA_HOME, entorno)
  y evidencia inmutable íntegra se marca `REUSE`.
* **Una sola invocación Maven focalizada por lote** (`-Dtest=...`, `-Dsurefire.failIfNoSpecifiedTests=false`); nunca la
  suite completa. Los XML de Surefire se capturan de inmediato en `target/archify-v3/evidence/` (solo lectura).
* **Logs independientes** por tarea: `target/archify-v3/runs/<runId>/logs/`.
* **No excluyente**: un test que no compila se pone en cuarentena (copia → borrado → reintento → restauración en
  `finally`) solo si es propio de V3 (`quarantinable: true`); un error ajeno aborta el lote. Un fallo no impide que el
  resto del lote se certifique.
* **Sin promoción artificial**: los fragmentos semánticos solo se fusionan si la tarea **pasa**, y las
  reclamaciones STRONG se comprueban mecánicamente (no UNIT/STATIC/SOURCE_INTEGRITY/ARCHITECTURE/UI, sin externo
  simulado, `strengthWhy` obligatorio, al menos una etapa directa). `audit-strong` repasa los invariantes.
* **Sincronización automática de Archify**: tras `run`, el runner encadena `discover-tests.js` → `validate-fragment.js`
  → `build-test-evidence.js --check` → fusión → `portal-check` → re-fusión sin pendientes (idempotencia).
* Seguridad: ejecutables en lista blanca, rechaza `-DskipTests`, `git push`, `npm publish`, `--update-snapshot`…; cwd y
  entradas dentro del repositorio; sin red ni credenciales; bloqueo contra Maven concurrente.

## Comandos

```powershell
python scripts/archify/v3/hydra_archify_v3.py plan            # RUN / REUSE / BLOCKED por tarea, sin ejecutar
python scripts/archify/v3/hydra_archify_v3.py run             # ejecuta lo pendiente y sincroniza Archify
python scripts/archify/v3/hydra_archify_v3.py status          # veredictos registrados
python scripts/archify/v3/hydra_archify_v3.py sync            # solo re-sincroniza fragmentos de tareas PASS
python scripts/archify/v3/hydra_archify_v3.py audit-strong    # invariantes STRONG (target/archify-v3/audit-strong.json)
python scripts/archify/v3/hydra_archify_v3.py capture-baseline --src <dir surefire> --run-id <id> --started <ISO>
python scripts/archify/v3/hydra_archify_v3.py adopt --run-id <id>   # adopta una ejecución previa sin repetirla
python scripts/archify/v3/hydra_archify_v3.py --sandbox selftest    # autoprueba aislada con tests sonda
python scripts/archify/v3/hydra_archify_v3.py restore-quarantine    # restaura ficheros en cuarentena tras un corte
python scripts/archify/v3/gen_v3_tasks.py                           # una tarea por fragmento de fragments/*.json
python -m unittest scripts.archify.v3.tests.test_v3                 # 15 pruebas unitarias del ejecutor
```

Opciones globales (`--manifest`, `--sandbox`) van **antes** del subcomando. Reanudar tras un corte = volver a lanzar
`run`: lo ya superado se reutiliza.

## Cómo añadir cobertura

1. Escribir la clase JUnit (nueva, `src/test/java/...`) con colaboradores y persistencia reales y un oráculo independiente.
2. Escribir su fragmento semántico en `fragments/<Clase>.json` (esquema: `validate-fragment.js`; texto llano, sin
   vocabulario de implementación en campos visibles). Las clases de soporte sin `@Test` viajan en el fragmento de una
   clase hermana.
3. `gen_v3_tasks.py` → `run`. Si pasa, se fusiona; si no, el fragmento queda retenido.

## Límites conocidos

* El estado/evidencia del ejecutor vive en `target/archify-v3/` (ignorado por git): conservar esa carpeta para reanudar.
* Las tareas `vitest/playwright/node/python` se registran en el estado V3 pero aún no alimentan el catálogo de Archify.
* Las puertas de release (`gate:*`) y sus 49 evidencias `STALE_EVIDENCE` requieren el pipeline de release oficial.
* El baseline V2 (`baseline-20261018T2346`) es el único origen de la evidencia de las 35 tareas importadas.

Documentos: `AUDITORIA_CONTRASTE.md`, `ANALISIS_709_ETAPAS.{md,csv,json}`, `PLAN_GRUPOS_NEW_TEST.json`,
`HALLAZGOS_PRODUCCION.md`, `RESULTADOS_V3.md`.

## Ampliaciones de la fase 2 (mismo runner, sin infraestructura paralela)

* Tareas `playwright` (navegador real + `frontend/dist` + backend Spring Boot real con almacenamiento temporal; config `frontend/playwright.real.config.ts`) y `vitest`: veredicto por tarea y evidencia **por fichero** en el almacén inmutable (`evidence/playwright`, `evidence/vitest`), que `discover-tests.js` y `build-test-evidence.js` leen como evidencia de ejecución.
* `run --include-stale` repite solo las tareas cuyo test Archify marca como STALE_EVIDENCE; `sync --restage` vuelve a fusionar los fragmentos de las tareas PASS (refresca hashes de fuente); `audit-evidence` audita cuarentena y correspondencia tarea ↔ evidencia (0 problemas).
* La cuarentena de compilación ya no se pierde entre lotes: un test roto de V3 sigue siendo cuarentenable en los lotes siguientes de la misma ejecución.
* Herramientas de apoyo: `category-matrix.js` (matriz por las 19 categorías), `verify-universe.js` (980 etapas), `phase2-aggregate.js`, `apply-remaps.js`, `gen_v3_tasks.py` (tareas desde fragmentos, incluidas las E2E).
* Documentos: `VERIFICACION_PREVIA_FASE2.md`, `RESULTADOS_FASE2.md`, `HALLAZGOS_PRODUCCION.md`.

## Continuación (fases 1–6 de la auditoría independiente)

* Mismo runner. Cambios en él: `gen_v3_tasks.py` ya no regenera tareas de fragmentos que posee una tarea escrita a mano o de remapeo.
* Vigencia: `audit-currency.js` (cierre transitivo de dependencias frente al inicio de la evidencia), `tree-snapshot.js` (huellas del árbol para trazar cambios de producción).
* Auditoría semántica de los tests que acreditan muchas etapas: veredictos aplicados a los fragmentos (los tests preexistentes sin fragmento V3 reciben un fragmento `remap-audit-*.json`).
* Documentos: `RESULTADOS_FASE3.md` (cierre y entregables), `REQUISITOS_EXTERNOS.md`, `ETAPAS_PENDIENTES_ANALISIS_FASE3.csv`, `ETAPAS_CAMBIOS_FASE3.csv`, `PROCESOS_121_FASE3.csv`, `HALLAZGOS_PRODUCCION.md` (#1 y #30-#45).