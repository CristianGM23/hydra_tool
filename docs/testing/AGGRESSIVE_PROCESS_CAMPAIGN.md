# HYDRA — campaña agresiva de procesos y artefactos

## Objetivo

Esta campaña complementa la suite funcional ordinaria. No considera suficiente que una pantalla renderice o que una llamada devuelva HTTP 200: cada proceso debe demostrar uno o varios efectos observables sobre el producto, como un artefacto materializado y parseable, una modificación semántica coherente, una traza, una transacción reversible o un rechazo explícito de una entrada peligrosa.

La campaña aplica seis clases de evidencia: **ARTIFACT**, **MUTATION**, **ROUND_TRIP**, **RECOVERY**, **SECURITY** e **INVARIANT**.

## Tipos de prueba incluidos

1. **End-to-end de servicio**: encadena planificación, materialización, validación y empaquetado cuando existe ese pipeline.
2. **Round-trip**: exporta/importa o preview/apply/rollback y compara identidad/contenido.
3. **Metamórfica**: repite el mismo proceso o cambia el orden de entradas y exige el mismo resultado canónico.
4. **Adversarial**: traversal, IDs hostiles, duplicados, referencias colgantes, datos parciales, límites y entradas que parecen comandos.
5. **Stress**: cientos o miles de elementos para detectar acumulación, crecimiento no acotado o pérdida de trazabilidad.
6. **Idempotencia/replay**: reejecuta eventos y transformaciones para impedir efectos duplicados.
7. **Sinergia entre capas**: cambia una capa y comprueba que otra queda actualizada o bloqueada de forma explícita.
8. **Aislamiento de tests**: ejecuta la suite completa con órdenes, pools y tamaños de batch distintos; un batch que falla pero pasa individualmente se considera defecto de aislamiento.
9. **Evidencia de artefacto**: comprueba magic bytes, entradas ZIP, hashes, rutas, manifiestos o conteos de traza, según el proceso.

## Cobertura por proceso

| Proceso HYDRA | Prueba agresiva principal | Evidencia final |
|---|---|---|
| Home | hashes válidos/hostiles y navegación repetida | módulo correcto o fallback HOME |
| Metamodel | 80 clases + alto fan-out + eliminación en cascada | cero relaciones colgantes y baseline inmutable |
| Grammars | quick fixes locales + proyecto generado de 201 entradas | ZIP portable, ordenado y determinista |
| Editor definitions | JSON round-trip + corrupción de palette/representation | definición válida o bloqueo exacto |
| Validations | rule/expression/action round-trip + quick-fix corrupto | preflight válido o error accionable |
| Quick fixes | operación completa frente a operación vacía | mutación explícita o rechazo antes de persistir |
| Events | cambio de payload obligatorio y binding incompleto | binding bloqueado hasta mapear el campo |
| EventFlow | 50 resincronizaciones + borrado de nodo | diagrama idempotente sin nodos huérfanos |
| Gamification | evento verificado → 100 awards + replay | puntos/ledger/feedback exactos, sin duplicados |
| Transformations | 500 elementos + replay + guards | 1 destino/trace/binding-trace por elemento y determinismo |
| Textual models | 10.000 elementos incompletos | completion acotada y contextual |
| Model editor | invariantes de identidad/proyección cubiertos por la suite completa y orden aleatorio | ninguna invención semántica o blanking |
| Integrations | mutación de perfil projection-only a runtime | rechazo de capability incompatible |
| Repositories | entradas URL/credenciales y suite completa reordenada | no canal de comandos/secretos |
| Round trip | preview/apply/rollback con IDs y paths hostiles | endpoints separados y codificados |
| Representable imports | mismo change-set en preview y apply | identidad externa preservada |
| Update site | suite completa reordenada + contratos de recovery existentes | evidencias/layers no se materializan silenciosamente |
| Distribution | plan → materialize → validate → package | ZIP no vacío, nombre/hash y ausencia de transient trees |
| Publication | validate → publish → update-check | manifiesto y metadata sin secretos serializados |
| Capabilities | metamodel/grammar + preview/apply/rollback | contextos separados y transacción reversible |
| Compatibility | claves hostiles parecidas a comandos | solo `selectedKeys`, nunca `command` |
| Generated projects | lifecycle stage con project ID hostil | stages declarados, nunca comandos arbitrarios |
| Artifacts | registry/graph/scan separados | inventario server-owned y rescan POST |

La prueba `HydraAggressiveProcessCoverage.test.ts` compara esta matriz con `HYDRA_MODULES`; si aparece un módulo nuevo sin escenario agresivo, la campaña falla. El gate independiente `scripts/testing/verify_aggressive_process_matrix.py` realiza la misma comprobación sin depender de Node/Vitest y forma parte del validador Windows.

## Ejecución

### Windows, campaña completa

```bat
VALIDAR_HYDRA_AGRESIVO_WINDOWS.cmd
```

Antes de ejecutar Maven/Vitest, el script exige cobertura agresiva **23/23** contra el registro actual de módulos. Si se añade un nuevo proceso a HYDRA sin escenario de aceptación agresivo, la campaña se detiene inmediatamente.

### Solo frontend agresivo

```bat
cd frontend
npm.cmd run test:aggressive
```

Por defecto la campaña dedicada comienza con `forks`. Si Vitest informa de un fallo puro de infraestructura al arrancar el worker, HYDRA repite exactamente el mismo conjunto con otro pool. Esa recuperación se aplica también a **cada batch de las tres pasadas completas**: primero se reintenta el mismo batch sin cambiar sus ficheros ni su orden; solo un fallo de producto pasa a aislamiento individual. Un pool que falla al arrancar queda marcado como degradado y se prueba después de los pools todavía sanos durante el resto de esa ejecución. Las aserciones, timeouts de tests, errores de sintaxis/tipo y OOM nunca se reclasifican como infraestructura. Después se ejecutan tres pasadas completas con semillas `1010`, `2718` y `31415`, tamaños de lote `7`, `13` y `23`, y preferencias `forks`, `threads` y `vmThreads`. Pueden modificarse sin perder reproducibilidad:

```bat
set HYDRA_AGGRESSIVE_SEEDS=42,4242,99991
set HYDRA_AGGRESSIVE_BATCH_SIZES=5,11,19
set HYDRA_AGGRESSIVE_POOLS=forks,threads,vmThreads
npm.cmd run test:aggressive
```

El resultado se registra en `frontend/test-results/hydra-aggressive-summary.json` (schema 1.1), incluyendo orden, seed, pool solicitado/real, intentos de recuperación, pools degradados, batch implicado y clasificación del fallo. Los timeouts de worker recuperados se muestran como warnings resumidos; el volcado completo de Vitest se reserva para fallos de producto o agotamiento de todos los pools.

### Solo backend agresivo

```bat
mvnw.cmd -B -Dtest=AggressiveArtifactGenerationTests,AggressiveTransformationRuntimeTests,AggressiveEventGamificationSynergyTests test
```

## Criterio de aceptación

La campaña es PASS únicamente si:

- todos los escenarios dedicados pasan;
- las tres pasadas reordenadas completas pasan;
- ningún batch presenta un fallo dependiente del orden;
- los fallos de worker se distinguen de fallos de producto y recuperan de forma aislada;
- cada generador comprobado produce un artefacto no vacío y verificable;
- los procesos reversibles separan preview/apply/rollback;
- las entradas hostiles no escapan de rutas seguras ni se transforman en comandos;
- replay/idempotencia no duplica estado, trazas, ledger o feedback;
- los gates estructurales, i18n, accesibilidad, manifest y CSS permanecen verdes.
## Determinismo de artefactos de metamodelo

La campaña exige que una selección equivalente de formatos produzca el mismo bundle aunque el cliente cambie el orden de la petición. Los formatos se ordenan por el contrato canónico de `MetamodelExportFormat`, se eliminan duplicados y las entradas ZIP usan metadatos temporales reproducibles. Una inversión `xml/plantuml` frente a `plantuml/xml` debe producir el mismo ZIP byte a byte.

