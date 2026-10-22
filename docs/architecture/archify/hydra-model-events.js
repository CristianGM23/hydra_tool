(function () {
  "use strict";
  var HM = window.HM, T = HM.T;
  var EV = "src/main/java/com/example/editor/event/";
  var SESS = "src/main/java/com/example/editor/definition/runtime/session/";
  var FE = "frontend/src/event-designer/";
  function ev(p, l, label) { return { path: p, line: l, label: label }; }
  function V(nes, nen, des, den, req) { return { n: T(nes, nen), d: T(des, den), req: req || "ALTERNATIVE" }; }
  function P(nes, nen, req, des, den) { return { n: T(nes, nen), req: req, d: T(des, den) }; }
  function N(id, t, a, r, les, len, ues, uen, o) {
    var n = { id: id, t: t, cap: (o && o.cap) || "events", l: T(les, len) };
    if (a) n.a = a;
    if (r) n.r = r;
    if (ues) n.u = T(ues, uen);
    if (o) {
      if (o.need) n.need = T(o.need[0], o.need[1]);
      if (o.out) n.out = T(o.out[0], o.out[1]);
      if (o.f) n.f = T(o.f[0], o.f[1]);
      if (o.s) n.s = T(o.s[0], o.s[1]);
      if (o.e) n.e = o.e;
      if (o.x) n.x = o.x;
    }
    return n;
  }

  var FLOW_RT = EV + "flow/runtime/EventFlowRuntimeService.java";
  var NODE_EX = EV + "flow/runtime/EventFlowRuntimeNodeExecutor.java";
  var SUPPORT = EV + "flow/runtime/EventFlowRuntimeSupport.java";
  var CHECKER = EV + "flow/EventFlowChecker.java";
  var NODEKIND = EV + "flow/FlowNodeKind.java";

  HM.cap("events", {
    name: T("Eventos y flujos", "Events & flows"),
    group: "behavior",
    user: {
      tagline: T("Reaccionar a lo que ocurre en HYDRA", "React to what happens in HYDRA"),
      what: T("Permite describir los hechos que ocurren (un modelo se guarda, un diagnóstico aparece, una corrección se aplica) como tipos de evento con sus datos, enlazar unos eventos con otros mediante reglas de reacción y diseñar flujos: diagramas de pasos que se ponen en marcha cuando llega un evento y que pueden decidir, esperar, repetir, ejecutar acciones y emitir nuevos eventos.",
        "Lets you describe the facts that happen (a model is saved, a diagnostic appears, a fix is applied) as event types with their data, link events together with reaction rules, and design flows: step diagrams that start when an event arrives and can decide, wait, retry, run actions and emit new events."),
      why: T("Para automatizar reacciones sin escribir código: encadenar comportamientos entre capacidades (validación, gamificación, acciones del editor) y dejar una traza de lo que pasó en cada ejecución.",
        "To automate reactions without writing code: chain behaviours between capabilities (validation, gamification, editor actions) and leave a trace of what happened in each run."),
      when: T("Cuando quieres que algo ocurra como consecuencia de otra cosa: avisar al guardar, otorgar un logro tras corregir errores, esperar a que lleguen dos eventos antes de actuar, o detectar que algo no llegó a tiempo.",
        "When you want something to happen as a consequence of something else: notify on save, grant an achievement after fixing errors, wait for two events before acting, or detect that something did not arrive in time."),
      canDefine: [
        T("Tipos de evento con sus campos de datos", "Event types with their data fields"),
        T("Reglas de enlace entre un evento y otro, con condición y copia de datos", "Binding rules from one event to another, with condition and data copy"),
        T("Flujos con variables, pasos y transiciones", "Flows with variables, steps and transitions"),
        T("Disparadores de flujo (qué eventos lo arrancan)", "Flow triggers (which events start it)"),
        T("Reintentos y límites de tiempo por paso", "Retries and time limits per step"),
        T("Enlaces de compensación (deshacer un paso si otro falla)", "Compensation links (undo a step if another fails)"),
        T("Qué hacer cuando un enlace falla (omitir, avisar o desactivar)", "What to do when a binding fails (skip, report or disable)")
      ],
      needs: [
        T("Un metamodelo guardado sobre el que definir los eventos", "A saved metamodel to define the events over"),
        T("Para las condiciones, un modelo de expresiones (opcional)", "For conditions, an expressions model (optional)"),
        T("Para pasos de acción u operación, que existan esas acciones u operaciones", "For action or operation steps, those actions or operations must exist"),
        T("Para ver reacciones reales, una sesión del editor que genere eventos", "To see real reactions, an editor session that produces events")
      ],
      produces: [
        T("Un modelo de eventos con sus tipos y enlaces", "An event model with its types and bindings"),
        T("Un modelo de flujos con su diagrama", "A flow model with its diagram"),
        T("Eventos derivados emitidos por enlaces y flujos", "Derived events emitted by bindings and flows"),
        T("Trazas de ejecución de cada flujo", "Execution traces of each flow"),
        T("Diagnósticos de comprobación (errores, avisos, información)", "Check diagnostics (errors, warnings, information)")
      ],
      mandatory: [
        T("Tipo de evento: identificador, nombre y espacio de nombres", "Event type: identifier, name and namespace"),
        T("Enlace: evento de origen y evento de destino propio", "Binding: source event and own target event"),
        T("Flujo: un identificador y exactamente un punto de entrada y al menos un final", "Flow: an identifier, exactly one entry point and at least one end"),
        T("Paso: identificador y tipo de paso", "Step: identifier and step type")
      ],
      optional: [
        T("Campos de datos del evento, descripción, categoría, versión", "Event data fields, description, category, version"),
        T("Condición del enlace y copia de datos entre eventos", "Binding condition and data copy between events"),
        T("Variables del flujo, reintentos, límites de tiempo, compensaciones", "Flow variables, retries, time limits, compensations")
      ],
      conditional: [
        T("Tipo enumerado del campo (solo si el campo es de tipo enumerado)", "Enumeration of the field (only if the field is an enumeration)"),
        T("Tipo de objeto del campo (solo si el campo es un objeto)", "Object type of the field (only if the field is an object)"),
        T("Evento del paso (solo en pasos que lo necesitan: entrada, evento, espera, emisión, ausencia)", "Step event (only for steps that need one: source, event, wait, emit, absence)"),
        T("Transición de tiempo agotado (solo si el paso tiene un límite de tiempo)", "Timeout transition (only if the step has a time limit)")
      ],
      next: [
        T("Comprobar el modelo de eventos y el de flujos", "Check the event model and the flow model"),
        T("Arrancar el motor de flujos y enviar un evento de prueba", "Start the flow engine and send a test event"),
        T("Revisar las trazas de ejecución", "Review the execution traces"),
        T("Usar los eventos en reglas de gamificación", "Use the events in gamification rules")
      ],
      interacts: [
        { cap: "model_editor", t: T("Cada cambio en una sesión de edición emite eventos", "Every change in an editing session emits events") },
        { cap: "gamification", t: T("Escucha los mismos eventos y emite los suyos", "Listens to the same events and emits its own") },
        { cap: "validation", t: T("Las validaciones y las correcciones producen eventos reservados", "Validations and fixes produce reserved events") },
        { cap: "metamodel", t: T("Los eventos se definen sobre un metamodelo", "Events are defined over a metamodel") }
      ]
    },
    func: {
      lifecycle: [
        T("tipo de evento definido → comprobado → guardado", "event type defined → checked → saved"),
        T("enlace creado (activo, sin copia de datos) → configurado → comprobado → guardado", "binding created (enabled, no data copy) → configured → checked → saved"),
        T("flujo diseñado → comprobado sin errores → compilado → activo (al arrancar o al llegar el primer evento)", "flow designed → checked without errors → compiled → active (on start or at the first event)"),
        T("evento llega → se eligen los flujos → se recorren los pasos → se guarda la traza → se emiten eventos derivados", "event arrives → flows are picked → steps are walked → trace is saved → derived events are emitted"),
        T("un flujo detenido a mano no se reactiva solo", "a flow engine stopped by hand does not reactivate itself")
      ],
      semantics: [
        T("Un evento canónico lleva siempre un origen, un contexto (de dónde viene), opcionalmente un actor y un sujeto, y datos propios; los identificadores de correlación permiten seguir una cadena de reacciones.", "A canonical event always carries a source, a context (where it comes from), optionally an actor and a subject, and its own data; correlation identifiers let you follow a chain of reactions."),
        T("Hay 32 tipos de evento reservados del sistema; no se pueden redefinir (es un error). Los tipos propios son cualquier otro identificador; si su espacio de nombres no es el del modelo se avisa.", "There are 32 reserved system event types; they cannot be redefined (an error). Custom types are any other identifier; if the namespace differs from the model's a warning is raised."),
        T("Un enlace puede leer un evento reservado o propio, pero solo puede producir un evento propio; todos los campos obligatorios del destino deben quedar rellenos.", "A binding can read a reserved or custom event but can only produce a custom one; every mandatory field of the target must be filled."),
        T("Cada campo de destino se rellena de exactamente una forma: copiando una ruta del evento de origen, evaluando una expresión o con un valor fijo.", "Each target field is filled in exactly one way: copying a path from the source event, evaluating an expression, or a fixed value."),
        T("Un flujo solo se compila si su comprobación no tiene errores. Los pasos de decisión y de ruta exigen una salida «verdadero» y otra «falso»; la unión exige al menos dos entradas.", "A flow only compiles if its check has no errors. Decision and route steps require a true and a false exit; a join requires at least two inputs."),
        T("La ejecución es secuencial: aunque un paso se divida en varias ramas, se procesan una tras otra en orden, no a la vez.", "Execution is sequential: even when a step splits into several branches they are processed one after another, not concurrently."),
        T("Si un paso falla y no tiene salida de error, HYDRA deshace en orden inverso los pasos completados que sean compensables.", "If a step fails and has no error exit, HYDRA undoes the completed compensable steps in reverse order."),
        T("Límites reales: 32 niveles de encadenamiento, 200 trazas, 256 estados de ejecución y 128 despliegues; el canal de eventos corta a 4096 entregas por publicación.", "Real limits: 32 levels of chaining, 200 traces, 256 runtime states and 128 deployments; the event channel stops at 4096 deliveries per publication.")
      ],
      states: [
        T("Completado", "Completed"), T("Filtrado", "Filtered"), T("En espera", "Waiting"), T("Fallido", "Failed"), T("Compensado", "Compensated"), T("Límite de profundidad", "Depth limit")
      ].map(function (n, i) {
        var d = [
          T("Todas las ramas llegaron a su final sin problema", "All branches reached their end without problems"),
          T("Una condición no se cumplió y la ejecución se descartó", "A condition was not met and the run was dropped"),
          T("Un paso espera un evento, un reintento o un plazo", "A step is waiting for an event, a retry or a deadline"),
          T("Un paso falló y no hubo salida ni compensación", "A step failed with no exit and no compensation"),
          T("Tras un fallo se deshicieron pasos ya completados", "After a failure already-completed steps were undone"),
          T("La cadena de eventos derivados superó el máximo", "The chain of derived events exceeded the maximum")
        ];
        return { n: n, d: d[i] };
      }),
      cross: [
        T("Editor de modelos → eventos → gamificación: guardar, crear, editar o borrar elementos emite eventos que la gamificación escucha para otorgar o retirar puntos.", "Model editor → events → gamification: saving, creating, editing or deleting elements emits events that gamification listens to in order to grant or take away points."),
        T("Validación y Quick Fix emiten eventos reservados al ejecutar una validación o aplicar una corrección.", "Validation and Quick Fix emit reserved events when a validation runs or a fix is applied."),
        T("La gamificación emite sus propios eventos (premio concedido, penalización, rechazo, compensación) que los flujos pueden consumir.", "Gamification emits its own events (award granted, penalty, rejection, compensation) that flows can consume."),
        T("Los pasos de acción, operación y comando de un flujo llegan a las herramientas del editor, a las operaciones del metamodelo y a la validación.", "The action, operation and command steps of a flow reach editor tools, metamodel operations and validation.")
      ]
    },
    tech: {
      runtime: T("Canal de eventos síncrono en memoria con tres suscriptores (enlaces, flujos, gamificación); modelo de eventos y de flujos canónicos con comprobador; compilador y motor de ejecución de flujos con traza y diario; publicación desde sesiones del editor con búfer de confirmación.",
        "Synchronous in-memory event bus with three subscribers (bindings, flows, gamification); canonical event and flow models with checkers; flow compiler and execution engine with trace and journal; publishing from editor sessions with a commit buffer."),
      services: ["InMemoryCanonicalEventBus", "EventBindingRuntimeService", "EventFlowRuntimeService", "EventFlowCompiler", "EventFlowRuntimeNodeExecutor", "EventFlowRuntimeSupport", "EventModelChecker", "EventFlowChecker", "EditorSessionEventPublisher", "EditorSessionPendingEventBuffer", "ReservedEventCatalog", "EventFlowRuntimeEventFactory"],
      apis: [
        "GET /api/canonical-metamodels/events/reserved-types",
        "POST|PUT /api/canonical-metamodels/{id}/events (If-Match-Definitions / If-Match-Bindings)",
        "POST /api/canonical-metamodels/{id}/events/check",
        "GET|PUT /api/canonical-metamodels/{id}/events/diagram (If-Match)",
        "POST|PUT /api/canonical-metamodels/{id}/event-flows (If-Match)",
        "POST /api/canonical-metamodels/{id}/event-flows/check",
        "PUT /api/canonical-metamodels/{id}/event-flows/diagram",
        "POST .../event-flows/runtime/{start|stop|refresh}; GET .../status; POST .../events; POST .../tick; GET .../traces; GET|DELETE .../state"
      ],
      schemas: ["EventDefinitionModel", "EventTypeDefinition", "EventFieldDefinition", "CanonicalEvent", "EventBindingModel", "EventBinding", "EventPayloadMapping", "EventFlowModel", "FlowDefinition", "FlowNode", "FlowTransition", "FlowVariable", "RetryPolicy", "TimeoutPolicy", "CompensationLink", "FlowExecutionTrace", "EventDiagnostic"],
      tests: ["CanonicalEventBusTests", "EventContractTests", "EventContractResourcesTests", "EventModelCheckerTests", "EventArchitectureDiagramRepositoryTests", "EventArchitectureDiagramVersionTests", "EventFlowCheckerTests", "EventFlowCompilerTests", "EventFlowExpressionConditionTests", "EventFlowRuntimeResourcesTests", "EventFlowRuntimeStateTests", "EventFlowRuntimeAutoDeploymentTests", "EventFlowRuntimeDeploymentRegistryTests", "EventBindingRuntimeServiceTests", "CanonicalEventModelControllerTests", "EditorSessionMemoryHardeningTests"],
      evidence: [
        ev(EV + "EventTypeDefinition.java", 11, "EventTypeDefinition"),
        ev(EV + "ReservedEventCatalog.java", 6, "32 reserved ids"),
        ev(EV + "CanonicalEvent.java", 12, "CanonicalEvent envelope"),
        ev(EV + "EventModelChecker.java", 18, "RESERVED_EVENT_TYPE"),
        ev(EV + "bus/InMemoryCanonicalEventBus.java", 17, "synchronous FIFO bus"),
        ev(SESS + "EditorModelSessionService.java", 736, "publish gate and buffering"),
        ev(SESS + "EditorSessionEventPublisher.java", 21, "event envelope built from session"),
        ev(SESS + "EditorSessionPendingEventBuffer.java", 49, "memory / spool / fallback buffer"),
        ev(NODEKIND, 5, "29 node kinds"),
        ev(NODE_EX, 36, "node behaviour by kind"),
        ev(FLOW_RT, 205, "processWithDeployment"),
        ev(FLOW_RT, 240, "executeFlow queue walk"),
        ev(CHECKER, 57, "flow checks"),
        ev(EV + "runtime/EventBindingRuntimeService.java", 66, "binding runtime"),
        ev(FE + "EventDesigner.tsx", 85, "designer views")
      ],
      notes: [
        T("Discrepancia verificada: los interruptores por sesión hydra.eventsEnabled / hydra.eventFlowsEnabled / hydra.gamificationEnabled se escriben en el payload (EditorModelSessionService.java:742-744) pero los consumidores los leen de metadata (EventFlowRuntimeService.java:493, GamificationRuntimeService.java:84, EventBindingRuntimeService.java:67) y el publicador nunca los copia (EditorSessionEventPublisher.java:46): desactivar por sesión no llega a los consumidores. Verificado en código, no ejecutado.",
          "Verified discrepancy: the per-session switches hydra.eventsEnabled / hydra.eventFlowsEnabled / hydra.gamificationEnabled are written to the payload (EditorModelSessionService.java:742-744) but consumers read them from metadata (EventFlowRuntimeService.java:493, GamificationRuntimeService.java:84, EventBindingRuntimeService.java:67) and the publisher never copies them (EditorSessionEventPublisher.java:46): per-session disabling never reaches consumers. Verified in code, not executed."),
        T("F1: core.model.element-reparented se publica (EditorModelSessionService.java:384) pero no está en ReservedEventCatalog (líneas 7-13) ni en la lista de reservados de la UI (CanonicalEventModelController.java:15); un disparador de flujo sobre él daría UNKNOWN_TRIGGER_EVENT (EventFlowChecker.java:117).",
          "F1: core.model.element-reparented is published (EditorModelSessionService.java:384) but is not in ReservedEventCatalog (lines 7-13) nor in the UI reserved list (CanonicalEventModelController.java:15); a flow trigger on it would raise UNKNOWN_TRIGGER_EVENT (EventFlowChecker.java:117)."),
        T("F2: reservados sin productor literal encontrado (solo búsqueda literal; hay construcción dinámica en ValidationRuntimeEventFactory.java:40): core.event-flow.started/completed/failed/derived-event; core.metamodel.class-created/attribute-created/reference-created/operation-created; core.operation.invoked/completed/failed; core.export.completed; core.model.exported; core.validation.execution-started.",
          "F2: reserved ids with no literal producer found (literal search only; dynamic construction exists in ValidationRuntimeEventFactory.java:40): core.event-flow.started/completed/failed/derived-event; core.metamodel.class-created/attribute-created/reference-created/operation-created; core.operation.invoked/completed/failed; core.export.completed; core.model.exported; core.validation.execution-started."),
        T("F4: el estado de la traza se sobrescribe, no se agrega (EventFlowRuntimeService.java:251,270,295,304,310,314): una traza puede leerse FILTERED o WAITING aunque otras ramas hayan completado.",
          "F4: the trace status is overwritten, not aggregated (EventFlowRuntimeService.java:251,270,295,304,310,314): a trace can read FILTERED or WAITING although other branches completed."),
        T("F5: ERROR_END y SUCCESS_END son pasos de paso directo (EventFlowRuntimeNodeExecutor.java:37-38): llegar a un ERROR_END por una transición normal produce COMPLETED.",
          "F5: ERROR_END and SUCCESS_END are pass-through (EventFlowRuntimeNodeExecutor.java:37-38): reaching an ERROR_END via a normal transition yields COMPLETED."),
        T("F6: solo en memoria — el estado de enlaces desactivados y el búfer de eventos de sesión no son duraderos (el volcado JSONL no se recupera tras reiniciar: EditorSessionPendingEventBuffer.java:25-30).",
          "F6: in-memory only — the disabled-binding state and the session event buffer are not durable (the JSONL spool is not recovered after restart: EditorSessionPendingEventBuffer.java:25-30)."),
        T("No se encontró validación en tiempo de ejecución de los valores del payload contra los tipos de campo; el comprobador solo vigila ids/nombres duplicados y el mapeo de campos obligatorios (EventModelChecker.java:23-24, :51).",
          "No runtime validation of payload values against field types was found; the checker only guards duplicate ids/names and required-field mapping (EventModelChecker.java:23-24, :51)."),
        T("Declarado pero sin consumidor encontrado: transición COMPENSATES y política de persistencia EXTERNAL/AUDIT/etc. (solo se guarda el valor). Sin pruebas encontradas por nombre para los ejecutores externos, el tick/timeout ni EventFlowRuntimeNodeExecutor.",
          "Declared but no consumer found: the COMPENSATES transition and the persistence policy values (only the value is stored). No tests found by name for the external executors, the tick/timeout path or EventFlowRuntimeNodeExecutor.")
      ]
    },

    elements: [
      /* 1 */ {
        id: "event_type", n: T("Tipo de evento", "Event type"), kind: "concept", req: "MANDATORY",
        mean: T("La descripción de una clase de hecho que puede ocurrir (por ejemplo «pedido aprobado»), con el nombre, el espacio de nombres y los datos que lleva.", "The description of a class of fact that can happen (for example “order approved”), with its name, namespace and the data it carries."),
        purpose: T("Dar un vocabulario común para que enlaces, flujos y gamificación hablen de lo mismo.", "Give a shared vocabulary so bindings, flows and gamification talk about the same thing."),
        when: T("Antes de reaccionar a algo propio de tu lenguaje: define primero el evento y luego úsalo.", "Before reacting to something specific to your language: define the event first, then use it."),
        card: "1 modelo de eventos ↔ 0..N tipos de evento; 1 tipo ↔ 0..N campos",
        variants: [
          V("Reservado", "Reserved", "Evento del sistema (32 en total, todos del espacio «core»): se puede escuchar pero no redefinir.", "System event (32 in total, all in the “core” space): can be listened to but not redefined.", "ALTERNATIVE"),
          V("Propio", "Custom", "Cualquier otro evento que defines tú; es el único que un enlace puede producir.", "Any other event you define; the only kind a binding can produce.", "ALTERNATIVE")
        ],
        props: [
          P("Identificador", "Identifier", "MANDATORY", "Clave única del tipo", "Unique key of the type"),
          P("Nombre", "Name", "MANDATORY", "Nombre legible, no vacío (al crearlo recibe un nombre por defecto que debes cambiar)", "Readable non-blank name (on creation it gets a default name you should change)"),
          P("Espacio de nombres", "Namespace", "MANDATORY", "Empieza por letra; letras, cifras, punto, guion y guion bajo. El nombre completo es espacio.nombre", "Starts with a letter; letters, digits, dot, hyphen and underscore. The full name is space.name"),
          P("Categoría", "Category", "OPTIONAL", "Texto libre; el formulario ofrece valores predefinidos (por defecto «dominio») y una opción personalizada", "Free text; the form offers presets (default “domain”) and a custom option"),
          P("Versión", "Version", "OPTIONAL", "Por defecto 1.0", "Defaults to 1.0"),
          P("Persistencia", "Persistence", "OPTIONAL", "Transitoria (por defecto), de sesión, auditoría, gamificación o externa", "Transient (default), session, audit, gamification or external"),
          P("Orígenes y sujetos permitidos", "Allowed sources and subjects", "OPTIONAL", "Listas de quién puede emitirlo y sobre qué clase de cosa", "Lists of who may emit it and on what kind of thing"),
          P("Abstracto / obsoleto / activo", "Abstract / deprecated / enabled", "OPTIONAL", "Marcas de ciclo de vida del tipo", "Lifecycle flags of the type")
        ],
        io: { i: T("Nombre, espacio y campos que escribes", "Name, namespace and fields you write"), o: T("Un tipo utilizable en enlaces, disparadores y pasos", "A type usable in bindings, triggers and steps") },
        effect: T("Habilita referirse al evento por su nombre completo; el comprobador rechaza redefinir los reservados.", "Makes the event referable by its full name; the checker rejects redefining reserved ones."),
        func: { rel: [T("Contiene 0..N campos de datos", "Contains 0..N data fields"), T("Es origen o destino de 0..N enlaces", "Is source or target of 0..N bindings"), T("Dispara o es usado por 0..N flujos", "Triggers or is used by 0..N flows")], lifecycle: T("definido → comprobado → guardado (versión 1.0 por defecto)", "defined → checked → saved (version 1.0 by default)"), cond: T("Redefinir un tipo reservado es un error; un espacio de nombres distinto al del modelo es un aviso.", "Redefining a reserved type is an error; a namespace different from the model's is a warning.") },
        tech: { type: "EventTypeDefinition", schema: "EventTypeDefinition (id, name, namespace, description, category, version, fields, allowedSources, allowedSubjects, persistencePolicy, abstractType, deprecated, enabled); EventDefinitionModel (schemaVersion 1.0)", evidence: [ev(EV + "EventTypeDefinition.java", 11, "contract"), ev(EV + "EventTypeDefinition.java", 29, "namespace regex"), ev(EV + "ReservedEventCatalog.java", 6, "reserved ids"), ev(EV + "EventModelChecker.java", 18, "RESERVED_EVENT_TYPE"), ev(EV + "EventModelChecker.java", 19, "FOREIGN_NAMESPACE"), ev(FE + "eventCategoryOptions.ts", 3, "category presets")] }
      },
      /* 2 */ {
        id: "event_field", n: T("Campo de datos del evento", "Event data field"), kind: "variant-set", req: "REPEATABLE",
        mean: T("Una pieza de información que viaja con el evento, con un nombre y un tipo de dato.", "A piece of information travelling with the event, with a name and a data type."),
        purpose: T("Declarar qué datos lleva el evento para poder copiarlos o consultarlos en enlaces y condiciones.", "Declare what data the event carries so it can be copied or queried in bindings and conditions."),
        when: T("Al definir un evento que transporta información (el elemento afectado, un contador, una fecha).", "When defining an event that carries information (the affected element, a counter, a date)."),
        card: "1 tipo de evento ↔ 0..N campos",
        variants: [
          V("Texto", "Text", "Cadena de caracteres (el tipo por defecto de un campo nuevo, llamado «value»).", "String of characters (the default type of a new field, called “value”)."),
          V("Entero", "Integer", "Número sin decimales.", "Number without decimals."),
          V("Decimal", "Decimal", "Número con decimales.", "Number with decimals."),
          V("Booleano", "Boolean", "Verdadero o falso.", "True or false."),
          V("Fecha", "Date", "Día sin hora.", "A day without time."),
          V("Fecha y hora", "Date and time", "Instante concreto.", "A specific instant."),
          V("Duración", "Duration", "Lapso de tiempo.", "A span of time."),
          V("Identificador de documento", "Document identifier", "Referencia a un documento (modelo, metamodelo…).", "Reference to a document (model, metamodel…)."),
          V("Identificador de elemento", "Element identifier", "Referencia a un elemento de un modelo.", "Reference to an element of a model."),
          V("Enumerado", "Enumeration", "Valor de una enumeración; exige indicar cuál.", "Value of an enumeration; requires naming which one.", "CONDITIONAL"),
          V("Objeto", "Object", "Estructura de otro tipo; exige indicar el tipo de objeto.", "Structure of another type; requires naming the object type.", "CONDITIONAL"),
          V("Lista", "Array", "Colección de valores.", "Collection of values."),
          V("Cualquiera", "Any", "Sin restricción de tipo.", "No type restriction.")
        ],
        props: [
          P("Identificador y nombre", "Identifier and name", "MANDATORY", "Únicos dentro del evento", "Unique within the event"),
          P("Tipo", "Type", "MANDATORY", "Uno de los 13 tipos anteriores", "One of the 13 types above"),
          P("Obligatorio", "Required", "OPTIONAL", "Si es obligatorio, un enlace cuyo destino sea este evento debe rellenarlo", "If required, a binding targeting this event must fill it"),
          P("Múltiple", "Many", "OPTIONAL", "Admite varios valores", "Accepts several values"),
          P("Tipo enumerado", "Enumeration type", "CONDITIONAL", "Obligatorio solo si el tipo es enumerado", "Mandatory only if the type is enumeration"),
          P("Tipo de objeto", "Object type", "CONDITIONAL", "Obligatorio solo si el tipo es objeto", "Mandatory only if the type is object"),
          P("Descripción", "Description", "OPTIONAL", "Explicación del campo", "Explanation of the field")
        ],
        io: { i: T("Nombre y tipo del dato", "Name and type of the data"), o: T("Un dato disponible para rutas, mapeos y condiciones", "A datum available to paths, mappings and conditions") },
        effect: T("Marca qué datos deben rellenarse en un enlace. HYDRA no comprueba en ejecución que los valores recibidos respeten el tipo declarado.", "Marks which data must be filled in a binding. HYDRA does not check at run time that received values respect the declared type."),
        func: { rel: [T("Pertenece a 1 tipo de evento", "Belongs to 1 event type"), T("Es destino de 0..1 mapeo por enlace", "Is the target of 0..1 mapping per binding")], lifecycle: T("añadido → tipado → comprobado", "added → typed → checked"), cond: T("Los campos obligatorios deben estar todos mapeados en cada enlace hacia ese evento; ids y nombres duplicados son error.", "Required fields must all be mapped in every binding to that event; duplicate ids and names are errors.") },
        tech: { type: "EventFieldDefinition / EventFieldType", schema: "EventFieldType: STRING, INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME, DURATION, DOCUMENT_ID, ELEMENT_ID, ENUM, OBJECT, ARRAY, ANY; enumTypeId mandatory iff ENUM, objectTypeId mandatory iff OBJECT", evidence: [ev(EV + "EventFieldDefinition.java", 6, "field contract"), ev(EV + "EventFieldType.java", 3, "13 types"), ev(EV + "EventModelChecker.java", 23, "duplicate field check"), ev(FE + "eventDesignerFactory.ts", 14, "default field")] }
      },
      /* 3 */ {
        id: "canonical_event", n: T("Evento en circulación", "Event in flight"), kind: "concept", req: "DERIVED",
        mean: T("Un hecho concreto ocurrido, envuelto con quién lo emitió, en qué contexto, sobre qué cosa, y con qué datos.", "A concrete fact that happened, wrapped with who emitted it, in which context, about what, and with which data."),
        purpose: T("Es lo que realmente viaja por HYDRA: lo producen las sesiones, la validación, los flujos, los enlaces y la gamificación, y lo consumen enlaces, flujos y gamificación.", "It is what actually travels through HYDRA: produced by sessions, validation, flows, bindings and gamification, consumed by bindings, flows and gamification."),
        when: T("Lo ves en las trazas, en eventos de prueba y al razonar sobre qué disparó qué.", "You see it in traces, in test events and when reasoning about what triggered what."),
        card: "1 evento ↔ 1 tipo; 1 evento ↔ 1 origen; 1 evento ↔ 0..1 actor; 1 evento ↔ 0..1 sujeto",
        variants: [
          V("Origen", "Source", "Componente que lo emite (obligatorio); módulo, instancia y versión opcionales.", "Component that emits it (mandatory); module, instance and version optional.", "MANDATORY"),
          V("Contexto", "Context", "Diseño de metamodelo, edición de modelo, validación, Quick Fix, operación, importación/exportación, flujo de eventos, gamificación o sistema (por defecto).", "Metamodel design, model editing, validation, Quick Fix, operation, import/export, event flow, gamification or system (default).", "MANDATORY"),
          V("Actor", "Actor", "Quién lo causa: usuario, sistema, importador, Quick Fix, operación, automatización o servicio externo.", "Who causes it: user, system, importer, Quick Fix, operation, automation or external service.", "OPTIONAL"),
          V("Sujeto", "Subject", "Sobre qué recae: metamodelo, clase, atributo, referencia, operación, modelo, elemento, propiedad, regla, diagnóstico, corrección, acción, definición de evento, flujo, regla o premio de gamificación, o editor.", "What it is about: metamodel, class, attribute, reference, operation, model, element, feature, rule, diagnostic, fix, action, event definition, flow, gamification rule or reward, or editor.", "OPTIONAL"),
          V("Correlación", "Correlation", "Identificadores de transacción, de correlación y de causa para seguir cadenas de reacciones.", "Transaction, correlation and causation identifiers to follow chains of reactions.", "OPTIONAL"),
          V("Datos y metadatos", "Data and metadata", "Dos mapas libres: los datos del evento y la información técnica adicional.", "Two free maps: the event data and extra technical information.", "OPTIONAL")
        ],
        props: [
          P("Identificador y tipo de evento", "Identifier and event type", "MANDATORY", "Quién es y de qué tipo", "What it is and which type"),
          P("Versión del tipo", "Type version", "OPTIONAL", "Por defecto 1.0", "Defaults to 1.0"),
          P("Instante", "Timestamp", "OPTIONAL", "Por defecto, el momento de creación", "Defaults to the creation time"),
          P("Política de persistencia", "Persistence policy", "OPTIONAL", "Se declara en el tipo; hoy no hay un consumidor que la aplique", "Declared on the type; no consumer applies it today")
        ],
        io: { i: T("Un hecho que ocurre", "A fact that happens"), o: T("Una notificación entregada a todos los suscriptores", "A notification delivered to all subscribers") },
        effect: T("Se entrega por un canal en memoria, de forma síncrona y en orden; las publicaciones hechas durante una entrega esperan su turno.", "Delivered through an in-memory channel, synchronously and in order; publications made during a delivery wait their turn."),
        func: { rel: [T("Es una instancia de 1 tipo de evento", "Is an instance of 1 event type"), T("Puede disparar 0..N flujos y 0..N enlaces", "May trigger 0..N flows and 0..N bindings")], lifecycle: T("emitido → entregado → procesado por enlaces, flujos y gamificación", "emitted → delivered → processed by bindings, flows and gamification"), cond: T("Si un suscriptor falla, los demás reciben igualmente el evento y el error se notifica al final.", "If a subscriber fails the others still receive the event and the error is reported at the end.") },
        tech: { type: "CanonicalEvent", schema: "CanonicalEvent (schemaVersion 1.0; EventSource, EventContext, EventActor, EventSubject); EventContextKind, EventActorKind, EventSubjectKind, EventPersistencePolicy (TRANSIENT, SESSION, AUDIT, GAMIFICATION, EXTERNAL)", evidence: [ev(EV + "CanonicalEvent.java", 12, "envelope"), ev(EV + "bus/InMemoryCanonicalEventBus.java", 17, "bus"), ev(EV + "bus/InMemoryCanonicalEventBus.java", 42, "4096 delivery cap")] }
      },
      /* 4 */ {
        id: "binding", n: T("Enlace entre eventos", "Event binding"), kind: "concept", req: "OPTIONAL",
        mean: T("Una regla «cuando ocurra este evento, y si se cumple esta condición, emite aquel otro copiando estos datos».", "A rule “when this event happens, and if this condition holds, emit that other one copying this data”."),
        purpose: T("Derivar eventos propios a partir de otros sin diseñar un flujo completo.", "Derive custom events from others without designing a full flow."),
        when: T("Cuando la reacción es de un solo paso: traducir un evento a otro.", "When the reaction is a single step: translate one event into another."),
        card: "1 enlace ↔ 1 evento de origen ↔ 1 evento de destino propio ↔ 0..1 condición ↔ 0..N copias",
        variants: [
          V("Omitir", "Skip", "Si falla, se ignora en silencio.", "If it fails it is silently ignored.", "ALTERNATIVE"),
          V("Avisar", "Report", "Si falla, se deja un aviso en el registro (por defecto).", "If it fails a warning is logged (default).", "ALTERNATIVE"),
          V("Desactivar el enlace", "Disable the binding", "Si falla, se deja un aviso y el enlace deja de aplicarse (hasta que se reinicie el motor).", "If it fails a warning is logged and the binding stops applying (until the engine restarts).", "ALTERNATIVE")
        ],
        props: [
          P("Evento de origen", "Source event", "MANDATORY", "Reservado o propio", "Reserved or custom"),
          P("Evento de destino", "Target event", "MANDATORY", "Debe ser propio, no reservado", "Must be custom, not reserved"),
          P("Condición", "Condition", "OPTIONAL", "Una expresión que debe dar verdadero para que el enlace actúe", "An expression that must be true for the binding to act"),
          P("Copias de datos", "Data copies", "OPTIONAL", "Ver «Copia de datos»; todos los campos obligatorios del destino deben cubrirse", "See “Data copy”; all required target fields must be covered"),
          P("Política de error", "Error policy", "OPTIONAL", "Omitir, avisar (por defecto) o desactivar el enlace", "Skip, report (default) or disable the binding"),
          P("Activo", "Enabled", "OPTIONAL", "Un enlace inactivo no se aplica", "A disabled binding is not applied"),
          P("Nombre y anotaciones", "Name and annotations", "OPTIONAL", "El nombre por defecto es el identificador", "Name defaults to the identifier")
        ],
        io: { i: T("Un evento del tipo de origen", "An event of the source type"), o: T("Un evento derivado del tipo de destino", "A derived event of the target type") },
        effect: T("El evento derivado se publica de nuevo en el canal, así que puede disparar a su vez otros enlaces o flujos; la cadena se corta a 32 niveles.", "The derived event is published back to the channel so it can in turn trigger other bindings or flows; the chain stops at 32 levels."),
        func: { rel: [T("Referencia 1 evento de origen y 1 de destino", "References 1 source and 1 target event"), T("Contiene 0..N copias de datos", "Contains 0..N data copies")], lifecycle: T("creado al conectar dos eventos (activo, sin copias) → configurado → comprobado → guardado", "created by connecting two events (enabled, no copies) → configured → checked → saved"), cond: T("Origen reservado o propio; destino solo propio; el destino de una copia debe existir y no repetirse.", "Source reserved or custom; target custom only; a copy's target must exist and not repeat.") },
        tech: { type: "EventBinding / EventBindingErrorPolicy", schema: "EventBinding; EventBindingModel (schemaVersion 1.0, eventDefinitionModelId, expressionModelId optional); EventBindingErrorPolicy: SKIP, REPORT, DISABLE_BINDING", evidence: [ev(EV + "EventBinding.java", 10, "binding"), ev(EV + "EventModelChecker.java", 30, "source check"), ev(EV + "EventModelChecker.java", 36, "target must be custom"), ev(EV + "runtime/EventBindingRuntimeService.java", 155, "failure policy")] }
      },
      /* 5 */ {
        id: "payload_mapping", n: T("Copia de datos", "Data copy"), kind: "variant-set", req: "REPEATABLE",
        mean: T("Cómo se rellena un campo del evento de destino de un enlace.", "How one field of a binding's target event is filled."),
        purpose: T("Trasladar o calcular los datos del evento derivado.", "Carry over or compute the derived event's data."),
        when: T("En cada campo del destino que debe tener valor, sobre todo los obligatorios.", "On every target field that must have a value, especially required ones."),
        card: "1 enlace ↔ 0..N copias; 1 copia ↔ 1 campo de destino",
        variants: [
          V("Desde una ruta del evento de origen", "From a path in the source event", "Copia el valor encontrado en esa ruta.", "Copies the value found at that path."),
          V("Desde una expresión", "From an expression", "Calcula el valor con una expresión del modelo de expresiones.", "Computes the value with an expression from the expressions model."),
          V("Valor fijo", "Fixed value", "Siempre el mismo valor.", "Always the same value.")
        ],
        props: [
          P("Campo de destino", "Target field", "MANDATORY", "Debe existir en el evento de destino y no repetirse", "Must exist in the target event and not repeat"),
          P("Forma de obtener el valor", "How the value is obtained", "MANDATORY", "Exactamente una: ruta, expresión o valor fijo", "Exactly one: path, expression or fixed value")
        ],
        io: { i: T("Evento de origen", "Source event"), o: T("Un campo relleno del evento derivado", "One filled field of the derived event") },
        effect: T("Define el contenido del evento derivado.", "Defines the content of the derived event."),
        func: { rel: [T("Pertenece a 1 enlace", "Belongs to 1 binding")], lifecycle: T("definida → comprobada", "defined → checked"), cond: T("Indicar dos formas a la vez, o ninguna, no es válido.", "Giving two ways at once, or none, is not valid.") },
        tech: { type: "EventPayloadMapping", schema: "targetFieldId + exactly one of sourcePath | expressionId | fixedValue", evidence: [ev(EV + "EventPayloadMapping.java", 4, "mapping"), ev(EV + "EventModelChecker.java", 45, "mapping checks"), ev(FE + "eventDesignerValidation.ts", 28, "required fields mapped")] }
      },
      /* 6 */ {
        id: "flow", n: T("Flujo de eventos", "Event flow"), kind: "concept", req: "OPTIONAL",
        mean: T("Un diagrama de pasos y transiciones que se ejecuta cuando llega uno de los eventos que lo disparan.", "A diagram of steps and transitions that runs when one of the events that trigger it arrives."),
        purpose: T("Expresar reacciones de varios pasos: decidir, esperar, repetir, actuar y emitir nuevos eventos.", "Express multi-step reactions: decide, wait, retry, act and emit new events."),
        when: T("Cuando una reacción necesita más que traducir un evento: ramas, esperas, acciones o recuperación ante fallos.", "When a reaction needs more than translating one event: branches, waits, actions or failure recovery."),
        card: "1 modelo de flujos ↔ 0..N flujos; 1 flujo ↔ 0..N disparadores, variables, pasos, transiciones y compensaciones",
        variants: [
          V("Disparado por evento", "Triggered by an event", "Se arranca cuando llega un evento de la lista de disparadores o el del paso de entrada.", "Starts when an event from the trigger list or from the source step arrives.", "ALTERNATIVE")
        ],
        props: [
          P("Identificador", "Identifier", "MANDATORY", "Único en el modelo de flujos", "Unique in the flow model"),
          P("Nombre y descripción", "Name and description", "OPTIONAL", "El nombre por defecto es el identificador", "Name defaults to the identifier"),
          P("Eventos disparadores", "Trigger events", "OPTIONAL", "Eventos que arrancan el flujo; deben existir como tipo", "Events that start the flow; must exist as a type"),
          P("Variables", "Variables", "OPTIONAL", "Valores propios del flujo", "Values owned by the flow"),
          P("Pasos y transiciones", "Steps and transitions", "MANDATORY", "Un solo punto de entrada y al menos un final", "A single entry point and at least one end"),
          P("Compensaciones", "Compensations", "OPTIONAL", "Qué deshacer si falla un paso", "What to undo if a step fails"),
          P("Activo", "Enabled", "OPTIONAL", "Un flujo inactivo no se ejecuta", "A disabled flow is not run")
        ],
        io: { i: T("Un evento disparador", "A trigger event"), o: T("Una traza y, si hay pasos de emisión, eventos derivados", "A trace and, with emit steps, derived events") },
        effect: T("Cada ejecución recorre el diagrama desde el punto de entrada y deja una traza.", "Each run walks the diagram from the entry point and leaves a trace."),
        func: { rel: [T("Contiene 0..N pasos, transiciones, variables y compensaciones", "Contains 0..N steps, transitions, variables and compensations"), T("Referencia 0..N tipos de evento como disparadores", "References 0..N event types as triggers")], lifecycle: T("diseñado → comprobado → compilado → desplegado (al arrancar el motor o al llegar el primer evento)", "designed → checked → compiled → deployed (on engine start or at the first event)"), cond: T("Solo se compila si la comprobación no da errores; el punto de entrada es el primer paso de inicio o de entrada.", "Only compiles if the check reports no errors; the entry is the first start or source step.") },
        tech: { type: "FlowDefinition / EventFlowModel", schema: "FlowDefinition (id, name, enabled, triggerEventTypeIds, variables, nodes, transitions, compensations, annotations); EventFlowModel (schemaVersion 1.0)", evidence: [ev(EV + "flow/FlowDefinition.java", 13, "flow"), ev(EV + "flow/runtime/EventFlowCompiler.java", 24, "compiler"), ev(EV + "flow/runtime/EventFlowCompiler.java", 39, "trigger index")] }
      },
      /* 7 */ {
        id: "flow_variable", n: T("Variable de flujo", "Flow variable"), kind: "concept", req: "OPTIONAL",
        mean: T("Un valor con nombre que pertenece al flujo, con un tipo (por defecto «cualquiera») y un valor inicial.", "A named value owned by the flow, with a type (default “any”) and an initial value."),
        purpose: T("Guardar datos propios de la ejecución del flujo.", "Hold data specific to the flow's run."),
        when: T("Cuando un flujo necesita recordar algo entre pasos.", "When a flow needs to remember something between steps."),
        card: "1 flujo ↔ 0..N variables",
        props: [P("Identificador y nombre", "Identifier and name", "MANDATORY", "Ambos obligatorios", "Both mandatory"), P("Tipo", "Type", "OPTIONAL", "Por defecto cualquiera", "Defaults to any"), P("Valor inicial", "Initial value", "OPTIONAL", "Valor de partida", "Starting value")],
        io: { i: T("Declaración de la variable", "Variable declaration"), o: T("Un valor disponible durante el flujo", "A value available during the flow") },
        effect: T("Se declara en el diseño del flujo; este material no encontró un paso que la lea o escriba de forma específica.", "Declared in the flow design; no step was found in the evidence that specifically reads or writes it."),
        func: { rel: [T("Pertenece a 1 flujo", "Belongs to 1 flow")], lifecycle: T("declarada → disponible al ejecutar", "declared → available at run time"), cond: T("Identificador y nombre son obligatorios.", "Identifier and name are mandatory.") },
        tech: { type: "FlowVariable", schema: "FlowVariable (id, name, type default ANY, initialValue)", evidence: [ev(EV + "flow/FlowDefinition.java", 13, "variables list")] }
      },
      /* 8 */ {
        id: "flow_node", n: T("Paso del flujo", "Flow step"), kind: "concept", req: "MANDATORY",
        mean: T("Cada caja del diagrama de un flujo: tiene un tipo que determina qué hace, y opciones comunes.", "Each box of a flow diagram: it has a type that determines what it does, and common options."),
        purpose: T("Descomponer la reacción en acciones elementales.", "Break the reaction into elementary actions."),
        when: T("Al diseñar un flujo; hay 29 tipos agrupados en nueve familias (siguientes elementos).", "When designing a flow; there are 29 types grouped into nine families (next elements)."),
        card: "1 flujo ↔ 1..N pasos; 1 paso ↔ 0..N transiciones de salida",
        props: [
          P("Identificador y tipo", "Identifier and type", "MANDATORY", "Único en el flujo; el tipo define el comportamiento", "Unique in the flow; the type defines the behaviour"),
          P("Nombre", "Name", "OPTIONAL", "Por defecto el identificador", "Defaults to the identifier"),
          P("Evento", "Event", "CONDITIONAL", "Obligatorio en los pasos de entrada, evento, espera, emisión y ausencia", "Mandatory for source, event, wait, emit and absence steps"),
          P("Referencia", "Reference", "CONDITIONAL", "La acción, operación o comando que ejecuta un paso externo", "The action, operation or command an external step runs"),
          P("Condición", "Condition", "OPTIONAL", "Expresión de verdadero/falso", "True/false expression"),
          P("Configuración", "Configuration", "OPTIONAL", "Opciones propias del tipo de paso", "Options specific to the step type"),
          P("Reintento y límite de tiempo", "Retry and timeout", "OPTIONAL", "Ver «Política de reintento y plazo»", "See “Retry and time-limit policy”"),
          P("Compensable", "Compensable", "OPTIONAL", "Si puede deshacerse tras un fallo posterior", "Whether it can be undone after a later failure")
        ],
        io: { i: T("Un testigo de ejecución que llega por una transición", "An execution token arriving via a transition"), o: T("Un testigo por la transición elegida, o el final de la rama", "A token along the chosen transition, or the end of the branch") },
        effect: T("La posición del paso en el diagrama es independiente de su significado (se guarda en un documento de diagrama aparte).", "A step's position on the diagram is independent of its meaning (stored in a separate diagram document)."),
        func: { rel: [T("Pertenece a 1 flujo", "Belongs to 1 flow"), T("Es origen y destino de 0..N transiciones", "Is source and target of 0..N transitions")], lifecycle: T("añadido (por defecto, paso de evento) → configurado por familia → conectado", "added (defaults to an event step) → configured by family → connected"), cond: T("Sin entrada alcanzable el paso es un error; un paso sin salida que no sea final es un error.", "A step unreachable from the entry is an error; a non-final step with no exit is an error.") },
        tech: { type: "FlowNode / FlowNodeKind", schema: "FlowNode (id, name, kind, referenceId, eventTypeId, conditionExpressionId, configuration, retryPolicy, timeoutPolicy, compensable, enabled); FlowNodeKind (29 values)", evidence: [ev(NODEKIND, 5, "29 kinds"), ev(EV + "flow/FlowNode.java", 11, "node"), ev(NODE_EX, 36, "behaviour dispatch")] }
      },
      /* 9 */ {
        id: "node_entry_exit", n: T("Familia: entradas y finales", "Family: entries and ends"), kind: "variant-set", req: "MANDATORY",
        mean: T("Los pasos que abren y cierran un flujo.", "The steps that open and close a flow."),
        purpose: T("Marcar dónde empieza una ejecución y dónde termina cada rama.", "Mark where a run begins and where each branch ends."),
        when: T("Todo flujo necesita exactamente un punto de entrada y al menos un final.", "Every flow needs exactly one entry point and at least one end."),
        card: "1 flujo ↔ exactamente 1 inicio o entrada; 1 flujo ↔ 1..N finales",
        variants: [
          V("Inicio", "Start", "Punto de entrada simple: deja pasar la ejecución.", "Simple entry point: lets the run through.", "ALTERNATIVE"),
          V("Entrada de evento", "Event source", "Punto de entrada que solo acepta el evento indicado; ese evento también pasa a ser disparador del flujo.", "Entry that only accepts the indicated event; that event also becomes a trigger of the flow.", "ALTERNATIVE"),
          V("Final correcto", "Success end", "Cierra una rama; no cambia el resultado por sí mismo.", "Closes a branch; does not by itself change the result.", "REPEATABLE"),
          V("Final con error", "Error end", "Cierra una rama marcada como error, pero si se llega a él por una transición normal el resultado sigue siendo «completado».", "Closes a branch marked as an error, but if reached through a normal transition the result is still “completed”.", "REPEATABLE")
        ],
        props: [P("Evento", "Event", "CONDITIONAL", "Solo en la entrada de evento", "Only on the event source")],
        io: { i: T("El evento que arranca el flujo", "The event that starts the flow"), o: T("La ejecución continúa o la rama termina", "The run continues or the branch ends") },
        effect: T("Una rama termina cuando su testigo no tiene transición de salida.", "A branch ends when its token has no outgoing transition."),
        func: { rel: [T("1 flujo ↔ exactamente 1 inicio o entrada", "1 flow ↔ exactly 1 start or source")], lifecycle: T("entrada → … → final", "entry → … → end"), cond: T("Cero o varios inicios/entradas es un error; sin ningún final es un error.", "Zero or several starts/sources is an error; no end at all is an error.") },
        tech: { type: "FlowNodeKind: START, SOURCE, SUCCESS_END, ERROR_END", schema: "kinds pass-through except SOURCE (matches eventTypeId when set)", evidence: [ev(NODE_EX, 37, "pass-through ends"), ev(NODE_EX, 39, "SOURCE match"), ev(CHECKER, 80, "FLOW_START_COUNT"), ev(CHECKER, 81, "FLOW_END_REQUIRED")] }
      },
      /* 10 */ {
        id: "node_event", n: T("Familia: pasos de evento", "Family: event steps"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Pasos que dependen de un evento concreto en medio del flujo.", "Steps that depend on a specific event in the middle of the flow."),
        purpose: T("Comprobar el tipo de evento o detener la ejecución hasta que llegue otro.", "Check the event type or pause the run until another one arrives."),
        when: T("Cuando el flujo debe continuar solo con un tipo de evento, o esperar una respuesta.", "When the flow must continue only with one event type, or wait for a reply."),
        card: "1 flujo ↔ 0..N pasos de evento",
        variants: [
          V("Paso de evento", "Event step", "Deja pasar si el evento es del tipo indicado; si no, la ejecución queda filtrada.", "Lets through if the event is of the indicated type; otherwise the run is filtered."),
          V("Esperar evento", "Wait for event", "Si ya llegó el evento esperado continúa; si no, aparca la ejecución en espera. Un límite de tiempo puede sacarla por la transición de tiempo agotado.", "If the awaited event has arrived it continues; otherwise it parks the run waiting. A time limit can release it through the timeout transition.")
        ],
        props: [P("Evento", "Event", "MANDATORY", "Tipo de evento esperado o requerido", "Expected or required event type"), P("Límite de tiempo", "Time limit", "OPTIONAL", "Para la espera", "For the wait")],
        io: { i: T("El evento actual o futuro", "The current or a future event"), o: T("Continuación, filtrado o espera", "Continuation, filtering or waiting") },
        effect: T("Una espera parada queda guardada y se reanuda cuando llega el evento o vence el plazo (al lanzar el reloj del motor).", "A parked wait is stored and resumes when the event arrives or the deadline passes (when the engine clock ticks)."),
        func: { rel: [T("Referencia 1 tipo de evento", "References 1 event type")], lifecycle: T("llega → comprueba/aparca → reanuda o vence", "arrives → checks/parks → resumes or expires"), cond: T("Requieren evento; si no existe como tipo es un error.", "They require an event; if it does not exist as a type it is an error.") },
        tech: { type: "FlowNodeKind: EVENT_STEP, WAIT_FOR_EVENT", schema: "FlowWaitingContinuation; TIMEOUT transition on timeout", evidence: [ev(NODE_EX, 57, "wait/park"), ev(FLOW_RT, 373, "resume waiting"), ev(FLOW_RT, 415, "timeout via transition"), ev(CHECKER, 75, "EVENT_TYPE_REQUIRED")] }
      },
      /* 11 */ {
        id: "node_external", n: T("Familia: pasos que actúan fuera del flujo", "Family: steps that act outside the flow"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Pasos que piden a otra parte de HYDRA que haga algo y esperan su resultado.", "Steps that ask another part of HYDRA to do something and wait for the result."),
        purpose: T("Que un flujo no solo observe, sino que actúe.", "So a flow does not merely observe but acts."),
        when: T("Cuando la reacción debe ejecutar una acción del editor, una operación del metamodelo o un comando de validación.", "When the reaction must run an editor action, a metamodel operation or a validation command."),
        card: "1 paso ↔ 0..1 referencia a lo que ejecuta",
        variants: [
          V("Comando", "Command step", "Ejecuta un comando de validación indicado por referencia.", "Runs a validation command given by reference."),
          V("Acción", "Action step", "Ejecuta una acción del editor mediante sus herramientas.", "Runs an editor action through its tools."),
          V("Operación", "Operation step", "Ejecuta una operación definida en el metamodelo.", "Runs an operation defined in the metamodel."),
          V("Paso manual", "Manual step", "Pensado para una intervención humana, pero no hay ejecutor: falla salvo que se permita explícitamente no hacer nada.", "Meant for a human intervention but has no executor: it fails unless doing nothing is explicitly allowed.")
        ],
        props: [P("Referencia", "Reference", "CONDITIONAL", "Qué acción, operación o comando ejecutar; una referencia vacía falla", "Which action, operation or command to run; a blank reference fails"), P("Permitir no hacer nada", "Allow no-op", "OPTIONAL", "Opción de configuración que evita el fallo del paso manual", "Configuration option that avoids the manual step's failure")],
        io: { i: T("La ejecución y su referencia", "The run and its reference"), o: T("Resultado de la acción o un fallo", "The action's result or a failure") },
        effect: T("Si la ejecución externa falla, el paso falla y entran en juego reintentos, transición de error y compensación.", "If the external execution fails the step fails and retries, the error transition and compensation come into play."),
        func: { rel: [T("Referencia 1 acción, operación o comando", "References 1 action, operation or command")], lifecycle: T("llega → ejecuta fuera → éxito o fallo", "arrives → runs externally → success or failure"), cond: T("Sin ejecutor disponible, falla.", "With no executor available, it fails.") },
        tech: { type: "FlowNodeKind: COMMAND_STEP, ACTION_STEP, OPERATION_STEP, MANUAL_STEP", schema: "external executors: ValidationFlowExternalStepExecutor, ActionFlowExternalStepExecutor (→ EditorSessionToolRuntime), OperationFlowExternalStepExecutor; configuration allowNoop", evidence: [ev(NODE_EX, 36, "external dispatch"), ev(NODE_EX, 186, "no executor => fails unless allowNoop")] }
      },
      /* 12 */ {
        id: "node_routing", n: T("Familia: decisión y encaminamiento", "Family: decisions and routing"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Pasos que eligen por qué camino sigue la ejecución o que reúnen caminos.", "Steps that choose which path the run follows or that merge paths."),
        purpose: T("Introducir ramas condicionales y volver a unirlas.", "Introduce conditional branches and join them again."),
        when: T("Cuando el flujo debe comportarse distinto según una condición.", "When the flow must behave differently according to a condition."),
        card: "1 decisión ↔ ≥1 salida «verdadero» y ≥1 salida «falso»",
        variants: [
          V("Decisión", "Decision", "Evalúa una condición y sigue por la salida «verdadero» o «falso».", "Evaluates a condition and follows the true or the false exit."),
          V("Ruta", "Route", "Igual que la decisión: elige entre salidas verdadero/falso según su configuración.", "Like decision: chooses between true/false exits according to its configuration."),
          V("Unión de ramas", "Merge", "Deja pasar; sirve para reunir caminos sin esperar.", "Lets through; merges paths without waiting.")
        ],
        props: [P("Condición", "Condition", "MANDATORY", "Para decisión y ruta; debe dar verdadero o falso", "For decision and route; must give true or false"), P("Salidas verdadero y falso", "True and false exits", "MANDATORY", "Al menos una de cada", "At least one of each")],
        io: { i: T("La ejecución", "The run"), o: T("Un único camino elegido", "One chosen path") },
        effect: T("La condición compara una ruta del evento con un valor esperado: igual (por defecto), distinto, existe, no existe, contiene, mayor que o menor que.", "The condition compares a path of the event with an expected value: equals (default), not equals, exists, does not exist, contains, greater than or less than."),
        func: { rel: [T("Tiene 2 o más transiciones de salida", "Has 2 or more outgoing transitions")], lifecycle: T("evalúa → elige salida", "evaluates → picks an exit"), cond: T("Sin ramas verdadero y falso, la comprobación da error.", "Without both true and false branches the check errors.") },
        tech: { type: "FlowNodeKind: DECISION, ROUTE, MERGE", schema: "TRUE/FALSE transitions; EventFlowConditionEvaluator operators: EQUALS, NOT_EQUALS, EXISTS, NOT_EXISTS, CONTAINS, GREATER_THAN, LESS_THAN", evidence: [ev(SUPPORT, 81, "branch selection"), ev(CHECKER, 99, "DECISION_BRANCHES"), ev(EV + "flow/runtime/EventFlowConditionEvaluator.java", 27, "boolean condition")] }
      },
      /* 13 */ {
        id: "node_parallel", n: T("Familia: ramas paralelas", "Family: parallel branches"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Pasos que dividen la ejecución en varias ramas y las esperan juntas.", "Steps that split the run into several branches and wait for them together."),
        purpose: T("Lanzar varias reacciones a partir de un mismo evento y sincronizarlas.", "Launch several reactions from the same event and synchronize them."),
        when: T("Cuando varias cosas deben ocurrir y luego hace falta que todas hayan pasado.", "When several things must happen and then all must have occurred."),
        card: "1 división ↔ 2..N ramas; 1 unión ↔ ≥2 entradas",
        variants: [
          V("División paralela", "Parallel split", "Sigue por todas las salidas normales o de éxito. Se procesan una detrás de otra, no a la vez.", "Follows all normal or success exits. They are processed one after the other, not concurrently."),
          V("Unión sincronizada", "Join", "Espera a que lleguen todos los tipos de evento configurados para un mismo grupo antes de continuar.", "Waits until all configured event types have arrived for the same group before continuing.")
        ],
        props: [P("Eventos a esperar", "Events to wait for", "MANDATORY", "En la unión: la lista de tipos de evento", "On the join: the list of event types")],
        io: { i: T("La ejecución", "The run"), o: T("Varias ramas, o una sola tras reunirlas", "Several branches, or one after joining") },
        effect: T("El motor no es concurrente: las ramas se turnan en una cola.", "The engine is not concurrent: branches take turns in a queue."),
        func: { rel: [T("Una unión tiene 2 o más entradas", "A join has 2 or more inputs")], lifecycle: T("divide → procesa ramas → une", "splits → processes branches → joins"), cond: T("Una unión con menos de dos entradas es un error.", "A join with fewer than two inputs is an error.") },
        tech: { type: "FlowNodeKind: PARALLEL_SPLIT, JOIN", schema: "configuration.eventTypes grouped by key", evidence: [ev(SUPPORT, 86, "fan-out NEXT/ON_SUCCESS"), ev(SUPPORT, 124, "join per key"), ev(CHECKER, 104, "JOIN_INPUT_COUNT")] }
      },
      /* 14 */ {
        id: "node_timing", n: T("Familia: tiempo y repetición", "Family: timing and repetition"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Marcadores de tiempo agotado y reintento dentro de un flujo.", "Markers for timeout and retry inside a flow."),
        purpose: T("Dejar visible en el diagrama dónde se espera o se repite.", "Make visible on the diagram where waiting or repeating happens."),
        when: T("Como apoyo gráfico; el comportamiento real lo da la política de cada paso.", "As graphical support; the real behaviour comes from each step's policy."),
        card: "—",
        variants: [
          V("Tiempo agotado", "Timeout", "Deja pasar; el límite real se define en el paso que espera.", "Lets through; the real limit is defined on the step that waits."),
          V("Reintento", "Retry", "Deja pasar; los reintentos reales se definen en el paso que puede fallar.", "Lets through; the real retries are defined on the step that may fail.")
        ],
        props: [], io: { i: T("La ejecución", "The run"), o: T("La ejecución sin cambios", "The run unchanged") },
        effect: T("Por sí mismos no hacen nada: la política de reintento y plazo vive en cada paso.", "By themselves they do nothing: retry and time-limit policy live on each step."),
        func: { rel: [T("Se complementa con la política de reintento y plazo", "Complemented by the retry and time-limit policy")], lifecycle: T("pasa", "passes through"), cond: T("Sin efecto propio.", "No effect of its own.") },
        tech: { type: "FlowNodeKind: TIMEOUT, RETRY", schema: "pass-through; real behaviour from TimeoutPolicy / RetryPolicy", evidence: [ev(FLOW_RT, 276, "retry handling"), ev(FLOW_RT, 330, "timeout detection")] }
      },
      /* 15 */ {
        id: "node_transform", n: T("Familia: transformación y emisión", "Family: transformation and emission"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Pasos que filtran, transforman, deduplican o emiten eventos derivados.", "Steps that filter, transform, deduplicate or emit derived events."),
        purpose: T("Dar forma a lo que continúa por el flujo y publicar los resultados.", "Shape what continues through the flow and publish results."),
        when: T("Cuando solo algunos eventos deben seguir, o el flujo debe producir un evento nuevo.", "When only some events must continue, or the flow must produce a new event."),
        card: "1 emisión ↔ 1 evento derivado por ejecución del paso",
        variants: [
          V("Filtro", "Filter", "Deja pasar solo si se cumple la condición; si no, la ejecución queda filtrada.", "Lets through only if the condition holds; otherwise the run is filtered."),
          V("Transformación", "Map", "Reasigna datos según una lista de correspondencias configurada.", "Reassigns data according to a configured list of mappings."),
          V("Deduplicar", "Deduplicate", "Descarta eventos repetidos según una clave (por defecto el identificador) durante un tiempo; recuerda como máximo 4096 claves.", "Discards repeated events by a key (default the identifier) for some time; remembers at most 4096 keys."),
          V("Emitir", "Emit", "Publica un evento derivado del tipo del paso o del destino configurado.", "Publishes a derived event of the step's type or the configured target.")
        ],
        props: [P("Condición", "Condition", "OPTIONAL", "Para el filtro", "For the filter"), P("Correspondencias", "Mappings", "OPTIONAL", "Para la transformación", "For the transformation"), P("Clave y duración", "Key and duration", "OPTIONAL", "Para deduplicar", "For deduplicate"), P("Evento", "Event", "MANDATORY", "Para emitir (o destino configurado)", "For emit (or configured target)")],
        io: { i: T("La ejecución con su evento", "The run with its event"), o: T("La ejecución modificada, filtrada, o un evento derivado", "The run modified, filtered, or a derived event") },
        effect: T("Los eventos emitidos se publican en el canal al terminar el procesado y pueden encadenar otros flujos.", "Emitted events are published to the channel when processing ends and can chain other flows."),
        func: { rel: [T("Una emisión referencia 1 tipo de evento", "An emit references 1 event type")], lifecycle: T("filtra/transforma/emite", "filters/maps/emits"), cond: T("Emitir exige evento; si no existe como tipo es un error.", "Emit requires an event; if it does not exist as a type it is an error.") },
        tech: { type: "FlowNodeKind: FILTER, MAP, DEDUPLICATE, EMIT", schema: "FILTER: configuration match; MAP: configuration.mappings; DEDUPLICATE: keyPath default id, ttlMillis, 4096 keys cap; EMIT: eventTypeId or configuration.targetEventTypeId", evidence: [ev(NODE_EX, 42, "FILTER"), ev(NODE_EX, 44, "MAP"), ev(NODE_EX, 70, "DEDUPLICATE"), ev(NODE_EX, 84, "EMIT"), ev(EV + "flow/runtime/EventFlowRuntimeEventFactory.java", 17, "derived events")] }
      },
      /* 16 */ {
        id: "node_pattern", n: T("Familia: ventanas, agregación y patrones", "Family: windows, aggregation and patterns"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Pasos con memoria que razonan sobre varios eventos a lo largo del tiempo.", "Steps with memory that reason about several events over time."),
        purpose: T("Detectar situaciones compuestas: cuántos llegaron, en qué orden, o cuál no llegó.", "Detect composite situations: how many arrived, in what order, or which did not arrive."),
        when: T("Cuando un solo evento no basta para decidir.", "When a single event is not enough to decide."),
        card: "1 paso ↔ 0..N eventos acumulados por clave",
        variants: [
          V("Ventana", "Window", "Acumula eventos en una ventana de un tamaño dado (por defecto 2), deslizante o no.", "Accumulates events in a window of a given size (default 2), sliding or not."),
          V("Agregación", "Aggregate", "Cuenta los eventos y, opcionalmente, suma un valor.", "Counts events and optionally sums a value."),
          V("Secuencia", "Sequence", "Exige que lleguen los tipos de evento en un orden dado; se reinicia si llega uno fuera de orden.", "Requires the event types to arrive in a given order; it resets when one arrives out of order."),
          V("Patrón", "Pattern", "Exige que lleguen todos los tipos indicados, en cualquier orden.", "Requires all the indicated types to arrive, in any order."),
          V("Ausencia", "Absence", "Programa una vigilancia: si en el plazo no llega el evento vigilado con la misma clave, emite un evento de aviso.", "Schedules a watch: if the watched event with the same key does not arrive in time, emits a warning event.")
        ],
        props: [P("Tamaño / deslizante", "Size / sliding", "OPTIONAL", "Para la ventana", "For the window"), P("Ruta de suma", "Sum path", "OPTIONAL", "Para la agregación", "For aggregation"), P("Tipos de evento", "Event types", "MANDATORY", "Para secuencia y patrón", "For sequence and pattern"), P("Evento y plazo", "Event and deadline", "MANDATORY", "Para ausencia: el evento a emitir, el vigilado y el plazo", "For absence: the event to emit, the watched one and the deadline")],
        io: { i: T("Eventos que llegan con el tiempo", "Events arriving over time"), o: T("Continuación cuando se cumple la condición compuesta", "Continuation when the composite condition is met") },
        effect: T("La ausencia se evalúa cuando el motor avanza su reloj; si llega el evento vigilado antes del plazo, la vigilancia se cancela.", "Absence is evaluated when the engine advances its clock; if the watched event arrives before the deadline the watch is cancelled."),
        func: { rel: [T("Referencia 1..N tipos de evento", "References 1..N event types")], lifecycle: T("acumula → completa → continúa", "accumulates → completes → continues"), cond: T("La ausencia exige evento; las pruebas de este comportamiento no se encontraron por nombre.", "Absence requires an event; tests for this behaviour were not found by name.") },
        tech: { type: "FlowNodeKind: WINDOW, AGGREGATE, SEQUENCE, PATTERN, ABSENCE", schema: "FlowAbsenceContinuation; configuration size/sliding, sumPath, eventTypes, timeoutMillis, watchEventTypeId", evidence: [ev(NODE_EX, 93, "WINDOW"), ev(NODE_EX, 107, "AGGREGATE"), ev(NODE_EX, 135, "SEQUENCE"), ev(NODE_EX, 151, "PATTERN"), ev(NODE_EX, 162, "ABSENCE"), ev(FLOW_RT, 143, "tick absence"), ev(FLOW_RT, 362, "cancel watch")] }
      },
      /* 17 */ {
        id: "node_recovery", n: T("Familia: recuperación y error", "Family: recovery and error"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Pasos pensados para el camino de fallos: alternativas, deshacer y tratamiento del error.", "Steps designed for the failure path: alternatives, undo and error handling."),
        purpose: T("Dar nombre y lugar en el diagrama a lo que ocurre cuando algo falla.", "Give a name and place on the diagram to what happens when something fails."),
        when: T("Cuando un fallo no debe dejar el sistema a medias.", "When a failure must not leave the system half-done."),
        card: "1 compensación ↔ 1 paso compensable ↔ 1 paso que lo deshace",
        variants: [
          V("Alternativa", "Fallback", "Deja pasar; es un destino válido de compensación.", "Lets through; a valid compensation target."),
          V("Paso de compensación", "Compensation step", "Deja pasar; es un destino válido de compensación y permite expresar el deshacer.", "Lets through; a valid compensation target that expresses the undo."),
          V("Tratamiento de error", "Error handler", "Deja pasar; marca dónde se atiende un error por la transición de error.", "Lets through; marks where an error is handled through the error transition.")
        ],
        props: [P("Referencia", "Reference", "OPTIONAL", "Acción o paso que atiende el caso", "Action or step that handles the case")],
        io: { i: T("La ejecución tras un fallo", "The run after a failure"), o: T("La ejecución continúa por el camino de recuperación", "The run continues along the recovery path") },
        effect: T("Por sí mismos no ejecutan nada: la compensación real la dispara el motor ante un fallo sin salida.", "By themselves they run nothing: real compensation is triggered by the engine on a failure with no exit."),
        func: { rel: [T("Se vincula a pasos mediante enlaces de compensación", "Linked to steps through compensation links")], lifecycle: T("fallo → camino de recuperación", "failure → recovery path"), cond: T("Solo alternativa y paso de compensación son destinos válidos de un enlace de compensación.", "Only fallback and compensation step are valid compensation link targets.") },
        tech: { type: "FlowNodeKind: FALLBACK, COMPENSATION_STEP, ERROR_HANDLER", schema: "pass-through kinds; FALLBACK/COMPENSATION_STEP valid compensation targets", evidence: [ev(NODE_EX, 36, "pass-through"), ev(CHECKER, 114, "compensation target check")] }
      },
      /* 18 */ {
        id: "transition", n: T("Transición", "Transition"), kind: "variant-set", req: "REPEATABLE",
        mean: T("La flecha entre dos pasos; su tipo determina cuándo se sigue.", "The arrow between two steps; its type determines when it is followed."),
        purpose: T("Definir el orden y las condiciones del recorrido.", "Define the order and conditions of the walk."),
        when: T("Para conectar cada paso con el siguiente.", "To connect each step to the next."),
        card: "1 paso ↔ 0..N salidas; 1 transición ↔ 1 origen ↔ 1 destino",
        variants: [
          V("Siguiente", "Next", "El camino normal (por defecto).", "The normal path (default)."),
          V("En éxito", "On success", "Se sigue si el paso acabó bien.", "Followed if the step succeeded."),
          V("En error", "On error", "Se sigue si el paso falló.", "Followed if the step failed."),
          V("Compensa", "Compensates", "Declarada, pero el motor no la usa todavía: la compensación se hace por los enlaces de compensación.", "Declared, but the engine does not use it yet: compensation is done through compensation links."),
          V("Tiempo agotado", "Timeout", "Se sigue si el paso superó su límite de tiempo.", "Followed if the step exceeded its time limit."),
          V("Verdadero", "True", "Salida de una decisión o ruta cuando la condición se cumple.", "Exit of a decision or route when the condition holds."),
          V("Falso", "False", "Salida de una decisión o ruta cuando no se cumple.", "Exit of a decision or route when it does not.")
        ],
        props: [
          P("Origen y destino", "Source and target", "MANDATORY", "Pasos existentes", "Existing steps"),
          P("Condición", "Condition", "OPTIONAL", "Debe dar verdadero o falso", "Must be true or false"),
          P("Orden", "Order", "OPTIONAL", "Ordena las salidas de un mismo paso", "Orders the exits of one step"),
          P("Etiqueta", "Label", "OPTIONAL", "Texto visible en el diagrama", "Text visible on the diagram")
        ],
        io: { i: T("Un testigo que sale de un paso", "A token leaving a step"), o: T("Un testigo que llega al siguiente", "A token arriving at the next") },
        effect: T("Si un paso falla, se busca primero la de tiempo agotado (si se agotó el tiempo) o la de error; la división paralela sigue por todas las normales o de éxito; en el resto se sigue la primera normal o de éxito, y si no hay, la primera salida.", "If a step fails, the timeout one (if time ran out) or the error one is sought first; a parallel split follows all normal or success ones; otherwise the first normal or success one is followed, falling back to the first exit."),
        func: { rel: [T("Pertenece a 1 flujo y conecta 2 pasos", "Belongs to 1 flow and connects 2 steps")], lifecycle: T("trazada → comprobada", "drawn → checked"), cond: T("Origen y destino deben existir; un paso huérfano o sin salida es un error.", "Source and target must exist; an orphan or exitless step is an error.") },
        tech: { type: "FlowTransition / FlowTransitionKind", schema: "FlowTransitionKind: NEXT, ON_SUCCESS, ON_ERROR, COMPENSATES, TIMEOUT, TRUE, FALSE", evidence: [ev(EV + "flow/FlowTransition.java", 6, "transition"), ev(EV + "flow/FlowTransitionKind.java", 4, "7 kinds"), ev(SUPPORT, 72, "selection rules"), ev(EV + "flow/runtime/EventFlowCompiler.java", 33, "sort by order")] }
      },
      /* 19 */ {
        id: "retry_timeout", n: T("Política de reintento y plazo", "Retry and time-limit policy"), kind: "concept", req: "OPTIONAL",
        mean: T("Cuántas veces se repite un paso que falla y cuánto se le deja para terminar.", "How many times a failing step is repeated and how long it is allowed to take."),
        purpose: T("Tolerar fallos puntuales y no quedar bloqueado en pasos lentos.", "Tolerate occasional failures and not get stuck on slow steps."),
        when: T("En pasos que llaman a algo externo o que pueden tardar.", "On steps that call something external or may be slow."),
        card: "1 paso ↔ 0..1 reintento ↔ 0..1 plazo",
        props: [
          P("Intentos máximos", "Maximum attempts", "OPTIONAL", "Cuántas veces se intenta", "How many times it is tried"),
          P("Espera entre intentos", "Wait between attempts", "OPTIONAL", "Si es cero, se reintenta de inmediato; si no, queda programado", "If zero it retries immediately; otherwise it is scheduled"),
          P("Multiplicador de espera", "Backoff multiplier", "OPTIONAL", "Aumenta la espera en cada intento", "Increases the wait at each attempt"),
          P("Duración límite", "Time limit", "OPTIONAL", "Si el paso tarda más, cuenta como tiempo agotado", "If the step takes longer it counts as timed out"),
          P("Transición de tiempo agotado", "Timeout transition", "CONDITIONAL", "Camino a seguir cuando se agota el plazo", "Path to follow when time runs out")
        ],
        io: { i: T("Un paso que falla o tarda", "A step that fails or is slow"), o: T("Un reintento, una espera programada o la salida de tiempo agotado", "A retry, a scheduled wait or the timeout exit") },
        effect: T("Los reintentos pendientes y los plazos se procesan cuando el motor avanza su reloj.", "Pending retries and deadlines are processed when the engine advances its clock."),
        func: { rel: [T("Pertenece a 1 paso", "Belongs to 1 step")], lifecycle: T("falla → reintenta → agota intentos → sigue la salida de error", "fails → retries → attempts exhausted → follows the error exit"), cond: T("Un tiempo medido por reloj real; la medida es de la propia ejecución del paso.", "Time is measured by wall clock; the measure is of the step's own execution.") },
        tech: { type: "RetryPolicy / TimeoutPolicy", schema: "RetryPolicy(maxAttempts, delayMillis, backoffMultiplier); TimeoutPolicy(timeoutMillis, timeoutTransitionId); FlowRetryContinuation", evidence: [ev(EV + "flow/RetryPolicy.java", 4, "retry"), ev(EV + "flow/TimeoutPolicy.java", 4, "timeout"), ev(FLOW_RT, 276, "retry handling"), ev(FLOW_RT, 330, "timeout")] }
      },
      /* 20 */ {
        id: "compensation_link", n: T("Enlace de compensación", "Compensation link"), kind: "concept", req: "OPTIONAL",
        mean: T("Una declaración de qué paso deshace el trabajo de otro cuando algo falla más tarde.", "A declaration of which step undoes another's work when something fails later."),
        purpose: T("Evitar que un fallo deje efectos a medias.", "Avoid leaving half-done effects after a failure."),
        when: T("Cuando un paso ya ejecutado tiene un efecto que hay que revertir si el flujo no llega a buen puerto.", "When an executed step has an effect that must be reverted if the flow does not succeed."),
        card: "1 flujo ↔ 0..N enlaces; 1 enlace ↔ 1 paso compensable ↔ 1 paso que compensa",
        props: [P("Paso compensado", "Compensated step", "MANDATORY", "El paso cuyo efecto se deshace", "The step whose effect is undone"), P("Paso compensador", "Compensating step", "MANDATORY", "Debe ser alternativa o paso de compensación", "Must be a fallback or compensation step"), P("Identificador", "Identifier", "MANDATORY", "Los tres identificadores son obligatorios", "All three identifiers are mandatory")],
        io: { i: T("Un fallo sin salida de error", "A failure with no error exit"), o: T("Los pasos compensables completados se deshacen en orden inverso", "Completed compensable steps are undone in reverse order") },
        effect: T("El resultado queda «compensado» si al menos una compensación se ejecutó; si no, «fallido».", "The result is “compensated” if at least one compensation ran; otherwise “failed”."),
        func: { rel: [T("Pertenece a 1 flujo y une 2 pasos", "Belongs to 1 flow and links 2 steps")], lifecycle: T("declarado → comprobado → usado al fallar", "declared → checked → used on failure"), cond: T("Solo actúa cuando el paso fallido no tiene transición utilizable.", "Only acts when the failed step has no usable transition.") },
        tech: { type: "CompensationLink", schema: "CompensationLink (id, nodeId, compensationNodeId — all mandatory)", evidence: [ev(EV + "flow/CompensationLink.java", 6, "link"), ev(CHECKER, 111, "compensation checks"), ev(FLOW_RT, 342, "LIFO compensation")] }
      },
      /* 21 */ {
        id: "execution_trace", n: T("Ejecución y traza", "Execution and trace"), kind: "derived", req: "DERIVED",
        mean: T("El registro de qué pasos se recorrieron en cada ejecución de un flujo y con qué resultado.", "The record of which steps were walked in each run of a flow and with what result."),
        purpose: T("Entender y depurar lo que hizo un flujo.", "Understand and debug what a flow did."),
        when: T("Después de enviar un evento de prueba o de que el flujo reaccione a uno real.", "After sending a test event or when the flow reacts to a real one."),
        card: "1 evento ↔ 0..N trazas (una por flujo disparado)",
        variants: [
          V("Completado", "Completed", "Todas las ramas llegaron al final.", "All branches reached their end."),
          V("Filtrado", "Filtered", "Una condición no se cumplió y la ejecución se descartó.", "A condition was not met and the run was dropped."),
          V("En espera", "Waiting", "Hay un paso esperando un evento, un reintento o un plazo.", "A step is waiting for an event, a retry or a deadline."),
          V("Fallido", "Failed", "Falló un paso sin salida ni compensación.", "A step failed with no exit and no compensation."),
          V("Compensado", "Compensated", "Se deshicieron pasos tras un fallo.", "Steps were undone after a failure."),
          V("Límite de profundidad", "Depth limit", "La cadena de eventos derivados llegó a 32 niveles.", "The chain of derived events reached 32 levels.")
        ],
        props: [P("Pasos ejecutados", "Executed steps", "DERIVED", "Lista con el resultado de cada paso", "List with each step's result"), P("Estado del motor", "Engine status", "DERIVED", "Activo, flujos cargados, eventos procesados y emitidos, estados, último fallo", "Active, loaded flows, processed and emitted events, states, last failure")],
        io: { i: T("Una ejecución", "A run"), o: T("Una traza consultable (se conservan las últimas 200)", "A queryable trace (the last 200 are kept)") },
        effect: T("El estado de una traza se sobrescribe en vez de agregarse: puede leerse «filtrado» o «en espera» aunque otras ramas hayan completado.", "A trace's status is overwritten instead of aggregated: it may read “filtered” or “waiting” although other branches completed."),
        func: { rel: [T("Pertenece a 1 flujo y 1 evento disparador", "Belongs to 1 flow and 1 trigger event")], lifecycle: T("se crea al ejecutar → se consulta → se descarta al llegar al máximo", "created on run → queried → dropped at the maximum"), cond: T("Solo se registra si el flujo estaba desplegado.", "Only recorded if the flow was deployed.") },
        tech: { type: "FlowExecutionTrace / FlowNodeExecution / FlowExecutionStatus / EventFlowRuntimeStatus", schema: "FlowExecutionStatus: COMPLETED, FILTERED, WAITING, FAILED, COMPENSATED, DEPTH_LIMIT; limits (EventFlowProperties): maximumExecutionTraces 200, maximumExecutionDepth 32, maximumRuntimeStates 256, maximumRuntimeDeployments 128", evidence: [ev(EV + "flow/runtime/FlowExecutionStatus.java", 2, "statuses"), ev(EV + "flow/EventFlowProperties.java", 17, "limits"), ev(FLOW_RT, 251, "status overwrite")] }
      },
      /* 22 */ {
        id: "diagnostics", n: T("Diagnósticos de comprobación", "Check diagnostics"), kind: "derived", req: "DERIVED",
        mean: T("Los errores, avisos e informaciones que HYDRA muestra al comprobar el modelo de eventos o el de flujos.", "The errors, warnings and information HYDRA shows when checking the event model or the flow model."),
        purpose: T("Detectar antes de ejecutar los problemas que impedirían compilar el flujo o que harían el enlace inválido.", "Detect before running the problems that would prevent compiling the flow or make the binding invalid."),
        when: T("Al pulsar Comprobar y antes de arrancar el motor.", "When pressing Check and before starting the engine."),
        card: "1 comprobación ↔ 0..N diagnósticos",
        variants: [
          V("Error", "Error", "Impide compilar o es inválido: tipo reservado redefinido, ids duplicados, flujo sin entrada única o sin final, decisión sin ramas verdadero y falso, unión con menos de dos entradas, transiciones a pasos inexistentes, pasos huérfanos o sin salida, destino de enlace reservado.", "Prevents compiling or is invalid: reserved type redefined, duplicate ids, flow without a single entry or without an end, decision lacking true and false branches, join with fewer than two inputs, transitions to missing steps, orphan or exitless steps, reserved binding target."),
          V("Aviso", "Warning", "Atención sin bloquear: espacio de nombres distinto al del modelo.", "Attention without blocking: namespace different from the model's."),
          V("Información", "Information", "Nota: un paso es un destino de compensación.", "Note: a step is a compensation target.")
        ],
        props: [P("Código, elemento y ruta", "Code, element and path", "DERIVED", "Dónde está el problema", "Where the problem is"), P("Mensaje", "Message", "DERIVED", "Qué corregir", "What to fix")],
        io: { i: T("El modelo de eventos o el de flujos", "The event model or the flow model"), o: T("Lista de diagnósticos", "List of diagnostics") },
        effect: T("El diseñador de flujos añade una lista de preparación en el cliente para guiar antes de comprobar.", "The flow designer adds a client-side readiness checklist to guide before checking."),
        func: { rel: [T("Se refiere a 1 elemento del modelo", "Refers to 1 element of the model")], lifecycle: T("comprobar → mostrar → corregir → volver a comprobar", "check → display → fix → check again"), cond: T("Un modelo de flujos con errores no se compila.", "A flow model with errors does not compile.") },
        tech: { type: "EventDiagnostic", schema: "EventDiagnostic(severity ERROR|WARNING|INFO, code, elementId, path, message); codes: RESERVED_EVENT_TYPE, FOREIGN_NAMESPACE, DUPLICATE_FLOW_ID, DUPLICATE_NODE_ID, EVENT_TYPE_REQUIRED, UNKNOWN_EVENT_TYPE, FLOW_START_COUNT, FLOW_END_REQUIRED, DUPLICATE_TRANSITION_ID, UNKNOWN_TRANSITION_SOURCE/TARGET, ORPHAN_NODE, DEAD_END_NODE, DECISION_BRANCHES, JOIN_INPUT_COUNT, UNREACHABLE_NODE, UNKNOWN_TRIGGER_EVENT, COMPENSATION_NODE (INFO)", evidence: [ev(CHECKER, 57, "DUPLICATE_FLOW_ID"), ev(CHECKER, 96, "ORPHAN_NODE"), ev(CHECKER, 151, "UNREACHABLE_NODE"), ev(FE + "EventFlowDesignerParts.tsx", 82, "readiness checklist")] }
      }
    ],

    map: {
      nodes: [
        { id: "m_type", elem: "event_type", n: T("Tipo de evento", "Event type"), k: "concept", req: "MANDATORY", d: T("Un hecho que puede ocurrir", "A fact that can happen") },
        { id: "m_field", elem: "event_field", n: T("Campo de datos", "Data field"), k: "concept", req: "REPEATABLE", d: T("Un dato que viaja con el evento", "A datum that travels with the event") },
        { id: "m_reserved", n: T("Evento reservado", "Reserved event"), k: "external", req: "ALTERNATIVE", d: T("32 eventos del sistema", "32 system events") },
        { id: "m_event", elem: "canonical_event", n: T("Evento en circulación", "Event in flight"), k: "derived", req: "DERIVED", d: T("Un hecho concreto con origen, contexto y datos", "A concrete fact with source, context and data") },
        { id: "m_binding", elem: "binding", n: T("Enlace", "Binding"), k: "concept", req: "OPTIONAL", d: T("Cuando ocurra A, emite B", "When A happens, emit B") },
        { id: "m_copy", elem: "payload_mapping", n: T("Copia de datos", "Data copy"), k: "concept", req: "REPEATABLE", d: T("Rellena un campo del destino", "Fills a target field") },
        { id: "m_flow", elem: "flow", n: T("Flujo", "Flow"), k: "concept", req: "OPTIONAL", d: T("Diagrama que se ejecuta ante un evento", "Diagram run on an event") },
        { id: "m_var", elem: "flow_variable", n: T("Variable", "Variable"), k: "concept", req: "OPTIONAL", d: T("Valor propio del flujo", "Value owned by the flow") },
        { id: "m_step", elem: "flow_node", n: T("Paso", "Step"), k: "concept", req: "MANDATORY", d: T("Caja del diagrama; 29 tipos en 9 familias", "Diagram box; 29 types in 9 families") },
        { id: "m_family", n: T("Familia de paso", "Step family"), k: "variant", req: "ALTERNATIVE", d: T("Entradas y finales, evento, externos, encaminamiento, paralelo, tiempo, transformación, patrones, recuperación", "Entries and ends, event, external, routing, parallel, timing, transformation, patterns, recovery") },
        { id: "m_trans", elem: "transition", n: T("Transición", "Transition"), k: "concept", req: "REPEATABLE", d: T("Siguiente, éxito, error, tiempo, verdadero, falso", "Next, success, error, timeout, true, false") },
        { id: "m_policy", elem: "retry_timeout", n: T("Reintento y plazo", "Retry and time limit"), k: "concept", req: "OPTIONAL", d: T("Tolerancia a fallos de un paso", "A step's fault tolerance") },
        { id: "m_comp", elem: "compensation_link", n: T("Compensación", "Compensation"), k: "concept", req: "OPTIONAL", d: T("Deshacer un paso", "Undo a step") },
        { id: "m_trace", elem: "execution_trace", n: T("Traza", "Trace"), k: "derived", req: "DERIVED", d: T("Registro de una ejecución", "Record of a run") },
        { id: "m_diag", elem: "diagnostics", n: T("Diagnóstico", "Diagnostic"), k: "derived", req: "DERIVED", d: T("Error, aviso o información de la comprobación", "Check error, warning or information") }
      ],
      edges: [
        { a: "m_type", b: "m_field", rel: "contains", card1: "1", card2: "0..N", req: "OPTIONAL" },
        { a: "m_reserved", b: "m_type", rel: "isa", card1: "", card2: "" },
        { a: "m_binding", b: "m_type", rel: "ref", card1: "0..N", card2: "2", l: T("origen y destino", "source and target") },
        { a: "m_binding", b: "m_copy", rel: "contains", card1: "1", card2: "0..N", req: "OPTIONAL" },
        { a: "m_copy", b: "m_field", rel: "ref", card1: "0..1", card2: "1", l: T("rellena", "fills") },
        { a: "m_flow", b: "m_type", rel: "ref", card1: "0..N", card2: "0..N", req: "OPTIONAL", l: T("disparadores", "triggers") },
        { a: "m_flow", b: "m_var", rel: "contains", card1: "1", card2: "0..N", req: "OPTIONAL" },
        { a: "m_flow", b: "m_step", rel: "contains", card1: "1", card2: "1..N" },
        { a: "m_step", b: "m_family", rel: "isa", card1: "", card2: "" },
        { a: "m_flow", b: "m_trans", rel: "contains", card1: "1", card2: "0..N" },
        { a: "m_step", b: "m_policy", rel: "contains", card1: "1", card2: "0..1", req: "OPTIONAL" },
        { a: "m_flow", b: "m_comp", rel: "contains", card1: "1", card2: "0..N", req: "OPTIONAL" },
        { a: "m_event", b: "m_flow", rel: "ref", card1: "0..N", card2: "0..N", l: T("dispara", "triggers") },
        { a: "m_flow", b: "m_trace", rel: "gen", card1: "1", card2: "0..N" },
        { a: "m_binding", b: "m_event", rel: "gen", card1: "1", card2: "0..N", l: T("deriva", "derives") },
        { a: "m_flow", b: "m_diag", rel: "gen", card1: "1", card2: "0..N", req: "OPTIONAL", l: T("al comprobar", "on check") }
      ]
    },

    define: [
      { id: "form", n: T("Formulario del diseñador de eventos", "Event designer form"), kind: "form", req: "ALTERNATIVE", elems: ["event_type", "event_field", "binding", "payload_mapping"],
        does: T("En la vista de formulario (la vista por defecto) abre el diseñador de eventos de un metamodelo. Revisa el espacio de nombres del modelo (propone «editor.» más el identificador del metamodelo). Pulsa para añadir un tipo de evento: aparece con un nombre por defecto, categoría «dominio», persistencia transitoria y versión 1.0. Cambia nombre, descripción y categoría (la lista ofrece valores y una opción personalizada). Añade campos (por defecto «value», de tipo texto) y elige su tipo; si es enumerado o de objeto, indica además cuál. Para un enlace, elige origen y destino, una condición, las copias de datos y la política de error. Una vista de estructura muestra el árbol completo. Pulsa Guardar.",
          "In the form view (the default) open a metamodel's event designer. Review the model namespace (it proposes “editor.” plus the metamodel identifier). Press to add an event type: it appears with a default name, category “domain”, transient persistence and version 1.0. Change name, description and category (the list offers values and a custom option). Add fields (default “value”, text type) and choose their type; if enumeration or object, also say which. For a binding, choose source and target, a condition, data copies and the error policy. A structure view shows the whole tree. Press Save."),
        func: T("Solo los tipos propios son editables; los reservados se muestran como referencia. Los campos obligatorios de un destino deben quedar mapeados.", "Only custom types are editable; reserved ones are shown for reference. Required fields of a target must be mapped."),
        tech: T("EventDesigner.tsx (vistas FORM, STRUCTURE, ARCHITECTURE, SOURCE), eventDesignerFactory.ts, eventDesignerValidation.ts, eventCategoryOptions.ts; POST/PUT .../events con If-Match-Definitions / If-Match-Bindings.", "EventDesigner.tsx (views FORM, STRUCTURE, ARCHITECTURE, SOURCE), eventDesignerFactory.ts, eventDesignerValidation.ts, eventCategoryOptions.ts; POST/PUT .../events with If-Match-Definitions / If-Match-Bindings."),
        proc: "events_author_form" },
      { id: "arch_connect", n: T("Diagrama de arquitectura de eventos (conectar)", "Event architecture diagram (connect)"), kind: "canvas", req: "ALTERNATIVE", elems: ["binding", "event_type"],
        does: T("En la vista de arquitectura se ven los eventos reservados (solo lectura) y los propios como cajas. Arrastra una conexión desde un evento de origen hasta un evento propio de destino: se crea un enlace activo, sin condición ni copias y con la política de avisar. Selecciona el enlace para poner la condición y las copias de datos. Guarda: se guardan los modelos y también la posición de las cajas.",
          "In the architecture view reserved events (read-only) and custom ones show as boxes. Drag a connection from a source event to a custom target event: an enabled binding is created, with no condition or copies and the report policy. Select the binding to set the condition and data copies. Save: the models and the box positions are saved."),
        func: T("La conexión se rechaza si el origen no existe, el destino no es propio, es un bucle sobre sí mismo o ya existe el mismo par.", "The connection is rejected if the source does not exist, the target is not custom, it loops onto itself or the same pair already exists."),
        tech: T("EventDesigner.tsx (nodos RESERVED_EVENT / CUSTOM_EVENT), eventConnections.ts applyEventConnection; GET/PUT .../events/diagram con If-Match; GET .../events/reserved-types.", "EventDesigner.tsx (RESERVED_EVENT / CUSTOM_EVENT nodes), eventConnections.ts applyEventConnection; GET/PUT .../events/diagram with If-Match; GET .../events/reserved-types."),
        proc: "events_author_connect" },
      { id: "flow_canvas", n: T("Diseñador de flujos (lienzo)", "Flow designer (canvas)"), kind: "canvas", req: "ALTERNATIVE", elems: ["flow", "flow_variable", "flow_node", "transition", "retry_timeout", "compensation_link"],
        does: T("Añade un flujo y dale identificador, nombre y eventos disparadores. En la paleta elige entre los 29 tipos de paso y arrástralo (un paso nuevo es un paso de evento por defecto). Configura cada paso según su familia: evento, referencia, condición, reintento y plazo. Conecta los pasos con transiciones y elige su tipo y su etiqueta. Declara variables y enlaces de compensación en las secciones del panel de estructura. Pulsa Comprobar (junto a una lista de preparación en pantalla) y Guardar; la posición de las cajas se guarda aparte.",
          "Add a flow and give it an identifier, name and trigger events. From the palette choose among the 29 step types and drag one in (a new step is an event step by default). Configure each step by family: event, reference, condition, retry and time limit. Connect steps with transitions and choose their type and label. Declare variables and compensation links in the structure panel sections. Press Check (along with an on-screen readiness checklist) and Save; box positions are saved separately."),
        func: T("El diseño no implica ejecutarlo: el motor solo compila flujos cuya comprobación es correcta.", "Designing does not imply running: the engine only compiles flows whose check is clean."),
        tech: T("EventFlowDesigner.tsx (paleta de 29 tipos, addNode), EventFlowHighLevelProperties.tsx, eventFlowStructureModel.ts, eventFlowFactory.ts, EventFlowDesignerParts.tsx; POST .../event-flows/check; POST/PUT .../event-flows (If-Match); PUT .../event-flows/diagram.", "EventFlowDesigner.tsx (29-kind palette, addNode), EventFlowHighLevelProperties.tsx, eventFlowStructureModel.ts, eventFlowFactory.ts, EventFlowDesignerParts.tsx; POST .../event-flows/check; POST/PUT .../event-flows (If-Match); PUT .../event-flows/diagram."),
        proc: "events_author_flow" },
      { id: "source_json", n: T("Código fuente (JSON)", "Source JSON"), kind: "text", req: "ALTERNATIVE", elems: ["event_type", "binding"],
        does: T("Cambia a la vista de código fuente del diseñador de eventos para ver y editar el modelo como JSON. Modifica los tipos de evento y los enlaces directamente, vuelve a la vista de formulario o de arquitectura para ver el resultado y guarda.",
          "Switch to the event designer's source view to see and edit the model as JSON. Modify event types and bindings directly, return to the form or architecture view to see the outcome and save."),
        func: T("Es la misma información que los demás modos; las reglas de comprobación son idénticas.", "Same information as the other modes; the checking rules are identical."),
        tech: T("Vista SOURCE de EventDesigner.tsx; normalización eventModelNormalization.ts.", "SOURCE view of EventDesigner.tsx; normalization in eventModelNormalization.ts."),
        proc: "events_author_source" },
      { id: "runtime_panel", n: T("Panel de ejecución de flujos", "Flow run panel"), kind: "api", req: "OPTIONAL", elems: ["execution_trace", "flow"],
        does: T("En el panel de ejecución arranca, detén o actualiza el motor de flujos; compila el modelo guardado. Envía un evento de prueba y observa el estado, las trazas y el estado interno; avanza el reloj para que se procesen reintentos, plazos de espera y ausencias. Puedes borrar el estado guardado.",
          "In the run panel start, stop or refresh the flow engine; it compiles the saved model. Send a test event and watch the status, the traces and the internal state; advance the clock so retries, wait deadlines and absences get processed. You can clear the stored state."),
        func: T("Sin arrancarlo a mano, el motor se activa solo al llegar el primer evento de un metamodelo, salvo que se haya detenido explícitamente.", "Without starting it by hand the engine activates itself when the first event of a metamodel arrives, unless it was explicitly stopped."),
        tech: T("EventFlowRuntimePanel.tsx; POST .../event-flows/runtime/{start|stop|refresh}, GET .../status, POST .../events, POST .../tick, GET .../traces, GET/DELETE .../state; EventFlowRuntimeController.", "EventFlowRuntimePanel.tsx; POST .../event-flows/runtime/{start|stop|refresh}, GET .../status, POST .../events, POST .../tick, GET .../traces, GET/DELETE .../state; EventFlowRuntimeController."),
        proc: "events_author_run" }
    ],

    rel: [
      { to: "model_editor", type: "event", dir: "in", u: T("Cada vez que guardas, creas, editas o borras en el editor se emiten eventos.", "Every time you save, create, edit or delete in the editor, events are emitted."), f: T("Los eventos de edición se guardan en un búfer mientras no hay guardado y se publican al guardar con éxito (o de inmediato en autoguardado).", "Editing events are buffered until a save and published when the save succeeds (or immediately on autosave)."), t: T("EditorModelSessionService.publish (:736-751) → EditorSessionEventPublisher → bus.", "EditorModelSessionService.publish (:736-751) → EditorSessionEventPublisher → bus.") },
      { to: "gamification", type: "event", dir: "out", u: T("La gamificación reacciona a los mismos eventos y emite los suyos.", "Gamification reacts to the same events and emits its own."), f: T("Sus eventos de premio, penalización, rechazo y compensación son reservados, así que los flujos pueden consumirlos.", "Its award, penalty, rejection and compensation events are reserved so flows can consume them."), t: T("GamificationRuntimeService se suscribe al bus (:83-85) y publica los core.gamification.* (:307-312).", "GamificationRuntimeService subscribes to the bus (:83-85) and publishes core.gamification.* (:307-312).") },
      { to: "validation", type: "producer", dir: "in", u: T("Las validaciones y correcciones producen eventos que puedes usar como disparadores.", "Validations and fixes produce events you can use as triggers."), f: T("Ejecución de validación completada, corrección aplicada o deshecha, violación reabierta.", "Validation execution completed, fix applied or undone, violation reopened."), t: T("ValidationOrchestrator.java:164-168; QuickFixRuntimeService.java:142,167,266-275.", "ValidationOrchestrator.java:164-168; QuickFixRuntimeService.java:142,167,266-275.") },
      { to: "metamodel", type: "required", dir: "out", u: T("Los eventos y flujos se definen sobre un metamodelo guardado.", "Events and flows are defined over a saved metamodel."), f: T("Cada modelo de eventos y de flujos pertenece a un metamodelo; los pasos de operación llegan a sus operaciones.", "Each event model and flow model belongs to a metamodel; operation steps reach its operations."), t: T("metamodelId en EventDefinitionModel / EventFlowModel; OperationFlowExternalStepExecutor.", "metamodelId in EventDefinitionModel / EventFlowModel; OperationFlowExternalStepExecutor.") },
      { to: "grammar", type: "event", dir: "in", u: T("Las capas de texto compartidas también emiten eventos.", "The shared text layers also emit events."), f: T("Emiten eventos reservados al operar la capa textual compartida.", "They emit reserved events when operating the shared textual layer."), t: T("TextualSharedLayerRuntimeService.java:62,78,83.", "TextualSharedLayerRuntimeService.java:62,78,83.") }
    ]
  });

  /* =============================== PROCESOS DE CAPACIDAD =============================== */

  HM.proc({
    id: "events_define", scope: "cap", cap: "events", elems: ["event_type", "event_field", "binding", "payload_mapping", "diagnostics"],
    n: T("Definir tipos de evento y enlaces", "Define event types and bindings"),
    d: T("Del primer tipo de evento a un modelo comprobado y guardado.", "From the first event type to a checked, saved model."),
    nodes: [
      N("s", "start", null, null, "Necesito reaccionar a un hecho", "I need to react to something", "Empieza cuando un hecho propio de tu lenguaje merece tener nombre.", "Starts when a fact specific to your language deserves a name."),
      N("a1", "step", "user", "MANDATORY", "Definir el tipo de evento", "Define the event type", "Das un identificador, un nombre y un espacio de nombres para poder referirte al hecho.", "You give an identifier, name and namespace so you can refer to the fact.", { need: ["Un metamodelo guardado", "A saved metamodel"], out: ["Un tipo de evento", "An event type"], e: "event_type", f: ["El nombre no puede estar en blanco; el espacio empieza por letra.", "The name cannot be blank; the namespace starts with a letter."], x: { rt: "EventTypeDefinition", test: ["EventContractTests"], ev: [ev(EV + "EventTypeDefinition.java", 29, "namespace regex")] } }),
      N("a2", "step", "user", "OPTIONAL", "Añadir campos de datos", "Add data fields", "Declaras qué datos lleva el evento y de qué tipo, para poder usarlos después.", "You declare what data the event carries and of which type, to use it afterwards.", { out: ["Campos tipados", "Typed fields"], e: "event_field" }),
      N("a3", "step", "user", "OPTIONAL", "Crear un enlace entre eventos", "Create a binding between events", "Indicas qué evento deriva en cuál, con una condición y la política de error.", "You say which event derives into which, with a condition and the error policy.", { need: ["Un evento de origen y uno propio de destino", "A source event and a custom target event"], out: ["Un enlace", "A binding"], e: "binding", f: ["El destino debe ser un evento propio.", "The target must be a custom event."] }),
      N("a4", "step", "user", "CONDITIONAL", "Definir las copias de datos", "Define the data copies", "Dices cómo se rellena cada campo del destino: ruta, expresión o valor fijo.", "You say how each target field is filled: path, expression or fixed value.", { out: ["Copias de datos", "Data copies"], e: "payload_mapping", f: ["Todos los campos obligatorios del destino deben cubrirse.", "All required target fields must be covered."] }),
      N("a5", "step", "auto", "MANDATORY", "Comprobar el modelo", "Check the model", "HYDRA revisa reservados, duplicados, espacios de nombres y mapeos antes de guardar.", "HYDRA reviews reserved events, duplicates, namespaces and mappings before saving.", { out: ["Lista de diagnósticos", "List of diagnostics"], e: "diagnostics", x: { rt: "EventModelChecker", test: ["EventModelCheckerTests"], ev: [ev(EV + "EventModelChecker.java", 18, "reserved check")] } }),
      N("d1", "decision", "auto", null, "¿Hay errores?", "Any errors?", "Los errores bloquean un modelo válido; los avisos y la información no.", "Errors block a valid model; warnings and information do not."),
      N("a6", "step", "user", "MANDATORY", "Guardar el modelo", "Save the model", "Persistes tipos y enlaces con control de versiones para no pisar cambios ajenos.", "You persist types and bindings with version control so you do not overwrite others' changes.", { out: ["Modelo de eventos guardado", "Event model saved"], x: { rt: "CanonicalEventModelController", test: ["CanonicalEventModelControllerTests"], ev: [ev(FE + "api.ts", 10, "save with If-Match")] } }),
      N("r1", "artifact", null, null, "Modelo de eventos", "Event model", "El resultado: tipos, campos y enlaces disponibles para flujos y enlaces.", "The result: types, fields and bindings available to flows and bindings."),
      N("e", "end", null, null, "Fin", "End")
    ],
    edges: [["s", "a1"], ["a1", "a2"], ["a2", "a3", "opt", T("Si hace falta enlazar", "If binding is needed")], ["a3", "a4", "cond"], ["a2", "a5"], ["a4", "a5"], ["a5", "d1"], ["d1", "a1", "loop", T("Sí: corregir", "Yes: fix")], ["d1", "a6", "next", T("No", "No")], ["a6", "r1"], ["r1", "e"]]
  });
  /* The above uses 'u' with missing EN in artifact; fix below by patching */

  HM.proc({
    id: "events_compose_flow", scope: "cap", cap: "events", elems: ["flow", "flow_variable", "flow_node", "transition", "retry_timeout", "compensation_link", "diagnostics"],
    n: T("Componer un flujo", "Compose a flow"),
    d: T("Cómo se construye un flujo válido y compilable.", "How a valid, compilable flow is built."),
    nodes: [
      N("s", "start", null, null, "Quiero automatizar una reacción", "I want to automate a reaction", "Una reacción con ramas, esperas o acciones necesita un flujo.", "A reaction with branches, waits or actions needs a flow."),
      N("a1", "step", "user", "MANDATORY", "Crear el flujo y elegir disparadores", "Create the flow and choose triggers", "Das identidad al flujo y dices qué eventos lo arrancan.", "You give the flow an identity and say which events start it.", { out: ["Un flujo vacío", "An empty flow"], e: "flow" }),
      N("a2", "step", "user", "MANDATORY", "Añadir un punto de entrada", "Add an entry point", "Todo flujo necesita exactamente un inicio o una entrada de evento.", "Every flow needs exactly one start or event source.", { e: "node_entry_exit", f: ["Cero o varios puntos de entrada es un error.", "Zero or several entry points is an error."] }),
      N("a3", "step", "user", "MANDATORY", "Añadir pasos por familia", "Add steps by family", "Eliges de las nueve familias: evento, externos, decisión, paralelo, tiempo, transformación, patrones y recuperación.", "You choose from the nine families: event, external, decision, parallel, timing, transformation, patterns and recovery.", { out: ["Pasos configurados", "Configured steps"], e: "flow_node", x: { rt: "FlowNodeKind (29)", test: ["EventFlowCheckerTests"], ev: [ev(NODEKIND, 5, "29 kinds")] } }),
      N("a4", "step", "user", "MANDATORY", "Conectar con transiciones", "Connect with transitions", "Defines el orden; las decisiones necesitan una salida verdadera y otra falsa.", "You define the order; decisions need a true and a false exit.", { e: "transition", f: ["Las uniones necesitan al menos dos entradas.", "Joins need at least two inputs."] }),
      N("a5", "step", "user", "OPTIONAL", "Añadir reintentos, plazos y compensaciones", "Add retries, time limits and compensations", "Das tolerancia a fallos a los pasos que actúan fuera o esperan.", "You give fault tolerance to steps that act outside or wait.", { e: "retry_timeout" }),
      N("a6", "step", "user", "OPTIONAL", "Declarar variables", "Declare variables", "Valores propios del flujo.", "Values owned by the flow.", { e: "flow_variable" }),
      N("a7", "step", "auto", "MANDATORY", "Comprobar el flujo", "Check the flow", "Se revisan entradas, finales, ramas, uniones, huérfanos, alcance y compensaciones.", "Entries, ends, branches, joins, orphans, reachability and compensations are reviewed.", { e: "diagnostics", x: { rt: "EventFlowChecker", test: ["EventFlowCheckerTests"], ev: [ev(CHECKER, 57, "checks")] } }),
      N("d1", "decision", "auto", null, "¿Hay errores?", "Any errors?", "Con errores el flujo no se puede compilar.", "With errors the flow cannot be compiled."),
      N("a8", "step", "user", "MANDATORY", "Guardar", "Save", "Se guarda el modelo y la posición de las cajas.", "The model and the box positions are saved."),
      N("r1", "artifact", null, null, "Flujo guardado", "Saved flow", "Queda listo para compilarse y activarse.", "It is ready to be compiled and activated."),
      N("e", "end", null, null, "Fin", "End")
    ],
    edges: [["s", "a1"], ["a1", "a2"], ["a2", "a3"], ["a3", "a4"], ["a4", "a5", "opt"], ["a4", "a6", "opt"], ["a5", "a7"], ["a6", "a7"], ["a4", "a7"], ["a7", "d1"], ["d1", "a3", "loop", T("Sí: corregir", "Yes: fix")], ["d1", "a8", "next", T("No", "No")], ["a8", "r1"], ["r1", "e"]]
  });

  HM.proc({
    id: "events_execute_flow", scope: "cap", cap: "events", elems: ["flow", "flow_node", "transition", "retry_timeout", "compensation_link", "execution_trace", "canonical_event"],
    n: T("Ejecutar un flujo", "Execute a flow"),
    d: T("Desde la llegada de un evento hasta la traza y los eventos derivados.", "From an event's arrival to the trace and derived events."),
    nodes: [
      N("s", "start", null, null, "Llega un evento", "An event arrives", "Cualquier evento publicado en el canal pone el motor a trabajar.", "Any event published on the channel puts the engine to work."),
      N("d0", "decision", "auto", null, "¿Detenido a mano?", "Stopped by hand?", "Un motor detenido explícitamente no se reactiva solo.", "An explicitly stopped engine does not reactivate itself.", { f: ["Si no se detuvo, el motor se despliega solo con el primer evento.", "If not stopped, the engine deploys itself at the first event."] }),
      N("a1", "step", "auto", "MANDATORY", "Reanudar esperas y cancelar vigilancias", "Resume waits and cancel watches", "Se reanudan los pasos que esperaban este evento y se cancelan las vigilancias de ausencia cuyo evento llegó.", "Steps awaiting this event resume and absence watches whose event arrived are cancelled.", { x: { rt: "EventFlowRuntimeService.processWithDeployment", test: ["EventFlowRuntimeStateTests"], ev: [ev(FLOW_RT, 205, "processWithDeployment")] } }),
      N("a2", "step", "auto", "MANDATORY", "Elegir los flujos disparados", "Pick the triggered flows", "Se buscan los flujos cuyo disparador coincide con el tipo del evento.", "Flows whose trigger matches the event's type are looked up.", { e: "flow", x: { rt: "EventFlowCompiler trigger index", test: ["EventFlowCompilerTests"], ev: [ev(EV + "flow/runtime/EventFlowCompiler.java", 39, "trigger index")] } }),
      N("a3", "step", "auto", "MANDATORY", "Recorrer los pasos", "Walk the steps", "Los pasos se procesan en una cola, uno tras otro; las ramas se turnan.", "Steps are processed in a queue, one after another; branches take turns.", { e: "flow_node", f: ["No es concurrente.", "It is not concurrent."], x: { rt: "executeFlow", test: [], ev: [ev(FLOW_RT, 240, "executeFlow")] } }),
      N("d1", "decision", "auto", null, "¿Se cumple la condición del paso?", "Does the step's condition hold?", "Si no se cumple, la ejecución queda filtrada y este testigo se descarta.", "If it does not, the run is filtered and this token is dropped.", { s: ["traza: en curso → filtrada", "trace: running → filtered"] }),
      N("a4", "step", "auto", "MANDATORY", "Ejecutar el paso", "Execute the step", "Según su tipo: filtra, decide, emite, espera, o pide a otra parte de HYDRA que actúe.", "By its type: filters, decides, emits, waits, or asks another part of HYDRA to act.", { x: { rt: "EventFlowRuntimeNodeExecutor", test: [], ev: [ev(NODE_EX, 36, "behaviour by kind")] } }),
      N("d2", "decision", "auto", null, "¿Falló o venció el plazo?", "Did it fail or time out?", "El fallo o el exceso de tiempo activan el reintento, la salida de error o la compensación.", "Failure or excess time triggers retry, the error exit or compensation.", { e: "retry_timeout" }),
      N("a5", "step", "auto", "OPTIONAL", "Reintentar o aparcar", "Retry or park", "Si hay intentos, se repite al instante o se programa; el paso queda en espera.", "If attempts remain it repeats at once or is scheduled; the step is waiting.", { e: "retry_timeout", s: ["traza: en curso → en espera", "trace: running → waiting"] }),
      N("a6", "step", "auto", "CONDITIONAL", "Compensar en orden inverso", "Compensate in reverse order", "Sin salida utilizable se deshacen los pasos compensables ya completados.", "With no usable exit, completed compensable steps are undone.", { e: "compensation_link", s: ["traza: en curso → compensada o fallida", "trace: running → compensated or failed"] }),
      N("a7", "step", "auto", "MANDATORY", "Seguir la transición elegida", "Follow the chosen transition", "Se elige la salida según el resultado y el tipo de paso; la rama acaba si no hay salida.", "The exit is chosen by the result and step type; a branch ends if there is none.", { e: "transition", s: ["traza: en curso → completada", "trace: running → completed"] }),
      N("a8", "step", "auto", "MANDATORY", "Guardar traza y estado", "Save trace and state", "Se registra qué ocurrió y se guarda el estado del motor.", "What happened is recorded and the engine state is saved.", { e: "execution_trace" }),
      N("a9", "step", "auto", "OPTIONAL", "Publicar eventos derivados", "Publish derived events", "Los eventos emitidos vuelven al canal y pueden disparar otros flujos o enlaces.", "Emitted events go back to the channel and may trigger other flows or bindings.", { e: "canonical_event", x: { rt: "EventFlowRuntimeEventFactory", test: [], ev: [ev(EV + "flow/runtime/EventFlowRuntimeEventFactory.java", 17, "derived event")] } }),
      N("r1", "artifact", null, null, "Traza de ejecución", "Execution trace", "El registro por flujo disparado que puedes consultar.", "The per-triggered-flow record you can query."),
      N("e", "end", null, null, "Fin", "End")
    ],
    edges: [["s", "d0"], ["d0", "e", "alt", T("Sí: ignorar", "Yes: ignore")], ["d0", "a1", "next", T("No", "No")], ["a1", "a2"], ["a2", "a3"], ["a3", "d1"], ["d1", "a8", "alt", T("No: filtrada", "No: filtered")], ["d1", "a4", "next", T("Sí", "Yes")], ["a4", "d2"], ["d2", "a7", "next", T("No", "No")], ["d2", "a5", "opt", T("Sí: hay intentos", "Yes: attempts remain")], ["a5", "a3", "loop"], ["d2", "a6", "cond", T("Sí: sin salida", "Yes: no exit")], ["a6", "a8"], ["a7", "a3", "loop", T("Siguiente paso", "Next step")], ["a7", "a8"], ["a8", "a9", "opt"], ["a8", "r1"], ["a9", "e", "event"], ["r1", "e"]]
  });

  HM.proc({
    id: "events_publish_session", scope: "cap", cap: "events", caps: ["model_editor", "gamification", "validation"], elems: ["canonical_event"],
    n: T("Publicar eventos desde una sesión de edición", "Publish events from an editing session"),
    d: T("Qué ocurre con los eventos mientras editas y cuándo se publican.", "What happens to events while you edit and when they are published."),
    nodes: [
      N("s", "start", null, null, "Cambias algo en el editor", "You change something in the editor", "Guardar, crear, editar, mover, borrar, importar, validar o aplicar una corrección.", "Saving, creating, editing, moving, deleting, importing, validating or applying a fix.", { cap: "model_editor" }),
      N("d1", "decision", "auto", null, "¿Publicación de eventos activada?", "Event publishing enabled?", "La política de la sesión puede desactivar la publicación.", "The session policy can disable publishing.", { cap: "model_editor", x: { rt: "EditorModelSessionService.publish", test: [], ev: [ev(SESS + "EditorModelSessionService.java", 739, "publishEvents gate")] } }),
      N("a1", "step", "auto", "MANDATORY", "Construir el evento", "Build the event", "Se rellenan origen, contexto de edición de modelo, actor, sujeto (modelo o elemento), transacción y correlación de sesión.", "Source, model-editing context, actor, subject (model or element), transaction and session correlation are filled.", { e: "canonical_event", x: { rt: "EditorSessionEventPublisher", test: ["EditorSessionMemoryHardeningTests"], ev: [ev(SESS + "EditorSessionEventPublisher.java", 21, "envelope")] } }),
      N("d2", "decision", "auto", null, "¿Autoguardado o sin copia de trabajo?", "Autosave or no working copy?", "Con autoguardado o sin copia de trabajo se publica de inmediato; si no, se guarda en un búfer.", "With autosave or no working copy it is published at once; otherwise it is buffered.", { x: { rt: "EditorSessionPendingEventBuffer", test: ["EditorSessionMemoryHardeningTests"], ev: [ev(SESS + "EditorModelSessionService.java", 746, "buffering")] } }),
      N("a2", "step", "auto", "CONDITIONAL", "Guardar en el búfer", "Keep in the buffer", "Se retiene en memoria, luego en un volcado en disco y como última opción en una cola; si se llena, es un conflicto.", "Held in memory, then in a disk spool and as a last resort in a queue; if full, it is a conflict.", { f: ["Si cierras la sesión sin guardar, los eventos retenidos se descartan.", "If you close the session without saving, held events are discarded."], x: { rt: "EditorSessionPendingEventBuffer", test: ["EditorSessionMemoryHardeningTests"], ev: [ev(SESS + "EditorSessionPendingEventBuffer.java", 49, "buffer tiers")] } }),
      N("a3", "step", "user", "CONDITIONAL", "Guardar el modelo", "Save the model", "Solo tras guardar con éxito, los eventos retenidos salen.", "Only after a successful save do held events go out.", { cap: "model_editor", s: ["eventos retenidos → publicables", "held events → publishable"] }),
      N("a4", "step", "auto", "MANDATORY", "Publicar en el canal", "Publish on the channel", "El canal entrega el evento a todos los suscriptores, uno tras otro. Un fallo al publicar se anota y no se reintenta.", "The channel delivers the event to every subscriber, one after another. A publishing failure is logged and not retried.", { x: { rt: "InMemoryCanonicalEventBus", test: ["CanonicalEventBusTests"], ev: [ev(EV + "bus/InMemoryCanonicalEventBus.java", 17, "bus")] } }),
      N("a5", "step", "auto", "OPTIONAL", "Enlaces de eventos", "Event bindings", "Los enlaces cuyo origen coincide derivan nuevos eventos.", "Bindings whose source matches derive new events.", { x: { rt: "EventBindingRuntimeService", test: ["EventBindingRuntimeServiceTests"], ev: [ev(EV + "runtime/EventBindingRuntimeService.java", 62, "subscribe")] } }),
      N("a6", "step", "auto", "OPTIONAL", "Flujos de eventos", "Event flows", "Los flujos disparados por el evento se ejecutan.", "Flows triggered by the event run.", { x: { rt: "EventFlowRuntimeService", test: ["EventFlowRuntimeAutoDeploymentTests"], ev: [ev(FLOW_RT, 492, "onEvent")] } }),
      N("a7", "step", "auto", "OPTIONAL", "Gamificación otorga o retira puntos", "Gamification grants or takes away points", "La gamificación escucha el mismo evento y aplica sus reglas.", "Gamification listens to the same event and applies its rules.", { cap: "gamification", x: { rt: "GamificationRuntimeService", test: [], ev: [] } }),
      N("a8", "step", "auto", "OPTIONAL", "Validación y Quick Fix emiten sus eventos", "Validation and Quick Fix emit their events", "Al ejecutar una validación o aplicar una corrección se emiten eventos que el resto escucha.", "Running a validation or applying a fix emits events the others listen to.", { cap: "validation", x: { rt: "ValidationOrchestrator / QuickFixRuntimeService", test: [], ev: [] } }),
      N("e", "end", null, null, "Fin", "End")
    ],
    edges: [["s", "d1"], ["d1", "e", "alt", T("No: no se publica", "No: not published")], ["d1", "a1", "next", T("Sí", "Yes")], ["a1", "d2"], ["d2", "a4", "next", T("Sí: inmediato", "Yes: immediate")], ["d2", "a2", "cond", T("No: en búfer", "No: buffered")], ["a2", "a3", "cond"], ["a3", "a4", "event"], ["a4", "a5", "opt"], ["a4", "a6", "opt"], ["a4", "a7", "event"], ["a8", "a4", "event"], ["s", "a8", "alt", T("Validar o corregir","Validate or fix")], ["a5", "e"], ["a6", "e"], ["a7", "e"]]
  });

  HM.proc({
    id: "events_binding_runtime", scope: "cap", cap: "events", elems: ["binding", "payload_mapping", "canonical_event"],
    n: T("Aplicar un enlace en ejecución", "Apply a binding at run time"),
    d: T("Cómo un enlace convierte un evento en otro.", "How a binding turns one event into another."),
    nodes: [
      N("s", "start", null, null, "Llega un evento", "An event arrives", "El motor de enlaces escucha todos los eventos.", "The binding engine listens to all events."),
      N("d0", "decision", "auto", null, "¿Profundidad menor de 32?", "Depth below 32?", "Una cadena de eventos derivados se corta a 32 niveles para evitar bucles.", "A chain of derived events stops at 32 levels to avoid loops.", { x: { rt: "EventBindingRuntimeService", test: ["EventBindingRuntimeServiceTests"], ev: [ev(EV + "runtime/EventBindingRuntimeService.java", 71, "depth cap")] } }),
      N("a1", "step", "auto", "MANDATORY", "Buscar enlaces con ese origen", "Find bindings with that source", "Solo cuentan los activos que no se hayan desactivado por un fallo previo.", "Only enabled ones not disabled by a previous failure count.", { e: "binding" }),
      N("d1", "decision", "auto", null, "¿La condición da verdadero?", "Is the condition true?", "Sin condición, el enlace actúa; con condición, debe dar verdadero.", "Without a condition the binding acts; with one, it must be true.", { x: { rt: "EventBindingRuntimeService", test: [], ev: [ev(EV + "runtime/EventBindingRuntimeService.java", 135, "condition")] } }),
      N("a2", "step", "auto", "MANDATORY", "Rellenar los campos del destino", "Fill the target fields", "Cada copia de datos copia una ruta, evalúa una expresión o pone un valor fijo.", "Each data copy copies a path, evaluates an expression or sets a fixed value.", { e: "payload_mapping" }),
      N("a3", "step", "auto", "MANDATORY", "Publicar el evento derivado", "Publish the derived event", "El nuevo evento tiene como origen el propio enlace y vuelve al canal.", "The new event has the binding itself as source and returns to the channel.", { e: "canonical_event", x: { rt: "EventBindingRuntimeService", test: [], ev: [ev(EV + "runtime/EventBindingRuntimeService.java", 98, "derived source")] } }),
      N("d2", "decision", "auto", null, "¿Falló el enlace?", "Did the binding fail?", "Según la política de error: omitir, avisar o desactivar.", "By error policy: skip, report or disable.", { e: "binding", x: { rt: "EventBindingRuntimeService", test: [], ev: [ev(EV + "runtime/EventBindingRuntimeService.java", 155, "failure policy")] } }),
      N("a4", "step", "auto", "CONDITIONAL", "Aplicar la política de error", "Apply the error policy", "Omitir ignora, avisar anota un aviso, desactivar anota y deja de aplicar el enlace hasta reiniciar.", "Skip ignores, report logs a warning, disable logs and stops applying the binding until restart.", { f: ["El estado de desactivación solo está en memoria.", "The disabled state is in memory only."], s: ["enlace: activo → desactivado", "binding: enabled → disabled"] }),
      N("r1", "artifact", null, null, "Evento derivado", "Derived event", "El resultado de aplicar el enlace.", "The result of applying the binding."),
      N("e", "end", null, null, "Fin", "End")
    ],
    edges: [["s", "d0"], ["d0", "e", "alt", T("No: se corta", "No: cut off")], ["d0", "a1", "next", T("Sí", "Yes")], ["a1", "d1"], ["d1", "e", "alt", T("No: no actúa", "No: does not act")], ["d1", "a2", "next", T("Sí", "Yes")], ["a2", "d2"], ["d2", "a3", "next", T("No", "No")], ["d2", "a4", "cond", T("Sí", "Yes")], ["a4", "e"], ["a3", "r1"], ["r1", "e", "event"]]
  });

  /* =============================== MICROPROCESOS DE AUTORÍA =============================== */

  HM.proc({
    id: "events_author_form", scope: "authoring", cap: "events", elems: ["event_type", "event_field", "binding", "payload_mapping"],
    n: T("Formulario de eventos", "Event form"), d: T("Los pasos del diseñador de eventos en vista de formulario.", "The steps of the event designer in form view."),
    nodes: [
      N("s", "start", null, null, "Abrir el diseñador de eventos", "Open the event designer", "Abres el metamodelo y su diseñador; carga la vista de formulario y los eventos reservados.", "You open the metamodel and its designer; the form view and reserved events load."),
      N("a1", "step", "user", "MANDATORY", "Revisar el espacio de nombres", "Review the namespace", "Es el prefijo de todos tus eventos; se propone a partir del metamodelo.", "It is the prefix of all your events; it is proposed from the metamodel.", { need: ["Un metamodelo", "A metamodel"], out: ["Espacio de nombres válido", "Valid namespace"], f: ["Empieza por letra; letras, cifras, punto, guion y guion bajo.", "Starts with a letter; letters, digits, dot, hyphen and underscore."] }),
      N("a2", "step", "user", "MANDATORY", "Añadir un tipo de evento", "Add an event type", "Aparece con un nombre por defecto, categoría «dominio», persistencia transitoria, versión 1.0.", "It appears with a default name, category “domain”, transient persistence, version 1.0.", { e: "event_type", out: ["Tipo de evento", "Event type"] }),
      N("a3", "step", "user", "OPTIONAL", "Elegir categoría", "Choose category", "Eliges de la lista o usas la opción personalizada.", "You choose from the list or use the custom option.", { e: "event_type" }),
      N("a4", "step", "user", "OPTIONAL", "Añadir campos", "Add fields", "Por defecto «value», de tipo texto; cambias nombre y tipo.", "Default “value”, text type; you change name and type.", { e: "event_field" }),
      N("d1", "decision", "user", null, "¿El campo es enumerado u objeto?", "Is the field an enumeration or object?", "En ese caso debes indicar el tipo enumerado o el tipo de objeto.", "In that case you must name the enumeration or object type.", { f: ["El tipo enumerado o de objeto es obligatorio solo para esos tipos.", "The enumeration or object type is mandatory only for those types."] }),
      N("a5", "step", "user", "CONDITIONAL", "Indicar tipo enumerado o de objeto", "Name the enumeration or object type", "Completa el campo.", "Completes the field.", { e: "event_field" }),
      N("a6", "step", "user", "OPTIONAL", "Editar un enlace: condición y copias", "Edit a binding: condition and copies", "Pones condición y copias de datos; los campos obligatorios del destino deben mapearse.", "You set the condition and data copies; required target fields must be mapped.", { e: "binding" }),
      N("a7", "step", "auto", "MANDATORY", "Comprobar", "Check", "La interfaz valida el espacio de nombres y los campos obligatorios; la comprobación completa se pide al servidor.", "The interface validates the namespace and required fields; the full check is requested from the server.", { x: { rt: "eventDesignerValidation / EventModelChecker", test: ["eventDesignerValidation.test.ts"], ev: [ev(FE + "eventDesignerValidation.ts", 28, "required mapped")] } }),
      N("a8", "step", "user", "MANDATORY", "Guardar", "Save", "Se guardan tipos y enlaces.", "Types and bindings are saved.", { x: { rt: "api.ts", test: ["api.test.ts"], ev: [ev(FE + "api.ts", 10, "save")] } }),
      N("r1", "artifact", null, null, "Modelo de eventos guardado", "Saved event model", "Resultado del formulario.", "Result of the form."),
      N("e", "end", null, null, "Fin", "End")
    ],
    edges: [["s", "a1"], ["a1", "a2"], ["a2", "a3", "opt"], ["a3", "a4", "opt"], ["a2", "a4"], ["a4", "d1"], ["d1", "a5", "cond", T("Sí", "Yes")], ["d1", "a6", "opt", T("No", "No")], ["a5", "a6", "opt"], ["a6", "a7"], ["d1", "a7", "next", T("No: nada más", "No: nothing else")], ["a7", "a8"], ["a8", "r1"], ["r1", "e"]]
  });

  HM.proc({
    id: "events_author_connect", scope: "authoring", cap: "events", elems: ["binding", "event_type"],
    n: T("Conectar eventos en el diagrama de arquitectura", "Connect events in the architecture diagram"), d: T("Crear un enlace dibujándolo.", "Create a binding by drawing it."),
    nodes: [
      N("s", "start", null, null, "Abrir la vista de arquitectura", "Open the architecture view", "Ves los eventos reservados (solo lectura) y los propios.", "You see reserved events (read-only) and custom ones."),
      N("a1", "step", "user", "MANDATORY", "Elegir el evento de origen", "Choose the source event", "Puede ser reservado o propio.", "May be reserved or custom.", { e: "event_type" }),
      N("a2", "step", "user", "MANDATORY", "Arrastrar hasta un evento propio de destino", "Drag to a custom target event", "Solo los eventos propios son destinos válidos.", "Only custom events are valid targets.", { e: "binding", f: ["Se rechaza un origen inexistente, un destino no propio, un bucle sobre sí mismo o un par duplicado.", "Rejected: unknown source, non-custom target, self-loop or duplicate pair."], x: { rt: "applyEventConnection", test: ["eventConnections.test.ts"], ev: [ev(FE + "eventConnections.ts", 11, "applyEventConnection")] } }),
      N("d1", "decision", "auto", null, "¿La conexión es válida?", "Is the connection valid?", "Si no, no se crea.", "If not it is not created."),
      N("a3", "step", "auto", "MANDATORY", "Crear el enlace", "Create the binding", "Nace activo, sin condición ni copias y con política de avisar.", "It starts enabled, with no condition or copies and the report policy.", { e: "binding", s: ["enlace: inexistente → creado", "binding: none → created"], x: { rt: "eventDesignerFactory", test: ["eventDesignerFactory.test.ts"], ev: [ev(FE + "eventDesignerFactory.ts", 17, "default binding")] } }),
      N("a4", "step", "user", "OPTIONAL", "Poner condición y copias de datos", "Set condition and data copies", "Selecciona el enlace y rellena; los campos obligatorios del destino deben mapearse.", "Select the binding and fill; required target fields must be mapped.", { e: "payload_mapping" }),
      N("a5", "step", "auto", "MANDATORY", "Comprobar", "Check", "Se comprueba el modelo de eventos.", "The event model is checked.", {}),
      N("a6", "step", "user", "MANDATORY", "Guardar modelos y diagrama", "Save models and diagram", "Se guardan los modelos y la posición de las cajas.", "Models and box positions are saved.", { x: { rt: "EventArchitectureDiagramController", test: ["EventArchitectureDiagramRepositoryTests"], ev: [ev(EV + "diagram/EventArchitectureDiagramController.java", 14, "diagram API")] } }),
      N("r1", "artifact", null, null, "Enlace guardado", "Saved binding", "Resultado de la conexión.", "Result of the connection."),
      N("e", "end", null, null, "Fin", "End")
    ],
    edges: [["s", "a1"], ["a1", "a2"], ["a2", "d1"], ["d1", "a2", "loop", T("No: corregir", "No: fix")], ["d1", "a3", "next", T("Sí", "Yes")], ["a3", "a4", "opt"], ["a3", "a5"], ["a4", "a5"], ["a5", "a6"], ["a6", "r1"], ["r1", "e"]]
  });

  HM.proc({
    id: "events_author_flow", scope: "authoring", cap: "events", elems: ["flow", "flow_node", "transition", "flow_variable", "retry_timeout", "compensation_link"],
    n: T("Lienzo del diseñador de flujos", "Flow designer canvas"), d: T("Los pasos del diseñador de flujos.", "The steps of the flow designer."),
    nodes: [
      N("s", "start", null, null, "Abrir el diseñador de flujos", "Open the flow designer", "Se carga el modelo de flujos del metamodelo.", "The metamodel's flow model loads."),
      N("a1", "step", "user", "MANDATORY", "Añadir un flujo", "Add a flow", "Pones identificador, nombre y eventos disparadores.", "You set identifier, name and trigger events.", { e: "flow", x: { rt: "eventFlowFactory", test: ["eventFlowFactory.test.ts"], ev: [] } }),
      N("a2", "step", "user", "MANDATORY", "Elegir un tipo de paso en la paleta", "Pick a step type from the palette", "29 tipos agrupados; un paso nuevo es de evento por defecto.", "29 types grouped; a new step defaults to event step.", { e: "flow_node" }),
      N("a3", "step", "user", "MANDATORY", "Configurar el paso", "Configure the step", "Las propiedades se agrupan por familia: evento, referencia, condición, reintento, plazo.", "Properties group by family: event, reference, condition, retry, time limit.", { e: "flow_node", x: { rt: "EventFlowHighLevelProperties", test: [], ev: [ev("frontend/src/event-designer/flow/EventFlowDesigner.tsx", 313, "addNode")] } }),
      N("d1", "decision", "user", null, "¿Faltan más pasos?", "More steps needed?", "Repites hasta tener el recorrido completo.", "You repeat until the walk is complete."),
      N("a4", "step", "user", "MANDATORY", "Conectar con transiciones", "Connect with transitions", "Eliges el tipo y la etiqueta de cada una.", "You choose each one's type and label.", { e: "transition" }),
      N("a5", "step", "user", "OPTIONAL", "Variables y compensaciones", "Variables and compensations", "En las secciones del panel de estructura.", "In the structure panel sections.", { e: "compensation_link" }),
      N("a6", "step", "auto", "MANDATORY", "Comprobar y revisar la lista de preparación", "Check and review the readiness checklist", "Se pide la comprobación al servidor y se ve una lista local de preparación.", "The server check is requested and a local readiness checklist is shown.", { x: { rt: "EventFlowChecker", test: ["EventFlowCheckerTests"], ev: [ev(FE + "EventFlowDesignerParts.tsx", 82, "readiness checklist")] } }),
      N("d2", "decision", "auto", null, "¿Hay errores?", "Any errors?", "Los errores impiden compilar.", "Errors prevent compiling."),
      N("a7", "step", "user", "MANDATORY", "Guardar el flujo y el diagrama", "Save the flow and the diagram", "Se guarda con control de versiones.", "Saved with version control.", { x: { rt: "flow/api.ts", test: ["eventFlowValidation.test.ts"], ev: [ev(FE + "flow/api.ts", 7, "save flows")] } }),
      N("r1", "artifact", null, null, "Flujo guardado", "Saved flow", "Resultado del diseño.", "Result of the design."),
      N("e", "end", null, null, "Fin", "End")
    ],
    edges: [["s", "a1"], ["a1", "a2"], ["a2", "a3"], ["a3", "d1"], ["d1", "a2", "loop", T("Sí", "Yes")], ["d1", "a4", "next", T("No", "No")], ["a4", "a5", "opt"], ["a4", "a6"], ["a5", "a6"], ["a6", "d2"], ["d2", "a3", "loop", T("Sí: corregir", "Yes: fix")], ["d2", "a7", "next", T("No", "No")], ["a7", "r1"], ["r1", "e"]]
  });

  HM.proc({
    id: "events_author_source", scope: "authoring", cap: "events", elems: ["event_type", "binding"],
    n: T("Código fuente (JSON)", "Source JSON"), d: T("Editar el modelo de eventos como JSON.", "Edit the event model as JSON."),
    nodes: [
      N("s", "start", null, null, "Cambiar a la vista de código fuente", "Switch to the source view", "Ves el modelo como JSON.", "You see the model as JSON."),
      N("a1", "step", "user", "MANDATORY", "Editar tipos y enlaces", "Edit types and bindings", "Cambias directamente el texto.", "You change the text directly.", { e: "event_type" }),
      N("a2", "step", "user", "OPTIONAL", "Volver a formulario o arquitectura", "Return to form or architecture", "Para ver el resultado en forma gráfica.", "To see the outcome graphically.", { e: "binding" }),
      N("a3", "step", "auto", "MANDATORY", "Normalizar y comprobar", "Normalize and check", "Se normaliza el modelo y se comprueban las reglas.", "The model is normalized and rules are checked.", { x: { rt: "eventModelNormalization / EventModelChecker", test: ["eventModelNormalization.test.ts"], ev: [] } }),
      N("d1", "decision", "auto", null, "¿Es válido?", "Is it valid?", "Con errores hay que corregir.", "With errors you must fix them."),
      N("a4", "step", "user", "MANDATORY", "Guardar", "Save", "Se guarda con control de versiones.", "Saved with version control."),
      N("r1", "artifact", null, null, "Modelo guardado", "Saved model", "Resultado de la edición.", "Result of the editing."),
      N("e", "end", null, null, "Fin", "End")
    ],
    edges: [["s", "a1"], ["a1", "a2", "opt"], ["a1", "a3"], ["a2", "a3"], ["a3", "d1"], ["d1", "a1", "loop", T("No: corregir", "No: fix")], ["d1", "a4", "next", T("Sí", "Yes")], ["a4", "r1"], ["r1", "e"]]
  });

  HM.proc({
    id: "events_author_run", scope: "authoring", cap: "events", elems: ["execution_trace", "flow"],
    n: T("Panel de ejecución", "Run panel"), d: T("Arrancar el motor y probar un flujo.", "Start the engine and test a flow."),
    nodes: [
      N("s", "start", null, null, "Abrir el panel de ejecución", "Open the run panel", "Trabaja sobre el modelo de flujos guardado.", "Works on the saved flow model."),
      N("a1", "step", "user", "MANDATORY", "Arrancar o actualizar el motor", "Start or refresh the engine", "Compila el modelo guardado; requiere que la comprobación esté limpia.", "Compiles the saved model; requires a clean check.", { f: ["Con errores, no compila.", "With errors it does not compile."], x: { rt: "EventFlowRuntimeService.compile", test: ["EventFlowCompilerTests"], ev: [ev(FLOW_RT, 84, "compile")] } }),
      N("a2", "step", "user", "OPTIONAL", "Enviar un evento de prueba", "Send a test event", "Simula la llegada de un evento.", "Simulates an event arriving."),
      N("a3", "step", "user", "OPTIONAL", "Avanzar el reloj", "Advance the clock", "Procesa reintentos, plazos y ausencias.", "Processes retries, deadlines and absences.", { x: { rt: "tick", test: [], ev: [ev(FLOW_RT, 133, "tick")] } }),
      N("a4", "step", "user", "MANDATORY", "Consultar estado y trazas", "Query status and traces", "Ves qué pasos se recorrieron.", "You see which steps were walked.", { e: "execution_trace" }),
      N("a5", "step", "user", "OPTIONAL", "Detener o borrar el estado", "Stop or clear the state", "Detener impide la reactivación automática.", "Stopping prevents automatic reactivation."),
      N("r1", "artifact", null, null, "Traza consultada", "Trace reviewed", "Resultado de la prueba.", "Result of the test."),
      N("e", "end", null, null, "Fin", "End")
    ],
    edges: [["s", "a1"], ["a1", "a2", "opt"], ["a2", "a3", "opt"], ["a2", "a4"], ["a3", "a4"], ["a1", "a4"], ["a4", "a5", "opt"], ["a4", "r1"], ["a5", "e"], ["r1", "e"]]
  });
})();

