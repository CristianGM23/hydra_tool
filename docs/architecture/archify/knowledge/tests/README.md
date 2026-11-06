# Almacén de evidencia de pruebas de HYDRA

Registra **qué demuestra realmente cada prueba**, no solo qué ficheros de prueba existen. Es evidencia funcional (qué comportamientos y etapas de proceso tienen evidencia automatizada), **no** cobertura de líneas ni una puntuación de calidad.

Trazabilidad: `capa → capacidad → proceso → etapa → implementación → prueba → comportamiento verificado → resultado demostrado → limitaciones` (y a la inversa). Los ids de capa, capacidad, proceso y etapa son los canónicos del portal (`hydra-model-*.js`); la única adición es la capa transversal `quality` (puertas de release, arquitectura, integridad de fuentes, i18n, accesibilidad, rendimiento).

## Ficheros

| Fichero | Contenido | Quién lo escribe |
|---|---|---|
| `test-catalog.json` | registros normalizados y estables: tipo técnico, capa primaria/secundarias, capacidades, procesos y etapas (`direct`/`indirect`), componentes de producción, qué ejecuta/afirma, **comportamiento verificado**, regla funcional, escenario, resultado, garantía, **limitaciones**, fuerza, sistemas externos reales/simulados, casos notables, fuentes. **Sin estado de ejecución.** | análisis semántico (lectura de aserciones) + `build-test-evidence.js` |
| `test-execution-state.json` | último estado fiablemente conocido por prueba: `PASS / FAIL / SKIPPED / NOT_EXECUTED / STALE_EVIDENCE / DISCOVERED`, origen, marca de tiempo, granularidad, duración, casos, entorno, motivo de obsolescencia | derivado de los artefactos locales (`build/hydra-maven/surefire-reports`, `target/release/*.json`) |
| `test-evidence-matrix.json` | agregados capa × capacidad × proceso × etapa (ids de prueba, estados, fuerza) | derivado; no editar a mano |
| `test-gaps.json` | áreas sin evidencia directa, evidencia débil u obsoleta, pruebas que fallan o no se ejecutaron, simuladas, ambiguas | derivado |
| `../../hydra-tests-data.js` | los mismos datos para el portal (`window.HYDRA_TESTS`) | generado |
| `capabilities-cache.json` | referencias compactas `testEvidence` / `processStageEvidence` por capacidad (sin duplicar texto de pruebas) | `build-test-evidence.js` |
| `reconciliation-checkpoint.json` | auditoría persistente, prueba a prueba, de la reconciliación exhaustiva de evidencia huérfana: decisión (`linked`/`rejected`/`deferred`) y motivo para cada prueba revisada, para no repetir el análisis | reconciliación manual asistida (ver historial de decisiones) |
| `strong-coverage-closure-plan.md` | plan de cierre hacia FUERTE para las 975 etapas (de 980) que hoy no son FUERTE: estado actual, por qué no llega a FUERTE según el rubro, y qué prueba concreta (componente real, tipo, fichero) haría falta, etapa por etapa y agrupado donde el hueco se repite | generado a partir de `test-evidence-matrix.json` + `test-catalog.json` + el modelo canónico; léase antes de escribir una prueba nueva para no duplicar lo ya enlazado |

No se guardan logs, informes de Surefire ni extractos grandes de código.

## Estados (nunca PASS por existir)
- **PASS / FAIL**: una ejecución real registrada terminó bien / mal (JUnit por caso; Vitest y Playwright a nivel de paso de suite; puertas de calificación por paso).
- **SKIPPED**: la ejecución omitió todos los casos a propósito (p. ej. pruebas condicionadas por variable de entorno).
- **NOT_EXECUTED**: existe pero quedó fuera de la última ejecución registrada.
- **STALE_EVIDENCE**: pasó, pero la prueba o el código de producción que ejercita cambió **después** de que empezara esa ejecución (commit posterior o cambio sin commitear), o la evidencia es "carry-forward" / anterior a cambios del producto.
- **DISCOVERED**: existe y no hay evidencia fiable de ejecución asociada (p. ej. scripts históricos, clases de apoyo sin casos).

## Fuerza de la evidencia (no es una nota de calidad)
`STRONG` (extremo a extremo / integración / runtime reales), `MEDIUM` (componente o comportamiento con colaboradores reales, MockMvc, UI con API simulada), `LIMITED` (unitaria, estática o de esquema), `NONE_MAPPED` (sin evidencia directa mapeada). Una etapa solo está **verificada** (✓) con al menos una prueba **directa** en PASS y ninguna directa en FAIL; evidencia solo indirecta, obsoleta o no correcta es parcial (◐/⚠); sin pruebas, ○ (no implica que falle).

## Herramientas
- `discover-tests.js` — descubrimiento determinista + extracción de la última evidencia de ejecución local; `--changed` lista pruebas nuevas / modificadas / eliminadas respecto al catálogo (actualización incremental).
- `ids.js` — índice de ids canónicos (capas, capacidades, procesos, etapas) para los analistas.
- `validate-fragment.js` — esquema de los registros semánticos (ambos idiomas, ids existentes, rutas y líneas reales, sin la palabra «contrato» como explicación llana, sin estado de ejecución).
- `build-test-evidence.js` — fusiona fragmentos semánticos con la evidencia de ejecución, recalcula estados/matriz/huecos, regenera `hydra-tests-data.js` y la caché. Tras regenerar `capabilities-cache.json` hay que volver a ejecutarlo.

## Cómo se actualiza (incremental)
1. `node knowledge/tests/discover-tests.js --changed` → pruebas nuevas/modificadas/eliminadas.
2. Para cada una, leer sus aserciones y escribir su registro semántico (fragmento JSON validado con `validate-fragment.js`, en un directorio temporal fuera de git, p. ej. `knowledge/tests/.fragments_pending/`); el resto conserva sus campos mientras no cambie su `sourceHash`. Borrar el directorio de fragmentos una vez fusionado (su contenido ya queda en `test-catalog.json`).
3. `node knowledge/tests/build-test-evidence.js --fragments <dir>` (sin `--fragments` solo recalcula estados con la ejecución más reciente).
4. `node knowledge/portal-check.js` y `portal-browser-check.js`.

Los estados cambian solos al aparecer nuevos informes locales (nueva ejecución de Maven/readiness): basta volver a ejecutar el paso 3.

## Evidencia de megacampañas (Fix186/187/188 y sucesivas) — entrada de solo lectura

`../integrations/campaign-evidence/*.json` (exportadas por `HYDRA_ARCHIFY_MEGABRIDGE_V1`/autopilotos) y
`../integrations/remote-evidence/*.json` son **evidencia externa de solo lectura**, no un catálogo paralelo.
El script `archify/reconciliar_archify.py` de cada paquete de campaña las cruza contra este almacén
(`test-execution-state.json`/`test-evidence-matrix.json`/`test-gaps.json`) y escribe un informe **aparte**
en `target/archify-campaign/archify-reconciliacion-*.json`, sin tocar nunca `knowledge/tests/*`
("`archifyStatusUnchanged: true`" en cada registro — la autoridad de cobertura sigue siendo este almacén).

Uso correcto de ese informe: léelo para decidir **qué pruebas merecen revisión** (campo `needsAttention`,
sección `campaignIssues`) y qué procesos siguen con etapas abiertas (`processes[].none/partial/stale`); después
sigue el flujo normal de 4 pasos de arriba (reanalizar sólo lo señalado, nunca importar sus resultados como
`PASS` directamente). No crear un segundo almacén de evidencia ni mover ese informe a `knowledge/`.

## Procedimiento de sincronización incremental permanente (campañas futuras)

Reutiliza siempre las 3 herramientas existentes (`discover-tests.js`, `build-test-evidence.js`, `validate-fragment.js`,
`portal-check.js`); esto no es un sistema nuevo, es la forma correcta y ya probada de ejecutarlas tras una megacampaña
o una `mvnw clean verify`/`npm test` nueva.

1. **Resolver estado dinámicamente**: `git rev-parse HEAD`, `git status --porcelain`, comparar con `knowledge/baseline.json`.
   Nunca asumir el HEAD de una conversación anterior.
2. **Detectar evidencia nueva, dos señales independientes** (una sola no basta):
   - `node knowledge/tests/discover-tests.js --changed` → nuevas/modificadas/eliminadas **a nivel de catálogo**.
   - Comparar `build/hydra-maven/surefire-reports` (fecha del fichero más reciente) y el `newest`/`start` que reportaría
     un `discover-tests.js --out <tmp>` fresco contra `test-execution-state.json.sources[0].newest`: si hay una ejecución
     local más nueva que la registrada, **aunque el catálogo no tenga altas/bajas**, hay evidencia de ejecución nueva que
     sincronizar (esto es exactamente lo que pasó con S088: 368 clases ya catalogadas llevaban una fecha de ejecución
     vieja mientras el disco tenía una `mvnw clean verify` más reciente).
3. **Distinguir "añadido real" de "ya cubierto por una familia/gate"** antes de decidir si hace falta analizar algo nuevo
   (evita contaminar el catálogo con registros `PENDING` de scripts ya agrupados):
   ```js
   // ids que discover-tests.js --changed marca como "added" pero que ya son miembros de una family:* existente
   // o de un gate ya mapeado NO necesitan fragmento nuevo: build-test-evidence.js los pliega solo.
   const familyMembers = new Set();
   catalog.tests.forEach(t => { if (t.id.startsWith('family:')) (t.members||[]).forEach(m => familyMembers.add(m)); });
   const genuinelyNew = addedIds.filter(id => !familyMembers.has(id) && !gateScriptIds.has(id));
   ```
   Si `genuinelyNew` queda vacío, el `discover-tests.js --changed` “ruidoso” es inofensivo: se puede regenerar completo
   sin miedo a crear `PENDING` (confírmalo con `build-test-evidence.js --discovery <fresco> --check` → `0 pending`
   antes de escribir de verdad).
4. **Decidir completo vs. parcial** (regla simple, ya acorde a cómo está hecha la herramienta):
   - Si `genuinelyNew` está vacío y no hay `modifiedIds` → regenerar completo es seguro y barato:
     `node knowledge/tests/build-test-evidence.js --discovery <fresco>` (recalcula TODO el catálogo/matriz/huecos/portal
     desde JSON; no hay modo parcial más barato porque la herramienta ya es una recomputación determinista de coste bajo).
   - Si hay `genuinelyNew`/`modifiedIds`: escribe antes sus fragmentos semánticos reales (leyendo el código, nunca
     adivinando) en `knowledge/tests/.fragments_pending/`, valida con `validate-fragment.js`, y solo entonces
     `build-test-evidence.js --fragments <dir>`.
   - Si una clase **no aparece nunca** en el discovery pese a existir en `src/test/java` (comprobado con
     `grep`/`Glob` directo), sospecha de un bug de `walk()` en `discover-tests.js` (p. ej. el que excluía
     cualquier carpeta llamada `target`, corregido en esta sesión) antes de asumir que falta analizarla.
5. **Megacampañas/evidencia externa** (`integrations/campaign-evidence/*.json`, `integrations/strong-evidence/*.json`):
   tratar como **disparador**, nunca como fuente de verdad directa. Un grupo de campaña en `PASS` solo sirve para decidir
   *qué volver a comprobar localmente*; el estado real sale siempre de `build/hydra-maven/surefire-reports` u otra
   evidencia local tal como hacen los pasos 1-4. Nunca copiar un booleano de un JSON de campaña a
   `test-execution-state.json`.
6. **Prueba de idempotencia (obligatoria tras cualquier regeneración real)**: repetir el mismo `build-test-evidence.js
   --discovery <mismo fichero>` una segunda vez y comparar los ficheros de salida byte a byte (`git diff` si están
   versionados, o un hash si no). Debe salir **sin diferencias**. Si cambia algo en la segunda pasada, hay una fuente
   de no-determinismo que investigar antes de confiar en el mecanismo.
7. **Alcance de la regeneración de vistas**: `build-test-evidence.js` ya reescribe en una sola pasada
   `test-catalog.json` + `test-execution-state.json` + `test-evidence-matrix.json` + `test-gaps.json` +
   `hydra-tests-data.js` + `capabilities-cache.json`; no hay (ni hace falta) un modo "solo estas etapas" porque
   recalcular todo desde el JSON ya cargado es barato. Lo que sí debe acotarse es el **análisis humano/semántico**
   (paso 4), no la recomputación mecánica.
8. **Trazabilidad**: cada fragmento nuevo debe llevar `src: [{path,line}]` real, `procs: [{id,stages,match}]` solo
   donde el test demuestre justificadamente esa etapa (nunca por coincidencia de nombre de clase: dos tests pueden
   citar la misma clase de producción en `prod` por motivos distintos — verifica qué aserción concreta corresponde
   al nodo antes de enlazar), y `strength`/`strengthWhy` consistentes con el tipo real de prueba.
9. **Cierre**: `node knowledge/portal-check.js` (debe dar 0 errores) y, si el tiempo lo permite,
   `node knowledge/portal-browser-check.js --quick`. Si algún nivel FUERTE/DÉBIL no mejora tras sincronizar evidencia
   nueva, documentar expresamente por qué (etapa sin prueba directa, proceso sin modelar todavía, o evidencia
   correctamente descartada por no demostrar lo que el nodo describe) en vez de dar la tarea por cerrada solo porque
   los validadores pasen.

## Certificación incremental V3 (evidencia inmutable + ejecutor reanudable)

* `discover-tests.js` lee además el almacén inmutable `target/archify-v3/evidence/evidence-index.json`: la evidencia más
  reciente por clase gana (almacén frente a `build/hydra-maven/surefire-reports`, que `maven-clean-plugin` borra en cada
  invocación). `build-test-evidence.js` usa el `start` de esa evidencia para la obsolescencia.
* Ejecutor: `python scripts/archify/v3/hydra_archify_v3.py plan|run|sync|status|audit-strong`. Manifiesto, análisis de las
  709 etapas, hallazgos y resultados en `knowledge/tests/v3/` (ver `v3/README_V3.md`).
* Los fragmentos nuevos viven en `v3/fragments/` y solo se fusionan cuando su tarea PASA; las reclamaciones STRONG se
  verifican mecánicamente (`audit-strong`).