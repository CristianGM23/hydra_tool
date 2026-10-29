/* HYDRA portal content — capability "validation" (Validation & Quick Fixes). Registers into window.HM. */
(function(){ "use strict"; var HM = window.HM, T = HM.T;
var V = "src/main/java/com/example/editor/validation/";
var FD = "frontend/src/validation-designer/";

HM.cap("validation", {
  name: T("Validación y Quick Fixes", "Validation & Quick Fixes"), group: "behavior",
  user: {
    tagline: T("Detecta problemas en tus modelos y te ayuda a corregirlos con un clic", "Detects problems in your models and helps you fix them in one click"),
    what: T("Aquí defines reglas que comprueban que un modelo es correcto (por ejemplo «todo cliente debe tener un nombre») y, para cada regla, correcciones automáticas (Quick Fixes) que arreglan el problema por ti. Cuando una regla no se cumple, HYDRA muestra un diagnóstico claro sobre el elemento afectado.",
            "Here you define rules that check a model is correct (for example \"every customer must have a name\") and, for each rule, automatic corrections (Quick Fixes) that repair the problem for you. When a rule is broken, HYDRA shows a clear diagnostic on the affected element."),
    why: T("Para que los errores se encuentren pronto, se entiendan sin ser técnico y se puedan arreglar sin editar a mano; además permite impedir que se guarde o exporte un modelo con errores graves.",
           "So errors are found early, understood without technical knowledge and fixed without manual editing; it can also stop a model with serious errors from being saved or exported."),
    when: T("Después de tener un metamodelo guardado: cuando quieres garantizar calidad en los modelos que se editan con él, o importar reglas que ya tenías en otras herramientas.",
            "Once you have a saved metamodel: when you want to guarantee quality in the models edited with it, or to import rules you already had in other tools."),
    canDefine: [
      T("Reglas de validación con su gravedad y mensaje", "Validation rules with their severity and message"),
      T("Sobre qué se aplica cada regla (una clase, un atributo, una relación, un elemento, todo el modelo…)", "What each rule applies to (a class, an attribute, a relation, an element, the whole model...)"),
      T("Cuándo se comprueba (al abrir, al guardar, al exportar, a mano, de forma continua)", "When it is checked (on open, on save, on export, manually, continuously)"),
      T("La condición que debe cumplirse, con un constructor guiado o con expresiones completas", "The condition that must hold, with a guided builder or full expressions"),
      T("Grupos de reglas que se activan o desactivan juntas", "Groups of rules enabled or disabled together"),
      T("Quick Fixes: acciones reutilizables de 10 tipos (asignar, copiar, crear, borrar, relacionar, transformar texto…)", "Quick Fixes: reusable actions of 10 kinds (set, copy, create, delete, relate, transform text...)"),
      T("Parámetros, precondiciones y política de deshacer de cada corrección", "Parameters, preconditions and undo policy of each fix"),
      T("Importar reglas y correcciones de otras herramientas (OCL, EVL, EOL)", "Import rules and fixes from other tools (OCL, EVL, EOL)")
    ],
    needs: [
      T("Un metamodelo guardado (las reglas se escriben sobre sus clases, atributos y relaciones)", "A saved metamodel (rules are written over its classes, attributes and relations)"),
      T("Un modelo que validar (abierto en el editor o guardado en el proyecto)", "A model to validate (open in the editor or saved in the project)"),
      T("Para las condiciones avanzadas: el documento de expresiones del metamodelo", "For advanced conditions: the metamodel's expression document"),
      T("Para las Quick Fixes: un conjunto de acciones asociado al metamodelo", "For Quick Fixes: an action set linked to the metamodel")
    ],
    produces: [
      T("Diagnósticos: un aviso por cada incumplimiento, con su gravedad, mensaje y elemento afectado", "Diagnostics: one notice per violation, with its severity, message and affected element"),
      T("Un informe de validación con las reglas ejecutadas y las omitidas", "A validation report listing rules that ran and rules that were skipped"),
      T("Una decisión de bloqueo (permitir o impedir guardar/exportar)", "A blocking decision (allow or prevent save/export)"),
      T("Cambios en el modelo cuando se aplica una Quick Fix, con opción de deshacer y rehacer", "Model changes when a Quick Fix is applied, with undo and redo"),
      T("Eventos de validación que otras capacidades pueden aprovechar", "Validation events that other capabilities can use")
    ],
    mandatory: [
      T("Nombre, código del problema, gravedad y mensaje de cada regla", "Name, problem code, severity and message of each rule"),
      T("Sobre qué se aplica la regla y qué condición debe cumplir", "What the rule applies to and the condition it must meet"),
      T("Para cada Quick Fix: nombre, al menos una operación, política de transacción y de deshacer", "For each Quick Fix: name, at least one operation, transaction and undo policy")
    ],
    optional: [
      T("Descripción, grupo de reglas y notas", "Description, rule group and notes"),
      T("Qué Quick Fixes ofrece cada regla", "Which Quick Fixes each rule offers"),
      T("Parámetros y precondición de una Quick Fix", "Parameters and precondition of a Quick Fix")
    ],
    conditional: [
      T("Clase, atributo o relación afectados (según el ámbito elegido)", "Affected class, attribute or relation (depending on the chosen scope)"),
      T("Momentos de ejecución (obligatorios salvo en reglas externas)", "Execution moments (mandatory unless the rule is external)"),
      T("Representación original y estado de conversión (solo en reglas importadas)", "Original representation and conversion status (imported rules only)")
    ],
    next: [
      T("Comprobar las definiciones y corregir los diagnósticos de diseño", "Check the definitions and fix design diagnostics"),
      T("Validar un modelo real en el editor y aplicar Quick Fixes", "Validate a real model in the editor and apply Quick Fixes"),
      T("Consumir los eventos de validación (por ejemplo para logros o auditoría)", "Consume validation events (for example for achievements or audit)"),
      T("Exportar o generar solo cuando no queden errores bloqueantes", "Export or generate only when no blocking errors remain")
    ],
    interacts: [
      { cap: "metamodel", t: T("Las reglas y correcciones se escriben sobre sus clases, atributos, relaciones y operaciones", "Rules and fixes are written over its classes, attributes, relations and operations") },
      { cap: "model_editor", t: T("El editor valida al abrir y al cambiar, muestra los problemas y permite aplicar Quick Fixes", "The editor validates on open and on change, lists problems and lets you apply Quick Fixes") },
      { cap: "events", t: T("Cada validación y corrección publica eventos", "Every validation and fix publishes events") },
      { cap: "grammar", t: T("Los editores de texto tienen sus propias correcciones de código, independientes de estas", "Text editors have their own code fixes, independent from these") }
    ]
  },
  func: {
    lifecycle: [
      T("Diseño: regla creada → guardada → comprobada (sin errores de diseño) → activa", "Design: rule created → saved → checked (no design errors) → active"),
      T("Ejecución: se eligen las reglas aplicables según el modo → se evalúan sobre los elementos candidatos → se emiten diagnósticos → se consolida el informe → se decide si se bloquea", "Execution: applicable rules are chosen by mode → evaluated over candidate elements → diagnostics emitted → report consolidated → blocking decided"),
      T("Diagnóstico: abierto → (corrección aplicada) → resuelto, o sigue abierto si la corrección no lo arregla", "Diagnostic: open → (fix applied) → resolved, or still open if the fix does not repair it"),
      T("Quick Fix: definida → asociada a una regla → vista previa → aplicada → revalidada → (deshecha / rehecha)", "Quick Fix: defined → linked to a rule → previewed → applied → revalidated → (undone / redone)")
    ],
    semantics: [
      T("Una regla se clasifica en varios ejes independientes: ámbito (a qué se aplica), modo de ejecución, momentos de disparo y representación original. No existe un único «tipo de restricción».", "A rule is classified on several independent axes: scope (what it applies to), execution mode, triggers and original representation. There is no single \"constraint type\"."),
      T("Ámbitos: clase, atributo, relación, objeto, modelo, representación, selección y proyecto. Clase y objeto exigen indicar la clase; atributo exige clase y atributo de esa clase; relación exige una relación que parta de la clase.", "Scopes: class, attribute, relation, object, model, representation, selection and project. Class and object require a class; attribute requires a class and one of its attributes; relation requires a relation starting at the class."),
      T("Modos: incremental, solo completa, solo manual y externa. En modo completo o incremental el modo manda y los momentos de disparo se ignoran.", "Modes: incremental, full only, manual only and external. In full or incremental runs the mode decides and the triggers are ignored."),
      T("La condición debe devolver verdadero/falso; si devuelve otra cosa, la regla falla y se informa como error de ejecución, no como incumplimiento.", "The condition must return true/false; anything else makes the rule fail and it is reported as an execution error, not as a violation."),
      T("Gravedades: ERROR / WARNING / INFO. Solo los errores pueden bloquear; los fallos de ejecución de una regla cuentan siempre como error.", "Severities: ERROR / WARNING / INFO. Only errors can block; a rule execution failure always counts as an error."),
      T("Un diagnóstico tiene un identificador estable (mismo modelo, regla, elemento y atributo → mismo identificador), lo que permite saber qué se creó, qué sigue igual y qué se resolvió entre dos ejecuciones.", "A diagnostic has a stable identity (same model, rule, element and attribute → same identity), so it is possible to tell what was created, what is unchanged and what was resolved between two runs."),
      T("Solo las reglas con representación canónica y conversión completa son editables y ejecutables; las importadas de otras tecnologías se conservan pero se omiten de la ejecución y se avisa.", "Only rules with canonical representation and full conversion are editable and executable; rules imported from other technologies are preserved but skipped at execution, with a notice."),
      T("Una Quick Fix no tiene destino propio: actúa sobre el elemento del diagnóstico o sobre la selección. No hay corrección por tipo de elemento.", "A Quick Fix has no target of its own: it acts on the diagnostic's element or on the selection. There is no per-element-type fix.")
    ],
    states: [
      { n: T("Regla activa", "Active rule"), d: T("Se evalúa cuando el modo y los momentos lo piden", "Evaluated when mode and triggers require it") },
      { n: T("Regla desactivada", "Disabled rule"), d: T("Se conserva pero se omite (con aviso informativo)", "Kept but skipped (with an informational notice)") },
      { n: T("Regla verificada", "Verified rule"), d: T("Sin errores de diseño tras «Comprobar definiciones»; editarla la deja sin verificar", "No design errors after \"Check definitions\"; editing it un-verifies it") },
      { n: T("Diagnóstico abierto / resuelto", "Diagnostic open / resolved"), d: T("Abierto mientras el elemento incumple la regla", "Open while the element breaks the rule") },
      { n: T("Corrección: aplicada y resuelta / sin resolver / con errores / sin cambios / deshecha / rehecha", "Fix: applied and resolved / unresolved / with errors / no changes / undone / redone"), d: T("Resultado de aplicar o revertir una Quick Fix", "Result of applying or reverting a Quick Fix") }
    ],
    cross: [
      T("Metamodelo → Validación: las reglas referencian clases, atributos, relaciones y operaciones; si estas cambian, hay que recomprobar las reglas.", "Metamodel → Validation: rules reference classes, attributes, relations and operations; if those change, rules must be re-checked."),
      T("Editor de modelos → Validación: abrir, cambiar el modelo, cambiar de representación o pedir validación manual dispara la ejecución; tras aplicar una Quick Fix se revalida de forma incremental.", "Model editor → Validation: opening, changing the model, changing representation or requesting manual validation triggers execution; after a Quick Fix is applied it is revalidated incrementally."),
      T("Validación → Eventos: la ejecución completada, las violaciones creadas/resueltas y las correcciones aplicadas se publican como eventos; Gamificación y auditoría pueden consumirlos.", "Validation → Events: completed runs, created/resolved violations and applied fixes are published as events; Gamification and audit can consume them."),
      T("Validación → Guardado/exportación: la política de bloqueo decide si un modelo con errores puede guardarse o exportarse.", "Validation → Save/export: the blocking policy decides whether a model with errors may be saved or exported.")
    ]
  },
  tech: {
    runtime: T("Motor declarativo: el documento de validación se compila a un plan (con caché por huella SHA-256), un motor evalúa reglas sobre candidatos, un orquestador consolida informes y decide el bloqueo; las Quick Fixes las ejecuta un ejecutor canónico sobre una copia y aplican/deshacen con instantáneas.",
               "Declarative engine: the validation document is compiled to a plan (cached by SHA-256 fingerprint), an engine evaluates rules over candidates, an orchestrator consolidates reports and decides blocking; Quick Fixes run in a canonical executor over a copy and apply/undo with snapshots."),
    services: ["ValidationCompiler", "ValidationEngine", "ValidationRuntimeApplicationService", "ValidationOrchestrator", "DefaultValidationBlockingPolicy", "ValidationModelChecker", "SafeExternalConstraintTranslator", "CanonicalActionExecutor", "QuickFixRuntimeService", "EditorModelSessionService", "TextualQuickFixRuntimeService", "GrammarSourceAnalyzer"],
    apis: [
      "POST .../validations/runtime/validate (ValidationRuntimeController)",
      "POST .../validations/runtime/orchestrate?gate= (default NONE)",
      "POST /api/models/{modelId}/validation-runtime/validate",
      "GET|POST /problems/{diagnosticId}/quick-fixes, .../preview, .../apply; POST /quick-fixes/undo, /quick-fixes/redo",
      "Editor-definition session: apply Quick Fix with If-Match (EditorModelSessionController)",
      "POST /{grammarId}/code-actions (grammar, separate subsystem)",
      "Textual quick-fix endpoints (TextualModelSessionController)"
    ],
    schemas: ["ValidationModel", "ValidationRule", "ValidationRuleSet", "ValidationContext", "ValidationContextKind", "ValidationExecutionMode", "ValidationTrigger", "ValidationSeverity", "ValidationGate", "ModelValidationDiagnostic", "ModelValidationReport", "ImportedValidationSource", "ActionModel", "ActionDefinition", "ActionOperation (10 subtypes)", "ActionParameter", "ActionValueSource", "ActionSelectorKind", "ActionTransactionPolicy", "ActionUndoPolicy", "QuickFixApplicationStatus", "GrammarCodeAction"],
    tests: ["ValidationEngineTests", "ValidationCompilerTests", "ValidationOrchestratorTests", "DefaultValidationBlockingPolicyTests", "ValidationModelCheckerTests", "ValidationRuntimeControllerTests", "CanonicalValidationControllerTests", "QuickFixContractsTests", "CanonicalModelChangeAnalyzerTests", "ActionModelCheckerTests", "OperationActionExecutionTests", "CanonicalActionControllerTests", "JsonActionModelRepositoryTests", "ExternalValidationRecoveryRuntimeTests", "GrammarRuleGraphAnalyzerTests", "ValidationDesigner.test.tsx", "ExpressionBuilder.test.tsx", "ValidationRuleEditor.help.test.tsx", "ValidationRuleManager.test.tsx", "QuickFixDesigner.test.tsx", "ProblemsPanel.test.tsx", "problemsApi.test.ts", "validationDesignerValidation.test.ts", "validationVerification.test.ts", "grammarLocalQuickFixes.test.ts"],
    evidence: [
      { path: V + "ValidationRule.java", line: 12, label: "ValidationRule record (mandatory/optional fields :14-27)" },
      { path: V + "ValidationContextKind.java", line: 5, label: "8 scope kinds" },
      { path: V + "ValidationModelChecker.java", line: 122, end_line: 143, label: "scope checks per kind" },
      { path: V + "ValidationModelChecker.java", line: 176, end_line: 184, label: "trigger / mode / representation consistency rules" },
      { path: V + "runtime/ValidationCompiler.java", line: 42, end_line: 90, label: "compile + exclusion of non-canonical rules (:67-74)" },
      { path: V + "runtime/ValidationEngine.java", line: 200, end_line: 213, label: "rule selection by mode" },
      { path: V + "runtime/ValidationEngine.java", line: 269, end_line: 288, label: "message markers + stable diagnostic id" },
      { path: V + "runtime/ValidationRuntimeApplicationService.java", line: 57, end_line: 75, label: "validate: compile then engine" },
      { path: V + "orchestration/ValidationOrchestrator.java", line: 55, end_line: 168, label: "consolidation, blocking, events" },
      { path: V + "orchestration/DefaultValidationBlockingPolicy.java", line: 36, label: "only ERROR blocks" },
      { path: V + "action/ActionOperation.java", line: 8, end_line: 20, label: "10 operation kinds" },
      { path: V + "action/CanonicalActionExecutor.java", line: 57, end_line: 177, label: "executor, policies, operations" },
      { path: V + "quickfix/QuickFixRuntimeService.java", line: 94, end_line: 127, label: "preview / apply" },
      { path: "src/main/java/com/example/editor/definition/runtime/session/EditorModelSessionService.java", line: 538, end_line: 585, label: "manual validation + session Quick Fix apply" },
      { path: "src/main/java/com/example/editor/grammar/diagnostic/GrammarCodeAction.java", line: 7, end_line: 15, label: "grammar code action (separate subsystem)" },
      { path: "frontend/src/problems/ProblemsPanel.tsx", line: 62, end_line: 128, label: "preview/apply/undo/redo UI" },
      { path: FD + "ValidationDesigner.tsx", line: 62, label: "authoring workspace" }
    ],
    notes: [
      T("Cinco momentos de disparo (al crear, al cambiar atributo, al cambiar relación, al eliminar, en segundo plano) existen en el modelo y en el editor de reglas pero ningún código los lee: están declarados, no cableados.", "Five triggers (on create, on attribute change, on relation change, on delete, background) exist in the model and in the rule editor but no code reads them: declared, not wired."),
      T("En ejecuciones FULL e INCREMENTAL los triggers se ignoran; decide el modo. Para ON_OPEN/ON_SAVE/BEFORE_EXPORT/MANUAL se seleccionan las reglas cuyo trigger coincide.", "In FULL and INCREMENTAL runs triggers are ignored; the mode decides. For ON_OPEN/ON_SAVE/BEFORE_EXPORT/MANUAL the rules whose trigger matches are selected."),
      T("El guardado del documento de validación hace comprobación estática pero el backend no bloquea por errores; el bloqueo de guardado existe solo en el frontend.", "Saving the validation document runs the static check but the backend does not block on errors; save blocking exists only in the frontend."),
      T("La sesión del editor llama directamente a la ejecución de validación, no al orquestador: no hay consolidación ni política de bloqueo SAVE ahí. La puerta SAVE/EXPORT solo se activa vía parámetro REST (no hay llamador interno fijo).", "The editor session calls the validation run directly, not the orchestrator: no consolidation or SAVE blocking policy there. The SAVE/EXPORT gate is only reachable via the REST parameter (no fixed internal caller)."),
      T("relationInstanceId de la localización del diagnóstico nunca se rellena.", "The diagnostic location's relationInstanceId is never filled."),
      T("El editor de reglas ofrece AUTHORITATIVE_OPTIONS sin JAVASCRIPT ni BLOCKLY aunque el modelo los admite; el texto de ayuda del código dice que admite letras/dígitos/puntos/dos puntos/guiones pero el campo fuerza [A-Z0-9_] en mayúsculas.", "The rule editor's AUTHORITATIVE_OPTIONS omit JAVASCRIPT and BLOCKLY although the model supports them; the code help text says letters/digits/dots/colons/hyphens are accepted but the field forces uppercase [A-Z0-9_]."),
      T("El mensaje solo sustituye 5 marcadores (${rule} ${code} ${featureId} ${elementId} ${classId}); sin escape ni ICU; no hay i18n de mensajes en backend (solo el mensaje por defecto de la UI está traducido).", "The message only substitutes 5 markers (${rule} ${code} ${featureId} ${elementId} ${classId}); no escaping or ICU; no backend message i18n (only the UI default message is translated)."),
      T("No encontrado: catálogo de funciones integradas (el nodo FUNCTION es texto libre «registrado por el runtime»), editor textual de reglas, enum de categoría de diagnóstico (estructural/semántico/externo), validación de tipo de valor de parámetros en el ejecutor.", "NOT FOUND: built-in function catalogue (FUNCTION node is free text \"registered by runtime\"), textual rule editor, diagnostic category enum (structural/semantic/external), parameter value-type validation in the executor."),
      T("REQUIRED y OPTIONAL de la política de deshacer se comportan igual en ejecución; deshacer disponible = hubo cambios y política ≠ NOT_SUPPORTED.", "REQUIRED and OPTIONAL undo policies behave the same at runtime; undo available = changes made and policy ≠ NOT_SUPPORTED."),
      T("Las Quick Fixes del ámbito de texto/gramática son otro subsistema sin puente con el de modelos; el runtime de Quick Fix textual no tiene llamador en el frontend ni deshacer/rehacer; no hay tests que instancien QuickFixRuntimeService ni TextualQuickFixRuntimeService. La transformación tiene además su propio paquete de quick fixes (fuera de alcance).", "Text/grammar Quick Fixes are a different subsystem with no bridge to the model one; the textual quick-fix runtime has no frontend caller and no undo/redo; no tests instantiate QuickFixRuntimeService or TextualQuickFixRuntimeService. Transformation also has its own quick-fix package (out of scope)."),
      T("Las Quick Fixes no se invocan desde el espacio de trabajo de ejecución del editor de modelos, solo desde el panel de problemas y la sesión de definición del editor.", "Quick Fixes are not invoked from the model editor's runtime workspace, only from the problems panel and the editor-definition session.")
    ]
  },

  elements: [
    { id: "rule", n: T("Regla de validación", "Validation rule"), kind: "concept", req: "MANDATORY",
      mean: T("Una comprobación declarativa sobre el modelo, con nombre, gravedad, mensaje y una condición que debe cumplirse.", "A declarative check over the model, with a name, severity, message and a condition that must hold."),
      purpose: T("Expresar una exigencia de calidad del lenguaje para que HYDRA la compruebe sola.", "Express a language quality requirement so HYDRA checks it automatically."),
      when: T("Siempre que quieras garantizar algo sobre los modelos de tu lenguaje.", "Whenever you want to guarantee something about your language's models."),
      card: "1 modelo de validación ↔ 0..N reglas · 1 regla ↔ 0..N Quick Fixes",
      variants: [],
      props: [
        { n: T("Nombre", "Name"), req: "MANDATORY", d: T("Cómo se llama la regla; no puede estar vacío", "What the rule is called; cannot be empty") },
        { n: T("Código del problema", "Problem code"), req: "MANDATORY", d: T("Identificador corto en mayúsculas (letras, números y guion bajo) que acompaña a cada diagnóstico", "Short uppercase identifier (letters, digits, underscore) that accompanies each diagnostic") },
        { n: T("Gravedad", "Severity"), req: "MANDATORY", d: T("Error, aviso o información", "Error, warning or information") },
        { n: T("Mensaje", "Message"), req: "MANDATORY", d: T("Texto que verá quien edita el modelo; no puede estar vacío", "Text shown to whoever edits the model; cannot be empty") },
        { n: T("Condición", "Condition"), req: "MANDATORY", d: T("Qué debe cumplirse (referencia a una expresión)", "What must hold (reference to an expression)") },
        { n: T("Descripción", "Description"), req: "OPTIONAL", d: T("Explicación para quien mantiene la regla", "Explanation for whoever maintains the rule") },
        { n: T("Activa", "Active"), req: "OPTIONAL", d: T("Permite apagar la regla sin borrarla", "Lets you switch the rule off without deleting it") },
        { n: T("Correcciones ofrecidas", "Fixes offered"), req: "OPTIONAL", d: T("Qué Quick Fixes se proponen cuando se incumple", "Which Quick Fixes are proposed when it is broken") }
      ],
      io: { i: T("Elementos candidatos del modelo", "Candidate model elements"), o: T("Un diagnóstico por cada elemento que incumple", "One diagnostic per element that breaks it") },
      effect: T("Al ejecutarse, evalúa la condición sobre cada elemento del ámbito y emite diagnósticos con la gravedad y el mensaje de la regla.", "When run, it evaluates the condition over each element in scope and emits diagnostics with the rule's severity and message."),
      func: { rel: [T("Pertenece a 0..1 grupo de reglas", "Belongs to 0..1 rule group"), T("Referencia 1 condición y 0..N Quick Fixes", "References 1 condition and 0..N Quick Fixes"), T("Se aplica a un ámbito, con un modo y 0..N momentos de disparo", "Applies to a scope, with a mode and 0..N triggers")],
              lifecycle: T("creada → guardada → comprobada → activa / desactivada", "created → saved → checked → active / disabled"),
              cond: T("Nombre, código, gravedad, mensaje, ámbito y condición son obligatorios; no puede haber dos reglas con el mismo identificador.", "Name, code, severity, message, scope and condition are mandatory; two rules cannot share the same identifier.") },
      tech: { type: "ValidationRule", schema: "record: id, name, code, context, severity, conditionExpressionId, messageTemplate, executionMode, authoritativeRepresentation, conversionStatus (mandatory); description, triggers, dependencies, quickFixIds, annotations, enabled (optional)", evidence: [{ path: V + "ValidationRule.java", line: 12, label: "ValidationRule" }, { path: V + "ValidationModelChecker.java", line: 50, end_line: 184, label: "static check codes" }] } },

    { id: "scope", n: T("Ámbito de la regla", "Rule scope"), kind: "variant-set", req: "MANDATORY",
      mean: T("A qué parte del modelo se aplica la regla: un tipo, una propiedad, una relación, un elemento concreto o el modelo entero.", "Which part of the model the rule applies to: a type, a property, a relation, a specific element or the whole model."),
      purpose: T("Acotar qué elementos se evalúan y con qué datos de contexto.", "Narrow which elements are evaluated and with what context data."),
      when: T("Al crear la regla; condiciona qué campos de clase/atributo/relación hay que rellenar.", "When creating the rule; it determines which class/attribute/relation fields must be filled."),
      card: "1 regla ↔ 1 ámbito",
      variants: [
        { n: T("Clase", "Class"), d: T("Todos los elementos de esa clase y de sus subclases. Exige indicar la clase.", "All elements of that class and its subclasses. Requires the class."), req: "ALTERNATIVE" },
        { n: T("Atributo", "Attribute"), d: T("Una propiedad de una clase. Exige clase y un atributo que pertenezca a ella.", "A property of a class. Requires a class and an attribute belonging to it."), req: "ALTERNATIVE" },
        { n: T("Relación", "Relation"), d: T("Un vínculo que sale de una clase. Exige una relación que empiece en esa clase.", "A link leaving a class. Requires a relation starting at that class."), req: "ALTERNATIVE" },
        { n: T("Objeto", "Object"), d: T("Un elemento concreto de una clase. Exige la clase.", "A specific element of a class. Requires the class."), req: "ALTERNATIVE" },
        { n: T("Modelo", "Model"), d: T("El modelo completo, evaluado una sola vez.", "The whole model, evaluated once."), req: "ALTERNATIVE" },
        { n: T("Proyecto", "Project"), d: T("El proyecto entero, evaluado una sola vez.", "The whole project, evaluated once."), req: "ALTERNATIVE" },
        { n: T("Representación", "Representation"), d: T("Una vista del modelo; hoy se comporta como global (una sola evaluación).", "A view of the model; today it behaves as global (a single evaluation)."), req: "ALTERNATIVE" },
        { n: T("Selección", "Selection"), d: T("Solo los elementos seleccionados en ese momento.", "Only the elements selected at that moment."), req: "ALTERNATIVE" }
      ],
      props: [
        { n: T("Clase", "Class"), req: "CONDITIONAL", d: T("Obligatoria en clase, atributo, relación y objeto", "Mandatory for class, attribute, relation and object") },
        { n: T("Atributo", "Attribute"), req: "CONDITIONAL", d: T("Obligatorio solo en ámbito atributo", "Mandatory only for attribute scope") },
        { n: T("Relación", "Relation"), req: "CONDITIONAL", d: T("Obligatoria solo en ámbito relación", "Mandatory only for relation scope") }
      ],
      io: { i: T("Elección del diseñador", "Designer's choice"), o: T("Conjunto de elementos candidatos", "Set of candidate elements") },
      effect: T("Los ámbitos de modelo, proyecto y representación se evalúan una vez; los demás recorren los elementos candidatos.", "Model, project and representation scopes are evaluated once; the others go through candidate elements."),
      func: { rel: [T("Referencia a clases/atributos/relaciones del metamodelo", "References metamodel classes/attributes/relations")], lifecycle: T("Se comprueba al guardar y al comprobar definiciones", "Checked on save and when checking definitions"),
              cond: T("En modelo, proyecto, representación y selección, indicar clase o atributo solo genera un aviso (no se usan).", "For model, project, representation and selection, giving a class or attribute only raises a warning (they are not used).") },
      tech: { type: "ValidationContextKind / ValidationContext", schema: "enum CLASS, ATTRIBUTE, RELATION, OBJECT, MODEL, REPRESENTATION, SELECTION, PROJECT", evidence: [{ path: V + "ValidationContextKind.java", line: 5, label: "kinds" }, { path: V + "ValidationModelChecker.java", line: 122, end_line: 143, label: "checks" }, { path: V + "runtime/ValidationEngine.java", line: 215, end_line: 267, label: "candidates / global" }] } },

    { id: "execmode", n: T("Modo de ejecución", "Execution mode"), kind: "variant-set", req: "MANDATORY",
      mean: T("Cómo participa la regla en las validaciones: de forma continua, solo en validaciones completas, solo a petición o fuera de HYDRA.", "How the rule takes part in validations: continuously, only in full validations, on request only, or outside HYDRA."),
      purpose: T("Equilibrar coste y utilidad: reglas baratas se reevalúan al cambiar; las caras, solo cuando se pide.", "Balance cost and usefulness: cheap rules re-run on change; expensive ones only on request."),
      when: T("Al definir el contexto de la regla.", "When defining the rule's context."),
      card: "1 regla ↔ 1 modo",
      variants: [
        { n: T("Incremental", "Incremental"), d: T("Se reevalúa solo cuando cambian los elementos de los que depende.", "Re-evaluated only when the elements it depends on change."), req: "ALTERNATIVE" },
        { n: T("Solo completa", "Full only"), d: T("Solo entra en validaciones completas, al guardar o antes de exportar.", "Only in full validations, on save or before export."), req: "ALTERNATIVE" },
        { n: T("Solo manual", "Manual only"), d: T("Solo cuando alguien la lanza a mano.", "Only when someone runs it manually."), req: "ALTERNATIVE" },
        { n: T("Externa", "External"), d: T("La evalúa otra herramienta; HYDRA conserva la regla pero no la ejecuta.", "Evaluated by another tool; HYDRA keeps the rule but does not run it."), req: "ALTERNATIVE" }
      ],
      props: [{ n: T("Dependencias", "Dependencies"), req: "OPTIONAL", d: T("Qué clases, atributos, relaciones, operaciones o el modelo vigila una regla incremental", "Which classes, attributes, relations, operations or the model an incremental rule watches") }],
      io: { i: T("Modo pedido en la validación", "Mode requested for the validation"), o: T("Lista de reglas que se ejecutan y de omitidas", "List of rules that run and rules skipped") },
      effect: T("Una validación completa ejecuta todas las reglas salvo las solo manuales; una incremental solo las incrementales afectadas por los cambios.", "A full validation runs all rules except manual-only ones; an incremental one runs only the incremental rules affected by the changes."),
      func: { rel: [T("Limita qué momentos de disparo admite la regla", "Restricts which triggers the rule accepts")], lifecycle: T("Fijo mientras no se edite la regla", "Fixed until the rule is edited"),
              cond: T("Solo manual admite únicamente el disparo manual; solo completa admite validación completa, al guardar y antes de exportar; externa no puede ser canónica.", "Manual only admits just the manual trigger; full only admits full validation, on save and before export; external cannot be canonical.") },
      tech: { type: "ValidationExecutionMode", schema: "enum INCREMENTAL, FULL_ONLY, MANUAL_ONLY, EXTERNAL", evidence: [{ path: V + "runtime/ValidationEngine.java", line: 174, end_line: 213, label: "rule selection by mode and changed dependencies" }, { path: V + "ValidationModelChecker.java", line: 176, end_line: 179, label: "mode vs trigger checks" }] } },

    { id: "triggers", n: T("Momentos de disparo", "Triggers"), kind: "variant-set", req: "CONDITIONAL",
      mean: T("Los momentos en que se pide validar: al abrir, al guardar, antes de exportar, a mano, en validación completa y otros cinco aún no operativos.", "The moments when validation is requested: on open, on save, before export, manually, in full validation, and five others not yet operational."),
      purpose: T("Decidir cuándo se aplica la regla cuando se valida por momentos concretos.", "Decide when the rule applies when validating at specific moments."),
      when: T("Al definir «¿Cuándo debe ejecutarse?» en la regla.", "When defining \"When should it run?\" on the rule."),
      card: "1 regla ↔ 0..N momentos (obligatorio ≥1 salvo regla externa)",
      variants: [
        { n: T("Al abrir", "On open"), d: T("Al abrir el modelo en el editor", "When the model is opened in the editor"), req: "REPEATABLE" },
        { n: T("Al guardar", "On save"), d: T("Antes de guardar", "Before saving"), req: "REPEATABLE" },
        { n: T("Antes de exportar", "Before export"), d: T("Antes de exportar o generar", "Before exporting or generating"), req: "REPEATABLE" },
        { n: T("Manual", "Manual"), d: T("Cuando el usuario pide validar", "When the user asks to validate"), req: "REPEATABLE" },
        { n: T("Validación completa", "Full validation"), d: T("En cada validación completa", "In every full validation"), req: "REPEATABLE" },
        { n: T("Al crear · al cambiar un atributo · al cambiar una relación · al eliminar · en segundo plano", "On create · on attribute change · on relation change · on delete · background"), d: T("Se pueden elegir pero hoy ningún proceso los lee: no tienen efecto", "Can be chosen but nothing reads them today: no effect"), req: "OPTIONAL" }
      ],
      props: [{ n: T("Selección por casillas", "Checkbox selection"), req: "CONDITIONAL", d: T("Por defecto una regla nueva se ejecuta al guardar y a mano", "By default a new rule runs on save and manually") }],
      io: { i: T("Evento de validación pedido", "Requested validation event"), o: T("Reglas cuyo momento coincide", "Rules whose trigger matches") },
      effect: T("En validaciones por momento (abrir, guardar, exportar, manual) se ejecutan las reglas que lo declaran; en completa e incremental el modo decide y los momentos se ignoran.", "In moment-based validations (open, save, export, manual) the rules declaring it run; in full and incremental the mode decides and triggers are ignored."),
      func: { rel: [T("Depende del modo de ejecución", "Depends on the execution mode")], lifecycle: T("Declarado en la regla; leído solo en validaciones por momento", "Declared on the rule; read only in moment-based validations"),
              cond: T("Obligatorio salvo en reglas externas.", "Mandatory except for external rules.") },
      tech: { type: "ValidationTrigger", schema: "enum ON_OPEN, ON_SAVE, BEFORE_EXPORT, MANUAL, FULL_VALIDATION, ON_CREATE, ON_ATTRIBUTE_CHANGE, ON_RELATION_CHANGE, ON_DELETE, BACKGROUND", evidence: [{ path: V + "ValidationModelChecker.java", line: 176, label: "TRIGGER_REQUIRED" }, { path: FD + "ValidationRuleEditor.tsx", line: 29, end_line: 42, label: "UI offers all 10" }] } },

    { id: "condition", n: T("Condición", "Condition"), kind: "concept", req: "MANDATORY",
      mean: T("La expresión verdadero/falso que dice cuándo un elemento está bien; si da falso, hay diagnóstico.", "The true/false expression stating when an element is fine; if false, there is a diagnostic."),
      purpose: T("Capturar la lógica de la regla de forma que HYDRA la pueda ejecutar.", "Capture the rule's logic so HYDRA can execute it."),
      when: T("Paso «Condición» del asistente o sección de condición del formulario.", "\"Condition\" step of the wizard or condition section of the form."),
      card: "1 regla ↔ 1 condición (documento de expresiones compartido)",
      variants: [
        { n: T("Cláusulas guiadas", "Guided clauses"), d: T("Comprobar un atributo, contar relaciones o contar elementos de una clase, combinando con Y / O y negando con NO", "Check an attribute, count relations or count class elements, combined with AND / OR and negated with NOT"), req: "ALTERNATIVE" },
        { n: T("Árbol de expresión completo", "Full expression tree"), d: T("Literales, el propio elemento, el modelo, variables, instancias de clase, acceso a atributos, navegación de relaciones, llamadas a operaciones, operadores unarios y binarios, colecciones, condicionales y funciones", "Literals, the element itself, the model, variables, class instances, attribute access, relation navigation, operation calls, unary and binary operators, collections, conditionals and functions"), req: "ALTERNATIVE" },
        { n: T("Operadores de las cláusulas", "Clause operators"), d: T("Igual, distinto, mayor, mayor o igual, menor, menor o igual; en texto además contiene, empieza por, termina en, cumple patrón; vacío / no vacío; nulo / no nulo", "Equal, different, greater, greater or equal, less, less or equal; for text also contains, starts with, ends with, matches; empty / not empty; null / not null"), req: "OPTIONAL" }
      ],
      props: [
        { n: T("Tipo de resultado", "Result type"), req: "MANDATORY", d: T("Debe ser verdadero/falso (o indeterminado)", "Must be true/false (or undetermined)") },
        { n: T("Condición por defecto", "Default condition"), req: "OPTIONAL", d: T("Una regla nueva nace con «verdadero»", "A new rule starts with \"true\"") }
      ],
      io: { i: T("Elemento candidato", "Candidate element"), o: T("Verdadero (correcto) o falso (incumplimiento)", "True (fine) or false (violation)") },
      effect: T("Si no devuelve verdadero/falso, la regla falla y se informa como error de ejecución de la regla.", "If it does not return true/false, the rule fails and is reported as a rule execution error."),
      func: { rel: [T("Se guarda aparte, en el documento de expresiones del metamodelo", "Stored separately, in the metamodel's expression document")], lifecycle: T("Se guarda antes que las reglas", "Saved before the rules"),
              cond: T("Si falta la expresión referenciada: error (aviso en reglas externas). El conjunto de funciones disponibles no está catalogado.", "If the referenced expression is missing: error (warning for external rules). The set of available functions is not catalogued.") },
      tech: { type: "ExpressionModel (conditionExpressionId)", schema: "ExpressionModel; 13 node kinds in the UI builder", evidence: [{ path: V + "ValidationRule.java", line: 20, label: "conditionExpressionId" }, { path: V + "ValidationModelChecker.java", line: 101, end_line: 110, label: "missing / non-boolean checks" }, { path: FD + "ExpressionBuilder.tsx", line: 26, end_line: 29, label: "node kinds" }, { path: FD + "GuidedConditionBuilder.tsx", line: 32, end_line: 34, label: "check kinds" }] } },

    { id: "severity", n: T("Gravedad", "Severity"), kind: "variant-set", req: "MANDATORY",
      mean: T("Lo grave que es incumplir la regla.", "How serious it is to break the rule."),
      purpose: T("Distinguir lo que impide continuar de lo que solo informa.", "Tell what prevents proceeding apart from what merely informs."),
      when: T("Al definir la identidad de la regla.", "When defining the rule's identity."),
      card: "1 regla ↔ 1 gravedad",
      variants: [
        { n: T("Error", "Error"), d: T("Problema serio; es el único que puede bloquear guardar o exportar", "Serious problem; the only one that can block save or export"), req: "ALTERNATIVE" },
        { n: T("Aviso", "Warning"), d: T("Conviene revisarlo, pero no bloquea", "Worth reviewing, but does not block"), req: "ALTERNATIVE" },
        { n: T("Información", "Info"), d: T("Dato orientativo", "Informational note"), req: "ALTERNATIVE" }
      ],
      props: [{ n: T("Error de ejecución", "Execution error"), req: "DERIVED", d: T("Si una regla falla o está rota se informa siempre como error, sea cual sea su gravedad", "If a rule fails or is broken it is always reported as an error, whatever its severity") }],
      io: { i: T("Incumplimiento de la regla", "Rule violation"), o: T("Diagnóstico con esa gravedad", "Diagnostic with that severity") },
      effect: T("Determina si el diagnóstico puede bloquear y cómo se presenta.", "Determines whether the diagnostic can block and how it is shown."),
      func: { rel: [T("Alimenta la decisión de bloqueo", "Feeds the blocking decision")], lifecycle: T("Fija en la regla", "Fixed on the rule"), cond: T("Una regla nueva nace con gravedad Error.", "A new rule starts with severity Error.") },
      tech: { type: "ValidationSeverity", schema: "enum ERROR, WARNING, INFO", evidence: [{ path: V + "ValidationSeverity.java", line: 4, label: "severities" }, { path: V + "orchestration/DefaultValidationBlockingPolicy.java", line: 36, label: "only ERROR blocks" }] } },

    { id: "message", n: T("Mensaje del diagnóstico", "Diagnostic message"), kind: "concept", req: "MANDATORY",
      mean: T("El texto que explica el problema a quien edita el modelo; admite 5 marcadores que se sustituyen por datos reales.", "The text explaining the problem to whoever edits the model; accepts 5 markers replaced with real data."),
      purpose: T("Que el problema se entienda sin conocer la regla.", "Make the problem understandable without knowing the rule."),
      when: T("Paso «Diagnóstico y momentos» del asistente.", "\"Diagnostic & triggers\" step of the wizard."),
      card: "1 regla ↔ 1 mensaje",
      variants: [
        { n: T("Nombre de la regla, código, atributo, elemento, clase", "Rule name, code, attribute, element, class"), d: T("Los cinco datos que se pueden insertar en el texto", "The five pieces of data that can be inserted in the text"), req: "OPTIONAL" }
      ],
      props: [
        { n: T("Texto", "Text"), req: "MANDATORY", d: T("No puede estar vacío", "Cannot be empty") },
        { n: T("Mensaje por defecto", "Default message"), req: "OPTIONAL", d: T("La interfaz propone uno traducido", "The interface proposes a translated one") }
      ],
      io: { i: T("Plantilla de texto + datos del elemento", "Text template + element data"), o: T("Mensaje final del diagnóstico", "Final diagnostic message") },
      effect: T("Se compone al emitir el diagnóstico. Si la regla falla, el mensaje es «Rule execution failed: …». No hay traducción automática de los mensajes de regla.", "Composed when the diagnostic is emitted. If the rule fails, the message is \"Rule execution failed: ...\". Rule messages are not automatically translated."),
      func: { rel: [T("Pertenece a la regla", "Belongs to the rule")], lifecycle: T("Se sustituye en cada ejecución", "Substituted on every run"), cond: T("Sin formato especial ni escapes.", "No special formatting or escaping.") },
      tech: { type: "messageTemplate", schema: "string with ${rule} ${code} ${featureId} ${elementId} ${classId}", evidence: [{ path: V + "ValidationRule.java", line: 21, label: "messageTemplate" }, { path: V + "runtime/ValidationEngine.java", line: 269, end_line: 277, label: "marker substitution" }] } },

    { id: "diagnostic", n: T("Diagnóstico e informe", "Diagnostic and report"), kind: "derived", req: "DERIVED",
      mean: T("El resultado visible de validar: un diagnóstico por cada incumplimiento, agrupados en un informe de la ejecución.", "The visible outcome of validating: one diagnostic per violation, grouped in a report for the run."),
      purpose: T("Decirte qué está mal, dónde, y qué correcciones hay disponibles.", "Tell you what is wrong, where, and which fixes are available."),
      when: T("Tras cualquier validación y en el panel de problemas.", "After any validation and in the problems panel."),
      card: "1 informe ↔ 0..N diagnósticos · 1 diagnóstico ↔ 0..N Quick Fixes",
      variants: [
        { n: T("Incumplimiento", "Violation"), d: T("El elemento no cumple la condición", "The element does not meet the condition"), req: "ALTERNATIVE" },
        { n: T("Error de ejecución", "Execution error"), d: T("La regla no pudo evaluarse (por ejemplo, la condición no devolvió verdadero/falso)", "The rule could not be evaluated (for example the condition did not return true/false)"), req: "ALTERNATIVE" },
        { n: T("Regla rota", "Broken rule"), d: T("La regla tiene un problema de definición", "The rule has a definition problem"), req: "ALTERNATIVE" },
        { n: T("Alcance del informe", "Report scope"), d: T("Modelo, objeto, atributo, selección, una regla o un grupo de reglas", "Model, object, feature, selection, one rule or a rule group"), req: "OPTIONAL" }
      ],
      props: [
        { n: T("Localización", "Location"), req: "MANDATORY", d: T("Elemento y atributo afectados", "Affected element and attribute") },
        { n: T("Identificador estable", "Stable identity"), req: "DERIVED", d: T("Se mantiene igual entre ejecuciones, para saber qué se resolvió", "Stays the same between runs, to know what got resolved") },
        { n: T("Correcciones disponibles", "Available fixes"), req: "OPTIONAL", d: T("Las de la regla", "The rule's own") },
        { n: T("Reglas ejecutadas y omitidas", "Rules run and skipped"), req: "DERIVED", d: T("Parte del informe, con métricas", "Part of the report, with metrics") }
      ],
      io: { i: T("Reglas + modelo", "Rules + model"), o: T("Lista de problemas y decisión de bloqueo", "List of problems and blocking decision") },
      effect: T("Compara con el informe anterior: qué es nuevo, qué sigue igual y qué se resolvió; esos cambios se publican como eventos.", "Compares with the previous report: what is new, unchanged and resolved; these changes are published as events."),
      func: { rel: [T("Lo emite una regla sobre un elemento", "Emitted by a rule over an element"), T("Referencia 0..N Quick Fixes", "References 0..N Quick Fixes")], lifecycle: T("abierto → resuelto", "open → resolved"),
              cond: T("Las validaciones completas y por momento reemplazan el informe; las incrementales lo fusionan. No existe una clasificación por categorías (estructural, semántica, externa).", "Full and moment-based validations replace the report; incremental ones merge into it. There is no category classification (structural, semantic, external).") },
      tech: { type: "ModelValidationDiagnostic / ModelValidationReport / ValidationDiagnosticStatus", schema: "id, kind VIOLATION|RULE_EXECUTION_ERROR|BROKEN_RULE, ruleId, severity, message, location, quickFixIds, details; report: mode, scope (MODEL, OBJECT, FEATURE, SELECTION, RULE, RULE_SET), executedRuleIds, skippedRuleIds, metrics", evidence: [{ path: V + "runtime/ModelValidationDiagnostic.java", line: 10, label: "runtime diagnostic" }, { path: V + "runtime/ModelValidationReport.java", line: 9, label: "report" }, { path: V + "orchestration/ValidationOrchestrator.java", line: 132, end_line: 141, label: "created/unchanged/resolved" }] } },

    { id: "ruleset", n: T("Grupo de reglas", "Rule group"), kind: "concept", req: "OPTIONAL",
      mean: T("Un conjunto ordenado de reglas que se pueden activar o desactivar juntas.", "An ordered set of rules that can be enabled or disabled together."),
      purpose: T("Organizar reglas por tema y filtrarlas en el diseñador.", "Organize rules by topic and filter them in the designer."),
      when: T("Cuando hay muchas reglas.", "When there are many rules."),
      card: "1 grupo ↔ 0..N reglas",
      variants: [],
      props: [{ n: T("Orden", "Order"), req: "MANDATORY", d: T("Número ≥ 0", "Number ≥ 0") }, { n: T("Activo", "Enabled"), req: "OPTIONAL", d: T("Apaga todas sus reglas a la vez", "Switches off all its rules at once") }],
      io: { i: T("Reglas", "Rules"), o: T("Agrupación y filtro", "Grouping and filter") },
      effect: T("Permite pedir una validación solo de un grupo.", "Allows requesting a validation of only one group."),
      func: { rel: [T("Agrupa 0..N reglas", "Groups 0..N rules")], lifecycle: T("Siempre disponible", "Always available"), cond: T("El orden no puede ser negativo.", "Order cannot be negative.") },
      tech: { type: "ValidationRuleSet", schema: "record with order >= 0 and enabled", evidence: [{ path: V + "ValidationRuleSet.java", line: 12, label: "ValidationRuleSet" }] } },

    { id: "repr", n: T("Representación original y conversión", "Original representation and conversion"), kind: "variant-set", req: "CONDITIONAL",
      mean: T("En qué lenguaje está escrita la regla de verdad (la propia de HYDRA u otro importado) y hasta qué punto se pudo convertir.", "In which language the rule is truly written (HYDRA's own or an imported one) and how far it could be converted."),
      purpose: T("Conservar reglas importadas sin perder información y ser honesto sobre qué se ejecuta.", "Keep imported rules without losing information and be honest about what is executed."),
      when: T("Sobre todo al importar reglas; el formulario completo muestra una sección avanzada.", "Mostly when importing rules; the full form shows an advanced section."),
      card: "1 regla ↔ 1 representación y 1 estado de conversión",
      variants: [
        { n: T("Canónica (HYDRA)", "Canonical (HYDRA)"), d: T("Editable y ejecutable", "Editable and executable"), req: "ALTERNATIVE" },
        { n: T("OCL · EVL · ATL · Sirius AQL", "OCL · EVL · ATL · Sirius AQL"), d: T("Regla importada de otra tecnología", "Rule imported from another technology"), req: "ALTERNATIVE" },
        { n: T("Java · JavaScript · Blockly · Xtext", "Java · JavaScript · Blockly · Xtext"), d: T("Otras fuentes admitidas por el modelo (el formulario no ofrece JavaScript ni Blockly)", "Other sources supported by the model (the form does not offer JavaScript or Blockly)"), req: "ALTERNATIVE" },
        { n: T("Externa", "External"), d: T("Ejecutada fuera; no puede ser canónica", "Run outside; cannot be canonical"), req: "ALTERNATIVE" },
        { n: T("Conversión completa", "Full conversion"), d: T("Se convirtió sin pérdida", "Converted without loss"), req: "ALTERNATIVE" },
        { n: T("Conversión parcial", "Partial conversion"), d: T("Parte no se pudo traducir; se omite de la ejecución", "Part could not be translated; skipped at execution"), req: "ALTERNATIVE" },
        { n: T("Externa / no soportada", "External / unsupported"), d: T("No se convierte; se conserva solo para revisión", "Not converted; kept for review only"), req: "ALTERNATIVE" }
      ],
      props: [{ n: T("Origen", "Source"), req: "CONDITIONAL", d: T("Tecnología, nombre de fichero y huella del contenido importado", "Technology, file name and fingerprint of the imported content") }],
      io: { i: T("Regla importada", "Imported rule"), o: T("Regla canónica ejecutable o regla conservada y omitida", "Executable canonical rule or preserved and skipped rule") },
      effect: T("Al compilar, solo las reglas canónicas con conversión completa entran en el plan; el resto se omite con un aviso. Solo un subconjunto muy sencillo de OCL/EVL/AQL (comparaciones, Y/O/NO, nulo, longitud, recortar espacios) se traduce automáticamente, y solo si se puede probar que es equivalente.", "When compiling, only canonical rules with full conversion enter the plan; the rest are skipped with a notice. Only a very simple subset of OCL/EVL/AQL (comparisons, AND/OR/NOT, null, length, trim) is auto-translated, and only if equivalence can be proven."),
      func: { rel: [T("Pertenece a la regla", "Belongs to the rule")], lifecycle: T("importada → (parcial/externa) o canónica", "imported → (partial/external) or canonical"),
              cond: T("Canónica no puede tener conversión externa; sin origen en una regla no canónica se emite aviso.", "Canonical cannot have external conversion; missing source on a non-canonical rule raises a warning.") },
      tech: { type: "AuthoritativeRepresentation / ConversionStatus / ImportedValidationSource", schema: "enums CANONICAL, OCL, EVL, ATL, SIRIUS_AQL, JAVA, JAVASCRIPT, BLOCKLY, XTEXT, EXTERNAL; FULL, PARTIAL, EXTERNAL, UNSUPPORTED; source: technology, fileName, contentHash, conversionStatus", evidence: [{ path: V + "runtime/ValidationCompiler.java", line: 67, end_line: 74, label: "non-canonical omitted" }, { path: V + "importing/SafeExternalConstraintTranslator.java", line: 35, end_line: 51, label: "safe subset" }, { path: V + "ImportedValidationSource.java", line: 14, end_line: 18, label: "source" }, { path: FD + "validationRuleOptions.ts", line: 13, end_line: 22, label: "AUTHORITATIVE_OPTIONS" }] } },

    { id: "gate", n: T("Bloqueo al guardar o exportar", "Blocking on save or export"), kind: "variant-set", req: "OPTIONAL",
      mean: T("La decisión de impedir una operación (guardar o exportar) si hay errores.", "The decision to prevent an operation (save or export) if there are errors."),
      purpose: T("Evitar que salgan modelos con errores graves.", "Prevent models with serious errors from going out."),
      when: T("Al pedir una validación orquestada con una puerta de salida.", "When requesting an orchestrated validation with an exit gate."),
      card: "1 validación ↔ 1 decisión",
      variants: [
        { n: T("Ninguna", "None"), d: T("No bloquea nada (valor por defecto del servicio)", "Blocks nothing (service default)"), req: "ALTERNATIVE" },
        { n: T("Guardar", "Save"), d: T("Bloquea guardar si hay errores", "Blocks saving if there are errors"), req: "ALTERNATIVE" },
        { n: T("Exportar", "Export"), d: T("Bloquea exportar si hay errores", "Blocks export if there are errors"), req: "ALTERNATIVE" }
      ],
      props: [{ n: T("Operaciones bloqueantes", "Blocking operations"), req: "OPTIONAL", d: T("Por defecto se bloquean guardar y exportar; el diagnóstico puede declarar su propia lista", "By default save and export are blocked; a diagnostic may declare its own list") }],
      io: { i: T("Informe consolidado", "Consolidated report"), o: T("Permitir o bloquear", "Allow or block") },
      effect: T("Solo los diagnósticos de gravedad Error bloquean.", "Only diagnostics with Error severity block."),
      func: { rel: [T("Consume diagnósticos", "Consumes diagnostics")], lifecycle: T("Se decide en cada validación orquestada", "Decided on each orchestrated validation"),
              cond: T("En el editor de definición, el bloqueo de guardado del documento de validación solo existe en la interfaz; la sesión del editor no aplica esta política.", "In the definition editor, blocking the save of the validation document exists only in the interface; the editor session does not apply this policy.") },
      tech: { type: "ValidationGate / DefaultValidationBlockingPolicy", schema: "enum NONE, SAVE, EXPORT", evidence: [{ path: V + "orchestration/ValidationGate.java", line: 4, label: "gates" }, { path: V + "orchestration/DefaultValidationBlockingPolicy.java", line: 29, end_line: 36, label: "policy" }] } },

    { id: "qf_action", n: T("Quick Fix (acción de corrección)", "Quick Fix (fix action)"), kind: "concept", req: "OPTIONAL",
      mean: T("Una acción reutilizable que arregla un problema: se compone de una o más operaciones sobre el modelo.", "A reusable action that fixes a problem: made of one or more operations on the model."),
      purpose: T("Convertir un diagnóstico en un arreglo de un clic, con vista previa y deshacer.", "Turn a diagnostic into a one-click fix, with preview and undo."),
      when: T("Cuando una regla tiene un arreglo evidente y seguro.", "When a rule has an obvious, safe fix."),
      card: "1 Quick Fix ↔ 1..N operaciones · 0..N reglas la ofrecen",
      variants: [],
      props: [
        { n: T("Nombre", "Name"), req: "MANDATORY", d: T("No puede estar vacío", "Cannot be empty") },
        { n: T("Operaciones", "Operations"), req: "MANDATORY", d: T("Al menos una (salvo acción importada solo para revisión)", "At least one (except an imported review-only action)") },
        { n: T("Política de transacción y de deshacer", "Transaction and undo policy"), req: "MANDATORY", d: T("Ver «Política de seguridad»", "See \"Safety policy\"") },
        { n: T("Descripción", "Description"), req: "OPTIONAL", d: T("Qué hace la corrección", "What the fix does") },
        { n: T("Activa", "Enabled"), req: "OPTIONAL", d: T("Una acción importada solo para revisión debe estar desactivada", "An imported review-only action must be disabled") }
      ],
      io: { i: T("Un diagnóstico (o una selección) + parámetros", "A diagnostic (or a selection) + parameters"), o: T("Modelo corregido y resumen de cambios", "Fixed model and change summary") },
      effect: T("Solo se ofrece si la regla que generó el diagnóstico la lista, existe en el conjunto de acciones, está activa y cumple su precondición.", "Only offered if the rule that produced the diagnostic lists it, it exists in the action set, is enabled and meets its precondition."),
      func: { rel: [T("Ofrecida por 0..N reglas (una regla no puede repetirla)", "Offered by 0..N rules (a rule cannot repeat it)"), T("Actúa sobre el elemento del diagnóstico o sobre la selección", "Acts on the diagnostic's element or on the selection")],
              lifecycle: T("definida → asociada → vista previa → aplicada → revalidada", "defined → linked → previewed → applied → revalidated"),
              cond: T("No hay «tipos» de Quick Fix: lo que la caracteriza es el conjunto de operaciones que contiene.", "There are no Quick Fix \"types\": what characterizes it is the set of operations it contains.") },
      tech: { type: "ActionDefinition", schema: "id, name, operations, transactionPolicy, undoPolicy (mandatory); description, enabled, parameters, preconditionExpressionId", evidence: [{ path: V + "action/ActionDefinition.java", line: 12, end_line: 25, label: "ActionDefinition" }, { path: V + "ValidationModelChecker.java", line: 115, end_line: 173, label: "DUPLICATE_QUICK_FIX / UNKNOWN_QUICK_FIX / ACTION_MODEL_REQUIRED" }] } },

    { id: "qf_ops", n: T("Operaciones de corrección", "Fix operations"), kind: "variant-set", req: "REPEATABLE",
      mean: T("Los pasos elementales que realiza una Quick Fix sobre el modelo. Hay 10 clases.", "The elementary steps a Quick Fix performs on the model. There are 10 kinds."),
      purpose: T("Combinar pasos sencillos para cubrir la corrección.", "Combine simple steps to cover the correction."),
      when: T("En el paso «Operaciones» del editor de Quick Fix.", "In the \"Operations\" step of the Quick Fix editor."),
      card: "1 Quick Fix ↔ 1..N operaciones (ordenadas)",
      variants: [
        { n: T("Asignar atributo", "Set attribute"), d: T("Pone un valor en un atributo", "Puts a value in an attribute"), req: "ALTERNATIVE" },
        { n: T("Vaciar atributo", "Clear attribute"), d: T("Borra el valor de un atributo", "Removes an attribute's value"), req: "ALTERNATIVE" },
        { n: T("Copiar atributo", "Copy attribute"), d: T("Copia el valor de un atributo de un elemento a otro", "Copies an attribute value from one element to another"), req: "ALTERNATIVE" },
        { n: T("Añadir relación", "Add relation"), d: T("Vincula dos elementos si no lo estaban", "Links two elements if not already linked"), req: "ALTERNATIVE" },
        { n: T("Quitar relación", "Remove relation"), d: T("Elimina el vínculo entre dos elementos", "Removes the link between two elements"), req: "ALTERNATIVE" },
        { n: T("Crear elemento", "Create element"), d: T("Crea un elemento de una clase, con un nombre de resultado para referirse a él luego y, si se quiere, dentro de un contenedor", "Creates an element of a class, with a result name to refer to it later and optionally inside a container"), req: "ALTERNATIVE" },
        { n: T("Eliminar elemento", "Delete element"), d: T("Borra el elemento, su contenido contenido y sus relaciones", "Deletes the element, what it contains and its relations"), req: "ALTERNATIVE" },
        { n: T("Transformar texto", "Transform text"), d: T("Pasa a mayúsculas, minúsculas, capitaliza o recorta espacios", "Converts to uppercase, lowercase, capitalizes or trims spaces"), req: "ALTERNATIVE" },
        { n: T("Ejecutar otra acción", "Run another action"), d: T("Reutiliza otra Quick Fix con argumentos (no puede llamarse a sí misma)", "Reuses another Quick Fix with arguments (it cannot call itself)"), req: "ALTERNATIVE" },
        { n: T("Invocar operación del metamodelo", "Invoke metamodel operation"), d: T("Lanza una operación de tipo comando definida en el metamodelo", "Runs a command-type operation defined in the metamodel"), req: "ALTERNATIVE" }
      ],
      props: [
        { n: T("Destino", "Target"), req: "MANDATORY", d: T("Sobre qué elemento actúa (ver «Selector de destino»)", "Which element it acts on (see \"Target selector\")") },
        { n: T("Atributo / relación / clase", "Attribute / relation / class"), req: "CONDITIONAL", d: T("Según la operación", "Depending on the operation") },
        { n: T("Origen del valor", "Value source"), req: "CONDITIONAL", d: T("En asignar y copiar", "In set and copy") },
        { n: T("Argumentos", "Arguments"), req: "CONDITIONAL", d: T("Solo al ejecutar otra acción o invocar una operación", "Only when running another action or invoking an operation") }
      ],
      io: { i: T("Elementos del modelo", "Model elements"), o: T("Cambios en el modelo", "Model changes") },
      effect: T("Se ejecutan en orden sobre una copia del modelo.", "Run in order over a copy of the model."),
      func: { rel: [T("Pertenecen a una Quick Fix", "Belong to a Quick Fix")], lifecycle: T("Se ejecutan en secuencia", "Run in sequence"), cond: T("Crear elemento: contenedor y relación de contención se indican juntos. Invocar: solo operaciones de tipo comando con vínculo a acción.", "Create element: container and containment relation are given together. Invoke: only command-type operations bound to actions.") },
      tech: { type: "ActionOperation (sealed, @JsonTypeInfo kind)", schema: "SET_ATTRIBUTE, CLEAR_ATTRIBUTE, COPY_ATTRIBUTE, ADD_RELATION, REMOVE_RELATION, CREATE_ELEMENT, DELETE_ELEMENT, TRANSFORM_TEXT, EXECUTE_ACTION, INVOKE_OPERATION", evidence: [{ path: V + "action/ActionOperation.java", line: 8, end_line: 20, label: "subtypes" }, { path: V + "action/CanonicalActionExecutor.java", line: 120, end_line: 177, label: "per-operation execution" }] } },

    { id: "qf_params", n: T("Parámetros y origen del valor", "Parameters and value source"), kind: "concept", req: "OPTIONAL",
      mean: T("Datos que una Quick Fix necesita: se fijan de antemano, se calculan con una expresión o se piden a la persona al aplicar.", "Data a Quick Fix needs: fixed in advance, computed by an expression, or asked from the person when applying."),
      purpose: T("Hacer la corrección flexible sin duplicarla.", "Make the fix flexible without duplicating it."),
      when: T("Cuando el arreglo depende de un dato (un nombre nuevo, un valor).", "When the fix depends on a datum (a new name, a value)."),
      card: "1 Quick Fix ↔ 0..N parámetros · 1 valor ↔ 1 origen",
      variants: [
        { n: T("Texto · entero · decimal · sí/no", "Text · integer · decimal · yes/no"), d: T("Tipos de dato básicos", "Basic data types"), req: "ALTERNATIVE" },
        { n: T("Elemento · clase · relación · atributo", "Element · class · relation · attribute"), d: T("Referencias al modelo o al metamodelo", "References to the model or metamodel"), req: "ALTERNATIVE" },
        { n: T("Valor fijo", "Fixed value"), d: T("Se escribe en la propia acción", "Written in the action itself"), req: "ALTERNATIVE" },
        { n: T("Valor calculado", "Computed value"), d: T("Lo da una expresión", "Provided by an expression"), req: "ALTERNATIVE" },
        { n: T("Valor pedido", "Asked value"), d: T("Lo da la persona al aplicar", "Provided by the person when applying"), req: "ALTERNATIVE" }
      ],
      props: [{ n: T("Nombre", "Name"), req: "MANDATORY", d: T("Un parámetro nuevo nace como texto obligatorio", "A new parameter starts as required text") }, { n: T("Tipo", "Type"), req: "MANDATORY", d: T("Uno de los ocho tipos", "One of the eight types") }, { n: T("Obligatorio", "Required"), req: "OPTIONAL", d: T("Si falta, se rechaza la aplicación", "If missing, applying is rejected") }],
      io: { i: T("Valores dados al aplicar", "Values given when applying"), o: T("Valor usado por la operación", "Value used by the operation") },
      effect: T("Se rechazan argumentos desconocidos o faltantes. El tipo del valor no se comprueba al ejecutar.", "Unknown or missing arguments are rejected. The value's type is not checked on execution."),
      func: { rel: [T("Pertenece a una Quick Fix", "Belongs to a Quick Fix")], lifecycle: T("Se resuelve al aplicar", "Resolved when applying"), cond: T("Un origen de valor tiene exactamente una de las tres formas.", "A value source has exactly one of the three forms.") },
      tech: { type: "ActionParameter / ActionValueSource / ActionParameterType", schema: "STRING, INTEGER, DECIMAL, BOOLEAN, ELEMENT_ID, CLASS_ID, RELATION_ID, ATTRIBUTE_ID; literal | expressionId | parameterName", evidence: [{ path: V + "action/ActionValueSource.java", line: 14, end_line: 15, label: "exactly one source" }, { path: V + "action/CanonicalActionExecutor.java", line: 62, end_line: 70, label: "validateArguments" }, { path: FD + "QuickFixDesigner.tsx", line: 200, label: "+ Parameter" }] } },

    { id: "qf_selector", n: T("Selector de destino", "Target selector"), kind: "variant-set", req: "MANDATORY",
      mean: T("Cómo elige una operación el elemento sobre el que actúa.", "How an operation chooses the element it acts on."),
      purpose: T("Apuntar al elemento problemático, a otro elegido o a uno recién creado.", "Point to the problem element, a chosen one or a newly created one."),
      when: T("En cada operación del editor.", "In every operation of the editor."),
      card: "1 operación ↔ 1..2 selectores (destino y origen)",
      variants: [
        { n: T("El propio elemento", "The element itself"), d: T("El elemento del diagnóstico", "The diagnostic's element"), req: "ALTERNATIVE" },
        { n: T("La selección", "The selection"), d: T("El primer elemento seleccionado en el editor", "The first element selected in the editor"), req: "ALTERNATIVE" },
        { n: T("Un parámetro", "A parameter"), d: T("El elemento que indique un parámetro", "The element a parameter points to"), req: "ALTERNATIVE" },
        { n: T("Elemento creado", "Created element"), d: T("Uno creado antes en la misma acción, por su nombre de resultado", "One created earlier in the same action, by its result name"), req: "ALTERNATIVE" },
        { n: T("Elemento concreto", "Specific element"), d: T("Un identificador fijo", "A fixed identifier"), req: "ALTERNATIVE" }
      ],
      props: [], io: { i: T("Diagnóstico, selección, parámetros", "Diagnostic, selection, parameters"), o: T("Elemento resuelto", "Resolved element") },
      effect: T("Se resuelve en cada aplicación.", "Resolved on every application."),
      func: { rel: [T("Pertenece a una operación", "Belongs to an operation")], lifecycle: T("Se resuelve al ejecutar", "Resolved at execution"), cond: T("No existe destino propio de la Quick Fix por tipo de elemento.", "There is no per-element-type target on the Quick Fix itself.") },
      tech: { type: "ActionSelectorKind", schema: "SELF, SELECTION, PARAMETER, CREATED_ELEMENT, ELEMENT_ID", evidence: [{ path: "src/main/java/com/example/editor/validation/quickfix/QuickFixRuntimeService.java", line: 181, end_line: 194, label: "selector resolution" }] } },

    { id: "qf_policy", n: T("Política de seguridad y precondición", "Safety policy and precondition"), kind: "variant-set", req: "MANDATORY",
      mean: T("Qué pasa si algo falla a mitad de la corrección, si se puede deshacer y bajo qué condición se ofrece.", "What happens if something fails halfway, whether it can be undone and under which condition it is offered."),
      purpose: T("Que una corrección nunca deje el modelo a medias sin que lo sepas.", "A fix must never leave the model half-done without you knowing."),
      when: T("Paso «Seguridad» del asistente de Quick Fix.", "\"Safety\" step of the Quick Fix wizard."),
      card: "1 Quick Fix ↔ 1 transacción, 1 deshacer, 0..1 precondición",
      variants: [
        { n: T("Atómica", "Atomic"), d: T("Si una operación falla, se revierte todo", "If one operation fails, everything is rolled back"), req: "ALTERNATIVE" },
        { n: T("Mejor esfuerzo", "Best effort"), d: T("Se aplican las que se pueden y se informa de los fallos", "Applies what it can and reports failures"), req: "ALTERNATIVE" },
        { n: T("Deshacer obligatorio", "Undo required"), d: T("Debe poderse deshacer (hoy se comporta igual que opcional)", "Must be undoable (today it behaves like optional)"), req: "ALTERNATIVE" },
        { n: T("Deshacer opcional", "Undo optional"), d: T("Se puede deshacer si hubo cambios", "Undoable if there were changes"), req: "ALTERNATIVE" },
        { n: T("Sin deshacer", "No undo"), d: T("No se guarda para deshacer", "Not kept for undo"), req: "ALTERNATIVE" }
      ],
      props: [{ n: T("Precondición", "Precondition"), req: "OPTIONAL", d: T("Expresión que debe dar verdadero para ofrecer la corrección; debe existir y devolver verdadero/falso", "Expression that must yield true for the fix to be offered; it must exist and return true/false") }],
      io: { i: T("Diagnóstico y modelo", "Diagnostic and model"), o: T("Corrección disponible o no", "Fix available or not") },
      effect: T("En modo atómico se vuelve a la instantánea previa ante cualquier error.", "In atomic mode it returns to the previous snapshot on any error."),
      func: { rel: [T("Pertenece a la Quick Fix", "Belongs to the Quick Fix")], lifecycle: T("Se evalúa al listar y aplicar", "Evaluated when listing and applying"), cond: T("Por defecto: atómica y deshacer obligatorio.", "By default: atomic and undo required.") },
      tech: { type: "ActionTransactionPolicy / ActionUndoPolicy / preconditionExpressionId", schema: "ATOMIC|BEST_EFFORT; REQUIRED|OPTIONAL|NOT_SUPPORTED", evidence: [{ path: V + "action/CanonicalActionExecutor.java", line: 57, end_line: 112, label: "policies, precondition" }, { path: V + "action/ActionModelChecker.java", line: 109, end_line: 117, label: "precondition checks" }] } },

    { id: "qf_result", n: T("Resultado de aplicar una Quick Fix", "Quick Fix result"), kind: "derived", req: "DERIVED",
      mean: T("Lo que ocurre al previsualizar, aplicar, deshacer o rehacer una corrección, y su estado final.", "What happens when previewing, applying, undoing or redoing a fix, and its final status."),
      purpose: T("Dar confianza: ves los cambios antes de aplicarlos y puedes revertirlos.", "Give confidence: you see the changes before applying and can revert them."),
      when: T("En el panel de problemas, al elegir un diagnóstico.", "In the problems panel, when selecting a diagnostic."),
      card: "1 aplicación ↔ 1 resumen de cambios ↔ 1 revalidación",
      variants: [
        { n: T("Vista previa", "Preview"), d: T("Ejecuta sobre una copia, muestra el resumen de cambios y no toca el modelo ni revalida", "Runs on a copy, shows the change summary and touches neither the model nor validation"), req: "ALTERNATIVE" },
        { n: T("Aplicada y resuelta", "Applied and resolved"), d: T("Cambió el modelo y el diagnóstico desapareció", "The model changed and the diagnostic disappeared"), req: "ALTERNATIVE" },
        { n: T("Aplicada sin resolver", "Applied, unresolved"), d: T("Cambió el modelo pero el diagnóstico sigue", "The model changed but the diagnostic remains"), req: "ALTERNATIVE" },
        { n: T("Aplicada con errores", "Applied with errors"), d: T("Parte de las operaciones falló (mejor esfuerzo)", "Some operations failed (best effort)"), req: "ALTERNATIVE" },
        { n: T("Sin cambios", "No changes"), d: T("La corrección no modificó nada", "The fix changed nothing"), req: "ALTERNATIVE" },
        { n: T("Deshecha / rehecha", "Undone / redone"), d: T("Se revirtió o se repitió con revalidación", "Reverted or repeated with revalidation"), req: "ALTERNATIVE" }
      ],
      props: [{ n: T("Resumen de cambios", "Change summary"), req: "DERIVED", d: T("Qué elementos se tocaron", "Which elements were touched") }, { n: T("Historial de deshacer y rehacer", "Undo and redo history"), req: "DERIVED", d: T("Acotado; al aplicar una nueva corrección se vacía el de rehacer", "Bounded; applying a new fix clears redo") }],
      io: { i: T("Corrección elegida y argumentos", "Chosen fix and arguments"), o: T("Modelo actualizado y diagnósticos revalidados", "Updated model and revalidated diagnostics") },
      effect: T("Tras aplicar, se revalida de forma incremental y se publican eventos de corrección iniciada, aplicada, fallida, deshecha y rehecha. Si falla, se vuelve a la instantánea.", "After applying, it is revalidated incrementally and events are published for fix started, applied, failed, undone and redone. On failure it returns to the snapshot."),
      func: { rel: [T("Resuelve (o no) un diagnóstico", "Resolves (or not) a diagnostic")], lifecycle: T("vista previa → aplicar → revalidar → deshacer / rehacer", "preview → apply → revalidate → undo / redo"),
              cond: T("El diagnóstico debe estar en el último informe; si no, se lanza una validación completa antes.", "The diagnostic must be in the last report; otherwise a full validation runs first.") },
      tech: { type: "QuickFixApplicationStatus / CanonicalModelChangeSummary", schema: "APPLIED_RESOLVED, APPLIED_UNRESOLVED, APPLIED_WITH_ERRORS, NO_CHANGES, UNDONE, REDONE", evidence: [{ path: "src/main/java/com/example/editor/validation/quickfix/QuickFixRuntimeService.java", line: 94, end_line: 127, label: "preview / apply" }, { path: "src/main/java/com/example/editor/validation/quickfix/QuickFixRuntimeService.java", line: 236, end_line: 240, label: "statuses" }, { path: "src/main/java/com/example/editor/definition/runtime/session/EditorModelSessionService.java", line: 567, end_line: 585, label: "session apply (needs quick-fix capability)" }] } },

    { id: "text_actions", n: T("Correcciones de código de textos y gramáticas", "Text and grammar code fixes"), kind: "concept", req: "OPTIONAL",
      mean: T("Un sistema distinto de correcciones, para editores de texto: proponen cambios de texto (por ejemplo sustituir una referencia no definida o añadir un punto y coma).", "A different fix system, for text editors: they propose text changes (for example replacing an undefined reference or adding a semicolon)."),
      purpose: T("Arreglar errores de escritura en gramáticas y modelos textuales.", "Fix writing errors in grammars and textual models."),
      when: T("Al editar una gramática o un modelo en texto.", "When editing a grammar or a textual model."),
      card: "1 diagnóstico de texto ↔ 0..N correcciones de código",
      variants: [
        { n: T("Gramática: recursión por la izquierda", "Grammar: left recursion"), d: T("Sugiere reescribir la regla", "Suggests rewriting the rule"), req: "ALTERNATIVE" },
        { n: T("Gramática: sustituir referencia", "Grammar: replace reference"), d: T("Propone el nombre más parecido; es automática y segura solo si la similitud es muy alta", "Proposes the closest name; automatic and safe only if similarity is very high"), req: "ALTERNATIVE" },
        { n: T("Editor de gramática: correcciones locales", "Grammar editor: local fixes"), d: T("Sustituir referencia no definida (hasta 4 candidatas), sustituir un símbolo mal formado, añadir «;»", "Replace undefined reference (up to 4 candidates), replace malformed token, add \";\""), req: "ALTERNATIVE" },
        { n: T("Modelo textual: insertar o reemplazar", "Textual model: insert or replace"), d: T("Metadatos de la sintaxis que el editor aplica en el navegador", "Syntax metadata the editor applies in the browser"), req: "ALTERNATIVE" }
      ],
      props: [{ n: T("Ediciones de texto", "Text edits"), req: "MANDATORY", d: T("Documento, rango y texto de reemplazo", "Document, range and replacement text") }, { n: T("Preferida / segura automática", "Preferred / safe automatic"), req: "OPTIONAL", d: T("Marcas que indican si se puede aplicar sola", "Flags indicating whether it can be applied alone") }],
      io: { i: T("Diagnóstico de texto", "Text diagnostic"), o: T("Edición de texto", "Text edit") },
      effect: T("Se aplican en el navegador como ediciones de texto, sin pasar por el sistema de Quick Fix de los modelos. Existe además un servicio de Quick Fix textual en el servidor sin uso desde la interfaz y sin deshacer.", "Applied in the browser as text edits, without going through the model Quick Fix system. A server-side textual Quick Fix service also exists with no interface caller and no undo."),
      func: { rel: [T("Independiente de las reglas de validación y de las acciones de corrección", "Independent from validation rules and fix actions")], lifecycle: T("diagnóstico de texto → propuesta → edición aplicada", "text diagnostic → proposal → edit applied"), cond: T("No hay tests para la sustitución de referencias.", "No tests for reference replacement.") },
      tech: { type: "GrammarCodeAction / GrammarTextEdit / TextualQuickFixRuntimeService", schema: "id, title, kind (free string), preferred, safeAutomatic, edits, preconditions, metadata", evidence: [{ path: "src/main/java/com/example/editor/grammar/diagnostic/GrammarCodeAction.java", line: 7, end_line: 15, label: "GrammarCodeAction" }, { path: "frontend/src/grammar-language-service/grammarCodeActions.ts", line: 4, end_line: 12, label: "client-side application" }] } }
  ],

  map: {
    nodes: [
      { id: "m_ruleset", elem: "ruleset", n: T("Grupo de reglas", "Rule group"), k: "concept", req: "OPTIONAL", d: T("Agrupa reglas", "Groups rules") },
      { id: "m_rule", elem: "rule", n: T("Regla", "Rule"), k: "concept", req: "MANDATORY", d: T("La comprobación", "The check") },
      { id: "m_scope", elem: "scope", n: T("Ámbito", "Scope"), k: "variant", req: "MANDATORY", d: T("A qué se aplica", "What it applies to") },
      { id: "m_mode", elem: "execmode", n: T("Modo de ejecución", "Execution mode"), k: "variant", req: "MANDATORY", d: T("Cómo participa", "How it takes part") },
      { id: "m_trig", elem: "triggers", n: T("Momentos", "Triggers"), k: "variant", req: "CONDITIONAL", d: T("Cuándo se pide", "When it is requested") },
      { id: "m_cond", elem: "condition", n: T("Condición", "Condition"), k: "concept", req: "MANDATORY", d: T("Lo que debe cumplirse", "What must hold") },
      { id: "m_sev", elem: "severity", n: T("Gravedad", "Severity"), k: "variant", req: "MANDATORY", d: T("Error / aviso / información", "Error / warning / info") },
      { id: "m_repr", elem: "repr", n: T("Representación original", "Original representation"), k: "variant", req: "CONDITIONAL", d: T("Reglas importadas", "Imported rules") },
      { id: "m_diag", elem: "diagnostic", n: T("Diagnóstico", "Diagnostic"), k: "derived", req: "DERIVED", d: T("Resultado del incumplimiento", "Result of a violation") },
      { id: "m_gate", elem: "gate", n: T("Bloqueo", "Blocking"), k: "derived", req: "OPTIONAL", d: T("Permitir o impedir", "Allow or prevent") },
      { id: "m_qf", elem: "qf_action", n: T("Quick Fix", "Quick Fix"), k: "concept", req: "OPTIONAL", d: T("Acción de corrección", "Fix action") },
      { id: "m_ops", elem: "qf_ops", n: T("Operaciones", "Operations"), k: "variant", req: "REPEATABLE", d: T("10 clases de paso", "10 kinds of step") },
      { id: "m_pol", elem: "qf_policy", n: T("Política y precondición", "Policy and precondition"), k: "variant", req: "MANDATORY", d: T("Seguridad", "Safety") },
      { id: "m_res", elem: "qf_result", n: T("Resultado de la corrección", "Fix result"), k: "derived", req: "DERIVED", d: T("Cambios y revalidación", "Changes and revalidation") },
      { id: "m_metamodel", n: T("Metamodelo", "Metamodel"), k: "external", req: "MANDATORY", d: T("Clases, atributos, relaciones", "Classes, attributes, relations") },
      { id: "m_text", elem: "text_actions", n: T("Correcciones de texto", "Text fixes"), k: "external", req: "OPTIONAL", d: T("Otro sistema, independiente", "Another, independent system") }
    ],
    edges: [
      { a: "m_ruleset", b: "m_rule", rel: "contains", card1: "0..1", card2: "0..N", req: "OPTIONAL" },
      { a: "m_rule", b: "m_scope", rel: "contains", card1: "1", card2: "1", req: "MANDATORY" },
      { a: "m_rule", b: "m_mode", rel: "contains", card1: "1", card2: "1", req: "MANDATORY" },
      { a: "m_rule", b: "m_trig", rel: "contains", card1: "1", card2: "0..N", req: "CONDITIONAL" },
      { a: "m_rule", b: "m_cond", rel: "ref", card1: "1", card2: "1", req: "MANDATORY", l: T("exige", "requires") },
      { a: "m_rule", b: "m_sev", rel: "contains", card1: "1", card2: "1", req: "MANDATORY" },
      { a: "m_rule", b: "m_repr", rel: "contains", card1: "1", card2: "1", req: "CONDITIONAL" },
      { a: "m_scope", b: "m_metamodel", rel: "ref", card1: "1", card2: "0..N", req: "CONDITIONAL", l: T("apunta a", "points to") },
      { a: "m_rule", b: "m_diag", rel: "gen", card1: "1", card2: "0..N", req: "MANDATORY", l: T("emite", "emits") },
      { a: "m_diag", b: "m_gate", rel: "gen", card1: "N", card2: "1", req: "OPTIONAL", l: T("decide", "decides") },
      { a: "m_rule", b: "m_qf", rel: "ref", card1: "0..N", card2: "0..N", req: "OPTIONAL", l: T("ofrece", "offers") },
      { a: "m_qf", b: "m_ops", rel: "contains", card1: "1", card2: "1..N", req: "MANDATORY" },
      { a: "m_qf", b: "m_pol", rel: "contains", card1: "1", card2: "1", req: "MANDATORY" },
      { a: "m_qf", b: "m_res", rel: "gen", card1: "1", card2: "N", req: "OPTIONAL", l: T("al aplicarse", "when applied") },
      { a: "m_text", b: "m_qf", rel: "ref", card1: "0..N", card2: "0..N", req: "OPTIONAL", l: T("alternativa independiente", "independent alternative") },
      { a: "m_res", b: "m_diag", rel: "ref", card1: "1", card2: "1", req: "OPTIONAL", l: T("resuelve", "resolves") }
    ]
  },

  define: [
    { id: "guided", n: T("Asistente guiado de reglas", "Guided rule wizard"), kind: "wizard", req: "ALTERNATIVE", elems: ["rule", "scope", "execmode", "triggers", "condition", "severity", "message", "qf_action"],
      does: T("En Validación abres la pestaña Reglas y pulsas «+ Regla»: nace una regla con ámbito clase sobre la primera clase, gravedad Error, modo incremental, ejecución al guardar y a mano, y condición «verdadero». El asistente guiado (modo por defecto) tiene 6 pasos. 1 Identidad: nombre, código del problema (se fuerza a mayúsculas, números y guion bajo), gravedad, grupo y si está activa. 2 Contexto: modo de ejecución, ámbito, clase y atributo o relación. 3 Diagnóstico y momentos: el mensaje y las casillas «¿Cuándo debe ejecutarse?». 4 Condición: cláusulas guiadas (comprobar un atributo, contar relaciones o contar elementos de una clase) combinadas con Y / O / NO. 5 Quick Fixes: casillas con las correcciones existentes. 6 Revisión: resumen y «Guardar validación». Puedes buscar y filtrar reglas por gravedad y grupo.",
              "In Validation you open the Rules tab and press \"+ Rule\": a rule is born with class scope over the first class, severity Error, incremental mode, run on save and manually, and condition \"true\". The guided wizard (default mode) has 6 steps. 1 Identity: name, problem code (forced to uppercase, digits and underscore), severity, group and whether it is active. 2 Context: execution mode, scope, class and attribute or relation. 3 Diagnostic & triggers: the message and the \"When should it run?\" checkboxes. 4 Condition: guided clauses (check an attribute, count relations or count class elements) combined with AND / OR / NOT. 5 Quick Fixes: checkboxes of existing fixes. 6 Review: summary and \"Save validation\". You can search and filter rules by severity and group."),
      func: T("Guardar se bloquea si hay problemas de diseño. Se guarda en orden: expresiones, acciones, comportamientos (solo si el metamodelo tiene operaciones), validaciones. Editar una regla la deja sin verificar.", "Saving is blocked if there are design problems. Saved in order: expressions, actions, behaviours (only if the metamodel has operations), validations. Editing a rule un-verifies it."),
      tech: T("ValidationRuleManager.tsx, ValidationRuleEditor.tsx (6 pasos), GuidedConditionBuilder.tsx, validationDesignerFactory.ts, ValidationDesigner.tsx. Sin metamodelo guardado el diseñador queda bloqueado.", "ValidationRuleManager.tsx, ValidationRuleEditor.tsx (6 steps), GuidedConditionBuilder.tsx, validationDesignerFactory.ts, ValidationDesigner.tsx. Without a saved metamodel the designer is blocked."), proc: "validation_author_guided" },
    { id: "form", n: T("Formulario completo de reglas", "Full rule form"), kind: "form", req: "ALTERNATIVE", elems: ["rule", "condition", "repr", "triggers"],
      does: T("Cambias al modo avanzado del editor: todas las secciones aparecen en una sola página. La condición se edita con el árbol de expresión completo (literales, atributos, relaciones, operadores, condicionales, funciones…). Aparece una sección avanzada para la representación original y el estado de conversión. Los diez momentos de disparo están disponibles, aunque cinco no tienen efecto hoy. Guardas con el mismo botón.",
              "You switch the editor to advanced mode: all sections appear on one page. The condition is edited with the full expression tree (literals, attributes, relations, operators, conditionals, functions...). An advanced section appears for the original representation and conversion status. All ten triggers are available, although five have no effect today. You save with the same button."),
      func: T("Es el único lugar donde se tocan la representación y la conversión.", "It is the only place where representation and conversion are touched."),
      tech: T("ValidationRuleEditor.tsx (modo ADVANCED), ExpressionBuilder.tsx.", "ValidationRuleEditor.tsx (ADVANCED mode), ExpressionBuilder.tsx."), proc: "validation_author_form" },
    { id: "check", n: T("Comprobar definiciones", "Check definitions"), kind: "flow", req: "OPTIONAL", elems: ["rule", "diagnostic"],
      does: T("Con todo guardado, pulsas «Comprobar definiciones»: HYDRA revisa los cuatro documentos (expresiones, acciones, comportamientos y validaciones) y te lleva a la pestaña Diagnósticos, que lista problemas de seis fuentes con código, ruta y elemento; al pulsar uno se selecciona el elemento. Las reglas sin errores quedan marcadas como verificadas.",
              "With everything saved, press \"Check definitions\": HYDRA reviews the four documents (expressions, actions, behaviours and validations) and takes you to the Diagnostics tab, which lists problems from six sources with code, path and element; clicking one selects the element. Rules without errors are marked verified."),
      func: T("No ejecuta el motor sobre un modelo: solo comprueba la definición.", "It does not run the engine over a model: it only checks the definition."),
      tech: T("ValidationDesigner.tsx (:359-389), validationVerification.ts.", "ValidationDesigner.tsx (:359-389), validationVerification.ts."), proc: null },
    { id: "qf_wizard", n: T("Editor de Quick Fixes", "Quick Fix editor"), kind: "wizard", req: "ALTERNATIVE", elems: ["qf_action", "qf_ops", "qf_params", "qf_selector", "qf_policy"],
      does: T("En la pestaña Quick fixes pulsas «+ Quick fix». El asistente tiene 5 pasos. 1 Identidad: nombre, descripción, activa. 2 Seguridad: transacción (atómica o mejor esfuerzo), política de deshacer y precondición opcional. 3 Parámetros: «+ Parámetro» crea uno de tipo texto obligatorio que renombras y cambias de tipo. 4 Operaciones: añades operaciones de las 10 clases; en cada una eliges el selector de destino, el atributo/relación/clase, el origen del valor (fijo, calculado o pedido) y, en ejecutar acción o invocar operación, los argumentos. 5 Revisión. Después, en el editor de reglas, marcas la casilla de esta Quick Fix en la regla que debe ofrecerla.",
              "In the Quick fixes tab you press \"+ Quick fix\". The wizard has 5 steps. 1 Identity: name, description, active. 2 Safety: transaction (atomic or best effort), undo policy and optional precondition. 3 Parameters: \"+ Parameter\" creates a required text one that you rename and retype. 4 Operations: you add operations of the 10 kinds; in each you choose the target selector, the attribute/relation/class, the value source (fixed, computed or asked) and, for run-action or invoke-operation, the arguments. 5 Review. Then, in the rule editor, you tick this Quick Fix on the rule that should offer it."),
      func: T("Una acción nueva nace activa, atómica, con deshacer obligatorio y sin operaciones; no se puede guardar sin al menos una operación (salvo importada solo para revisión).", "A new action starts enabled, atomic, with undo required and no operations; it cannot be saved without at least one operation (except an imported review-only one)."),
      tech: T("QuickFixDesigner.tsx (OperationEditor), validationDesignerFactory.ts, validationDesignerValidation.ts.", "QuickFixDesigner.tsx (OperationEditor), validationDesignerFactory.ts, validationDesignerValidation.ts."), proc: "validation_author_quickfix" },
    { id: "import", n: T("Importar reglas y correcciones", "Import rules and fixes"), kind: "import", req: "OPTIONAL", elems: ["rule", "repr", "qf_action"],
      does: T("Desde el diseñador importas reglas en OCL, EVL, ATL o formato HYDRA, y Quick Fixes en EVL, EOL o formato HYDRA (también puedes exportar). Las reglas importadas pueden quedar parciales o externas: se conservan para revisión pero no se ejecutan. HYDRA enlaza las Quick Fixes importadas con las reglas por nombre o código y te avisa de los enlaces que no pudo resolver.",
              "From the designer you import rules in OCL, EVL, ATL or HYDRA format, and Quick Fixes in EVL, EOL or HYDRA format (you can also export). Imported rules may stay partial or external: they are kept for review but not executed. HYDRA links imported Quick Fixes to rules by name or code and warns you of the links it could not resolve."),
      func: T("Solo un subconjunto sencillo y demostrablemente equivalente se convierte a regla ejecutable.", "Only a simple, provably equivalent subset is converted to an executable rule."),
      tech: T("ValidationDesigner.tsx (:415-497), quickFixInterchange.ts, SafeExternalConstraintTranslator. No existe editor textual de reglas.", "ValidationDesigner.tsx (:415-497), quickFixInterchange.ts, SafeExternalConstraintTranslator. There is no textual rule editor."), proc: "validation_author_import" },
    { id: "runtime_ui", n: T("Panel de problemas del editor", "Editor problems panel"), kind: "direct", req: "OPTIONAL", elems: ["diagnostic", "qf_result"],
      does: T("En el editor ves la lista de problemas. Al seleccionar uno aparecen las Quick Fixes disponibles; si piden datos, los rellenas. Pulsas «Vista previa» para ver los cambios, «Aplicar» para ejecutarlos, y «Deshacer» o «Rehacer» para revertir.",
              "In the editor you see the problem list. Selecting one shows the available Quick Fixes; if they need data you fill it in. You press \"Preview\" to see the changes, \"Apply\" to run them, and \"Undo\" or \"Redo\" to revert."),
      func: T("No se invocan desde el espacio de ejecución del editor de modelos, sino desde este panel y la sesión de definición.", "Not invoked from the model editor's runtime workspace, but from this panel and the definition session."),
      tech: T("ProblemsPanel.tsx, problemsApi.ts, editor-definition/api.ts.", "ProblemsPanel.tsx, problemsApi.ts, editor-definition/api.ts."), proc: "validation_apply_quickfix" }
  ],

  rel: [
    { to: "metamodel", type: "required", dir: "in", u: T("Las reglas y correcciones se escriben sobre las clases, atributos, relaciones y operaciones del metamodelo; sin metamodelo guardado no se puede diseñar.", "Rules and fixes are written over the metamodel's classes, attributes, relations and operations; without a saved metamodel you cannot design."), f: T("Cada referencia se comprueba contra el metamodelo (clase conocida, atributo de esa clase…).", "Each reference is checked against the metamodel (known class, attribute of that class...)."), t: T("ValidationModel.metamodelId; ValidationModelChecker contra el metamodelo.", "ValidationModel.metamodelId; ValidationModelChecker against the metamodel.") },
    { to: "model_editor", type: "producer", dir: "out", u: T("El editor muestra los problemas y deja aplicar correcciones; valida al abrir y al cambiar.", "The editor shows problems and lets you apply fixes; it validates on open and on change."), f: T("Abrir → validación al abrir; cambios → incremental; petición manual → completa; tras una corrección → incremental.", "Open → on-open validation; changes → incremental; manual request → full; after a fix → incremental."), t: T("EditorModelSessionService llama a ValidationRuntimeApplicationService.validate (no al orquestador); Quick Fix de sesión exige la capacidad QUICK_FIX_RUNTIME.", "EditorModelSessionService calls ValidationRuntimeApplicationService.validate (not the orchestrator); session Quick Fix requires the QUICK_FIX_RUNTIME capability.") },
    { to: "events", type: "event", dir: "out", u: T("Cada validación y cada corrección publica eventos que otras capacidades pueden usar.", "Every validation and fix publishes events other capabilities can use."), f: T("Ejecución terminada, violación creada, violación resuelta; corrección iniciada, aplicada, fallida, deshecha y rehecha.", "Run completed, violation created, violation resolved; fix started, applied, failed, undone and redone."), t: T("core.validation.execution-completed / violation-created / violation-resolved; core.quickfix.started / applied / failed / undone / redone.", "core.validation.execution-completed / violation-created / violation-resolved; core.quickfix.started / applied / failed / undone / redone.") },
    { to: "gamification", type: "event", dir: "out", u: T("Los eventos de validación pueden alimentar logros (por ejemplo, resolver problemas).", "Validation events can feed achievements (for example, resolving problems)."), f: T("Consume los eventos de violación resuelta y de corrección aplicada.", "Consumes the violation-resolved and fix-applied events."), t: T("Consumo vía bus de eventos core.*; el acoplamiento concreto no se verificó en esta capacidad.", "Consumption via the core.* event bus; the concrete coupling was not verified for this capability.") },
    { to: "grammar", type: "alternative", dir: "out", u: T("Los editores de texto tienen sus propias correcciones, que no pasan por este sistema.", "Text editors have their own fixes, which do not go through this system."), f: T("Subsistema independiente sin puente con las acciones de corrección de modelos.", "Independent subsystem with no bridge to the model fix actions."), t: T("El paquete grammar no importa validation.action ni validation.quickfix; GrammarCodeAction aplicada en el navegador.", "The grammar package does not import validation.action or validation.quickfix; GrammarCodeAction applied in the browser.") },
    { to: "recovery", type: "optional", dir: "in", u: T("La recuperación de proyectos externos puede traer reglas de validación para revisarlas.", "Recovery of external projects can bring validation rules for review."), f: T("Reglas externas se conservan con estado de conversión.", "External rules are preserved with a conversion status."), t: T("ExternalValidationRecoveryRuntimeTests; ImportedValidationSource.", "ExternalValidationRecoveryRuntimeTests; ImportedValidationSource.") },
    { to: "transformation", type: "alternative", dir: "out", u: T("Las transformaciones tienen sus propias correcciones, aparte de estas.", "Transformations have their own fixes, separate from these."), f: T("Subsistema separado (fuera del alcance de esta capacidad).", "Separate subsystem (outside this capability's scope)."), t: T("Paquete transformation/quickfix.", "transformation/quickfix package.") }
  ]
});

/* ------------------------------------------------------------------ CAPABILITY PROCESSES */
HM.proc({ id: "validation_define_rule", scope: "cap", cap: "validation", elems: ["rule", "scope", "condition", "severity", "triggers"],
  n: T("Definir una regla de validación", "Define a validation rule"), d: T("Desde la idea hasta una regla guardada, comprobada y lista para ejecutarse.", "From the idea to a saved, checked rule ready to run."),
  nodes: [
    { id: "s", t: "start", cap: "validation", l: T("Necesito una exigencia de calidad", "I need a quality requirement"), u: T("Parte de querer comprobar algo sobre los modelos.", "Starts from wanting to check something about the models.") },
    { id: "meta", t: "step", a: "user", r: "MANDATORY", cap: "metamodel", l: T("Tener el metamodelo guardado", "Have the metamodel saved"), u: T("Las reglas se escriben sobre sus clases, atributos y relaciones; sin él el diseñador está bloqueado.", "Rules are written over its classes, attributes and relations; without it the designer is blocked."), need: T("Metamodelo", "Metamodel"), out: T("Tipos disponibles", "Available types") },
    { id: "id", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("Dar identidad: nombre, código, gravedad", "Give identity: name, code, severity"), u: T("Define cómo se llama la regla y lo grave que es incumplirla.", "Defines what the rule is called and how serious it is to break it."), need: T("Nombre, código, gravedad", "Name, code, severity"), out: T("Regla con identidad", "Rule with identity"), e: "rule", f: T("El código se fuerza a mayúsculas, números y guion bajo.", "The code is forced to uppercase, digits and underscore."), x: { rt: "ValidationRuleEditor", test: ["ValidationRuleEditor.help.test.tsx"], ev: [{ path: FD + "ValidationRuleEditor.tsx", line: 92, label: "code forced to [A-Z0-9_]" }] } },
    { id: "ctx", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("Elegir ámbito y modo", "Choose scope and mode"), u: T("Decide a qué se aplica la regla y si se reevalúa de forma continua o solo a petición.", "Decides what the rule applies to and whether it re-runs continuously or only on request."), need: T("Ámbito, clase/atributo/relación, modo", "Scope, class/attribute/relation, mode"), out: T("Contexto de la regla", "Rule context"), e: "scope", f: T("Clase y objeto exigen clase; atributo exige clase y atributo suyo; relación exige relación de la clase.", "Class and object require a class; attribute requires a class and its attribute; relation requires a relation of the class."), x: { rt: "ValidationModelChecker", test: ["ValidationModelCheckerTests"], ev: [{ path: V + "ValidationModelChecker.java", line: 122, label: "scope checks" }] } },
    { id: "msg", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("Escribir el mensaje", "Write the message"), u: T("El texto que verá quien incumpla la regla; puede incluir datos del elemento.", "The text seen by whoever breaks the rule; it can include element data."), need: T("Texto no vacío", "Non-empty text"), out: T("Mensaje", "Message"), e: "message" },
    { id: "trg", t: "step", a: "user", r: "CONDITIONAL", cap: "validation", l: T("Elegir los momentos de disparo", "Choose triggers"), u: T("Marca cuándo debe comprobarse: al abrir, al guardar, antes de exportar, a mano…", "Mark when it should be checked: on open, on save, before export, manually..."), need: T("Al menos un momento (salvo regla externa)", "At least one trigger (unless external)"), out: T("Momentos", "Triggers"), e: "triggers", f: T("Solo manual admite el disparo manual; solo completa admite completa, guardar y exportar.", "Manual only admits the manual trigger; full only admits full, save and export.") },
    { id: "cnd", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("Escribir la condición", "Write the condition"), u: T("Dice qué debe cumplirse; debe dar verdadero o falso.", "States what must hold; it must give true or false."), need: T("Cláusulas o expresión", "Clauses or expression"), out: T("Condición", "Condition"), e: "condition" },
    { id: "qf", t: "step", a: "user", r: "OPTIONAL", cap: "validation", l: T("Asociar Quick Fixes", "Link Quick Fixes"), u: T("Elige qué correcciones se ofrecerán cuando la regla falle.", "Choose which fixes are offered when the rule fails."), need: T("Quick Fixes existentes", "Existing Quick Fixes"), out: T("Correcciones ofrecidas", "Fixes offered"), e: "qf_action", f: T("Sin duplicados y deben existir en el conjunto de acciones.", "No duplicates and they must exist in the action set.") },
    { id: "blk", t: "decision", a: "auto", r: "MANDATORY", cap: "validation", l: T("¿Hay problemas de diseño?", "Design problems?"), u: T("HYDRA revisa la regla antes de guardar y bloquea si hay errores.", "HYDRA reviews the rule before saving and blocks if there are errors."), f: T("El bloqueo ocurre solo en la interfaz; el servidor no rechaza por errores.", "Blocking happens only in the interface; the server does not reject on errors."), x: { rt: "validationDesignerValidation", test: ["validationDesignerValidation.test.ts"], ev: [{ path: FD + "ValidationDesigner.tsx", line: 263, end_line: 266, label: "blocking error" }] } },
    { id: "sv", t: "step", a: "auto", r: "MANDATORY", cap: "validation", l: T("Guardar documentos en orden", "Save documents in order"), u: T("Se guardan expresiones, acciones, comportamientos y validaciones; cada etapa con su control de versión.", "Expressions, actions, behaviours and validations are saved; each stage with its own version control."), need: T("Diseño sin errores", "Design without errors"), out: T("Documentos guardados", "Saved documents"), x: { rt: "ValidationDesigner", test: ["ValidationDesigner.test.tsx"], ev: [{ path: FD + "ValidationDesigner.tsx", line: 278, end_line: 338, label: "ordered save with ETag per stage" }] } },
    { id: "chk", t: "step", a: "user", r: "OPTIONAL", cap: "validation", l: T("Comprobar definiciones", "Check definitions"), u: T("Revisa los documentos y marca como verificadas las reglas sin errores.", "Reviews the documents and marks error-free rules as verified."), need: T("Todo guardado", "Everything saved"), out: T("Diagnósticos de diseño y reglas verificadas", "Design diagnostics and verified rules"), s: T("regla: guardada → verificada", "rule: saved → verified") },
    { id: "ok", t: "artifact", cap: "validation", l: T("Regla activa y verificada", "Active, verified rule"), u: T("La regla queda lista para ejecutarse en las validaciones.", "The rule is ready to run in validations.") },
    { id: "e", t: "end", cap: "validation", l: T("Fin", "End") }
  ],
  edges: [["s", "meta"], ["meta", "id"], ["id", "ctx"], ["ctx", "msg"], ["msg", "trg", "cond"], ["msg", "cnd"], ["trg", "cnd"], ["cnd", "qf", "opt"], ["cnd", "blk"], ["qf", "blk"],
    ["blk", "id", "loop", T("Sí: corregir", "Yes: fix")], ["blk", "sv", "next", T("No", "No")], ["sv", "chk", "opt"], ["sv", "ok"], ["chk", "ok"], ["ok", "e"]] });

HM.proc({ id: "validation_execute", scope: "cap", cap: "validation", elems: ["rule", "execmode", "triggers", "diagnostic", "gate", "severity"],
  n: T("Ejecutar una validación", "Run a validation"), d: T("Desde la petición (abrir, cambiar, guardar, exportar o manual) hasta el informe y la decisión de bloqueo.", "From the request (open, change, save, export or manual) to the report and blocking decision."),
  nodes: [
    { id: "s", t: "start", cap: "model_editor", l: T("Se pide validar", "Validation requested"), u: T("Abrir el modelo, cambiarlo, cambiar de vista, pedir validar a mano o guardar/exportar.", "Opening the model, changing it, switching view, requesting validation manually or saving/exporting.") },
    { id: "mode", t: "decision", a: "auto", cap: "validation", l: T("¿Qué modo?", "Which mode?"), u: T("El modo decide qué reglas se ejecutan: completa, incremental, por momento, selección o clase.", "The mode decides which rules run: full, incremental, by moment, selection or class."), f: T("Completa: todas salvo solo manuales. Incremental: las incrementales afectadas por los cambios. Por momento: las que lo declaran. Selección/clase: todas salvo solo completas y solo manuales. En completa e incremental los momentos se ignoran.", "Full: all except manual-only. Incremental: incremental rules affected by changes. By moment: those declaring it. Selection/class: all except full-only and manual-only. In full and incremental triggers are ignored."), e: "execmode", x: { rt: "ValidationEngine", test: ["ValidationEngineTests"], ev: [{ path: V + "runtime/ValidationEngine.java", line: 200, end_line: 213, label: "mode-based selection" }] } },
    { id: "comp", t: "step", a: "auto", r: "MANDATORY", cap: "validation", l: T("Compilar el plan", "Compile the plan"), u: T("Las reglas se convierten en un plan ejecutable; se omiten las desactivadas, las no canónicas y las que tienen errores de diseño.", "Rules are turned into an executable plan; disabled, non-canonical and design-broken rules are left out."), need: T("Documento de validación", "Validation document"), out: T("Plan y avisos de omisión", "Plan and skip notices"), f: T("Con errores y sin permitir plan parcial, la validación termina como «no ejecutable».", "With errors and partial plans not allowed, validation ends as \"not executable\"."), x: { rt: "ValidationCompiler", test: ["ValidationCompilerTests"], ev: [{ path: V + "runtime/ValidationCompiler.java", line: 42, end_line: 90, label: "compile" }, { path: V + "runtime/ValidationRuntimeApplicationService.java", line: 64, end_line: 67, label: "NOT_EXECUTABLE" }] } },
    { id: "exec", t: "decision", a: "auto", cap: "validation", l: T("¿Plan ejecutable?", "Executable plan?"), u: T("Si el plan no se puede ejecutar, se informa y no se valida.", "If the plan cannot run, it is reported and nothing is validated.") },
    { id: "cand", t: "step", a: "auto", r: "MANDATORY", cap: "validation", l: T("Elegir candidatos por ámbito", "Pick candidates by scope"), u: T("Modelo, proyecto y representación se evalúan una vez; los demás recorren los elementos candidatos (la clase incluye sus subclases; selección usa lo seleccionado).", "Model, project and representation are evaluated once; others go through candidate elements (class includes subclasses; selection uses what is selected)."), e: "scope" },
    { id: "eval", t: "step", a: "auto", r: "MANDATORY", cap: "validation", l: T("Evaluar la condición", "Evaluate the condition"), u: T("Cada regla evalúa su condición; falso significa incumplimiento.", "Each rule evaluates its condition; false means violation."), e: "condition", f: T("Si no devuelve verdadero/falso se emite un error de ejecución de la regla.", "If it does not return true/false a rule execution error is emitted.") },
    { id: "diag", t: "step", a: "auto", r: "MANDATORY", cap: "validation", l: T("Emitir diagnósticos", "Emit diagnostics"), u: T("Por cada incumplimiento: gravedad y mensaje de la regla, elemento afectado y correcciones ofrecidas, con identificador estable.", "For each violation: the rule's severity and message, the affected element and fixes offered, with a stable identity."), e: "diagnostic", s: T("diagnóstico: (nuevo) → abierto", "diagnostic: (new) → open") },
    { id: "cons", t: "step", a: "auto", r: "OPTIONAL", cap: "validation", l: T("Consolidar con el informe anterior", "Consolidate with the previous report"), u: T("Se calcula qué es nuevo, qué sigue y qué se resolvió; las validaciones completas y por momento reemplazan el informe, las incrementales lo fusionan.", "What is new, unchanged and resolved is computed; full and moment-based runs replace the report, incremental ones merge."), f: T("Solo si la validación pasa por el orquestador; la sesión del editor llama directamente al motor y no consolida.", "Only if validation goes through the orchestrator; the editor session calls the engine directly and does not consolidate."), x: { rt: "ValidationOrchestrator", test: ["ValidationOrchestratorTests"], ev: [{ path: V + "orchestration/ValidationOrchestrator.java", line: 89, end_line: 141, label: "consolidation and delta" }] } },
    { id: "ev", t: "step", a: "auto", r: "OPTIONAL", cap: "events", l: T("Publicar eventos", "Publish events"), u: T("Se avisa de ejecución completada, violaciones creadas y resueltas.", "Completed run, created and resolved violations are announced."), need: T("Informe consolidado", "Consolidated report"), out: T("Eventos de validación", "Validation events"), x: { rt: "ValidationOrchestrator", test: ["ValidationOrchestratorTests"], ev: [{ path: V + "orchestration/ValidationOrchestrator.java", line: 163, end_line: 168, label: "core.validation.* events" }] } },
    { id: "blk", t: "decision", a: "auto", r: "OPTIONAL", cap: "validation", l: T("¿Hay errores que bloqueen?", "Blocking errors?"), u: T("Con una puerta de guardar o exportar, solo los diagnósticos de gravedad Error bloquean.", "With a save or export gate, only Error-severity diagnostics block."), e: "gate", f: T("Sin puerta (ninguna), nunca bloquea. Guardar y exportar bloquean por defecto.", "With no gate, never blocks. Save and export block by default."), x: { rt: "DefaultValidationBlockingPolicy", test: ["DefaultValidationBlockingPolicyTests"], ev: [{ path: V + "orchestration/DefaultValidationBlockingPolicy.java", line: 29, end_line: 36, label: "policy" }] } },
    { id: "rep", t: "artifact", cap: "validation", l: T("Informe de validación", "Validation report"), u: T("Diagnósticos, reglas ejecutadas y omitidas, métricas y decisión de bloqueo.", "Diagnostics, rules run and skipped, metrics and blocking decision.") },
    { id: "no", t: "end", cap: "validation", l: T("Operación bloqueada", "Operation blocked") },
    { id: "ok", t: "end", cap: "validation", l: T("Operación permitida / informe mostrado", "Operation allowed / report shown") },
    { id: "ne", t: "end", cap: "validation", l: T("No ejecutable: se informa", "Not executable: reported") }
  ],
  edges: [["s", "mode"], ["mode", "comp", "next", T("Completa o incremental", "Full or incremental")], ["mode", "comp", "alt", T("Por momento, selección o clase", "By moment, selection or class")], ["comp", "exec"], ["exec", "ne", "alt", T("No", "No")], ["exec", "cand", "next", T("Sí", "Yes")], ["cand", "eval"], ["eval", "diag"], ["diag", "cons", "opt"], ["diag", "rep"], ["cons", "rep"], ["rep", "ev", "event"], ["rep", "blk", "opt"], ["blk", "no", "alt", T("Sí", "Yes")], ["blk", "ok", "next", T("No", "No")], ["ev", "ok", "event"], ["rep", "ok"]] });

HM.proc({ id: "validation_apply_quickfix", scope: "cap", cap: "validation", elems: ["qf_action", "qf_result", "qf_policy", "qf_params", "qf_ops", "diagnostic"],
  n: T("Aplicar una Quick Fix", "Apply a Quick Fix"), d: T("De un diagnóstico a un modelo corregido, con vista previa, revalidación y posibilidad de deshacer y rehacer.", "From a diagnostic to a fixed model, with preview, revalidation and undo and redo."),
  nodes: [
    { id: "s", t: "start", cap: "validation", l: T("Selecciono un diagnóstico", "I select a diagnostic"), u: T("En el panel de problemas elijo el problema que quiero arreglar.", "In the problems panel I pick the problem I want to fix.") },
    { id: "list", t: "step", a: "auto", r: "MANDATORY", cap: "validation", l: T("Listar correcciones aplicables", "List applicable fixes"), u: T("Solo se ofrecen las que la regla lista, existen, están activas y cumplen su precondición.", "Only those the rule lists, that exist, are enabled and meet their precondition are offered."), need: T("Diagnóstico en el último informe", "Diagnostic in the last report"), out: T("Lista de Quick Fixes", "List of Quick Fixes"), e: "qf_action", f: T("Si el diagnóstico no está en el último informe, se lanza antes una validación completa.", "If the diagnostic is not in the last report, a full validation is run first."), x: { rt: "QuickFixRuntimeService", test: ["QuickFixContractsTests"], ev: [{ path: "src/main/java/com/example/editor/validation/quickfix/QuickFixRuntimeService.java", line: 181, end_line: 214, label: "applicability" }] } },
    { id: "pick", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("Elegir la corrección", "Choose the fix"), u: T("Selecciono una de las correcciones disponibles.", "I select one of the available fixes."), out: T("Quick Fix elegida", "Chosen Quick Fix") },
    { id: "par", t: "step", a: "user", r: "CONDITIONAL", cap: "validation", l: T("Rellenar parámetros", "Fill in parameters"), u: T("Si la corrección pide datos, los escribo; los fijos y calculados no se piden.", "If the fix asks for data, I type it; fixed and computed ones are not asked."), e: "qf_params", f: T("Parámetros desconocidos o faltantes se rechazan.", "Unknown or missing parameters are rejected.") },
    { id: "pv", t: "step", a: "user", r: "OPTIONAL", cap: "validation", l: T("Vista previa", "Preview"), u: T("Veo qué cambiaría sin tocar el modelo ni revalidar.", "I see what would change without touching the model or revalidating."), out: T("Resumen de cambios", "Change summary"), x: { rt: "QuickFixRuntimeService", test: [], ev: [{ path: "src/main/java/com/example/editor/validation/quickfix/QuickFixRuntimeService.java", line: 94, end_line: 99, label: "preview on a copy" }] } },
    { id: "ok", t: "decision", a: "user", r: "MANDATORY", cap: "validation", l: T("¿Aplicar?", "Apply?"), u: T("Decido si el cambio es el que quiero.", "I decide whether the change is what I want.") },
    { id: "ap", t: "step", a: "auto", r: "MANDATORY", cap: "validation", l: T("Ejecutar las operaciones", "Run the operations"), u: T("Se guarda una instantánea y las operaciones se ejecutan sobre el modelo vivo, en orden.", "A snapshot is taken and the operations run on the live model, in order."), e: "qf_ops", s: T("modelo: instantánea previa → modificado", "model: previous snapshot → modified"), f: T("Atómica: cualquier error revierte todo. Mejor esfuerzo: acumula mensajes. Precondición debe dar verdadero.", "Atomic: any error rolls everything back. Best effort: accumulates messages. Precondition must give true."), x: { rt: "CanonicalActionExecutor", test: ["OperationActionExecutionTests"], ev: [{ path: V + "action/CanonicalActionExecutor.java", line: 57, end_line: 177, label: "execution" }] } },
    { id: "fail", t: "decision", a: "auto", r: "MANDATORY", cap: "validation", l: T("¿Hubo fallo?", "Did it fail?"), u: T("Si falló, se vuelve a la instantánea y se informa.", "If it failed, it goes back to the snapshot and reports.") },
    { id: "rb", t: "step", a: "auto", r: "CONDITIONAL", cap: "validation", l: T("Revertir y avisar", "Roll back and notify"), u: T("El modelo queda como antes y se publica un evento de fallo.", "The model stays as before and a failure event is published."), x: { rt: "QuickFixRuntimeService", test: [], ev: [{ path: "src/main/java/com/example/editor/validation/quickfix/QuickFixRuntimeService.java", line: 122, end_line: 123, label: "rollback + core.quickfix.failed" }] } },
    { id: "rv", t: "step", a: "auto", r: "MANDATORY", cap: "validation", l: T("Revalidar de forma incremental", "Revalidate incrementally"), u: T("Se comprueban de nuevo solo las reglas afectadas por el cambio.", "Only the rules affected by the change are checked again."), e: "diagnostic", s: T("diagnóstico: abierto → resuelto (si desaparece)", "diagnostic: open → resolved (if it disappears)") },
    { id: "st", t: "step", a: "auto", r: "MANDATORY", cap: "validation", l: T("Calcular el estado", "Compute the status"), u: T("Aplicada y resuelta, sin resolver, con errores o sin cambios.", "Applied and resolved, unresolved, with errors or no changes."), e: "qf_result" },
    { id: "hist", t: "step", a: "auto", r: "OPTIONAL", cap: "validation", l: T("Guardar en el historial de deshacer", "Save in the undo history"), u: T("Si hubo cambios y la política lo permite, se guarda para deshacer y se vacía el historial de rehacer.", "If there were changes and the policy allows, it is kept for undo and the redo history is cleared."), f: T("Disponible si hubo cambios y la política no es «sin deshacer».", "Available if there were changes and the policy is not \"no undo\".") },
    { id: "evq", t: "step", a: "auto", r: "OPTIONAL", cap: "events", l: T("Publicar eventos de corrección", "Publish fix events"), u: T("Se avisa de corrección iniciada y aplicada.", "Fix started and applied are announced.") },
    { id: "undo", t: "step", a: "user", r: "OPTIONAL", cap: "validation", l: T("Deshacer / rehacer", "Undo / redo"), u: T("Puedo revertir la corrección o repetirla; cada vez se revalida.", "I can revert the fix or repeat it; it revalidates each time."), s: T("corrección: aplicada → deshecha → rehecha", "fix: applied → undone → redone") },
    { id: "res", t: "artifact", cap: "validation", l: T("Modelo corregido y diagnósticos actualizados", "Fixed model and updated diagnostics"), u: T("El resultado visible de la corrección.", "The visible result of the fix.") },
    { id: "e", t: "end", cap: "validation", l: T("Fin", "End") }
  ],
  edges: [["s", "list"], ["list", "pick"], ["pick", "par", "cond"], ["pick", "pv", "opt"], ["par", "pv", "opt"], ["par", "ok"], ["pv", "ok"], ["pick", "ok"], ["ok", "e", "alt", T("No", "No")], ["ok", "ap", "next", T("Sí", "Yes")], ["ap", "fail"],
    ["fail", "rb", "cond", T("Sí", "Yes")], ["rb", "e"], ["fail", "rv", "next", T("No", "No")], ["rv", "st"], ["st", "hist", "opt"], ["st", "evq", "event"], ["hist", "res"], ["st", "res"], ["evq", "res", "event"], ["res", "undo", "opt"], ["undo", "rv", "loop", T("Revalidar", "Revalidate")], ["res", "e"]] });

HM.proc({ id: "validation_define_quickfix", scope: "cap", cap: "validation", elems: ["qf_action", "qf_ops", "qf_params", "qf_policy", "qf_selector"],
  n: T("Definir una Quick Fix", "Define a Quick Fix"), d: T("De una idea de arreglo a una acción reutilizable asociada a una regla.", "From a fix idea to a reusable action linked to a rule."),
  nodes: [
    { id: "s", t: "start", cap: "validation", l: T("Hay un arreglo evidente", "There is an obvious fix"), u: T("Una regla tiene una corrección clara y segura.", "A rule has a clear, safe correction.") },
    { id: "id", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("Dar nombre y descripción", "Name and describe"), u: T("Identifica la corrección.", "Identifies the fix."), need: T("Nombre", "Name"), e: "qf_action" },
    { id: "pol", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("Elegir seguridad", "Choose safety"), u: T("Si falla se revierte todo o se aplica lo posible, y si se puede deshacer.", "If it fails everything is rolled back or what is possible is applied, and whether it can be undone."), e: "qf_policy", f: T("Por defecto atómica y deshacer obligatorio.", "Default: atomic and undo required.") },
    { id: "pre", t: "step", a: "user", r: "OPTIONAL", cap: "validation", l: T("Añadir precondición", "Add precondition"), u: T("Solo se ofrece la corrección si se cumple.", "The fix is only offered if it holds."), f: T("Debe existir y devolver verdadero/falso.", "Must exist and return true/false.") },
    { id: "par", t: "step", a: "user", r: "OPTIONAL", cap: "validation", l: T("Declarar parámetros", "Declare parameters"), u: T("Datos que se pedirán al aplicar.", "Data to be asked when applying."), e: "qf_params" },
    { id: "op", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("Añadir operaciones", "Add operations"), u: T("Elige entre 10 clases de paso, con su selector de destino y origen del valor.", "Choose among 10 kinds of step, with their target selector and value source."), e: "qf_ops", f: T("Al menos una (salvo importada solo para revisión).", "At least one (except imported review-only).") },
    { id: "more", t: "decision", a: "user", r: "MANDATORY", cap: "validation", l: T("¿Más operaciones?", "More operations?"), u: T("Se pueden encadenar tantas como hagan falta.", "As many as needed can be chained.") },
    { id: "lnk", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("Asociar a una regla", "Link to a rule"), u: T("En el editor de reglas se marca la corrección.", "In the rule editor the fix is ticked."), e: "rule", f: T("Sin duplicados en una regla.", "No duplicates on a rule.") },
    { id: "chk", t: "step", a: "auto", r: "OPTIONAL", cap: "validation", l: T("Comprobar definiciones", "Check definitions"), u: T("Detecta acciones inexistentes, precondiciones inválidas o recursión directa.", "Detects missing actions, invalid preconditions or direct recursion."), x: { rt: "ActionModelChecker", test: ["ActionModelCheckerTests"], ev: [{ path: V + "action/ActionModelChecker.java", line: 109, end_line: 117, label: "precondition checks" }] } },
    { id: "art", t: "artifact", cap: "validation", l: T("Quick Fix lista", "Quick Fix ready"), u: T("Una acción guardada y ofrecida por una regla.", "A saved action offered by a rule.") },
    { id: "e", t: "end", cap: "validation", l: T("Fin", "End") }
  ],
  edges: [["s", "id"], ["id", "pol"], ["pol", "pre", "opt"], ["pol", "par", "opt"], ["pre", "par", "opt"], ["par", "op"], ["pol", "op"], ["op", "more"], ["more", "op", "loop", T("Sí", "Yes")], ["more", "lnk", "next", T("No", "No")], ["lnk", "chk", "opt"], ["lnk", "art"], ["chk", "art"], ["art", "e"]] });

/* ------------------------------------------------------------------ AUTHORING MICRO-PROCESSES */
HM.proc({ id: "validation_author_guided", scope: "authoring", cap: "validation", elems: ["rule", "scope", "triggers", "condition", "message", "qf_action"],
  n: T("Asistente guiado de reglas", "Guided rule wizard"), d: T("Los 6 pasos del asistente de reglas.", "The 6 steps of the rule wizard."),
  nodes: [
    { id: "s", t: "start", cap: "validation", l: T("Pulsar «+ Regla»", "Press \"+ Rule\""), u: T("Se crea una regla con valores por defecto sobre la primera clase.", "A rule is created with defaults over the first class."), out: T("Regla inicial: clase, Error, incremental, al guardar y manual, condición «verdadero»", "Initial rule: class, Error, incremental, on save and manual, condition \"true\"") },
    { id: "id", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("1 Identidad", "1 Identity"), u: T("Nombre, código del problema, gravedad, grupo y activa.", "Name, problem code, severity, group and active."), need: T("Nombre y código", "Name and code"), out: T("Identidad", "Identity"), e: "rule" },
    { id: "ctx", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("2 Contexto", "2 Context"), u: T("Modo de ejecución, ámbito, clase y atributo o relación.", "Execution mode, scope, class and attribute or relation."), e: "scope" },
    { id: "dec", t: "decision", a: "user", cap: "validation", l: T("¿Ámbito de atributo o relación?", "Attribute or relation scope?"), u: T("Solo entonces hay que indicar el atributo o la relación.", "Only then must the attribute or relation be given.") },
    { id: "tar", t: "step", a: "user", r: "CONDITIONAL", cap: "validation", l: T("Elegir atributo o relación objetivo", "Choose target attribute or relation"), u: T("Debe pertenecer a la clase elegida.", "It must belong to the chosen class."), e: "scope" },
    { id: "dg", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("3 Diagnóstico y momentos", "3 Diagnostic & triggers"), u: T("Mensaje y casillas «¿Cuándo debe ejecutarse?».", "Message and \"When should it run?\" checkboxes."), e: "message" },
    { id: "cn", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("4 Condición guiada", "4 Guided condition"), u: T("Cláusulas: atributo, nº de relaciones o nº de elementos de una clase; operadores de comparación y texto; Y / O / NO.", "Clauses: attribute, number of relations or number of class elements; comparison and text operators; AND / OR / NOT."), e: "condition" },
    { id: "qf", t: "step", a: "user", r: "OPTIONAL", cap: "validation", l: T("5 Quick Fixes", "5 Quick Fixes"), u: T("Marcar las correcciones existentes que ofrecerá la regla.", "Tick the existing fixes the rule will offer."), e: "qf_action" },
    { id: "rv", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("6 Revisión", "6 Review"), u: T("Resumen de la regla antes de guardar.", "Rule summary before saving.") },
    { id: "ok", t: "decision", a: "auto", cap: "validation", l: T("¿Problemas de diseño?", "Design problems?"), u: T("Guardar se bloquea si hay problemas.", "Saving is blocked if there are problems.") },
    { id: "sv", t: "artifact", cap: "validation", l: T("Validación guardada", "Validation saved"), u: T("Botón «Guardar validación»: crea si no hay versión previa, reemplaza si la hay.", "\"Save validation\" button: creates if no previous version, replaces otherwise.") },
    { id: "e", t: "end", cap: "validation", l: T("Fin", "End") }
  ],
  edges: [["s", "id"], ["id", "ctx"], ["ctx", "dec"], ["dec", "tar", "cond", T("Sí", "Yes")], ["dec", "dg", "next", T("No", "No")], ["tar", "dg"], ["dg", "cn"], ["cn", "qf", "opt"], ["cn", "rv"], ["qf", "rv"], ["rv", "ok"], ["ok", "id", "loop", T("Sí: corregir", "Yes: fix")], ["ok", "sv", "next", T("No", "No")], ["sv", "e"]] });

HM.proc({ id: "validation_author_form", scope: "authoring", cap: "validation", elems: ["rule", "condition", "repr", "triggers"],
  n: T("Formulario completo de reglas", "Full rule form"), d: T("Edición avanzada de todas las secciones en una página.", "Advanced editing of all sections on one page."),
  nodes: [
    { id: "s", t: "start", cap: "validation", l: T("Pasar al modo avanzado", "Switch to advanced mode"), u: T("Se muestran todas las secciones de la regla a la vez.", "All the rule's sections are shown at once.") },
    { id: "idc", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("Identidad y contexto", "Identity and context"), u: T("Nombre, código, gravedad, grupo, ámbito y modo.", "Name, code, severity, group, scope and mode."), e: "rule" },
    { id: "tg", t: "step", a: "user", r: "CONDITIONAL", cap: "validation", l: T("Momentos y mensaje", "Triggers and message"), u: T("Los diez momentos disponibles (cinco no tienen efecto hoy) y el mensaje.", "The ten available triggers (five have no effect today) and the message."), e: "triggers" },
    { id: "ex", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("Árbol de expresión", "Expression tree"), u: T("Se construye la condición con los 13 tipos de nodo.", "The condition is built with the 13 node kinds."), e: "condition", x: { rt: "ExpressionBuilder", test: ["ExpressionBuilder.test.tsx"], ev: [{ path: FD + "ExpressionBuilder.tsx", line: 87, end_line: 120, label: "expression tree" }] } },
    { id: "rp", t: "step", a: "user", r: "OPTIONAL", cap: "validation", l: T("Sección avanzada: representación y conversión", "Advanced section: representation and conversion"), u: T("Solo para reglas importadas o externas.", "Only for imported or external rules."), e: "repr", f: T("Canónica no puede tener conversión externa; externa no puede ser canónica.", "Canonical cannot have external conversion; external cannot be canonical.") },
    { id: "qf", t: "step", a: "user", r: "OPTIONAL", cap: "validation", l: T("Quick Fixes ofrecidas", "Offered Quick Fixes"), u: T("Lista de casillas.", "Checkbox list."), e: "qf_action" },
    { id: "ok", t: "decision", a: "auto", cap: "validation", l: T("¿Problemas de diseño?", "Design problems?"), u: T("Se bloquea guardar si los hay.", "Saving is blocked if there are any.") },
    { id: "sv", t: "artifact", cap: "validation", l: T("Regla guardada", "Rule saved"), u: T("La regla queda en el documento de validación.", "The rule is stored in the validation document.") },
    { id: "e", t: "end", cap: "validation", l: T("Fin", "End") }
  ],
  edges: [["s", "idc"], ["idc", "tg"], ["tg", "ex"], ["ex", "rp", "opt"], ["ex", "qf", "opt"], ["rp", "qf", "opt"], ["rp", "ok"], ["qf", "ok"], ["ex", "ok"], ["ok", "idc", "loop", T("Sí: corregir", "Yes: fix")], ["ok", "sv", "next", T("No", "No")], ["sv", "e"]] });

HM.proc({ id: "validation_author_quickfix", scope: "authoring", cap: "validation", elems: ["qf_action", "qf_ops", "qf_params", "qf_policy", "qf_selector"],
  n: T("Editor de Quick Fixes", "Quick Fix editor"), d: T("Los 5 pasos: identidad, seguridad, parámetros, operaciones y revisión.", "The 5 steps: identity, safety, parameters, operations and review."),
  nodes: [
    { id: "s", t: "start", cap: "validation", l: T("Pulsar «+ Quick fix»", "Press \"+ Quick fix\""), u: T("Nace una acción activa, atómica, con deshacer obligatorio y sin operaciones.", "An enabled, atomic action with undo required and no operations is created.") },
    { id: "id", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("1 Identidad", "1 Identity"), u: T("Nombre, descripción y activa.", "Name, description and enabled."), e: "qf_action" },
    { id: "sf", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("2 Seguridad", "2 Safety"), u: T("Transacción y política de deshacer; precondición opcional.", "Transaction and undo policy; optional precondition."), e: "qf_policy" },
    { id: "pr", t: "step", a: "user", r: "OPTIONAL", cap: "validation", l: T("3 Parámetros", "3 Parameters"), u: T("«+ Parámetro» crea uno de texto obligatorio; renombrar y cambiar tipo.", "\"+ Parameter\" creates a required text one; rename and retype."), e: "qf_params" },
    { id: "op", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("4 Operaciones", "4 Operations"), u: T("Elegir clase de operación y rellenar sus datos.", "Choose the operation kind and fill in its data."), e: "qf_ops" },
    { id: "kd", t: "decision", a: "user", cap: "validation", l: T("¿Qué clase de operación?", "Which operation kind?"), u: T("Los campos cambian según la clase.", "Fields change according to the kind.") },
    { id: "sl", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("Selector de destino", "Target selector"), u: T("El propio elemento, la selección, un parámetro, uno creado antes o uno fijo.", "The element itself, the selection, a parameter, one created earlier or a fixed one."), e: "qf_selector" },
    { id: "vs", t: "step", a: "user", r: "CONDITIONAL", cap: "validation", l: T("Origen del valor", "Value source"), u: T("Fijo, calculado o pedido; solo en asignar y copiar.", "Fixed, computed or asked; only in set and copy."), e: "qf_params" },
    { id: "ag", t: "step", a: "user", r: "CONDITIONAL", cap: "validation", l: T("Argumentos", "Arguments"), u: T("Solo al ejecutar otra acción o invocar una operación del metamodelo.", "Only when running another action or invoking a metamodel operation.") },
    { id: "rv", t: "step", a: "user", r: "MANDATORY", cap: "validation", l: T("5 Revisión", "5 Review"), u: T("Resumen antes de guardar.", "Summary before saving.") },
    { id: "ok", t: "decision", a: "auto", cap: "validation", l: T("¿Válida?", "Valid?"), u: T("Debe tener nombre y al menos una operación; una importada solo para revisión debe estar desactivada.", "It must have a name and at least one operation; an imported review-only action must be disabled."), x: { rt: "validationDesignerValidation", test: ["validationDesignerValidation.test.ts"], ev: [{ path: FD + "validationDesignerValidation.ts", line: 115, end_line: 117, label: "review-only requires disabled" }] } },
    { id: "sv", t: "artifact", cap: "validation", l: T("Quick Fix guardada", "Quick Fix saved"), u: T("Se guarda en el conjunto de acciones.", "Saved in the action set.") },
    { id: "e", t: "end", cap: "validation", l: T("Fin", "End") }
  ],
  edges: [["s", "id"], ["id", "sf"], ["sf", "pr", "opt"], ["sf", "op"], ["pr", "op"], ["op", "kd"], ["kd", "sl", "next", T("Cambia atributo, relación, elemento o texto", "Changes attribute, relation, element or text")], ["kd", "sl", "alt", T("Reutiliza otra acción o invoca operación", "Reuses another action or invokes operation")], ["sl", "vs", "cond"], ["sl", "ag", "cond"], ["sl", "rv"], ["vs", "rv"], ["ag", "rv"], ["rv", "ok"], ["ok", "op", "loop", T("No: corregir", "No: fix")], ["ok", "sv", "next", T("Sí", "Yes")], ["sv", "e"]] });

HM.proc({ id: "validation_author_import", scope: "authoring", cap: "validation", elems: ["rule", "repr", "qf_action"],
  n: T("Importar reglas y correcciones", "Import rules and fixes"), d: T("Traer reglas OCL/EVL/ATL y correcciones EVL/EOL de otras herramientas.", "Bring OCL/EVL/ATL rules and EVL/EOL fixes from other tools."),
  nodes: [
    { id: "s", t: "start", cap: "validation", l: T("Elegir importar", "Choose import"), u: T("Desde el diseñador, importar reglas o correcciones.", "From the designer, import rules or fixes.") },
    { id: "ex", t: "step", a: "ext", r: "MANDATORY", cap: "validation", l: T("Fichero de la otra herramienta", "File from the other tool"), u: T("Reglas en OCL, EVL, ATL o formato HYDRA; correcciones en EVL, EOL o formato HYDRA.", "Rules in OCL, EVL, ATL or HYDRA format; fixes in EVL, EOL or HYDRA format."), need: T("Fichero", "File"), out: T("Contenido a convertir", "Content to convert") },
    { id: "cv", t: "step", a: "auto", r: "MANDATORY", cap: "validation", l: T("Convertir lo demostrable", "Convert what can be proven"), u: T("Solo un subconjunto sencillo y equivalente se traduce a regla ejecutable.", "Only a simple, equivalent subset is translated to an executable rule."), e: "repr", x: { rt: "SafeExternalConstraintTranslator", test: [], ev: [{ path: V + "importing/SafeExternalConstraintTranslator.java", line: 35, end_line: 51, label: "safe subset" }] } },
    { id: "dc", t: "decision", a: "auto", cap: "validation", l: T("¿Conversión completa?", "Full conversion?"), u: T("Decide si la regla es ejecutable o solo se conserva.", "Decides whether the rule is executable or only kept.") },
    { id: "pt", t: "step", a: "auto", r: "CONDITIONAL", cap: "validation", l: T("Conservar como parcial o externa", "Keep as partial or external"), u: T("No se ejecuta pero se conserva para revisión, con aviso de omisión.", "Not executed but kept for review, with a skip notice."), s: T("regla: importada → parcial/externa", "rule: imported → partial/external") },
    { id: "lk", t: "step", a: "auto", r: "OPTIONAL", cap: "validation", l: T("Enlazar correcciones con reglas", "Link fixes to rules"), u: T("Por nombre o código; se avisa de los enlaces no resueltos.", "By name or code; unresolved links are reported."), e: "qf_action" },
    { id: "rv", t: "step", a: "user", r: "OPTIONAL", cap: "validation", l: T("Revisar y completar", "Review and complete"), u: T("Se editan las reglas importadas con el asistente o el formulario.", "Imported rules are edited with the wizard or the form.") },
    { id: "sv", t: "artifact", cap: "validation", l: T("Reglas y correcciones importadas", "Imported rules and fixes"), u: T("Se guardan junto al resto del diseño.", "Saved alongside the rest of the design.") },
    { id: "e", t: "end", cap: "validation", l: T("Fin", "End") }
  ],
  edges: [["s", "ex"], ["ex", "cv"], ["cv", "dc"], ["dc", "lk", "next", T("Sí", "Yes")], ["dc", "pt", "cond", T("No", "No")], ["pt", "lk", "opt"], ["lk", "rv", "opt"], ["lk", "sv"], ["rv", "sv"], ["sv", "e"]] });
})();
