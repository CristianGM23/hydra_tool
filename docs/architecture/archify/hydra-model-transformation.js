/* HYDRA portal content — capability "transformation" (Transformaciones / Transformations). */
(function(){ "use strict"; var HM = window.HM, T = HM.T;
  var J = "src/main/java/com/example/editor/transformation/";
  var D = "frontend/src/transformation-designer/";
  var TT = "src/test/java/com/example/editor/transformation/";
  function ev(p, line, label){ return { path: p, line: line, label: label }; }

  HM.cap("transformation", {
    name: T("Transformaciones", "Transformations"), group: "behavior",
    user: {
      tagline: T("Convierte un modelo en otro modelo, en texto o en código, y mantiene ambos lados al día",
                 "Turns a model into another model, into text or into code, and keeps both sides up to date"),
      what: T("Una transformación es una receta que dice cómo pasar de un modelo (o de un texto) a otro resultado. Hay tres tipos: modelo a modelo, modelo a texto (documentos, código, informes) y texto a modelo (leer un texto escrito en un lenguaje propio y obtener un modelo). Si la receta es reversible, HYDRA también puede llevar los cambios de vuelta y sincronizar los dos lados.",
              "A transformation is a recipe that says how to get from a model (or a text) to another result. There are three kinds: model to model, model to text (documents, code, reports) and text to model (read a text written in your own language and obtain a model). If the recipe is reversible, HYDRA can also carry changes back and keep the two sides synchronised."),
      why: T("Evita rehacer a mano el mismo trabajo cada vez que cambia el modelo: el resultado se obtiene siempre igual, con un rastro de qué elemento originó cada cosa.",
             "Avoids redoing the same work by hand each time the model changes: the result is always obtained the same way, with a trail of which element produced what."),
      when: T("Cuando necesitas derivar un segundo modelo, generar documentos o código a partir de un modelo, importar un texto como modelo, o mantener dos modelos coherentes entre sí.",
              "When you need to derive a second model, generate documents or code from a model, import a text as a model, or keep two models consistent with each other."),
      canDefine: [
        T("El tipo de transformación (modelo a modelo, modelo a texto, texto a modelo)", "The kind of transformation (model to model, model to text, text to model)"),
        T("Los metamodelos de origen y de destino", "The source and target metamodels"),
        T("Reglas que dicen qué tipo de elemento origen produce qué elementos destino", "Rules saying which kind of source element produces which target elements"),
        T("Asignaciones de atributos: copiar, valor fijo, expresión o correspondencia de relaciones", "Attribute assignments: copy, fixed value, expression or relation correspondence"),
        T("Plantillas de generación de ficheros (ruta y contenido) para modelo a texto", "File generation templates (path and content) for model to text"),
        T("Una gramática y una transformación posterior para texto a modelo", "A grammar and a follow-up transformation for text to model"),
        T("La política de sincronización y el rastro que se guarda", "The synchronisation policy and the trail that is kept")
      ],
      needs: [
        T("Al menos un metamodelo guardado como origen", "At least one saved metamodel as source"),
        T("Un metamodelo de destino (solo modelo a modelo)", "A target metamodel (model to model only)"),
        T("Un modelo de entrada para ejecutar o previsualizar", "An input model to run or preview"),
        T("Una gramática guardada (solo texto a modelo)", "A saved grammar (text to model only)"),
        T("Una herramienta externa instalada (solo ejecución nativa)", "An installed external tool (native execution only)")
      ],
      produces: [
        T("Un modelo destino con anotaciones de qué regla lo originó", "A target model annotated with the rule that produced each element"),
        T("Ficheros de texto generados con su ruta", "Generated text files with their path"),
        T("Un rastro de correspondencias entre elementos origen y destino", "A trail of correspondences between source and target elements"),
        T("Diagnósticos de calidad de la definición", "Quality diagnostics of the definition"),
        T("Sesiones de sincronización con puntos de control restaurables", "Synchronisation sessions with restorable checkpoints"),
        T("Exportaciones a otros lenguajes e informes de paridad", "Exports to other languages and parity reports")
      ],
      mandatory: [
        T("Nombre y tipo de transformación", "Name and kind of transformation"),
        T("Metamodelo de origen", "Source metamodel"),
        T("Al menos una regla (o una regla de generación)", "At least one rule (or a generation rule)")
      ],
      optional: [
        T("Condición (guarda) de cada regla", "Condition (guard) of each rule"),
        T("Política de valores por asignación", "Value policy per assignment"),
        T("Casos de prueba", "Test cases"),
        T("Exportación a otros lenguajes", "Export to other languages")
      ],
      conditional: [
        T("Metamodelo de destino (solo modelo a modelo)", "Target metamodel (model to model only)"),
        T("Gramática (solo texto a modelo)", "Grammar (text to model only)"),
        T("Direccionalidad bidireccional y política de conflictos (solo modelo a modelo reversible)", "Bidirectionality and conflict policy (reversible model to model only)")
      ],
      next: [
        T("Comprobar la definición y corregir diagnósticos", "Check the definition and fix diagnostics"),
        T("Previsualizar sobre un modelo real", "Preview on a real model"),
        T("Crear casos de prueba y medir la cobertura", "Create test cases and measure coverage"),
        T("Exportar a otro lenguaje y comparar resultados", "Export to another language and compare results"),
        T("Abrir una sesión de sincronización", "Open a synchronisation session")
      ],
      interacts: [
        { cap: "model_editor", t: T("Los modelos que edita el usuario son la entrada de la transformación", "The models edited by the user are the input of a transformation") },
        { cap: "metamodel", t: T("Origen y destino se definen sobre metamodelos", "Source and target are defined over metamodels") },
        { cap: "grammar", t: T("El texto a modelo lee el texto con una gramática", "Text to model reads the text using a grammar") },
        { cap: "toolchains", t: T("La ejecución nativa usa herramientas externas instaladas", "Native execution uses installed external tools") }
      ]
    },
    func: {
      lifecycle: [
        T("borrador → comprobada (sin errores) → guardada → ejecutable", "draft → checked (no errors) → saved → executable"),
        T("ejecución por lotes: modelo de entrada → modelo o ficheros de salida + rastro", "batch run: input model → output model or files + trail"),
        T("sincronización: sesión creada (revisión 1) → sincronizada (revisión+1) → restaurable desde cualquier punto de control", "synchronisation: session created (revision 1) → synchronised (revision+1) → restorable from any checkpoint"),
        T("exportación: definición → artefactos en otro lenguaje → ejecución nativa → paridad", "export: definition → artefacts in another language → native run → parity")
      ],
      semantics: [
        T("Solo se puede guardar una definición cuya comprobación no tenga errores; los avisos no bloquean.", "Only a definition whose check has no errors can be saved; warnings do not block."),
        T("Modelo a modelo exige al menos un origen y un destino; modelo a texto exige orígenes y reglas de generación; texto a modelo exige una gramática.", "Model to model requires at least one source and one target; model to text requires sources and generation rules; text to model requires a grammar."),
        T("Solo las transformaciones modelo a modelo pueden ser bidireccionales; entonces el rastro debe estar activo y las reglas de vuelta se emparejan con las de ida mediante un identificador de correspondencia.", "Only model-to-model transformations can be bidirectional; then the trail must be enabled and backward rules are paired with forward rules by a correspondence identifier."),
        T("El orden de evaluación de una asignación es: reducir colección, aplicar correspondencia de enumerados, convertir tipo, aplicar la política de error.", "An assignment is evaluated in this order: reduce collection, apply enumeration mapping, convert type, apply the error policy."),
        T("La ejecución es determinista: los identificadores de los elementos destino se derivan de forma estable, así que repetir la ejecución da el mismo resultado.", "Execution is deterministic: target element identifiers are derived in a stable way, so repeating the run gives the same result."),
        T("La sincronización incremental compara cada lado con su línea base; solo hay conflicto si ambos lados cambiaron el mismo elemento emparejado, o si ambos añadieron elementos sin emparejar de tipos coincidentes.", "Incremental synchronisation compares each side with its baseline; a conflict exists only if both sides changed the same paired element, or both added unpaired elements of matching types."),
        T("Límite honesto: la sincronización incremental no se declara en la definición; se elige en cada petición de sincronizar.", "Honest limit: incremental synchronisation is not declared in the definition; it is chosen on each synchronise request."),
        T("Límite honesto: no hay pruebas de extremo a extremo de la sincronización incremental ni de las sesiones.", "Honest limit: there are no end-to-end tests of incremental synchronisation or sessions.")
      ],
      states: [
        { n: T("Borrador", "Draft"), d: T("En edición, aún sin comprobar", "Being edited, not yet checked") },
        { n: T("Comprobada", "Checked"), d: T("Sin errores; se puede guardar", "No errors; can be saved") },
        { n: T("Guardada", "Saved"), d: T("Disponible para ejecutar, probar y exportar", "Available to run, test and export") },
        { n: T("Sesión sincronizada", "Synchronised session"), d: T("Línea base de ambos lados registrada en una revisión", "Baselines of both sides recorded in a revision") },
        { n: T("Sincronización bloqueada", "Synchronisation blocked"), d: T("Conflicto sin resolver; la revisión no avanza", "Unresolved conflict; the revision does not advance") }
      ],
      cross: [
        T("Depende de los modelos del editor (entrada) y de los metamodelos (extremos de origen y destino).", "Depends on editor models (input) and metamodels (source and target ends)."),
        T("Texto a modelo depende de una gramática para leer el texto.", "Text to model depends on a grammar to read the text."),
        T("La ejecución nativa depende de herramientas externas gestionadas por las cadenas de herramientas.", "Native execution depends on external tools managed by toolchains."),
        T("No confundir con la certificación de paridad nativa de la integración tecnológica, que es otro mecanismo.", "Not to be confused with the native parity certification of technology integration, which is a different mechanism.")
      ]
    },
    tech: {
      runtime: T("Comprobador de definiciones, compilador de plan y runtime canónico determinista; motor de sincronización incremental con sesiones; adaptadores de exportación y ejecutor nativo por proceso externo controlado.",
                 "Definition checker, plan compiler and deterministic canonical runtime; incremental synchronisation engine with sessions; export adapters and a native executor through a controlled external process."),
      services: ["TransformationDefinitionChecker", "TransformationPlanCompiler", "CanonicalTransformationRuntime", "TransformationRuntimeService", "TransformationIncrementalSynchronizationEngine", "TransformationSynchronizationConsistencyValidator", "TransformationSynchronizationSessionService", "TransformationTextGenerationService", "CanonicalTextGenerationRuntime", "TransformationTextToModelService", "GrammarTransformationTextParserGateway", "TransformationExportService", "TransformationLanguageAdapterRegistry", "TransformationNativeRuntimeExecutor", "TransformationNativeRuntimeSupport", "TransformationParityService", "TransformationNativeParityService", "TransformationTestService", "TransformationQuickFixService", "HydraExternalProcessRunner"],
      apis: ["TransformationController: POST preview-synchronize, POST {id}/synchronize", "TransformationSynchronizationSessionController (sessions: CRUD, rebase, history, restore)", "TransformationNativeParityController: GET runtimes, POST diagnose, POST execute", "TransformationToolchainProvisioningController (install/uninstall)", "TransformationTestController (tests CRUD and run)"],
      schemas: ["TransformationDefinitionDocument (schema 1.0/1.1)", "TransformationEndpoint", "TransformationRule", "TransformationTargetPattern", "TransformationBinding", "TransformationValuePolicy", "TransformationGenerationRule", "TransformationTextToModelConfiguration", "TransformationExecutionPolicy", "TracePolicy", "SynchronizationPolicy", "TransformationTrace / TraceLink / BindingTrace", "ModelDelta", "SynchronizationConflict", "SynchronizationResult", "TransformationDiagnostic"],
      tests: ["TransformationDefinitionCheckerTests", "CanonicalTransformationRuntimeTests", "AggressiveTransformationRuntimeTests", "TransformationSynchronizationConsistencyValidatorTests", "TransformationLanguageAdapterTests", "TransformationNativeRuntimeExecutorTests", "TransformationTestServiceTests", "TransformationQuickFixServiceTests", "TransformationDesignerContract.test.ts", "transformationDesignerModel.test.ts"],
      evidence: [
        ev(J + "core/TransformationDefinitionDocument.java", 9, "definition document"),
        ev(J + "TransformationDefinitionChecker.java", 73, "kind/direction rules"),
        ev(J + "TransformationPlanCompiler.java", 31, "plan compile"),
        ev(J + "CanonicalTransformationRuntime.java", 46, "canonical run"),
        ev(J + "TransformationRuntimeService.java", 85, "incremental chosen per request"),
        ev(J + "TransformationIncrementalSynchronizationEngine.java", 50, "conflict detection"),
        ev(J + "sync/TransformationSynchronizationSessionService.java", 102, "session synchronize"),
        ev(J + "export/TransformationExportService.java", 34, "supported kinds"),
        ev(J + "nativeexec/TransformationNativeRuntimeSupport.java", 25, "capability table"),
        ev(J + "parity/TransformationParityService.java", 50, "parity logic")
      ],
      notes: [
        T("Hallazgo: el modo de ejecución de la política (BATCH/INCREMENTAL) nunca se lee en tiempo de ejecución; solo existe su valor por defecto. INCREMENTAL se elige por petición de sincronización, y el tipo del frontend solo admite BATCH (D/types.ts:10).",
          "Finding: the policy execution mode (BATCH/INCREMENTAL) is never read at runtime; only its default exists. INCREMENTAL is chosen per synchronisation request, and the frontend type only allows BATCH (D/types.ts:10)."),
        T("Asimetría: la ejecución BOTH por lotes con FAIL_ON_CONFLICT lanza excepción; la sincronización incremental devuelve un resultado blocked=true sin lanzar.",
          "Asymmetry: batch BOTH with FAIL_ON_CONFLICT throws; incremental synchronisation returns a blocked=true result without throwing."),
        T("El comprobador rechaza deterministic=false y failOnBindingError=false: la política de ejecución es de facto fija.",
          "The checker rejects deterministic=false and failOnBindingError=false: the execution policy is effectively fixed."),
        T("Los indicadores de bidireccionalidad completa y de sincronización incremental son false en todas las entradas de la tabla de capacidades nativas.",
          "Full-bidirectional and incremental-sync flags are false in every entry of the native capability table."),
        T("No encontrado: pruebas de extremo a extremo de TransformationRuntimeService.synchronize, del motor incremental y del servicio de sesiones; pruebas dedicadas de textmodel, parity y text. Frontend: sin pruebas de componentes de paneles.",
          "Not found: end-to-end tests of synchronize, the incremental engine and the session service; dedicated tests of textmodel, parity and text. Frontend: no component tests for panels."),
        T("La ejecución de procesos externos pasa por HydraExternalProcessRunner, sin ProcessBuilder directo.",
          "External process execution goes through HydraExternalProcessRunner, with no direct ProcessBuilder."),
        T("Quick Fix de transformaciones existe solo para modelo a modelo y es un subsistema aparte.",
          "Transformation Quick Fix exists only for model to model and is a separate subsystem.")
      ]
    },

    elements: [
      { id: "definition", n: T("Definición de transformación", "Transformation definition"), kind: "concept", req: "MANDATORY",
        mean: T("El documento que reúne nombre, tipo, extremos, reglas y políticas de una transformación.", "The document gathering name, kind, ends, rules and policies of a transformation."),
        purpose: T("Es lo que se comprueba, guarda, ejecuta, prueba y exporta.", "It is what gets checked, saved, run, tested and exported."),
        when: T("Siempre: es el punto de partida de toda transformación.", "Always: it is the starting point of every transformation."),
        card: "1 definición ↔ 1..N extremos, 0..N reglas",
        props: [
          { n: T("Nombre", "Name"), req: "MANDATORY", d: T("Cómo se llama la transformación", "What the transformation is called") },
          { n: T("Tipo", "Kind"), req: "MANDATORY", d: T("Modelo a modelo por defecto", "Model to model by default") },
          { n: T("Versión de esquema", "Schema version"), req: "OPTIONAL", d: T("Si se deja vacía se usa la 1.1; se aceptan la 1.0 y la 1.1", "If left blank 1.1 is used; 1.0 and 1.1 are accepted") },
          { n: T("Anotaciones", "Annotations"), req: "OPTIONAL", d: T("Notas libres en la definición y en cada parte", "Free notes on the definition and each part") }
        ],
        io: { i: T("Elecciones del usuario en el diseñador", "User choices in the designer"), o: T("Definición guardada y comprobada", "Saved, checked definition") },
        effect: T("Al guardar se exige una comprobación sin errores; se crea la definición nueva o se reemplaza la existente.", "Saving requires an error-free check; a new definition is created or the existing one is replaced."),
        func: { rel: [T("Contiene 1..N extremos, 0..N reglas, 0..N reglas de generación y 3 políticas", "Contains 1..N ends, 0..N rules, 0..N generation rules and 3 policies")],
                lifecycle: T("borrador → comprobada → guardada (nueva o reemplazo) → eliminada", "draft → checked → saved (new or replacement) → deleted"),
                cond: T("Guardar exige comprobación sin errores; los avisos no bloquean", "Saving requires an error-free check; warnings do not block") },
        tech: { type: "TransformationDefinitionDocument", schema: "schemaVersion \"1.1\", supports \"1.0\"", evidence: [ev(J + "core/TransformationDefinitionDocument.java", 9, "record"), ev(D + "TransformationDesigner.tsx", 39, "save requires check")] } },

      { id: "kind", n: T("Tipo de transformación", "Kind of transformation"), kind: "variant-set", req: "MANDATORY",
        mean: T("Qué clase de resultado produce la transformación.", "What kind of result the transformation produces."),
        purpose: T("Determina qué extremos, reglas y herramientas se necesitan.", "Determines which ends, rules and tools are needed."),
        when: T("Se elige al empezar; cambiarlo reajusta la definición.", "Chosen at the start; changing it adjusts the definition."),
        card: "1 definición ↔ 1 tipo",
        variants: [
          { n: T("Modelo a modelo (M2M)", "Model to model (M2M)"), d: T("Convierte un modelo en otro modelo de un metamodelo distinto; puede ser reversible", "Converts a model into another model of a different metamodel; can be reversible"), req: "ALTERNATIVE" },
          { n: T("Modelo a texto (M2T)", "Model to text (M2T)"), d: T("Genera ficheros de texto (documentos, código, informes) a partir de un modelo", "Generates text files (documents, code, reports) from a model"), req: "ALTERNATIVE" },
          { n: T("Texto a modelo (T2M)", "Text to model (T2M)"), d: T("Lee un texto con una gramática y obtiene un modelo, con una transformación posterior opcional", "Reads a text with a grammar and obtains a model, with an optional follow-up transformation"), req: "ALTERNATIVE" }
        ],
        io: { i: T("Elección en el paso del tipo", "Choice in the kind step"), o: T("Campos y comprobaciones que se aplican", "Fields and checks that apply") },
        effect: T("Pasar a un tipo distinto de modelo a modelo fuerza la direccionalidad a una sola dirección; pasar a texto a modelo prepara una configuración de texto vacía.", "Switching to a kind other than model to model forces one-way; switching to text to model prepares an empty text configuration."),
        func: { rel: [T("Condiciona los extremos exigidos y qué reglas existen", "Conditions the required ends and which rules exist")],
                lifecycle: T("elegido al crear → puede cambiarse antes de guardar", "chosen at creation → may be changed before saving"),
                cond: T("M2M: origen y destino; M2T: solo origen; T2M: gramática", "M2M: source and target; M2T: source only; T2M: grammar") },
        tech: { type: "TransformationKind", schema: "enum M2M|M2T|T2M", evidence: [ev(J + "core/TransformationKind.java", 4, "enum"), ev(D + "transformationDesignerModel.ts", 33, "kind switching")] } },

      { id: "directionality", n: T("Direccionalidad", "Directionality"), kind: "variant-set", req: "CONDITIONAL",
        mean: T("Si la transformación solo va del origen al destino o también puede ir al revés.", "Whether the transformation only goes from source to target or can also go backwards."),
        purpose: T("Habilita las reglas de vuelta y la sincronización entre dos lados.", "Enables backward rules and synchronisation between two sides."),
        when: T("Solo en modelo a modelo; en los demás tipos es siempre de una dirección.", "Only in model to model; other kinds are always one-way."),
        card: "1 definición ↔ 1 direccionalidad",
        variants: [
          { n: T("Unidireccional", "Unidirectional"), d: T("Solo reglas de ida; es el valor por defecto", "Forward rules only; the default"), req: "ALTERNATIVE" },
          { n: T("Bidireccional", "Bidirectional"), d: T("Permite reglas de ida y de vuelta emparejadas; exige rastro activo", "Allows paired forward and backward rules; requires the trail to be enabled"), req: "ALTERNATIVE" }
        ],
        io: { i: T("Elección en el diseñador", "Choice in the designer"), o: T("Reglas con dirección y correspondencia", "Rules with direction and correspondence") },
        effect: T("Al pasar a bidireccional se asigna dirección y correspondencia a las reglas y se activa el rastro; al volver a unidireccional las reglas de vuelta quedan desactivadas.", "Switching to bidirectional assigns direction and correspondence to rules and enables the trail; switching back disables backward rules."),
        func: { rel: [T("Habilita la política de sincronización y las reglas de vuelta", "Enables the synchronisation policy and backward rules")],
                lifecycle: T("unidireccional ⇄ bidireccional antes de guardar", "unidirectional ⇄ bidirectional before saving"),
                cond: T("Bidireccional solo con tipo modelo a modelo y con el rastro activo", "Bidirectional only with model-to-model kind and the trail enabled") },
        tech: { type: "Directionality", schema: "UNIDIRECTIONAL|BIDIRECTIONAL", evidence: [ev(J + "TransformationDefinitionChecker.java", 73, "BIDIRECTIONAL only M2M"), ev(J + "TransformationDefinitionChecker.java", 80, "needs trace enabled")] } },

      { id: "endpoint", n: T("Extremo (origen o destino)", "End (source or target)"), kind: "concept", req: "MANDATORY",
        mean: T("Un metamodelo que actúa como origen o como destino de la transformación.", "A metamodel acting as source or target of the transformation."),
        purpose: T("Fija sobre qué tipos y atributos se escriben las reglas.", "Fixes over which types and attributes the rules are written."),
        when: T("Al elegir los metamodelos en el primer paso del diseñador.", "When choosing metamodels in the first designer step."),
        card: "1 definición ↔ 1..N orígenes, 0..N destinos",
        variants: [
          { n: T("Origen", "Source"), d: T("Metamodelo del modelo de entrada; es el valor por defecto de un extremo", "Metamodel of the input model; the default for an end"), req: "ALTERNATIVE" },
          { n: T("Destino", "Target"), d: T("Metamodelo del modelo de salida (solo modelo a modelo)", "Metamodel of the output model (model to model only)"), req: "CONDITIONAL" }
        ],
        props: [
          { n: T("Metamodelo", "Metamodel"), req: "MANDATORY", d: T("Cuál es el metamodelo referenciado", "Which metamodel is referenced") },
          { n: T("Huella del metamodelo", "Metamodel fingerprint"), req: "OPTIONAL", d: T("Permite detectar que el metamodelo cambió", "Lets you detect that the metamodel changed") },
          { n: T("Alias", "Alias"), req: "OPTIONAL", d: T("Nombre corto para referirse al extremo", "Short name to refer to the end") },
          { n: T("Obligatorio", "Required"), req: "OPTIONAL", d: T("Si el extremo debe estar presente al ejecutar", "Whether the end must be present when running") }
        ],
        io: { i: T("Metamodelo guardado", "Saved metamodel"), o: T("Tipos y atributos disponibles en las reglas", "Types and attributes available in rules") },
        effect: T("El modelo producido usa el metamodelo del extremo destino; si se ejecuta al revés, origen y destino se intercambian.", "The produced model uses the target end's metamodel; when run backwards, source and target are swapped."),
        func: { rel: [T("Referencia 1 metamodelo; lo usan 0..N reglas", "References 1 metamodel; used by 0..N rules")],
                lifecycle: T("elegido → referenciado por reglas → intercambiado al ejecutar al revés", "chosen → referenced by rules → swapped when run backwards"),
                cond: T("Modelo a modelo: ≥1 origen y ≥1 destino; modelo a texto: ≥1 origen", "Model to model: ≥1 source and ≥1 target; model to text: ≥1 source") },
        tech: { type: "TransformationEndpoint", schema: "id, role, metamodelId, metamodelFingerprint, alias, required", evidence: [ev(J + "TransformationDefinitionChecker.java", 87, "SOURCE_REQUIRED/TARGET_REQUIRED")] } },

      { id: "rule", n: T("Regla de transformación", "Transformation rule"), kind: "concept", req: "CONDITIONAL",
        mean: T("Dice qué tipo de elemento origen produce uno o varios elementos destino y cómo se rellenan.", "Says which kind of source element produces one or more target elements and how they are filled."),
        purpose: T("Es la unidad de lógica de una transformación modelo a modelo.", "It is the unit of logic of a model-to-model transformation."),
        when: T("Una por cada par tipo origen → tipo destino que quieras convertir.", "One for each source type → target type pair you want to convert."),
        card: "1 regla ↔ 1 tipo origen, 1..N patrones destino, 0..N asignaciones",
        variants: [
          { n: T("Regla de ida", "Forward rule"), d: T("Convierte origen en destino; es el valor por defecto", "Converts source into target; the default"), req: "ALTERNATIVE" },
          { n: T("Regla de vuelta", "Backward rule"), d: T("Convierte destino en origen; solo con transformación bidireccional", "Converts target into source; only with a bidirectional transformation"), req: "CONDITIONAL" }
        ],
        props: [
          { n: T("Nombre", "Name"), req: "OPTIONAL", d: T("Por defecto «Origen to Destino»", "Defaults to “Source to Target”") },
          { n: T("Activa", "Enabled"), req: "OPTIONAL", d: T("Una regla desactivada no se aplica", "A disabled rule is not applied") },
          { n: T("Extremo y tipo origen", "Source end and type"), req: "MANDATORY", d: T("Qué elementos dispara la regla", "Which elements trigger the rule") },
          { n: T("Condición (guarda)", "Condition (guard)"), req: "OPTIONAL", d: T("Expresión booleana: la regla solo se aplica si es verdadera", "Boolean expression: the rule only applies if true") },
          { n: T("Identificador de correspondencia", "Correspondence identifier"), req: "CONDITIONAL", d: T("Empareja una regla de ida con su regla de vuelta; si falta se usa la propia identidad de la regla", "Pairs a forward rule with its backward rule; if blank the rule's own identity is used") }
        ],
        io: { i: T("Elemento origen del tipo indicado", "Source element of the given type"), o: T("Elementos destino con atributos asignados", "Target elements with assigned attributes") },
        effect: T("Por cada elemento origen del tipo indicado cuya condición se cumple se crea un elemento destino por patrón, con un identificador estable.", "For each source element of the given type whose condition holds, one target element per pattern is created, with a stable identifier."),
        func: { rel: [T("Pertenece a 1 definición; tiene 1..N patrones destino y 0..N asignaciones", "Belongs to 1 definition; has 1..N target patterns and 0..N assignments"), T("Una regla de ida ↔ 0..1 regla de vuelta (misma correspondencia)", "A forward rule ↔ 0..1 backward rule (same correspondence)")],
                lifecycle: T("creada → activada/desactivada → eliminada", "created → enabled/disabled → deleted"),
                cond: T("Modelo a modelo: el extremo origen debe existir, el tipo origen debe existir y debe haber ≥1 patrón destino; las reglas de vuelta exigen bidireccional y emparejamiento completo", "Model to model: the source end and type must exist and there must be ≥1 target pattern; backward rules require bidirectional and complete pairing") },
        tech: { type: "TransformationRule", schema: "id, name, enabled, sourceEndpointId, sourceTypeId, guard, targets, bindings, direction, correspondenceId", evidence: [ev(J + "core/TransformationRule.java", 12, "record"), ev(J + "TransformationDefinitionChecker.java", 203, "REVERSE needs BIDIRECTIONAL"), ev(J + "TransformationDefinitionChecker.java", 300, "RULE_TARGET_REQUIRED")] } },

      { id: "targetpattern", n: T("Patrón destino", "Target pattern"), kind: "concept", req: "MANDATORY",
        mean: T("Describe un elemento que la regla crea en el destino: de qué extremo y de qué tipo.", "Describes an element the rule creates in the target: of which end and which type."),
        purpose: T("Permite que una regla cree varios elementos y que las asignaciones se dirijan a uno concreto por su alias.", "Lets a rule create several elements and lets assignments address a specific one by its alias."),
        when: T("Al elegir la clase destino de una regla.", "When picking the target class of a rule."),
        card: "1 regla ↔ 1..N patrones",
        props: [
          { n: T("Extremo destino", "Target end"), req: "MANDATORY", d: T("En qué metamodelo se crea", "In which metamodel it is created") },
          { n: T("Tipo destino", "Target type"), req: "MANDATORY", d: T("Qué clase de elemento se crea", "Which kind of element is created") },
          { n: T("Alias", "Alias"), req: "MANDATORY", d: T("Nombre único dentro de la regla para dirigir las asignaciones", "Unique name inside the rule to address assignments") }
        ],
        io: { i: T("Regla que se aplica", "Rule being applied"), o: T("Un elemento destino por patrón", "One target element per pattern") },
        effect: T("Cada patrón produce un elemento destino con identificador determinista.", "Each pattern yields a target element with a deterministic identifier."),
        func: { rel: [T("Referenciado por 0..N asignaciones mediante su alias", "Referenced by 0..N assignments through its alias")],
                lifecycle: T("creado con la regla → eliminado con la regla", "created with the rule → deleted with it"),
                cond: T("El extremo y el tipo se comprueban contra los metamodelos", "End and type are checked against the metamodels") },
        tech: { type: "TransformationTargetPattern", schema: "id, endpointId, typeId, alias", evidence: [ev(J + "TransformationDefinitionChecker.java", 291, "pattern endpoint check"), ev(J + "TransformationDefinitionChecker.java", 297, "pattern type check")] } },

      { id: "binding", n: T("Asignación de atributo o relación", "Attribute or relation assignment"), kind: "variant-set", req: "REPEATABLE",
        mean: T("Dice cómo se obtiene un valor (o una relación) del elemento destino.", "Says how a value (or relation) of the target element is obtained."),
        purpose: T("Rellena el elemento creado: copiando, fijando, calculando o enlazando.", "Fills the created element: copying, fixing, computing or linking."),
        when: T("Una por cada atributo destino que quieras rellenar.", "One for each target attribute you want to fill."),
        card: "1 regla ↔ 0..N asignaciones; 1 atributo destino ↔ 0..1 asignación",
        variants: [
          { n: T("Copia directa", "Direct copy"), d: T("Toma el valor de un atributo origen; es el valor por defecto", "Takes the value of a source attribute; the default"), req: "ALTERNATIVE" },
          { n: T("Valor fijo", "Fixed value"), d: T("Escribe siempre el mismo valor literal", "Always writes the same literal value"), req: "ALTERNATIVE" },
          { n: T("Expresión", "Expression"), d: T("Calcula el valor con una expresión construida en el diseñador", "Computes the value with an expression built in the designer"), req: "ALTERNATIVE" },
          { n: T("Correspondencia de relación", "Relation correspondence"), d: T("Crea la relación equivalente entre los elementos destino correspondientes; solo se crea desde el panel de relaciones", "Creates the equivalent relation between corresponding target elements; only created from the relations panel"), req: "ALTERNATIVE" }
        ],
        props: [
          { n: T("Alias y atributo destino", "Target alias and attribute"), req: "MANDATORY", d: T("Qué elemento y qué atributo se rellenan", "Which element and attribute are filled") },
          { n: T("Atributo origen", "Source attribute"), req: "CONDITIONAL", d: T("Solo en la copia directa", "Only for direct copy") },
          { n: T("Valor literal", "Literal value"), req: "CONDITIONAL", d: T("Solo en el valor fijo", "Only for fixed value") },
          { n: T("Expresión", "Expression"), req: "CONDITIONAL", d: T("Solo en la asignación por expresión; no puede faltar", "Only for expression assignments; cannot be missing") },
          { n: T("Política de valores", "Value policy"), req: "OPTIONAL", d: T("Cómo se reduce, convierte y trata el error", "How it is reduced, converted and how errors are treated") }
        ],
        io: { i: T("Elemento origen y su valor", "Source element and its value"), o: T("Atributo o relación del elemento destino", "Attribute or relation of the target element") },
        effect: T("Si un valor falla y la política es parar, la ejecución se detiene; con «omitir» el valor se salta; con «valor de reserva» se usa el de reserva.", "If a value fails and the policy is stop, the run halts; with “skip” the value is skipped; with “fallback” the fallback is used."),
        func: { rel: [T("Se dirige a 1 patrón destino por alias; 0..1 política de valores", "Addresses 1 target pattern by alias; 0..1 value policy")],
                lifecycle: T("creada → editada → eliminada", "created → edited → deleted"),
                cond: T("La copia directa exige atributos origen y destino conocidos; no se admiten dos asignaciones al mismo atributo destino", "Direct copy requires known source and target attributes; two assignments to the same target attribute are rejected") },
        tech: { type: "TransformationBinding / BindingKind", schema: "DIRECT|CONSTANT|EXPRESSION|TRACE_EQUIVALENT", evidence: [ev(J + "core/TransformationBinding.java", 11, "record"), ev(J + "TransformationDefinitionChecker.java", 340, "DIRECT checks"), ev(J + "TransformationDefinitionChecker.java", 385, "EXPRESSION needs expression"), ev(J + "TransformationDefinitionChecker.java", 323, "duplicate bindings")] } },

      { id: "valuepolicy", n: T("Política de valores", "Value policy"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Reglas para adaptar un valor antes de escribirlo: qué hacer con listas, de qué tipo convertirlo y qué hacer si falla.", "Rules to adapt a value before writing it: what to do with lists, which type to convert it to and what to do if it fails."),
        purpose: T("Absorbe diferencias de tipo y de cardinalidad entre origen y destino.", "Absorbs type and cardinality differences between source and target."),
        when: T("Cuando origen y destino no coinciden en tipo o en número de valores.", "When source and destination differ in type or number of values."),
        card: "1 asignación ↔ 0..1 política",
        variants: [
          { n: T("Colección: conservar / primero / último / contar", "Collection: preserve / first / last / count"), d: T("Qué hacer cuando el origen tiene varios valores", "What to do when the source has several values"), req: "ALTERNATIVE" },
          { n: T("Conversión: ninguna / a texto / a entero / a entero largo / a decimal / a booleano", "Conversion: none / to text / to integer / to long / to decimal / to boolean"), d: T("Tipo al que se convierte el valor", "Type the value is converted to"), req: "ALTERNATIVE" },
          { n: T("Error: parar / omitir valor / usar reserva", "Error: stop / skip value / use fallback"), d: T("Qué hacer si el valor no se puede obtener; por defecto parar", "What to do if the value cannot be obtained; stop by default"), req: "ALTERNATIVE" }
        ],
        props: [
          { n: T("Correspondencia de enumerados", "Enumeration mapping"), req: "OPTIONAL", d: T("Traduce valores de un enumerado a otros", "Translates values of one enumeration into others") },
          { n: T("Valor de reserva", "Fallback value"), req: "CONDITIONAL", d: T("Solo se admite con el tratamiento «usar reserva»", "Only accepted with the “use fallback” treatment") }
        ],
        io: { i: T("Valor obtenido del origen", "Value obtained from the source"), o: T("Valor adaptado o salto del valor", "Adapted value or skipped value") },
        effect: T("Orden: reducir colección, correspondencia de enumerados, conversión y, por último, tratamiento del error.", "Order: reduce collection, enumeration mapping, conversion and finally error treatment."),
        func: { rel: [T("Pertenece a 0..1 asignación", "Belongs to 0..1 assignment")],
                lifecycle: T("definida con la asignación", "defined with the assignment"),
                cond: T("El valor de reserva solo vale con «usar reserva»", "The fallback only applies with “use fallback”") },
        tech: { type: "TransformationValuePolicy", schema: "CollectionMode, ConversionMode, BindingErrorPolicy, enumMapping, fallbackValue", evidence: [ev(J + "core/TransformationValuePolicy.java", 8, "record"), ev(J + "CanonicalTransformationRuntime.java", 144, "fallback only with USE_FALLBACK")] } },

      { id: "genrule", n: T("Regla de generación (modelo a texto)", "Generation rule (model to text)"), kind: "concept", req: "CONDITIONAL",
        mean: T("Dice qué fichero de texto se genera por cada elemento de una clase y con qué contenido.", "Says which text file is generated for each element of a class and with what content."),
        purpose: T("Es la unidad de lógica de una transformación modelo a texto.", "It is the unit of logic of a model-to-text transformation."),
        when: T("Una por cada clase de contexto sobre la que quieras generar ficheros.", "One for each context class over which you want to generate files."),
        card: "1 definición M2T ↔ 1..N reglas de generación",
        variants: [
          { n: T("Si el fichero ya existe: fallar", "If the file already exists: fail"), d: T("Dos reglas que generan la misma ruta provocan error; es el valor por defecto", "Two rules generating the same path cause an error; the default"), req: "ALTERNATIVE" },
          { n: T("Si el fichero ya existe: reemplazar", "If the file already exists: replace"), d: T("El último contenido sustituye al anterior", "The last content replaces the previous one"), req: "ALTERNATIVE" }
        ],
        props: [
          { n: T("Clase de contexto", "Context class"), req: "MANDATORY", d: T("Por cada elemento de esta clase se genera un fichero", "A file is generated for each element of this class") },
          { n: T("Ruta de salida", "Output path"), req: "MANDATORY", d: T("Plantilla relativa y portable; por defecto «generated/{{atributo}}.txt»", "Relative, portable template; by default “generated/{{attribute}}.txt”") },
          { n: T("Plantilla de contenido", "Content template"), req: "MANDATORY", d: T("Texto con marcas: identificador, clase y atributos del elemento", "Text with tokens: identifier, class and attributes of the element") },
          { n: T("Condición (guarda)", "Condition (guard)"), req: "OPTIONAL", d: T("Debe ser booleana; si es falsa no se genera", "Must be boolean; if false nothing is generated") },
          { n: T("Activa", "Enabled"), req: "OPTIONAL", d: T("Una regla desactivada no genera", "A disabled rule does not generate") }
        ],
        io: { i: T("Elementos de la clase de contexto", "Elements of the context class"), o: T("Ficheros con ruta y contenido", "Files with path and content") },
        effect: T("Cada elemento que cumple la condición produce un fichero; la ruta debe ser relativa y portable.", "Each element meeting the condition produces a file; the path must be relative and portable."),
        func: { rel: [T("Parte de 1 extremo origen y 1 clase de contexto", "Starts from 1 source end and 1 context class")],
                lifecycle: T("creada → editada → activada/desactivada → eliminada", "created → edited → enabled/disabled → deleted"),
                cond: T("Identificador único, clase existente, ruta obligatoria y portable; al menos una regla de generación en la definición", "Unique identifier, existing class, mandatory portable path; at least one generation rule in the definition") },
        tech: { type: "TransformationGenerationRule", schema: "id, sourceEndpointId, contextTypeId, guard, outputPathTemplate, contentTemplate, overwritePolicy", evidence: [ev(J + "core/TransformationGenerationRule.java", 9, "record"), ev(J + "TransformationDefinitionChecker.java", 117, "id unique"), ev(J + "TransformationDefinitionChecker.java", 124, "path required/portable"), ev(J + "text/CanonicalTextGenerationRuntime.java", 42, "duplicate path with FAIL")] } },

      { id: "t2m", n: T("Configuración de texto a modelo", "Text-to-model configuration"), kind: "concept", req: "CONDITIONAL",
        mean: T("Indica con qué gramática se lee el texto y, opcionalmente, qué transformación posterior lo convierte en el modelo final.", "Indicates which grammar reads the text and, optionally, which follow-up transformation turns it into the final model."),
        purpose: T("Permite importar texto escrito en un lenguaje propio como modelo.", "Lets you import text written in your own language as a model."),
        when: T("Solo en transformaciones de tipo texto a modelo.", "Only in text-to-model transformations."),
        card: "1 definición T2M ↔ 1 gramática, 0..1 transformación posterior",
        props: [
          { n: T("Gramática", "Grammar"), req: "MANDATORY", d: T("La gramática guardada que interpreta el texto", "The saved grammar that interprets the text") },
          { n: T("Ruta lógica del texto", "Logical text path"), req: "OPTIONAL", d: T("Por defecto «model.hydra»", "Defaults to “model.hydra”") },
          { n: T("Transformación posterior", "Follow-up transformation"), req: "OPTIONAL", d: T("Si se deja vacía, el resultado es el modelo leído sin más proyección", "If left blank, the result is the parsed model with no further projection") }
        ],
        io: { i: T("Texto fuente", "Source text"), o: T("Modelo leído, o modelo proyectado", "Parsed model, or projected model") },
        effect: T("Si el texto no se puede leer con la gramática no se obtiene ningún modelo.", "If the text cannot be read with the grammar, no model is produced."),
        func: { rel: [T("Referencia 1 gramática y 0..1 transformación modelo a modelo posterior", "References 1 grammar and 0..1 follow-up model-to-model transformation")],
                lifecycle: T("configurada → ejecutada sobre un texto", "configured → run over a text"),
                cond: T("Sin gramática la comprobación falla; la comprobación de que la posterior sea modelo a modelo no está verificada", "Without a grammar the check fails; that the follow-up is model to model is not verified") },
        tech: { type: "TransformationTextToModelConfiguration", schema: "grammarId, relativePath, postTransformationId", evidence: [ev(J + "textmodel/TransformationTextToModelService.java", 36, "no post = parsed model"), ev(J + "TransformationDefinitionChecker.java", 102, "GRAMMAR_REQUIRED")] } },

      { id: "execpolicy", n: T("Política de ejecución", "Execution policy"), kind: "concept", req: "OPTIONAL",
        mean: T("Opciones de cómo se ejecuta: modo, determinismo y tratamiento de errores de asignación.", "Options of how it runs: mode, determinism and handling of assignment errors."),
        purpose: T("Declara garantías de ejecución.", "Declares execution guarantees."),
        when: T("Normalmente se deja con los valores por defecto.", "Normally left at its default values."),
        card: "1 definición ↔ 1 política",
        props: [
          { n: T("Modo (por lotes / incremental)", "Mode (batch / incremental)"), req: "OPTIONAL", d: T("Hoy no tiene efecto: el modo incremental se pide al sincronizar", "Has no effect today: incremental mode is requested when synchronising") },
          { n: T("Determinista", "Deterministic"), req: "OPTIONAL", d: T("Debe ser verdadero; falso se rechaza", "Must be true; false is rejected") },
          { n: T("Fallar ante error de asignación", "Fail on assignment error"), req: "OPTIONAL", d: T("Debe ser verdadero; falso se rechaza", "Must be true; false is rejected") }
        ],
        io: { i: T("Valores elegidos", "Chosen values"), o: T("Comprobación aceptada o rechazada", "Check accepted or rejected") },
        effect: T("En la práctica solo admite el valor por defecto.", "In practice only the default value is accepted."),
        func: { rel: [T("Pertenece a 1 definición", "Belongs to 1 definition")], lifecycle: T("fija durante la vida de la definición", "fixed through the life of the definition"), cond: T("Determinista y fallar ante error deben ser verdaderos", "Deterministic and fail-on-error must be true") },
        tech: { type: "TransformationExecutionPolicy", schema: "mode, deterministic, failOnBindingError", evidence: [ev(J + "TransformationDefinitionChecker.java", 62, "rejects false values"), ev(J + "core/TransformationExecutionPolicy.java", 3, "record")] } },

      { id: "tracepolicy", n: T("Rastro de correspondencias", "Correspondence trail"), kind: "concept", req: "OPTIONAL",
        mean: T("Opción que guarda qué elemento destino salió de qué elemento origen y de qué regla, con detalle opcional por asignación.", "Option that records which target element came from which source element and rule, with optional per-assignment detail."),
        purpose: T("Permite saber el origen de cada cosa y es la base de la sincronización.", "Lets you know where each thing came from and is the basis of synchronisation."),
        when: T("Activo por defecto; obligatorio en transformaciones bidireccionales.", "On by default; mandatory in bidirectional transformations."),
        card: "1 ejecución ↔ 1 rastro ↔ 0..N enlaces",
        props: [
          { n: T("Activo", "Enabled"), req: "OPTIONAL", d: T("Por defecto sí; sin rastro no hay enlaces", "Yes by default; without a trail there are no links") },
          { n: T("Conservar detalle por asignación", "Keep per-assignment detail"), req: "OPTIONAL", d: T("Por defecto sí; guarda de dónde salió cada valor", "Yes by default; records where each value came from") }
        ],
        variants: [
          { n: T("Enlace", "Link"), d: T("Elemento origen, regla, alias destino, elemento destino, correspondencia y dirección", "Source element, rule, target alias, target element, correspondence and direction"), req: "REPEATABLE" },
          { n: T("Detalle de asignación", "Assignment detail"), d: T("Regla, asignación, tipo y atributos origen y destino; solo si el rastro y el detalle están activos", "Rule, assignment, kind and source and target attributes; only if both trail and detail are enabled"), req: "CONDITIONAL" }
        ],
        io: { i: T("Ejecución de la transformación", "Transformation run"), o: T("Rastro: identificador de ejecución, modelos, dirección, enlaces y detalles", "Trail: run identifier, models, direction, links and details") },
        effect: T("Los elementos generados llevan anotaciones de la regla y del origen; el modelo lleva la transformación, la ejecución, el modelo origen y la dirección.", "Generated elements carry annotations of the rule and source; the model carries the transformation, run, source model and direction."),
        func: { rel: [T("Producido por 1 ejecución; lo consumen la sincronización y las sesiones", "Produced by 1 run; consumed by synchronisation and sessions")],
                lifecycle: T("generado en cada ejecución → guardado como línea base de sesión", "generated in each run → stored as a session baseline"),
                cond: T("Bidireccional exige rastro activo; la actualización sincronizada puede exigir rastro previo", "Bidirectional requires an enabled trail; synchronised updates can require a previous trail") },
        tech: { type: "TracePolicy / TransformationTrace / TraceLink / BindingTrace", schema: "enabled, retainBindingDetails; links, bindingTraces", evidence: [ev(J + "CanonicalTransformationRuntime.java", 70, "BindingTrace conditions"), ev(J + "CanonicalTransformationRuntime.java", 63, "output annotations")] } },

      { id: "syncpolicy", n: T("Política de sincronización", "Synchronisation policy"), kind: "variant-set", req: "CONDITIONAL",
        mean: T("Decide qué ocurre cuando los dos lados cambian a la vez, si se propagan los borrados y si hace falta un rastro previo.", "Decides what happens when both sides change at once, whether deletions propagate and whether a previous trail is needed."),
        purpose: T("Hace predecible la sincronización entre los dos modelos.", "Makes synchronisation between the two models predictable."),
        when: T("Solo tiene sentido en transformaciones bidireccionales.", "Only meaningful in bidirectional transformations."),
        card: "1 definición ↔ 1 política",
        variants: [
          { n: T("Parar ante conflicto", "Stop on conflict"), d: T("No aplica nada y devuelve el conflicto; es el valor por defecto", "Applies nothing and reports the conflict; the default"), req: "ALTERNATIVE" },
          { n: T("Gana el lado origen", "Source side wins"), d: T("Se descartan los cambios conflictivos del destino", "Conflicting target changes are dropped"), req: "ALTERNATIVE" },
          { n: T("Gana el lado destino", "Target side wins"), d: T("Se descartan los cambios conflictivos del origen", "Conflicting source changes are dropped"), req: "ALTERNATIVE" }
        ],
        props: [
          { n: T("Propagar borrados", "Propagate deletions"), req: "OPTIONAL", d: T("Por defecto no", "No by default") },
          { n: T("Exigir rastro previo para actualizar", "Require previous trail for updates"), req: "OPTIONAL", d: T("Por defecto sí", "Yes by default") }
        ],
        io: { i: T("Cambios en ambos lados", "Changes on both sides"), o: T("Resultado fusionado, bloqueado o sin cambios", "Merged, blocked or unchanged result") },
        effect: T("Con «parar ante conflicto» la sincronización incremental queda bloqueada sin error; la ejecución por lotes en ambas direcciones sí lanza error.", "With “stop on conflict” incremental synchronisation is blocked without an error; batch run in both directions does raise an error."),
        func: { rel: [T("Pertenece a 1 definición bidireccional; la aplican las sesiones", "Belongs to 1 bidirectional definition; applied by sessions")],
                lifecycle: T("definida → aplicada en cada sincronización", "defined → applied at each synchronisation"),
                cond: T("Solo se aplica cuando ambos lados cambiaron", "Only applied when both sides changed") },
        tech: { type: "SynchronizationPolicy / ConflictPolicy", schema: "FAIL_ON_CONFLICT|FORWARD_WINS|REVERSE_WINS; propagateDeletions; requireTraceForUpdates", evidence: [ev(J + "core/TransformationSynchronizationPolicy.java", 13, "default FAIL_ON_CONFLICT")] } },

      { id: "delta", n: T("Cambios entre versiones (delta)", "Changes between versions (delta)"), kind: "derived", req: "DERIVED",
        mean: T("La lista de lo que se añadió, quitó o modificó en un modelo respecto a su línea base.", "The list of what was added, removed or modified in a model compared with its baseline."),
        purpose: T("Permite propagar solo lo que cambió en lugar de rehacerlo todo.", "Lets you propagate only what changed instead of redoing everything."),
        when: T("En cada sincronización incremental.", "At every incremental synchronisation."),
        card: "1 sincronización ↔ 1 delta por lado",
        variants: [
          { n: T("Elementos añadidos / quitados / modificados", "Elements added / removed / modified"), d: T("Se detectan por identidad; cambia si varían atributos, clase o anotaciones", "Detected by identity; changed if attributes, class or annotations differ"), req: "REPEATABLE" },
          { n: T("Atributos modificados", "Modified attributes"), d: T("Qué atributos concretos cambiaron", "Which specific attributes changed"), req: "REPEATABLE" },
          { n: T("Relaciones añadidas / quitadas / modificadas", "Relations added / removed / modified"), d: T("Cambios entre elementos", "Changes between elements"), req: "REPEATABLE" }
        ],
        io: { i: T("Línea base y modelo actual", "Baseline and current model"), o: T("Delta por lado", "Delta per side") },
        effect: T("Si ninguno de los dos lados cambió, la sincronización termina sin hacer nada.", "If neither side changed, synchronisation ends without doing anything."),
        func: { rel: [T("Calculado desde 1 línea base; alimenta la detección de conflictos", "Computed from 1 baseline; feeds conflict detection")], lifecycle: T("calculado en cada sincronización", "computed at every synchronisation"), cond: T("Exige línea base de ambos lados", "Requires a baseline of both sides") },
        tech: { type: "ModelDelta", schema: "added/removed/modified elements, modified attributes, relations", evidence: [ev(J + "TransformationIncrementalSynchronizationEngine.java", 39, "delta(baseline,current)"), ev(J + "TransformationIncrementalSynchronizationEngine.java", 357, "delta computation")] } },

      { id: "conflict", n: T("Conflicto de sincronización", "Synchronisation conflict"), kind: "derived", req: "DERIVED",
        mean: T("Un par de elementos que se cambiaron en ambos lados de forma incompatible.", "A pair of elements changed on both sides in an incompatible way."),
        purpose: T("Evita pisar cambios sin que el usuario lo decida.", "Avoids overwriting changes without the user's decision."),
        when: T("Cuando ambos lados cambiaron respecto a la línea base.", "When both sides changed compared with the baseline."),
        card: "1 sincronización ↔ 0..N conflictos",
        variants: [
          { n: T("Cambio estructural concurrente", "Concurrent structural change"), d: T("Se cambió la estructura del elemento emparejado en ambos lados", "The structure of the paired element was changed on both sides"), req: "ALTERNATIVE" },
          { n: T("Cambio de atributo concurrente", "Concurrent feature change"), d: T("Se cambió el mismo atributo en ambos lados", "The same attribute was changed on both sides"), req: "ALTERNATIVE" },
          { n: T("Altas concurrentes sin emparejar", "Concurrent unpaired additions"), d: T("Ambos lados añadieron elementos de tipos coincidentes: es ambiguo", "Both sides added elements of matching types: ambiguous"), req: "ALTERNATIVE" }
        ],
        io: { i: T("Deltas de ambos lados", "Deltas of both sides"), o: T("Lista de conflictos con motivo", "List of conflicts with reason") },
        effect: T("Con «parar ante conflicto» el resultado queda bloqueado; con «gana un lado» se descartan los cambios conflictivos del otro.", "With “stop on conflict” the result is blocked; with “a side wins” the other side's conflicting changes are dropped."),
        func: { rel: [T("Referencia 1 correspondencia, un elemento origen y uno destino", "References 1 correspondence, one source and one target element")], lifecycle: T("detectado → bloqueado o resuelto por la política", "detected → blocked or resolved by the policy"), cond: T("Solo si ambos lados cambiaron", "Only if both sides changed") },
        tech: { type: "SynchronizationConflict", schema: "correspondenceId, source/target element, features, reason", evidence: [ev(J + "TransformationIncrementalSynchronizationEngine.java", 191, "conflict detection"), ev(J + "TransformationIncrementalSynchronizationEngine.java", 205, "reasons")] } },

      { id: "session", n: T("Sesión de sincronización", "Synchronisation session"), kind: "concept", req: "OPTIONAL",
        mean: T("Un seguimiento persistente de un par de modelos sincronizados, con puntos de control a los que se puede volver.", "A persistent record of a pair of synchronised models, with checkpoints to which you can return."),
        purpose: T("Guarda la línea base de ambos lados y el rastro para sincronizar de forma incremental repetidamente.", "Stores the baseline of both sides and the trail to synchronise incrementally and repeatedly."),
        when: T("Cuando se quiere mantener dos modelos coherentes a lo largo del tiempo.", "When you want to keep two models consistent over time."),
        card: "1 sesión ↔ 1 transformación, 2..200 puntos de control",
        variants: [
          { n: T("Creada", "Created"), d: T("Revisión 1, sincronización completa desde el lado autoritativo", "Revision 1, full synchronisation from the authoritative side"), req: "ALTERNATIVE" },
          { n: T("Sincronizada", "Synchronised"), d: T("Sincronización incremental; sube la revisión solo si hubo cambios sin bloqueo", "Incremental synchronisation; the revision goes up only if there were unblocked changes"), req: "ALTERNATIVE" },
          { n: T("Rebasada", "Rebased"), d: T("Se rehace la línea base desde un lado elegido", "The baseline is rebuilt from a chosen side"), req: "ALTERNATIVE" },
          { n: T("Restaurada", "Restored"), d: T("Nueva revisión copiada de un punto de control anterior", "New revision copied from an earlier checkpoint"), req: "ALTERNATIVE" }
        ],
        props: [
          { n: T("Revisión", "Revision"), req: "DERIVED", d: T("Empieza en 1 y solo avanza si algo cambió y no se bloqueó", "Starts at 1 and only advances if something changed and was not blocked") },
          { n: T("Líneas base de origen y destino", "Source and target baselines"), req: "DERIVED", d: T("Los modelos en el último punto sincronizado", "The models at the last synchronised point") },
          { n: T("Puntos de control", "Checkpoints"), req: "DERIVED", d: T("Uno por revisión, con operación, dirección aplicada, deltas y conflictos", "One per revision, with operation, applied direction, deltas and conflicts") }
        ],
        io: { i: T("Dos modelos y la transformación", "Two models and the transformation"), o: T("Resultado de sincronización y nueva revisión", "Synchronisation result and new revision") },
        effect: T("Exige condición de versión en cada cambio, que la transformación no haya cambiado desde la sesión y que los modelos coincidan con la línea base.", "Requires a version precondition on each change, that the transformation has not changed since the session, and that models match the baseline."),
        func: { rel: [T("Referencia 1 transformación bidireccional; tiene 1..N puntos de control", "References 1 bidirectional transformation; has 1..N checkpoints")],
                lifecycle: T("creada → sincronizada* → rebasada / restaurada → eliminada; el historial se recorta entre 2 y 200 entradas", "created → synchronised* → rebased / restored → deleted; history is trimmed to between 2 and 200 entries"),
                cond: T("Si la sincronización queda bloqueada o no hay cambios, la revisión no avanza", "If synchronisation is blocked or there are no changes, the revision does not advance") },
        tech: { type: "TransformationSynchronizationSession / Checkpoint", schema: "schemaVersion 1.0; id, transformationId, transformationEtag, revision, baselines, baselineTrace; checkpoint id = sessionId + \".r\" + revision", evidence: [ev(J + "sync/TransformationSynchronizationSessionService.java", 59, "create"), ev(J + "sync/TransformationSynchronizationSessionService.java", 102, "synchronize"), ev(J + "sync/TransformationSynchronizationSessionService.java", 129, "rebase"), ev(J + "sync/TransformationSynchronizationSessionService.java", 166, "restore"), ev(J + "sync/TransformationSynchronizationSessionService.java", 222, "If-Match")] } },

      { id: "export", n: T("Exportación a otro lenguaje", "Export to another language"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Convierte la definición en ficheros de otro lenguaje de transformación o de programación.", "Turns the definition into files of another transformation or programming language."),
        purpose: T("Permite usar la lógica fuera de HYDRA y compararla con la ejecución propia.", "Lets you use the logic outside HYDRA and compare it with HYDRA's own execution."),
        when: T("Cuando necesitas el resultado en otra herramienta o lenguaje.", "When you need the result in another tool or language."),
        card: "1 definición ↔ 0..N exportaciones",
        variants: [
          { n: T("ATL y ETL", "ATL and ETL"), d: T("Lenguajes declarativos; exportación parcial; solo modelo a modelo", "Declarative languages; partial export; model to model only"), req: "ALTERNATIVE" },
          { n: T("Acceleo y EGL", "Acceleo and EGL"), d: T("Lenguajes de plantillas; exportación parcial; solo modelo a texto", "Template languages; partial export; model to text only"), req: "ALTERNATIVE" },
          { n: T("Java, Xtend, Python, Go, JavaScript, TypeScript", "Java, Xtend, Python, Go, JavaScript, TypeScript"), d: T("Código nativo para modelo a modelo y modelo a texto; exacto si se genera, parcial si queda bloqueado", "Native code for model to model and model to text; exact if generated, partial if blocked"), req: "ALTERNATIVE" },
          { n: T("Paquete con el motor de HYDRA", "Package with HYDRA's engine"), d: T("Los tres tipos de transformación; reproduce el motor propio", "All three kinds of transformation; reproduces HYDRA's own engine"), req: "ALTERNATIVE" }
        ],
        props: [
          { n: T("Fidelidad", "Fidelity"), req: "DERIVED", d: T("Exacta, motor propio o parcial", "Exact, own engine or partial") },
          { n: T("Dirección inversa", "Backward direction"), req: "CONDITIONAL", d: T("La exportación de vuelta solo existe para modelo a modelo", "Backward export only exists for model to model") }
        ],
        io: { i: T("Definición y lenguaje elegido", "Definition and chosen language"), o: T("Ficheros exportados y diagnósticos", "Exported files and diagnostics") },
        effect: T("El resultado lista ficheros (ruta y contenido) y diagnósticos con gravedad, código y solución.", "The result lists files (path and content) and diagnostics with severity, code and solution."),
        func: { rel: [T("Parte de 1 definición; puede ejecutarse después de forma nativa", "Starts from 1 definition; can then be run natively")], lifecycle: T("elegido el lenguaje → exportado → ejecutado → comparado", "language chosen → exported → run → compared"), cond: T("Cada lenguaje admite solo los tipos y direcciones indicados", "Each language supports only the stated kinds and directions") },
        tech: { type: "TransformationExportLanguage / ExportFidelity", schema: "ATL, ETL, JAVA, XTEND, ACCELEO, EGL, PYTHON, GO, JAVASCRIPT, TYPESCRIPT; EXACT|CANONICAL_RUNTIME|SUBSET", evidence: [ev(J + "export/TransformationExportService.java", 34, "supported kinds"), ev(J + "export/TransformationLanguageAdapterRegistry.java", 10, "registry")] } },

      { id: "runtime", n: T("Ejecución nativa y herramientas", "Native execution and tools"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Ejecuta lo exportado con una herramienta externa y muestra si esa herramienta está lista.", "Runs the export with an external tool and shows whether that tool is ready."),
        purpose: T("Verifica que la exportación se comporta como la transformación original.", "Verifies that the export behaves like the original transformation."),
        when: T("Después de exportar, para comprobar o ejecutar fuera de HYDRA.", "After exporting, to check or run outside HYDRA."),
        card: "1 lenguaje ↔ 1 estado de salud; 1 ejecución ↔ 1 estado",
        variants: [
          { n: T("Modo: exportación nativa / paquete del motor propio", "Mode: native export / own-engine package"), d: T("Lenguaje nativo o paquete con el motor de HYDRA", "Native language or package with HYDRA's engine"), req: "ALTERNATIVE" },
          { n: T("Origen de la herramienta: ninguno / comando explícito / paquete gestionado", "Tool source: none / explicit command / managed bundle"), d: T("De dónde sale la herramienta", "Where the tool comes from"), req: "ALTERNATIVE" },
          { n: T("Salud: desactivada / sin configurar / configuración inválida / sin verificar / lista / herramienta no disponible / incompatible / fallida", "Health: disabled / not configured / invalid configuration / unverified / ready / tool unavailable / incompatible / failed"), d: T("Si la herramienta se puede usar", "Whether the tool can be used"), req: "DERIVED" },
          { n: T("Estado de la ejecución: sin ejecutar / correcta / desactivada / sin configurar / capacidad ocupada / herramienta no disponible / tiempo agotado / fallida / resultado inválido / cancelada", "Run status: not run / succeeded / disabled / not configured / capacity busy / tool unavailable / timed out / failed / invalid result / cancelled"), d: T("Resultado de un intento de ejecución", "Outcome of a run attempt"), req: "DERIVED" },
          { n: T("Instalación de herramientas: instalada / actualizada / retirada / sin cambios / bloqueada / revertida", "Tool provisioning: installed / updated / removed / no change / blocked / rolled back"), d: T("Resultado de instalar o quitar una herramienta", "Outcome of installing or removing a tool"), req: "DERIVED" }
        ],
        props: [
          { n: T("Capacidad por lenguaje", "Capability per language"), req: "DERIVED", d: T("ATL y ETL: modelo a modelo, ida y vuelta; Acceleo y EGL: modelo a texto, solo ida; el resto: ambos tipos, ida y vuelta (vuelta solo en modelo a modelo)", "ATL and ETL: model to model, forward and backward; Acceleo and EGL: model to text, forward only; others: both kinds, forward and backward (backward only for model to model)") }
        ],
        io: { i: T("Exportación y herramienta instalada", "Export and installed tool"), o: T("Resultado nativo y estado", "Native result and status") },
        effect: T("La herramienta se lanza mediante un ejecutor controlado, nunca directamente.", "The tool is launched through a controlled executor, never directly."),
        func: { rel: [T("Depende de las cadenas de herramientas para instalar la herramienta", "Depends on toolchains to install the tool")], lifecycle: T("diagnosticada → lista → ejecutada → estado final", "diagnosed → ready → run → final status"), cond: T("Sin herramienta lista no se ejecuta; las banderas de bidireccionalidad completa y sincronización incremental son falsas en todas las entradas", "Without a ready tool nothing runs; full-bidirectionality and incremental-sync flags are false in all entries") },
        tech: { type: "RuntimeMode / RuntimeSource / RuntimeHealth / RuntimeStatus / ToolchainProvisioningStatus", schema: "see variants", evidence: [ev(J + "nativeexec/TransformationNativeRuntimeSupport.java", 25, "capability table"), ev(J + "nativeexec/TransformationNativeRuntimeExecutor.java", 30, "process runner")] } },

      { id: "parity", n: T("Paridad", "Parity"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Compara el resultado de HYDRA con el de la ejecución externa del código exportado.", "Compares HYDRA's result with that of the external run of the exported code."),
        purpose: T("Demuestra que la exportación es fiel.", "Shows that the export is faithful."),
        when: T("Tras exportar y ejecutar con una herramienta externa.", "After exporting and running with an external tool."),
        card: "1 exportación ↔ 1 resultado de paridad",
        variants: [
          { n: T("Coincide", "Matched"), d: T("Los dos resultados son iguales", "Both results are equal"), req: "ALTERNATIVE" },
          { n: T("No coincide", "Mismatched"), d: T("Los resultados difieren", "The results differ"), req: "ALTERNATIVE" },
          { n: T("Exportación bloqueada", "Export blocked"), d: T("La exportación no es compatible, no se puede comparar", "The export is not compatible, so it cannot be compared"), req: "ALTERNATIVE" },
          { n: T("Evidencia no válida", "Invalid evidence"), d: T("Lenguaje o tipo no admitido", "Unsupported language or kind"), req: "ALTERNATIVE" }
        ],
        io: { i: T("Exportación y resultado externo", "Export and external result"), o: T("Veredicto de paridad", "Parity verdict") },
        effect: T("En modelo a modelo se compara el modelo obtenido; en modelo a texto se comparan los ficheros.", "In model to model the obtained model is compared; in model to text the files are compared."),
        func: { rel: [T("Compara 1 ejecución propia con 1 ejecución externa", "Compares 1 own run with 1 external run")], lifecycle: T("exportado → ejecutado → comparado", "exported → run → compared"), cond: T("No confundir con la certificación de paridad de la integración tecnológica", "Not to be confused with the parity certification of technology integration") },
        tech: { type: "ParityStatus", schema: "MATCHED|MISMATCHED|EXPORT_BLOCKED|INVALID_EVIDENCE", evidence: [ev(J + "parity/TransformationParityService.java", 50, "parity logic"), ev(J + "nativeexec/TransformationNativeParityService.java", 51, "native parity")] } },

      { id: "testcase", n: T("Caso de prueba y cobertura", "Test case and coverage"), kind: "concept", req: "OPTIONAL",
        mean: T("Un ejemplo guardado de entrada y resultado esperado que se puede repetir, con un informe de qué reglas y asignaciones se ejercitaron.", "A stored example of input and expected result that can be repeated, with a report of which rules and assignments were exercised."),
        purpose: T("Protege la transformación frente a cambios no deseados.", "Protects the transformation against unwanted changes."),
        when: T("Cuando la transformación ya produce el resultado esperado.", "Once the transformation already yields the expected result."),
        card: "1 definición ↔ 0..N casos",
        variants: [
          { n: T("Aprobado", "Pass"), d: T("El resultado coincide con lo esperado", "The result matches the expectation"), req: "ALTERNATIVE" },
          { n: T("Fallido", "Fail"), d: T("El resultado difiere", "The result differs"), req: "ALTERNATIVE" },
          { n: T("Error", "Error"), d: T("No se pudo ejecutar el caso", "The case could not be run"), req: "ALTERNATIVE" }
        ],
        io: { i: T("Entrada y resultado esperado", "Input and expected result"), o: T("Veredicto y cobertura de reglas y asignaciones", "Verdict and rule and assignment coverage") },
        effect: T("Existe el mismo mecanismo para los tres tipos de transformación.", "The same mechanism exists for the three kinds of transformation."),
        func: { rel: [T("Pertenece a 1 definición", "Belongs to 1 definition")], lifecycle: T("creado → ejecutado → actualizado → eliminado", "created → run → updated → deleted"), cond: T("El informe distingue reglas y asignaciones cubiertas", "The report distinguishes covered rules and assignments") },
        tech: { type: "TransformationTestStatus", schema: "PASS|FAIL|ERROR; coverage report", evidence: [ev(J + "testing/TransformationTestService.java", 143, "dispatch"), ev(TT + "testing/TransformationTestServiceTests.java", 1, "tests")] } },

      { id: "diagnostics", n: T("Diagnósticos de calidad", "Quality diagnostics"), kind: "variant-set", req: "DERIVED",
        mean: T("Los avisos y errores que aparecen al comprobar la definición, con una explicación y una solución.", "The warnings and errors shown when checking the definition, with an explanation and a solution."),
        purpose: T("Guía al usuario a una definición correcta antes de guardar.", "Guides the user to a correct definition before saving."),
        when: T("Al pulsar «Comprobar» y antes de guardar.", "When pressing “Check” and before saving."),
        card: "1 comprobación ↔ 0..N diagnósticos",
        variants: [
          { n: T("Error", "Error"), d: T("Bloquea el guardado", "Blocks saving"), req: "ALTERNATIVE" },
          { n: T("Aviso", "Warning"), d: T("Informa sin bloquear; p. ej. un atributo obligatorio del destino sin asignar", "Informs without blocking; e.g. a mandatory target attribute left unmapped"), req: "ALTERNATIVE" },
          { n: T("Información", "Info"), d: T("Nota sin consecuencias", "Note with no consequences"), req: "ALTERNATIVE" }
        ],
        props: [
          { n: T("Código y ubicación", "Code and location"), req: "DERIVED", d: T("Qué regla, asignación y ruta lo originan", "Which rule, assignment and path cause it") },
          { n: T("Explicación y solución", "Explanation and solution"), req: "DERIVED", d: T("Título, mensaje y cómo arreglarlo", "Title, message and how to fix it") }
        ],
        io: { i: T("Definición", "Definition"), o: T("Informe válido o no", "Valid or invalid report") },
        effect: T("El informe es válido cuando no contiene ningún error. Hay correcciones automáticas (Quick Fix) solo para modelo a modelo.", "The report is valid when it contains no error. Automatic fixes (Quick Fix) exist only for model to model."),
        func: { rel: [T("Referencia 0..1 regla y 0..1 asignación", "References 0..1 rule and 0..1 assignment")], lifecycle: T("abierto → corregido → ausente", "open → fixed → gone"), cond: T("Códigos como falta de origen, de destino, de gramática o de asignación obligatoria", "Codes such as missing source, target, grammar or mandatory mapping") },
        tech: { type: "TransformationDiagnostic / DiagnosticSeverity", schema: "severity, code, ruleId, bindingId, path, title, message, solution", evidence: [ev(J + "TransformationDefinitionChecker.java", 87, "SOURCE_REQUIRED/TARGET_REQUIRED"), ev(J + "TransformationDefinitionChecker.java", 424, "REQUIRED_TARGET_ATTRIBUTE_UNMAPPED"), ev(J + "quickfix/TransformationQuickFixService.java", 43, "quick fix M2M only")] } }
    ],

    map: {
      nodes: [
        { id: "n_def", elem: "definition", n: T("Definición", "Definition"), k: "concept", req: "MANDATORY", d: T("El documento completo", "The complete document") },
        { id: "n_kind", elem: "kind", n: T("Tipo", "Kind"), k: "variant", req: "MANDATORY", d: T("Modelo a modelo, a texto o texto a modelo", "Model to model, to text or text to model") },
        { id: "n_end", elem: "endpoint", n: T("Extremo", "End"), k: "concept", req: "MANDATORY", d: T("Metamodelo de origen o destino", "Source or target metamodel") },
        { id: "n_meta", n: T("Metamodelo", "Metamodel"), k: "external", d: T("Referenciado por cada extremo", "Referenced by each end") },
        { id: "n_rule", elem: "rule", n: T("Regla", "Rule"), k: "concept", req: "CONDITIONAL", d: T("Tipo origen produce destino", "Source type produces target") },
        { id: "n_pat", elem: "targetpattern", n: T("Patrón destino", "Target pattern"), k: "concept", req: "MANDATORY", d: T("Elemento creado", "Element created") },
        { id: "n_bind", elem: "binding", n: T("Asignación", "Assignment"), k: "concept", req: "REPEATABLE", d: T("Cómo se rellena un atributo", "How an attribute is filled") },
        { id: "n_pol", elem: "valuepolicy", n: T("Política de valores", "Value policy"), k: "concept", req: "OPTIONAL", d: T("Colección, conversión, error", "Collection, conversion, error") },
        { id: "n_gen", elem: "genrule", n: T("Regla de generación", "Generation rule"), k: "concept", req: "CONDITIONAL", d: T("Fichero por elemento", "File per element") },
        { id: "n_t2m", elem: "t2m", n: T("Texto a modelo", "Text to model"), k: "concept", req: "CONDITIONAL", d: T("Gramática y proyección", "Grammar and projection") },
        { id: "n_gram", n: T("Gramática", "Grammar"), k: "external", d: T("Lee el texto", "Reads the text") },
        { id: "n_trace", elem: "tracepolicy", n: T("Rastro", "Trail"), k: "derived", req: "DERIVED", d: T("Enlaces origen-destino", "Source-target links") },
        { id: "n_sync", elem: "syncpolicy", n: T("Política de sincronización", "Synchronisation policy"), k: "concept", req: "CONDITIONAL", d: T("Qué hacer ante conflictos", "What to do about conflicts") },
        { id: "n_sess", elem: "session", n: T("Sesión", "Session"), k: "concept", req: "OPTIONAL", d: T("Línea base y puntos de control", "Baseline and checkpoints") },
        { id: "n_out", n: T("Modelo o ficheros resultado", "Resulting model or files"), k: "derived", d: T("Producto de ejecutar", "Product of running") },
        { id: "n_exp", elem: "export", n: T("Exportación", "Export"), k: "derived", req: "OPTIONAL", d: T("Otro lenguaje", "Another language") }
      ],
      edges: [
        { a: "n_def", b: "n_kind", rel: "contains", card1: "1", card2: "1", req: "MANDATORY" },
        { a: "n_def", b: "n_end", rel: "contains", card1: "1", card2: "1..N", req: "MANDATORY" },
        { a: "n_end", b: "n_meta", rel: "ref", card1: "N", card2: "1" },
        { a: "n_def", b: "n_rule", rel: "contains", card1: "1", card2: "0..N", req: "CONDITIONAL" },
        { a: "n_rule", b: "n_pat", rel: "contains", card1: "1", card2: "1..N", req: "MANDATORY" },
        { a: "n_rule", b: "n_bind", rel: "contains", card1: "1", card2: "0..N" },
        { a: "n_bind", b: "n_pol", rel: "contains", card1: "1", card2: "0..1", req: "OPTIONAL" },
        { a: "n_def", b: "n_gen", rel: "contains", card1: "1", card2: "0..N", req: "CONDITIONAL" },
        { a: "n_def", b: "n_t2m", rel: "contains", card1: "1", card2: "0..1", req: "CONDITIONAL" },
        { a: "n_t2m", b: "n_gram", rel: "ref", card1: "N", card2: "1", req: "MANDATORY" },
        { a: "n_def", b: "n_trace", rel: "contains", card1: "1", card2: "1" },
        { a: "n_def", b: "n_sync", rel: "contains", card1: "1", card2: "1", req: "CONDITIONAL" },
        { a: "n_sess", b: "n_def", rel: "ref", card1: "N", card2: "1", req: "OPTIONAL" },
        { a: "n_sess", b: "n_trace", rel: "ref", card1: "1", card2: "1" },
        { a: "n_def", b: "n_out", rel: "gen", card1: "1", card2: "N" },
        { a: "n_def", b: "n_exp", rel: "gen", card1: "1", card2: "0..N", req: "OPTIONAL" }
      ]
    },

    define: [
      { id: "guided", n: T("Asistente guiado del diseñador", "Designer guided mode"), kind: "wizard", req: "ALTERNATIVE",
        elems: ["definition", "kind", "directionality", "endpoint", "syncpolicy"],
        does: T("Se abre el diseñador en modo guiado, con tres pasos: ámbito, creación y revisión. En el ámbito escribes el nombre, eliges el tipo (modelo a modelo, a texto o texto a modelo) y, si es modelo a modelo, la direccionalidad. Si es bidireccional eliges qué ocurre ante cambios simultáneos (parar, gana el origen, gana el destino) y marcas «Propagar borrados» y «Exigir rastro previo». Después eliges el metamodelo origen y, en modelo a modelo, el destino. En la creación usas el espacio de trabajo que corresponde al tipo. En la revisión pulsas «Comprobar» y guardas.",
                "The designer opens in guided mode with three steps: scope, authoring and review. In scope you type the name, choose the kind (model to model, to text or text to model) and, for model to model, the directionality. If bidirectional you choose what happens on simultaneous changes (stop, source wins, target wins) and tick “Propagate deletions” and “Require previous trail for updates”. Then you pick the source metamodel and, for model to model, the target. In authoring you use the workspace matching the kind. In review you press “Check” and save."),
        func: T("Cambiar de tipo reajusta direccionalidad y configuración de texto; pasar a bidireccional fuerza el rastro. Guardar exige comprobación sin errores.", "Switching kind adjusts directionality and text configuration; bidirectional forces the trail. Saving requires an error-free check."),
        tech: T("TransformationDesigner.tsx (modo GUIDED por defecto, pasos scope/authoring/review), transformationDesignerModel.ts (valores por defecto y cambio de tipo).", "TransformationDesigner.tsx (GUIDED by default, steps scope/authoring/review), transformationDesignerModel.ts (defaults and kind switching)."),
        proc: "transformation_author_guided" },
      { id: "advanced", n: T("Modo avanzado", "Advanced mode"), kind: "form", req: "ALTERNATIVE",
        elems: ["definition", "execpolicy", "tracepolicy"],
        does: T("En modo avanzado ves a la vez las secciones de ámbito, espacio de trabajo, revisión, vista previa, exportación y pruebas, y puedes saltar entre ellas sin seguir un orden.",
                "In advanced mode you see the scope, workspace, review, preview, export and testing sections together and can jump between them in any order."),
        func: T("Es la misma definición que en modo guiado; solo cambia la presentación.", "It is the same definition as in guided mode; only the presentation changes."),
        tech: T("TransformationDesigner.tsx (modo avanzado, secciones).", "TransformationDesigner.tsx (advanced mode, sections).") },
      { id: "mapping", n: T("Espacio de mapeo (modelo a modelo)", "Mapping workspace (model to model)"), kind: "canvas", req: "CONDITIONAL",
        elems: ["rule", "targetpattern", "binding", "valuepolicy"],
        does: T("Eliges una clase origen y una clase destino y, si es bidireccional, si la regla va de ida o de vuelta. Se crea una regla «Origen to Destino». Mapeas atributos uno a uno o con «mapear nombres compatibles». Por cada asignación eliges si es copia, valor fijo o expresión y ajustas colección, conversión, tratamiento del error y valor de reserva. En el panel de relaciones creas las correspondencias de relación. Puedes añadir una condición a la regla, activarla o desactivarla y borrarla.",
                "You pick a source class and a target class and, if bidirectional, whether the rule goes forward or backward. A rule “Source to Target” is created. You map attributes one by one or with “map compatible names”. For each assignment you choose copy, fixed value or expression and adjust collection, conversion, error handling and fallback. In the relations panel you create relation correspondences. You can add a condition to the rule, enable or disable it and delete it."),
        func: T("En unidireccional las reglas de vuelta no se pueden crear; las asignaciones de relación solo se crean en el panel de relaciones.", "In unidirectional mode backward rules cannot be created; relation assignments are only created in the relations panel."),
        tech: T("TransformationMappingWorkspace.tsx, TransformationBindingRow.tsx, TransformationRelationBindings.tsx, ExpressionBuilder; createRule, setAttributeBinding, addDirectBinding, mapCompatibleNames, createGuard en transformationDesignerModel.ts.", "TransformationMappingWorkspace.tsx, TransformationBindingRow.tsx, TransformationRelationBindings.tsx, ExpressionBuilder; createRule, setAttributeBinding, addDirectBinding, mapCompatibleNames, createGuard in transformationDesignerModel.ts."),
        proc: "transformation_author_mapping" },
      { id: "generation", n: T("Espacio de generación (modelo a texto)", "Generation workspace (model to text)"), kind: "form", req: "CONDITIONAL",
        elems: ["genrule"],
        does: T("Añades una regla por cada clase de contexto. Cada regla nace con una ruta «generated/{{primer atributo}}.txt» y una plantilla de contenido; editas la plantilla con marcas de identificador, clase y atributo, y puedes activar o borrar la regla. No hay un control visible para elegir qué hacer si el fichero ya existe: queda en «fallar».",
                "You add one rule per context class. Each rule starts with a path “generated/{{first attribute}}.txt” and a content template; you edit the template with identifier, class and attribute tokens and can enable or delete the rule. There is no visible control for what to do if the file already exists: it stays at “fail”."),
        func: T("La política de sobrescritura no se verificó como editable en la interfaz.", "The overwrite policy was not verified as editable in the interface."),
        tech: T("TransformationGenerationWorkspace.tsx; createGenerationRule en transformationDesignerModel.ts.", "TransformationGenerationWorkspace.tsx; createGenerationRule in transformationDesignerModel.ts."),
        proc: "transformation_author_generation" },
      { id: "text2model", n: T("Espacio de texto a modelo", "Text-to-model workspace"), kind: "form", req: "CONDITIONAL",
        elems: ["t2m"],
        does: T("Eliges la gramática, escribes la ruta lógica del texto (por defecto «model.hydra») y, si quieres proyectar el resultado, eliges una transformación modelo a modelo posterior.",
                "You pick the grammar, type the logical text path (default “model.hydra”) and, if you want to project the result, choose a follow-up model-to-model transformation."),
        func: T("Sin gramática la comprobación falla.", "Without a grammar the check fails."),
        tech: T("TransformationTextToModelWorkspace.tsx.", "TransformationTextToModelWorkspace.tsx."),
        proc: "transformation_author_text2model" },
      { id: "review", n: T("Revisión, vista previa, exportación y pruebas", "Review, preview, export and testing"), kind: "form", req: "OPTIONAL",
        elems: ["diagnostics", "testcase", "export", "runtime", "parity", "session"],
        does: T("En la revisión pulsas «Comprobar» para ver diagnósticos y correcciones rápidas, previsualizas el resultado sobre un modelo real, exportas a otro lenguaje, ejecutas de forma nativa y comparas, y creas casos de prueba con su cobertura. En la vista previa también se gestionan las sesiones de sincronización y la instalación de herramientas.",
                "In review you press “Check” to see diagnostics and quick fixes, preview the result on a real model, export to another language, run natively and compare, and create test cases with their coverage. The preview also hosts synchronisation sessions and tool installation."),
        func: T("Las correcciones rápidas solo existen para modelo a modelo.", "Quick fixes only exist for model to model."),
        tech: T("TransformationPreviewPanel.tsx, TransformationExportPanel.tsx, TransformationTestingPanel.tsx, paneles de sesiones y aprovisionamiento.", "TransformationPreviewPanel.tsx, TransformationExportPanel.tsx, TransformationTestingPanel.tsx, sessions and provisioning panels.") },
      { id: "api", n: T("Interfaz programática", "Programmatic interface"), kind: "api", req: "ALTERNATIVE",
        elems: ["definition", "session", "testcase"],
        does: T("Las mismas operaciones del diseñador (listar, cargar, comprobar, crear, reemplazar, borrar, ejecutar, previsualizar, exportar, paridad, pruebas, sesiones) están disponibles para herramientas externas.",
                "The same operations as the designer (list, load, check, create, replace, delete, run, preview, export, parity, tests, sessions) are available to external tools."),
        func: T("Es el mismo contrato que usa el diseñador.", "It is the same contract the designer uses."),
        tech: T("frontend api.ts y controladores de transformaciones, pruebas, sesiones, paridad nativa y herramientas.", "frontend api.ts and controllers for transformations, tests, sessions, native parity and toolchains.") }
    ],

    rel: [
      { to: "model_editor", type: "required", dir: "in", u: T("Los modelos del editor son la entrada de la transformación.", "Editor models are the input of the transformation."),
        f: T("Sin un modelo de entrada no hay ejecución, vista previa ni sincronización.", "Without an input model there is no run, preview or synchronisation."),
        t: T("TransformationRuntimeService consume el modelo de entrada; sincronización exige ambos modelos.", "TransformationRuntimeService consumes the input model; synchronisation requires both models.") },
      { to: "metamodel", type: "required", dir: "out", u: T("Origen y destino se definen sobre metamodelos guardados.", "Source and target are defined over saved metamodels."),
        f: T("Los tipos y atributos de las reglas se validan contra el metamodelo; una huella detecta cambios.", "Rule types and attributes are validated against the metamodel; a fingerprint detects changes."),
        t: T("TransformationEndpoint.metamodelId/metamodelFingerprint; el modelo producido usa el metamodelo del destino.", "TransformationEndpoint.metamodelId/metamodelFingerprint; the produced model uses the target metamodel.") },
      { to: "grammar", type: "conditional", dir: "out", u: T("Texto a modelo lee el texto con una gramática.", "Text to model reads the text with a grammar."),
        f: T("Solo en el tipo texto a modelo; sin gramática la comprobación falla.", "Only for the text-to-model kind; without a grammar the check fails."),
        t: T("GrammarTransformationTextParserGateway; GRAMMAR_REQUIRED.", "GrammarTransformationTextParserGateway; GRAMMAR_REQUIRED.") },
      { to: "toolchains", type: "conditional", dir: "out", u: T("La ejecución nativa necesita una herramienta externa instalada.", "Native execution needs an installed external tool."),
        f: T("Solo si se ejecuta lo exportado fuera de HYDRA.", "Only if the export is run outside HYDRA."),
        t: T("TransformationToolchainProvisioningController; HydraExternalProcessRunner.", "TransformationToolchainProvisioningController; HydraExternalProcessRunner.") }
    ]
  });

  // -------------------------------------------------------------------------------------------------
  // Capability processes
  // -------------------------------------------------------------------------------------------------
  HM.proc({ id: "transformation_define", scope: "cap", cap: "transformation", elems: ["definition", "kind", "endpoint", "rule", "genrule", "t2m"],
    n: T("Definir una transformación", "Define a transformation"),
    d: T("Del tipo elegido a una definición guardada, para los tres tipos.", "From the chosen kind to a saved definition, for all three kinds."),
    nodes: [
      { id: "s", t: "start", cap: "transformation", l: T("Abrir el diseñador", "Open the designer"), u: T("Se empieza una transformación nueva o se abre una existente.", "A new transformation is started or an existing one is opened.") },
      { id: "kind", t: "step", a: "user", r: "MANDATORY", cap: "transformation", l: T("Elegir nombre y tipo", "Choose name and kind"), e: "kind",
        u: T("Escribes el nombre y eliges modelo a modelo, modelo a texto o texto a modelo; el tipo decide qué se pide después.", "You type the name and choose model to model, model to text or text to model; the kind decides what is asked next."),
        need: T("Nada previo", "Nothing beforehand"), out: T("Tipo elegido", "Kind chosen"), s: T("definición: nueva → con tipo", "definition: new → with kind") },
      { id: "ends", t: "step", a: "user", r: "MANDATORY", cap: "metamodel", l: T("Elegir metamodelos", "Pick metamodels"), e: "endpoint",
        u: T("Eliges el metamodelo de origen y, en modelo a modelo, el de destino; en texto a modelo, la gramática.", "You pick the source metamodel and, for model to model, the target; for text to model, the grammar."),
        need: T("Metamodelos guardados", "Saved metamodels"), out: T("Extremos fijados", "Ends fixed"), f: T("Modelo a modelo: origen y destino; modelo a texto: origen; texto a modelo: gramática.", "Model to model: source and target; model to text: source; text to model: grammar.") },
      { id: "k", t: "decision", a: "auto", cap: "transformation", l: T("¿Qué tipo es?", "Which kind?"), u: T("Cada tipo tiene su propio espacio de trabajo.", "Each kind has its own workspace.") },
      { id: "m2m", t: "step", a: "user", r: "CONDITIONAL", cap: "transformation", l: T("Definir reglas y asignaciones", "Define rules and assignments"), e: "rule",
        u: T("Creas reglas de clase origen a clase destino y las asignaciones de atributos y relaciones.", "You create rules from source class to target class and the attribute and relation assignments."),
        need: T("Extremos fijados", "Ends fixed"), out: T("Reglas con asignaciones", "Rules with assignments") },
      { id: "m2t", t: "step", a: "user", r: "CONDITIONAL", cap: "transformation", l: T("Definir reglas de generación", "Define generation rules"), e: "genrule",
        u: T("Añades una regla por clase con ruta de salida y plantilla de contenido.", "You add one rule per class with output path and content template."),
        need: T("Origen fijado", "Source fixed"), out: T("Reglas de generación", "Generation rules") },
      { id: "t2m", t: "step", a: "user", r: "CONDITIONAL", cap: "grammar", l: T("Configurar texto a modelo", "Configure text to model"), e: "t2m",
        u: T("Eliges la gramática, la ruta lógica del texto y, si quieres, una transformación posterior.", "You pick the grammar, the logical text path and optionally a follow-up transformation."),
        need: T("Gramática guardada", "A saved grammar"), out: T("Configuración de texto", "Text configuration") },
      { id: "pol", t: "step", a: "user", r: "OPTIONAL", cap: "transformation", l: T("Ajustar direccionalidad y políticas", "Adjust directionality and policies"), e: "syncpolicy",
        u: T("En modelo a modelo puedes hacerla bidireccional, elegir la política de conflictos y revisar el rastro.", "In model to model you can make it bidirectional, choose the conflict policy and review the trail."),
        out: T("Políticas definidas", "Policies defined"), f: T("Bidireccional fuerza el rastro y emparejar reglas de ida y vuelta.", "Bidirectional forces the trail and pairing of forward and backward rules.") },
      { id: "chk", t: "step", a: "auto", r: "MANDATORY", cap: "transformation", l: T("Comprobar la definición", "Check the definition"), e: "diagnostics",
        u: T("HYDRA revisa la definición y lista errores y avisos con una solución.", "HYDRA reviews the definition and lists errors and warnings with a solution."),
        out: T("Informe de diagnósticos", "Diagnostics report"), x: { rt: "TransformationDefinitionChecker", test: ["TransformationDefinitionCheckerTests"], ev: [ev(J + "TransformationDefinitionChecker.java", 62, "checker")] } },
      { id: "ok", t: "decision", a: "auto", cap: "transformation", l: T("¿Sin errores?", "No errors?"), u: T("Los avisos no bloquean; un error impide guardar.", "Warnings do not block; an error prevents saving.") },
      { id: "sv", t: "step", a: "user", r: "MANDATORY", cap: "transformation", l: T("Guardar", "Save"), e: "definition",
        u: T("Se crea la definición o se reemplaza la existente.", "The definition is created or the existing one replaced."), s: T("definición: comprobada → guardada", "definition: checked → saved") },
      { id: "art", t: "artifact", cap: "transformation", l: T("Definición guardada", "Saved definition"), u: T("Lista para ejecutar, probar y exportar.", "Ready to run, test and export.") },
      { id: "e", t: "end", cap: "transformation", l: T("Fin", "End") }
    ],
    edges: [["s", "kind"], ["kind", "ends"], ["ends", "k"], ["k", "m2m", "next", T("Modelo a modelo", "Model to model")], ["k", "m2t", "alt", T("Modelo a texto", "Model to text")], ["k", "t2m", "alt", T("Texto a modelo", "Text to model")],
      ["m2m", "pol", "opt"], ["m2m", "chk"], ["m2t", "chk"], ["t2m", "chk"], ["pol", "chk"], ["chk", "ok"], ["ok", "sv", "next", T("Sí", "Yes")], ["ok", "kind", "loop", T("No: corregir", "No: fix")], ["sv", "art"], ["art", "e"]] });

  HM.proc({ id: "transformation_execute", scope: "cap", cap: "transformation", elems: ["rule", "binding", "valuepolicy", "tracepolicy"],
    n: T("Ejecutar por lotes (ida y vuelta)", "Run in batch (forward and backward)"),
    d: T("Compilar el plan y producir el modelo destino con su rastro.", "Compile the plan and produce the target model with its trail."),
    nodes: [
      { id: "s", t: "start", cap: "transformation", l: T("Pedir ejecución", "Request a run"), u: T("Se ejecuta una definición guardada o un borrador sin guardarlo, en ida o en vuelta.", "A saved definition or an unsaved draft is run, forward or backward.") },
      { id: "in", t: "step", a: "user", r: "MANDATORY", cap: "model_editor", l: T("Elegir modelo de entrada", "Choose input model"), u: T("Indicas el modelo y la dirección.", "You indicate the model and the direction."), need: T("Modelo del editor", "An editor model"), out: T("Entrada fijada", "Input fixed") },
      { id: "cmp", t: "step", a: "auto", r: "MANDATORY", cap: "transformation", l: T("Compilar el plan", "Compile the plan"), u: T("HYDRA comprueba la definición, descarta las reglas desactivadas o de otra dirección y, al revés, intercambia origen y destino.", "HYDRA checks the definition, drops disabled rules or those of the other direction and, when backward, swaps source and target."),
        out: T("Plan compilado", "Compiled plan"), f: T("La vuelta exige bidireccional; sin reglas aplicables hay error.", "Backward requires bidirectional; with no applicable rules it is an error."), x: { rt: "TransformationPlanCompiler", test: ["CanonicalTransformationRuntimeTests"], ev: [ev(J + "TransformationPlanCompiler.java", 31, "compile")] } },
      { id: "ok", t: "decision", a: "auto", cap: "transformation", l: T("¿Plan válido?", "Valid plan?"), u: T("Los errores de la definición detienen la ejecución.", "Definition errors stop the run.") },
      { id: "alloc", t: "step", a: "auto", r: "MANDATORY", cap: "transformation", l: T("Crear elementos destino", "Create target elements"), e: "targetpattern",
        u: T("Por cada elemento origen que cumple la regla y su condición se crea un elemento destino por patrón, con identificador estable.", "For each source element meeting the rule and its condition, one target element per pattern is created with a stable identifier."), out: T("Elementos destino vacíos", "Empty target elements"), x: { rt: "CanonicalTransformationRuntime", test: ["CanonicalTransformationRuntimeTests", "AggressiveTransformationRuntimeTests"], ev: [ev(J + "CanonicalTransformationRuntime.java", 77, "allocation")] } },
      { id: "pop", t: "step", a: "auto", r: "MANDATORY", cap: "transformation", l: T("Rellenar atributos y relaciones", "Fill attributes and relations"), e: "binding",
        u: T("Cada asignación copia, fija o calcula el valor, lo adapta con la política de valores y crea las relaciones.", "Each assignment copies, fixes or computes the value, adapts it with the value policy and creates the relations."), out: T("Modelo destino relleno", "Filled target model"),
        f: T("Un fallo de asignación detiene la ejecución, salvo «omitir» o «reserva».", "An assignment failure stops the run, unless “skip” or “fallback”."), x: { rt: "CanonicalTransformationRuntime", test: ["CanonicalTransformationRuntimeTests"], ev: [ev(J + "CanonicalTransformationRuntime.java", 103, "population")] } },
      { id: "tr", t: "step", a: "auto", r: "OPTIONAL", cap: "transformation", l: T("Registrar el rastro", "Record the trail"), e: "tracepolicy",
        u: T("Si el rastro está activo se guardan enlaces y, opcionalmente, detalle por asignación; los elementos llevan anotaciones.", "If the trail is enabled links and optionally per-assignment detail are stored; elements carry annotations."), out: T("Rastro", "Trail") },
      { id: "out", t: "artifact", cap: "transformation", l: T("Modelo destino y rastro", "Target model and trail"), u: T("Se devuelve el modelo destino (con el metamodelo del destino) y su rastro; la ejecución de borrador no se guarda.", "The target model (with the target metamodel) and its trail are returned; a draft run is not persisted.") },
      { id: "err", t: "end", cap: "transformation", l: T("Error de ejecución", "Run error") },
      { id: "e", t: "end", cap: "transformation", l: T("Fin", "End") }
    ],
    edges: [["s", "in"], ["in", "cmp"], ["cmp", "ok"], ["ok", "alloc", "next", T("Sí", "Yes")], ["ok", "err", "alt", T("No", "No")], ["alloc", "pop"], ["pop", "tr", "opt"], ["pop", "out"], ["tr", "out"], ["out", "e"]] });

  HM.proc({ id: "transformation_batch_both", scope: "cap", cap: "transformation", elems: ["syncpolicy"],
    n: T("Ejecutar por lotes en ambas direcciones", "Run in batch in both directions"),
    d: T("Reconstruir el lado opuesto completo según la política de conflictos.", "Rebuild the whole opposite side according to the conflict policy."),
    nodes: [
      { id: "s", t: "start", cap: "transformation", l: T("Pedir sincronización por lotes", "Request batch synchronisation"), u: T("Se pide actuar sobre ambos lados sin sesión.", "A run on both sides is requested without a session.") },
      { id: "pol", t: "decision", a: "auto", cap: "transformation", l: T("¿Qué política hay?", "Which policy?"), e: "syncpolicy", u: T("La política decide quién gana o si se detiene.", "The policy decides who wins or whether it stops."), f: T("No se calcula ningún delta en este modo.", "No delta is computed in this mode.") },
      { id: "fail", t: "step", a: "auto", cap: "transformation", l: T("Parar con error", "Stop with an error"), u: T("Con «parar ante conflicto» se lanza un error en vez de devolver un resultado bloqueado.", "With “stop on conflict” an error is raised instead of returning a blocked result."), s: T("sincronización: pedida → error", "synchronisation: requested → error") },
      { id: "fw", t: "step", a: "auto", cap: "transformation", l: T("Ejecutar de ida", "Run forward"), u: T("Si gana el origen, se reconstruye el destino completo desde el origen.", "If source wins, the whole target is rebuilt from the source.") },
      { id: "rv", t: "step", a: "auto", cap: "transformation", l: T("Ejecutar de vuelta", "Run backward"), u: T("En el resto de casos se reconstruye el origen completo desde el destino.", "Otherwise the whole source is rebuilt from the target.") },
      { id: "res", t: "artifact", cap: "transformation", l: T("Lado opuesto reconstruido", "Opposite side rebuilt"), u: T("El resultado reemplaza completo el lado opuesto.", "The result fully replaces the opposite side.") },
      { id: "e", t: "end", cap: "transformation", l: T("Fin", "End") }
    ],
    edges: [["s", "pol"], ["pol", "fail", "alt", T("Parar ante conflicto", "Stop on conflict")], ["pol", "fw", "next", T("Gana el origen", "Source wins")], ["pol", "rv", "alt", T("Gana el destino", "Target wins")], ["fail", "e"], ["fw", "res"], ["rv", "res"], ["res", "e"]] });

  HM.proc({ id: "transformation_sync", scope: "cap", cap: "transformation", elems: ["session", "delta", "conflict", "syncpolicy"],
    n: T("Sincronización incremental con sesión", "Incremental synchronisation with a session"),
    d: T("Crear, sincronizar, bloquear por conflicto, rebasar y restaurar.", "Create, synchronise, block on conflict, rebase and restore."),
    nodes: [
      { id: "s", t: "start", cap: "transformation", l: T("Abrir una sesión", "Open a session"), u: T("Se parte de una transformación bidireccional y de dos modelos.", "It starts from a bidirectional transformation and two models.") },
      { id: "cr", t: "step", a: "user", r: "MANDATORY", cap: "transformation", l: T("Crear la sesión", "Create the session"), e: "session",
        u: T("Eliges desde qué lado se inicializa; HYDRA sincroniza completo, guarda ambas líneas base y el rastro y crea el primer punto de control.", "You choose which side initialises; HYDRA synchronises fully, stores both baselines and the trail, and creates the first checkpoint."),
        out: T("Sesión en revisión 1", "Session at revision 1"), s: T("sesión: ninguna → revisión 1", "session: none → revision 1"), x: { rt: "TransformationSynchronizationSessionService", test: [], ev: [ev(J + "sync/TransformationSynchronizationSessionService.java", 59, "create")] } },
      { id: "chg", t: "step", a: "user", r: "MANDATORY", cap: "model_editor", l: T("Editar uno o ambos modelos", "Edit one or both models"), u: T("El usuario cambia los modelos con el tiempo.", "The user changes the models over time.") },
      { id: "sy", t: "step", a: "auto", r: "MANDATORY", cap: "transformation", l: T("Sincronizar", "Synchronise"), e: "delta",
        u: T("HYDRA comprueba la coherencia con la línea base, calcula los cambios de cada lado y los compara.", "HYDRA checks consistency with the baseline, computes each side's changes and compares them."),
        need: T("Condición de versión y misma versión de la transformación", "Version precondition and same transformation version"), f: T("Línea base obligatoria; modelos y huella del metamodelo coherentes.", "Baseline mandatory; models and metamodel fingerprint consistent."),
        x: { rt: "TransformationIncrementalSynchronizationEngine", test: ["TransformationSynchronizationConsistencyValidatorTests"], ev: [ev(J + "TransformationIncrementalSynchronizationEngine.java", 37, "requireBaseline")] } },
      { id: "nc", t: "decision", a: "auto", cap: "transformation", l: T("¿Hubo cambios?", "Any changes?"), u: T("Si ninguno de los lados cambió no se hace nada.", "If neither side changed nothing is done.") },
      { id: "cf", t: "decision", a: "auto", cap: "transformation", l: T("¿Hay conflicto?", "Any conflict?"), e: "conflict", u: T("Solo hay conflicto si ambos lados cambiaron lo mismo o añadieron cosas ambiguas.", "A conflict only exists if both sides changed the same thing or added ambiguous things.") },
      { id: "pl", t: "decision", a: "auto", cap: "transformation", l: T("¿Qué dice la política?", "What does the policy say?"), e: "syncpolicy", u: T("Parar, o que gane un lado.", "Stop, or let a side win.") },
      { id: "blk", t: "step", a: "auto", cap: "transformation", l: T("Resultado bloqueado", "Blocked result"), u: T("Se devuelve el listado de conflictos sin aplicar nada y sin lanzar error; la revisión no avanza.", "The list of conflicts is returned with nothing applied and no error; the revision does not advance."), s: T("sincronización: pedida → bloqueada", "synchronisation: requested → blocked") },
      { id: "ap", t: "step", a: "auto", r: "MANDATORY", cap: "transformation", l: T("Propagar los cambios", "Propagate the changes"),
        u: T("Se aplican al lado opuesto solo los elementos emparejados afectados (con borrados si la política lo indica), descartando los conflictivos del lado perdedor.", "Only affected paired elements are applied to the opposite side (with deletions if the policy says so), dropping the conflicting ones of the losing side.") },
      { id: "rev", t: "step", a: "auto", r: "MANDATORY", cap: "transformation", l: T("Nueva revisión y punto de control", "New revision and checkpoint"), e: "session",
        u: T("La revisión sube en uno y se guarda un punto de control.", "The revision goes up by one and a checkpoint is stored."), s: T("sesión: revisión n → n+1", "session: revision n → n+1") },
      { id: "ot", t: "decision", a: "user", cap: "transformation", l: T("¿Qué hacer después?", "What next?"), u: T("Puedes seguir sincronizando, rebasar o restaurar.", "You can keep synchronising, rebase or restore.") },
      { id: "rb", t: "step", a: "user", r: "OPTIONAL", cap: "transformation", l: T("Rebasar", "Rebase"), u: T("Se rehace la línea base desde el lado elegido.", "The baseline is rebuilt from the chosen side."), s: T("sesión: revisión n → n+1 (rebasada)", "session: revision n → n+1 (rebased)") },
      { id: "rs", t: "step", a: "user", r: "OPTIONAL", cap: "transformation", l: T("Restaurar un punto de control", "Restore a checkpoint"), u: T("Se crea una nueva revisión con las líneas base del punto elegido.", "A new revision is created with the baselines of the chosen checkpoint."), s: T("sesión: revisión n → n+1 (restaurada)", "session: revision n → n+1 (restored)") },
      { id: "out", t: "artifact", cap: "transformation", l: T("Sesión con historial", "Session with history"), u: T("Modelos al día, línea base y puntos de control (entre 2 y 200).", "Models up to date, baseline and checkpoints (between 2 and 200).") },
      { id: "e", t: "end", cap: "transformation", l: T("Fin", "End") }
    ],
    edges: [["s", "cr"], ["cr", "chg"], ["chg", "sy"], ["sy", "nc"], ["nc", "e", "alt", T("No: sin cambios", "No: unchanged")], ["nc", "cf", "next", T("Sí", "Yes")], ["cf", "ap", "next", T("No", "No")], ["cf", "pl", "cond", T("Sí", "Yes")],
      ["pl", "blk", "alt", T("Parar", "Stop")], ["pl", "ap", "next", T("Gana un lado", "A side wins")], ["blk", "chg", "loop", T("Resolver y repetir", "Resolve and repeat")], ["ap", "rev"], ["rev", "out"], ["out", "ot"],
      ["ot", "chg", "loop", T("Seguir sincronizando", "Keep synchronising")], ["ot", "rb", "opt", T("Rebasar", "Rebase")], ["ot", "rs", "opt", T("Restaurar", "Restore")], ["rb", "out"], ["rs", "out"], ["ot", "e", "next", T("Terminar", "Finish")]] });

  HM.proc({ id: "transformation_export_native", scope: "cap", cap: "transformation", elems: ["export", "runtime"],
    n: T("Exportar y ejecutar de forma nativa", "Export and run natively"),
    d: T("De la definición a ficheros de otro lenguaje y su ejecución con una herramienta externa.", "From the definition to files in another language and their run with an external tool."),
    nodes: [
      { id: "s", t: "start", cap: "transformation", l: T("Pedir exportación", "Request an export"), u: T("Se parte de una definición guardada.", "It starts from a saved definition.") },
      { id: "lg", t: "step", a: "user", r: "MANDATORY", cap: "transformation", l: T("Elegir lenguaje y dirección", "Choose language and direction"), e: "export", u: T("Eliges ATL, ETL, Acceleo, EGL, un lenguaje de programación o el paquete con el motor de HYDRA.", "You choose ATL, ETL, Acceleo, EGL, a programming language or the HYDRA engine package."), f: T("La dirección de vuelta solo existe para modelo a modelo.", "Backward direction only exists for model to model.") },
      { id: "cp", t: "decision", a: "auto", cap: "transformation", l: T("¿Compatible?", "Compatible?"), u: T("Cada lenguaje admite solo ciertos tipos de transformación.", "Each language supports only certain kinds of transformation.") },
      { id: "ex", t: "step", a: "auto", r: "MANDATORY", cap: "transformation", l: T("Generar los ficheros", "Generate the files"), u: T("Se obtienen ficheros con ruta y contenido y diagnósticos; la fidelidad es exacta, parcial o la del motor propio.", "Files with path and content and diagnostics are obtained; fidelity is exact, partial or that of HYDRA's engine."), out: T("Exportación", "Export"), x: { rt: "TransformationExportService", test: ["TransformationLanguageAdapterTests"], ev: [ev(J + "export/TransformationExportService.java", 34, "supported kinds")] } },
      { id: "dg", t: "step", a: "user", r: "OPTIONAL", cap: "toolchains", l: T("Diagnosticar o instalar la herramienta", "Diagnose or install the tool"), e: "runtime", u: T("Compruebas si la herramienta está lista y, si no, la instalas.", "You check whether the tool is ready and, if not, install it."), need: T("Cadena de herramientas", "A toolchain"), out: T("Salud de la herramienta", "Tool health") },
      { id: "rd", t: "decision", a: "auto", cap: "transformation", l: T("¿Herramienta lista?", "Tool ready?"), u: T("Sin herramienta lista no se ejecuta.", "Without a ready tool nothing runs.") },
      { id: "run", t: "step", a: "ext", r: "OPTIONAL", cap: "transformation", l: T("Ejecutar con la herramienta", "Run with the tool"), e: "runtime", u: T("Se lanza la herramienta mediante un ejecutor controlado, con límites de capacidad y tiempo.", "The tool is launched through a controlled executor, with capacity and time limits."), x: { rt: "TransformationNativeRuntimeExecutor", test: ["TransformationNativeRuntimeExecutorTests"], ev: [ev(J + "nativeexec/TransformationNativeRuntimeExecutor.java", 30, "process runner")] } },
      { id: "art", t: "artifact", cap: "transformation", l: T("Exportación y resultado nativo", "Export and native result"), u: T("Ficheros exportados y, si se ejecutó, el estado de la ejecución.", "Exported files and, if run, the run status.") },
      { id: "bl", t: "end", cap: "transformation", l: T("Exportación bloqueada", "Export blocked") },
      { id: "e", t: "end", cap: "transformation", l: T("Fin", "End") }
    ],
    edges: [["s", "lg"], ["lg", "cp"], ["cp", "ex", "next", T("Sí", "Yes")], ["cp", "bl", "alt", T("No", "No")], ["ex", "art"], ["ex", "dg", "opt"], ["dg", "rd"], ["rd", "run", "next", T("Sí", "Yes")], ["rd", "dg", "loop", T("No: instalar", "No: install")], ["run", "art"], ["art", "e"]] });

  HM.proc({ id: "transformation_parity", scope: "cap", cap: "transformation", elems: ["parity", "export"],
    n: T("Comprobar la paridad", "Check parity"),
    d: T("Comparar el resultado de HYDRA con el de la ejecución externa.", "Compare HYDRA's result with the external run's."),
    nodes: [
      { id: "s", t: "start", cap: "transformation", l: T("Pedir paridad", "Request parity"), u: T("Se parte de una exportación y su resultado externo.", "It starts from an export and its external result.") },
      { id: "ev", t: "decision", a: "auto", cap: "transformation", l: T("¿Lenguaje y tipo admitidos?", "Language and kind supported?"), e: "parity", u: T("Si no son admitidos, la evidencia no es válida.", "If not supported, the evidence is invalid.") },
      { id: "cp", t: "decision", a: "auto", cap: "transformation", l: T("¿Exportación compatible?", "Export compatible?"), u: T("Una exportación bloqueada no se puede comparar.", "A blocked export cannot be compared.") },
      { id: "ru", t: "step", a: "auto", r: "MANDATORY", cap: "transformation", l: T("Ejecutar con el motor de HYDRA", "Run with HYDRA's engine"), u: T("Se obtiene el resultado propio sin guardar nada.", "HYDRA's own result is obtained without saving anything.") },
      { id: "kd", t: "decision", a: "auto", cap: "transformation", l: T("¿Modelo o texto?", "Model or text?"), u: T("Modelo a modelo compara modelos; modelo a texto compara ficheros.", "Model to model compares models; model to text compares files.") },
      { id: "cm", t: "step", a: "auto", r: "MANDATORY", cap: "transformation", l: T("Comparar resultados", "Compare results"), u: T("Se comparan ambos resultados.", "Both results are compared."), x: { rt: "TransformationParityService", test: ["TransformationTestServiceTests"], ev: [ev(J + "parity/TransformationParityService.java", 50, "parity logic")] } },
      { id: "mt", t: "artifact", cap: "transformation", l: T("Coincide / No coincide", "Matched / Mismatched"), u: T("Veredicto de paridad.", "Parity verdict.") },
      { id: "bl", t: "artifact", cap: "transformation", l: T("Exportación bloqueada", "Export blocked"), u: T("No se pudo comparar.", "Could not compare.") },
      { id: "iv", t: "artifact", cap: "transformation", l: T("Evidencia no válida", "Invalid evidence"), u: T("Lenguaje o tipo no admitido.", "Unsupported language or kind.") },
      { id: "e", t: "end", cap: "transformation", l: T("Fin", "End") }
    ],
    edges: [["s", "ev"], ["ev", "iv", "alt", T("No", "No")], ["ev", "cp", "next", T("Sí", "Yes")], ["cp", "bl", "alt", T("No", "No")], ["cp", "ru", "next", T("Sí", "Yes")], ["ru", "kd"], ["kd", "cm", "next", T("Modelo", "Model")], ["kd", "cm", "alt", T("Texto", "Text")], ["cm", "mt"], ["mt", "e"], ["bl", "e"], ["iv", "e"]] });

  // -------------------------------------------------------------------------------------------------
  // Authoring micro-processes
  // -------------------------------------------------------------------------------------------------
  HM.proc({ id: "transformation_author_guided", scope: "authoring", cap: "transformation", elems: ["definition", "kind", "directionality", "endpoint", "syncpolicy"],
    n: T("Diseñador guiado: ámbito y revisión", "Guided designer: scope and review"),
    d: T("Los pasos de ámbito, creación y revisión del modo guiado.", "The scope, authoring and review steps of guided mode."),
    nodes: [
      { id: "s", t: "start", cap: "transformation", l: T("Abrir el diseñador", "Open the designer"), u: T("Se abre en modo guiado.", "It opens in guided mode.") },
      { id: "nm", t: "step", a: "user", r: "MANDATORY", cap: "transformation", l: T("Escribir el nombre", "Type the name"), e: "definition", u: T("Nombre de la transformación.", "Name of the transformation."), out: T("Nombre", "Name") },
      { id: "ty", t: "step", a: "user", r: "MANDATORY", cap: "transformation", l: T("Elegir tipo", "Choose kind"), e: "kind", u: T("Modelo a modelo, modelo a texto o texto a modelo; los tipos distintos de modelo a modelo quedan en una sola dirección.", "Model to model, model to text or text to model; kinds other than model to model stay one-way."), x: { rt: "transformationDesignerModel.ts", test: ["transformationDesignerModel.test.ts"], ev: [ev(D + "transformationDesignerModel.ts", 33, "kind switching")] } },
      { id: "isM", t: "decision", a: "auto", cap: "transformation", l: T("¿Es modelo a modelo?", "Model to model?"), u: T("Solo entonces se pregunta la direccionalidad.", "Only then is directionality asked.") },
      { id: "di", t: "step", a: "user", r: "CONDITIONAL", cap: "transformation", l: T("Elegir direccionalidad", "Choose directionality"), e: "directionality", u: T("Unidireccional o bidireccional.", "Unidirectional or bidirectional."), f: T("Bidireccional fuerza el rastro y asigna correspondencias.", "Bidirectional forces the trail and assigns correspondences.") },
      { id: "isB", t: "decision", a: "auto", cap: "transformation", l: T("¿Bidireccional?", "Bidirectional?"), u: T("Solo entonces se piden las opciones de sincronización.", "Only then are the synchronisation options asked.") },
      { id: "cn", t: "step", a: "user", r: "CONDITIONAL", cap: "transformation", l: T("Elegir cambios simultáneos", "Choose concurrent changes"), e: "syncpolicy", u: T("«Parar ante conflicto», «Gana el origen» o «Gana el destino», más «Propagar borrados» y «Exigir rastro previo».", "“Stop on conflict”, “Source wins” or “Target wins”, plus “Propagate deletions” and “Require previous trail”.") },
      { id: "mm", t: "step", a: "user", r: "MANDATORY", cap: "metamodel", l: T("Elegir metamodelos", "Pick metamodels"), e: "endpoint", u: T("Origen y, en modelo a modelo, destino.", "Source and, for model to model, target.") },
      { id: "wk", t: "step", a: "user", r: "MANDATORY", cap: "transformation", l: T("Trabajar en el espacio del tipo", "Work in the kind's workspace"), u: T("Mapeo, generación o texto a modelo, según el tipo.", "Mapping, generation or text to model, depending on kind.") },
      { id: "ck", t: "step", a: "user", r: "MANDATORY", cap: "transformation", l: T("Comprobar", "Check"), u: T("Se muestran diagnósticos y correcciones rápidas.", "Diagnostics and quick fixes are shown."), s: T("definición: borrador → comprobada", "definition: draft → checked") },
      { id: "ok", t: "decision", a: "auto", cap: "transformation", l: T("¿Sin errores?", "No errors?"), u: T("Guardar exige comprobación sin errores.", "Saving requires an error-free check.") },
      { id: "sv", t: "step", a: "user", r: "MANDATORY", cap: "transformation", l: T("Guardar", "Save"), u: T("Crea la definición o reemplaza la existente.", "Creates the definition or replaces the existing one.") },
      { id: "art", t: "artifact", cap: "transformation", l: T("Definición guardada", "Saved definition"), u: T("Lista para usar.", "Ready to use.") },
      { id: "e", t: "end", cap: "transformation", l: T("Fin", "End") }
    ],
    edges: [["s", "nm"], ["nm", "ty"], ["ty", "isM"], ["isM", "di", "cond", T("Sí", "Yes")], ["isM", "mm", "next", T("No", "No")], ["di", "isB"], ["isB", "cn", "cond", T("Sí", "Yes")], ["isB", "mm", "next", T("No", "No")], ["cn", "mm"], ["mm", "wk"], ["wk", "ck"], ["ck", "ok"], ["ok", "sv", "next", T("Sí", "Yes")], ["ok", "wk", "loop", T("No: corregir", "No: fix")], ["sv", "art"], ["art", "e"]] });

  HM.proc({ id: "transformation_author_mapping", scope: "authoring", cap: "transformation", elems: ["rule", "targetpattern", "binding", "valuepolicy"],
    n: T("Espacio de mapeo", "Mapping workspace"),
    d: T("Crear una regla y sus asignaciones.", "Create a rule and its assignments."),
    nodes: [
      { id: "s", t: "start", cap: "transformation", l: T("Abrir el espacio de mapeo", "Open the mapping workspace"), u: T("Con los metamodelos ya elegidos.", "With the metamodels already chosen.") },
      { id: "sc", t: "step", a: "user", r: "MANDATORY", cap: "transformation", l: T("Elegir clase origen y destino", "Pick source and target class"), e: "targetpattern", u: T("Una clase de cada lado.", "One class from each side.") },
      { id: "bi", t: "decision", a: "auto", cap: "transformation", l: T("¿Bidireccional?", "Bidirectional?"), u: T("En unidireccional la regla es siempre de ida.", "In unidirectional mode the rule is always forward.") },
      { id: "dr", t: "step", a: "user", r: "CONDITIONAL", cap: "transformation", l: T("Elegir ida o vuelta", "Choose forward or backward"), e: "rule", u: T("Indica en qué sentido actúa la regla.", "States in which sense the rule acts.") },
      { id: "ar", t: "step", a: "user", r: "MANDATORY", cap: "transformation", l: T("Añadir la regla", "Add the rule"), e: "rule", u: T("Se crea «Origen to Destino», activa, con un patrón destino y un alias único; hereda la correspondencia de la regla opuesta si existe.", "“Source to Target” is created, enabled, with a target pattern and a unique alias; it inherits the correspondence of the opposite rule if one exists."), x: { rt: "transformationDesignerModel.ts", test: ["transformationDesignerModel.test.ts"], ev: [ev(D + "transformationDesignerModel.ts", 64, "createRule")] } },
      { id: "mp", t: "step", a: "user", r: "MANDATORY", cap: "transformation", l: T("Mapear atributos", "Map attributes"), e: "binding", u: T("Uno a uno o con «mapear nombres compatibles» (sin distinguir mayúsculas).", "One by one or with “map compatible names” (case-insensitive).") },
      { id: "kd", t: "step", a: "user", r: "OPTIONAL", cap: "transformation", l: T("Elegir tipo de asignación", "Choose assignment kind"), e: "binding", u: T("Copia, valor fijo o expresión (la correspondencia de relación se crea aparte).", "Copy, fixed value or expression (relation correspondence is created separately).") },
      { id: "vp", t: "step", a: "user", r: "OPTIONAL", cap: "transformation", l: T("Ajustar política de valores", "Adjust value policy"), e: "valuepolicy", u: T("Colección, conversión, error y, con «usar reserva», valor de reserva.", "Collection, conversion, error and, with “use fallback”, the fallback value.") },
      { id: "rl", t: "step", a: "user", r: "OPTIONAL", cap: "transformation", l: T("Crear relaciones", "Create relations"), e: "binding", u: T("En el panel de relaciones se crean las correspondencias de relación.", "Relation correspondences are created in the relations panel.") },
      { id: "gd", t: "step", a: "user", r: "OPTIONAL", cap: "transformation", l: T("Añadir condición a la regla", "Add a condition to the rule"), e: "rule", u: T("Se crea una expresión booleana sobre el tipo origen.", "A boolean expression over the source type is created.") },
      { id: "ck", t: "step", a: "auto", r: "MANDATORY", cap: "transformation", l: T("Comprobar la regla", "Check the rule"), e: "diagnostics", u: T("Se revisan tipos, patrones, asignaciones y duplicados.", "Types, patterns, assignments and duplicates are reviewed.") },
      { id: "ok", t: "decision", a: "auto", cap: "transformation", l: T("¿Sin errores?", "No errors?"), u: T("Los avisos, como un atributo obligatorio sin asignar, no bloquean.", "Warnings, such as a mandatory attribute left unmapped, do not block.") },
      { id: "art", t: "artifact", cap: "transformation", l: T("Regla lista", "Rule ready"), u: T("Se guarda con la definición.", "Saved with the definition.") },
      { id: "e", t: "end", cap: "transformation", l: T("Fin", "End") }
    ],
    edges: [["s", "sc"], ["sc", "bi"], ["bi", "dr", "cond", T("Sí", "Yes")], ["bi", "ar", "next", T("No", "No")], ["dr", "ar"], ["ar", "mp"], ["mp", "kd", "opt"], ["kd", "vp", "opt"], ["mp", "rl", "opt"], ["rl", "gd", "opt"], ["mp", "gd", "opt"], ["vp", "ck"], ["gd", "ck"], ["mp", "ck"], ["ck", "ok"], ["ok", "art", "next", T("Sí", "Yes")], ["ok", "mp", "loop", T("No: corregir", "No: fix")], ["art", "e"]] });

  HM.proc({ id: "transformation_author_generation", scope: "authoring", cap: "transformation", elems: ["genrule"],
    n: T("Espacio de generación", "Generation workspace"),
    d: T("Crear reglas de generación de ficheros.", "Create file generation rules."),
    nodes: [
      { id: "s", t: "start", cap: "transformation", l: T("Abrir el espacio de generación", "Open the generation workspace"), u: T("Solo en transformaciones modelo a texto.", "Only in model-to-text transformations.") },
      { id: "cc", t: "step", a: "user", r: "MANDATORY", cap: "transformation", l: T("Elegir la clase de contexto", "Choose the context class"), e: "genrule", u: T("Se añade una regla por clase.", "One rule is added per class.") },
      { id: "df", t: "step", a: "auto", r: "MANDATORY", cap: "transformation", l: T("Regla con valores por defecto", "Rule with default values"), e: "genrule", u: T("Nace con ruta «generated/{{primer atributo}}.txt», una plantilla y sobrescritura en «fallar».", "It starts with path “generated/{{first attribute}}.txt”, a template and overwrite on “fail”."), x: { rt: "transformationDesignerModel.ts", test: ["transformationDesignerModel.test.ts"], ev: [ev(D + "transformationDesignerModel.ts", 83, "createGenerationRule")] } },
      { id: "ed", t: "step", a: "user", r: "MANDATORY", cap: "transformation", l: T("Editar la plantilla de contenido", "Edit the content template"), e: "genrule", u: T("Con marcas de identificador, clase y atributo.", "With identifier, class and attribute tokens.") },
      { id: "en", t: "step", a: "user", r: "OPTIONAL", cap: "transformation", l: T("Activar o borrar", "Enable or delete"), e: "genrule", u: T("Una regla desactivada no genera.", "A disabled rule does not generate.") },
      { id: "ck", t: "step", a: "auto", r: "MANDATORY", cap: "transformation", l: T("Comprobar", "Check"), e: "diagnostics", u: T("Identificador único, clase existente, ruta relativa y portable, y al menos una regla.", "Unique identifier, existing class, relative and portable path, and at least one rule.") },
      { id: "ok", t: "decision", a: "auto", cap: "transformation", l: T("¿Sin errores?", "No errors?"), u: T("Un error impide guardar.", "An error prevents saving.") },
      { id: "art", t: "artifact", cap: "transformation", l: T("Reglas de generación listas", "Generation rules ready"), u: T("Se guardan con la definición.", "Saved with the definition.") },
      { id: "e", t: "end", cap: "transformation", l: T("Fin", "End") }
    ],
    edges: [["s", "cc"], ["cc", "df"], ["df", "ed"], ["ed", "en", "opt"], ["ed", "ck"], ["en", "ck"], ["ck", "ok"], ["ok", "art", "next", T("Sí", "Yes")], ["ok", "ed", "loop", T("No: corregir", "No: fix")], ["art", "e"]] });

  HM.proc({ id: "transformation_author_text2model", scope: "authoring", cap: "transformation", elems: ["t2m"],
    n: T("Espacio de texto a modelo", "Text-to-model workspace"),
    d: T("Configurar la lectura de texto como modelo.", "Configure reading text as a model."),
    nodes: [
      { id: "s", t: "start", cap: "transformation", l: T("Abrir el espacio de texto a modelo", "Open the text-to-model workspace"), u: T("Solo en transformaciones de texto a modelo.", "Only in text-to-model transformations.") },
      { id: "gr", t: "step", a: "user", r: "MANDATORY", cap: "grammar", l: T("Elegir la gramática", "Pick the grammar"), e: "t2m", u: T("La gramática guardada que lee el texto.", "The saved grammar that reads the text."), need: T("Una gramática guardada", "A saved grammar") },
      { id: "pt", t: "step", a: "user", r: "OPTIONAL", cap: "transformation", l: T("Indicar la ruta lógica del texto", "Set the logical text path"), e: "t2m", u: T("Por defecto «model.hydra».", "Defaults to “model.hydra”.") },
      { id: "po", t: "step", a: "user", r: "OPTIONAL", cap: "transformation", l: T("Elegir transformación posterior", "Choose follow-up transformation"), e: "t2m", u: T("Una transformación modelo a modelo que proyecta el modelo leído.", "A model-to-model transformation that projects the parsed model.") },
      { id: "ck", t: "step", a: "auto", r: "MANDATORY", cap: "transformation", l: T("Comprobar", "Check"), e: "diagnostics", u: T("Sin gramática la comprobación falla.", "Without a grammar the check fails.") },
      { id: "ok", t: "decision", a: "auto", cap: "transformation", l: T("¿Sin errores?", "No errors?"), u: T("Un error impide guardar.", "An error prevents saving.") },
      { id: "art", t: "artifact", cap: "transformation", l: T("Configuración lista", "Configuration ready"), u: T("Se guarda con la definición.", "Saved with the definition.") },
      { id: "e", t: "end", cap: "transformation", l: T("Fin", "End") }
    ],
    edges: [["s", "gr"], ["gr", "pt", "opt"], ["gr", "ck"], ["pt", "po", "opt"], ["pt", "ck"], ["po", "ck"], ["ck", "ok"], ["ok", "art", "next", T("Sí", "Yes")], ["ok", "gr", "loop", T("No: corregir", "No: fix")], ["art", "e"]] });
})();
