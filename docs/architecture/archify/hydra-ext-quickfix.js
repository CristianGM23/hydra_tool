/* HYDRA portal — ext: Quick Fix categories (qfcat_) + per-operation facets for the validation capability.
   Facts verified in: CanonicalActionExecutor, QuickFixRuntimeService, TextualQuickFixRuntimeService, NeutralGrammarSyntaxContract,
   GrammarSourceAnalyzer, GrammarCodeAction, grammarLocalQuickFixes.ts, grammarCodeActions.ts, TransformationQuickFixService, TransformationQuickFixPanel.tsx. */
(function(){
  "use strict";
  var HM = window.HM, T = HM.T;
  var ALL = ["user", "functional", "technical"], FT = ["functional", "technical"], TO = ["technical"];

  // a = [what, when, diagnostic, config, modifies, optionality, interaction, state, revalidation, implementation] (each a T)
  function F(a){
    return [
      { n: T("Qué corrige", "What it fixes"), v: a[0], a: ALL },
      { n: T("Cuándo está disponible", "When it is available"), v: a[1], a: ALL },
      { n: T("Diagnóstico o validación asociada", "Associated diagnostic or validation"), v: a[2], a: FT },
      { n: T("Configuración", "Configuration"), v: a[3], a: FT },
      { n: T("Qué modifica", "What it modifies"), v: a[4], a: ALL },
      { n: T("Opcionalidad y multiplicidad", "Optionality and multiplicity"), v: a[5], a: ALL },
      { n: T("Interacción del usuario", "User interaction"), v: a[6], a: ALL },
      { n: T("Estado resultante", "Resulting state"), v: a[7], a: ALL },
      { n: T("Revalidación", "Revalidation"), v: a[8], a: FT },
      { n: T("Implementación", "Implementation"), v: a[9], a: TO }
    ];
  }

  var EV = "src/main/java/com/example/editor/";
  var NV = "No verificado en el código.", NVE = "Not verified in the code.";

  // ------------------------------------------------------------------------------------------------
  // (A) Categories element
  // ------------------------------------------------------------------------------------------------
  HM.extend("validation", {
    elements: [
      { id: "qfcat_categories", n: T("Categorías de Quick Fix", "Quick Fix categories"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Las distintas familias de correcciones sugeridas que ofrece HYDRA: sobre el modelo, sobre el texto del modelo, sobre la gramática y sobre las transformaciones. No son lo mismo y se comportan de forma distinta.",
                "The different families of suggested fixes HYDRA offers: on the model, on the model's text, on the grammar and on transformations. They are not the same thing and behave differently."),
        purpose: T("Que quien trabaja sepa qué tipo de corrección está viendo, qué toca, cuándo aparece y qué ocurre después de aplicarla.",
                   "So that people know which kind of fix they are looking at, what it touches, when it shows up and what happens after applying it."),
        when: T("Cuando un problema detectado en un modelo, un texto, una gramática o una transformación trae una sugerencia de arreglo.",
                "When a problem found in a model, a text, a grammar or a transformation comes with a suggested repair."),
        card: "1 diagnóstico ↔ 0..N correcciones sugeridas · cada corrección pertenece a exactamente 1 categoría",
        variants: [
          { n: T("Quick Fix del modelo (acción declarativa)", "Model Quick Fix (declarative action)"), d: T("Acción definida por la persona que modela, formada por operaciones sobre el modelo, con vista previa, deshacer y rehacer.", "Action defined by the modeler, made of operations on the model, with preview, undo and redo."), req: "ALTERNATIVE" },
          { n: T("Corrección sintáctica del modelo textual", "Textual-model syntax fix"), d: T("Inserta o sustituye un fragmento del texto cuando el analizador espera una única opción clara.", "Inserts or replaces a fragment of the text when the parser expects a single clear option."), req: "ALTERNATIVE" },
          { n: T("Acción de código de gramática: sustituir referencia", "Grammar code action: replace reference"), d: T("Cambia una referencia no resuelta por el símbolo declarado más parecido.", "Replaces an unresolved reference with the most similar declared symbol."), req: "ALTERNATIVE" },
          { n: T("Acción de código de gramática: reescribir recursión izquierda", "Grammar code action: rewrite left recursion"), d: T("Reescribe una regla que vuelve a sí misma sin consumir entrada.", "Rewrites a rule that returns to itself without consuming input."), req: "ALTERNATIVE" },
          { n: T("Correcciones locales de gramática (en el navegador)", "Local grammar fixes (in the browser)"), d: T("Arreglos inmediatos del borrador: referencia sin definir, palabra de entrada mal escrita y «;» ausente.", "Immediate fixes on the draft: undefined reference, misspelled entry keyword and missing \";\"."), req: "ALTERNATIVE" },
          { n: T("Quick Fix de transformación", "Transformation Quick Fix"), d: T("Propuesta de cambio en una transformación entre modelos: conversión de tipo, reducción de colección, quitar correspondencia obsoleta o añadir una obligatoria.", "Proposed change in a model-to-model transformation: type conversion, collection reduction, remove obsolete mapping or add a required one."), req: "ALTERNATIVE" }
        ],
        props: [
          { n: T("Qué corrige", "What it fixes"), req: "MANDATORY", d: T("El tipo de problema que cubre", "The kind of problem it covers") },
          { n: T("Cuándo está disponible", "When it is available"), req: "MANDATORY", d: T("Condiciones para que se ofrezca", "Conditions for it to be offered") },
          { n: T("Configuración", "Configuration"), req: "CONDITIONAL", d: T("Solo el Quick Fix del modelo se configura a mano", "Only the model Quick Fix is configured by hand") },
          { n: T("Estado resultante", "Resulting state"), req: "MANDATORY", d: T("Qué queda tras aplicarla", "What remains after applying it") }
        ],
        io: { i: T("Un problema detectado y su contexto", "A detected problem and its context"), o: T("Una propuesta o un cambio ya aplicado", "A proposal or an applied change") },
        effect: T("Ninguna categoría arregla nada por sí sola sin que la persona la elija; salvo las marcadas como seguras, que aun así se aplican con una acción explícita.",
                  "No category fixes anything on its own without the person choosing it; even the ones marked safe are applied with an explicit action."),
        func: { rel: [T("El Quick Fix del modelo es el concepto «Quick Fix (acción de corrección)»", "The model Quick Fix is the \"Quick Fix (fix action)\" concept"), T("Las demás categorías no usan el conjunto de acciones del modelo", "The other categories do not use the model's action set")],
                lifecycle: T("problema detectado → sugerencia → (vista previa) → aplicada → comprobación posterior (según categoría)", "problem detected → suggestion → (preview) → applied → follow-up check (depends on the category)"),
                cond: T("La revalidación automática tras aplicar solo está confirmada en el Quick Fix del modelo y en el modelo textual; en las demás depende del flujo de la pantalla.", "Automatic revalidation after applying is confirmed only for the model Quick Fix and the textual model; for the others it depends on the screen's flow.") },
        tech: { type: "6 familias de implementación independientes", schema: "ActionDefinition + QuickFixRuntimeService; TextualQuickFixRuntimeService + SuggestedFix(INSERT|REPLACE); GrammarCodeAction (quickfix.reference.replace, quickfix.grammar.left-recursion); grammarLocalQuickFixes.ts; TransformationQuickFix",
                evidence: [
                  { path: EV + "validation/quickfix/QuickFixRuntimeService.java", line: 43, end_line: 127, label: "Quick Fix del modelo" },
                  { path: EV + "textual/runtime/NeutralGrammarSyntaxContract.java", line: 239, end_line: 253, label: "SuggestedFix INSERT/REPLACE" },
                  { path: EV + "grammar/application/GrammarSourceAnalyzer.java", line: 187, end_line: 202, label: "left-recursion rewrite action" },
                  { path: EV + "grammar/application/GrammarSourceAnalyzer.java", line: 386, end_line: 397, label: "reference replacement action" },
                  { path: "frontend/src/grammar-designer/grammarLocalQuickFixes.ts", line: 13, end_line: 54, label: "local fixes" },
                  { path: EV + "transformation/quickfix/TransformationQuickFixService.java", line: 40, end_line: 59, label: "suggest()" }
                ] } }
    ],
    mapNodes: [
      { id: "qfcat_node", elem: "qfcat_categories", n: T("Categorías de Quick Fix", "Quick Fix categories"), k: "variant", req: "OPTIONAL", d: T("6 familias de corrección", "6 fix families") },
      { id: "qfcat_node_model", elem: "qfcat_categories", n: T("Quick Fix de modelo", "Model Quick Fix"), k: "variant", req: "ALTERNATIVE", d: T("Acción declarativa con deshacer", "Declarative action with undo") },
      { id: "qfcat_node_other", elem: "qfcat_categories", n: T("Texto, gramática y transformación", "Text, grammar and transformation"), k: "variant", req: "ALTERNATIVE", d: T("Otras 5 familias, sin conjunto de acciones", "5 other families, no action set") }
    ],
    mapEdges: [
      { a: "qfcat_node", b: "qfcat_node_model", rel: "contains", card1: "1", card2: "1", req: "ALTERNATIVE" },
      { a: "qfcat_node", b: "qfcat_node_other", rel: "contains", card1: "1", card2: "5", req: "ALTERNATIVE" },
      { a: "qfcat_node_model", b: "m_qf", rel: "ref", card1: "1", card2: "1", req: "ALTERNATIVE" },
      { a: "qfcat_node_other", b: "m_text", rel: "ref", card1: "0..1", card2: "1", req: "OPTIONAL" },
      { a: "m_diag", b: "qfcat_node", rel: "ref", card1: "1", card2: "0..N", req: "OPTIONAL" }
    ]
  });

  var C = [];
  // 0 — model Quick Fix
  C[0] = F([
    T("Cualquier problema de una regla de validación que tenga un arreglo definido como acción: cambia valores, relaciones o elementos del modelo.", "Any validation-rule problem that has a fix defined as an action: changes values, relations or elements of the model."),
    T("Cuando la regla que generó el diagnóstico la lista, la acción existe, está activa y cumple su precondición (si la tiene). Sin sesión de modelo abierta no se puede aplicar.", "When the rule that produced the diagnostic lists it, the action exists, is enabled and meets its precondition (if any). It cannot be applied without an open model session."),
    T("Diagnóstico de una regla de validación del modelo; el diagnóstico guarda la lista de Quick Fixes ofrecidos. Se rechaza una acción que el diagnóstico seleccionado no ofrece.", "Diagnostic from a model validation rule; the diagnostic holds the list of offered Quick Fixes. An action not offered by the selected diagnostic is rejected."),
    T("Nombre, operaciones (1..N), política transaccional (atómica o parcial), política de deshacer, parámetros, precondición, activa/inactiva. Se define en el editor de Quick Fixes de la capacidad.", "Name, operations (1..N), transaction policy (atomic or partial), undo policy, parameters, precondition, enabled/disabled. Defined in the capability's Quick Fix editor."),
    T("El modelo en edición (sobre una copia y luego se aplica a la sesión). Actúa sobre el elemento del diagnóstico o la selección.", "The model being edited (run on a copy, then applied to the session). Acts on the diagnostic's element or the selection."),
    T("Opcional por regla (0..N); una regla no puede repetir la misma. Una acción contiene 1..N operaciones ordenadas.", "Optional per rule (0..N); a rule cannot repeat the same one. An action contains 1..N ordered operations."),
    T("Elegir la acción entre las ofrecidas, rellenar los parámetros pedidos, ver la vista previa (opcional) y confirmar. Después puede deshacer y rehacer.", "Pick the action among those offered, fill in the requested parameters, view the preview (optional) and confirm. Afterwards it can be undone and redone."),
    T("Aplicada y resuelta, aplicada sin resolver, aplicada con errores, sin cambios, o deshecha/rehecha. Si falla en modo atómico se restaura el estado anterior.", "Applied and resolved, applied but unresolved, applied with errors, no changes, or undone/redone. If it fails in atomic mode the previous state is restored."),
    T("Implementada: tras aplicar (y tras deshacer/rehacer) se revalida de forma incremental sobre los elementos cambiados y se calcula si el diagnóstico original desapareció.", "Implemented: after applying (and after undo/redo) an incremental validation runs over the changed elements and it is computed whether the original diagnostic disappeared."),
    T("QuickFixRuntimeService.resolve/preview/apply/undo/redo; CanonicalActionExecutor.execute; pilas de deshacer y rehacer acotadas a 20 entradas por modelo; eventos core.quickfix.started/applied/failed/undone/redone; instantánea previa para compensación.", "QuickFixRuntimeService.resolve/preview/apply/undo/redo; CanonicalActionExecutor.execute; undo and redo stacks bounded to 20 entries per model; events core.quickfix.started/applied/failed/undone/redone; prior snapshot for compensation.")
  ]);
  // 1 — textual-model syntax fix + shared runtime
  C[1] = F([
    T("Un error de sintaxis del texto del modelo cuando el analizador espera una única opción inequívoca: falta una palabra, valor o símbolo, o hay uno equivocado. También cubre los Quick Fixes de validación aplicados sobre el texto.", "A syntax error in the model text when the parser expects a single unambiguous option: a word, value or symbol is missing or wrong. It also covers validation Quick Fixes applied on the text."),
    T("Sintaxis: solo si hay exactamente una opción esperada y es una palabra reservada o un valor de ejemplo (identificador, texto, número o booleano). Validación: solo si el texto actual se analiza sin errores.", "Syntax: only when there is exactly one expected option and it is a reserved word or a sample value (identifier, text, number or boolean). Validation: only if the current text parses without errors."),
    T("Error sintáctico del contrato de la gramática (la sugerencia viaja en los metadatos del diagnóstico) y diagnósticos de validación del modelo semántico del texto.", "Syntax error from the grammar contract (the suggestion travels in the diagnostic metadata) and validation diagnostics of the text's semantic model."),
    T("Sin configuración manual de la sugerencia sintáctica (se deduce de la gramática). La variante de validación reutiliza las acciones declaradas del modelo.", "No manual configuration of the syntax suggestion (derived from the grammar). The validation variant reuses the model's declared actions."),
    T("El texto del borrador: INSERT añade en la posición (con un espacio si hace falta); REPLACE sustituye el rango del error. En la variante de validación se edita el texto solo si el cambio semántico se concilia con él.", "The draft text: INSERT adds at the position (with a space if needed); REPLACE replaces the error range. In the validation variant the text is edited only if the semantic change reconciles with it."),
    T("Opcional; como máximo una sugerencia sintáctica por error. Las de validación: 0..N por diagnóstico.", "Optional; at most one syntax suggestion per error. Validation ones: 0..N per diagnostic."),
    T("Sintaxis: un clic en la sugerencia del panel de problemas. Validación: elegir acción, ver vista previa de las ediciones de texto propuestas y confirmar.", "Syntax: one click on the suggestion in the problems panel. Validation: pick the action, view the preview of the proposed text edits and confirm."),
    T("Sintaxis: el texto cambia y queda a la espera de validar. Validación: aplicada (texto y modelo sincronizados) o rechazada, con la sesión intacta, si no es segura o no resuelve el diagnóstico.", "Syntax: the text changes and waits to be validated. Validation: applied (text and model in sync) or rejected, leaving the session intact, if unsafe or if it does not resolve the diagnostic."),
    T("Sintaxis: se revalida el borrador tras editar. Validación: implementada y previa al guardado: se aplica en memoria, se reanaliza el texto, se comprueba equivalencia semántica y que el diagnóstico desaparece; si no, no se confirma nada.", "Syntax: the draft is revalidated after the edit. Validation: implemented and pre-commit: applied in memory, the text is reparsed, semantic equivalence is checked and the diagnostic must disappear; otherwise nothing is committed."),
    T("NeutralGrammarSyntaxContract.suggestedFix (metadatos quickFixKind/quickFixReplacement/quickFixLabel); TextualModelEditor.applySyntaxQuickFix; TextualQuickFixRuntimeService.validate/resolve/preview/apply con TextualCanonicalReconciliationService y commitReconciled con control de etag. Deshacer específico: " + NV, "NeutralGrammarSyntaxContract.suggestedFix (metadata quickFixKind/quickFixReplacement/quickFixLabel); TextualModelEditor.applySyntaxQuickFix; TextualQuickFixRuntimeService.validate/resolve/preview/apply with TextualCanonicalReconciliationService and etag-checked commitReconciled. Dedicated undo: " + NVE)
  ]);
  // 2 — grammar reference replacement
  C[2] = F([
    T("Una referencia a una regla o token que no se resuelve (no existe, otra capitalización, no visible o con tipo distinto) cuando hay un símbolo declarado parecido.", "A reference to a rule or token that does not resolve (missing, different case, not visible or of another kind) when a similar declared symbol exists."),
    T("Solo si hay al menos un candidato parecido. Se marca preferida y segura únicamente si la similitud y el margen sobre el segundo candidato superan los umbrales configurados y el tipo es compatible.", "Only if at least one similar candidate exists. Marked preferred and safe only if similarity and margin over the second candidate exceed the configured thresholds and the kind is compatible."),
    T("Diagnósticos de referencia de la gramática (no encontrada, mayúsculas distintas, no visible, importación ausente, tipo erróneo, ambigua); el diagnóstico guarda los identificadores de sus acciones.", "Grammar reference diagnostics (not found, case mismatch, not visible, missing import, wrong kind, ambiguous); the diagnostic keeps its action identifiers."),
    T("Umbrales de puntuación mínima y margen mínimo para marcarla como automática (propiedades del analizador de gramática). Precondición: hash del borrador analizado.", "Minimum score and minimum margin thresholds to mark it automatic (grammar analyzer properties). Precondition: hash of the analyzed draft."),
    T("El texto fuente de la gramática: sustituye solo el nombre de la referencia por el del candidato. No toca el modelo semántico.", "The grammar source text: replaces only the reference name with the candidate's. Does not touch the semantic model."),
    T("Opcional; una acción por referencia problemática (el mejor candidato).", "Optional; one action per problematic reference (the best candidate)."),
    T("Aplicar desde el panel de problemas o el editor; la acción no segura exige revisión antes de compilar.", "Apply from the problems panel or the editor; a non-safe action requires review before compiling."),
    T("Borrador de la gramática modificado, pendiente de nuevo análisis; se avisa si es segura o si debe revisarse.", "Grammar draft modified, pending a new analysis; the user is told whether it is safe or needs review."),
    T("Se rechaza si el borrador cambió respecto al análisis (revisión distinta). La reevaluación automática posterior a aplicar: " + NV, "Rejected if the draft changed relative to the analysis (different revision). Automatic re-evaluation after applying: " + NVE),
    T("GrammarSourceAnalyzer (kind quickfix.reference.replace, id replace-reference:doc:offset:nombre, metadato candidateScore); GrammarCodeAction; grammarCodeActions.applyGrammarCodeAction (aplica ediciones por documento).", "GrammarSourceAnalyzer (kind quickfix.reference.replace, id replace-reference:doc:offset:name, candidateScore metadata); GrammarCodeAction; grammarCodeActions.applyGrammarCodeAction (applies edits per document).")
  ]);
  // 3 — left recursion rewrite
  C[3] = F([
    T("Una recursión izquierda: una regla que puede volver a sí misma sin consumir entrada, directa o indirecta.", "A left recursion: a rule that can return to itself without consuming input, direct or indirect."),
    T("Solo si el análisis encuentra una reescritura posible para el ciclo. Si ANTLR admite de forma nativa esa recursión directa, el diagnóstico es informativo y no hace falta reescribir.", "Only if the analysis finds a possible rewrite for the cycle. If ANTLR natively supports that direct recursion, the diagnostic is informational and no rewrite is needed."),
    T("Diagnóstico de recursión izquierda de la gramática (error) o de recursión admitida nativamente (información), con tipo de recursión y ciclo en los metadatos.", "Grammar left-recursion diagnostic (error) or natively supported recursion (info), with recursion kind and cycle in the metadata."),
    T("Sin configuración manual. Marca de seguridad calculada por el análisis; hash del borrador como precondición.", "No manual configuration. Safety flag computed by the analysis; draft hash as precondition."),
    T("El texto de la regla afectada en la gramática: se sustituye su cuerpo por una forma base más repetición. Puede cambiar la forma del árbol de análisis.", "The text of the affected rule in the grammar: its body is replaced with a base-plus-repetition form. May change the shape of the parse tree."),
    T("Opcional; una acción por recursión con reescritura disponible.", "Optional; one action per recursion with an available rewrite."),
    T("Usar «Resolver automáticamente» y revisar el nuevo análisis antes de compilar; si no es segura queda marcada para revisión semántica.", "Use \"Resolve automatically\" and review the new analysis before compiling; if not safe it is flagged for semantic review."),
    T("Regla reescrita en el borrador, sin garantía de equivalencia si no es segura; hay que reanalizar antes de compilar.", "Rule rewritten in the draft, with no equivalence guarantee if not safe; a new analysis is needed before compiling."),
    T("Se rechaza si la revisión del borrador no coincide con la analizada. Reanálisis posterior automático: " + NV, "Rejected if the draft revision differs from the analyzed one. Automatic re-analysis afterwards: " + NVE),
    T("GrammarSourceAnalyzer.inspectRecursion (kind quickfix.grammar.left-recursion, id rewrite-left-recursion:doc:n, metadatos semanticReviewRequired y detail); GrammarRuleGraphAnalyzer.Rewrite/Recursion.", "GrammarSourceAnalyzer.inspectRecursion (kind quickfix.grammar.left-recursion, id rewrite-left-recursion:doc:n, semanticReviewRequired and detail metadata); GrammarRuleGraphAnalyzer.Rewrite/Recursion.")
  ]);
  // 4 — local grammar fixes
  C[4] = F([
    T("Tres fallos obvios del borrador: una referencia sin definir (sustituir por un símbolo parecido), una palabra de entrada mal escrita (poner «entry») y un «;» ausente al final de una regla.", "Three obvious draft failures: an undefined reference (replace with a similar symbol), a misspelled entry keyword (use \"entry\") and a missing \";\" at the end of a rule."),
    T("De inmediato, en el navegador, mientras llega el análisis completo del servidor. La sustitución de referencia exige candidatos con similitud mínima; el resto se ofrecen siempre.", "Immediately, in the browser, while the full server analysis is pending. Reference replacement requires candidates with minimum similarity; the others are always offered."),
    T("Diagnósticos locales GRAMMAR_LOCAL_UNDEFINED_REFERENCE, GRAMMAR_LOCAL_MALFORMED_ENTRY y GRAMMAR_LOCAL_MISSING_RULE_TERMINATOR.", "Local diagnostics GRAMMAR_LOCAL_UNDEFINED_REFERENCE, GRAMMAR_LOCAL_MALFORMED_ENTRY and GRAMMAR_LOCAL_MISSING_RULE_TERMINATOR."),
    T("Sin configuración. Hasta 4 candidatos por referencia, con similitud ≥ 0,45; preferida si ≥ 0,72 y automática si ≥ 0,82 (el mejor candidato); «entry» y «;» siempre seguras.", "No configuration. Up to 4 candidates per reference with similarity ≥ 0.45; preferred if ≥ 0.72 and automatic if ≥ 0.82 (top candidate); \"entry\" and \";\" always safe."),
    T("Solo el texto del borrador actual de la gramática; nunca el estado semántico.", "Only the text of the current grammar draft; never the semantic state."),
    T("Opcional; 0..4 propuestas por referencia sin definir, 1 por cada uno de los otros dos fallos.", "Optional; 0..4 proposals per undefined reference, 1 for each of the other two failures."),
    T("Elegir una propuesta en el panel de problemas o en el editor; se informa de si fue una corrección segura o debe revisarse.", "Choose a proposal in the problems panel or editor; the user is told whether it was a safe fix or needs review."),
    T("Borrador corregido, pendiente del análisis canónico del servidor.", "Draft corrected, pending the server's canonical analysis."),
    T("Local, no revalida por sí misma; se rechaza si la revisión del borrador no coincide con la analizada. Reanálisis posterior: " + NV, "Local, does not revalidate by itself; rejected if the draft revision differs from the analyzed one. Re-analysis afterwards: " + NVE),
    T("frontend/src/grammar-designer/grammarLocalQuickFixes.ts (buildLocalGrammarFeedback, similitud Levenshtein + prefijo); GrammarDesigner aplica con applyGrammarCodeAction.", "frontend/src/grammar-designer/grammarLocalQuickFixes.ts (buildLocalGrammarFeedback, Levenshtein + prefix similarity); GrammarDesigner applies via applyGrammarCodeAction.")
  ]);
  // 5 — transformation Quick Fix
  C[5] = F([
    T("Cuatro situaciones de una transformación entre modelos: conversión de tipo necesaria, colección que alimenta un destino de un solo valor, correspondencia que apunta a algo que ya no existe y atributo destino obligatorio sin correspondencia.", "Four situations in a model-to-model transformation: a required type conversion, a collection feeding a single-valued target, a mapping pointing to something that no longer exists, and a required target attribute without a mapping."),
    T("Solo para transformaciones entre modelos. Se piden con «Buscar Quick Fixes»; nada se calcula hasta que se pulsa. La correspondencia obligatoria solo se propone si hay un único atributo origen con el mismo nombre y tipo.", "Only for model-to-model transformations. They are requested with \"Find quick fixes\"; nothing is computed until pressed. The required mapping is proposed only if exactly one source attribute has the same name and type."),
    T("Diagnósticos de la transformación: conversión de tipo requerida, reducción de colección requerida, atributo o relación origen/destino desconocidos, y atributo destino obligatorio sin correspondencia.", "Transformation diagnostics: type conversion required, collection reduction required, unknown source/target attribute or relation, and required target attribute unmapped."),
    T("Sin configuración. Conversión elegida según el tipo del atributo destino (texto, entero, largo, decimal, booleano); la reducción de colección usa «primero».", "No configuration. Conversion chosen from the target attribute's type (text, integer, long, decimal, boolean); collection reduction uses \"first\"."),
    T("Una copia de la definición de la transformación (regla y correspondencia concretas); no cambia el borrador hasta aplicar la propuesta.", "A copy of the transformation definition (the specific rule and mapping); the draft is not changed until the proposal is applied."),
    T("Opcional; 0..N propuestas, como máximo una por diagnóstico y correspondencia. Ninguna está marcada como segura automática en el código.", "Optional; 0..N proposals, at most one per diagnostic and mapping. None is flagged as safe-automatic in the code."),
    T("Pulsar «Buscar Quick Fixes», revisar cada propuesta con su título y descripción y pulsar «Aplicar propuesta»; se avisa de que requiere revisión.", "Press \"Find quick fixes\", review each proposal by its title and description and press \"Apply proposal\"; it is flagged as requiring review."),
    T("El borrador de la transformación se sustituye por la propuesta y la lista de propuestas se vacía. No se persiste nada automáticamente.", "The transformation draft is replaced by the proposal and the proposal list is cleared. Nothing is persisted automatically."),
    T("No hay revalidación en el propio servicio ni en el panel; si el diseñador vuelve a comprobar tras aplicar: " + NV, "No revalidation in the service or the panel itself; whether the designer re-checks after applying: " + NVE),
    T("TransformationQuickFixService.suggest (conversionFix, collectionFix, removeBrokenBindingFix, requiredMappings); TransformationQuickFix (id fix:binding:conversion|first|remove, safeAutomatic=false); TransformationQuickFixController; frontend TransformationQuickFixPanel.tsx + api.transformationQuickFixes.", "TransformationQuickFixService.suggest (conversionFix, collectionFix, removeBrokenBindingFix, requiredMappings); TransformationQuickFix (id fix:binding:conversion|first|remove, safeAutomatic=false); TransformationQuickFixController; frontend TransformationQuickFixPanel.tsx + api.transformationQuickFixes.")
  ]);

  HM.facets({ caps: { validation: { elements: { qfcat_categories: { variants: C.map(function(f, i){ return { i: i, facets: f }; }) } } } } });

  // ------------------------------------------------------------------------------------------------
  // (B) Facets for the 10 operations of qf_ops
  // ------------------------------------------------------------------------------------------------
  var AV = T("Disponible cuando la Quick Fix que la contiene está activa, la regla la ofrece y su precondición se cumple; además el destino resuelto debe existir en el modelo.",
             "Available when the Quick Fix that contains it is enabled, the rule offers it and its precondition is met; in addition the resolved target must exist in the model.");
  var DG = T("La ofrece una regla mediante su diagnóstico; la operación en sí no valida nada.", "A rule offers it through its diagnostic; the operation itself validates nothing.");
  var RV = T("No revalida por sí sola: la revalidación incremental la hace la aplicación de la Quick Fix completa.", "Does not revalidate by itself: the incremental revalidation is done when the whole Quick Fix is applied.");
  var UNDO = {
    es: "Se puede deshacer junto con el resto de la Quick Fix si hubo cambios y la política de deshacer lo permite. ",
    en: "It can be undone together with the rest of the Quick Fix if there were changes and the undo policy allows it. "
  };
  function U(es, en){ return T(UNDO.es + es, UNDO.en + en); }
  var IMPL = function(cls, extra){ return T("ActionOperation subtipo " + cls + "; CanonicalActionExecutor.runOperation. " + extra, "ActionOperation subtype " + cls + "; CanonicalActionExecutor.runOperation. " + extra); };

  var O = [];
  O[0] = F([
    T("Un valor ausente o incorrecto de un atributo: lo escribe con un valor fijo, calculado por una expresión o pedido al aplicar.", "A missing or wrong attribute value: writes it with a fixed value, one computed by an expression or one asked when applying."),
    AV, DG,
    T("Selector de destino, atributo y origen del valor (fijo, expresión o parámetro; es obligatorio). Falla si el parámetro no se facilitó.", "Target selector, attribute and value source (fixed, expression or parameter; mandatory). Fails if the parameter was not provided."),
    T("Un atributo de un elemento. Sustituye el valor anterior.", "One attribute of one element. Replaces the previous value."),
    T("Repetible: 0..N en una misma Quick Fix; un atributo por operación.", "Repeatable: 0..N in one Quick Fix; one attribute per operation."),
    T("Solo si el valor es «pedido»: la persona lo escribe al aplicar. Si es fijo o calculado no pregunta.", "Only if the value is \"asked\": the person types it when applying. Fixed or computed values are not asked."),
    U("El atributo queda con el nuevo valor.", "The attribute holds the new value."), RV,
    IMPL("SetAttributeOperation", "Usa resolveValue (literal, expressionId o parameterName) y MutableState.setAttribute.")
  ]);
  O[1] = F([
    T("Un valor que no debería existir: deja un atributo sin valor.", "A value that should not exist: leaves an attribute without a value."),
    AV, DG,
    T("Selector de destino y atributo. No necesita origen de valor.", "Target selector and attribute. Needs no value source."),
    T("Un atributo de un elemento: se elimina su valor (no se elimina el elemento).", "One attribute of one element: its value is removed (the element is not)."),
    T("Repetible; un atributo por operación.", "Repeatable; one attribute per operation."),
    T("Ninguna adicional: no pide datos.", "None additional: it asks for no data."),
    U("El atributo queda sin valor; si un regla lo exige, puede volver a fallar.", "The attribute has no value; if a rule requires it, it may fail again."), RV,
    IMPL("ClearAttributeOperation", "MutableState.clearAttribute elimina la clave del mapa de atributos.")
  ]);
  O[2] = F([
    T("Un valor que ya está bien en otro elemento: lo copia de un atributo de un elemento origen a uno de destino.", "A value that is already right in another element: copies it from an attribute of a source element to a target one."),
    AV, DG,
    T("Selector de origen y atributo origen; selector de destino y atributo destino. Ambos elementos deben existir.", "Source selector and source attribute; target selector and target attribute. Both elements must exist."),
    T("El atributo destino del elemento destino; el origen no cambia.", "The target attribute of the target element; the source does not change."),
    T("Repetible; una copia por operación.", "Repeatable; one copy per operation."),
    T("Ninguna adicional.", "None additional."),
    U("El destino toma el valor actual del origen (nulo si el origen no lo tiene).", "The target takes the source's current value (null if the source has none)."), RV,
    IMPL("CopyAttributeOperation", "state.setAttribute(destino, targetAttributeId, state.attribute(origen, sourceAttributeId)).")
  ]);
  O[3] = F([
    T("Una conexión que falta entre dos elementos: crea el vínculo si no existía.", "A missing connection between two elements: creates the link if it did not exist."),
    AV, DG,
    T("Relación, selector de origen y selector de destino. Ambos elementos deben existir.", "Relation, source selector and target selector. Both elements must exist."),
    T("Añade un vínculo entre los dos elementos; si ya existía, no hace nada (idempotente).", "Adds a link between the two elements; if it already existed it does nothing (idempotent)."),
    T("Repetible; un vínculo por operación.", "Repeatable; one link per operation."),
    T("Ninguna adicional.", "None additional."),
    U("Existe el vínculo; sin duplicados.", "The link exists; no duplicates."), RV,
    IMPL("AddRelationOperation", "MutableState.addRelation comprueba existencia y genera id generated:relation:n. No valida cardinalidades del metamodelo: no verificado en este ejecutor.")
  ]);
  O[4] = F([
    T("Una conexión que sobra entre dos elementos: elimina el vínculo.", "A connection between two elements that should not be there: removes the link."),
    AV, DG,
    T("Relación, selector de origen y selector de destino.", "Relation, source selector and target selector."),
    T("Quita los vínculos de esa relación entre los dos elementos; los elementos permanecen.", "Removes the links of that relation between the two elements; the elements remain."),
    T("Repetible; un vínculo por operación. Si no existía, no falla.", "Repeatable; one link per operation. If there was none, it does not fail."),
    T("Ninguna adicional.", "None additional."),
    U("Los dos elementos quedan sin ese vínculo.", "The two elements no longer have that link."), RV,
    IMPL("RemoveRelationOperation", "MutableState.removeRelation usa removeIf sobre las relaciones.")
  ]);
  O[5] = F([
    T("Un elemento que falta: crea uno de una clase y, si se indica, lo aloja dentro de un contenedor; si no, queda como raíz.", "A missing element: creates one of a class and, if indicated, places it in a container; otherwise it becomes a root."),
    AV, DG,
    T("Clase, nombre de resultado (para referirlo en pasos posteriores), y opcionalmente contenedor y relación de contención (se indican juntos).", "Class, result name (to refer to it in later steps), and optionally container and containment relation (given together)."),
    T("Añade un elemento nuevo (sin atributos iniciales) y, con contenedor, un vínculo de contención.", "Adds a new element (with no initial attributes) and, with a container, a containment link."),
    T("Repetible; un elemento por operación. El nombre de resultado debe ser único para poder referirse a él.", "Repeatable; one element per operation. The result name must be unique to be referable."),
    T("Ninguna adicional; los valores se dan luego con «asignar atributo» sobre el elemento creado.", "None additional; values are given afterwards with \"set attribute\" on the created element."),
    U("Existe el elemento nuevo, con identificador generado, referible por su nombre de resultado.", "The new element exists, with a generated identifier, referable by its result name."), RV,
    IMPL("CreateElementOperation", "create(): id generated:resultName:n, annotations createdBy, createdElements y addRoot/addRelation.")
  ]);
  O[6] = F([
    T("Un elemento sobrante: lo borra junto con su contenido y los vínculos que lo tocan.", "A superfluous element: deletes it together with what it contains and the links touching it."),
    AV, DG,
    T("Solo selector de destino.", "Target selector only."),
    T("El elemento, todo lo que contiene (por contención, en cascada) y todas las relaciones que lo implican. Es la operación de mayor alcance.", "The element, everything it contains (by containment, cascading) and all relations involving it. It is the widest-reaching operation."),
    T("Repetible; un elemento y su subárbol por operación.", "Repeatable; one element and its subtree per operation."),
    T("Conviene revisar la vista previa: el efecto puede ser amplio.", "Reviewing the preview is advisable: the effect may be wide."),
    U("El elemento y su subárbol desaparecen y se limpian sus raíces y relaciones.", "The element and its subtree disappear and its roots and relations are cleaned up."), RV,
    IMPL("DeleteElementOperation", "MutableState.deleteElement + collectContained por relaciones de contención del metamodelo.")
  ]);
  O[7] = F([
    T("Un texto mal formateado: lo pasa a mayúsculas, minúsculas, capitaliza la primera letra o recorta los espacios de los extremos.", "A badly formatted text: converts to uppercase, lowercase, capitalizes the first letter or trims surrounding spaces."),
    AV, DG,
    T("Selector de destino, atributo y transformación (cuatro opciones: mayúsculas, minúsculas, capitalizar, recortar). Un valor nulo se trata como texto vacío.", "Target selector, attribute and transformation (four options: uppercase, lowercase, capitalize, trim). A null value is treated as empty text."),
    T("Un atributo de texto de un elemento: lo sustituye por su versión transformada.", "One text attribute of one element: replaces it with its transformed version."),
    T("Repetible; una transformación por operación.", "Repeatable; one transformation per operation."),
    T("Ninguna adicional.", "None additional."),
    U("El texto queda normalizado.", "The text is normalized."), RV,
    IMPL("TransformTextOperation", "transform(): switch UPPERCASE/LOWERCASE/CAPITALIZE/TRIM con Locale.ROOT.")
  ]);
  O[8] = F([
    T("Un arreglo ya definido: reutiliza otra Quick Fix pasándole argumentos, sin repetir sus pasos.", "A fix already defined: reuses another Quick Fix passing it arguments, without repeating its steps."),
    AV, DG,
    T("Identificador de la acción a ejecutar y argumentos con su origen de valor. La acción debe existir y sus parámetros obligatorios deben recibir valor; no puede llamarse a sí misma.", "Identifier of the action to run and arguments with their value source. The action must exist and its mandatory parameters must receive a value; it cannot call itself."),
    T("Lo que modifica la acción anidada (hereda su alcance).", "Whatever the nested action modifies (inherits its reach)."),
    T("Repetible; una acción anidada por operación.", "Repeatable; one nested action per operation."),
    T("Los argumentos se resuelven en el momento; los «pedidos» vienen de los parámetros de la acción externa.", "Arguments are resolved at that moment; \"asked\" ones come from the outer action's parameters."),
    U("El modelo queda como lo deja la acción anidada; una recursión detectada interrumpe la ejecución.", "The model is as the nested action leaves it; a detected recursion interrupts the run."), RV,
    IMPL("ExecuteActionOperation", "executeNested() → run() con actionStack para detectar recursión y validateArguments.")
  ]);
  O[9] = F([
    T("Un arreglo que ya existe como operación de tipo comando del metamodelo: la lanza sobre un elemento en lugar de repetir su lógica.", "A fix that already exists as a command-type metamodel operation: runs it on an element instead of repeating its logic."),
    AV, DG,
    T("Operación del metamodelo y selector de destino, y tantos argumentos como parámetros tenga la operación. Requisitos: es de tipo comando, tiene vínculo con una acción y la clase del destino es la dueña o una subclase.", "Metamodel operation and target selector, and as many arguments as the operation has parameters. Requirements: it is a command, it has a binding to an action and the target class is the owner or a subclass."),
    T("Lo que modifique la acción vinculada a la operación, sobre el destino indicado.", "Whatever the action bound to the operation modifies, on the indicated target."),
    T("Repetible; una invocación por operación. El número de argumentos debe coincidir exactamente.", "Repeatable; one invocation per operation. The number of arguments must match exactly."),
    T("Ninguna adicional; los argumentos salen de sus orígenes de valor.", "None additional; arguments come from their value sources."),
    U("El modelo queda como lo deja la acción vinculada; si algún requisito falla se interrumpe con un error explicativo.", "The model is as the bound action leaves it; if any requirement fails it stops with an explanatory error."), RV,
    IMPL("InvokeOperationOperation", "invokeCommand(): comprueba MetaOperationBehaviorKind.COMMAND, isSameOrSubtype, número de argumentos y OperationBinding con implementationKind ACTION.")
  ]);

  HM.facets({ caps: { validation: { elements: { qf_ops: { variants: O.map(function(f, i){ return { i: i, facets: f }; }) } } } } });

  // ------------------------------------------------------------------------------------------------
  // Lens for the new element
  // ------------------------------------------------------------------------------------------------
  HM.lens({ caps: { validation: { elements: { qfcat_categories: {
    nf: T("Categorías de Quick Fix (familias de corrección)", "Quick Fix categories (fix families)"),
    fm: T("Seis familias distintas, cada una con su disponibilidad, alcance y ciclo de vida: la acción declarativa del modelo (con vista previa, deshacer y revalidación), la corrección sintáctica y de validación del texto del modelo, dos acciones de código de gramática (sustituir referencia y reescribir recursión izquierda), las correcciones locales del borrador de gramática y las propuestas sobre transformaciones. Solo la primera usa el conjunto de acciones declarado en el modelo.",
          "Six distinct families, each with its own availability, scope and life cycle: the model's declarative action (with preview, undo and revalidation), the syntax and validation fix of the model text, two grammar code actions (replace reference and rewrite left recursion), the local fixes of the grammar draft and the proposals on transformations. Only the first uses the action set declared in the model."),
    tm: T("Familias implementadas por componentes separados: ActionDefinition/QuickFixRuntimeService/CanonicalActionExecutor; NeutralGrammarSyntaxContract.suggestedFix y TextualQuickFixRuntimeService; GrammarCodeAction generadas por GrammarSourceAnalyzer; grammarLocalQuickFixes.ts; TransformationQuickFixService.",
          "Families implemented by separate components: ActionDefinition/QuickFixRuntimeService/CanonicalActionExecutor; NeutralGrammarSyntaxContract.suggestedFix and TextualQuickFixRuntimeService; GrammarCodeAction produced by GrammarSourceAnalyzer; grammarLocalQuickFixes.ts; TransformationQuickFixService."),
    variants: [
      { k: "ActionDefinition", fd: T("Acción declarada en el modelo de acciones; ofrecida por reglas de validación; ejecutada con vista previa, aplicación transaccional, revalidación incremental, deshacer y rehacer.", "Action declared in the action model; offered by validation rules; run with preview, transactional application, incremental revalidation, undo and redo."),
        td: T("ActionDefinition → QuickFixRuntimeService → CanonicalActionExecutor; resultado QuickFixApplicationResult con estado y validación.", "ActionDefinition → QuickFixRuntimeService → CanonicalActionExecutor; QuickFixApplicationResult with status and validation.") },
      { k: "INSERT / REPLACE", fd: T("Sugerencia sintáctica local (insertar o sustituir) y Quick Fix de validación aplicado al texto con conciliación y equivalencia semántica.", "Local syntax suggestion (insert or replace) and validation Quick Fix applied to the text with reconciliation and semantic equivalence."),
        td: T("SuggestedFix(kind,replacement,label) en metadatos; TextualQuickFixRuntimeService.apply con reconcile + reanálisis + commitReconciled(etag).", "SuggestedFix(kind,replacement,label) in metadata; TextualQuickFixRuntimeService.apply with reconcile + reparse + commitReconciled(etag).") },
      { k: "quickfix.reference.replace", fd: T("Sustitución de una referencia no resuelta por el mejor candidato declarado, segura si supera umbrales de puntuación y margen.", "Replacement of an unresolved reference by the best declared candidate, safe if it exceeds score and margin thresholds."),
        td: T("GrammarCodeAction con edits, preconditions.draftSourceHash y metadata.candidateScore; GrammarSourceAnalyzer.", "GrammarCodeAction with edits, preconditions.draftSourceHash and metadata.candidateScore; GrammarSourceAnalyzer.") },
      { k: "quickfix.grammar.left-recursion", fd: T("Reescritura de una regla con recursión izquierda; segura según el análisis, con revisión semántica si no lo es.", "Rewrite of a rule with left recursion; safe according to the analysis, with semantic review if not."),
        td: T("GrammarCodeAction con Rewrite del grafo de reglas; metadata semanticReviewRequired y detail.", "GrammarCodeAction with a Rewrite from the rule graph; semanticReviewRequired and detail metadata.") },
      { k: "GRAMMAR_LOCAL_*", fd: T("Arreglos inmediatos en el navegador sobre el borrador: referencia sin definir, palabra de entrada mal escrita y «;» ausente.", "Immediate in-browser fixes on the draft: undefined reference, misspelled entry keyword and missing \";\"."),
        td: T("buildLocalGrammarFeedback: acciones con id local:<código>:…, source=local-inspection; Levenshtein + prefijo.", "buildLocalGrammarFeedback: actions with id local:<code>:…, source=local-inspection; Levenshtein + prefix.") },
      { k: "TransformationQuickFix", fd: T("Propuestas revisables sobre una transformación entre modelos, bajo demanda, que sustituyen el borrador al aplicarse.", "Reviewable proposals on a model-to-model transformation, on demand, that replace the draft when applied."),
        td: T("TransformationQuickFixService.suggest → TransformationQuickFix(id,diagnosticCode,ruleId,bindingId,title,description,safeAutomatic,proposedTransformation).", "TransformationQuickFixService.suggest → TransformationQuickFix(id,diagnosticCode,ruleId,bindingId,title,description,safeAutomatic,proposedTransformation).") }
    ]
  } } } } });
})();
