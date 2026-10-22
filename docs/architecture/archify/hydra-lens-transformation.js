/* HYDRA portal lens - capability "transformation": FUNCTIONAL and TECHNICAL projections. */
(function(){ "use strict"; var HM = window.HM, T = HM.T;
  function nd(lf, sf, fio, t){ var o = { lf: lf, sf: sf, fio: fio }; if (t) { o.lt = t[0]; o.st = t[1]; o.tx = t[2]; } return o; }

  HM.lens({
    caps: {
      transformation: {
        func: {
          title: T("Motor de transformaciones entre modelos, texto y código con trazabilidad y sincronización",
                   "Transformation engine between models, text and code with traceability and synchronisation"),
          summary: T("Una definición de transformación declara un tipo (modelo a modelo, modelo a texto o texto a modelo), sus extremos y un conjunto de reglas con asignaciones. Su ciclo de vida es borrador, comprobada, guardada; una vez guardada se ejecuta, se prueba y se exporta. Cada ejecución puede dejar un rastro que empareja elementos origen y destino, y ese rastro es la base de las sesiones de sincronización, que avanzan por revisiones con puntos de control restaurables.",
                     "A transformation definition declares a kind (model to model, model to text or text to model), its ends and a set of rules with assignments. Its lifecycle is draft, checked, saved; once saved it is run, tested and exported. Each run can leave a trail pairing source and target elements, and that trail underpins synchronisation sessions, which advance through revisions with restorable checkpoints."),
          inputs: [
            T("Definición con tipo, extremos y reglas", "Definition with kind, ends and rules"),
            T("Modelos conformes a los metamodelos de origen y destino", "Models conforming to the source and target metamodels"),
            T("Gramática (solo texto a modelo)", "Grammar (text to model only)"),
            T("Texto fuente (texto a modelo)", "Source text (text to model)"),
            T("Línea base de ambos lados (sincronización incremental)", "Baseline of both sides (incremental synchronisation)")
          ],
          outputs: [
            T("Modelo destino anotado con la regla y el origen de cada elemento", "Target model annotated with the rule and source of each element"),
            T("Ficheros generados con ruta y contenido", "Generated files with path and content"),
            T("Rastro de correspondencias origen-destino", "Source-target correspondence trail"),
            T("Informe de diagnósticos con gravedad y solución", "Diagnostics report with severity and solution"),
            T("Sesiones con revisiones y puntos de control", "Sessions with revisions and checkpoints"),
            T("Exportaciones y veredictos de paridad", "Exports and parity verdicts")
          ],
          conditions: [
            T("Solo se guarda una definición sin errores; los avisos no bloquean", "Only an error-free definition can be saved; warnings do not block"),
            T("Solo el tipo modelo a modelo admite bidireccionalidad, y exige rastro activo", "Only the model-to-model kind supports bidirectionality, and it requires an enabled trail"),
            T("La ejecución es determinista: mismos datos, mismos identificadores de destino", "Execution is deterministic: same input, same target identifiers"),
            T("Hay conflicto solo si ambos lados cambiaron el mismo elemento emparejado o añadieron elementos ambiguos", "A conflict exists only if both sides changed the same paired element or added ambiguous elements"),
            T("La sincronización incremental se elige en cada petición, no en la definición", "Incremental synchronisation is chosen on each request, not in the definition")
          ]
        },
        tech: {
          title: T("Comprobador, compilador de plan, runtime canónico y motor incremental", "Checker, plan compiler, canonical runtime and incremental engine"),
          summary: T("TransformationDefinitionChecker valida el documento (TransformationDefinitionDocument, esquema 1.1 con soporte de 1.0); TransformationPlanCompiler y CanonicalTransformationRuntime ejecutan de forma determinista a través de TransformationRuntimeService. La sincronización usa TransformationIncrementalSynchronizationEngine y TransformationSynchronizationSessionService, expuestos en TransformationController (POST preview-synchronize, POST {id}/synchronize) y TransformationSynchronizationSessionController. La exportación pasa por TransformationExportService y la ejecución nativa por TransformationNativeRuntimeExecutor sobre HydraExternalProcessRunner (TransformationNativeParityController: GET runtimes, POST diagnose, POST execute).",
                     "TransformationDefinitionChecker validates the document (TransformationDefinitionDocument, schema 1.1 supporting 1.0); TransformationPlanCompiler and CanonicalTransformationRuntime run deterministically through TransformationRuntimeService. Synchronisation uses TransformationIncrementalSynchronizationEngine and TransformationSynchronizationSessionService, exposed by TransformationController (POST preview-synchronize, POST {id}/synchronize) and TransformationSynchronizationSessionController. Export goes through TransformationExportService and native execution through TransformationNativeRuntimeExecutor over HydraExternalProcessRunner (TransformationNativeParityController: GET runtimes, POST diagnose, POST execute).")
        },
        elements: {
          definition: {
            nf: T("Definición de transformación (documento declarativo completo)", "Transformation definition (complete declarative document)"),
            fm: T("Unidad declarativa que agrupa tipo, extremos, reglas o reglas de generación y tres políticas (ejecución, rastro, sincronización). Cada definición tiene uno o más extremos y cero o más reglas. Pasa de borrador a comprobada y guardada, y puede reemplazarse o eliminarse.",
                  "Declarative unit grouping kind, ends, rules or generation rules and three policies (execution, trail, synchronisation). Each definition has one or more ends and zero or more rules. It moves from draft to checked and saved, and can be replaced or deleted."),
            tm: T("Registro TransformationDefinitionDocument con schemaVersion \"1.1\" (se acepta \"1.0\"); el guardado exige antes una comprobación sin errores.",
                  "TransformationDefinitionDocument record with schemaVersion \"1.1\" (\"1.0\" accepted); saving first requires an error-free check.")
          },
          kind: {
            nf: T("Tipo de transformación (clase de producto)", "Kind of transformation (class of product)"),
            fm: T("Clasificador exclusivo de la definición que fija el producto (modelo, ficheros o modelo leído de un texto) y, con ello, qué extremos y qué unidad de lógica son exigibles. Cambiarlo antes de guardar reajusta direccionalidad y configuración de texto.",
                  "Exclusive classifier of the definition that fixes the product (model, files or a model read from text) and therefore which ends and which unit of logic are required. Changing it before saving readjusts directionality and text configuration."),
            tm: T("Enumeración TransformationKind (M2M, M2T, T2M); el cambio de tipo vive en transformationDesignerModel.ts.",
                  "TransformationKind enum (M2M, M2T, T2M); kind switching lives in transformationDesignerModel.ts."),
            variants: [
              { k: "M2M", fd: T("Produce un modelo de otro metamodelo mediante reglas; exige origen y destino y es el único tipo que puede ser reversible.", "Produces a model of another metamodel through rules; requires source and target and is the only kind that can be reversible.") },
              { k: "M2T", fd: T("Produce ficheros: por cada elemento de una clase de contexto que cumple su condición se emite un fichero; solo exige origen.", "Produces files: for each element of a context class that meets its condition one file is emitted; only a source is required.") },
              { k: "T2M", fd: T("Lee un texto con una gramática y obtiene un modelo, que puede proyectarse con una transformación modelo a modelo posterior.", "Reads a text with a grammar and obtains a model, which can be projected by a follow-up model-to-model transformation.") }
            ]
          },
          directionality: {
            nf: T("Direccionalidad (sentidos en que opera la definición)", "Directionality (senses in which the definition operates)"),
            fm: T("Atributo condicional de la definición modelo a modelo que decide si solo existen reglas de ida o si también hay reglas de vuelta emparejadas con las de ida. Habilita la política de sincronización y obliga a mantener el rastro.",
                  "Conditional attribute of a model-to-model definition deciding whether only forward rules exist or backward rules paired with forward ones exist too. It enables the synchronisation policy and forces the trail to be kept."),
            tm: T("Enumeración Directionality; el comprobador exige M2M y rastro activo para BIDIRECTIONAL.", "Directionality enum; the checker requires M2M and an enabled trail for BIDIRECTIONAL."),
            variants: [
              { k: "UNIDIRECTIONAL", fd: T("Solo reglas de ida; es el estado por defecto y el único posible fuera de modelo a modelo.", "Forward rules only; the default state and the only one possible outside model to model.") },
              { k: "BIDIRECTIONAL", fd: T("Cada regla de ida puede tener su regla de vuelta con la misma correspondencia; reglas de vuelta quedan desactivadas si se retrocede a unidireccional.", "Each forward rule can have its backward rule with the same correspondence; backward rules are disabled if one reverts to unidirectional.") }
            ]
          },
          endpoint: {
            nf: T("Extremo (metamodelo con rol de origen o destino)", "End (metamodel with a source or target role)"),
            fm: T("Vínculo entre la definición y un metamodelo que cumple un rol. Sobre sus tipos y atributos se expresan las reglas; la huella del metamodelo permite detectar su evolución. En una ejecución inversa los roles se intercambian.",
                  "Link between the definition and a metamodel playing a role. Rules are expressed over its types and attributes; the metamodel fingerprint allows detecting its evolution. In a backward run the roles are swapped."),
            tm: T("Registro TransformationEndpoint (id, role, metamodelId, metamodelFingerprint, alias, required); el comprobador emite SOURCE_REQUIRED y TARGET_REQUIRED.",
                  "TransformationEndpoint record (id, role, metamodelId, metamodelFingerprint, alias, required); the checker emits SOURCE_REQUIRED and TARGET_REQUIRED."),
            variants: [
              { fd: T("Rol de entrada: de él proceden los elementos que disparan las reglas; es el rol por defecto y obligatorio en todos los tipos con reglas.", "Input role: the elements that trigger rules come from it; the default role and required in every kind with rules.") },
              { fd: T("Rol de salida: define el metamodelo del modelo producido; solo existe en modelo a modelo.", "Output role: defines the metamodel of the produced model; exists only in model to model.") }
            ]
          },
          rule: {
            nf: T("Regla de transformación (correspondencia de tipo origen a patrones destino)", "Transformation rule (correspondence from a source type to target patterns)"),
            fm: T("Unidad de lógica del modelo a modelo: para cada elemento del tipo origen que cumple su guarda crea un elemento por patrón destino y los rellena mediante asignaciones. Puede estar activa o no y, en bidireccional, se empareja con su regla opuesta por un identificador de correspondencia.",
                  "Unit of logic of model to model: for each element of the source type meeting its guard it creates one element per target pattern and fills them through assignments. It can be enabled or not and, in bidirectional mode, is paired with its opposite rule by a correspondence identifier."),
            tm: T("Registro TransformationRule (sourceEndpointId, sourceTypeId, guard, targets, bindings, direction, correspondenceId); el comprobador emite RULE_TARGET_REQUIRED y rechaza REVERSE sin BIDIRECTIONAL.",
                  "TransformationRule record (sourceEndpointId, sourceTypeId, guard, targets, bindings, direction, correspondenceId); the checker emits RULE_TARGET_REQUIRED and rejects REVERSE without BIDIRECTIONAL."),
            variants: [
              { fd: T("Sentido directo: transforma elementos del origen en elementos del destino; es el sentido por defecto.", "Direct sense: transforms source elements into target elements; the default sense.") },
              { k: "REVERSE", fd: T("Sentido inverso: transforma elementos del destino en origen; exige definición bidireccional y emparejamiento completo con una regla de ida.", "Inverse sense: transforms target elements into source; requires a bidirectional definition and complete pairing with a forward rule.") }
            ]
          },
          targetpattern: {
            nf: T("Patrón destino (elemento a crear, identificado por alias)", "Target pattern (element to create, identified by alias)"),
            fm: T("Plantilla de un elemento producido por una regla, con su extremo, su tipo y un alias único dentro de la regla. Una regla puede crear varios elementos por cada origen y las asignaciones apuntan a uno por su alias.",
                  "Template of an element produced by a rule, with its end, its type and an alias unique within the rule. A rule can create several elements per source and assignments point to one by its alias."),
            tm: T("Registro TransformationTargetPattern (id, endpointId, typeId, alias); el comprobador valida extremo y tipo del patrón.", "TransformationTargetPattern record (id, endpointId, typeId, alias); the checker validates the pattern's end and type.")
          },
          binding: {
            nf: T("Asignación (derivación del valor de un atributo o relación destino)", "Assignment (derivation of the value of a target attribute or relation)"),
            fm: T("Instrucción de una regla que determina el valor de un atributo, o una relación, de un elemento destino concreto. A cada atributo destino le corresponde como máximo una asignación y puede llevar una política de valores que adapta el resultado.",
                  "Rule instruction determining the value of an attribute, or a relation, of a specific target element. Each target attribute has at most one assignment and may carry a value policy adapting the result."),
            tm: T("Registro TransformationBinding con enumeración BindingKind; el comprobador detecta asignaciones duplicadas y exige atributos conocidos (DIRECT) o expresión (EXPRESSION).",
                  "TransformationBinding record with the BindingKind enum; the checker detects duplicate assignments and requires known attributes (DIRECT) or an expression (EXPRESSION)."),
            variants: [
              { k: "DIRECT", fd: T("El valor es el de un atributo del elemento origen; exige que ambos atributos existan.", "The value is that of an attribute of the source element; both attributes must exist.") },
              { k: "CONSTANT", fd: T("El valor es un literal fijo, idéntico en todas las aplicaciones de la regla.", "The value is a fixed literal, identical in every application of the rule.") },
              { k: "EXPRESSION", fd: T("El valor se calcula con una expresión sobre el origen; la expresión es obligatoria.", "The value is computed with an expression over the source; the expression is mandatory.") },
              { k: "TRACE_EQUIVALENT", fd: T("Reproduce una relación del origen entre los destinos que corresponden a sus extremos, según el rastro.", "Reproduces a source relation between the targets corresponding to its ends, according to the trail.") }
            ]
          },
          valuepolicy: {
            nf: T("Política de valores (adaptación de cardinalidad, tipo y error)", "Value policy (adaptation of cardinality, type and error)"),
            fm: T("Conjunto opcional de decisiones que adapta el valor de una asignación en un orden fijo: reducción de colecciones, traducción de enumerados, conversión de tipo y tratamiento del fallo. Absorbe las diferencias de cardinalidad y tipo entre origen y destino.",
                  "Optional set of decisions adapting an assignment's value in a fixed order: collection reduction, enumeration translation, type conversion and failure handling. It absorbs cardinality and type differences between source and target."),
            tm: T("Registro TransformationValuePolicy (CollectionMode, ConversionMode, BindingErrorPolicy, enumMapping, fallbackValue); el runtime solo admite fallbackValue con USE_FALLBACK.",
                  "TransformationValuePolicy record (CollectionMode, ConversionMode, BindingErrorPolicy, enumMapping, fallbackValue); the runtime only accepts fallbackValue with USE_FALLBACK."),
            variants: [
              { fd: T("Eje de cardinalidad: conservar la lista, quedarse con el primer o el último valor, o reducirla a su número de elementos.", "Cardinality axis: keep the list, keep the first or last value, or reduce it to its element count.") },
              { fd: T("Eje de tipo: dejar el valor tal cual o convertirlo a texto, entero, entero largo, decimal o booleano.", "Type axis: leave the value as is or convert it to text, integer, long, decimal or boolean.") },
              { fd: T("Eje de fallo: detener la ejecución (por defecto), omitir el valor o sustituirlo por el valor de reserva.", "Failure axis: halt the run (default), skip the value or replace it with the fallback value.") }
            ]
          },
          genrule: {
            nf: T("Regla de generación (fichero por elemento de una clase de contexto)", "Generation rule (one file per element of a context class)"),
            fm: T("Unidad de lógica del modelo a texto: asocia una clase de contexto con una plantilla de ruta y una plantilla de contenido, ambas con marcas que se resuelven con el elemento. La guarda, si existe, es booleana; la ruta ha de ser relativa y portable y dos reglas no deben emitir la misma ruta salvo que la política lo permita.",
                  "Unit of logic of model to text: associates a context class with a path template and a content template, both with tokens resolved from the element. The guard, if present, is boolean; the path must be relative and portable and two rules must not emit the same path unless the policy allows it."),
            tm: T("Registro TransformationGenerationRule (sourceEndpointId, contextTypeId, guard, outputPathTemplate, contentTemplate, overwritePolicy); CanonicalTextGenerationRuntime falla ante una ruta duplicada con FAIL.",
                  "TransformationGenerationRule record (sourceEndpointId, contextTypeId, guard, outputPathTemplate, contentTemplate, overwritePolicy); CanonicalTextGenerationRuntime fails on a duplicate path with FAIL."),
            variants: [
              { k: "FAIL", fd: T("Rutas coincidentes entre reglas son un error; es la política por defecto.", "Colliding paths between rules are an error; the default policy.") },
              { fd: T("Rutas coincidentes se resuelven a favor de la última regla aplicada.", "Colliding paths are resolved in favour of the last applied rule.") }
            ]
          },
          t2m: {
            nf: T("Configuración de texto a modelo (gramática y proyección opcional)", "Text-to-model configuration (grammar and optional projection)"),
            fm: T("Parte de la definición de texto a modelo que nombra la gramática con que se interpreta el texto, la ruta lógica del texto y, de forma opcional, una transformación modelo a modelo posterior. Sin transformación posterior el resultado es el modelo leído tal cual.",
                  "Part of a text-to-model definition naming the grammar that interprets the text, the logical text path and, optionally, a follow-up model-to-model transformation. Without a follow-up the result is the parsed model as is."),
            tm: T("Registro TransformationTextToModelConfiguration (grammarId, relativePath, postTransformationId); TransformationTextToModelService lee el texto vía GrammarTransformationTextParserGateway y el comprobador emite GRAMMAR_REQUIRED.",
                  "TransformationTextToModelConfiguration record (grammarId, relativePath, postTransformationId); TransformationTextToModelService reads the text via GrammarTransformationTextParserGateway and the checker emits GRAMMAR_REQUIRED.")
          },
          execpolicy: {
            nf: T("Política de ejecución (garantías de la ejecución)", "Execution policy (guarantees of execution)"),
            fm: T("Declaración de las garantías con que se ejecuta una definición: determinismo y detención ante errores de asignación, además de un modo nominal. En la práctica es fija: solo se admiten sus valores por defecto y el modo no condiciona nada.",
                  "Declaration of the guarantees under which a definition runs: determinism and halting on assignment errors, plus a nominal mode. In practice it is fixed: only its defaults are accepted and the mode conditions nothing."),
            tm: T("Registro TransformationExecutionPolicy (mode, deterministic, failOnBindingError); el comprobador rechaza deterministic=false y failOnBindingError=false, y el modo BATCH/INCREMENTAL no se lee en ejecución.",
                  "TransformationExecutionPolicy record (mode, deterministic, failOnBindingError); the checker rejects deterministic=false and failOnBindingError=false, and the BATCH/INCREMENTAL mode is never read at runtime.")
          },
          tracepolicy: {
            nf: T("Rastro de correspondencias (enlaces origen-regla-destino)", "Correspondence trail (source-rule-target links)"),
            fm: T("Registro que cada ejecución produce de qué elemento destino nació de qué elemento origen y de qué regla, con detalle opcional por asignación. Es obligatorio si hay bidireccionalidad y es la línea base de las sesiones de sincronización; los elementos generados llevan además anotaciones.",
                  "Record each run produces of which target element arose from which source element and rule, with optional per-assignment detail. It is mandatory with bidirectionality and is the baseline of synchronisation sessions; generated elements also carry annotations."),
            tm: T("TracePolicy (enabled, retainBindingDetails) y TransformationTrace con TraceLink y BindingTrace; CanonicalTransformationRuntime anota la salida y registra BindingTrace condicionalmente.",
                  "TracePolicy (enabled, retainBindingDetails) and TransformationTrace with TraceLink and BindingTrace; CanonicalTransformationRuntime annotates the output and records BindingTrace conditionally."),
            variants: [
              { fd: T("Vínculo individual entre un elemento origen, la regla, el alias y el elemento destino, con su correspondencia y sentido.", "Individual link between a source element, the rule, the alias and the target element, with its correspondence and sense.") },
              { fd: T("Explicación fina de un valor: regla, asignación, tipo y atributos de origen y destino; solo existe si rastro y detalle están activos.", "Fine-grained explanation of a value: rule, assignment, kind and source and target attributes; only exists if both trail and detail are enabled.") }
            ]
          },
          syncpolicy: {
            nf: T("Política de sincronización (resolución de conflictos y borrados)", "Synchronisation policy (conflict and deletion resolution)"),
            fm: T("Parámetros que gobiernan una sincronización entre los dos lados de una definición bidireccional: quién prevalece cuando ambos lados cambiaron, si los borrados se propagan y si se necesita un rastro anterior para actualizar. Solo interviene cuando hay cambios concurrentes.",
                  "Parameters governing synchronisation between the two sides of a bidirectional definition: who prevails when both sides changed, whether deletions propagate and whether a previous trail is needed to update. It only intervenes when there are concurrent changes."),
            tm: T("SynchronizationPolicy con ConflictPolicy, propagateDeletions y requireTraceForUpdates; el valor por defecto es FAIL_ON_CONFLICT.", "SynchronizationPolicy with ConflictPolicy, propagateDeletions and requireTraceForUpdates; the default is FAIL_ON_CONFLICT."),
            variants: [
              { k: "FAIL_ON_CONFLICT", fd: T("Ante conflicto no se aplica nada: la sincronización incremental devuelve el conflicto y la de lotes en ambas direcciones lo trata como error.", "On conflict nothing is applied: incremental synchronisation returns the conflict and batch in both directions treats it as an error.") },
              { k: "FORWARD_WINS", fd: T("Prevalece el lado origen: los cambios conflictivos del destino se descartan.", "The source side prevails: conflicting target changes are dropped.") },
              { k: "REVERSE_WINS", fd: T("Prevalece el lado destino: los cambios conflictivos del origen se descartan.", "The target side prevails: conflicting source changes are dropped.") }
            ]
          },
          delta: {
            nf: T("Delta (diferencia de un lado respecto a su línea base)", "Delta (difference of one side with respect to its baseline)"),
            fm: T("Magnitud derivada que expresa qué elementos, atributos y relaciones se añadieron, quitaron o modificaron en un lado desde la última revisión sincronizada. Hay un delta por lado y por sincronización; si ambos están vacíos no hay nada que propagar.",
                  "Derived quantity expressing which elements, attributes and relations were added, removed or modified on one side since the last synchronised revision. There is one delta per side and per synchronisation; if both are empty there is nothing to propagate."),
            tm: T("ModelDelta calculado por TransformationIncrementalSynchronizationEngine a partir de la línea base y del modelo actual.", "ModelDelta computed by TransformationIncrementalSynchronizationEngine from the baseline and the current model."),
            variants: [
              { fd: T("Nivel de elemento: alta, baja o cambio, detectado por identidad; cuenta como cambio una variación de atributos, clase o anotaciones.", "Element level: addition, removal or change, detected by identity; a variation in attributes, class or annotations counts as a change.") },
              { fd: T("Nivel de atributo: cuáles de los atributos de un elemento cambiaron.", "Attribute level: which of an element's attributes changed.") },
              { fd: T("Nivel de relación: altas, bajas y cambios de relaciones entre elementos.", "Relation level: additions, removals and changes of relations between elements.") }
            ]
          },
          conflict: {
            nf: T("Conflicto de sincronización (cambio concurrente incompatible)", "Synchronisation conflict (incompatible concurrent change)"),
            fm: T("Situación derivada en la que los deltas de los dos lados no pueden fusionarse sin decidir a favor de uno. Referencia una correspondencia con su elemento origen y su elemento destino, y se resuelve o bloquea según la política de sincronización.",
                  "Derived situation in which the deltas of both sides cannot be merged without deciding for one. It references a correspondence with its source and target elements and is resolved or blocked according to the synchronisation policy."),
            tm: T("SynchronizationConflict (correspondenceId, elementos origen y destino, features, reason) detectado en TransformationIncrementalSynchronizationEngine.", "SynchronizationConflict (correspondenceId, source and target elements, features, reason) detected in TransformationIncrementalSynchronizationEngine."),
            variants: [
              { fd: T("Ambos lados modificaron la estructura del mismo elemento emparejado.", "Both sides modified the structure of the same paired element.") },
              { fd: T("Ambos lados modificaron el mismo atributo de un elemento emparejado.", "Both sides modified the same attribute of a paired element.") },
              { fd: T("Ambos lados añadieron elementos sin pareja de tipos coincidentes, de modo que no se sabe si son equivalentes.", "Both sides added unpaired elements of matching types, so it is unknown whether they are equivalent.") }
            ]
          },
          session: {
            nf: T("Sesión de sincronización (seguimiento persistente con revisiones)", "Synchronisation session (persistent tracking with revisions)"),
            fm: T("Seguimiento persistente de un par de modelos bajo una definición bidireccional. Conserva las líneas base de ambos lados y el rastro, y registra un punto de control por revisión. La revisión empieza en 1 y solo avanza si hubo cambios sin bloqueo; el historial se acota entre 2 y 200 puntos.",
                  "Persistent tracking of a pair of models under a bidirectional definition. It keeps the baselines of both sides and the trail, and records one checkpoint per revision. The revision starts at 1 and only advances if there were unblocked changes; history is bounded between 2 and 200 checkpoints."),
            tm: T("TransformationSynchronizationSession y Checkpoint (schemaVersion 1.0; transformationEtag, revision, baselines, baselineTrace); id de punto de control = sessionId + \".r\" + revision; los cambios exigen If-Match.",
                  "TransformationSynchronizationSession and Checkpoint (schemaVersion 1.0; transformationEtag, revision, baselines, baselineTrace); checkpoint id = sessionId + \".r\" + revision; changes require If-Match."),
            variants: [
              { fd: T("Estado inicial: revisión 1, tras una sincronización completa desde el lado que se declara autoritativo.", "Initial state: revision 1, after a full synchronisation from the side declared authoritative.") },
              { fd: T("Avance incremental: propaga los deltas y sube la revisión solo si hubo cambios y no se bloqueó.", "Incremental advance: propagates deltas and raises the revision only if there were changes and it was not blocked.") },
              { fd: T("Recomposición de la línea base a partir del lado elegido.", "Recomposition of the baseline from the chosen side.") },
              { fd: T("Nueva revisión cuyo contenido es copia de un punto de control anterior; el historial no se reescribe.", "New revision whose content is a copy of an earlier checkpoint; history is not rewritten.") }
            ]
          },
          export: {
            nf: T("Exportación (proyección de la definición a otro lenguaje)", "Export (projection of the definition to another language)"),
            fm: T("Proyección de una definición guardada a ficheros de otro lenguaje, con una fidelidad declarada (exacta, motor propio o parcial) y diagnósticos. Cada lenguaje admite solo ciertos tipos de transformación y sentidos; el sentido inverso solo existe en modelo a modelo.",
                  "Projection of a saved definition into files of another language, with a declared fidelity (exact, own engine or partial) and diagnostics. Each language supports only certain transformation kinds and senses; the backward sense only exists in model to model."),
            tm: T("TransformationExportService con TransformationLanguageAdapterRegistry; enum TransformationExportLanguage (ATL, ETL, JAVA, XTEND, ACCELEO, EGL, PYTHON, GO, JAVASCRIPT, TYPESCRIPT) y ExportFidelity (EXACT, CANONICAL_RUNTIME, SUBSET).",
                  "TransformationExportService with TransformationLanguageAdapterRegistry; TransformationExportLanguage enum (ATL, ETL, JAVA, XTEND, ACCELEO, EGL, PYTHON, GO, JAVASCRIPT, TYPESCRIPT) and ExportFidelity (EXACT, CANONICAL_RUNTIME, SUBSET)."),
            variants: [
              { fd: T("Familia declarativa de modelo a modelo; el resultado es parcial.", "Declarative model-to-model family; the result is partial.") },
              { fd: T("Familia de plantillas de modelo a texto; el resultado es parcial.", "Template model-to-text family; the result is partial.") },
              { fd: T("Código en un lenguaje de programación para modelo a modelo y modelo a texto; exacto si se genera y parcial si se bloquea.", "Programming-language code for model to model and model to text; exact if generated and partial if blocked.") },
              { fd: T("Paquete que reproduce el motor propio para los tres tipos de transformación.", "Package reproducing the own engine for all three transformation kinds.") }
            ]
          },
          runtime: {
            nf: T("Ejecución nativa (herramienta externa, salud y estado)", "Native execution (external tool, health and status)"),
            fm: T("Ejecución de lo exportado con una herramienta externa, con una capacidad declarada por lenguaje. Cada lenguaje tiene un estado de salud de su herramienta y cada intento de ejecución termina en un estado; la instalación de herramientas tiene también su propio resultado. Sin herramienta lista no hay ejecución.",
                  "Execution of the export with an external tool, with a capacity declared per language. Each language has a health state for its tool and each run attempt ends in a status; tool provisioning has its own outcome too. Without a ready tool there is no execution."),
            tm: T("Enums RuntimeMode, RuntimeSource, RuntimeHealth, RuntimeStatus y ToolchainProvisioningStatus; TransformationNativeRuntimeSupport mantiene la tabla de capacidades y TransformationNativeRuntimeExecutor lanza el proceso mediante HydraExternalProcessRunner, sin ProcessBuilder directo.",
                  "RuntimeMode, RuntimeSource, RuntimeHealth, RuntimeStatus and ToolchainProvisioningStatus enums; TransformationNativeRuntimeSupport keeps the capability table and TransformationNativeRuntimeExecutor launches the process through HydraExternalProcessRunner, with no direct ProcessBuilder."),
            variants: [
              { fd: T("Eje de modo: ejecutar la exportación en lenguaje nativo o el paquete con el motor propio.", "Mode axis: run the native-language export or the package with the own engine.") },
              { fd: T("Eje de procedencia de la herramienta: ninguna, comando indicado explícitamente o paquete gestionado.", "Tool-origin axis: none, an explicitly given command or a managed bundle.") },
              { fd: T("Eje de salud de la herramienta: desde desactivada o sin configurar hasta lista, no disponible, incompatible o fallida.", "Tool-health axis: from disabled or not configured to ready, unavailable, incompatible or failed.") },
              { fd: T("Eje de resultado de un intento: correcta, desactivada, sin capacidad, tiempo agotado, fallida, resultado inválido o cancelada, entre otros.", "Attempt-outcome axis: succeeded, disabled, no capacity, timed out, failed, invalid result or cancelled, among others.") },
              { fd: T("Eje de aprovisionamiento: instalada, actualizada, retirada, sin cambios, bloqueada o revertida.", "Provisioning axis: installed, updated, removed, unchanged, blocked or rolled back.") }
            ]
          },
          parity: {
            nf: T("Paridad (equivalencia entre ejecución propia y externa)", "Parity (equivalence between own and external execution)"),
            fm: T("Veredicto que compara el resultado de la ejecución propia con el de la ejecución externa del código exportado: modelos en modelo a modelo, ficheros en modelo a texto. Existe un veredicto por exportación y distingue los casos en que no se puede comparar.",
                  "Verdict comparing the result of the own run with that of the external run of the exported code: models in model to model, files in model to text. There is one verdict per export and it distinguishes the cases in which comparison is impossible."),
            tm: T("Enum ParityStatus resuelto por TransformationParityService y TransformationNativeParityService.", "ParityStatus enum resolved by TransformationParityService and TransformationNativeParityService."),
            variants: [
              { k: "MATCHED", fd: T("Los resultados de ambas ejecuciones son equivalentes.", "The results of both runs are equivalent.") },
              { k: "MISMATCHED", fd: T("Los resultados divergen: la exportación no reproduce la transformación.", "The results diverge: the export does not reproduce the transformation.") },
              { k: "EXPORT_BLOCKED", fd: T("La exportación quedó bloqueada por incompatibilidad y no hay nada que comparar.", "The export was blocked by incompatibility and there is nothing to compare.") },
              { k: "INVALID_EVIDENCE", fd: T("El lenguaje o el tipo no se admiten, de modo que la evidencia no es utilizable.", "The language or kind is not supported, so the evidence is unusable.") }
            ]
          },
          testcase: {
            nf: T("Caso de prueba (ejemplo repetible con cobertura)", "Test case (repeatable example with coverage)"),
            fm: T("Ejemplo guardado de entrada y resultado esperado asociado a una definición, aplicable a los tres tipos. Al ejecutarse produce un veredicto y un informe de qué reglas y asignaciones se ejercitaron.",
                  "Stored example of input and expected result attached to a definition, applicable to all three kinds. When run it yields a verdict and a report of which rules and assignments were exercised."),
            tm: T("TransformationTestStatus gestionado por TransformationTestService (despacho por tipo) y TransformationTestController (CRUD y ejecución).", "TransformationTestStatus managed by TransformationTestService (dispatch by kind) and TransformationTestController (CRUD and run)."),
            variants: [
              { k: "PASS", fd: T("El resultado obtenido coincide con el esperado.", "The obtained result matches the expected one.") },
              { k: "FAIL", fd: T("El resultado obtenido difiere del esperado.", "The obtained result differs from the expected one.") },
              { k: "ERROR", fd: T("El caso no llegó a producir un resultado comparable.", "The case did not produce a comparable result.") }
            ]
          },
          diagnostics: {
            nf: T("Diagnóstico de calidad (hallazgo con gravedad y solución)", "Quality diagnostic (finding with severity and solution)"),
            fm: T("Hallazgo derivado de comprobar una definición, con gravedad, código, ubicación (regla, asignación, ruta) y una propuesta de solución. El informe es válido si no contiene errores. Las correcciones automáticas existen solo para modelo a modelo.",
                  "Finding derived from checking a definition, with severity, code, location (rule, assignment, path) and a proposed solution. The report is valid if it contains no errors. Automatic fixes exist only for model to model."),
            tm: T("TransformationDiagnostic (severity, code, ruleId, bindingId, path, title, message, solution) producido por TransformationDefinitionChecker (p. ej. REQUIRED_TARGET_ATTRIBUTE_UNMAPPED); TransformationQuickFixService cubre solo M2M.",
                  "TransformationDiagnostic (severity, code, ruleId, bindingId, path, title, message, solution) produced by TransformationDefinitionChecker (e.g. REQUIRED_TARGET_ATTRIBUTE_UNMAPPED); TransformationQuickFixService covers M2M only."),
            variants: [
              { fd: T("Incumplimiento que impide guardar la definición.", "Violation that prevents saving the definition.") },
              { fd: T("Hallazgo que informa sin impedir guardar, como un atributo obligatorio del destino sin asignar.", "Finding that informs without preventing saving, such as a mandatory target attribute left unmapped.") },
              { fd: T("Observación sin consecuencia sobre la validez.", "Observation with no consequence on validity.") }
            ]
          }
        }
      }
    },

    procs: {
      transformation_define: {
        nf: T("Elaboración de una definición hasta su validación", "Authoring a definition up to its validation"),
        df: T("La definición se construye fijando el tipo y los extremos; según el tipo se elaboran reglas de transformación, reglas de generación o la configuración de texto. Opcionalmente se declaran direccionalidad y políticas. La comprobación produce un informe: con errores se vuelve a corregir y sin errores se guarda la definición.",
              "The definition is built by fixing the kind and the ends; depending on the kind, transformation rules, generation rules or the text configuration are authored. Directionality and policies are optionally declared. The check yields a report: with errors the author goes back to fix, without errors the definition is saved."),
        nt: T("Diseñador y TransformationDefinitionChecker", "Designer and TransformationDefinitionChecker"),
        dt: T("El diseñador (TransformationDesigner.tsx, con transformationDesignerModel.ts para valores por defecto y cambio de tipo) arma el TransformationDefinitionDocument; TransformationDefinitionChecker lo valida y el guardado exige antes una comprobación sin errores. Los detalles del guardado en el backend no están en el modelo.",
              "The designer (TransformationDesigner.tsx, with transformationDesignerModel.ts for defaults and kind switching) builds the TransformationDefinitionDocument; TransformationDefinitionChecker validates it and saving first requires an error-free check. Backend save details are not in the model."),
        nodes: {
          kind: nd(T("Fijar el tipo de transformación", "Fix the kind of transformation"), T("definición nueva → clasificada por tipo", "new definition → classified by kind"),
                   T("Nombre y tipo elegidos → definición con su clase de producto", "Chosen name and kind → definition with its class of product"),
                   ["transformationDesignerModel.ts", T("Cambio de tipo en el modelo del diseñador", "Kind switching in the designer model"), T("Pasar a un tipo distinto de M2M fuerza una sola dirección y pasar a T2M prepara una configuración de texto vacía.", "Switching to a kind other than M2M forces one direction and switching to T2M prepares an empty text configuration.")]),
          ends: nd(T("Vincular los extremos a metamodelos o gramática", "Bind the ends to metamodels or a grammar"), T("tipo → extremos exigibles", "kind → required ends"),
                   T("Metamodelos (o gramática) disponibles → extremos de origen y destino fijados", "Available metamodels (or grammar) → source and target ends fixed")),
          k: nd(T("Bifurcación por tipo", "Branch by kind"), T("tipo → espacio de trabajo aplicable", "kind → applicable workspace"),
                T("Tipo de la definición → camino de elaboración correspondiente", "Definition kind → corresponding authoring path")),
          m2m: nd(T("Elaborar reglas y asignaciones de correspondencia", "Author correspondence rules and assignments"), T("tipo origen → patrones destino + asignaciones", "source type → target patterns + assignments"),
                  T("Extremos fijados → reglas con patrones destino y asignaciones", "Ends fixed → rules with target patterns and assignments")),
          m2t: nd(T("Elaborar reglas de generación de ficheros", "Author file generation rules"), T("clase de contexto → ruta + plantilla", "context class → path + template"),
                  T("Origen fijado → reglas de generación con ruta y contenido", "Source fixed → generation rules with path and content")),
          t2m: nd(T("Declarar la lectura de texto como modelo", "Declare reading text as a model"), T("gramática + proyección opcional", "grammar + optional projection"),
                  T("Gramática guardada → configuración de texto a modelo", "Saved grammar → text-to-model configuration")),
          pol: nd(T("Declarar direccionalidad y políticas", "Declare directionality and policies"), T("unidireccional | bidireccional + conflictos", "unidirectional | bidirectional + conflicts"),
                  T("Reglas elaboradas → definición con sentido y políticas de rastro y sincronización", "Authored rules → definition with sense and trail and synchronisation policies")),
          chk: nd(T("Validar la definición contra el modelo de reglas", "Validate the definition against the rules model"), T("definición → informe de diagnósticos", "definition → diagnostics report"),
                  T("Definición en borrador → informe válido (sin errores) o inválido", "Draft definition → valid (error-free) or invalid report"),
                  ["TransformationDefinitionChecker", T("Comprobación síncrona del documento", "Synchronous document check"), T("Valida tipo, extremos, reglas y políticas y emite códigos como SOURCE_REQUIRED, TARGET_REQUIRED, GRAMMAR_REQUIRED y RULE_TARGET_REQUIRED; rechaza deterministic=false y failOnBindingError=false.", "Validates kind, ends, rules and policies and emits codes such as SOURCE_REQUIRED, TARGET_REQUIRED, GRAMMAR_REQUIRED and RULE_TARGET_REQUIRED; rejects deterministic=false and failOnBindingError=false.")]),
          ok: nd(T("Decidir validez del informe", "Decide the report's validity"), T("errores bloquean; avisos no", "errors block; warnings do not"),
                 T("Informe de diagnósticos → continuar a guardar o volver a corregir", "Diagnostics report → proceed to save or go back to fix")),
          sv: nd(T("Persistir la definición validada", "Persist the validated definition"), T("comprobada → guardada (nueva o reemplazo)", "checked → saved (new or replacement)"),
                 T("Definición comprobada → definición guardada, nueva o sustituta", "Checked definition → saved definition, new or replacing")),
          art: nd(T("Definición disponible para operar", "Definition available to operate"), T("estado: guardada", "state: saved"),
                  T("Definición guardada → apta para ejecutar, probar y exportar", "Saved definition → ready to run, test and export"))
        }
      },

      transformation_execute: {
        nf: T("Ejecución por lotes de una definición", "Batch execution of a definition"),
        df: T("Una definición, guardada o en borrador, se aplica a un modelo de entrada en sentido directo o inverso. Si el plan es válido, se crea un elemento destino por patrón y regla aplicable, se rellenan sus atributos y relaciones y, si el rastro está activo, se registran las correspondencias. El resultado es el modelo destino con su rastro; un error de definición o de asignación detiene la ejecución.",
              "A definition, saved or draft, is applied to an input model in the forward or backward sense. If the plan is valid, one target element is created per pattern and applicable rule, its attributes and relations are filled and, if the trail is enabled, correspondences are recorded. The result is the target model with its trail; a definition or assignment error halts the run."),
        nt: T("TransformationRuntimeService → PlanCompiler → CanonicalRuntime", "TransformationRuntimeService → PlanCompiler → CanonicalRuntime"),
        dt: T("TransformationRuntimeService compila el plan con TransformationPlanCompiler y lo ejecuta en CanonicalTransformationRuntime en dos fases (asignación de elementos y relleno). La ejecución es determinista y la salida se anota con la regla y el origen. Los endpoints de ejecución por lotes no figuran en el modelo.",
              "TransformationRuntimeService compiles the plan with TransformationPlanCompiler and runs it in CanonicalTransformationRuntime in two phases (element allocation and population). Execution is deterministic and the output is annotated with rule and source. Batch run endpoints are not in the model."),
        nodes: {
          "in": nd(T("Fijar la entrada y el sentido", "Fix the input and the sense"), T("modelo + dirección de ejecución", "model + execution direction"),
                   T("Modelo de entrada y sentido → entrada de la ejecución definida", "Input model and sense → run input defined")),
          cmp: nd(T("Derivar el plan de reglas aplicables", "Derive the plan of applicable rules"), T("reglas activas del sentido → plan", "active rules of the sense → plan"),
                  T("Definición y sentido → plan con reglas aplicables, extremos intercambiados si es inverso", "Definition and sense → plan with applicable rules, ends swapped if backward"),
                  ["TransformationPlanCompiler.compile", T("Compilación del plan antes de ejecutar", "Plan compilation before running"), T("Comprueba la definición, descarta reglas desactivadas o de otro sentido e intercambia origen y destino al revés; el sentido inverso exige BIDIRECTIONAL y sin reglas aplicables hay error.", "Checks the definition, drops disabled rules or those of the other sense and swaps source and target when backward; the backward sense requires BIDIRECTIONAL and no applicable rules is an error.")]),
          ok: nd(T("Decidir si el plan es ejecutable", "Decide whether the plan is executable"), T("errores de definición → detener",  "definition errors → halt"),
                 T("Plan compilado → ejecución o fin con error", "Compiled plan → run or end with error")),
          alloc: nd(T("Instanciar un elemento por patrón y origen", "Instantiate one element per pattern and source"), T("origen × regla × guarda → elementos vacíos", "source × rule × guard → empty elements"),
                    T("Elementos origen y reglas con guarda cumplida → elementos destino con identificador estable", "Source elements and rules with satisfied guard → target elements with stable identifier"),
                    ["CanonicalTransformationRuntime", T("Fase de asignación del runtime canónico", "Allocation phase of the canonical runtime"), T("Crea un elemento destino por patrón con identificador determinista; repetir la ejecución da el mismo resultado.", "Creates one target element per pattern with a deterministic identifier; repeating the run gives the same result.")]),
          pop: nd(T("Evaluar asignaciones y adaptar valores", "Evaluate assignments and adapt values"), T("reducir → enumerados → tipo → error", "reduce → enumerations → type → error"),
                  T("Elementos destino vacíos y asignaciones → modelo destino con atributos y relaciones", "Empty target elements and assignments → target model with attributes and relations"),
                  ["CanonicalTransformationRuntime", T("Fase de relleno del runtime canónico", "Population phase of the canonical runtime"), T("Aplica la política de valores en el orden colección, enumerado, conversión y error; un fallo detiene la ejecución salvo omitir o usar reserva (fallbackValue solo con USE_FALLBACK).", "Applies the value policy in the order collection, enumeration, conversion and error; a failure halts the run unless skip or fallback is used (fallbackValue only with USE_FALLBACK).")]),
          tr: nd(T("Materializar las correspondencias de la ejecución", "Materialise the run's correspondences"), T("enlaces origen-regla-destino + anotaciones", "source-rule-target links + annotations"),
                 T("Ejecución con rastro activo → rastro con enlaces y detalle opcional", "Run with enabled trail → trail with links and optional detail"),
                 ["TransformationTrace", T("Rastro producido por el runtime canónico", "Trail produced by the canonical runtime"), T("Genera TraceLink y, con rastro y detalle activos, BindingTrace; anota los elementos de salida con la regla y el origen.", "Generates TraceLink and, with trail and detail enabled, BindingTrace; annotates output elements with rule and source.")]),
          out: nd(T("Resultado: modelo destino y rastro", "Result: target model and trail"), T("producto de la ejecución, sin persistir si es borrador", "run product, not persisted if draft"),
                  T("Modelo destino relleno y rastro → resultado de la ejecución", "Filled target model and trail → run result"))
        }
      },

      transformation_batch_both: {
        nf: T("Sincronización completa por lotes según política de conflictos", "Full batch synchronisation according to the conflict policy"),
        df: T("Sin sesión ni deltas, la política de conflictos decide qué lado se reconstruye por completo. Si prevalece el origen, el destino se rehace desde él; si prevalece el destino, se rehace el origen; con parar ante conflicto la petición termina con error en lugar de devolver un resultado bloqueado.",
              "Without a session or deltas, the conflict policy decides which side is rebuilt in full. If the source prevails the target is redone from it; if the target prevails the source is redone; with stop on conflict the request ends with an error instead of returning a blocked result."),
        nt: T("Ejecución BOTH en TransformationRuntimeService", "BOTH execution in TransformationRuntimeService"),
        dt: T("Es el camino por lotes de la dirección BOTH: con FAIL_ON_CONFLICT lanza excepción, a diferencia de la sincronización incremental que devuelve blocked=true. El detalle de la bifurcación entre ida y vuelta en el código no está documentado más allá del modelo.",
              "This is the batch path of the BOTH direction: with FAIL_ON_CONFLICT it throws, unlike incremental synchronisation which returns blocked=true. Code-level detail of the forward/backward fork is not documented beyond the model."),
        nodes: {
          pol: nd(T("Resolver quién prevalece según la política", "Resolve who prevails according to the policy"), T("FAIL_ON_CONFLICT | FORWARD_WINS | REVERSE_WINS", "FAIL_ON_CONFLICT | FORWARD_WINS | REVERSE_WINS"),
                  T("Política de conflictos → lado a reconstruir o parada", "Conflict policy → side to rebuild or stop")),
          fail: nd(T("Abortar la sincronización por conflicto potencial", "Abort synchronisation on potential conflict"), T("pedida → error, sin resultado bloqueado", "requested → error, no blocked result"),
                   T("Política de parar ante conflicto → error sin cambios aplicados", "Stop-on-conflict policy → error with no change applied"),
                   ["FAIL_ON_CONFLICT (lotes)", T("Lanza excepción en lotes", "Throws in batch"), T("En ejecución BOTH por lotes la política lanza excepción; la incremental devuelve blocked=true sin lanzar.", "In batch BOTH execution the policy throws; incremental returns blocked=true without throwing.")]),
          fw: nd(T("Reconstruir el destino desde el origen", "Rebuild the target from the source"), T("origen prevalece → destino completo", "source prevails → whole target"),
                 T("Modelo origen → destino reconstruido por entero", "Source model → target rebuilt entirely")),
          rv: nd(T("Reconstruir el origen desde el destino", "Rebuild the source from the target"), T("destino prevalece → origen completo", "target prevails → whole source"),
                 T("Modelo destino → origen reconstruido por entero", "Target model → source rebuilt entirely")),
          res: nd(T("Sustitución íntegra del lado opuesto", "Full replacement of the opposite side"), T("sin delta, sin sesión", "no delta, no session"),
                  T("Lado reconstruido → lado opuesto sustituido completo", "Rebuilt side → opposite side fully replaced"))
        }
      },

      transformation_sync: {
        nf: T("Sincronización incremental con revisiones y puntos de control", "Incremental synchronisation with revisions and checkpoints"),
        df: T("Una sesión parte de una sincronización completa y registra la línea base de ambos lados. En cada sincronización se verifica la coherencia con la línea base, se calculan los deltas de cada lado y se detectan conflictos. Si no hay cambios termina; si hay conflicto la política bloquea o decide el ganador; si no, se propagan los cambios y la revisión avanza. La sesión permite además rebasar la línea base o restaurar un punto de control como nueva revisión.",
              "A session starts from a full synchronisation and records the baseline of both sides. At each synchronisation consistency with the baseline is verified, each side's deltas are computed and conflicts are detected. With no changes it ends; with a conflict the policy blocks or chooses the winner; otherwise changes are propagated and the revision advances. The session also allows rebasing the baseline or restoring a checkpoint as a new revision."),
        nt: T("SessionService → IncrementalSynchronizationEngine", "SessionService → IncrementalSynchronizationEngine"),
        dt: T("TransformationSynchronizationSessionController expone CRUD, rebase, historial y restauración; TransformationSynchronizationSessionService (create, synchronize, rebase, restore) exige If-Match y delega en TransformationIncrementalSynchronizationEngine, con TransformationSynchronizationConsistencyValidator para la coherencia. Los puntos de control son sessionId + \".r\" + revision. No se hallaron pruebas de extremo a extremo del motor ni del servicio de sesiones.",
              "TransformationSynchronizationSessionController exposes CRUD, rebase, history and restore; TransformationSynchronizationSessionService (create, synchronize, rebase, restore) requires If-Match and delegates to TransformationIncrementalSynchronizationEngine, with TransformationSynchronizationConsistencyValidator for consistency. Checkpoints are sessionId + \".r\" + revision. No end-to-end tests of the engine or the session service were found."),
        nodes: {
          cr: nd(T("Inicializar la sesión con línea base completa", "Initialise the session with a full baseline"), T("ninguna → revisión 1", "none → revision 1"),
                 T("Dos modelos y lado autoritativo → sesión con líneas base, rastro y primer punto de control", "Two models and authoritative side → session with baselines, trail and first checkpoint"),
                 ["TransformationSynchronizationSessionService.create", T("Creación de la sesión", "Session creation"), T("Hace una sincronización completa desde el lado autoritativo, guarda líneas base y baselineTrace y registra el primer punto de control (revisión 1).", "Performs a full synchronisation from the authoritative side, stores baselines and baselineTrace and records the first checkpoint (revision 1).")]),
          chg: nd(T("Evolución independiente de los dos lados", "Independent evolution of both sides"), T("los modelos divergen de la línea base", "models diverge from the baseline"),
                  T("Ediciones sobre uno o ambos modelos → modelos distintos de la línea base", "Edits on one or both models → models different from the baseline")),
          sy: nd(T("Calcular deltas y contrastarlos", "Compute deltas and compare them"), T("línea base × modelos → delta por lado", "baseline × models → delta per side"),
                 T("Líneas base y modelos actuales → deltas de ambos lados y conflictos detectados", "Baselines and current models → deltas of both sides and detected conflicts"),
                 ["TransformationIncrementalSynchronizationEngine", T("Requisito de línea base y detección de conflictos", "Baseline requirement and conflict detection"), T("requireBaseline exige línea base; calcula delta(baseline, current) por lado y detecta conflictos; TransformationSynchronizationConsistencyValidator comprueba modelos y huella del metamodelo. La petición lleva una condición de versión y la misma versión de la transformación.", "requireBaseline demands a baseline; computes delta(baseline, current) per side and detects conflicts; TransformationSynchronizationConsistencyValidator checks models and metamodel fingerprint. The request carries a version precondition and the same transformation version.")]),
          nc: nd(T("Detectar ausencia de cambios", "Detect absence of changes"), T("deltas vacíos → nada que propagar", "empty deltas → nothing to propagate"),
                 T("Deltas de ambos lados → fin sin efecto o continuar con el análisis de conflictos", "Deltas of both sides → end with no effect or proceed to conflict analysis")),
          cf: nd(T("Evaluar la concurrencia de cambios", "Assess change concurrency"), T("deltas cruzados → conflictos 0..N", "crossed deltas → conflicts 0..N"),
                 T("Deltas de ambos lados → ausencia de conflicto o lista de conflictos", "Deltas of both sides → no conflict or list of conflicts")),
          pl: nd(T("Aplicar la política de conflictos", "Apply the conflict policy"), T("parar | gana un lado", "stop | a side wins"),
                 T("Conflictos y política → bloqueo o ganador designado", "Conflicts and policy → block or designated winner")),
          blk: nd(T("Devolver el resultado bloqueado", "Return the blocked result"), T("pedida → bloqueada, revisión estable", "requested → blocked, revision unchanged"),
                  T("Conflictos con política de parar → resultado bloqueado sin cambios aplicados", "Conflicts with stop policy → blocked result with no change applied"),
                  ["SynchronizationResult (blocked=true)", T("Resultado sin excepción", "Result without exception"), T("La sincronización incremental devuelve un resultado con blocked=true y la lista de SynchronizationConflict sin lanzar excepción; la revisión no avanza.", "Incremental synchronisation returns a result with blocked=true and the list of SynchronizationConflict without throwing; the revision does not advance.")]),
          ap: nd(T("Propagar los cambios emparejados al lado opuesto", "Propagate paired changes to the opposite side"), T("deltas − conflictos del perdedor → destino", "deltas − loser's conflicts → target"),
                 T("Deltas sin conflicto o con ganador → lado opuesto actualizado, con borrados si la política lo indica", "Conflict-free deltas or with a winner → opposite side updated, with deletions if the policy says so")),
          rev: nd(T("Avanzar la revisión y registrar el punto de control", "Advance the revision and record the checkpoint"), T("revisión n → n+1", "revision n → n+1"),
                  T("Sincronización aplicada → nueva revisión con su punto de control", "Applied synchronisation → new revision with its checkpoint"),
                  ["TransformationSynchronizationSessionService.synchronize", T("Revisión y punto de control", "Revision and checkpoint"), T("Sube la revisión solo si hubo cambios sin bloqueo y guarda un punto de control con id sessionId + \".r\" + revision; el historial se recorta entre 2 y 200 entradas.", "Raises the revision only if there were unblocked changes and stores a checkpoint with id sessionId + \".r\" + revision; history is trimmed to between 2 and 200 entries.")]),
          ot: nd(T("Elegir la siguiente operación de la sesión", "Choose the session's next operation"), T("sincronizar | rebasar | restaurar | cerrar", "synchronise | rebase | restore | close"),
                 T("Sesión con historial → nueva sincronización, rebase, restauración o fin", "Session with history → new synchronisation, rebase, restore or end")),
          rb: nd(T("Rehacer la línea base desde un lado", "Rebuild the baseline from one side"), T("revisión n → n+1 (rebasada)", "revision n → n+1 (rebased)"),
                 T("Lado elegido → líneas base recompuestas en nueva revisión", "Chosen side → baselines recomposed in a new revision"),
                 ["TransformationSynchronizationSessionService.rebase", T("Rebase de la sesión", "Session rebase"), T("Operación rebase del servicio de sesiones, expuesta por el controlador de sesiones.", "Rebase operation of the session service, exposed by the session controller.")]),
          rs: nd(T("Reinstaurar un estado histórico como revisión nueva", "Reinstate a historical state as a new revision"), T("revisión n → n+1 (restaurada)", "revision n → n+1 (restored)"),
                 T("Punto de control elegido → nueva revisión con sus líneas base", "Chosen checkpoint → new revision with its baselines"),
                 ["TransformationSynchronizationSessionService.restore", T("Restauración desde punto de control", "Restore from a checkpoint"), T("Operación restore del servicio de sesiones: copia el punto elegido en una revisión nueva sin reescribir el historial.", "Restore operation of the session service: copies the chosen checkpoint into a new revision without rewriting history.")]),
          out: nd(T("Estado coherente de la sesión", "Consistent session state"), T("modelos al día + 2..200 puntos de control", "models up to date + 2..200 checkpoints"),
                  T("Operaciones aplicadas → sesión con historial restaurable", "Applied operations → session with restorable history"))
        }
      },

      transformation_export_native: {
        nf: T("Exportación a otro lenguaje y verificación por ejecución nativa", "Export to another language and verification by native execution"),
        df: T("Se elige un lenguaje y un sentido y se comprueba que la combinación con el tipo sea admisible; si no, la exportación queda bloqueada. De lo contrario se generan los ficheros con su fidelidad y diagnósticos. Opcionalmente se diagnostica o aprovisiona la herramienta y, si está lista, se ejecuta lo exportado y se obtiene un estado de ejecución.",
              "A language and a sense are chosen and the combination with the kind is checked to be admissible; if not, the export is blocked. Otherwise the files are generated with their fidelity and diagnostics. Optionally the tool is diagnosed or provisioned and, if ready, the export is run and an execution status is obtained."),
        nt: T("TransformationExportService → NativeRuntimeExecutor", "TransformationExportService → NativeRuntimeExecutor"),
        dt: T("TransformationExportService resuelve el adaptador en TransformationLanguageAdapterRegistry; la ejecución nativa la hace TransformationNativeRuntimeExecutor a través de HydraExternalProcessRunner, con TransformationNativeRuntimeSupport como tabla de capacidades. TransformationNativeParityController ofrece GET runtimes, POST diagnose y POST execute, y TransformationToolchainProvisioningController gestiona la instalación.",
              "TransformationExportService resolves the adapter in TransformationLanguageAdapterRegistry; native execution is done by TransformationNativeRuntimeExecutor through HydraExternalProcessRunner, with TransformationNativeRuntimeSupport as the capability table. TransformationNativeParityController offers GET runtimes, POST diagnose and POST execute, and TransformationToolchainProvisioningController manages installation."),
        nodes: {
          lg: nd(T("Seleccionar destino de la proyección", "Select the projection target"), T("lenguaje × sentido ∈ capacidades", "language × sense ∈ capabilities"),
                 T("Definición guardada → lenguaje y sentido de exportación elegidos", "Saved definition → export language and sense chosen")),
          cp: nd(T("Verificar admisibilidad lenguaje-tipo", "Verify language-kind admissibility"), T("tipos y sentidos soportados por lenguaje", "kinds and senses supported per language"),
                 T("Lenguaje y tipo → exportación admisible o bloqueada", "Language and kind → admissible or blocked export"),
                 ["TransformationExportService", T("Tipos soportados por lenguaje", "Supported kinds per language"), T("Comprueba los tipos de transformación que admite cada lenguaje antes de generar.", "Checks the transformation kinds each language supports before generating.")]),
          ex: nd(T("Proyectar la definición a ficheros", "Project the definition into files"), T("definición → ficheros + fidelidad + diagnósticos", "definition → files + fidelity + diagnostics"),
                 T("Definición y lenguaje → ficheros con ruta y contenido, fidelidad y diagnósticos", "Definition and language → files with path and content, fidelity and diagnostics"),
                 ["TransformationExportService", T("Generación mediante adaptador de lenguaje", "Generation through a language adapter"), T("Delega en el adaptador del TransformationLanguageAdapterRegistry; la fidelidad es EXACT, CANONICAL_RUNTIME o SUBSET.", "Delegates to the adapter of TransformationLanguageAdapterRegistry; fidelity is EXACT, CANONICAL_RUNTIME or SUBSET.")]),
          dg: nd(T("Evaluar o provisionar la herramienta", "Evaluate or provision the tool"), T("salud de la herramienta → lista | no lista", "tool health → ready | not ready"),
                 T("Lenguaje exportado → estado de salud de la herramienta o instalación", "Exported language → tool health state or installation"),
                 ["TransformationToolchainProvisioningController", T("Diagnóstico e instalación", "Diagnosis and installation"), T("El diagnóstico va por POST diagnose del controlador de paridad nativa y la instalación y retirada por el controlador de aprovisionamiento de herramientas.", "Diagnosis goes through POST diagnose of the native parity controller and installation and removal through the toolchain provisioning controller.")]),
          rd: nd(T("Decidir disponibilidad de la herramienta", "Decide tool availability"), T("sin herramienta lista no se ejecuta", "without a ready tool nothing runs"),
                 T("Estado de salud → ejecución o aprovisionamiento previo", "Health state → run or prior provisioning")),
          run: nd(T("Ejecutar lo exportado fuera del motor propio", "Run the export outside the own engine"), T("exportación → estado de ejecución nativa", "export → native execution status"),
                  T("Exportación y herramienta lista → resultado y estado de la ejecución", "Export and ready tool → result and execution status"),
                  ["TransformationNativeRuntimeExecutor", T("Proceso externo controlado", "Controlled external process"), T("Lanza el proceso mediante HydraExternalProcessRunner, sin ProcessBuilder directo, con límites de capacidad y de tiempo; los indicadores de bidireccionalidad completa y de sincronización incremental son false en la tabla de capacidades.", "Launches the process through HydraExternalProcessRunner, with no direct ProcessBuilder, with capacity and time limits; the full-bidirectional and incremental-sync flags are false in the capability table.")]),
          art: nd(T("Exportación con su evidencia de ejecución", "Export with its execution evidence"), T("ficheros + estado nativo", "files + native status"),
                  T("Exportación y, si hubo, ejecución → paquete de salida con estado", "Export and, if any, execution → output package with status"))
        }
      },

      transformation_parity: {
        nf: T("Verificación de equivalencia entre ejecución propia y externa", "Verification of equivalence between own and external execution"),
        df: T("A partir de una exportación y su resultado externo se comprueban primero lenguaje y tipo y la compatibilidad de la exportación; de no cumplirse, el veredicto es evidencia no válida o exportación bloqueada. Si procede, se ejecuta la transformación propia sin persistir y se comparan modelos (modelo a modelo) o ficheros (modelo a texto) para emitir coincide o no coincide.",
              "From an export and its external result, language and kind and the export's compatibility are checked first; if not satisfied, the verdict is invalid evidence or export blocked. Otherwise the own transformation is run without persisting and models (model to model) or files (model to text) are compared to issue matched or mismatched."),
        nt: T("TransformationParityService y NativeParityService", "TransformationParityService and NativeParityService"),
        dt: T("La lógica de comparación está en TransformationParityService y la de ejecución nativa en TransformationNativeParityService; el veredicto es un ParityStatus. No se hallaron pruebas dedicadas de paridad. Los endpoints concretos de paridad propia no figuran en el modelo.",
              "Comparison logic is in TransformationParityService and native execution logic in TransformationNativeParityService; the verdict is a ParityStatus. No dedicated parity tests were found. Concrete endpoints of own-engine parity are not in the model."),
        nodes: {
          ev: nd(T("Validar lenguaje y tipo de la evidencia", "Validate the evidence's language and kind"), T("soportados → seguir; si no, evidencia inválida", "supported → proceed; else invalid evidence"),
                 T("Exportación y resultado externo → evidencia admisible o INVALID_EVIDENCE", "Export and external result → admissible evidence or INVALID_EVIDENCE")),
          cp: nd(T("Verificar que la exportación es comparable", "Verify the export is comparable"), T("exportación bloqueada → sin comparación", "blocked export → no comparison"),
                 T("Exportación → comparable o EXPORT_BLOCKED", "Export → comparable or EXPORT_BLOCKED")),
          ru: nd(T("Obtener el resultado de referencia propio", "Obtain the own reference result"), T("definición + entrada → resultado sin persistir", "definition + input → unpersisted result"),
                 T("Definición y entrada → resultado propio de referencia", "Definition and input → own reference result")),
          kd: nd(T("Elegir la forma de comparación por tipo", "Choose the comparison form by kind"), T("modelo a modelo: modelos; modelo a texto: ficheros", "model to model: models; model to text: files"),
                 T("Tipo de la transformación → comparación de modelos o de ficheros", "Transformation kind → model or file comparison")),
          cm: nd(T("Contrastar resultado propio y externo", "Contrast own and external result"), T("propio × externo → veredicto", "own × external → verdict"),
                 T("Dos resultados → MATCHED o MISMATCHED", "Two results → MATCHED or MISMATCHED"),
                 ["TransformationParityService", T("Lógica de paridad", "Parity logic"), T("Compara el modelo obtenido en modelo a modelo y los ficheros en modelo a texto.", "Compares the obtained model in model to model and the files in model to text.")]),
          mt: nd(T("Veredicto: equivalente o divergente", "Verdict: equivalent or divergent"), T("MATCHED | MISMATCHED", "MATCHED | MISMATCHED"),
                 T("Comparación → veredicto de equivalencia", "Comparison → equivalence verdict")),
          bl: nd(T("Veredicto: no comparable por bloqueo", "Verdict: not comparable due to blocking"), T("EXPORT_BLOCKED", "EXPORT_BLOCKED"),
                 T("Exportación incompatible → veredicto sin comparación", "Incompatible export → verdict without comparison")),
          iv: nd(T("Veredicto: evidencia inutilizable", "Verdict: unusable evidence"), T("INVALID_EVIDENCE", "INVALID_EVIDENCE"),
                 T("Lenguaje o tipo no admitidos → veredicto sin comparación", "Unsupported language or kind → verdict without comparison"))
        }
      },

      transformation_author_guided: {
        nf: T("Elaboración guiada: ámbito, creación y revisión", "Guided authoring: scope, creation and review"),
        df: T("El modo guiado ordena la elaboración en tres etapas. En el ámbito se fijan nombre, tipo y, solo en modelo a modelo, la direccionalidad; si es bidireccional se declara la política de conflictos y de borrados. Después se eligen los extremos, se trabaja en el espacio del tipo y se revisa: sin errores se guarda; con errores se vuelve al espacio de trabajo.",
              "Guided mode orders authoring into three stages. In scope the name, kind and, only for model to model, directionality are fixed; if bidirectional the conflict and deletion policy is declared. Then the ends are chosen, the kind's workspace is used and a review follows: without errors it is saved; with errors the author returns to the workspace."),
        nt: T("TransformationDesigner.tsx (modo GUIDED)", "TransformationDesigner.tsx (GUIDED mode)"),
        dt: T("TransformationDesigner.tsx arranca en modo GUIDED con pasos scope, authoring y review; transformationDesignerModel.ts aporta los valores por defecto y el cambio de tipo, con pruebas en transformationDesignerModel.test.ts. El guardado exige una comprobación sin errores.",
              "TransformationDesigner.tsx starts in GUIDED mode with steps scope, authoring and review; transformationDesignerModel.ts provides defaults and kind switching, with tests in transformationDesignerModel.test.ts. Saving requires an error-free check."),
        nodes: {
          nm: nd(T("Nombrar la definición", "Name the definition"), T("atributo obligatorio de la definición", "mandatory attribute of the definition"),
                 T("Intención del autor → nombre de la definición", "Author's intent → definition name")),
          ty: nd(T("Fijar el tipo y su efecto en la direccionalidad", "Fix the kind and its effect on directionality"), T("M2M | M2T | T2M; distintos de M2M: un sentido", "M2M | M2T | T2M; not M2M: one sense"),
                 T("Nombre → tipo elegido con direccionalidad ajustada", "Name → chosen kind with adjusted directionality"),
                 ["transformationDesignerModel.ts", T("Cambio de tipo en el modelo del diseñador", "Kind switching in the designer model"), T("Ver kind switching: fuera de M2M fuerza una sola dirección.", "See kind switching: outside M2M it forces one direction.")]),
          isM: nd(T("Condicionar la direccionalidad al tipo", "Condition directionality on kind"), T("solo M2M puede ser reversible", "only M2M can be reversible"),
                  T("Tipo elegido → pregunta de direccionalidad o salto", "Chosen kind → directionality question or skip")),
          di: nd(T("Declarar el sentido de la definición", "Declare the definition's sense"), T("unidireccional | bidireccional", "unidirectional | bidirectional"),
                 T("Definición M2M → direccionalidad, con rastro y correspondencias si es bidireccional", "M2M definition → directionality, with trail and correspondences if bidirectional")),
          isB: nd(T("Condicionar las opciones de sincronización", "Condition the synchronisation options"), T("solo bidireccional tiene política de conflictos", "only bidirectional has a conflict policy"),
                  T("Direccionalidad → opciones de sincronización o salto", "Directionality → synchronisation options or skip")),
          cn: nd(T("Declarar la política de sincronización", "Declare the synchronisation policy"), T("ganador + borrados + rastro previo", "winner + deletions + previous trail"),
                 T("Definición bidireccional → política de conflictos y de propagación", "Bidirectional definition → conflict and propagation policy")),
          mm: nd(T("Fijar los extremos de la definición", "Fix the definition's ends"), T("origen siempre; destino solo en M2M", "source always; target only in M2M"),
                 T("Metamodelos guardados → extremos de origen y destino", "Saved metamodels → source and target ends")),
          wk: nd(T("Elaborar la lógica del tipo", "Author the kind's logic"), T("reglas | generación | texto a modelo", "rules | generation | text to model"),
                 T("Extremos fijados → reglas, reglas de generación o configuración de texto", "Ends fixed → rules, generation rules or text configuration")),
          ck: nd(T("Validar la definición completa", "Validate the complete definition"), T("borrador → comprobada", "draft → checked"),
                 T("Definición elaborada → informe con diagnósticos y correcciones propuestas", "Authored definition → report with diagnostics and proposed fixes")),
          ok: nd(T("Decidir entre guardar y corregir", "Decide between saving and fixing"), T("errores bloquean el guardado", "errors block saving"),
                 T("Informe → guardado permitido o retorno al trabajo", "Report → saving allowed or return to work")),
          sv: nd(T("Persistir la definición", "Persist the definition"), T("comprobada → guardada", "checked → saved"),
                 T("Definición sin errores → definición nueva o reemplazada", "Error-free definition → new or replaced definition")),
          art: nd(T("Definición persistida y operable", "Persisted, operable definition"), T("estado: guardada", "state: saved"),
                  T("Guardado completado → definición lista para operar", "Save completed → definition ready to operate"))
        }
      },

      transformation_author_mapping: {
        nf: T("Elaboración de reglas de correspondencia y asignaciones", "Authoring correspondence rules and assignments"),
        df: T("Se emparejan un tipo origen y un tipo destino, y en bidireccional se fija el sentido de la regla. Se crea la regla con un patrón destino y se declaran asignaciones atributo a atributo o por coincidencia de nombres, con su tipo de derivación y política de valores. Opcionalmente se añaden correspondencias de relación y una guarda; la comprobación decide si la regla queda lista.",
              "A source type and a target type are paired and, in bidirectional mode, the rule's sense is fixed. The rule is created with one target pattern and assignments are declared attribute by attribute or by name matching, with their derivation kind and value policy. Optionally relation correspondences and a guard are added; the check decides whether the rule is ready."),
        nt: T("Espacio de mapeo del diseñador (transformationDesignerModel.ts)", "Designer mapping workspace (transformationDesignerModel.ts)"),
        dt: T("La interfaz vive en TransformationMappingWorkspace.tsx, TransformationBindingRow.tsx y TransformationRelationBindings.tsx, con ExpressionBuilder; las operaciones del modelo son createRule, setAttributeBinding, addDirectBinding, mapCompatibleNames y createGuard en transformationDesignerModel.ts.",
              "The interface lives in TransformationMappingWorkspace.tsx, TransformationBindingRow.tsx and TransformationRelationBindings.tsx, with ExpressionBuilder; the model operations are createRule, setAttributeBinding, addDirectBinding, mapCompatibleNames and createGuard in transformationDesignerModel.ts."),
        nodes: {
          sc: nd(T("Emparejar tipo origen y tipo destino", "Pair a source type and a target type"), T("par de clases → patrón destino de la regla", "class pair → rule's target pattern"),
                 T("Metamodelos de ambos extremos → clase origen y clase destino elegidas", "Metamodels of both ends → chosen source and target class")),
          bi: nd(T("Condicionar el sentido de la regla", "Condition the rule's sense"), T("unidireccional: solo ida", "unidirectional: forward only"),
                 T("Direccionalidad de la definición → elección de sentido o ida por defecto", "Definition directionality → sense choice or forward by default")),
          dr: nd(T("Declarar el sentido de la regla", "Declare the rule's sense"), T("ida | vuelta", "forward | backward"),
                 T("Par de clases en definición bidireccional → sentido de la regla", "Class pair in bidirectional definition → rule's sense")),
          ar: nd(T("Crear la regla con su patrón y correspondencia", "Create the rule with its pattern and correspondence"), T("regla activa + patrón + alias único", "enabled rule + pattern + unique alias"),
                 T("Par de clases y sentido → regla con patrón destino y correspondencia heredada si existe la opuesta", "Class pair and sense → rule with target pattern and inherited correspondence if an opposite exists"),
                 ["createRule", T("Operación del modelo del diseñador", "Designer model operation"), T("createRule en transformationDesignerModel.ts crea la regla activa con un patrón destino y un alias único.", "createRule in transformationDesignerModel.ts creates the enabled rule with a target pattern and a unique alias.")]),
          mp: nd(T("Asignar atributos destino", "Assign target attributes"), T("atributo origen → atributo destino", "source attribute → target attribute"),
                 T("Regla creada → asignaciones por atributo, también por coincidencia de nombres", "Created rule → per-attribute assignments, also by name matching"),
                 ["mapCompatibleNames", T("Mapeo por nombres compatibles", "Compatible-names mapping"), T("Usa addDirectBinding y setAttributeBinding; la coincidencia de nombres no distingue mayúsculas.", "Uses addDirectBinding and setAttributeBinding; name matching is case-insensitive.")]),
          kd: nd(T("Elegir la derivación de cada asignación", "Choose each assignment's derivation"), T("DIRECT | CONSTANT | EXPRESSION", "DIRECT | CONSTANT | EXPRESSION"),
                 T("Asignación de atributo → tipo de derivación elegido", "Attribute assignment → chosen derivation kind")),
          vp: nd(T("Adaptar cardinalidad, tipo y fallo del valor", "Adapt the value's cardinality, type and failure"), T("colección · conversión · error · reserva", "collection · conversion · error · fallback"),
                 T("Asignación → política de valores definida", "Assignment → value policy defined")),
          rl: nd(T("Reproducir relaciones entre elementos destino", "Reproduce relations between target elements"), T("TRACE_EQUIVALENT desde el panel de relaciones", "TRACE_EQUIVALENT from the relations panel"),
                 T("Relaciones del origen → correspondencias de relación entre destinos", "Source relations → relation correspondences between targets")),
          gd: nd(T("Restringir la regla con una guarda", "Constrain the rule with a guard"), T("expresión booleana sobre el tipo origen", "boolean expression over the source type"),
                 T("Regla → regla condicionada por una guarda", "Rule → rule conditioned by a guard"),
                 ["createGuard", T("Operación del modelo del diseñador", "Designer model operation"), T("createGuard en transformationDesignerModel.ts crea la expresión booleana sobre el tipo origen.", "createGuard in transformationDesignerModel.ts creates the boolean expression over the source type.")]),
          ck: nd(T("Validar tipos, patrones y asignaciones", "Validate types, patterns and assignments"), T("duplicados, atributos y patrones", "duplicates, attributes and patterns"),
                 T("Regla con asignaciones → diagnósticos de la regla", "Rule with assignments → rule diagnostics")),
          ok: nd(T("Decidir si la regla queda aceptada", "Decide whether the rule is accepted"), T("avisos no bloquean", "warnings do not block"),
                 T("Diagnósticos → regla aceptada o retorno a las asignaciones", "Diagnostics → rule accepted or return to assignments")),
          art: nd(T("Regla incorporada a la definición", "Rule incorporated into the definition"), T("viaja con la definición", "travels with the definition"),
                  T("Regla aceptada → regla integrada en la definición", "Accepted rule → rule integrated into the definition"))
        }
      },

      transformation_author_generation: {
        nf: T("Elaboración de reglas de generación de ficheros", "Authoring file generation rules"),
        df: T("Por cada clase de contexto se añade una regla de generación que nace con una ruta y una plantilla por defecto y política de rutas coincidentes en fallar. Se edita la plantilla con marcas del elemento y la regla puede activarse, desactivarse o eliminarse. La comprobación exige identificador único, clase existente, ruta relativa y portable y al menos una regla.",
              "For each context class a generation rule is added, born with a default path and template and a colliding-path policy of fail. The template is edited with element tokens and the rule can be enabled, disabled or deleted. The check requires a unique identifier, an existing class, a relative and portable path and at least one rule."),
        nt: T("Espacio de generación del diseñador (createGenerationRule)", "Designer generation workspace (createGenerationRule)"),
        dt: T("La interfaz es TransformationGenerationWorkspace.tsx y la regla se crea con createGenerationRule en transformationDesignerModel.ts. No se verificó un control visible para la política de sobrescritura: queda en el valor por defecto.",
              "The interface is TransformationGenerationWorkspace.tsx and the rule is created with createGenerationRule in transformationDesignerModel.ts. No visible control for the overwrite policy was verified: it stays at its default."),
        nodes: {
          cc: nd(T("Elegir la clase que origina los ficheros", "Choose the class that originates files"), T("una regla por clase de contexto", "one rule per context class"),
                 T("Metamodelo origen → clase de contexto elegida", "Source metamodel → chosen context class")),
          df: nd(T("Instanciar la regla con sus valores iniciales", "Instantiate the rule with initial values"), T("ruta y plantilla por defecto; rutas repetidas: fallar", "default path and template; repeated paths: fail"),
                 T("Clase de contexto → regla de generación con ruta, plantilla y política por defecto", "Context class → generation rule with default path, template and policy"),
                 ["createGenerationRule", T("Operación del modelo del diseñador", "Designer model operation"), T("createGenerationRule en transformationDesignerModel.ts crea la regla con ruta \"generated/{{primer atributo}}.txt\", una plantilla y sobrescritura en fallar.", "createGenerationRule in transformationDesignerModel.ts creates the rule with path \"generated/{{first attribute}}.txt\", a template and overwrite on fail.")]),
          ed: nd(T("Redactar el contenido con marcas del elemento", "Write the content with element tokens"), T("identificador, clase y atributos del elemento", "element identifier, class and attributes"),
                 T("Regla inicial → plantilla de contenido definitiva", "Initial rule → final content template")),
          en: nd(T("Gobernar la vigencia de la regla", "Govern the rule's validity"), T("activa | desactivada | eliminada", "enabled | disabled | deleted"),
                 T("Regla → regla activa, inactiva o retirada", "Rule → enabled, inactive or withdrawn rule")),
          ck: nd(T("Validar rutas, clases e identificadores", "Validate paths, classes and identifiers"), T("único · existente · portable · ≥ 1 regla", "unique · existing · portable · ≥ 1 rule"),
                 T("Reglas de generación → diagnósticos de generación", "Generation rules → generation diagnostics"),
                 ["TransformationDefinitionChecker", T("Comprobaciones de reglas de generación", "Generation rule checks"), T("Valida identificador único, ruta obligatoria y portable y existencia de la clase.", "Validates unique identifier, mandatory portable path and existence of the class.")]),
          ok: nd(T("Decidir si las reglas son aceptables", "Decide whether the rules are acceptable"), T("un error impide guardar", "an error prevents saving"),
                 T("Diagnósticos → reglas aceptadas o retorno a la edición", "Diagnostics → rules accepted or return to editing")),
          art: nd(T("Reglas de generación integradas", "Generation rules integrated"), T("viajan con la definición", "travel with the definition"),
                  T("Reglas aceptadas → conjunto de reglas de generación de la definición", "Accepted rules → the definition's set of generation rules"))
        }
      },

      transformation_author_text2model: {
        nf: T("Configuración de la lectura de texto como modelo", "Configuring the reading of text as a model"),
        df: T("Se designa la gramática que interpreta el texto, se puede fijar la ruta lógica del texto y se puede nombrar una transformación modelo a modelo posterior que proyecta el modelo leído. La comprobación falla sin gramática; si no hay errores la configuración se incorpora a la definición.",
              "The grammar that interprets the text is designated, the logical text path can be set and a follow-up model-to-model transformation projecting the parsed model can be named. The check fails without a grammar; if there are no errors the configuration is incorporated into the definition."),
        nt: T("TransformationTextToModelWorkspace.tsx", "TransformationTextToModelWorkspace.tsx"),
        dt: T("La interfaz es TransformationTextToModelWorkspace.tsx; en tiempo de ejecución TransformationTextToModelService lee el texto vía GrammarTransformationTextParserGateway y el comprobador emite GRAMMAR_REQUIRED. No hay pruebas dedicadas de textmodel.",
              "The interface is TransformationTextToModelWorkspace.tsx; at runtime TransformationTextToModelService reads the text via GrammarTransformationTextParserGateway and the checker emits GRAMMAR_REQUIRED. There are no dedicated textmodel tests."),
        nodes: {
          gr: nd(T("Designar la gramática de lectura", "Designate the reading grammar"), T("referencia obligatoria a una gramática", "mandatory reference to a grammar"),
                 T("Gramáticas guardadas → gramática asociada a la definición", "Saved grammars → grammar associated with the definition")),
          pt: nd(T("Identificar el texto de partida", "Identify the source text"), T("ruta lógica con valor por defecto", "logical path with a default value"),
                 T("Gramática → ruta lógica del texto", "Grammar → logical text path")),
          po: nd(T("Encadenar una proyección posterior", "Chain a follow-up projection"), T("modelo leído → modelo proyectado", "parsed model → projected model"),
                 T("Modelo leído → transformación modelo a modelo posterior, opcional", "Parsed model → optional follow-up model-to-model transformation")),
          ck: nd(T("Verificar la presencia de la gramática", "Verify the grammar is present"), T("sin gramática: error", "no grammar: error"),
                 T("Configuración → diagnósticos de texto a modelo", "Configuration → text-to-model diagnostics"),
                 ["TransformationDefinitionChecker", T("Comprobación GRAMMAR_REQUIRED", "GRAMMAR_REQUIRED check"), T("Emite GRAMMAR_REQUIRED sin gramática; la comprobación de que la transformación posterior sea modelo a modelo no está verificada.", "Emits GRAMMAR_REQUIRED without a grammar; that the follow-up is model to model is not verified.")]),
          ok: nd(T("Decidir si la configuración es aceptable", "Decide whether the configuration is acceptable"), T("un error impide guardar", "an error prevents saving"),
                 T("Diagnósticos → configuración aceptada o retorno a la gramática", "Diagnostics → configuration accepted or return to the grammar")),
          art: nd(T("Configuración integrada en la definición", "Configuration integrated into the definition"), T("viaja con la definición", "travels with the definition"),
                  T("Configuración aceptada → configuración de texto a modelo de la definición", "Accepted configuration → the definition's text-to-model configuration"))
        }
      }
    }
  });
})();
