(function () {
  "use strict";
  var HM = window.HM, T = HM.T;
  // Helpers: E = element lens, V = variant lens, N = node lens. Arguments are [es, en] pairs.
  function t(p) { return p ? T(p[0], p[1]) : undefined; }
  function V(k, fd, td) { var v = { fd: t(fd) }; if (k) v.k = k; if (td) v.td = t(td); return v; }
  function E(nf, fm, tm, variants) { return { nf: t(nf), fm: t(fm), tm: t(tm), variants: variants || [] }; }
  function N(lf, sf, fio, lt, st, tx) {
    var n = { lf: t(lf), sf: t(sf), fio: t(fio) };
    if (lt) n.lt = typeof lt === "string" ? lt : t(lt);
    if (st) n.st = t(st);
    if (tx) n.tx = t(tx);
    return n;
  }
  function L(a) { return a.map(t); }

  HM.lens({
    caps: {
      events: {
        func: {
          title: T("Motor declarativo de eventos canónicos, enlaces y flujos de reacción", "Declarative engine of canonical events, bindings and reaction flows"),
          summary: T("El dominio gira en torno al tipo de evento (reservado del sistema o propio, con campos tipados) y al evento en circulación, una instancia con origen, contexto, actor, sujeto y correlación. Sobre ellos se declaran dos mecanismos de reacción: el enlace, que deriva un evento propio de otro con condición y copia de datos, y el flujo, un grafo de pasos y transiciones con ramas, esperas, reintentos y compensaciones que deja una traza por ejecución. Cada modelo de eventos y de flujos pertenece a un metamodelo y todo se comprueba antes de poder activarse.",
            "The domain revolves around the event type (system-reserved or custom, with typed fields) and the event in flight, an instance with source, context, actor, subject and correlation. Two reaction mechanisms are declared over them: the binding, which derives a custom event from another with a condition and data copy, and the flow, a graph of steps and transitions with branches, waits, retries and compensations that leaves one trace per run. Every event and flow model belongs to a metamodel and everything is checked before it can be activated."),
          inputs: L([
            ["Tipos de evento propios con sus campos tipados", "Custom event types with their typed fields"],
            ["Eventos en circulación emitidos por sesiones, validación, gamificación y los propios flujos", "Events in flight emitted by sessions, validation, gamification and the flows themselves"],
            ["Enlaces con evento de origen, destino propio, condición y copias de datos", "Bindings with source event, custom target, condition and data copies"],
            ["Flujos con disparadores, variables, pasos, transiciones y compensaciones", "Flows with triggers, variables, steps, transitions and compensations"],
            ["Un metamodelo guardado que contiene los modelos", "A saved metamodel that holds the models"]
          ]),
          outputs: L([
            ["Eventos derivados por enlaces y por pasos de emisión", "Events derived by bindings and by emit steps"],
            ["Una traza por flujo disparado, con estado final", "One trace per triggered flow, with a final status"],
            ["Diagnósticos de comprobación con gravedad, código y elemento", "Check diagnostics with severity, code and element"],
            ["Modelos de eventos y de flujos versionados", "Versioned event and flow models"],
            ["Efectos externos pedidos por pasos de comando, acción y operación", "External effects requested by command, action and operation steps"]
          ]),
          conditions: L([
            ["Un tipo reservado no se redefine; un destino de enlace es siempre propio", "A reserved type is never redefined; a binding target is always custom"],
            ["Un flujo se compila solo sin errores, con una única entrada y al menos un final", "A flow compiles only without errors, with a single entry and at least one end"],
            ["La ejecución es secuencial y la cadena de derivados se corta a 32 niveles", "Execution is sequential and the chain of derived events stops at 32 levels"],
            ["Sin salida de error se compensan en orden inverso los pasos compensables completados", "With no error exit, completed compensable steps are compensated in reverse order"],
            ["Un motor detenido a mano no se reactiva por sí solo", "An engine stopped by hand does not reactivate by itself"]
          ])
        },
        tech: {
          title: T("Bus de eventos en memoria, comprobadores y motor de flujos", "In-memory event bus, checkers and flow engine"),
          summary: T("Un InMemoryCanonicalEventBus síncrono entrega CanonicalEvent a tres suscriptores (EventBindingRuntimeService, EventFlowRuntimeService y la gamificación). Los modelos se validan con EventModelChecker y EventFlowChecker, EventFlowCompiler indexa los disparadores y EventFlowRuntimeNodeExecutor ejecuta cada FlowNodeKind. Las sesiones del editor publican mediante EditorSessionEventPublisher con EditorSessionPendingEventBuffer, y la API REST cuelga de /api/canonical-metamodels/{id}/events y /event-flows con If-Match.",
            "A synchronous InMemoryCanonicalEventBus delivers CanonicalEvent to three subscribers (EventBindingRuntimeService, EventFlowRuntimeService and gamification). Models are validated by EventModelChecker and EventFlowChecker, EventFlowCompiler indexes the triggers and EventFlowRuntimeNodeExecutor runs each FlowNodeKind. Editor sessions publish through EditorSessionEventPublisher with EditorSessionPendingEventBuffer, and the REST API hangs from /api/canonical-metamodels/{id}/events and /event-flows with If-Match.")
        },
        elements: {
          event_type: E(
            ["Tipo de evento (contrato nominal de un hecho)", "Event type (nominal contract of a fact)"],
            ["Contrato con nombre completo espacio.nombre que declara una clase de hechos y los campos que transportan. Se divide en reservados del sistema, inmutables, y propios; pertenece a un modelo de eventos y contiene 0..N campos. Su ciclo es definido, comprobado y guardado, con marcas de abstracto, obsoleto y activo.",
              "Contract with the full name space.name that declares a class of facts and the fields they carry. It splits into immutable system-reserved types and custom ones; it belongs to an event model and holds 0..N fields. Its life cycle is defined, checked and saved, with abstract, deprecated and enabled flags."],
            ["Registro EventTypeDefinition dentro de un EventDefinitionModel (schemaVersion 1.0); el espacio de nombres se valida por expresión regular y los reservados los lista ReservedEventCatalog.",
              "EventTypeDefinition record inside an EventDefinitionModel (schemaVersion 1.0); the namespace is validated by a regular expression and reserved ids are listed by ReservedEventCatalog."],
            [V(null, ["Pertenece al vocabulario cerrado del sistema (32 ids en el espacio core); es referenciable como origen o disparador, pero redefinirlo es un error del modelo.", "Belongs to the closed system vocabulary (32 ids in the core space); it can be referenced as source or trigger, but redefining it is a model error."],
                ["RESERVED_EVENT_TYPE en EventModelChecker.", "RESERVED_EVENT_TYPE in EventModelChecker."]),
             V(null, ["Tipo definido por el autor del modelo; es el único que puede ser destino de un enlace, y un espacio de nombres ajeno al del modelo solo genera un aviso.", "Type defined by the model author; the only one that can be the target of a binding, and a namespace foreign to the model only raises a warning."],
                ["FOREIGN_NAMESPACE (aviso).", "FOREIGN_NAMESPACE (warning)."])]
          ),
          event_field: E(
            ["Campo de carga útil (dato tipado del evento)", "Payload field (typed datum of the event)"],
            ["Dato nombrado y tipado que declara un tipo de evento. Su identificador y su nombre son únicos dentro del tipo; si es obligatorio, todo enlace hacia ese tipo debe rellenarlo, y puede admitir varios valores. El tipo enumerado y el tipo de objeto solo se exigen en esas dos modalidades.",
              "Named, typed datum declared by an event type. Its identifier and name are unique within the type; when required, every binding towards that type must fill it, and it may admit several values. The enumeration type and the object type are only required in those two modes."],
            ["EventFieldDefinition con un EventFieldType de 13 valores; enumTypeId y objectTypeId son obligatorios solo para ENUM y OBJECT, y no se valida el valor recibido contra el tipo en ejecución.",
              "EventFieldDefinition with a 13-value EventFieldType; enumTypeId and objectTypeId are mandatory only for ENUM and OBJECT, and the received value is not validated against the type at run time."],
            [V("STRING", ["Valor textual; es el tipo con el que nace un campo nuevo.", "Textual value; the type a new field is born with."]),
             V("INTEGER", ["Magnitud entera, adecuada para contadores y sumas.", "Whole-number magnitude, suited to counters and sums."]),
             V("DECIMAL", ["Magnitud real con parte fraccionaria.", "Real magnitude with a fractional part."]),
             V("BOOLEAN", ["Indicador de dos estados.", "Two-state flag."]),
             V("DATE", ["Fecha de calendario sin hora del día.", "Calendar date without time of day."]),
             V("DATETIME", ["Punto exacto en la línea temporal.", "Exact point on the timeline."]),
             V("DURATION", ["Medida de tiempo transcurrido, no un instante.", "Measure of elapsed time, not an instant."]),
             V("DOCUMENT_ID", ["Clave que apunta a un documento del repositorio (modelo o metamodelo).", "Key that points to a repository document (model or metamodel)."]),
             V("ELEMENT_ID", ["Clave que apunta a un elemento dentro de un modelo.", "Key that points to an element inside a model."]),
             V("ENUM", ["Valor de un conjunto cerrado; exige designar la enumeración (enumTypeId).", "Value of a closed set; requires designating the enumeration (enumTypeId)."]),
             V("OBJECT", ["Dato estructurado de otro tipo; exige designar el tipo de objeto (objectTypeId).", "Structured datum of another type; requires designating the object type (objectTypeId)."]),
             V("ARRAY", ["Secuencia de valores del mismo dato.", "Sequence of values of the same datum."]),
             V("ANY", ["Sin restricción declarada; el contenido se acepta tal cual.", "No declared restriction; the content is accepted as is."])]
          ),
          canonical_event: E(
            ["Evento canónico (envoltorio de un hecho ocurrido)", "Canonical event (envelope of an occurred fact)"],
            ["Ocurrencia concreta de un tipo de evento: pertenece a exactamente un tipo y lleva un origen, un contexto, como mucho un actor y un sujeto, correlación causal y dos mapas de datos. Se emite una vez, se entrega a todos los suscriptores en orden y puede originar nuevos eventos derivados; no se almacena como entidad.",
              "Concrete occurrence of an event type: it belongs to exactly one type and carries a source, a context, at most one actor and one subject, causal correlation and two data maps. It is emitted once, delivered to all subscribers in order and may originate derived events; it is not stored as an entity."],
            ["Registro CanonicalEvent (schemaVersion 1.0) con EventSource, EventContext, EventActor y EventSubject; la política de persistencia se declara en el tipo pero ningún consumidor la aplica.",
              "CanonicalEvent record (schemaVersion 1.0) with EventSource, EventContext, EventActor and EventSubject; the persistence policy is declared on the type but no consumer applies it."],
            [V(null, ["Quién emite el hecho; es la única parte de la envoltura que no puede faltar junto con el contexto.", "Who emits the fact; together with the context, the only part of the envelope that cannot be missing."]),
             V(null, ["Ámbito en el que ocurre el hecho; si no se indica, es el sistema.", "Scope in which the fact occurs; if not given it is the system."]),
             V(null, ["Agente responsable del hecho, cuando se conoce.", "Agent responsible for the fact, when known."]),
             V(null, ["Entidad de dominio sobre la que recae el hecho, desde un metamodelo hasta un premio.", "Domain entity the fact is about, from a metamodel to a reward."]),
             V(null, ["Cadena que enlaza un hecho con la transacción y con la causa que lo originó.", "Chain that ties a fact to its transaction and to the cause that originated it."]),
             V(null, ["Parte variable: los datos del dominio y los metadatos técnicos, mantenidos aparte.", "Variable part: the domain data and the technical metadata, kept apart."])]
          ),
          binding: E(
            ["Enlace de derivación (regla evento → evento)", "Derivation binding (event → event rule)"],
            ["Regla declarativa con un evento de origen, reservado o propio, y un evento de destino que debe ser propio. Opcionalmente lleva una condición y 0..N copias de datos que deben cubrir los campos obligatorios del destino. Mientras está activo deriva un evento por cada evento de origen que cumpla la condición, y su política de error decide qué ocurre si falla.",
              "Declarative rule with a source event, reserved or custom, and a target event that must be custom. It optionally carries a condition and 0..N data copies that must cover the target's required fields. While enabled it derives one event per source event that meets the condition, and its error policy decides what happens on failure."],
            ["EventBinding dentro de un EventBindingModel (eventDefinitionModelId, expressionModelId opcional); lo aplica EventBindingRuntimeService, que guarda en memoria los enlaces desactivados.",
              "EventBinding inside an EventBindingModel (eventDefinitionModelId, optional expressionModelId); applied by EventBindingRuntimeService, which keeps disabled bindings in memory."],
            [V("SKIP", ["El fallo se descarta sin dejar rastro y el enlace sigue activo.", "The failure is discarded without a trace and the binding stays active."]),
             V("REPORT", ["El fallo se registra como aviso y el enlace sigue activo; es la política por defecto.", "The failure is recorded as a warning and the binding stays active; the default policy."]),
             V("DISABLE_BINDING", ["Tras el primer fallo el enlace queda inactivo hasta reiniciar el motor.", "After the first failure the binding becomes inactive until the engine restarts."], ["Estado de desactivación solo en memoria.", "Disabled state kept in memory only."])]
          ),
          payload_mapping: E(
            ["Correspondencia de carga útil (campo destino ← origen del valor)", "Payload mapping (target field ← value source)"],
            ["Asignación que determina el valor de un único campo del evento de destino de un enlace. Cada campo de destino aparece como mucho una vez y su valor procede de exactamente una fuente; la cobertura de todos los campos obligatorios es condición de validez del enlace.",
              "Assignment that determines the value of one single field of a binding's target event. Each target field appears at most once and its value comes from exactly one source; covering all required fields is a validity condition of the binding."],
            ["EventPayloadMapping: targetFieldId más exactamente uno de sourcePath, expressionId o fixedValue, comprobado por EventModelChecker.",
              "EventPayloadMapping: targetFieldId plus exactly one of sourcePath, expressionId or fixedValue, checked by EventModelChecker."],
            [V("sourcePath", ["El valor se toma tal cual de una posición del evento original.", "The value is taken as is from a position in the original event."]),
             V("expressionId", ["El valor es el resultado de evaluar una expresión del modelo de expresiones asociado.", "The value is the result of evaluating an expression from the associated expressions model."]),
             V("fixedValue", ["El valor es una constante declarada en la propia correspondencia.", "The value is a constant declared in the mapping itself."])]
          ),
          flow: E(
            ["Flujo de eventos (grafo de reacción con estado)", "Event flow (stateful reaction graph)"],
            ["Grafo dirigido de pasos y transiciones que arranca cuando llega un evento que coincide con alguno de sus disparadores. Un flujo contiene variables, compensaciones y exactamente un punto de entrada con al menos un final. Su ciclo es diseñado, comprobado, compilado y desplegado, y cada ejecución recorre el grafo y deja una traza.",
              "Directed graph of steps and transitions that starts when an event matching one of its triggers arrives. A flow holds variables, compensations and exactly one entry point with at least one end. Its life cycle is designed, checked, compiled and deployed, and each run walks the graph and leaves a trace."],
            ["FlowDefinition dentro de un EventFlowModel (schemaVersion 1.0); EventFlowCompiler lo compila y construye el índice de disparadores.",
              "FlowDefinition inside an EventFlowModel (schemaVersion 1.0); EventFlowCompiler compiles it and builds the trigger index."],
            [V(null, ["Único modo de arranque: la llegada de un evento cuyo tipo figura entre los disparadores, o es el del paso de entrada de evento.", "Only start mode: the arrival of an event whose type is among the triggers, or is that of the event source entry."])]
          ),
          flow_variable: E(
            ["Variable de ejecución del flujo", "Flow run variable"],
            ["Valor con nombre y tipo, de partida opcional, que pertenece a un único flujo y se declara en su diseño. Existe para guardar estado propio de la ejecución; la evidencia no muestra un paso que la lea o la escriba de forma específica.",
              "Named, typed value with an optional starting value that belongs to a single flow and is declared in its design. It exists to hold run-specific state; the evidence does not show a step that specifically reads or writes it."],
            ["FlowVariable (id, name, type con ANY por defecto, initialValue) dentro de FlowDefinition.",
              "FlowVariable (id, name, type defaulting to ANY, initialValue) inside FlowDefinition."]
          ),
          flow_node: E(
            ["Paso del flujo (unidad de comportamiento tipada)", "Flow step (typed unit of behaviour)"],
            ["Nodo del grafo cuyo comportamiento viene dado por su tipo, dentro de 29 tipos repartidos en nueve familias. Un flujo tiene 1..N pasos, y cada paso puede ser origen y destino de transiciones. Puede llevar un evento o una referencia externa según su tipo, una condición, política de reintento y plazo, y la marca de compensable; sin ruta de entrada o sin salida (si no es final) es un error.",
              "Graph node whose behaviour is given by its type, among 29 types spread over nine families. A flow has 1..N steps, and each step may be source and target of transitions. It may carry an event or an external reference depending on its type, a condition, retry and time-limit policy, and the compensable flag; having no incoming route, or no exit when not final, is an error."],
            ["FlowNode con kind de FlowNodeKind (29 valores), referenceId, eventTypeId, conditionExpressionId, configuration, retryPolicy, timeoutPolicy y compensable; EventFlowRuntimeNodeExecutor despacha por kind.",
              "FlowNode with a FlowNodeKind kind (29 values), referenceId, eventTypeId, conditionExpressionId, configuration, retryPolicy, timeoutPolicy and compensable; EventFlowRuntimeNodeExecutor dispatches by kind."]
          ),
          node_entry_exit: E(
            ["Pasos de frontera del flujo (entrada y finales)", "Flow boundary steps (entry and ends)"],
            ["Pasos que delimitan el grafo: el flujo debe tener exactamente una entrada, de inicio o de evento, y al menos un final. Una rama termina cuando el testigo no tiene transición de salida, de modo que los finales marcan el cierre pero no condicionan por sí solos el resultado.",
              "Steps that bound the graph: the flow must have exactly one entry, start or event source, and at least one end. A branch ends when the token has no outgoing transition, so ends mark closure but do not by themselves determine the result."],
            ["Los cuatro kinds son de paso directo salvo SOURCE, que contrasta eventTypeId; EventFlowChecker emite FLOW_START_COUNT y FLOW_END_REQUIRED.",
              "All four kinds are pass-through except SOURCE, which matches eventTypeId; EventFlowChecker raises FLOW_START_COUNT and FLOW_END_REQUIRED."],
            [V("START", ["Entrada neutra: admite cualquier evento disparador del flujo.", "Neutral entry: admits any trigger event of the flow."]),
             V("SOURCE", ["Entrada selectiva ligada a un tipo de evento, que además se incorpora a los disparadores.", "Selective entry bound to one event type, which is also added to the triggers."], ["Contrasta el eventTypeId cuando está informado.", "Matches the eventTypeId when set."]),
             V("SUCCESS_END", ["Cierre de rama esperado como camino normal.", "Branch closure expected as the normal path."]),
             V("ERROR_END", ["Cierre de rama con intención de fallo; el estado resultante sigue siendo COMPLETED si se llega por una transición normal.", "Branch closure with failure intent; the resulting status is still COMPLETED when reached through a normal transition."], ["Hallazgo F5: paso directo en el ejecutor.", "Finding F5: pass-through in the executor."])]
          ),
          node_event: E(
            ["Pasos de sincronía con eventos (filtrar por tipo y esperar)", "Event synchronization steps (type gating and waiting)"],
            ["Pasos que condicionan la marcha a un tipo de evento concreto: uno exige que el evento actual sea de ese tipo y otro suspende la rama hasta que llegue. Una espera suspendida se guarda y se reanuda por la llegada del evento o por vencimiento de su plazo; ambos requieren un tipo de evento existente.",
              "Steps that tie progress to a specific event type: one requires that the current event be of that type and the other suspends the branch until it arrives. A suspended wait is stored and resumed by the event's arrival or by its deadline; both require an existing event type."],
            ["EVENT_STEP y WAIT_FOR_EVENT; la espera se guarda como FlowWaitingContinuation y el vencimiento sale por la transición TIMEOUT.",
              "EVENT_STEP and WAIT_FOR_EVENT; the wait is stored as a FlowWaitingContinuation and expiry leaves through the TIMEOUT transition."],
            [V("EVENT_STEP", ["Compuerta por tipo: coincide y continúa, o no coincide y la ejecución se filtra.", "Type gate: it matches and continues, or it does not and the run is filtered."]),
             V("WAIT_FOR_EVENT", ["Punto de sincronía: continúa si el evento ya constaba, y si no suspende la rama con estado de espera.", "Synchronization point: continues if the event was already recorded, otherwise suspends the branch with a waiting status."])]
          ),
          node_external: E(
            ["Pasos de efecto externo (delegación fuera del motor)", "External effect steps (delegation outside the engine)"],
            ["Pasos que delegan trabajo en otras capacidades de HYDRA: un comando de validación, una acción del editor o una operación del metamodelo, identificados por una referencia. Su resultado es éxito o fallo, y un fallo activa la política de reintento, la salida de error y la compensación. El paso manual no tiene ejecutor.",
              "Steps that delegate work to other HYDRA capabilities: a validation command, an editor action or a metamodel operation, identified by a reference. Their outcome is success or failure, and a failure triggers the retry policy, the error exit and compensation. The manual step has no executor."],
            ["Ejecutores externos ValidationFlowExternalStepExecutor, ActionFlowExternalStepExecutor (hacia EditorSessionToolRuntime) y OperationFlowExternalStepExecutor; sin ejecutor el paso falla salvo configuración allowNoop.",
              "External executors ValidationFlowExternalStepExecutor, ActionFlowExternalStepExecutor (towards EditorSessionToolRuntime) and OperationFlowExternalStepExecutor; with no executor the step fails unless configured with allowNoop."],
            [V("COMMAND_STEP", ["Invoca un comando de validación designado por su referencia.", "Invokes a validation command designated by its reference."]),
             V("ACTION_STEP", ["Invoca una acción del editor de modelos.", "Invokes a model editor action."]),
             V("OPERATION_STEP", ["Invoca una operación declarada en el metamodelo.", "Invokes an operation declared in the metamodel."]),
             V("MANUAL_STEP", ["Modela una intervención humana que el motor no puede realizar; solo prospera si se admite explícitamente el no-operar.", "Models a human intervention the engine cannot perform; it only succeeds if no-op is explicitly allowed."], ["Configuración allowNoop.", "allowNoop configuration."])]
          ),
          node_routing: E(
            ["Pasos de bifurcación y confluencia", "Branching and confluence steps"],
            ["Pasos que reparten la ejecución según una condición booleana, con al menos una salida verdadera y una falsa, o que reúnen caminos. La condición compara una posición del evento con un valor esperado mediante un operador; sin ambas ramas, la comprobación del flujo da error.",
              "Steps that split the run according to a boolean condition, with at least one true and one false exit, or that merge paths. The condition compares a position of the event with an expected value through an operator; without both branches, the flow check reports an error."],
            ["DECISION, ROUTE y MERGE con transiciones TRUE y FALSE; operadores de EventFlowConditionEvaluator: EQUALS, NOT_EQUALS, EXISTS, NOT_EXISTS, CONTAINS, GREATER_THAN, LESS_THAN; error DECISION_BRANCHES.",
              "DECISION, ROUTE and MERGE with TRUE and FALSE transitions; EventFlowConditionEvaluator operators: EQUALS, NOT_EQUALS, EXISTS, NOT_EXISTS, CONTAINS, GREATER_THAN, LESS_THAN; error DECISION_BRANCHES."],
            [V("DECISION", ["Bifurcación binaria: evalúa y elige entre la salida verdadera y la falsa.", "Binary fork: evaluates and chooses between the true and the false exit."]),
             V("ROUTE", ["Encaminamiento con la misma semántica verdadero/falso, gobernado por su configuración.", "Routing with the same true/false semantics, governed by its configuration."]),
             V("MERGE", ["Confluencia sin sincronía: cada camino que llega continúa sin esperar a los demás.", "Confluence without synchronization: each arriving path continues without waiting for the others."])]
          ),
          node_parallel: E(
            ["Pasos de concurrencia lógica (división y unión)", "Logical concurrency steps (split and join)"],
            ["Pasos que abren varias ramas a partir de un mismo punto y las vuelven a sincronizar. La concurrencia es solo lógica: las ramas se turnan en una cola dentro de una misma ejecución. La unión exige al menos dos entradas y espera a que estén presentes todos los tipos de evento configurados.",
              "Steps that open several branches from one point and synchronize them again. Concurrency is only logical: branches take turns in a queue within a single run. The join requires at least two inputs and waits until all configured event types are present."],
            ["PARALLEL_SPLIT sigue las salidas NEXT y ON_SUCCESS; JOIN agrupa por clave según configuration.eventTypes; error JOIN_INPUT_COUNT.",
              "PARALLEL_SPLIT follows the NEXT and ON_SUCCESS exits; JOIN groups by key according to configuration.eventTypes; error JOIN_INPUT_COUNT."],
            [V("PARALLEL_SPLIT", ["Abre una rama por cada salida normal o de éxito, procesadas por turnos y no a la vez.", "Opens one branch per normal or success exit, processed in turns rather than simultaneously."]),
             V("JOIN", ["Barrera de sincronía por grupo: no continúa hasta reunir todos los tipos de evento configurados.", "Per-group synchronization barrier: it does not continue until all configured event types are gathered."])]
          ),
          node_timing: E(
            ["Marcadores temporales del diagrama", "Diagram timing markers"],
            ["Marcadores sin comportamiento propio que hacen visible en el grafo dónde hay un plazo o una repetición. El comportamiento efectivo lo define la política de reintento y plazo de cada paso, no el marcador.",
              "Markers with no behaviour of their own that make visible on the graph where a deadline or repetition exists. The effective behaviour is defined by each step's retry and time-limit policy, not by the marker."],
            ["TIMEOUT y RETRY son de paso directo; el reintento y la detección de plazo viven en EventFlowRuntimeService, a partir de RetryPolicy y TimeoutPolicy.",
              "TIMEOUT and RETRY are pass-through; retry handling and timeout detection live in EventFlowRuntimeService, driven by RetryPolicy and TimeoutPolicy."],
            [V("TIMEOUT", ["Anotación de un límite de espera; no impone ningún plazo.", "Annotation of a waiting limit; it imposes no deadline."]),
             V("RETRY", ["Anotación de una repetición; no repite nada por sí misma.", "Annotation of a repetition; it repeats nothing by itself."])]
          ),
          node_transform: E(
            ["Pasos de conformación y emisión de eventos", "Event shaping and emission steps"],
            ["Pasos que deciden qué continúa por el flujo y qué se publica: filtrar por condición, reasignar datos, suprimir repetidos y emitir un evento derivado. Un paso de emisión produce un evento derivado por ejecución, publicado al cerrar el procesado, lo que permite encadenar flujos.",
              "Steps that decide what continues through the flow and what is published: filtering by condition, remapping data, suppressing repeats and emitting a derived event. An emit step produces one derived event per execution, published when processing ends, which allows chaining flows."],
            ["FILTER, MAP, DEDUPLICATE y EMIT; DEDUPLICATE usa keyPath (por defecto id), ttlMillis y un tope de 4096 claves; EMIT usa eventTypeId o configuration.targetEventTypeId.",
              "FILTER, MAP, DEDUPLICATE and EMIT; DEDUPLICATE uses keyPath (default id), ttlMillis and a cap of 4096 keys; EMIT uses eventTypeId or configuration.targetEventTypeId."],
            [V("FILTER", ["Compuerta por condición sobre el contenido: lo que no cumple se filtra y termina la ejecución.", "Gate by a condition on the content: what does not comply is filtered and ends the run."]),
             V("MAP", ["Reasigna datos del testigo según una lista de correspondencias configurada.", "Reassigns the token's data according to a configured list of mappings."]),
             V("DEDUPLICATE", ["Supresión de duplicados por clave y vigencia, con memoria acotada.", "Duplicate suppression by key and validity, with bounded memory."]),
             V("EMIT", ["Produce un evento derivado del tipo del paso o del destino configurado.", "Produces a derived event of the step's type or the configured target."])]
          ),
          node_pattern: E(
            ["Pasos de correlación temporal de eventos", "Temporal event correlation steps"],
            ["Pasos con memoria que acumulan eventos por clave y deciden cuando se da una situación compuesta: un tamaño de ventana, un conteo, un orden, un conjunto o una falta de evento en plazo. La ausencia se evalúa cuando el reloj del motor avanza y se cancela si el evento vigilado llega antes del plazo.",
              "Steps with memory that accumulate events by key and decide when a composite situation holds: a window size, a count, an order, a set or a missing event within a deadline. Absence is evaluated when the engine clock advances and is cancelled if the watched event arrives before the deadline."],
            ["WINDOW, AGGREGATE, SEQUENCE, PATTERN y ABSENCE; ABSENCE persiste una FlowAbsenceContinuation y usa timeoutMillis y watchEventTypeId; no se hallaron pruebas por nombre.",
              "WINDOW, AGGREGATE, SEQUENCE, PATTERN and ABSENCE; ABSENCE persists a FlowAbsenceContinuation and uses timeoutMillis and watchEventTypeId; no tests were found by name."],
            [V("WINDOW", ["Agrupa eventos en una ventana de tamaño fijo (2 por defecto), deslizante o por lotes.", "Groups events into a fixed-size window (2 by default), sliding or batched."]),
             V("AGGREGATE", ["Resume la serie con un conteo y, si se indica, una suma sobre una posición del evento.", "Summarizes the series with a count and, if given, a sum over a position of the event."]),
             V("SEQUENCE", ["Detecta un orden concreto de tipos; un evento fuera de orden reinicia el avance.", "Detects a concrete order of types; an out-of-order event resets the progress."]),
             V("PATTERN", ["Detecta un conjunto completo de tipos sin importar el orden de llegada.", "Detects a complete set of types regardless of arrival order."]),
             V("ABSENCE", ["Detecta la falta de un evento vigilado en su plazo, y entonces emite un evento de aviso.", "Detects the lack of a watched event within its deadline, and then emits a warning event."])]
          ),
          node_recovery: E(
            ["Pasos de contingencia (alternativa, deshacer, error)", "Contingency steps (fallback, undo, error)"],
            ["Pasos que dan lugar en el grafo al camino de fallo. No ejecutan nada por sí mismos: la compensación real la activa el motor ante un fallo sin salida utilizable, y solo la alternativa y el paso de compensación son destinos válidos de un enlace de compensación.",
              "Steps that give a place in the graph to the failure path. They run nothing themselves: real compensation is triggered by the engine on a failure with no usable exit, and only the fallback and the compensation step are valid targets of a compensation link."],
            ["FALLBACK, COMPENSATION_STEP y ERROR_HANDLER son de paso directo; EventFlowChecker valida el destino de la compensación y emite COMPENSATION_NODE como información.",
              "FALLBACK, COMPENSATION_STEP and ERROR_HANDLER are pass-through; EventFlowChecker validates the compensation target and raises COMPENSATION_NODE as information."],
            [V("FALLBACK", ["Camino alternativo elegible como destino de una compensación.", "Alternative path eligible as the target of a compensation."]),
             V("COMPENSATION_STEP", ["Paso que expresa el deshacer; destino válido de un enlace de compensación.", "Step that expresses the undo; a valid target of a compensation link."]),
             V("ERROR_HANDLER", ["Punto donde se atiende el error que llega por la transición de error; no es destino de compensación.", "Point where the error arriving through the error transition is handled; not a compensation target."])]
          ),
          transition: E(
            ["Transición (arco dirigido con semántica de disparo)", "Transition (directed arc with firing semantics)"],
            ["Arco entre un paso de origen y uno de destino cuyo tipo decide cuándo se sigue: tras un resultado normal o satisfactorio, ante un error, ante un plazo vencido o según una condición verdadero/falso. Un paso puede tener 0..N salidas ordenadas; tras un fallo se prefiere la de plazo o la de error, y una división paralela sigue todas las normales.",
              "Arc between a source and a target step whose type decides when it is followed: after a normal or successful outcome, on error, on a missed deadline or per a true/false condition. A step can have 0..N ordered exits; after a failure the timeout or the error one is preferred, and a parallel split follows all normal ones."],
            ["FlowTransition con FlowTransitionKind de 7 valores; EventFlowCompiler ordena por order y EventFlowRuntimeSupport aplica las reglas de selección.",
              "FlowTransition with a 7-value FlowTransitionKind; EventFlowCompiler sorts by order and EventFlowRuntimeSupport applies the selection rules."],
            [V("NEXT", ["Camino por defecto, válido tras cualquier resultado que no sea un fallo.", "Default path, valid after any outcome that is not a failure."]),
             V("ON_SUCCESS", ["Camino reservado al caso en que el paso concluyó sin fallo.", "Path reserved for when the step concluded without failure."]),
             V("ON_ERROR", ["Camino de captura de fallos del paso, que evita la compensación.", "Failure-capture path of the step, which avoids compensation."]),
             V("COMPENSATES", ["Declarada en el modelo pero sin consumidor; la compensación se rige por los enlaces de compensación.", "Declared in the model but without a consumer; compensation is governed by compensation links."], ["Sin consumidor encontrado en el motor.", "No consumer found in the engine."]),
             V("TIMEOUT", ["Camino de salida cuando el paso agota su plazo.", "Exit path when the step runs out of time."]),
             V("TRUE", ["Salida de una bifurcación cuando la condición se cumple.", "Exit of a fork when the condition holds."]),
             V("FALSE", ["Salida de una bifurcación cuando la condición no se cumple.", "Exit of a fork when the condition does not hold."])]
          ),
          retry_timeout: E(
            ["Política de resiliencia del paso (reintento y plazo)", "Step resilience policy (retry and time limit)"],
            ["Par opcional de políticas de un paso: cuántos intentos admite, con qué espera y crecimiento entre ellos, y cuánto tiempo puede tardar antes de contar como vencido y salir por la transición de plazo. Pertenece a un único paso; los reintentos pendientes y plazos progresan cuando avanza el reloj del motor.",
              "Optional pair of policies of a step: how many attempts it admits, with what wait and growth between them, and how long it may take before counting as expired and leaving through the timeout transition. It belongs to one step; pending retries and deadlines advance when the engine clock advances."],
            ["RetryPolicy (maxAttempts, delayMillis, backoffMultiplier) y TimeoutPolicy (timeoutMillis, timeoutTransitionId); los reintentos programados se guardan como FlowRetryContinuation.",
              "RetryPolicy (maxAttempts, delayMillis, backoffMultiplier) and TimeoutPolicy (timeoutMillis, timeoutTransitionId); scheduled retries are stored as FlowRetryContinuation."]
          ),
          compensation_link: E(
            ["Vínculo de compensación (saga: paso → deshacer)", "Compensation link (saga: step → undo)"],
            ["Asociación que declara qué paso compensador revierte el efecto de un paso compensado cuando el flujo falla sin salida utilizable. Pertenece a un flujo y une 2 pasos; las compensaciones se aplican en orden inverso al de finalización, y el resultado es compensado solo si se ejecutó al menos una.",
              "Association that declares which compensating step reverts the effect of a compensated step when the flow fails with no usable exit. It belongs to a flow and ties 2 steps; compensations are applied in reverse order of completion, and the outcome is compensated only if at least one ran."],
            ["CompensationLink (id, nodeId, compensationNodeId, los tres obligatorios) dentro de FlowDefinition; la compensación en orden LIFO la ejecuta EventFlowRuntimeService.",
              "CompensationLink (id, nodeId, compensationNodeId, all three mandatory) inside FlowDefinition; the LIFO compensation is run by EventFlowRuntimeService."]
          ),
          execution_trace: E(
            ["Traza de ejecución (resultado observable de un flujo)", "Execution trace (observable outcome of a flow)"],
            ["Registro derivado, de solo lectura, de una ejecución de flujo: una por cada flujo disparado por un evento, con los pasos recorridos y un estado final. Solo se genera si el flujo estaba desplegado y se descartan las más antiguas al superar el máximo. El estado se sobrescribe en vez de agregarse, por lo que puede no reflejar todas las ramas.",
              "Derived, read-only record of a flow run: one for each flow triggered by an event, with the steps walked and a final status. It is only produced if the flow was deployed and the oldest are discarded when the maximum is exceeded. The status is overwritten rather than aggregated, so it may not reflect all branches."],
            ["FlowExecutionTrace con FlowNodeExecution y estado FlowExecutionStatus; límites de EventFlowProperties: 200 trazas, profundidad 32, 256 estados, 128 despliegues. Hallazgo F4 sobre la sobrescritura del estado.",
              "FlowExecutionTrace with FlowNodeExecution and FlowExecutionStatus status; EventFlowProperties limits: 200 traces, depth 32, 256 states, 128 deployments. Finding F4 on status overwriting."],
            [V("COMPLETED", ["Cierre normal: toda rama alcanzó su final.", "Normal closure: every branch reached its end."]),
             V("FILTERED", ["Cierre por descarte: una compuerta no se cumplió y la ejecución se abandonó.", "Closure by discard: a gate did not hold and the run was abandoned."]),
             V("WAITING", ["Ejecución suspendida a la espera de un evento, un reintento o un plazo.", "Run suspended awaiting an event, a retry or a deadline."]),
             V("FAILED", ["Cierre por fallo sin salida de error ni compensación.", "Closure by failure with neither error exit nor compensation."]),
             V("COMPENSATED", ["Cierre tras un fallo en el que se revirtió al menos un paso.", "Closure after a failure in which at least one step was reverted."]),
             V("DEPTH_LIMIT", ["Corte de seguridad al alcanzar el tope de encadenamiento de derivados.", "Safety cut when the chaining limit of derived events is reached."])]
          ),
          diagnostics: E(
            ["Diagnóstico de comprobación (hallazgo sobre el modelo)", "Check diagnostic (finding about the model)"],
            ["Hallazgo estructurado producido al comprobar un modelo de eventos o de flujos, con gravedad, código, elemento y ruta afectados. Una comprobación produce 0..N diagnósticos; los errores impiden compilar el flujo o validar el enlace, mientras que los avisos y la información no bloquean.",
              "Structured finding produced when checking an event or flow model, with severity, code, affected element and path. A check yields 0..N diagnostics; errors prevent compiling the flow or validating the binding, while warnings and information do not block."],
            ["EventDiagnostic(severity, code, elementId, path, message) producido por EventModelChecker y EventFlowChecker.",
              "EventDiagnostic(severity, code, elementId, path, message) produced by EventModelChecker and EventFlowChecker."],
            [V("ERROR", ["Violación de una regla del modelo, como un reservado redefinido, ids duplicados o un grafo sin entrada única.", "Violation of a model rule, such as a redefined reserved type, duplicate ids or a graph without a single entry."]),
             V("WARNING", ["Anomalía tolerada, como un espacio de nombres ajeno al del modelo.", "Tolerated anomaly, such as a namespace foreign to the model's."], ["FOREIGN_NAMESPACE.", "FOREIGN_NAMESPACE."]),
             V("INFO", ["Observación sobre el grafo, como la presencia de un destino de compensación.", "Observation about the graph, such as the presence of a compensation target."], ["COMPENSATION_NODE.", "COMPENSATION_NODE."])]
          )
        }
      }
    },

    procs: {
      events_define: {
        nf: T("Elaborar el modelo de eventos", "Build the event model"),
        df: T("Secuencia que lleva de un tipo de evento a un modelo de eventos válido: se declaran tipos y campos, se derivan eventos mediante enlaces con sus copias de datos y se itera hasta que la comprobación no arroja errores. Los enlaces y las copias son opcionales y condicionales.",
          "Sequence that takes an event type to a valid event model: types and fields are declared, events are derived through bindings with their data copies, and it iterates until the check yields no errors. Bindings and copies are optional and conditional."),
        nt: T("EventModelChecker → CanonicalEventModelController", "EventModelChecker → CanonicalEventModelController"),
        dt: T("El modelo se comprueba con EventModelChecker (POST /api/canonical-metamodels/{id}/events/check) y se guarda con POST|PUT .../events usando If-Match-Definitions e If-Match-Bindings. Pruebas: EventContractTests, EventModelCheckerTests, CanonicalEventModelControllerTests.",
          "The model is checked with EventModelChecker (POST /api/canonical-metamodels/{id}/events/check) and saved with POST|PUT .../events using If-Match-Definitions and If-Match-Bindings. Tests: EventContractTests, EventModelCheckerTests, CanonicalEventModelControllerTests."),
        nodes: {
          a1: N(["Declarar un tipo de evento", "Declare an event type"], ["identidad → contrato nominal", "identity → nominal contract"], ["Metamodelo guardado + identidad del tipo → tipo de evento definido", "Saved metamodel + type identity → defined event type"], "EventTypeDefinition", ["Registro con validación de espacio de nombres", "Record with namespace validation"], ["El espacio de nombres se valida por expresión regular en EventTypeDefinition.", "The namespace is validated by a regular expression in EventTypeDefinition."]),
          a2: N(["Tipar la carga útil", "Type the payload"], ["campos tipados del tipo", "typed fields of the type"], ["Tipo de evento + nombre y tipo del dato → campo de carga útil", "Event type + name and type of the datum → payload field"], "EventFieldDefinition / EventFieldType", ["13 valores de tipo de campo", "13 field type values"]),
          a3: N(["Declarar una derivación", "Declare a derivation"], ["origen → destino propio, política", "source → custom target, policy"], ["Evento de origen + destino propio → enlace de derivación", "Source event + custom target → derivation binding"], "EventBinding", ["Origen reservado o propio; destino propio", "Reserved or custom source; custom target"], ["EventModelChecker rechaza un destino reservado (comprobación de destino propio).", "EventModelChecker rejects a reserved target (custom-target check)."]),
          a4: N(["Asignar valores al destino", "Assign values to the target"], ["campo destino ← ruta|expresión|fijo", "target field ← path|expression|fixed"], ["Enlace + campos obligatorios del destino → correspondencias que los cubren", "Binding + required target fields → mappings that cover them"], "EventPayloadMapping", ["Exactamente una fuente por campo", "Exactly one source per field"]),
          a5: N(["Verificar la coherencia del modelo", "Verify model consistency"], ["reglas × modelo → hallazgos", "rules × model → findings"], ["Modelo de eventos y enlaces → diagnósticos por gravedad", "Event and binding model → diagnostics by severity"], "EventModelChecker", ["POST .../events/check", "POST .../events/check"], ["Comprueba tipos reservados, ids y nombres duplicados, espacios de nombres y mapeo de campos obligatorios.", "Checks reserved types, duplicate ids and names, namespaces and required-field mapping."]),
          d1: N(["¿Existe algún hallazgo bloqueante?", "Is there any blocking finding?"], ["gravedad ERROR frente al resto", "ERROR severity versus the rest"], ["Diagnósticos → continuar a guardar o volver a editar", "Diagnostics → continue to save or return to editing"]),
          a6: N(["Versionar el modelo", "Version the model"], ["persistencia con concurrencia optimista", "persistence with optimistic concurrency"], ["Modelo comprobado → modelo persistido en nueva versión", "Checked model → model persisted in a new version"], "CanonicalEventModelController", ["POST|PUT .../events + If-Match", "POST|PUT .../events + If-Match"], ["La escritura usa If-Match-Definitions e If-Match-Bindings.", "Writing uses If-Match-Definitions and If-Match-Bindings."]),
          r1: N(["Modelo de eventos persistido", "Persisted event model"], ["vocabulario y enlaces disponibles", "vocabulary and bindings available"], ["Tipos, campos y enlaces → base reutilizable por flujos y enlaces", "Types, fields and bindings → base reusable by flows and bindings"])
        }
      },

      events_compose_flow: {
        nf: T("Componer un flujo de reacción", "Compose a reaction flow"),
        df: T("Construcción de un grafo de reacción válido: se declara el flujo con sus disparadores, se fija la entrada, se añaden pasos de las nueve familias, se conectan con transiciones y se completan opcionalmente la resiliencia, las compensaciones y las variables. Se comprueba y se corrige en bucle hasta no tener errores.",
          "Construction of a valid reaction graph: the flow is declared with its triggers, the entry is fixed, steps of the nine families are added and connected with transitions, and resilience, compensations and variables are optionally completed. It is checked and corrected in a loop until no errors remain."),
        nt: T("EventFlowChecker → .../event-flows", "EventFlowChecker → .../event-flows"),
        dt: T("La comprobación usa EventFlowChecker (POST .../event-flows/check, con códigos como FLOW_START_COUNT o JOIN_INPUT_COUNT) y la persistencia POST|PUT .../event-flows con If-Match. El diagrama se guarda aparte con PUT .../event-flows/diagram. Pruebas: EventFlowCheckerTests.",
          "The check uses EventFlowChecker (POST .../event-flows/check, with codes such as FLOW_START_COUNT or JOIN_INPUT_COUNT) and persistence is POST|PUT .../event-flows with If-Match. The diagram is saved separately with PUT .../event-flows/diagram. Tests: EventFlowCheckerTests."),
        nodes: {
          a1: N(["Declarar flujo y disparadores", "Declare flow and triggers"], ["identidad + tipos que lo arrancan", "identity + types that start it"], ["Identidad y tipos de evento → flujo sin pasos con sus disparadores", "Identity and event types → stepless flow with its triggers"], "FlowDefinition", ["triggerEventTypeIds", "triggerEventTypeIds"]),
          a2: N(["Fijar la frontera de entrada", "Fix the entry boundary"], ["una sola entrada: inicio o evento", "a single entry: start or event"], ["Flujo → única entrada START o SOURCE", "Flow → single START or SOURCE entry"], "FlowNodeKind START / SOURCE", ["FLOW_START_COUNT si no hay exactamente una", "FLOW_START_COUNT if not exactly one"]),
          a3: N(["Seleccionar pasos por familia", "Select steps by family"], ["29 tipos en nueve familias", "29 types in nine families"], ["Familias de paso → pasos configurados con su evento o referencia", "Step families → configured steps with their event or reference"], "FlowNodeKind (29)", ["FlowNode", "FlowNode"], ["El comportamiento de cada tipo lo despacha EventFlowRuntimeNodeExecutor.", "The behaviour of each type is dispatched by EventFlowRuntimeNodeExecutor."]),
          a4: N(["Trazar los arcos de control", "Draw the control arcs"], ["tipo de arco y orden de salida", "arc type and exit order"], ["Pasos → grafo conexo con salidas verdadero/falso donde se bifurca", "Steps → connected graph with true/false exits where it forks"], "FlowTransition / FlowTransitionKind", ["7 tipos de transición", "7 transition kinds"]),
          a5: N(["Añadir resiliencia y compensación", "Add resilience and compensation"], ["intentos, plazos y paso inverso", "attempts, deadlines and inverse step"], ["Pasos que actúan fuera o esperan → política de reintento, plazo y compensación", "Steps that act outside or wait → retry, deadline and compensation policy"], "RetryPolicy / TimeoutPolicy / CompensationLink", ["Políticas por paso y vínculos del flujo", "Per-step policies and flow links"]),
          a6: N(["Declarar estado del flujo", "Declare flow state"], ["variables tipadas con valor inicial", "typed variables with initial value"], ["Flujo → variables disponibles durante la ejecución", "Flow → variables available during the run"], "FlowVariable", ["id, name, type, initialValue", "id, name, type, initialValue"]),
          a7: N(["Verificar el grafo del flujo", "Verify the flow graph"], ["estructura × reglas → hallazgos", "structure × rules → findings"], ["Flujo → diagnósticos de entrada, final, ramas, uniones, alcance y compensación", "Flow → diagnostics on entry, end, branches, joins, reachability and compensation"], "EventFlowChecker", ["POST .../event-flows/check", "POST .../event-flows/check"], ["Comprueba entradas, finales, ramas, uniones, nodos huérfanos y alcance de cada paso.", "Checks entries, ends, branches, joins, orphan nodes and the reachability of each step."]),
          d1: N(["¿Impide el grafo compilar?", "Does the graph block compilation?"], ["errores frente a avisos", "errors versus warnings"], ["Diagnósticos → corregir pasos o guardar", "Diagnostics → fix steps or save"]),
          a8: N(["Persistir flujo y diagrama", "Persist flow and diagram"], ["semántica y geometría por separado", "semantics and geometry kept apart"], ["Flujo comprobado → versión nueva del flujo y de su diagrama", "Checked flow → new version of the flow and its diagram"], "POST|PUT .../event-flows", ["If-Match; diagrama en PUT .../diagram", "If-Match; diagram via PUT .../diagram"]),
          r1: N(["Flujo listo para desplegarse", "Flow ready to be deployed"], ["modelo apto para compilar", "model fit for compilation"], ["Flujo sin errores → candidato a compilación y activación", "Error-free flow → candidate for compilation and activation"])
        }
      },

      events_execute_flow: {
        nf: T("Procesar un evento con los flujos desplegados", "Process an event with the deployed flows"),
        df: T("Ciclo de vida de una ejecución: el evento reanuda esperas y cancela vigilancias, selecciona los flujos cuyo disparador coincide y recorre sus pasos por turnos. En cada paso pueden darse el filtrado, el reintento, la compensación o la transición elegida; al final se guarda una traza y se publican los derivados, que pueden disparar otros flujos.",
          "Life cycle of a run: the event resumes waits and cancels watches, selects the flows whose trigger matches and walks their steps in turns. At each step filtering, retry, compensation or the chosen transition may occur; at the end a trace is saved and derived events are published, which may trigger other flows."),
        nt: T("EventFlowRuntimeService.processWithDeployment", "EventFlowRuntimeService.processWithDeployment"),
        dt: T("Un EventFlowRuntimeService suscrito al bus procesa cada evento (onEvent, processWithDeployment) y recorre los pasos con una cola en executeFlow, delegando cada kind en EventFlowRuntimeNodeExecutor. El estado de traza lo escribe el propio servicio; un tick avanza reintentos, plazos y ausencias. Pruebas: EventFlowRuntimeStateTests, EventFlowRuntimeAutoDeploymentTests, EventFlowCompilerTests; no se hallaron pruebas por nombre para el ejecutor de nodos.",
          "An EventFlowRuntimeService subscribed to the bus processes each event (onEvent, processWithDeployment) and walks the steps with a queue in executeFlow, delegating each kind to EventFlowRuntimeNodeExecutor. The trace status is written by the service itself; a tick advances retries, deadlines and absences. Tests: EventFlowRuntimeStateTests, EventFlowRuntimeAutoDeploymentTests, EventFlowCompilerTests; no tests were found by name for the node executor."),
        nodes: {
          d0: N(["¿Motor parado explícitamente?", "Engine explicitly stopped?"], ["estado de despliegue del metamodelo", "metamodel deployment state"], ["Evento + estado del motor → ignorar o procesar (con autodespliegue)", "Event + engine state → ignore or process (with auto-deployment)"]),
          a1: N(["Reactivar continuaciones pendientes", "Reactivate pending continuations"], ["esperas despertadas, vigilancias anuladas", "waits woken, watches cancelled"], ["Evento + continuaciones guardadas → esperas reanudadas y ausencias canceladas", "Event + stored continuations → resumed waits and cancelled absences"], "processWithDeployment", ["EventFlowRuntimeService", "EventFlowRuntimeService"], ["Reanuda las esperas del evento y cancela las vigilancias de ausencia cuyo evento llegó (pruebas: EventFlowRuntimeStateTests).", "Resumes the event's waits and cancels the absence watches whose event arrived (tests: EventFlowRuntimeStateTests)."]),
          a2: N(["Resolver flujos por disparador", "Resolve flows by trigger"], ["tipo del evento → flujos disparados", "event type → triggered flows"], ["Tipo de evento → conjunto de flujos desplegados que reaccionan", "Event type → set of deployed flows that react"], "EventFlowCompiler.triggerIndex", ["Índice de disparadores compilado", "Compiled trigger index"], ["Los flujos se buscan por el índice de disparadores que construye EventFlowCompiler.", "Flows are looked up through the trigger index built by EventFlowCompiler."]),
          a3: N(["Recorrer el grafo por turnos", "Walk the graph in turns"], ["cola de testigos, sin concurrencia", "token queue, no concurrency"], ["Flujo disparado → testigos procesados uno tras otro", "Triggered flow → tokens processed one after another"], "executeFlow", ["Cola secuencial", "Sequential queue"], ["executeFlow procesa los testigos de las ramas en una cola, uno tras otro.", "executeFlow processes the branches' tokens in a queue, one after another."]),
          d1: N(["¿Pasa la compuerta del paso?", "Does the step gate pass?"], ["condición satisfecha o filtrado", "condition met or filtered"], ["Testigo + condición del paso → continuar o descartar como filtrado", "Token + step condition → continue or drop as filtered"]),
          a4: N(["Aplicar el comportamiento del tipo de paso", "Apply the step type's behaviour"], ["despacho por tipo de paso", "dispatch by step kind"], ["Testigo + paso → decisión, emisión, espera o efecto externo", "Token + step → decision, emission, wait or external effect"], "EventFlowRuntimeNodeExecutor", ["Despacho por FlowNodeKind", "Dispatch by FlowNodeKind"], ["Cada FlowNodeKind tiene su comportamiento en el ejecutor; los ejecutores externos se invocan desde él.", "Each FlowNodeKind has its behaviour in the executor; external executors are invoked from it."]),
          d2: N(["¿Fallo o plazo vencido en el paso?", "Failure or missed deadline in the step?"], ["resultado anómalo → resiliencia", "anomalous outcome → resilience"], ["Resultado del paso → seguir, reintentar o recuperar", "Step outcome → proceed, retry or recover"]),
          a5: N(["Reprogramar el intento", "Reschedule the attempt"], ["reintento inmediato o diferido", "immediate or deferred retry"], ["Paso fallido con intentos disponibles → reintento ejecutado o programado", "Failed step with attempts left → retry executed or scheduled"], "FlowRetryContinuation", ["EventFlowRuntimeService (reintentos)", "EventFlowRuntimeService (retries)"], ["Con retraso cero se repite al instante; si no, se programa una continuación y la traza queda en WAITING.", "With zero delay it repeats at once; otherwise a continuation is scheduled and the trace is left in WAITING."]),
          a6: N(["Revertir pasos completados", "Revert completed steps"], ["compensación en orden inverso", "reverse-order compensation"], ["Fallo sin salida utilizable → pasos compensables revertidos; COMPENSATED o FAILED", "Failure with no usable exit → compensable steps reverted; COMPENSATED or FAILED"], "EventFlowRuntimeService (compensación LIFO)", ["Enlaces CompensationLink", "CompensationLink links"], ["Las compensaciones de los pasos completados se aplican en orden LIFO a través de los CompensationLink.", "The compensations of completed steps are applied in LIFO order through CompensationLink."]),
          a7: N(["Elegir el arco de salida", "Choose the exit arc"], ["tipo de arco según resultado", "arc kind by outcome"], ["Resultado del paso → siguiente paso o fin de rama", "Step outcome → next step or branch end"], "EventFlowRuntimeSupport", ["Reglas de selección de transición", "Transition selection rules"], ["Aplica las reglas: plazo o error tras un fallo, todas las normales en una división paralela y, en el resto, la primera normal o de éxito.", "Applies the rules: timeout or error after a failure, all normal ones in a parallel split and, otherwise, the first normal or success one."]),
          a8: N(["Registrar traza y estado del motor", "Record the trace and engine state"], ["resultado observable de la ejecución", "observable outcome of the run"], ["Ramas terminadas → traza con estado final y estado del motor actualizado", "Finished branches → trace with final status and updated engine state"], "FlowExecutionTrace", ["Estado sobrescrito (F4)", "Overwritten status (F4)"], ["El estado de la traza se sobrescribe en vez de agregarse (hallazgo F4).", "The trace status is overwritten rather than aggregated (finding F4)."]),
          a9: N(["Reinyectar eventos derivados", "Re-inject derived events"], ["emisión → nuevo ciclo de procesado", "emission → new processing cycle"], ["Pasos de emisión → eventos derivados que reentran en el canal", "Emit steps → derived events that re-enter the channel"], "EventFlowRuntimeEventFactory", ["Publicación en el bus", "Publication on the bus"], ["La factoría construye los eventos derivados que se publican al terminar el procesado.", "The factory builds the derived events that are published when processing ends."]),
          r1: N(["Registro de la ejecución", "Run record"], ["una traza por flujo disparado", "one trace per triggered flow"], ["Ejecución completada → traza consultable con estado final", "Completed run → queryable trace with final status"])
        }
      },

      events_publish_session: {
        nf: T("Propagar los eventos de una sesión de edición", "Propagate the events of an editing session"),
        df: T("Cómo un cambio en la sesión se convierte en un evento canónico y llega a sus consumidores: se comprueba la política de publicación, se construye la envoltura y se publica de inmediato o se retiene hasta un guardado correcto. El canal entrega luego a enlaces, flujos y gamificación, y la validación y las correcciones aportan sus propios eventos reservados.",
          "How a change in the session becomes a canonical event and reaches its consumers: the publishing policy is checked, the envelope is built and it is published at once or held until a successful save. The channel then delivers to bindings, flows and gamification, and validation and fixes contribute their own reserved events."),
        nt: T("EditorModelSessionService.publish → InMemoryCanonicalEventBus", "EditorModelSessionService.publish → InMemoryCanonicalEventBus"),
        dt: T("EditorModelSessionService.publish decide si se publica, EditorSessionEventPublisher construye el evento y EditorSessionPendingEventBuffer lo retiene en niveles de memoria, volcado y cola; InMemoryCanonicalEventBus lo entrega de forma síncrona a EventBindingRuntimeService, EventFlowRuntimeService y la gamificación. Hallazgo verificado: los interruptores por sesión se escriben en el payload pero los consumidores los leen de metadata, de modo que desactivar por sesión no les llega.",
          "EditorModelSessionService.publish decides whether to publish, EditorSessionEventPublisher builds the event and EditorSessionPendingEventBuffer holds it in memory, spool and queue tiers; InMemoryCanonicalEventBus delivers it synchronously to EventBindingRuntimeService, EventFlowRuntimeService and gamification. Verified finding: the per-session switches are written to the payload but consumers read them from metadata, so per-session disabling never reaches them."),
        nodes: {
          d1: N(["¿Autoriza la política de sesión publicar?", "Does the session policy allow publishing?"], ["interruptor de publicación", "publishing switch"], ["Cambio de sesión + política → publicar o descartar el hecho", "Session change + policy → publish or discard the fact"], "EditorModelSessionService.publish", ["Compuerta publishEvents", "publishEvents gate"], ["La compuerta es publishEvents; el hallazgo verificado indica que el interruptor por sesión no llega a los consumidores.", "The gate is publishEvents; the verified finding says the per-session switch does not reach consumers."]),
          a1: N(["Armar la envoltura del hecho", "Assemble the fact envelope"], ["origen, contexto, actor, sujeto, correlación", "source, context, actor, subject, correlation"], ["Cambio de sesión → evento canónico con contexto de edición", "Session change → canonical event with editing context"], "EditorSessionEventPublisher", ["Construye el CanonicalEvent", "Builds the CanonicalEvent"]),
          d2: N(["¿Salida inmediata o diferida?", "Immediate or deferred release?"], ["autoguardado o sin copia de trabajo", "autosave or no working copy"], ["Evento + modo de sesión → publicar ya o retener", "Event + session mode → publish now or hold"], "EditorSessionPendingEventBuffer", ["Decisión de almacenamiento previo", "Pre-storage decision"]),
          a2: N(["Retener hasta el guardado", "Hold until save"], ["niveles memoria → volcado → cola", "tiers memory → spool → queue"], ["Evento diferido → evento retenido, descartado si se cierra sin guardar", "Deferred event → held event, discarded if closed without saving"], "EditorSessionPendingEventBuffer", ["Búfer por niveles", "Tiered buffer"], ["Retiene en memoria, luego en volcado y por último en una cola; no es duradero y el volcado JSONL no se recupera tras reiniciar (F6).", "Holds in memory, then in a spool and lastly in a queue; it is not durable and the JSONL spool is not recovered after restart (F6)."]),
          a3: N(["Confirmar la edición con éxito", "Confirm the edit succeeded"], ["guardado → retenidos liberables", "save → held events releasable"], ["Guardado correcto → eventos retenidos habilitados para publicar", "Successful save → held events enabled for publication"]),
          a4: N(["Entregar a todos los suscriptores", "Deliver to all subscribers"], ["canal síncrono, orden de llegada", "synchronous channel, arrival order"], ["Evento canónico → una entrega por suscriptor, cada uno en su turno", "Canonical event → one delivery per subscriber, each in turn"], "InMemoryCanonicalEventBus", ["Bus síncrono FIFO", "Synchronous FIFO bus"], ["Entrega a los suscriptores de forma síncrona y en orden; un fallo al publicar se anota sin reintentar.", "Delivers to subscribers synchronously and in order; a publishing failure is logged without retry."]),
          a5: N(["Derivar eventos por reglas", "Derive events by rules"], ["enlaces con ese origen", "bindings with that source"], ["Evento entregado → eventos derivados por los enlaces coincidentes", "Delivered event → events derived by the matching bindings"], "EventBindingRuntimeService", ["Suscriptor del bus", "Bus subscriber"]),
          a6: N(["Disparar flujos de reacción", "Trigger reaction flows"], ["flujos con ese disparador", "flows with that trigger"], ["Evento entregado → ejecución de los flujos disparados", "Delivered event → execution of the triggered flows"], "EventFlowRuntimeService.onEvent", ["Suscriptor del bus", "Bus subscriber"]),
          a7: N(["Aplicar reglas de recompensa", "Apply reward rules"], ["reacción de la gamificación", "gamification reaction"], ["Evento entregado → puntos concedidos o retirados", "Delivered event → points granted or taken away"], "GamificationRuntimeService", ["Suscriptor del bus", "Bus subscriber"]),
          a8: N(["Aportar eventos de validación y corrección", "Contribute validation and fix events"], ["productores reservados adicionales", "additional reserved producers"], ["Validación ejecutada o corrección aplicada → eventos reservados al canal", "Validation run or fix applied → reserved events on the channel"], "ValidationOrchestrator / QuickFixRuntimeService", ["Productores de eventos reservados", "Reserved event producers"])
        }
      },

      events_binding_runtime: {
        nf: T("Derivar un evento mediante un enlace", "Derive an event through a binding"),
        df: T("Aplicación en ejecución de un enlace: se respeta el tope de encadenamiento, se localizan los enlaces activos del tipo de origen, se evalúa su condición, se rellenan los campos del destino y se publica el derivado, cuyo origen es el propio enlace. Si algo falla, la política de error decide entre omitir, avisar o desactivar el enlace.",
          "Run-time application of a binding: the chaining limit is respected, the enabled bindings of the source type are located, their condition is evaluated, the target fields are filled and the derived event is published, whose source is the binding itself. If anything fails, the error policy decides between skipping, reporting or disabling the binding."),
        nt: T("EventBindingRuntimeService", "EventBindingRuntimeService"),
        dt: T("EventBindingRuntimeService se suscribe al bus, controla la profundidad, evalúa la condición, construye el evento derivado y aplica la política de fallo; el estado de enlaces desactivados es solo en memoria. Pruebas: EventBindingRuntimeServiceTests.",
          "EventBindingRuntimeService subscribes to the bus, controls the depth, evaluates the condition, builds the derived event and applies the failure policy; the disabled-binding state is in memory only. Tests: EventBindingRuntimeServiceTests."),
        nodes: {
          d0: N(["¿Cabe otro nivel de derivación?", "Is another derivation level allowed?"], ["tope de encadenamiento de 32", "chaining cap of 32"], ["Evento + profundidad de la cadena → derivar o cortar el bucle", "Event + chain depth → derive or cut the loop"], "EventBindingRuntimeService (tope de profundidad)", ["Corte a 32 niveles", "Cut at 32 levels"]),
          a1: N(["Localizar enlaces aplicables", "Locate applicable bindings"], ["origen coincidente y enlace activo", "matching source and enabled binding"], ["Tipo del evento → enlaces activos con ese origen", "Event type → enabled bindings with that source"], "EventBindingRuntimeService", ["Excluye los desactivados por fallo", "Excludes those disabled by failure"]),
          d1: N(["¿Se satisface la condición del enlace?", "Is the binding condition satisfied?"], ["ausencia de condición equivale a sí", "no condition counts as yes"], ["Evento + condición → el enlace actúa o se descarta", "Event + condition → the binding acts or is dropped"], "EventBindingRuntimeService (condición)", ["Evaluación de expresión", "Expression evaluation"]),
          a2: N(["Poblar la carga del destino", "Populate the target payload"], ["ruta, expresión o constante por campo", "path, expression or constant per field"], ["Evento original + correspondencias → carga útil del evento derivado", "Original event + mappings → payload of the derived event"], "EventPayloadMapping", ["Una fuente por campo", "One source per field"]),
          a3: N(["Emitir el evento derivado", "Emit the derived event"], ["origen = el enlace, reentra en el canal", "source = the binding, re-enters the channel"], ["Carga útil → evento derivado publicado en el bus", "Payload → derived event published on the bus"], "EventBindingRuntimeService", ["Origen del derivado: el enlace", "Source of the derived event: the binding"]),
          d2: N(["¿Terminó la aplicación en fallo?", "Did the application end in failure?"], ["política SKIP, REPORT o DISABLE_BINDING", "policy SKIP, REPORT or DISABLE_BINDING"], ["Resultado del enlace → continuar o activar su política de error", "Binding outcome → continue or trigger its error policy"], "EventBindingErrorPolicy", ["Política de fallo del enlace", "Binding failure policy"]),
          a4: N(["Reaccionar al fallo del enlace", "React to the binding failure"], ["ignorar, anotar o apagar el enlace", "ignore, log or switch off the binding"], ["Fallo + política → sin rastro, aviso, o aviso y enlace inactivo", "Failure + policy → no trace, warning, or warning and inactive binding"], "EventBindingErrorPolicy", ["Desactivación solo en memoria", "Disabling in memory only"], ["DISABLE_BINDING deja el enlace inactivo hasta reiniciar el motor; el estado no es duradero (F6).", "DISABLE_BINDING leaves the binding inactive until the engine restarts; the state is not durable (F6)."]),
          r1: N(["Evento derivado producido", "Derived event produced"], ["hecho nuevo con origen en el enlace", "new fact originating from the binding"], ["Aplicación correcta → evento propio disponible para suscriptores", "Successful application → custom event available to subscribers"])
        }
      },

      events_author_form: {
        nf: T("Definir eventos con formulario estructurado", "Define events with a structured form"),
        df: T("Autoría guiada de tipos, campos y enlaces: se fija el espacio de nombres, se crean tipos con valores por defecto, se refinan categoría y campos (con tipo enumerado u objeto solo cuando proceda), se configuran condiciones y copias de un enlace, se valida y se persiste.",
          "Guided authoring of types, fields and bindings: the namespace is fixed, types are created with default values, category and fields are refined (with an enumeration or object type only where applicable), a binding's conditions and copies are configured, and the model is validated and persisted."),
        nt: T("EventDesigner (FORM) → .../events", "EventDesigner (FORM) → .../events"),
        dt: T("Se resuelve en la vista FORM de EventDesigner.tsx con eventDesignerFactory.ts, eventDesignerValidation.ts y eventCategoryOptions.ts; la comprobación completa y el guardado llaman a los endpoints .../events/check y .../events con If-Match-Definitions e If-Match-Bindings.",
          "It is resolved in the FORM view of EventDesigner.tsx with eventDesignerFactory.ts, eventDesignerValidation.ts and eventCategoryOptions.ts; the full check and the save call the .../events/check and .../events endpoints with If-Match-Definitions and If-Match-Bindings."),
        nodes: {
          a1: N(["Fijar el espacio de nombres", "Fix the namespace"], ["prefijo común de los tipos propios", "common prefix of custom types"], ["Metamodelo → espacio de nombres válido del modelo", "Metamodel → valid namespace of the model"], "eventDesignerValidation", ["Validación en el cliente", "Client-side validation"]),
          a2: N(["Crear un tipo con valores por defecto", "Create a type with defaults"], ["transitoria, versión 1.0, categoría dominio", "transient, version 1.0, domain category"], ["Espacio de nombres → tipo de evento propio inicial", "Namespace → initial custom event type"], "eventDesignerFactory", ["Creación con valores por defecto", "Creation with defaults"]),
          a3: N(["Clasificar el tipo", "Classify the type"], ["categoría predefinida o libre", "preset or free category"], ["Tipo creado → tipo con categoría asignada", "Created type → type with assigned category"], "eventCategoryOptions", ["Opciones de categoría", "Category options"]),
          a4: N(["Declarar campos de carga útil", "Declare payload fields"], ["campo inicial de tipo texto", "initial text-type field"], ["Tipo de evento → campos con nombre y tipo", "Event type → fields with name and type"], "eventDesignerFactory", ["Campo por defecto \"value\"", "Default field \"value\""]),
          d1: N(["¿Exige el tipo una referencia adicional?", "Does the type require an extra reference?"], ["solo ENUM u OBJECT", "only ENUM or OBJECT"], ["Tipo de campo → pedir enumeración u objeto, o pasar al siguiente paso", "Field type → ask for enumeration or object, or move on"]),
          a5: N(["Designar enumeración u objeto", "Designate enumeration or object"], ["completa el tipo del campo", "completes the field type"], ["Campo ENUM u OBJECT → campo con enumTypeId u objectTypeId", "ENUM or OBJECT field → field with enumTypeId or objectTypeId"], "EventFieldDefinition", ["enumTypeId / objectTypeId", "enumTypeId / objectTypeId"]),
          a6: N(["Parametrizar un enlace", "Parameterize a binding"], ["condición y cobertura de campos", "condition and field coverage"], ["Enlace → condición y copias que cubren los campos obligatorios", "Binding → condition and copies covering required fields"], "EventBinding / EventPayloadMapping", ["Edición en el formulario", "Editing in the form"]),
          a7: N(["Validar antes de persistir", "Validate before persisting"], ["cliente y servidor", "client and server"], ["Modelo en edición → diagnósticos locales y del servidor", "Model being edited → local and server diagnostics"], "eventDesignerValidation / EventModelChecker", ["Cliente y POST .../events/check", "Client and POST .../events/check"]),
          a8: N(["Persistir tipos y enlaces", "Persist types and bindings"], ["guardado versionado", "versioned save"], ["Modelo válido → nueva versión de definiciones y enlaces", "Valid model → new version of definitions and bindings"], "api.ts", ["POST|PUT .../events", "POST|PUT .../events"], ["Guardado con If-Match-Definitions e If-Match-Bindings.", "Save with If-Match-Definitions and If-Match-Bindings."]),
          r1: N(["Modelo de eventos resultante", "Resulting event model"], ["definiciones y enlaces estables", "stable definitions and bindings"], ["Formulario completado → modelo persistido", "Completed form → persisted model"])
        }
      },

      events_author_connect: {
        nf: T("Crear enlaces dibujando conexiones entre eventos", "Create bindings by drawing connections between events"),
        df: T("Autoría gráfica de derivaciones: sobre el diagrama de arquitectura se elige un evento de origen y se conecta con un destino propio. La conexión solo es válida si el origen existe, el destino es propio, no es un bucle y no duplica un par existente; entonces nace un enlace activo que se completa con condición y copias. Se guardan los modelos y la posición de las cajas.",
          "Graphical authoring of derivations: on the architecture diagram a source event is chosen and connected to a custom target. The connection is only valid if the source exists, the target is custom, it is not a loop and it does not duplicate an existing pair; an enabled binding is then born and completed with a condition and copies. The models and the box positions are saved."),
        nt: T("EventDesigner (arquitectura) → .../events/diagram", "EventDesigner (architecture) → .../events/diagram"),
        dt: T("La conexión se resuelve en el cliente con applyEventConnection (eventConnections.ts) y eventDesignerFactory.ts; el diagrama se lee y escribe con GET|PUT .../events/diagram con If-Match, a través de EventArchitectureDiagramController. Pruebas: eventConnections.test.ts, eventDesignerFactory.test.ts, EventArchitectureDiagramRepositoryTests.",
          "The connection is resolved on the client with applyEventConnection (eventConnections.ts) and eventDesignerFactory.ts; the diagram is read and written with GET|PUT .../events/diagram with If-Match, through EventArchitectureDiagramController. Tests: eventConnections.test.ts, eventDesignerFactory.test.ts, EventArchitectureDiagramRepositoryTests."),
        nodes: {
          a1: N(["Seleccionar el origen de la derivación", "Select the derivation source"], ["reservado o propio", "reserved or custom"], ["Diagrama de eventos → evento de origen elegido", "Event diagram → chosen source event"]),
          a2: N(["Proponer el destino propio", "Propose the custom target"], ["arrastre hacia un evento propio", "drag towards a custom event"], ["Origen elegido → intento de conexión con un destino", "Chosen source → connection attempt with a target"], "applyEventConnection", ["eventConnections.ts", "eventConnections.ts"]),
          d1: N(["¿Cumple la conexión las reglas de enlace?", "Does the connection satisfy the binding rules?"], ["origen, destino propio, sin bucle ni par repetido", "source, custom target, no loop or repeated pair"], ["Intento de conexión → aceptar o rechazar", "Connection attempt → accept or reject"]),
          a3: N(["Instanciar el enlace activo", "Instantiate the enabled binding"], ["sin condición, sin copias, REPORT", "no condition, no copies, REPORT"], ["Conexión válida → enlace activo con política de aviso", "Valid connection → enabled binding with report policy"], "eventDesignerFactory", ["Enlace por defecto", "Default binding"]),
          a4: N(["Completar la derivación", "Complete the derivation"], ["condición y cobertura de campos", "condition and field coverage"], ["Enlace → condición y copias de campos obligatorios", "Binding → condition and copies of required fields"]),
          a5: N(["Verificar el modelo de eventos", "Verify the event model"], ["reglas de enlace y tipos", "binding and type rules"], ["Modelo con el nuevo enlace → diagnósticos", "Model with the new binding → diagnostics"], "EventModelChecker", ["POST .../events/check", "POST .../events/check"]),
          a6: N(["Persistir modelos y geometría", "Persist models and geometry"], ["semántica y posición por separado", "semantics and position kept apart"], ["Modelo y diagrama → versiones guardadas", "Model and diagram → saved versions"], "EventArchitectureDiagramController", ["GET|PUT .../events/diagram", "GET|PUT .../events/diagram"]),
          r1: N(["Derivación persistida", "Persisted derivation"], ["enlace estable en el modelo", "stable binding in the model"], ["Conexión guardada → enlace disponible en ejecución", "Saved connection → binding available at run time"])
        }
      },

      events_author_flow: {
        nf: T("Diseñar flujos sobre el lienzo", "Design flows on the canvas"),
        df: T("Autoría gráfica de un flujo: se crea con identidad y disparadores, se incorporan pasos de la paleta y se configuran según su familia, se conectan con transiciones y se completan variables, plazos y compensaciones. La comprobación y una lista de preparación guían la corrección antes de guardar el flujo y su diagrama.",
          "Graphical authoring of a flow: it is created with an identity and triggers, steps from the palette are added and configured by family, connected with transitions, and variables, deadlines and compensations are completed. The check and a readiness list guide the correction before the flow and its diagram are saved."),
        nt: T("EventFlowDesigner → .../event-flows", "EventFlowDesigner → .../event-flows"),
        dt: T("El lienzo es EventFlowDesigner.tsx (paleta de 29 tipos, addNode) con EventFlowHighLevelProperties.tsx, eventFlowFactory.ts y EventFlowDesignerParts.tsx. La comprobación y el guardado usan POST .../event-flows/check y POST|PUT .../event-flows con If-Match, y el diagrama PUT .../event-flows/diagram.",
          "The canvas is EventFlowDesigner.tsx (29-kind palette, addNode) with EventFlowHighLevelProperties.tsx, eventFlowFactory.ts and EventFlowDesignerParts.tsx. The check and the save use POST .../event-flows/check and POST|PUT .../event-flows with If-Match, and the diagram uses PUT .../event-flows/diagram."),
        nodes: {
          a1: N(["Dar de alta el flujo", "Register the flow"], ["identidad y tipos disparadores", "identity and trigger types"], ["Modelo de flujos → flujo con identificador y disparadores", "Flow model → flow with identifier and triggers"], "eventFlowFactory", ["Alta en el cliente", "Client-side creation"]),
          a2: N(["Escoger la familia de paso", "Choose the step family"], ["29 tipos; por defecto de evento", "29 types; event step by default"], ["Paleta de tipos → paso nuevo en el flujo", "Type palette → new step in the flow"], "EventFlowDesigner.addNode", ["Paleta de 29 tipos", "29-kind palette"]),
          a3: N(["Parametrizar el paso por familia", "Parameterize the step by family"], ["evento, referencia, condición, política", "event, reference, condition, policy"], ["Paso nuevo → paso con su evento, referencia, condición y política", "New step → step with its event, reference, condition and policy"], "EventFlowHighLevelProperties", ["Propiedades agrupadas por familia", "Properties grouped by family"]),
          d1: N(["¿Está completo el recorrido?", "Is the walk complete?"], ["¿faltan pasos por incorporar?", "are steps still missing?"], ["Pasos actuales → incorporar otro paso o pasar a conectar", "Current steps → add another step or move on to connect"]),
          a4: N(["Unir los pasos con arcos", "Join the steps with arcs"], ["tipo y etiqueta de cada transición", "type and label of each transition"], ["Pasos → grafo con transiciones tipadas", "Steps → graph with typed transitions"], "FlowTransition", ["7 tipos de transición", "7 transition kinds"]),
          a5: N(["Completar variables y compensaciones", "Complete variables and compensations"], ["estado propio y deshacer", "own state and undo"], ["Grafo → variables y vínculos de compensación declarados", "Graph → declared variables and compensation links"], "FlowVariable / CompensationLink", ["Panel de estructura", "Structure panel"]),
          a6: N(["Verificar y repasar la preparación", "Verify and review readiness"], ["comprobación remota + lista local", "remote check + local list"], ["Flujo → diagnósticos del servidor y lista de preparación", "Flow → server diagnostics and readiness list"], "EventFlowChecker", ["POST .../event-flows/check", "POST .../event-flows/check"], ["La lista de preparación se muestra en el cliente (EventFlowDesignerParts.tsx).", "The readiness list is displayed on the client (EventFlowDesignerParts.tsx)."]),
          d2: N(["¿Subsisten errores bloqueantes?", "Do blocking errors remain?"], ["los errores impiden compilar", "errors prevent compiling"], ["Diagnósticos → corregir pasos o guardar", "Diagnostics → fix steps or save"]),
          a7: N(["Persistir flujo y geometría", "Persist flow and geometry"], ["versionado optimista", "optimistic versioning"], ["Flujo válido → versión del flujo y de su diagrama", "Valid flow → version of the flow and its diagram"], "flow/api.ts", ["POST|PUT .../event-flows", "POST|PUT .../event-flows"]),
          r1: N(["Flujo diseñado y guardado", "Designed and saved flow"], ["modelo apto para compilar", "model fit for compilation"], ["Diseño terminado → flujo disponible para el motor", "Finished design → flow available to the engine"])
        }
      },

      events_author_source: {
        nf: T("Editar el modelo de eventos como texto estructurado", "Edit the event model as structured text"),
        df: T("Autoría directa del modelo: se cambian tipos y enlaces sobre su representación textual, opcionalmente se contrasta en las vistas gráficas, se normaliza y se aplican las mismas reglas de comprobación que en los otros modos; con errores se vuelve a editar y, si es válido, se guarda.",
          "Direct authoring of the model: types and bindings are changed on their textual representation, optionally cross-checked in the graphical views, normalized, and the same checking rules as the other modes apply; with errors you edit again and, if valid, it is saved."),
        nt: T("EventDesigner (SOURCE) → .../events", "EventDesigner (SOURCE) → .../events"),
        dt: T("Vista SOURCE de EventDesigner.tsx con eventModelNormalization.ts; la validez se comprueba con EventModelChecker y el guardado usa los mismos endpoints .../events con If-Match. Pruebas: eventModelNormalization.test.ts.",
          "SOURCE view of EventDesigner.tsx with eventModelNormalization.ts; validity is checked with EventModelChecker and saving uses the same .../events endpoints with If-Match. Tests: eventModelNormalization.test.ts."),
        nodes: {
          a1: N(["Modificar el modelo en bruto", "Modify the raw model"], ["tipos y enlaces como texto", "types and bindings as text"], ["Modelo serializado → modelo serializado modificado", "Serialized model → modified serialized model"]),
          a2: N(["Contrastar en una vista gráfica", "Cross-check in a graphical view"], ["lectura alternativa del mismo modelo", "alternative reading of the same model"], ["Modelo editado → vista de formulario o de arquitectura", "Edited model → form or architecture view"]),
          a3: N(["Normalizar y verificar", "Normalize and verify"], ["forma canónica + reglas", "canonical form + rules"], ["Texto editado → modelo normalizado con diagnósticos", "Edited text → normalized model with diagnostics"], "eventModelNormalization / EventModelChecker", ["Cliente y servidor", "Client and server"]),
          d1: N(["¿Es válido el modelo editado?", "Is the edited model valid?"], ["sin errores bloqueantes", "no blocking errors"], ["Diagnósticos → guardar o corregir el texto", "Diagnostics → save or fix the text"]),
          a4: N(["Persistir el modelo", "Persist the model"], ["versionado optimista", "optimistic versioning"], ["Modelo válido → nueva versión guardada", "Valid model → new saved version"], "POST|PUT .../events", ["If-Match", "If-Match"]),
          r1: N(["Modelo persistido desde texto", "Model persisted from text"], ["mismo modelo que en otros modos", "same model as in the other modes"], ["Edición válida → modelo de eventos guardado", "Valid edit → saved event model"])
        }
      },

      events_author_run: {
        nf: T("Probar el motor de flujos", "Test the flow engine"),
        df: T("Operación de pruebas sobre el modelo de flujos guardado: se arranca o actualiza el motor, que compila solo si no hay errores, se inyecta un evento de prueba, se avanza el reloj para procesar reintentos, plazos y ausencias y se consultan estado y trazas. Detener el motor evita su reactivación automática.",
          "Testing operation over the saved flow model: the engine is started or refreshed, compiling only if there are no errors, a test event is injected, the clock is advanced to process retries, deadlines and absences and the status and traces are queried. Stopping the engine prevents its automatic reactivation."),
        nt: T("EventFlowRuntimePanel → EventFlowRuntimeController", "EventFlowRuntimePanel → EventFlowRuntimeController"),
        dt: T("EventFlowRuntimePanel.tsx llama a POST .../event-flows/runtime/{start|stop|refresh}, GET .../status, POST .../events, POST .../tick, GET .../traces y GET|DELETE .../state sobre EventFlowRuntimeController. El motor compila con EventFlowRuntimeService.compile y no se hallaron pruebas por nombre para el reloj (tick).",
          "EventFlowRuntimePanel.tsx calls POST .../event-flows/runtime/{start|stop|refresh}, GET .../status, POST .../events, POST .../tick, GET .../traces and GET|DELETE .../state on EventFlowRuntimeController. The engine compiles with EventFlowRuntimeService.compile and no tests were found by name for the clock (tick)."),
        nodes: {
          a1: N(["Desplegar el modelo compilado", "Deploy the compiled model"], ["compilación condicionada a cero errores", "compilation conditioned on zero errors"], ["Modelo de flujos guardado → despliegue activo de flujos compilados", "Saved flow model → active deployment of compiled flows"], "EventFlowRuntimeService.compile", ["POST .../runtime/{start|refresh}", "POST .../runtime/{start|refresh}"]),
          a2: N(["Inyectar un evento sintético", "Inject a synthetic event"], ["simula una llegada real", "simulates a real arrival"], ["Tipo y datos de prueba → ejecución de los flujos disparados", "Test type and data → execution of the triggered flows"], "POST .../events", ["Entrada de prueba al motor", "Test input to the engine"]),
          a3: N(["Hacer avanzar el tiempo lógico", "Advance logical time"], ["reintentos, plazos y ausencias", "retries, deadlines and absences"], ["Reloj del motor → continuaciones vencidas procesadas", "Engine clock → expired continuations processed"], "tick", ["POST .../tick", "POST .../tick"], ["Procesa reintentos, plazos y vigilancias de ausencia al avanzar el reloj del motor.", "Processes retries, deadlines and absence watches as the engine clock advances."]),
          a4: N(["Observar el resultado de la ejecución", "Observe the run outcome"], ["estado, trazas y estado interno", "status, traces and internal state"], ["Ejecuciones → estado del motor, trazas y estados guardados", "Runs → engine status, traces and stored states"], "GET .../status, .../traces, .../state", ["EventFlowRuntimeController", "EventFlowRuntimeController"]),
          a5: N(["Parar o limpiar el motor", "Stop or clear the engine"], ["detención explícita y vaciado", "explicit stop and clearing"], ["Orden del autor → motor detenido o estado borrado", "Author order → engine stopped or state cleared"], "POST .../runtime/stop; DELETE .../state", ["EventFlowRuntimeController", "EventFlowRuntimeController"]),
          r1: N(["Resultado de la prueba", "Test result"], ["evidencia de lo que hizo el flujo", "evidence of what the flow did"], ["Trazas consultadas → comprensión del comportamiento", "Queried traces → understanding of behaviour"])
        }
      }
    }
  });
})();
