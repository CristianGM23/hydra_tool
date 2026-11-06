/* HYDRA portal audience lens — capability "validation". FUNCTIONAL + TECHNICAL projections (overlay on hydra-model-validation.js). */
(function(){ "use strict"; var HM = window.HM, T = HM.T;
HM.lens({
  caps: {
    validation: {
      func: {
        title: T("Motor declarativo de restricciones, diagnósticos y acciones correctoras", "Declarative engine of constraints, diagnostics and corrective actions"),
        summary: T("Una regla es una restricción declarativa que combina un ámbito, un modo, unos momentos de disparo, una condición booleana, una gravedad y una plantilla de mensaje. Al evaluarla sobre sus elementos candidatos produce diagnósticos con identidad estable, que se consolidan en un informe y alimentan una decisión de bloqueo. Cada diagnóstico puede enlazar con acciones correctoras reutilizables (compuestas de operaciones ordenadas) cuya aplicación es reversible y vuelve a evaluar el modelo.",
                   "A rule is a declarative constraint combining a scope, a mode, triggers, a boolean condition, a severity and a message template. Evaluating it over its candidate elements yields diagnostics with a stable identity, which are consolidated into a report and feed a blocking decision. Each diagnostic can link to reusable corrective actions (made of ordered operations) whose application is reversible and triggers a new evaluation of the model."),
        inputs: [
          T("Documento de validación: reglas, grupos y referencias a condiciones", "Validation document: rules, groups and references to conditions"),
          T("Metamodelo de referencia (clases, atributos, relaciones, operaciones)", "Reference metamodel (classes, attributes, relations, operations)"),
          T("Modelo o selección a evaluar y modo de validación solicitado", "Model or selection to evaluate and requested validation mode"),
          T("Conjunto de acciones correctoras asociado al metamodelo", "Corrective action set linked to the metamodel"),
          T("Reglas externas (OCL, EVL, ATL) con su estado de conversión", "External rules (OCL, EVL, ATL) with their conversion status")
        ],
        outputs: [
          T("Diagnósticos clasificados como incumplimiento, error de ejecución o regla rota", "Diagnostics classified as violation, execution error or broken rule"),
          T("Informe con reglas ejecutadas, omitidas y métricas", "Report with executed and skipped rules and metrics"),
          T("Decisión de bloqueo por puerta (ninguna, guardar, exportar)", "Blocking decision per gate (none, save, export)"),
          T("Modelo modificado con resumen de cambios e historial deshacer/rehacer", "Modified model with change summary and undo/redo history"),
          T("Eventos de ejecución, violación creada/resuelta y corrección", "Events for execution, violation created/resolved and fix")
        ],
        conditions: [
          T("Solo las reglas canónicas con conversión completa y sin errores de diseño son ejecutables", "Only canonical rules with full conversion and no design errors are executable"),
          T("La condición debe resolverse a verdadero/falso; de lo contrario es un error de ejecución, no un incumplimiento", "The condition must resolve to true/false; otherwise it is an execution error, not a violation"),
          T("Solo la gravedad ERROR puede bloquear; un fallo de la regla cuenta siempre como error", "Only ERROR severity can block; a rule failure always counts as an error"),
          T("En modo completo o incremental los momentos de disparo se ignoran; manda el modo", "In full or incremental runs triggers are ignored; the mode rules"),
          T("Una acción correctora se ofrece solo si la regla la lista, está activa y cumple su precondición", "A corrective action is offered only if the rule lists it, it is enabled and its precondition holds")
        ]
      },
      tech: {
        title: T("Compilador de plan + ValidationEngine + ValidationOrchestrator + CanonicalActionExecutor", "Plan compiler + ValidationEngine + ValidationOrchestrator + CanonicalActionExecutor"),
        summary: T("ValidationCompiler convierte el documento en un plan (caché por huella SHA-256) que ValidationEngine evalúa a través de ValidationRuntimeApplicationService; ValidationOrchestrator consolida informes, aplica DefaultValidationBlockingPolicy y publica eventos core.*. Las Quick Fixes las ejecuta CanonicalActionExecutor sobre una copia y QuickFixRuntimeService aplica/deshace con instantáneas. Endpoints: POST .../validations/runtime/validate y /orchestrate?gate=, POST /api/models/{modelId}/validation-runtime/validate y /problems/{diagnosticId}/quick-fixes (+ preview, apply, undo, redo); la sesión del editor llama directamente al motor, no al orquestador.",
                   "ValidationCompiler turns the document into a plan (cached by SHA-256 fingerprint) that ValidationEngine evaluates through ValidationRuntimeApplicationService; ValidationOrchestrator consolidates reports, applies DefaultValidationBlockingPolicy and publishes core.* events. Quick Fixes run in CanonicalActionExecutor over a copy and QuickFixRuntimeService applies/undoes with snapshots. Endpoints: POST .../validations/runtime/validate and /orchestrate?gate=, POST /api/models/{modelId}/validation-runtime/validate and /problems/{diagnosticId}/quick-fixes (+ preview, apply, undo, redo); the editor session calls the engine directly, not the orchestrator.")
      },
      elements: {
        rule: {
          nf: T("Regla de validación (restricción declarativa)", "Validation rule (declarative constraint)"),
          fm: T("Unidad central del modelo de validación: restricción con identidad, clasificada por ámbito, modo, disparos y representación. Pertenece a un modelo de validación (0..1 grupo), referencia exactamente una condición y 0..N acciones correctoras, y no puede compartir identificador con otra regla. Ciclo de vida: creada, guardada, verificada, activa o desactivada.",
                  "Central unit of the validation model: a constraint with identity, classified by scope, mode, triggers and representation. It belongs to a validation model (0..1 group), references exactly one condition and 0..N corrective actions, and cannot share its identifier with another rule. Lifecycle: created, saved, verified, active or disabled."),
          tm: T("Registro ValidationRule (id, name, code, context, severity, conditionExpressionId, messageTemplate, executionMode, authoritativeRepresentation, conversionStatus; opcionales description, triggers, dependencies, quickFixIds, annotations, enabled). Sus comprobaciones estáticas están en ValidationModelChecker.",
                  "ValidationRule record (id, name, code, context, severity, conditionExpressionId, messageTemplate, executionMode, authoritativeRepresentation, conversionStatus; optional description, triggers, dependencies, quickFixIds, annotations, enabled). Its static checks live in ValidationModelChecker."),
          variants: []
        },
        scope: {
          nf: T("Ámbito de evaluación (conjunto de candidatos)", "Evaluation scope (candidate set)"),
          fm: T("Eje que determina sobre qué elementos se evalúa la condición. Hay ocho ámbitos; clase, atributo, relación y objeto iteran sobre candidatos, mientras que modelo, proyecto y representación se evalúan una sola vez. Cada ámbito impone qué referencias al metamodelo son obligatorias y cuáles sobran.",
                  "Axis that determines over which elements the condition is evaluated. There are eight scopes; class, attribute, relation and object iterate over candidates, whereas model, project and representation are evaluated once. Each scope dictates which metamodel references are mandatory and which are superfluous."),
          tm: T("Enum ValidationContextKind dentro de ValidationContext; ValidationModelChecker valida las referencias por tipo y ValidationEngine resuelve candidatos o el caso global.",
                  "ValidationContextKind enum inside ValidationContext; ValidationModelChecker validates references per kind and ValidationEngine resolves candidates or the global case."),
          variants: [
            { k: "CLASS", fd: T("Candidatos: instancias de la clase y de sus subclases; requiere la clase.", "Candidates: instances of the class and its subclasses; requires the class.") },
            { k: "ATTRIBUTE", fd: T("Candidatos: instancias con un atributo concreto; requiere clase y un atributo declarado en ella.", "Candidates: instances carrying a specific attribute; requires a class and an attribute declared in it.") },
            { k: "RELATION", fd: T("Candidatos: instancias origen de una relación; requiere una relación que parta de la clase.", "Candidates: instances at the source of a relation; requires a relation starting at the class.") },
            { k: "OBJECT", fd: T("Candidato: una instancia concreta de la clase indicada.", "Candidate: one concrete instance of the given class.") },
            { k: "MODEL", fd: T("Evaluación única sobre el modelo como un todo; clase y atributo no se usan.", "Single evaluation over the model as a whole; class and attribute are unused.") },
            { k: "PROJECT", fd: T("Evaluación única sobre el proyecto completo, más allá de un modelo.", "Single evaluation over the entire project, beyond one model.") },
            { k: "REPRESENTATION", fd: T("Prevista para una vista concreta, pero hoy equivale a un ámbito global de evaluación única.", "Intended for a specific view, but today equivalent to a global single-evaluation scope.") },
            { k: "SELECTION", fd: T("Candidatos: el conjunto de elementos seleccionados en el momento de validar.", "Candidates: the set of elements selected at validation time.") }
          ]
        },
        execmode: {
          nf: T("Modo de ejecución (política de participación)", "Execution mode (participation policy)"),
          fm: T("Eje que decide en qué tipos de validación entra la regla y, por tanto, el coste que asume. Condiciona qué momentos de disparo son admisibles y si la regla puede ser canónica. En ejecuciones completas o incrementales el modo prevalece sobre los disparos declarados.",
                  "Axis deciding in which kinds of validation the rule takes part and hence the cost it incurs. It restricts which triggers are admissible and whether the rule can be canonical. In full or incremental runs the mode prevails over declared triggers."),
          tm: T("Enum ValidationExecutionMode; ValidationEngine selecciona reglas por modo y por dependencias cambiadas, y ValidationModelChecker valida la coherencia modo/disparo.",
                  "ValidationExecutionMode enum; ValidationEngine selects rules by mode and by changed dependencies, and ValidationModelChecker validates mode/trigger consistency."),
          variants: [
            { k: "INCREMENTAL", fd: T("Declara dependencias; se reejecuta solo si cambia alguno de los elementos vigilados.", "Declares dependencies; re-runs only if one of the watched elements changes.") },
            { k: "FULL_ONLY", fd: T("Participa únicamente en validaciones completas; admite los disparos completa, guardar y exportar.", "Takes part only in full validations; admits the full, save and export triggers.") },
            { k: "MANUAL_ONLY", fd: T("Nunca se ejecuta sola; admite únicamente el disparo manual.", "Never runs by itself; admits only the manual trigger.") },
            { k: "EXTERNAL", fd: T("La evaluación pertenece a otra herramienta; la regla se conserva pero no se ejecuta ni puede ser canónica.", "Evaluation belongs to another tool; the rule is kept but neither executed nor allowed to be canonical.") }
          ]
        },
        triggers: {
          nf: T("Momento de disparo (evento de validación)", "Trigger (validation event)"),
          fm: T("Conjunto 0..N de eventos del ciclo de vida del modelo en que la regla pide ser evaluada; obligatorio salvo en reglas externas. Solo se leen en validaciones por momento (abrir, guardar, exportar, manual); cinco de los diez valores están declarados pero ningún proceso los consume.",
                  "A 0..N set of model lifecycle events at which the rule asks to be evaluated; mandatory except for external rules. They are read only in moment-based validations (open, save, export, manual); five of the ten values are declared but no process consumes them."),
          tm: T("Enum ValidationTrigger (10 valores); la interfaz de ValidationRuleEditor ofrece los diez, pero ValidationEngine solo filtra por ON_OPEN, ON_SAVE, BEFORE_EXPORT y MANUAL.",
                  "ValidationTrigger enum (10 values); the ValidationRuleEditor UI offers all ten, but ValidationEngine only filters by ON_OPEN, ON_SAVE, BEFORE_EXPORT and MANUAL."),
          variants: [
            { k: "ON_OPEN", fd: T("Evalúa la regla al abrir el modelo para edición.", "Evaluates the rule when the model is opened for editing.") },
            { k: "ON_SAVE", fd: T("Evalúa la regla antes de persistir el modelo; es parte del valor por defecto de una regla nueva.", "Evaluates the rule before the model is persisted; part of a new rule's default.") },
            { k: "BEFORE_EXPORT", fd: T("Evalúa la regla antes de exportar o generar artefactos.", "Evaluates the rule before exporting or generating artifacts.") },
            { k: "MANUAL", fd: T("Evalúa la regla cuando la persona solicita validar; también forma parte del valor por defecto.", "Evaluates the rule when the person requests validation; also part of the default.") },
            { k: "FULL_VALIDATION", fd: T("Marca la regla como parte de cada validación completa.", "Marks the rule as part of every full validation.") },
            { fd: T("Eventos finos del ciclo de vida (creación, cambio de atributo o relación, borrado, segundo plano): se pueden declarar pero no producen ninguna evaluación.", "Fine-grained lifecycle events (creation, attribute or relation change, deletion, background): they can be declared but trigger no evaluation."), td: T("ON_CREATE, ON_ATTRIBUTE_CHANGE, ON_RELATION_CHANGE, ON_DELETE y BACKGROUND: declarados, no cableados.", "ON_CREATE, ON_ATTRIBUTE_CHANGE, ON_RELATION_CHANGE, ON_DELETE and BACKGROUND: declared, not wired.") }
          ]
        },
        condition: {
          nf: T("Condición booleana (predicado de conformidad)", "Boolean condition (conformance predicate)"),
          fm: T("Predicado que se evalúa sobre cada candidato: verdadero significa conforme y falso genera un incumplimiento. Es una expresión compartida entre reglas, referenciada por la regla y no incrustada en ella, y debe tipar a booleano. Puede construirse con cláusulas guiadas o con el árbol de expresión completo.",
                  "Predicate evaluated over each candidate: true means conformant and false yields a violation. It is an expression shared across rules, referenced by the rule rather than embedded in it, and must type to boolean. It can be built from guided clauses or from the full expression tree."),
          tm: T("Referencia conditionExpressionId a un ExpressionModel; ValidationModelChecker detecta expresión ausente o no booleana. El constructor de interfaz maneja 13 tipos de nodo (ExpressionBuilder) y las comprobaciones guiadas (GuidedConditionBuilder).",
                  "conditionExpressionId reference to an ExpressionModel; ValidationModelChecker detects a missing or non-boolean expression. The UI builder handles 13 node kinds (ExpressionBuilder) and guided checks (GuidedConditionBuilder)."),
          variants: [
            { fd: T("Forma restringida: comprobaciones de atributo, recuentos de relaciones o de instancias combinadas con conjunción, disyunción y negación.", "Restricted form: attribute checks, relation or instance counts combined through conjunction, disjunction and negation.") },
            { fd: T("Forma general: expresión sobre el propio elemento, el modelo, variables y colecciones, con navegación, operaciones, condicionales y funciones.", "General form: expression over the element itself, the model, variables and collections, with navigation, operations, conditionals and functions.") },
            { fd: T("Vocabulario de comparación de las cláusulas: relacionales, de texto (contiene, prefijo, sufijo, patrón), de vacío y de nulidad.", "Comparison vocabulary of the clauses: relational, textual (contains, prefix, suffix, pattern), emptiness and nullity.") }
          ]
        },
        severity: {
          nf: T("Gravedad (nivel de criticidad del incumplimiento)", "Severity (criticality level of a violation)"),
          fm: T("Escala ordinal de tres niveles asignada a la regla y heredada por sus diagnósticos. Determina la capacidad de bloqueo: solo el nivel más alto puede impedir una operación. Una regla que falla o está rota produce siempre el nivel más alto, con independencia de su gravedad declarada.",
                  "Three-level ordinal scale assigned to the rule and inherited by its diagnostics. It determines blocking capacity: only the highest level can prevent an operation. A rule that fails or is broken always yields the highest level, regardless of its declared severity."),
          tm: T("Enum ValidationSeverity; DefaultValidationBlockingPolicy considera solo ERROR para bloquear.", "ValidationSeverity enum; DefaultValidationBlockingPolicy considers only ERROR for blocking."),
          variants: [
            { k: "ERROR", fd: T("Nivel crítico: único capaz de bloquear guardado o exportación; valor inicial de una regla nueva.", "Critical level: the only one able to block saving or export; initial value of a new rule.") },
            { k: "WARNING", fd: T("Nivel intermedio: se reporta para revisión sin interrumpir ninguna operación.", "Intermediate level: reported for review without interrupting any operation.") },
            { k: "INFO", fd: T("Nivel informativo: observación sin consecuencias sobre el flujo de trabajo.", "Informational level: an observation with no consequences on the workflow.") }
          ]
        },
        message: {
          nf: T("Plantilla de mensaje (texto parametrizado del diagnóstico)", "Message template (parameterized diagnostic text)"),
          fm: T("Texto no vacío asociado 1:1 a la regla que se instancia en cada diagnóstico sustituyendo cinco marcadores contextuales (regla, código, atributo, elemento, clase). No hay formato avanzado, escapes ni traducción automática; un fallo de la regla sustituye el texto por un mensaje de error de ejecución.",
                  "Non-empty text tied 1:1 to the rule that is instantiated in every diagnostic by substituting five contextual markers (rule, code, attribute, element, class). There is no advanced formatting, escaping or automatic translation; a rule failure replaces the text with an execution-error message."),
          tm: T("Campo messageTemplate de ValidationRule con marcadores ${rule} ${code} ${featureId} ${elementId} ${classId}, sustituidos en ValidationEngine; sin i18n de mensajes en backend.",
                  "messageTemplate field of ValidationRule with markers ${rule} ${code} ${featureId} ${elementId} ${classId}, substituted in ValidationEngine; no backend message i18n."),
          variants: [
            { fd: T("Los cinco datos contextuales admitidos como marcadores: regla, código, atributo, elemento y clase.", "The five contextual data items admitted as markers: rule, code, attribute, element and class.") }
          ]
        },
        diagnostic: {
          nf: T("Diagnóstico e informe de validación (resultado observable)", "Diagnostic and validation report (observable result)"),
          fm: T("Un diagnóstico es la instancia de un hallazgo sobre un elemento y atributo, con identidad estable entre ejecuciones (mismo modelo, regla, elemento y atributo). El informe agrupa 0..N diagnósticos con las reglas ejecutadas y omitidas, y al compararlo con el anterior distingue creados, sin cambio y resueltos. Estados: abierto, resuelto.",
                  "A diagnostic is the instance of a finding on an element and attribute, with a stable identity across runs (same model, rule, element and attribute). The report groups 0..N diagnostics with the executed and skipped rules, and when compared with the previous one it distinguishes created, unchanged and resolved. States: open, resolved."),
          tm: T("ModelValidationDiagnostic y ModelValidationReport (id, kind, ruleId, severity, message, location, quickFixIds, details; modo, alcance, executedRuleIds, skippedRuleIds, metrics); el delta lo calcula ValidationOrchestrator. relationInstanceId de la localización nunca se rellena.",
                  "ModelValidationDiagnostic and ModelValidationReport (id, kind, ruleId, severity, message, location, quickFixIds, details; mode, scope, executedRuleIds, skippedRuleIds, metrics); the delta is computed by ValidationOrchestrator. The location's relationInstanceId is never filled."),
          variants: [
            { k: "VIOLATION", fd: T("El candidato evaluó la condición a falso; hereda gravedad y mensaje de la regla.", "The candidate evaluated the condition to false; inherits severity and message from the rule.") },
            { k: "RULE_EXECUTION_ERROR", fd: T("La regla no pudo evaluarse (por ejemplo, resultado no booleano); se informa siempre como error.", "The rule could not be evaluated (for example, a non-boolean result); always reported as an error.") },
            { k: "BROKEN_RULE", fd: T("Defecto de definición detectado en la propia regla, anterior a cualquier evaluación.", "Definition defect detected in the rule itself, prior to any evaluation.") },
            { fd: T("Delimitación de un informe: modelo, objeto, atributo, selección, una regla o un grupo de reglas.", "Delimitation of a report: model, object, feature, selection, one rule or a rule group."), td: T("Valores de scope: MODEL, OBJECT, FEATURE, SELECTION, RULE, RULE_SET.", "scope values: MODEL, OBJECT, FEATURE, SELECTION, RULE, RULE_SET.") }
          ]
        },
        ruleset: {
          nf: T("Grupo de reglas (agrupación ordenada con activación conjunta)", "Rule group (ordered grouping with joint activation)"),
          fm: T("Colección ordenada de 0..N reglas que se organiza por tema y se activa o desactiva en bloque. Una regla pertenece como máximo a un grupo, el orden es un número no negativo y desactivar el grupo apaga todas sus reglas. Permite acotar una validación a un solo grupo.",
                  "Ordered collection of 0..N rules organized by topic and enabled or disabled as a block. A rule belongs to at most one group, order is a non-negative number and disabling the group switches off all its rules. It allows restricting a validation to a single group."),
          tm: T("Registro ValidationRuleSet con order >= 0 y enabled, dentro de ValidationModel.", "ValidationRuleSet record with order >= 0 and enabled, inside ValidationModel."),
          variants: []
        },
        repr: {
          nf: T("Representación autoritativa y estado de conversión", "Authoritative representation and conversion status"),
          fm: T("Dos ejes que declaran en qué lenguaje reside la verdad de la regla y con qué fidelidad se tradujo al lenguaje propio. Solo la combinación canónica con conversión completa es editable y ejecutable; las demás se conservan con su origen para revisión y se omiten con aviso. Una canónica no puede tener conversión externa, y una externa no puede ser canónica.",
                  "Two axes declaring in which language the rule's truth resides and how faithfully it was translated to the native language. Only the canonical combination with full conversion is editable and executable; the others are kept with their origin for review and skipped with a notice. A canonical rule cannot have external conversion, and an external one cannot be canonical."),
          tm: T("Enums AuthoritativeRepresentation y ConversionStatus más ImportedValidationSource (technology, fileName, contentHash); ValidationCompiler omite las no canónicas y SafeExternalConstraintTranslator traduce un subconjunto seguro.",
                  "AuthoritativeRepresentation and ConversionStatus enums plus ImportedValidationSource (technology, fileName, contentHash); ValidationCompiler skips non-canonical ones and SafeExternalConstraintTranslator translates a safe subset."),
          variants: [
            { k: "CANONICAL", fd: T("Representación nativa: es la fuente de verdad, editable y ejecutable.", "Native representation: the source of truth, editable and executable.") },
            { fd: T("Fuentes de restricciones declarativas de otras tecnologías de modelado, conservadas con su origen.", "Declarative constraint sources from other modeling technologies, kept with their origin.") },
            { fd: T("Fuentes de código o de bloques admitidas por el modelo de datos; la interfaz de edición no ofrece las de JavaScript ni Blockly.", "Code or block sources admitted by the data model; the editing interface does not offer the JavaScript and Blockly ones.") },
            { k: "EXTERNAL", fd: T("La verdad reside fuera de HYDRA y la regla nunca es canónica.", "The truth lives outside HYDRA and the rule is never canonical.") },
            { k: "FULL", fd: T("Traducción sin pérdida: la regla pasa a ser ejecutable.", "Lossless translation: the rule becomes executable.") },
            { k: "PARTIAL", fd: T("Traducción incompleta: la regla se conserva pero queda fuera del plan de ejecución.", "Incomplete translation: the rule is kept but left out of the execution plan.") },
            { k: "UNSUPPORTED", fd: T("Sin traducción posible: solo se guarda para revisión humana.", "No translation possible: stored only for human review.") }
          ]
        },
        gate: {
          nf: T("Puerta de bloqueo (política de decisión sobre una operación)", "Blocking gate (decision policy over an operation)"),
          fm: T("Decisión binaria permitir/bloquear que se toma al consolidar un informe respecto de una operación de salida. Solo los diagnósticos de gravedad máxima bloquean y, por defecto, las operaciones bloqueables son guardar y exportar. Una validación tiene una única decisión; sin puerta nunca se bloquea.",
                  "Binary allow/block decision taken when consolidating a report with respect to an outgoing operation. Only maximum-severity diagnostics block and, by default, the blockable operations are save and export. A validation has a single decision; with no gate it never blocks."),
          tm: T("Enum ValidationGate evaluado por DefaultValidationBlockingPolicy dentro de ValidationOrchestrator; el parámetro gate de /orchestrate lo selecciona (por defecto NONE). La sesión del editor no aplica esta política.",
                  "ValidationGate enum evaluated by DefaultValidationBlockingPolicy inside ValidationOrchestrator; the gate parameter of /orchestrate selects it (default NONE). The editor session does not apply this policy."),
          variants: [
            { k: "NONE", fd: T("Sin operación protegida: el informe es solo informativo.", "No protected operation: the report is merely informative.") },
            { k: "SAVE", fd: T("Protege la persistencia: cualquier diagnóstico de gravedad máxima impide guardar.", "Protects persistence: any maximum-severity diagnostic prevents saving.") },
            { k: "EXPORT", fd: T("Protege la salida: cualquier diagnóstico de gravedad máxima impide exportar o generar.", "Protects output: any maximum-severity diagnostic prevents exporting or generating.") }
          ]
        },
        qf_action: {
          nf: T("Acción correctora (Quick Fix reutilizable)", "Corrective action (reusable Quick Fix)"),
          fm: T("Secuencia ordenada de 1..N operaciones sobre el modelo, con parámetros, política transaccional, política de deshacer y precondición opcional. Carece de destino propio: opera sobre el elemento del diagnóstico o la selección, y no se clasifica por tipo. Varias reglas pueden ofrecerla, pero una misma regla no la repite. Se ofrece solo si está activa, existe y su precondición se cumple.",
                  "Ordered sequence of 1..N operations over the model, with parameters, transactional policy, undo policy and an optional precondition. It has no target of its own: it acts on the diagnostic's element or on the selection, and is not classified by type. Several rules may offer it, but a single rule does not repeat it. It is offered only if enabled, existing and with its precondition met."),
          tm: T("Registro ActionDefinition (id, name, operations, transactionPolicy, undoPolicy; opcionales description, enabled, parameters, preconditionExpressionId); ValidationModelChecker detecta DUPLICATE_QUICK_FIX, UNKNOWN_QUICK_FIX y ACTION_MODEL_REQUIRED.",
                  "ActionDefinition record (id, name, operations, transactionPolicy, undoPolicy; optional description, enabled, parameters, preconditionExpressionId); ValidationModelChecker detects DUPLICATE_QUICK_FIX, UNKNOWN_QUICK_FIX and ACTION_MODEL_REQUIRED."),
          variants: []
        },
        qf_ops: {
          nf: T("Operación de corrección (paso elemental sobre el modelo)", "Fix operation (elementary step over the model)"),
          fm: T("Paso atómico de una acción correctora, tomado de un vocabulario cerrado de diez primitivas sobre atributos, relaciones, elementos y composición de acciones. Cada operación apunta a un elemento mediante un selector y se ejecuta en orden sobre una copia del modelo. Los campos exigidos dependen de la primitiva.",
                  "Atomic step of a corrective action, drawn from a closed vocabulary of ten primitives over attributes, relations, elements and action composition. Each operation points at an element through a selector and runs in order over a copy of the model. The required fields depend on the primitive."),
          tm: T("Interfaz sellada ActionOperation con @JsonTypeInfo(kind) y diez subtipos; CanonicalActionExecutor implementa la ejecución de cada uno.", "Sealed interface ActionOperation with @JsonTypeInfo(kind) and ten subtypes; CanonicalActionExecutor implements the execution of each."),
          variants: [
            { k: "SET_ATTRIBUTE", fd: T("Escribe en un atributo un valor obtenido de una fuente (fija, calculada o pedida).", "Writes into an attribute a value obtained from a source (fixed, computed or asked).") },
            { k: "CLEAR_ATTRIBUTE", fd: T("Deja un atributo sin valor.", "Leaves an attribute without a value.") },
            { k: "COPY_ATTRIBUTE", fd: T("Transfiere el valor de un atributo de un elemento origen a uno destino.", "Transfers an attribute value from a source element to a target one.") },
            { k: "ADD_RELATION", fd: T("Establece un vínculo entre dos elementos; es idempotente si ya existía.", "Establishes a link between two elements; idempotent if it already existed.") },
            { k: "REMOVE_RELATION", fd: T("Disuelve el vínculo entre dos elementos.", "Dissolves the link between two elements.") },
            { k: "CREATE_ELEMENT", fd: T("Instancia un elemento de una clase, lo nombra para referencias posteriores y opcionalmente lo aloja en un contenedor mediante una relación de contención.", "Instantiates an element of a class, names it for later references and optionally places it in a container through a containment relation.") },
            { k: "DELETE_ELEMENT", fd: T("Elimina un elemento arrastrando su contenido y sus vínculos.", "Deletes an element together with its contents and links.") },
            { k: "TRANSFORM_TEXT", fd: T("Normaliza un valor textual: mayúsculas, minúsculas, capitalización o recorte de blancos.", "Normalizes a textual value: uppercase, lowercase, capitalization or whitespace trimming.") },
            { k: "EXECUTE_ACTION", fd: T("Compone otra acción pasándole argumentos; no admite recursión directa.", "Composes another action passing it arguments; direct recursion is not allowed.") },
            { k: "INVOKE_OPERATION", fd: T("Delega en una operación de tipo comando del metamodelo vinculada a una acción.", "Delegates to a command-type metamodel operation bound to an action.") }
          ]
        },
        qf_params: {
          nf: T("Parámetro de acción y origen del valor", "Action parameter and value source"),
          fm: T("Dato de entrada declarado por una acción correctora (0..N), con nombre, tipo y obligatoriedad, que parametriza sus operaciones. Todo valor tiene exactamente un origen: constante, resultado de una expresión o dato solicitado a la persona. Se resuelve al aplicar y se rechazan argumentos desconocidos o ausentes.",
                  "Input datum declared by a corrective action (0..N), with a name, type and requiredness, that parameterizes its operations. Every value has exactly one origin: a constant, the result of an expression or a datum requested from the person. It is resolved at application time and unknown or missing arguments are rejected."),
          tm: T("ActionParameter (ActionParameterType) y ActionValueSource (literal | expressionId | parameterName, exactamente uno); CanonicalActionExecutor.validateArguments comprueba presencia, sin validar el tipo del valor.",
                  "ActionParameter (ActionParameterType) and ActionValueSource (literal | expressionId | parameterName, exactly one); CanonicalActionExecutor.validateArguments checks presence, without validating the value's type."),
          variants: [
            { fd: T("Tipos escalares: cadena, entero, decimal y lógico.", "Scalar types: string, integer, decimal and boolean."), td: T("STRING, INTEGER, DECIMAL, BOOLEAN.", "STRING, INTEGER, DECIMAL, BOOLEAN.") },
            { fd: T("Tipos de referencia: identificadores de elemento, clase, relación y atributo.", "Reference types: element, class, relation and attribute identifiers."), td: T("ELEMENT_ID, CLASS_ID, RELATION_ID, ATTRIBUTE_ID.", "ELEMENT_ID, CLASS_ID, RELATION_ID, ATTRIBUTE_ID.") },
            { k: "literal", fd: T("Constante incorporada a la propia operación; no se solicita.", "Constant embedded in the operation itself; never requested.") },
            { k: "expressionId", fd: T("Se deriva de una expresión evaluada sobre el contexto; no se solicita.", "Derived from an expression evaluated over the context; never requested.") },
            { k: "parameterName", fd: T("Referencia a un parámetro declarado, cuyo valor aporta la persona al aplicar.", "Reference to a declared parameter, whose value the person supplies on application.") }
          ]
        },
        qf_selector: {
          nf: T("Selector de destino (resolución del elemento objetivo)", "Target selector (target element resolution)"),
          fm: T("Mecanismo por el que cada operación identifica el elemento sobre el que actúa, evaluado en cada aplicación según el contexto del diagnóstico. Una operación usa 1..2 selectores (destino y, si procede, origen). No existe un destino propio de la acción por tipo de elemento.",
                  "Mechanism by which each operation identifies the element it acts on, evaluated at each application according to the diagnostic's context. An operation uses 1..2 selectors (target and, where applicable, source). There is no per-element-type target on the action itself."),
          tm: T("Enum ActionSelectorKind; la resolución está en QuickFixRuntimeService.", "ActionSelectorKind enum; resolution is in QuickFixRuntimeService."),
          variants: [
            { k: "SELF", fd: T("El elemento sobre el que se emitió el diagnóstico.", "The element on which the diagnostic was emitted.") },
            { k: "SELECTION", fd: T("El primer elemento de la selección activa en el editor.", "The first element of the editor's active selection.") },
            { k: "PARAMETER", fd: T("El elemento designado por un parámetro de la acción.", "The element designated by an action parameter.") },
            { k: "CREATED_ELEMENT", fd: T("Un elemento producido antes en la misma acción y referido por su nombre de resultado.", "An element produced earlier in the same action and referred to by its result name.") },
            { k: "ELEMENT_ID", fd: T("Un elemento fijado de antemano por su identificador.", "An element fixed in advance by its identifier.") }
          ]
        },
        qf_policy: {
          nf: T("Política transaccional, de reversibilidad y precondición", "Transactional, reversibility and precondition policy"),
          fm: T("Tres decisiones de seguridad por acción: qué ocurre ante fallos intermedios, si el resultado se registra para revertirse y bajo qué predicado se ofrece. Valores por defecto: transacción atómica y deshacer obligatorio. La precondición, si existe, debe resolverse a booleano.",
                  "Three safety decisions per action: what happens on intermediate failures, whether the outcome is recorded to be reverted, and under which predicate it is offered. Defaults: atomic transaction and undo required. The precondition, if any, must resolve to boolean."),
          tm: T("Enums ActionTransactionPolicy y ActionUndoPolicy más preconditionExpressionId; CanonicalActionExecutor las aplica y ActionModelChecker valida la precondición. REQUIRED y OPTIONAL se comportan igual en ejecución.",
                  "ActionTransactionPolicy and ActionUndoPolicy enums plus preconditionExpressionId; CanonicalActionExecutor applies them and ActionModelChecker validates the precondition. REQUIRED and OPTIONAL behave the same at runtime."),
          variants: [
            { k: "ATOMIC", fd: T("Todo o nada: ante cualquier fallo se restaura el estado previo.", "All or nothing: any failure restores the previous state.") },
            { k: "BEST_EFFORT", fd: T("Se conservan las operaciones logradas y se acumulan los fallos como mensajes.", "Successful operations are kept and failures accumulate as messages.") },
            { k: "REQUIRED", fd: T("Declara que la acción debe poder revertirse; en la práctica se trata como opcional.", "Declares that the action must be revertible; in practice treated as optional.") },
            { k: "OPTIONAL", fd: T("Reversible si el resultado modificó algo.", "Reversible if the outcome changed anything.") },
            { k: "NOT_SUPPORTED", fd: T("Acción irreversible: no se registra nada para deshacer.", "Irreversible action: nothing is recorded for undo.") }
          ]
        },
        qf_result: {
          nf: T("Resultado de aplicación y estado de corrección", "Application result and fix status"),
          fm: T("Resultado de previsualizar, aplicar, deshacer o rehacer una acción: resumen de cambios, estado final y revalidación incremental. Ciclo de vida: vista previa, aplicación, revalidación, deshacer/rehacer. Exige que el diagnóstico conste en el último informe; si no, se valida antes. El historial es acotado y aplicar una corrección nueva vacía el de rehacer.",
                  "Outcome of previewing, applying, undoing or redoing an action: change summary, final status and incremental revalidation. Lifecycle: preview, application, revalidation, undo/redo. It requires the diagnostic to be in the last report; otherwise validation runs first. History is bounded and applying a new fix clears the redo history."),
          tm: T("QuickFixApplicationStatus y CanonicalModelChangeSummary producidos por QuickFixRuntimeService (preview/apply); en la sesión del editor exige la capacidad QUICK_FIX_RUNTIME.",
                  "QuickFixApplicationStatus and CanonicalModelChangeSummary produced by QuickFixRuntimeService (preview/apply); in the editor session it requires the QUICK_FIX_RUNTIME capability."),
          variants: [
            { fd: T("Simulación sobre una copia: informa de los cambios sin alterar el modelo ni revalidar.", "Simulation over a copy: reports changes without altering the model or revalidating.") },
            { k: "APPLIED_RESOLVED", fd: T("El modelo cambió y el diagnóstico de origen desapareció tras revalidar.", "The model changed and the originating diagnostic disappeared after revalidating.") },
            { k: "APPLIED_UNRESOLVED", fd: T("El modelo cambió pero el diagnóstico persiste: la corrección fue insuficiente.", "The model changed but the diagnostic persists: the correction was insufficient.") },
            { k: "APPLIED_WITH_ERRORS", fd: T("Aplicación parcial propia de la transacción de mejor esfuerzo.", "Partial application typical of the best-effort transaction.") },
            { k: "NO_CHANGES", fd: T("La acción no modificó el modelo.", "The action did not modify the model.") },
            { k: "UNDONE / REDONE", fd: T("Reversión o reaplicación desde el historial, seguidas de revalidación.", "Reversion or reapplication from the history, followed by revalidation.") }
          ]
        },
        text_actions: {
          nf: T("Acción de código textual (corrección de gramáticas y modelos de texto)", "Textual code action (grammar and text model fix)"),
          fm: T("Subsistema de correcciones distinto, sobre diagnósticos de texto: cada propuesta lleva ediciones (documento, rango, texto de reemplazo) y marcas de preferida y de segura automática. Es independiente de las reglas de validación y de las acciones correctoras de modelos, sin puente entre ambos.",
                  "A different corrections subsystem, over text diagnostics: each proposal carries edits (document, range, replacement text) and preferred and safe-automatic flags. It is independent from validation rules and from model corrective actions, with no bridge between them."),
          tm: T("GrammarCodeAction/GrammarTextEdit aplicadas en el navegador (grammarCodeActions.ts); TextualQuickFixRuntimeService existe en servidor pero sin llamador en la interfaz ni deshacer. Endpoint POST /{grammarId}/code-actions.",
                  "GrammarCodeAction/GrammarTextEdit applied in the browser (grammarCodeActions.ts); TextualQuickFixRuntimeService exists on the server but with no UI caller or undo. Endpoint POST /{grammarId}/code-actions."),
          variants: [
            { fd: T("Propuesta de reescritura de una regla gramatical con recursión por la izquierda.", "Proposal to rewrite a grammar rule with left recursion.") },
            { fd: T("Sustitución de una referencia no definida por el nombre más parecido; solo es automática si la similitud es muy alta.", "Replacement of an undefined reference by the closest name; automatic only when similarity is very high.") },
            { fd: T("Correcciones locales: referencia indefinida con hasta cuatro candidatas, símbolo mal formado y punto y coma ausente.", "Local fixes: undefined reference with up to four candidates, malformed token and missing semicolon.") },
            { fd: T("Inserción o reemplazo guiado por metadatos de la sintaxis, aplicado en el cliente.", "Insertion or replacement guided by syntax metadata, applied on the client side.") }
          ]
        }
      }
    }
  },

  procs: {
    validation_define_rule: {
      nf: T("Alta de una regla verificada", "Registering a verified rule"),
      df: T("Se construye una regla en etapas (identidad, contexto, mensaje, disparos opcionales, condición, acciones correctoras opcionales); un control de coherencia devuelve al principio mientras existan defectos de diseño. Superado el control, se persisten en orden los documentos y, de forma opcional, se verifica la regla (guardada → verificada).",
          "A rule is built in stages (identity, context, message, optional triggers, condition, optional corrective actions); a coherence check loops back while design defects remain. Once passed, the documents are persisted in order and, optionally, the rule is verified (saved → verified)."),
      nt: T("ValidationDesigner: validar → guardar por etapas", "ValidationDesigner: validate → staged save"),
      dt: T("Proceso del cliente: ValidationRuleEditor edita la regla, validationDesignerValidation bloquea el guardado y ValidationDesigner persiste expresiones, acciones, comportamientos y validaciones con control de versión (ETag) por etapa. El backend hace la comprobación estática pero no rechaza por errores.",
          "Client-side process: ValidationRuleEditor edits the rule, validationDesignerValidation blocks saving and ValidationDesigner persists expressions, actions, behaviours and validations with per-stage version control (ETag). The backend runs the static check but does not reject on errors."),
      nodes: {
        meta: { lf: T("Resolver el metamodelo de referencia", "Resolve the reference metamodel"), sf: T("clases, atributos, relaciones → vocabulario de reglas", "classes, attributes, relations → rule vocabulary"), fio: T("Metamodelo guardado → vocabulario de tipos sobre el que se pueden formular reglas", "Saved metamodel → type vocabulary over which rules can be formulated") },
        id: { lf: T("Establecer identidad y criticidad", "Establish identity and criticality"), sf: T("nombre, código normalizado, gravedad", "name, normalized code, severity"), fio: T("Nombre, código y gravedad → regla con identidad única", "Name, code and severity → rule with unique identity"),
              lt: "ValidationRuleEditor", st: T("código forzado a [A-Z0-9_]", "code forced to [A-Z0-9_]"), tx: T("El campo del código normaliza la entrada a mayúsculas, dígitos y guion bajo; la prueba asociada es ValidationRuleEditor.help.test.tsx.", "The code field normalizes input to uppercase, digits and underscore; the associated test is ValidationRuleEditor.help.test.tsx.") },
        ctx: { lf: T("Fijar ámbito, modo y referencias al metamodelo", "Fix scope, mode and metamodel references"), sf: T("ámbito × modo → candidatos y vigilancia", "scope × mode → candidates and watching"), fio: T("Ámbito, modo y clase/atributo/relación → contexto coherente con el metamodelo", "Scope, mode and class/attribute/relation → context consistent with the metamodel"),
               lt: "ValidationModelChecker", st: T("comprobaciones por ámbito", "per-scope checks"), tx: T("ValidationModelChecker valida la presencia y pertenencia de clase, atributo y relación según el ValidationContextKind; cubierto por ValidationModelCheckerTests.", "ValidationModelChecker validates the presence and membership of class, attribute and relation according to ValidationContextKind; covered by ValidationModelCheckerTests.") },
        msg: { lf: T("Redactar la plantilla del diagnóstico", "Draft the diagnostic template"), sf: T("texto con marcadores contextuales", "text with contextual markers"), fio: T("Texto no vacío con marcadores → plantilla de mensaje de la regla", "Non-empty text with markers → rule message template") },
        trg: { lf: T("Declarar eventos de evaluación", "Declare evaluation events"), sf: T("disparos compatibles con el modo", "triggers compatible with the mode"), fio: T("Conjunto de eventos del ciclo de vida → disparos admisibles según el modo", "Set of lifecycle events → triggers admissible for the mode") },
        cnd: { lf: T("Formular el predicado de conformidad", "Formulate the conformance predicate"), sf: T("expresión booleana compartida", "shared boolean expression"), fio: T("Cláusulas o expresión → condición booleana referenciada por la regla", "Clauses or expression → boolean condition referenced by the rule") },
        qf: { lf: T("Vincular acciones correctoras", "Link corrective actions"), sf: T("0..N acciones existentes, sin repetir", "0..N existing actions, no repeats"), fio: T("Acciones del conjunto asociado → lista de correcciones ofrecidas por la regla", "Actions from the linked set → list of fixes offered by the rule") },
        blk: { lf: T("Control de coherencia del diseño", "Design coherence check"), sf: T("defectos → vuelta a la edición", "defects → back to editing"), fio: T("Regla y documentos → defectos detectados que impiden o permiten persistir", "Rule and documents → detected defects that prevent or allow persisting"),
               lt: "validationDesignerValidation", st: T("bloqueo solo en el cliente", "blocking on the client only"), tx: T("El bloqueo de guardado lo aplica ValidationDesigner en la interfaz (prueba validationDesignerValidation.test.ts); el backend no rechaza el guardado por errores.", "Save blocking is applied by ValidationDesigner in the interface (validationDesignerValidation.test.ts test); the backend does not reject the save on errors.") },
        sv: { lf: T("Persistir los documentos del diseño", "Persist the design documents"), sf: T("expresiones → acciones → comportamientos → validaciones", "expressions → actions → behaviours → validations"), fio: T("Diseño sin defectos → cuatro documentos persistidos en secuencia con control de concurrencia", "Defect-free design → four documents persisted in sequence with concurrency control"),
              lt: "ValidationDesigner (guardado ordenado)", st: T("ETag por etapa", "ETag per stage"), tx: T("ValidationDesigner guarda las etapas en orden con un ETag por etapa (ValidationDesigner.test.tsx).", "ValidationDesigner saves the stages in order with an ETag per stage (ValidationDesigner.test.tsx).") },
        chk: { lf: T("Verificar la definición completa", "Verify the complete definition"), sf: T("regla: guardada → verificada", "rule: saved → verified"), fio: T("Cuatro documentos guardados → diagnósticos de diseño y reglas marcadas como verificadas", "Four saved documents → design diagnostics and rules marked verified") },
        ok: { lf: T("Regla lista para su evaluación", "Rule ready for evaluation"), sf: T("regla activa y verificada", "active, verified rule"), fio: T("Regla persistida y verificada → regla elegible por el motor de ejecución", "Persisted, verified rule → rule eligible for the execution engine") }
      }
    },

    validation_execute: {
      nf: T("Evaluación de reglas y decisión de bloqueo", "Rule evaluation and blocking decision"),
      df: T("Una petición de validación fija el modo; se compila el documento en un plan y, si es ejecutable, se eligen candidatos por ámbito, se evalúa cada condición y se emiten diagnósticos. Opcionalmente el resultado se consolida con el informe previo (creado, sin cambio, resuelto), se publican eventos y se decide el bloqueo según la puerta. Termina como bloqueada, permitida o no ejecutable.",
          "A validation request fixes the mode; the document is compiled into a plan and, if executable, candidates are chosen by scope, each condition is evaluated and diagnostics are emitted. Optionally the result is consolidated with the previous report (created, unchanged, resolved), events are published and blocking is decided according to the gate. It ends as blocked, allowed or not executable."),
      nt: T("validate/orchestrate → compilar → ValidationEngine", "validate/orchestrate → compile → ValidationEngine"),
      dt: T("POST .../validations/runtime/validate (ValidationRuntimeController) y POST /api/models/{modelId}/validation-runtime/validate pasan por ValidationRuntimeApplicationService (ValidationCompiler y luego ValidationEngine). POST .../validations/runtime/orchestrate?gate= (por defecto NONE) añade ValidationOrchestrator con consolidación, DefaultValidationBlockingPolicy y eventos core.validation.*; la sesión del editor invoca directamente el servicio de ejecución, sin orquestador.",
          "POST .../validations/runtime/validate (ValidationRuntimeController) and POST /api/models/{modelId}/validation-runtime/validate go through ValidationRuntimeApplicationService (ValidationCompiler and then ValidationEngine). POST .../validations/runtime/orchestrate?gate= (default NONE) adds ValidationOrchestrator with consolidation, DefaultValidationBlockingPolicy and core.validation.* events; the editor session invokes the run service directly, without the orchestrator."),
      nodes: {
        mode: { lf: T("Seleccionar las reglas aplicables", "Select the applicable rules"), sf: T("modo × disparos → conjunto de reglas", "mode × triggers → rule set"), fio: T("Modo de validación y reglas declaradas → reglas que participan en esta ejecución", "Validation mode and declared rules → rules taking part in this run"),
                lt: "ValidationEngine (selección por modo)", st: T("modo y dependencias cambiadas", "mode and changed dependencies"), tx: T("ValidationEngine selecciona por modo; en FULL e INCREMENTAL ignora los triggers y para ON_OPEN, ON_SAVE, BEFORE_EXPORT y MANUAL filtra las reglas cuyo trigger coincide (ValidationEngineTests).", "ValidationEngine selects by mode; in FULL and INCREMENTAL it ignores triggers and for ON_OPEN, ON_SAVE, BEFORE_EXPORT and MANUAL it filters the rules whose trigger matches (ValidationEngineTests).") },
        comp: { lf: T("Derivar el plan de ejecución", "Derive the execution plan"), sf: T("documento → plan + exclusiones", "document → plan + exclusions"), fio: T("Documento de validación → plan ejecutable y avisos de las reglas excluidas", "Validation document → executable plan and notices for excluded rules"),
                lt: "ValidationCompiler.compile", st: T("plan cacheado por huella SHA-256", "plan cached by SHA-256 fingerprint"), tx: T("ValidationCompiler excluye reglas desactivadas, no canónicas o con errores de diseño; con errores y sin plan parcial permitido, ValidationRuntimeApplicationService devuelve NOT_EXECUTABLE (ValidationCompilerTests).", "ValidationCompiler excludes disabled, non-canonical or design-broken rules; with errors and no partial plan allowed, ValidationRuntimeApplicationService returns NOT_EXECUTABLE (ValidationCompilerTests).") },
        exec: { lf: T("Admisibilidad del plan", "Plan admissibility"), sf: T("ejecutable / no ejecutable", "executable / not executable"), fio: T("Plan compilado → continuar con la evaluación o cerrar como no ejecutable", "Compiled plan → continue with evaluation or close as not executable") },
        cand: { lf: T("Resolver el conjunto de candidatos", "Resolve the candidate set"), sf: T("ámbito → elementos a evaluar o caso único", "scope → elements to evaluate or single case"), fio: T("Ámbito de cada regla y modelo → elementos candidatos (o evaluación global única)", "Each rule's scope and model → candidate elements (or a single global evaluation)"),
                lt: "ValidationEngine (candidatos)", st: T("candidatos o caso global", "candidates or global case"), tx: T("ValidationEngine resuelve los candidatos por tipo de ámbito o trata el caso global (evidencia ValidationEngine.java líneas 215-267).", "ValidationEngine resolves candidates by scope kind or handles the global case (evidence ValidationEngine.java lines 215-267).") },
        eval: { lf: T("Evaluar la condición sobre cada candidato", "Evaluate the condition over each candidate"), sf: T("reglas × candidatos → veredicto", "rules × candidates → verdict"), fio: T("Condición booleana y candidato → conforme, incumplimiento o error de ejecución", "Boolean condition and candidate → conformant, violation or execution error") },
        diag: { lf: T("Materializar los hallazgos", "Materialize the findings"), sf: T("incumplimiento → diagnóstico con identidad estable", "violation → diagnostic with stable identity"), fio: T("Incumplimientos → diagnósticos con gravedad, mensaje, localización y correcciones", "Violations → diagnostics with severity, message, location and fixes"),
                lt: "ValidationEngine (diagnóstico)", st: T("marcadores + id estable", "markers + stable id"), tx: T("Sustituye los cinco marcadores del mensaje y calcula el identificador estable del diagnóstico (evidencia ValidationEngine.java líneas 269-288).", "Substitutes the five message markers and computes the diagnostic's stable identity (evidence ValidationEngine.java lines 269-288).") },
        cons: { lf: T("Comparar con el informe previo", "Compare with the previous report"), sf: T("creados / sin cambio / resueltos", "created / unchanged / resolved"), fio: T("Informe nuevo e informe anterior → delta y informe consolidado (reemplazo o fusión)", "New and previous report → delta and consolidated report (replace or merge)"),
                lt: "ValidationOrchestrator", st: T("solo vía /orchestrate", "only via /orchestrate"), tx: T("ValidationOrchestrator consolida el informe (ValidationOrchestratorTests); la sesión del editor invoca el motor directamente y no pasa por aquí.", "ValidationOrchestrator consolidates the report (ValidationOrchestratorTests); the editor session calls the engine directly and does not go through here.") },
        ev: { lf: T("Notificar la evolución de las violaciones", "Notify the evolution of violations"), sf: T("ejecución completada, violación creada/resuelta", "run completed, violation created/resolved"), fio: T("Informe consolidado → eventos de ejecución y de cambio de violaciones", "Consolidated report → run and violation-change events"),
              lt: "ValidationOrchestrator (eventos)", st: T("eventos core.validation.*", "core.validation.* events"), tx: T("ValidationOrchestrator publica los eventos core.validation.* al consolidar (ValidationOrchestratorTests).", "ValidationOrchestrator publishes the core.validation.* events on consolidation (ValidationOrchestratorTests).") },
        blk: { lf: T("Resolver la puerta de salida", "Resolve the exit gate"), sf: T("puerta × gravedad ERROR → permitir/bloquear", "gate × ERROR severity → allow/block"), fio: T("Informe y puerta (ninguna, guardar, exportar) → decisión de permitir o bloquear", "Report and gate (none, save, export) → allow or block decision"),
               lt: "DefaultValidationBlockingPolicy", st: T("solo ERROR bloquea", "only ERROR blocks"), tx: T("La política bloquea solo con diagnósticos ERROR y por defecto actúa sobre guardar y exportar (DefaultValidationBlockingPolicyTests).", "The policy blocks only on ERROR diagnostics and by default acts on save and export (DefaultValidationBlockingPolicyTests).") },
        rep: { lf: T("Informe de validación consolidado", "Consolidated validation report"), sf: T("diagnósticos + reglas ejecutadas/omitidas", "diagnostics + executed/skipped rules"), fio: T("Resultados de evaluación → informe con diagnósticos, reglas omitidas, métricas y decisión", "Evaluation results → report with diagnostics, skipped rules, metrics and decision") }
      }
    },

    validation_apply_quickfix: {
      nf: T("Aplicación reversible de una acción correctora", "Reversible application of a corrective action"),
      df: T("Desde un diagnóstico se listan las acciones aplicables, se elige una, se completan sus parámetros y opcionalmente se previsualiza. Si se confirma, se toma una instantánea, se ejecutan las operaciones y, según la política transaccional, un fallo revierte; si no, se revalida de forma incremental, se calcula el estado, se registra el historial de deshacer y se emiten eventos. Deshacer o rehacer reinician el ciclo de revalidación.",
          "From a diagnostic the applicable actions are listed, one is chosen, its parameters are completed and it is optionally previewed. If confirmed, a snapshot is taken, the operations run and, according to the transactional policy, a failure rolls back; otherwise the model is revalidated incrementally, the status is computed, the undo history is recorded and events are emitted. Undo or redo restart the revalidation cycle."),
      nt: T("/problems/{diagnosticId}/quick-fixes → QuickFixRuntimeService", "/problems/{diagnosticId}/quick-fixes → QuickFixRuntimeService"),
      dt: T("GET|POST /problems/{diagnosticId}/quick-fixes, .../preview y .../apply, más POST /quick-fixes/undo y /quick-fixes/redo; en la sesión del editor se aplica con If-Match (EditorModelSessionService, que exige la capacidad QUICK_FIX_RUNTIME). QuickFixRuntimeService orquesta y CanonicalActionExecutor ejecuta; la UI es ProblemsPanel.tsx. Ninguna prueba instancia QuickFixRuntimeService directamente.",
          "GET|POST /problems/{diagnosticId}/quick-fixes, .../preview and .../apply, plus POST /quick-fixes/undo and /quick-fixes/redo; in the editor session it is applied with If-Match (EditorModelSessionService, which requires the QUICK_FIX_RUNTIME capability). QuickFixRuntimeService orchestrates and CanonicalActionExecutor executes; the UI is ProblemsPanel.tsx. No test instantiates QuickFixRuntimeService directly."),
      nodes: {
        list: { lf: T("Determinar las acciones elegibles", "Determine the eligible actions"), sf: T("regla ∩ conjunto ∩ activa ∩ precondición", "rule ∩ set ∩ enabled ∩ precondition"), fio: T("Diagnóstico del último informe → acciones correctoras elegibles", "Diagnostic from the last report → eligible corrective actions"),
                lt: "QuickFixRuntimeService (aplicabilidad)", st: T("GET /problems/{id}/quick-fixes", "GET /problems/{id}/quick-fixes"), tx: T("QuickFixRuntimeService filtra por lo que la regla lista, existencia, activación y precondición; si el diagnóstico no está en el último informe lanza antes una validación completa (QuickFixContractsTests).", "QuickFixRuntimeService filters by what the rule lists, existence, enablement and precondition; if the diagnostic is not in the last report it first launches a full validation (QuickFixContractsTests).") },
        pick: { lf: T("Seleccionar la acción a aplicar", "Select the action to apply"), sf: T("una acción de la lista elegible", "one action from the eligible list"), fio: T("Lista de acciones elegibles → acción correctora seleccionada", "Eligible action list → selected corrective action") },
        par: { lf: T("Aportar los valores solicitados", "Provide the requested values"), sf: T("solo parámetros de origen «pedido»", "only parameters with an \"asked\" source"), fio: T("Parámetros declarados → argumentos de la aplicación (desconocidos o ausentes se rechazan)", "Declared parameters → application arguments (unknown or missing ones are rejected)") },
        pv: { lf: T("Simular el efecto sobre el modelo", "Simulate the effect on the model"), sf: T("copia aislada, sin revalidar", "isolated copy, no revalidation"), fio: T("Acción y argumentos → resumen de cambios sin alterar el modelo", "Action and arguments → change summary without altering the model"),
              lt: "QuickFixRuntimeService.preview", st: T("POST .../preview", "POST .../preview"), tx: T("La vista previa se ejecuta sobre una copia (QuickFixRuntimeService.java líneas 94-99); no hay pruebas que instancien este servicio.", "The preview runs on a copy (QuickFixRuntimeService.java lines 94-99); no tests instantiate this service.") },
        ok: { lf: T("Confirmar la aplicación", "Confirm the application"), sf: T("aplicar / abandonar", "apply / abandon"), fio: T("Resumen de cambios → decisión de aplicar o abandonar", "Change summary → decision to apply or abandon") },
        ap: { lf: T("Ejecutar la secuencia de operaciones", "Run the operation sequence"), sf: T("instantánea → operaciones en orden", "snapshot → operations in order"), fio: T("Acción, argumentos y modelo vivo → modelo modificado o restaurado según la política", "Action, arguments and live model → modified or restored model according to the policy"),
              lt: "CanonicalActionExecutor", st: T("política atómica o mejor esfuerzo", "atomic or best-effort policy"), tx: T("CanonicalActionExecutor valida argumentos y precondición, ejecuta cada operación y aplica la política transaccional (OperationActionExecutionTests, CanonicalActionControllerTests).", "CanonicalActionExecutor validates arguments and precondition, runs each operation and applies the transactional policy (OperationActionExecutionTests, CanonicalActionControllerTests).") },
        fail: { lf: T("Detectar el fallo de ejecución", "Detect the execution failure"), sf: T("fallo → restaurar / sin fallo → seguir", "failure → restore / no failure → continue"), fio: T("Resultado de la ejecución → rama de reversión o de revalidación", "Execution outcome → rollback or revalidation branch") },
        rb: { lf: T("Restaurar el estado previo", "Restore the previous state"), sf: T("instantánea → modelo original", "snapshot → original model"), fio: T("Fallo de ejecución → modelo sin cambios y notificación de fallo", "Execution failure → unchanged model and failure notification"),
              lt: "QuickFixRuntimeService (rollback)", st: T("evento core.quickfix.failed", "core.quickfix.failed event"), tx: T("QuickFixRuntimeService restaura la instantánea y publica core.quickfix.failed (líneas 122-123); sin pruebas directas.", "QuickFixRuntimeService restores the snapshot and publishes core.quickfix.failed (lines 122-123); no direct tests.") },
        rv: { lf: T("Reevaluar el alcance afectado", "Re-evaluate the affected scope"), sf: T("diagnóstico: abierto → resuelto", "diagnostic: open → resolved"), fio: T("Modelo modificado → informe revalidado con reglas dependientes del cambio", "Modified model → report revalidated with change-dependent rules") },
        st: { lf: T("Clasificar el resultado de la corrección", "Classify the fix outcome"), sf: T("resuelta / sin resolver / con errores / sin cambios", "resolved / unresolved / with errors / no changes"), fio: T("Cambios y diagnóstico revalidado → estado de aplicación", "Changes and revalidated diagnostic → application status"),
              lt: "QuickFixApplicationStatus", st: T("calculado en QuickFixRuntimeService", "computed in QuickFixRuntimeService"), tx: T("Los estados APPLIED_RESOLVED, APPLIED_UNRESOLVED, APPLIED_WITH_ERRORS y NO_CHANGES se asignan en QuickFixRuntimeService (líneas 236-240).", "The statuses APPLIED_RESOLVED, APPLIED_UNRESOLVED, APPLIED_WITH_ERRORS and NO_CHANGES are assigned in QuickFixRuntimeService (lines 236-240).") },
        hist: { lf: T("Registrar el punto de reversión", "Record the reversion point"), sf: T("historial acotado; rehacer se vacía", "bounded history; redo is cleared"), fio: T("Aplicación con cambios y política admisible → entrada en el historial de deshacer", "Application with changes and admissible policy → entry in the undo history"),
                tx: T("El deshacer queda disponible si hubo cambios y la política no es NOT_SUPPORTED; REQUIRED y OPTIONAL se comportan igual.", "Undo becomes available if there were changes and the policy is not NOT_SUPPORTED; REQUIRED and OPTIONAL behave the same.") },
        evq: { lf: T("Difundir el ciclo de la corrección", "Broadcast the fix lifecycle"), sf: T("iniciada, aplicada (fallida/deshecha/rehecha)", "started, applied (failed/undone/redone)"), fio: T("Resultado de la aplicación → eventos de corrección para otras capacidades", "Application outcome → fix events for other capabilities"),
              tx: T("Se publican core.quickfix.started / applied / failed / undone / redone.", "core.quickfix.started / applied / failed / undone / redone are published.") },
        undo: { lf: T("Revertir o reaplicar la corrección", "Revert or reapply the fix"), sf: T("aplicada → deshecha → rehecha", "applied → undone → redone"), fio: T("Historial de deshacer/rehacer → modelo revertido o repetido y revalidado", "Undo/redo history → reverted or repeated and revalidated model"),
               st: T("POST /quick-fixes/undo|redo", "POST /quick-fixes/undo|redo") },
        res: { lf: T("Modelo corregido y estado de diagnósticos", "Corrected model and diagnostic status"), sf: T("modelo + informe revalidado", "model + revalidated report"), fio: T("Aplicación completada → modelo corregido con diagnósticos actualizados", "Completed application → corrected model with updated diagnostics") }
      }
    },

    validation_define_quickfix: {
      nf: T("Definición de una acción correctora reutilizable", "Defining a reusable corrective action"),
      df: T("Se compone una acción a partir de su identidad, política de seguridad, precondición y parámetros opcionales y una o más operaciones encadenadas; después se asocia a las reglas que deben ofrecerla. Una comprobación opcional detecta acciones inexistentes, precondiciones inválidas y recursión directa.",
          "An action is composed from its identity, safety policy, optional precondition and parameters and one or more chained operations; it is then linked to the rules that must offer it. An optional check detects missing actions, invalid preconditions and direct recursion."),
      nt: T("QuickFixDesigner → ActionModel → ActionModelChecker", "QuickFixDesigner → ActionModel → ActionModelChecker"),
      dt: T("Edición en el cliente (QuickFixDesigner, validationDesignerFactory, validationDesignerValidation) sobre el ActionModel, que se guarda junto al resto del diseño; ActionModelChecker comprueba las precondiciones. La asociación a la regla guarda quickFixIds en ValidationRule.",
          "Client-side editing (QuickFixDesigner, validationDesignerFactory, validationDesignerValidation) over the ActionModel, saved alongside the rest of the design; ActionModelChecker checks the preconditions. The link to the rule stores quickFixIds in ValidationRule."),
      nodes: {
        id: { lf: T("Identificar la acción correctora", "Identify the corrective action"), sf: T("nombre obligatorio, descripción opcional", "mandatory name, optional description"), fio: T("Nombre y descripción → acción con identidad", "Name and description → action with identity") },
        pol: { lf: T("Definir la política de seguridad", "Define the safety policy"), sf: T("transacción × reversibilidad", "transaction × reversibility"), fio: T("Elección transaccional y de deshacer → política (por defecto atómica, deshacer obligatorio)", "Transactional and undo choice → policy (default atomic, undo required)") },
        pre: { lf: T("Condicionar su oferta", "Condition its offer"), sf: T("predicado booleano opcional", "optional boolean predicate"), fio: T("Expresión existente → precondición que debe cumplirse para ofrecerla", "Existing expression → precondition that must hold for it to be offered") },
        par: { lf: T("Declarar los datos de entrada", "Declare the input data"), sf: T("nombre, tipo, obligatoriedad", "name, type, requiredness"), fio: T("Datos que requiere el arreglo → parámetros tipados de la acción", "Data the fix requires → typed action parameters") },
        op: { lf: T("Componer las operaciones elementales", "Compose the elementary operations"), sf: T("10 primitivas, selector y origen del valor", "10 primitives, selector and value source"), fio: T("Primitiva elegida y sus datos → operación ordenada de la acción", "Chosen primitive and its data → ordered operation of the action") },
        more: { lf: T("¿Falta algún paso del arreglo?", "Is any fix step missing?"), sf: T("encadenar más operaciones o cerrar", "chain more operations or close"), fio: T("Acción parcial → nueva operación o cierre de la secuencia", "Partial action → new operation or closing of the sequence") },
        lnk: { lf: T("Vincular con las reglas ofertantes", "Link with the offering rules"), sf: T("regla ↔ acción sin duplicados", "rule ↔ action without duplicates"), fio: T("Acción y reglas → vínculo regla-acción (una regla no repite acción)", "Action and rules → rule-action link (a rule does not repeat an action)") },
        chk: { lf: T("Verificar la coherencia de la acción", "Verify the action's coherence"), sf: T("referencias, precondición, recursión", "references, precondition, recursion"), fio: T("Acción y conjunto de acciones → defectos de definición detectados", "Action and action set → detected definition defects"),
               lt: "ActionModelChecker", st: T("comprobaciones de precondición", "precondition checks"), tx: T("ActionModelChecker valida la precondición (ActionModelCheckerTests; evidencia en ActionModelChecker.java líneas 109-117).", "ActionModelChecker validates the precondition (ActionModelCheckerTests; evidence in ActionModelChecker.java lines 109-117).") },
        art: { lf: T("Acción correctora disponible", "Corrective action available"), sf: T("definida, vinculada, ofrecida", "defined, linked, offered"), fio: T("Acción vinculada → acción ofrecida por las reglas ante sus diagnósticos", "Linked action → action offered by the rules on their diagnostics") }
      }
    },

    validation_author_guided: {
      nf: T("Creación guiada de una regla en seis etapas", "Guided creation of a rule in six stages"),
      df: T("Una regla nace con valores por defecto (ámbito de clase, gravedad ERROR, modo incremental, disparos al guardar y manual, condición verdadera) y se completa por etapas: identidad, contexto (con referencia opcional a atributo o relación), diagnóstico y disparos, condición guiada, acciones correctoras y revisión. Un control de coherencia devuelve a la primera etapa; si no hay defectos se persiste.",
          "A rule is born with defaults (class scope, ERROR severity, incremental mode, on-save and manual triggers, true condition) and is completed in stages: identity, context (with optional attribute or relation reference), diagnostic and triggers, guided condition, corrective actions and review. A coherence check returns to the first stage; if there are no defects it is persisted."),
      nt: T("ValidationRuleManager + ValidationRuleEditor (6 pasos)", "ValidationRuleManager + ValidationRuleEditor (6 steps)"),
      dt: T("Interfaz del cliente: ValidationRuleManager crea la regla, ValidationRuleEditor aloja los seis pasos, GuidedConditionBuilder la condición, validationDesignerFactory los valores por defecto y ValidationDesigner el guardado («Guardar validación»: crea si no hay versión previa, reemplaza si la hay). No hay editor textual de reglas.",
          "Client UI: ValidationRuleManager creates the rule, ValidationRuleEditor hosts the six steps, GuidedConditionBuilder the condition, validationDesignerFactory the defaults and ValidationDesigner the save (\"Save validation\": creates if no previous version, replaces otherwise). There is no textual rule editor."),
      nodes: {
        id: { lf: T("Etapa 1: identidad de la regla", "Stage 1: rule identity"), sf: T("nombre, código, gravedad, grupo, activa", "name, code, severity, group, active"), fio: T("Datos de identidad → regla con identidad y criticidad", "Identity data → rule with identity and criticality") },
        ctx: { lf: T("Etapa 2: contexto de evaluación", "Stage 2: evaluation context"), sf: T("modo, ámbito, clase y rasgo objetivo", "mode, scope, class and target feature"), fio: T("Modo y ámbito elegidos → contexto de evaluación de la regla", "Chosen mode and scope → the rule's evaluation context") },
        dec: { lf: T("¿El ámbito exige atributo o relación?", "Does the scope require an attribute or relation?"), sf: T("solo atributo y relación lo requieren", "only attribute and relation require it"), fio: T("Ámbito elegido → necesidad o no de referencia fina al metamodelo", "Chosen scope → need or not for a fine-grained metamodel reference") },
        tar: { lf: T("Precisar el rasgo objetivo", "Pin down the target feature"), sf: T("miembro de la clase elegida", "member of the chosen class"), fio: T("Clase del ámbito → atributo o relación perteneciente a ella", "Scope class → attribute or relation belonging to it") },
        dg: { lf: T("Etapa 3: mensaje y eventos de evaluación", "Stage 3: message and evaluation events"), sf: T("plantilla + disparos", "template + triggers"), fio: T("Texto con marcadores y disparos marcados → mensaje y eventos de la regla", "Text with markers and ticked triggers → the rule's message and events") },
        cn: { lf: T("Etapa 4: condición por cláusulas", "Stage 4: clause-based condition"), sf: T("comprobaciones combinadas con Y / O / NO", "checks combined with AND / OR / NOT"), fio: T("Cláusulas de atributo, relaciones o instancias → predicado booleano", "Attribute, relation or instance clauses → boolean predicate"),
              lt: "GuidedConditionBuilder", st: T("constructor de cláusulas del cliente", "client-side clause builder"), tx: T("Las comprobaciones guiadas se construyen con GuidedConditionBuilder y se guardan como expresión (ValidationRuleManager.test.tsx).", "Guided checks are built with GuidedConditionBuilder and saved as an expression (ValidationRuleManager.test.tsx).") },
        qf: { lf: T("Etapa 5: acciones correctoras ofrecidas", "Stage 5: offered corrective actions"), sf: T("selección entre acciones existentes", "selection among existing actions"), fio: T("Acciones disponibles → correcciones asociadas a la regla", "Available actions → corrections tied to the rule") },
        rv: { lf: T("Etapa 6: resumen previo a la persistencia", "Stage 6: pre-persistence summary"), sf: T("lectura global de la regla", "overall reading of the rule"), fio: T("Regla completa → resumen para confirmar el alta", "Complete rule → summary to confirm registration") },
        ok: { lf: T("Control de coherencia antes de persistir", "Coherence check before persisting"), sf: T("defectos → volver a la identidad", "defects → back to identity"), fio: T("Regla y documentos → permiso o bloqueo de la persistencia", "Rule and documents → permission or blocking of persistence") },
        sv: { lf: T("Diseño de validación persistido", "Validation design persisted"), sf: T("alta o reemplazo del documento", "creation or replacement of the document"), fio: T("Regla sin defectos → documento de validación almacenado", "Defect-free rule → stored validation document") }
      }
    },

    validation_author_form: {
      nf: T("Edición avanzada de una regla en una sola vista", "Advanced editing of a rule in a single view"),
      df: T("Todas las secciones de la regla se editan a la vez: identidad y contexto, disparos y mensaje, condición mediante el árbol de expresión completo, y opcionalmente la representación original y el estado de conversión, junto con las acciones correctoras. Es el único camino que toca la representación y la conversión. Un control de coherencia decide si se persiste o se vuelve a editar.",
          "All the rule's sections are edited at once: identity and context, triggers and message, condition through the full expression tree, and optionally the original representation and conversion status, together with the corrective actions. It is the only path that touches representation and conversion. A coherence check decides whether to persist or return to editing."),
      nt: T("ValidationRuleEditor (modo ADVANCED) + ExpressionBuilder", "ValidationRuleEditor (ADVANCED mode) + ExpressionBuilder"),
      dt: T("Cliente: ValidationRuleEditor en modo ADVANCED con ExpressionBuilder para el árbol de 13 tipos de nodo; el guardado es el mismo de ValidationDesigner. Las opciones de representación son AUTHORITATIVE_OPTIONS (sin JAVASCRIPT ni BLOCKLY).",
          "Client: ValidationRuleEditor in ADVANCED mode with ExpressionBuilder for the 13-node-kind tree; saving is the same ValidationDesigner one. Representation options are AUTHORITATIVE_OPTIONS (without JAVASCRIPT or BLOCKLY)."),
      nodes: {
        idc: { lf: T("Identidad y contexto de la regla", "Rule identity and context"), sf: T("gravedad, grupo, ámbito, modo", "severity, group, scope, mode"), fio: T("Datos de identidad y contexto → regla con clasificación completa", "Identity and context data → rule with full classification") },
        tg: { lf: T("Eventos de evaluación y plantilla de mensaje", "Evaluation events and message template"), sf: T("diez disparos, cinco sin efecto", "ten triggers, five without effect"), fio: T("Disparos elegidos y texto → eventos declarados y mensaje de la regla", "Chosen triggers and text → declared events and the rule's message") },
        ex: { lf: T("Construir la expresión de conformidad", "Build the conformance expression"), sf: T("árbol general de expresión booleana", "general boolean expression tree"), fio: T("Nodos de expresión → condición booleana arbitraria", "Expression nodes → arbitrary boolean condition"),
              lt: "ExpressionBuilder", st: T("árbol de 13 tipos de nodo", "tree of 13 node kinds"), tx: T("ExpressionBuilder construye el árbol de expresión (ExpressionBuilder.test.tsx; evidencia en ExpressionBuilder.tsx líneas 87-120).", "ExpressionBuilder builds the expression tree (ExpressionBuilder.test.tsx; evidence in ExpressionBuilder.tsx lines 87-120).") },
        rp: { lf: T("Declarar la verdad de la regla importada", "Declare the imported rule's truth"), sf: T("representación × estado de conversión", "representation × conversion status"), fio: T("Origen y fidelidad de la traducción → pareja representación/conversión coherente", "Origin and translation fidelity → consistent representation/conversion pair") },
        qf: { lf: T("Seleccionar las acciones correctoras", "Select the corrective actions"), sf: T("lista de acciones existentes", "list of existing actions"), fio: T("Acciones disponibles → correcciones asociadas a la regla", "Available actions → corrections tied to the rule") },
        ok: { lf: T("Control de coherencia antes de persistir", "Coherence check before persisting"), sf: T("defectos → volver a editar", "defects → back to editing"), fio: T("Regla completa → permiso o bloqueo de la persistencia", "Complete rule → permission or blocking of persistence") },
        sv: { lf: T("Regla incorporada al documento de validación", "Rule incorporated into the validation document"), sf: T("regla persistida", "persisted rule"), fio: T("Regla sin defectos → documento de validación actualizado", "Defect-free rule → updated validation document") }
      }
    },

    validation_author_quickfix: {
      nf: T("Creación guiada de una acción correctora en cinco etapas", "Guided creation of a corrective action in five stages"),
      df: T("Una acción nace activa, atómica, con deshacer obligatorio y sin operaciones, y se completa en cinco etapas: identidad, seguridad (con precondición opcional), parámetros opcionales, operaciones y revisión. Cada operación se especifica según su primitiva: selector de destino, origen del valor si asigna o copia y argumentos si compone otra acción o invoca una operación. Debe tener al menos una operación para ser válida.",
          "An action is born enabled, atomic, with undo required and no operations, and is completed in five stages: identity, safety (with optional precondition), optional parameters, operations and review. Each operation is specified according to its primitive: target selector, value source if it assigns or copies, and arguments if it composes another action or invokes an operation. It needs at least one operation to be valid."),
      nt: T("QuickFixDesigner (OperationEditor) + validación de diseño", "QuickFixDesigner (OperationEditor) + design validation"),
      dt: T("Cliente: QuickFixDesigner con OperationEditor, valores por defecto de validationDesignerFactory y reglas de validationDesignerValidation (nombre, al menos una operación; una acción importada solo para revisión debe estar desactivada).",
          "Client: QuickFixDesigner with OperationEditor, defaults from validationDesignerFactory and rules from validationDesignerValidation (name, at least one operation; an imported review-only action must be disabled)."),
      nodes: {
        id: { lf: T("Etapa 1: identidad de la acción", "Stage 1: action identity"), sf: T("nombre, descripción, activa", "name, description, enabled"), fio: T("Datos de identidad → acción identificable", "Identity data → identifiable action") },
        sf: { lf: T("Etapa 2: política de seguridad y precondición", "Stage 2: safety policy and precondition"), sf: T("transacción, reversibilidad, predicado", "transaction, reversibility, predicate"), fio: T("Decisiones de seguridad → política y precondición opcional", "Safety decisions → policy and optional precondition") },
        pr: { lf: T("Etapa 3: parámetros de la acción", "Stage 3: action parameters"), sf: T("nuevo parámetro: texto obligatorio", "new parameter: required text"), fio: T("Datos requeridos → parámetros con nombre y tipo", "Required data → parameters with name and type") },
        op: { lf: T("Etapa 4: secuencia de operaciones", "Stage 4: operation sequence"), sf: T("primitiva y sus campos", "primitive and its fields"), fio: T("Primitiva elegida → operación con los campos propios de su clase", "Chosen primitive → operation with the fields of its own kind") },
        kd: { lf: T("¿Qué primitiva se compone?", "Which primitive is composed?"), sf: T("los campos dependen de la primitiva", "fields depend on the primitive"), fio: T("Primitiva elegida → conjunto de campos obligatorios y opcionales", "Chosen primitive → set of mandatory and optional fields") },
        sl: { lf: T("Resolver el elemento objetivo", "Resolve the target element"), sf: T("cinco formas de designar el destino", "five ways to designate the target"), fio: T("Contexto del diagnóstico o selección → elemento sobre el que actúa la operación", "Diagnostic or selection context → element on which the operation acts") },
        vs: { lf: T("Determinar el valor que se escribe", "Determine the value to write"), sf: T("constante, expresión o dato pedido", "constant, expression or asked datum"), fio: T("Fuente de valor elegida → valor para asignar o copiar", "Chosen value source → value to set or copy") },
        ag: { lf: T("Pasar datos a la acción o comando invocado", "Pass data to the invoked action or command"), sf: T("argumentos de la composición", "arguments of the composition"), fio: T("Valores de entrada → argumentos para la acción o la operación invocada", "Input values → arguments for the invoked action or operation") },
        rv: { lf: T("Etapa 5: resumen de la acción", "Stage 5: action summary"), sf: T("lectura global antes de persistir", "overall reading before persisting"), fio: T("Acción completa → resumen para confirmar", "Complete action → summary to confirm") },
        ok: { lf: T("Admisibilidad de la definición", "Admissibility of the definition"), sf: T("nombre, ≥1 operación, revisión desactivada", "name, ≥1 operation, review-only disabled"), fio: T("Acción completa → decisión de persistir o volver a editar", "Complete action → decision to persist or return to editing"),
              lt: "validationDesignerValidation", st: T("regla de revisión: desactivada", "review-only rule: disabled"), tx: T("validationDesignerValidation exige que una acción importada solo para revisión esté desactivada (validationDesignerValidation.ts líneas 115-117; validationDesignerValidation.test.ts).", "validationDesignerValidation requires an imported review-only action to be disabled (validationDesignerValidation.ts lines 115-117; validationDesignerValidation.test.ts).") },
        sv: { lf: T("Acción incorporada al conjunto de acciones", "Action incorporated into the action set"), sf: T("acción persistida", "persisted action"), fio: T("Acción válida → conjunto de acciones actualizado", "Valid action → updated action set") }
      }
    },

    validation_author_import: {
      nf: T("Incorporación de reglas y correcciones externas", "Incorporating external rules and fixes"),
      df: T("Se aporta un fichero de otra herramienta (reglas OCL, EVL, ATL o formato propio; correcciones EVL, EOL o formato propio) y se convierte solo el subconjunto demostrablemente equivalente. La regla con conversión completa pasa a ser ejecutable; si es parcial, externa o no soportada se conserva para revisión sin ejecutarse. Las correcciones se enlazan con las reglas por nombre o código, informando de los enlaces no resueltos, y pueden revisarse antes de persistir.",
          "A file from another tool is supplied (rules in OCL, EVL, ATL or native format; fixes in EVL, EOL or native format) and only the provably equivalent subset is converted. A rule with full conversion becomes executable; if partial, external or unsupported it is kept for review without being executed. Fixes are linked to rules by name or code, reporting unresolved links, and can be reviewed before persisting."),
      nt: T("ValidationDesigner (importación) → SafeExternalConstraintTranslator", "ValidationDesigner (import) → SafeExternalConstraintTranslator"),
      dt: T("Cliente: ValidationDesigner (importación y exportación) con quickFixInterchange para las correcciones. Traducción de restricciones en SafeExternalConstraintTranslator (subconjunto: comparaciones, Y/O/NO, nulo, longitud, recorte); el origen se guarda en ImportedValidationSource. Sin pruebas propias del traductor en este modelo.",
          "Client: ValidationDesigner (import and export) with quickFixInterchange for fixes. Constraint translation in SafeExternalConstraintTranslator (subset: comparisons, AND/OR/NOT, null, length, trim); the origin is kept in ImportedValidationSource. No dedicated translator tests in this model."),
      nodes: {
        ex: { lf: T("Contenido de la otra herramienta", "Content from the other tool"), sf: T("reglas y correcciones por lenguaje de origen", "rules and fixes by source language"), fio: T("Fichero en lenguaje externo → contenido a convertir con su origen", "File in an external language → content to convert with its origin") },
        cv: { lf: T("Traducir el subconjunto equivalente", "Translate the equivalent subset"), sf: T("equivalencia demostrable → regla canónica", "provable equivalence → canonical rule"), fio: T("Restricción externa → regla canónica con conversión completa o resultado no traducible", "External constraint → canonical rule with full conversion or untranslatable result"),
              lt: "SafeExternalConstraintTranslator", st: T("traducción solo si es demostrable", "translation only if provable"), tx: T("SafeExternalConstraintTranslator traduce comparaciones, Y/O/NO, nulo, longitud y recorte, y solo si puede probar la equivalencia (evidencia líneas 35-51; sin pruebas directas en el modelo).", "SafeExternalConstraintTranslator translates comparisons, AND/OR/NOT, null, length and trim, and only if equivalence can be proven (evidence lines 35-51; no direct tests in the model).") },
        dc: { lf: T("¿Se alcanzó conversión completa?", "Was full conversion reached?"), sf: T("completa → ejecutable; resto → conservada", "full → executable; otherwise → kept"), fio: T("Resultado de la traducción → regla ejecutable o regla preservada", "Translation result → executable rule or preserved rule") },
        pt: { lf: T("Preservar la regla sin ejecutarla", "Preserve the rule without executing it"), sf: T("conversión parcial, externa o no soportada", "partial, external or unsupported conversion"), fio: T("Regla no convertible → regla conservada con estado y origen, omitida al ejecutar", "Non-convertible rule → rule kept with status and origin, skipped at execution") },
        lk: { lf: T("Resolver los vínculos regla-corrección", "Resolve the rule-fix links"), sf: T("por nombre o código; avisos de no resueltos", "by name or code; unresolved warnings"), fio: T("Correcciones y reglas importadas → vínculos resueltos e informe de los pendientes", "Imported fixes and rules → resolved links and a report of pending ones") },
        rv: { lf: T("Completar las reglas importadas", "Complete the imported rules"), sf: T("edición con las vistas nativas", "editing with the native views"), fio: T("Regla importada → regla completada con las vistas guiada o avanzada", "Imported rule → rule completed with the guided or advanced views") },
        sv: { lf: T("Contenido importado incorporado al diseño", "Imported content incorporated into the design"), sf: T("reglas y acciones persistidas", "persisted rules and actions"), fio: T("Reglas y correcciones resueltas → diseño de validación ampliado", "Resolved rules and fixes → extended validation design") }
      }
    }
  }
});
})();
