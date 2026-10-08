# R6C3 — Round-trip semántico de instancias Xtext/Langium

## Objetivo

La segunda mitad de R6C3 extiende la paridad de metamodelos hasta las instancias `CanonicalModelDocument`. El criterio de éxito no es que Xtext y Langium produzcan texto parecido, sino que una instancia canónica pueda proyectarse, repartirse entre documentos, reconstruirse y volver a compararse sin cambiar raíces, tipos, atributos, contenciones ni referencias.

## Identidad nativa y política fail-closed

Las gramáticas generadas solo emiten `name=ID` cuando el metamodelo declara realmente un atributo string `name`. Por tanto, un round-trip de instancia exacto solo puede usar ese mecanismo cuando el valor canónico de `name` coincide con el ID estable del elemento.

`GeneratedTextualInstanceCodec` no inventa nombres ni IDs. Si falta `name`, o si `element.id` y el valor de `name` divergen, el resultado queda bloqueado mediante `INSTANCE_IDENTITY_REQUIRES_EXTERNAL_PROVIDER` o `INSTANCE_IDENTITY_MISMATCH`. Además, una identidad fuera del subconjunto portable `[A-Za-z_][A-Za-z0-9_]*` se rechaza con `INSTANCE_ID_NOT_PORTABLE_ID`, y una identidad que coincida con una keyword literal de clase, feature, enumeración o booleano se rechaza con `INSTANCE_ID_COLLIDES_WITH_KEYWORD`; HYDRA no presupone un escaping idéntico en Xtext y Langium. Las colisiones entre keywords generadas de clases o features se bloquean mediante `INSTANCE_CLASS_KEYWORD_COLLISION` / `INSTANCE_FEATURE_KEYWORD_COLLISION`. En esos casos debe corregirse el contrato de sintaxis o configurarse un proveedor/política específica del runtime nativo; HYDRA no afirma portabilidad cuando el lexer o el parser serían ambiguos.

## Contención profunda

El codec sigue literalmente la sintaxis de las gramáticas generadas. En la generación actual, el cuantificador de una feature opcional se aplica a su asignación, no a la keyword literal que la precede; por ello el serializer conserva la keyword aunque el valor opcional esté ausente. Esta compatibilidad se contrasta en tests contra los exportadores Xtext y Langium reales de HYDRA.

Las relaciones `CONTAINMENT` se serializan como anidación real de reglas y nunca como IDs planos. La reconstrucción vuelve a materializar relaciones canónicas de contención y calcula las raíces como los elementos de primer nivel del documento, de modo que los hijos no se promueven accidentalmente a `rootElementIds`.

Antes de generar texto, `CanonicalModelSemanticComparator.validate` comprueba:

- IDs de elementos y relaciones sin duplicados;
- clases, atributos y relaciones declarados;
- tipos y multiplicidades;
- fuentes y destinos compatibles;
- unicidad cuando el metamodelo la exige;
- un único contenedor por elemento;
- ausencia de ciclos de contención;
- coincidencia entre raíces canónicas y topología de contención.

## Referencias forward, backward y cíclicas

Las referencias no contenedoras se leen en dos fases. Primero se analizan todos los documentos y se registran destinos pendientes; después se resuelven contra el conjunto global de elementos. Así se admiten referencias hacia elementos declarados más tarde, hacia documentos anteriores o posteriores y ciclos entre referencias no contenedoras sin depender del orden físico de los ficheros.

Los ciclos de contención continúan prohibidos y se rechazan antes de serializar.

## Multiarchivo

`TextualInstanceDocumentStrategy` ofrece:

- `SINGLE_DOCUMENT`: todas las raíces en un único documento;
- `ONE_DOCUMENT_PER_ROOT`: cada raíz y su árbol de contención en un documento independiente.

Una contención nunca cruza documentos: el hijo hereda el documento de su raíz. Las referencias sí pueden cruzarlos y se contabilizan en `crossDocumentReferences`. Para cada cruce se genera un `TextualInstanceDocumentDependency` tipado con origen, destino, relación y `resolutionMode=WORKSPACE_SCOPE`. Las gramáticas actuales no declaran una regla de `import`, por lo que HYDRA no inserta imports ficticios: Xtext/Langium deben resolver esos destinos mediante su índice/scope de workspace. La reimportación reconstruye el mismo plan y una divergencia produce `INSTANCE_DOCUMENT_DEPENDENCY_MISMATCH`. Las rutas generadas son relativas y saneadas; no se reutilizan rutas aportadas por la instancia para evitar traversal.

## Source maps y bindings

Cada reconstrucción produce `TextualSemanticSourceMapEntry` para:

- `MODEL_ELEMENT`;
- `MODEL_FEATURE`;
- `MODEL_RELATION`.

Cada entrada conserva `metamodelElementId`, `grammarRuleId`, documento, rango y metadatos de proveedor. El token de identidad `name=ID` genera también una entrada `MODEL_FEATURE` marcada con `identity=true`, además del rango `MODEL_ELEMENT` del objeto completo. `TextualInstanceRoundTripResult.bindingCoverageComplete()` exige que la trazabilidad de un modelo no vacío no tenga bindings incompletos.

## Equivalencia de instancias

`CanonicalModelSemanticComparator.compare` ignora IDs de instancia de relación regenerados y anotaciones de transporte. Compara:

- `metamodelId`;
- raíces y su orden;
- IDs estables de elementos;
- clase y atributos;
- destinos de cada relación;
- orden de destinos cuando la relación neutral está marcada como `ordered`.

`TextualInstanceRoundTripService.requireLossless` falla de forma cerrada si falla una invariante, la serialización no es nativamente representable, la relectura produce incidencias, falta cobertura de binding o aparece cualquier diferencia semántica.

## Paridad Xtext/Langium

`TextualInstanceProviderParityService` ejecuta el mismo `CanonicalModelDocument` por Xtext y Langium y compara las dos reconstrucciones entre sí. Solo declara `semanticallyAligned` cuando ambos recorridos son lossless y la comparación cruzada también es equivalente.

## Pruebas

Las pruebas focales cubren:

- tres niveles de contención;
- referencias forward/backward;
- ciclos de referencias no contenedoras;
- dos documentos con referencias cruzadas y plan `WORKSPACE_SCOPE`;
- preservación exacta de raíces;
- source maps y bindings;
- paridad Xtext/Langium;
- ausencia de identidad sintética;
- divergencia ID/name;
- IDs no portables, colisiones de identidad con keywords y ambigüedades de features generadas;
- features opcionales sin valor manteniendo la keyword exigida por la gramática;
- rechazo de ciclos de contención.

La campaña agresiva añade 40 raíces repartidas en 40 documentos y un ciclo de 40 referencias cruzadas, ejecutando dos round-trips consecutivos para comprobar determinismo.

## Validación

- Gate de metamodelos: `scripts/validation/verify_r6c3_semantic_roundtrip.py`.
- Gate de instancias: `scripts/validation/verify_r6c3_instance_roundtrip.py`.
- Tests: `TextualInstanceSemanticRoundTripTests` y `TextualInstanceAggressiveRoundTripTests`.
- Launcher acumulativo: `scripts/validation/validate_r6c3_fix127_149.ps1`.
- Los launchers anteriores de R6C3 se conservan como wrappers compatibles hacia el launcher más reciente.

## Frontera entre identificador canónico e ID portable (Fix150)

La política de identificadores de HYDRA y el token `ID` común de Xtext/Langium no son equivalentes. Un identificador puede ser estable y válido dentro del modelo canónico y, aun así, no poder serializarse de forma nativa sin escaping o una política específica del proveedor.

El caso de regresión utiliza `node-1`: `StableIdentifierPolicy` lo acepta como ID estable de elemento, mientras que el subconjunto portable de las gramáticas generadas (`[A-Za-z_][A-Za-z0-9_]*`) lo rechaza por el guion. En esa situación `GeneratedTextualInstanceCodec` debe devolver `INSTANCE_ID_NOT_PORTABLE_ID` y no generar documentos. El test no debe utilizar rutas como `../evil`, porque esas entradas son inválidas en el contrato canónico y se rechazan antes de alcanzar la capa textual.
