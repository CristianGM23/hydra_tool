# HYDRA Transformation Layer

## Estado

Este documento fija la arquitectura acordada de la capa de transformaciones y separa las decisiones permanentes del alcance de cada corte de implementación. La fuente de verdad es el modelo canónico de HYDRA; ningún lenguaje externo constituye la representación interna.

## Principios

1. **Neutralidad tecnológica.** ATL, ETL, Java, Xtend, Acceleo, EGL y futuras tecnologías son adaptadores de ejecución/exportación. No definen el modelo interno.
2. **Un único documento canónico.** El flujo guiado y el editor avanzado manipulan exactamente el mismo `TransformationDefinitionDocument`; cambiar de modo no convierte ni duplica información.
3. **Semántica de referencia propia.** HYDRA compila la definición canónica a un plan y dispone de un runtime neutral. Los exportadores deberán demostrar paridad semántica frente a ese runtime.
4. **Trazabilidad first-class.** Cada elemento destino conserva la correspondencia source-rule-target; las relaciones pueden resolver equivalentes mediante esa traza.
5. **Fail closed.** Una transformación con errores estructurales no se persiste como ejecutable ni se compila. Los avisos no se silencian.
6. **IDs estables.** Las referencias usan IDs canónicos, no nombres visibles. Los endpoints conservan huellas del metamodelo para detectar evolución.
7. **Progressive disclosure.** La experiencia básica cubre correspondencias directas y condiciones guiadas; las expresiones completas permanecen accesibles sin sustituir el documento.
8. **Acoplamiento mínimo.** Eventos, logs y gamificación podrán consumir eventos de la transformación, pero el runtime de transformaciones no dependerá de esas capas.

## Modelo canónico

`TransformationDefinitionDocument` contiene:

- `schemaVersion`, `id`, `name`, `kind`.
- `sources[]` y `targets[]` con `metamodelId`, fingerprint, alias y rol.
- `rules[]`.
- `executionPolicy`.
- `tracePolicy`.
- `annotations` neutrales.

Aunque la primera UI M2M expone un source y un target, el contrato usa colecciones para no bloquear escenarios multi-modelo futuros.

### Regla

Una `TransformationRule` define:

- source endpoint y source type;
- alias source;
- guard opcional;
- uno o más target patterns;
- bindings;
- metadatos.

### Bindings

Tipos reservados/implementados:

- `DIRECT`: copia una propiedad source a una propiedad target;
- `CONSTANT`: escribe un literal;
- `EXPRESSION`: calcula un valor mediante el AST neutral de expresiones;
- `TRACE_EQUIVALENT`: navega una relación source y enlaza con los targets creados desde los elementos relacionados.

## UI M2M

La vista principal tiene tres zonas:

`SOURCE TREE -> MAPPINGS/RULES -> TARGET TREE`

Los árboles reutilizan `MetamodelStructureTree`. La condición reutiliza `GuidedConditionBuilder` y `ExpressionBuilder`. El modo guiado usa `HydraGuidedWorkflow`; el selector de modo usa `HydraCreationModeSwitch`; la cabecera usa `DesignerCommandBar`.

El modo guiado recorre:

1. Scope: nombre + metamodelos source/target.
2. Correspondences: class-to-class rules.
3. Properties and conditions: bindings y guards.
4. Review: checker y persistencia.

El modo avanzado muestra el mismo estado en una única superficie de trabajo.

## Compilación y runtime

Pipeline objetivo:

`TransformationDefinitionDocument -> TransformationPlanCompiler -> CompiledTransformationPlan -> CanonicalTransformationRuntime`

La ejecución M2M se divide en dos fases.

### Fase 1: match + allocation

- recorrer elementos source;
- resolver herencia;
- evaluar guard;
- crear IDs target deterministas;
- registrar allocation/trace.

### Fase 2: population + resolution

- ejecutar bindings direct/constant/expression;
- resolver `TRACE_EQUIVALENT` cuando todos los targets ya existen;
- crear relaciones target;
- materializar `CanonicalModelDocument` y `TransformationTrace`.

Esta separación evita dependencias del orden de recorrido.

## Expresiones

Desde R5C13 Fix24, el AST, los tipos, el checker, el evaluador, el análisis de dependencias y el fingerprint viven en el kernel neutral `semantic.expression`. `Transformation`, eventos, operaciones y runtimes semánticos consumen directamente ese kernel y ya no dependen de `validation.expression`. El documento persistente histórico `ExpressionModel`, su repositorio y su API REST permanecen temporalmente en `validation.expression` como adaptador de compatibilidad, sin cambiar URLs, JSON ni esquemas almacenados. La política de identificadores estables se ha extraído igualmente a `com.example.editor.contract.StableIdentifierPolicy`, conservando una fachada compatible en `validation.contract`.

## Diagnósticos

El checker debe cubrir como mínimo:

- IDs duplicados o ausentes;
- endpoints ausentes/no resolubles;
- tipos source/target inexistentes;
- alias target inválidos;
- guards no booleanos o con contexto incorrecto;
- atributos/relaciones inexistentes;
- bindings target duplicados;
- conversiones de tipo no directas;
- target attributes obligatorios no mapeados;
- expresiones inválidas;
- referencias obsoletas por evolución del metamodelo.

Los quick-fixes futuros incluirán remapeo, defaults, conversiones, enum mapping y reparación de referencias obsoletas.

## Trazabilidad

`TransformationTrace` registra ejecución, source/target model y links. `TransformationBindingTrace` registra qué binding pobló cada feature cuando la política lo habilita. La traza no depende de ATL/ETL y servirá para preview, explicación, debugging, incrementalidad futura y tests de paridad.

## M2T

Compartirá infraestructura de identidad, expresiones, checker, testing y adapters, pero utilizará una experiencia específica basada en `GenerationRule` (`contextType`, `guard`, `outputPath`, `template`, políticas de overwrite/merge). Adaptadores implementados: Acceleo y EGL proyectan de forma fail-closed el subconjunto nativo demostrado; Java y Xtend empaquetan la definición canónica completa para ejecutarla mediante el runtime HYDRA sin aproximar semántica.

## T2M

Se implementa como parsing mediante el Grammar Designer (Xtext/Langium/ANTLR) hacia un modelo canónico a través de `TransformationTextParserGateway`. Cuando el modelo producido por la gramática no coincide con el modelo de dominio, se encadena opcionalmente una M2M persistida. No existe un segundo lenguaje T2M duplicado.

## Adaptadores

`TransformationLanguageAdapter` + `TransformationLanguageAdapterRegistry` están implementados. M2M: ATL y ETL generan únicamente el subconjunto nativo cuya equivalencia puede demostrarse; Java y Xtend conservan el documento canónico completo y delegan la semántica en el runtime HYDRA. M2T: Acceleo y EGL traducen el subconjunto de plantillas estructurales demostrado; Java y Xtend conservan toda la definición. Todo adaptador declara fidelidad (`EXACT`, `CANONICAL_RUNTIME` o `SUBSET`) y falla cerrado cuando una construcción no puede proyectarse sin pérdida. ETL sigue siendo referencia conceptual, nunca modelo persistido de HYDRA.

## Alcance diferido

No introducir en el primer ciclo:

- QVT-R/TGG y bidireccionalidad;
- ejecución incremental/reactiva;
- UI N->M arbitraria;
- código Java arbitrario embebido;
- side effects;
- sincronización continua.

La arquitectura debe permitirlos sin hacerlos parte del contrato básico.

## Validación independiente antes de consolidar la arquitectura

La arquitectura completa deberá revisarse posteriormente por cinco agentes aislados: MDE/MTL, arquitectura software, UX MDE, compiladores/interoperabilidad y QA/rendimiento/seguridad. Quórum requerido: al menos 4/5 `ACCEPT` o `ACCEPT_WITH_CHANGES` y ningún finding bloqueante demostrado de corrección semántica, pérdida de información, seguridad o capacidad de evolución. Si no se cumple, la decisión afectada vuelve a diseño antes de consolidarse.

## Cortes de implementación

### Slice 1

- modelo canónico M2M;
- persistencia + ETag + REST;
- checker estático;
- diseñador guiado/manual homogéneo;
- class mappings + direct property mappings + guard;
- compilador y runtime batch de referencia;
- trace y trace-equivalent;
- tests de contrato/unitarios.

### Slice 2

- constant/expression authoring completo;
- conversion catalog SAFE/CONDITIONAL/LOSSY;
- enum mapping;
- cardinalidad/colecciones;
- preview sobre modelo source;
- explicación y cobertura.

### Slice 3

- ATL/ETL/Java/Xtend adapters;
- pruebas de paridad;
- import/export de definiciones.

### Slice 4

- M2T canónico + templates + Acceleo/EGL/Xtend/Java.

### Slice 5

- integración T2M con Grammar Designer;
- capacidades avanzadas evaluadas por el quórum.

## Fix18: adaptation, text generation and grammar-first text parsing

The canonical transformation document now keeps value acquisition and value adaptation independent. A binding still states whether a value comes from a direct feature, constant, expression or trace equivalence. `TransformationValuePolicy` then states collection reduction, primitive conversion, enum mapping and failure handling. This prevents an artificial Cartesian product of binding kinds.

M2T rules are stored as neutral generation rules. The reference runtime renders a deliberately small data-template surface and returns in-memory artifacts. Paths are portable relative paths and collisions require an explicit policy. No template can execute Java, shell commands or arbitrary host code.

T2M is expressed as grammar parsing followed by an optional M2M projection. The transformation layer depends on `TransformationTextParserGateway`, not on Xtext, Langium, ANTLR or the neutral parser directly. The first gateway implementation resolves the active grammar snapshot and converts parser output to `CanonicalModelDocument`, preserving future replacement by native provider runtimes without changing the transformation contract.

Preview endpoints operate on drafts and are side-effect free with respect to transformation persistence. Persisted execution remains available through resource-specific endpoints.

## Fix19: autoría completa y adaptadores de exportación

El diseñador expone ya las tres familias sobre el mismo `TransformationDefinitionDocument`. M2M permite origen directo, constante o expresión, adaptación de colecciones/tipos, enum mapping y relaciones por traza; M2T permite reglas de generación con contexto, ruta, plantilla, colisión y guard; T2M permite seleccionar gramática, ruta lógica y una proyección M2M opcional. Guiado y manual continúan siendo dos vistas del mismo estado canónico.

La vista previa es siempre side-effect free respecto a la persistencia. El panel de exportación consulta los adaptadores disponibles y muestra compatibilidad, fidelidad, diagnósticos y artefactos antes de permitir su descarga. Los adaptadores nativos no descartan guards, conversiones ni tokens que todavía no pueden representar: bloquean la exportación y recomiendan el bundle de runtime canónico cuando es necesario.

Los endpoints de exportación resuelven únicamente los metamodelos semánticamente necesarios para cada familia: M2M source+target, M2T solo source y T2M ninguno, ya que el parser y la proyección opcional son responsables de resolver su propio contexto.

## Fix22: testing persistente, cobertura y quick-fixes

Los casos de prueba pasan a ser artefactos canónicos persistentes independientes de la definición principal. `TransformationTestCase` conserva entrada, expectativa y metadatos con control ETag; el almacenamiento reutiliza `CanonicalDocumentStore` y un directorio propio. M2M/M2T/T2M comparten el mismo runner, pero cada familia compara el resultado correspondiente: modelo canónico, artefactos textuales o modelo parseado/proyectado.

La cobertura se deriva de evidencia real de ejecución. En M2M el runner fuerza trazabilidad detallada únicamente sobre una copia en memoria de la definición para contar reglas y bindings sin cambiar la política persistida del usuario. En M2T los hits proceden de los artefactos y su `ruleId`. T2M no inventa reglas propias; una proyección M2M sigue siendo responsabilidad de la transformación M2M referenciada.

Los quick-fixes de transformación son propuestas side-effect free. El backend devuelve una copia completa del `TransformationDefinitionDocument` con la reparación propuesta y la UI exige una acción explícita para aplicarla. Las primeras reparaciones cubren conversión de tipos, reducción many-to-one, eliminación de mappings obsoletos y sugerencias conservadoras para propiedades obligatorias con una única coincidencia exacta de nombre, tipo y multiplicidad. Las heurísticas semánticas nunca se aplican automáticamente.

Para evitar pruebas huérfanas, la eliminación de una transformación falla cerrado mientras existan casos persistidos vinculados. No se usa borrado en cascada porque el almacenamiento JSON multiarchivo no ofrece una transacción atómica que permita garantizar rollback completo.
