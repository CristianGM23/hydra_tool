# R6C3 — Paridad profunda Xtext/Langium y round-trip semántico

## Objetivo

R6C3 hace explícito que HYDRA compara significado, no texto ni identificadores regenerados. La fuente de verdad continúa siendo el metamodelo neutral; Xtext y Langium son proyecciones tecnológicas del mismo contrato.

## Contrato semántico

`MetamodelSemanticComparator` compara clases, clase abstracta/concreta, atributos, tipos, multiplicidades, enumeraciones, relaciones, contención/referencia y root explícito. Los ids de importación, líneas, provenance y anotaciones de parser no forman parte de la identidad semántica.

Los resultados se clasifican como `EXACT`, `HIGH`, `APPROXIMATE`, `LOSSY` o `UNAVAILABLE`. `requireLossless` y `requireSemanticParity` fallan de forma cerrada cuando la reconstrucción pierde significado estructural.

## Reglas de transporte

`Model`/`Element` y sus nombres libres de colisión siguen pudiendo existir como envoltorios de transporte, pero se marcan mediante `HYDRA_TRANSPORT_RULE` y se eliminan antes de inferir el metamodelo. Nunca se convierten en clases del dominio.

El root del dominio se conserva mediante `HYDRA_SEMANTIC_ROOT`. Tras retirar el wrapper, el importador restaura el root/entry semántico original sin depender de qué regla aparezca primero en el fichero.

## Atributo name

HYDRA no sintetiza `name=ID`. Xtext ya no inserta tampoco un `ID` anónimo para clases que no declaran un atributo `name`. `name=ID` solo se emite cuando el metamodelo contiene realmente un atributo string denominado `name`.

Una referencia hacia un tipo sin `name` se conserva como referencia neutral, pero `TextualSemanticCapabilityAnalyzer` declara `REFERENCE_SCOPE_REQUIRES_EXTERNAL_PROVIDER`: el runtime nativo necesita un scope/name provider explícito y HYDRA no falsifica la disponibilidad añadiendo un atributo inexistente.

## Contenciones y referencias

Las relaciones `CONTAINMENT` se generan como anidación de reglas. Las relaciones `REFERENCE` se generan como cross-references. El comparador considera un cambio entre ambas categorías como pérdida semántica.

## Paridad cruzada

`TextualProviderParityService` ejecuta:

`MetaModel -> Xtext -> reimportación -> MetaModel'`

`MetaModel -> Langium -> reimportación -> MetaModel''`

Después comprueba fuente↔Xtext, fuente↔Langium y Xtext↔Langium. Solo declara paridad cuando las tres comparaciones son semánticamente compatibles.

## Validación

- Gate estructural permanente: `scripts/validation/verify_r6c3_semantic_roundtrip.py`.
- Tests JUnit focales: `TextualMetamodelSemanticRoundTripTests` y `TextualGrammarImportRegressionTests`.
- Gate Windows acumulativo actual: `scripts/validation/validate_r6c3_fix127_145.ps1`; los launchers anteriores se conservan como wrappers compatibles.
- El gate acumulativo conserva la fase `R6C2-INHERITED-FIX127-132` y ejecuta directamente sus checks sin depender de un launcher heredado; a continuación valida metamodelos e instancias de R6C3 hasta Fix145.
- Un fallo heredado de R6C2 y una regresión introducida por R6C3 quedan así diferenciados en la misma cualificación.
- La validación nativa Maven requiere el toolchain local del proyecto; no debe etiquetarse PASS si el wrapper no puede obtener Maven.


## Extensión a instancias

Fix140–145 extienden este contrato desde `MetaModel` a `CanonicalModelDocument`. La especificación completa está en `docs/grammar/R6C3_INSTANCE_SEMANTIC_ROUNDTRIP.md`.
