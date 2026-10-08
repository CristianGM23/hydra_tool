# R6C4 — Paridad de capacidades de edición textual

R6C4 continúa desde el cierre validado de R6C3. El objetivo no es declarar que HYDRA embebe los runtimes nativos de Xtext o Langium, sino ofrecer una capa común de interacción sobre el mismo `CanonicalModelDocument`, el mismo `TextualSemanticSourceMap` y una revisión last-known-good fijada. Cuando un host nativo sea externo, la capacidad se declara como tal y nunca se simula su ejecución.

## Fix151 — contrato de capacidades

Introduce `TextualInteractionCapability`, `TextualInteractionExecutionMode` y estados explícitos. El modo neutral de HYDRA queda separado de `PROVIDER_NATIVE_EXTERNAL` y `UNAVAILABLE`.

## Fix152 — índice único de workspace

`TextualWorkspaceIndex` concentra documentos, modelo, elementos y source maps en estructuras inmutables. Todas las capacidades leen el mismo snapshot y evitan índices paralelos divergentes.

## Fix153 — completion semántico

El completion reutiliza IDs, clases, atributos y relaciones del modelo canónico. Filtra por prefijo, ordena de forma estable y limita el número de propuestas.

## Fix154 — hover contextual

El hover utiliza la entrada más específica del source map y combina identidad semántica, clase, binding de metamodelo y regla gramatical.

## Fix155 — go-to-definition

La navegación resuelve primero el destino semántico de una referencia y después busca la declaración `MODEL_ELEMENT`, incluso si está en otro documento.

## Fix156 — find references

Las referencias se obtienen por `targetElementId` y se limitan de forma determinista. La definición puede incluirse como primera localización para una navegación completa.

## Fix157 — rename preview-first

Rename valida `StableIdentifierPolicy`, rechaza colisiones y devuelve `GrammarTextEdit` declarativos para declaración y referencias. No modifica la sesión, no evita ETag y no escribe en repositorios.

## Fix158 — formato determinista

El formateador normaliza LF y elimina únicamente espacios finales, preservando indentación y líneas vacías para no asumir que el whitespace es irrelevante. Devuelve una edición de documento completo únicamente cuando el contenido cambia.

## Fix159 — document symbols

Los símbolos se derivan de `MODEL_ELEMENT`, conservan rangos y clase canónica y siguen el orden textual.

## Fix160 — folding

Solo se generan rangos plegables para spans semánticos multilínea. No se inventan regiones basadas en heurísticas ajenas al source map.

## Fix161 — semantic tokens

Las entradas `MODEL_ELEMENT`, `MODEL_FEATURE` y `MODEL_RELATION` se proyectan respectivamente a tipos neutrales `class`, `property` y `reference`. La UI decide después el estilo visual.

## Fix162 — invalidación incremental

Las referencias entre documentos forman un grafo inverso. Un cambio invalida transitivamente los documentos dependientes y declara `fullReparseRequired` únicamente cuando el cierre cubre todo el workspace.

## Fix163 — compatibilidad de snapshots

La política distingue `EXACT`, `REPARSE_SAFE` y `MIGRATION_REQUIRED`. Cambiar el provider con el mismo contrato semántico exige reparse pero no migración; cambiar gramática o binding semántico fuerza migración.

## Fix164 — límites, cancelación y last-known-good

Las operaciones están acotadas, cooperan con interrupción del hilo y `TextualInteractionFacade` construye el workspace exclusivamente desde `lastValidSemanticModel`, `lastValidSourceMap` y `lastParsedRevision`. Un borrador roto no contamina navegación ni refactorings: el fallback neutral falla de forma cerrada hasta recuperar una revisión válida; el host nativo externo puede seguir ofreciendo capacidades propias sobre texto incompleto.

## Fix165 — REST, OpenAPI, tests y cualificación

La API de sesiones textuales expone capabilities, completion, hover, definition, references, rename preview, format, symbols, folding, semantic tokens, invalidation y snapshot compatibility. El contrato OpenAPI y el cliente TypeScript se regeneran desde los controladores. La cualificación ejecuta el gate estructural, JUnit focal y build frontend sobre la baseline R6C3 ya validada en host.

## Invariantes

R6C4 conserva cuatro reglas: una sola identidad semántica neutral, operaciones preview-first cuando podrían mutar texto, resultados fijados a una revisión válida y ausencia de afirmaciones falsas sobre hosts nativos externos. La capa de interacción no persiste modelos ni aplica ediciones por su cuenta.

## Fix166 — cualificación autosuficiente y recuperación de empaquetado

El launcher de R6C4 ya no exige la presencia de un launcher histórico de R6C3 como prueba de integridad. Esa dependencia era accidental: un workspace podía contener la implementación y la evidencia nativa ya validada, pero no conservar `validate_r6c3_fix127_150.ps1` si la corrección pendiente se había aplicado mediante un launcher focal.

`validate_r6c4_fix151_166.ps1` comprueba directamente los contratos estructurales R6C3 de metamodelo, instancias y resiliencia antes de ejecutar la cualificación R6C4. El launcher `validate_r6c4_fix151_165.ps1` permanece como wrapper compatible. De este modo la integridad se basa en capacidades y contratos reales, no en la existencia de un nombre de script heredado.


## Fix167 — inventario canónico y reparación de source completo

La cualificación deja de depender también de los verificadores históricos `verify_r6c3_*` como archivos individuales. El launcher canónico `validate_r6c4_fix151_167.ps1` valida primero `MANIFEST_SHA256.txt`, que representa el inventario fuente completo, y después vuelve a ejecutar mediante Maven los tests focales de metamodelo, importación, round-trip de instancias, campaña agresiva, resiliencia y capacidades R6C4.

La entrega recomendada para workspaces que hayan recibido overlays parciales es un **overlay completo de source Fix167**. Se extrae sobre la raíz de HYDRA y sobrescribe únicamente rutas fuente canónicas; no elimina `target/`, `frontend/node_modules/`, `data/` ni otros archivos propios que no formen parte del source. Así se evita reconstruir el estado correcto a partir de una cadena de hotfixes intermedios.

## Fix168 — manifiesto canónico y extras preservados del workspace

`MANIFEST_SHA256.txt` describe el source canónico congelado, pero un workspace de desarrollo puede contener trabajo legítimo adicional que todavía no pertenezca a esa entrega, por ejemplo documentación Archify, scripts focales de recuperación o artefactos fuente creados en otra línea de trabajo. Considerar esos extras como corrupción impedía cualificar una copia funcional de HYDRA.

`verify_manifest.py` conserva por defecto el modo **estricto**, que exige igualdad de inventario y se usa para comprobar un ZIP limpio. El nuevo `--allow-extra` mantiene exactamente las garantías importantes para un workspace: toda ruta del manifiesto debe existir y conservar su SHA-256; únicamente se toleran rutas adicionales. El launcher `validate_r6c4_fix151_168.ps1` utiliza este modo y reporta cuántos extras preserva sin borrarlos ni incorporarlos silenciosamente al release.

Esta separación evita dos errores opuestos: no se debilita la reproducibilidad del paquete oficial y tampoco se exige destruir o mover trabajo local válido para ejecutar la cualificación.

## Fix169 — navegación cross-document con cursor adyacente

La navegación neutral deja de asumir que la posición del cursor cae siempre dentro del rango exacto del token de referencia. Monaco y otros clientes pueden solicitar definición o referencias con el cursor en el whitespace inmediatamente anterior al identificador. En ese caso, el rango amplio `MODEL_ELEMENT` del elemento contenedor podía ganar la resolución y devolver la definición local en lugar del `targetElementId` de la relación.

`TextualLanguageFeatureService` incorpora una resolución específica para operaciones de navegación: conserva el comportamiento exacto cuando el cursor ya está sobre `MODEL_RELATION`, pero si existe una relación en la misma línea a como máximo dos offsets y entre el cursor y el token solo hay whitespace, prioriza esa relación. Hover, folding y semantic tokens siguen usando el source map original sin ampliar rangos globales.

La regresión `definitionUsesReferenceTargetWhenCursorIsImmediatelyBeforeCrossDocumentToken` exige que `doc-a` navegue a `doc-b:c2` tanto desde la posición exacta del token como desde la posición inmediatamente anterior, y que ambos resultados sean idénticos.
