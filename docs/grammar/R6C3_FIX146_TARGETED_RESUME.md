# R6C3 Fix146 — reanudación dirigida de la cualificación

## Motivo

La ejecución Windows de Fix145 dejó en PASS todos los bloques anteriores y falló únicamente en `TextualInstanceSemanticRoundTripTests.failsClosedForNonPortableNativeIds`. El fixture intentaba construir un `CanonicalModelElement` con `../evil`, valor rechazado correctamente por la política canónica antes de alcanzar el codec textual.

## Corrección

El test usa ahora `node-1`: es un identificador canónico estable válido para HYDRA, pero queda fuera del subconjunto portable `[A-Za-z_][A-Za-z0-9_]*` exigido por la identidad nativa Xtext/Langium. De este modo el test alcanza la responsabilidad que pretende verificar y debe producir `INSTANCE_ID_NOT_PORTABLE_ID`. No se modifica código de producción.

## Reanudación

`validate_r6c3_fix146_pending.ps1` reutiliza como checkpoints los bloques que ya pasaron en el host del usuario y ejecuta únicamente el método que quedó pendiente. El launcher acumulativo completo se conserva por separado para auditorías futuras.
