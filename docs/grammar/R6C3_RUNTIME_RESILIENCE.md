# R6C3 — Resiliencia del runtime textual y protección del last-known-good

## Objetivo

El cierre de R6C3 no considera suficiente que Xtext y Langium reconstruyan el mismo modelo. El runtime que mantiene una sesión textual también debe impedir que un resultado antiguo, duplicado de forma ambigua o cancelado sustituya el estado semántico vigente.

## Revisiones de borrador

`TextualModelSessionService` trata `draftRevision` como identidad lógica del contenido que se analiza.

- Una revisión inferior a la almacenada se rechaza como `stale` antes de consultar el snapshot de gramática o iniciar el parser.
- Repetir exactamente la misma revisión con los mismos documentos es una operación idempotente y devuelve el estado ya almacenado sin volver a analizar ni escribir.
- Reutilizar la misma revisión con documentos distintos se rechaza. Una revisión no puede representar dos contenidos diferentes.

La persistencia mantiene además el ETag existente, por lo que una escritura concurrente posterior no puede ser sustituida silenciosamente por una copia anterior.

## Last-known-good

Un borrador inválido nunca sustituye `lastValidSemanticModel` ni `lastValidSourceMap`. Las perspectivas, proyecciones y consumidores que necesiten un modelo estable leen únicamente ese último estado válido mediante `modelForPerspectives`.

El snapshot de gramática queda fijado en la sesión. Los análisis posteriores recuperan `grammarSnapshotId` de la propia sesión, evitando que una activación posterior de otra compilación cambie a mitad de edición el significado de un documento ya abierto.

## Concurrencia y cancelación

Las operaciones de parse están limitadas por `BoundedPermitPool` y `maximumConcurrentParses`. La espera por capacidad también está acotada mediante `permitAcquireTimeoutMillis`.

Si el hilo llamador es interrumpido, la espera de capacidad se convierte en `CancellationException`. El parser neutral comprueba igualmente el estado de interrupción durante el recorrido de documentos, elementos y asignaciones, de modo que una petición cancelada no se promueve como resultado válido.

## Límites y rutas

El runtime conserva límites configurables de número de documentos, bytes por documento, bytes totales, diagnósticos y entradas del source map. Las rutas de `TextualModelSourceDocument` se normalizan a `/`, deben ser relativas y rechazan traversal mediante `../`.

Las estructuras persistidas de sesión realizan copias defensivas de documentos, diagnósticos, source maps y metadatos. Un llamador no puede mutar posteriormente el snapshot almacenado modificando las colecciones que utilizó para construirlo.

## Validación

El gate `verify_r6c3_runtime_resilience.py` comprueba estos contratos y `TextualRuntimeResilienceTests` cubre reintentos idempotentes, colisiones de revisión, revisiones antiguas, cancelación, traversal y copias defensivas.

El launcher acumulativo más reciente es `validate_r6c3_fix127_149.ps1`. Los launchers anteriores se mantienen como wrappers para que los comandos ya usados durante la campaña sigan siendo válidos.
