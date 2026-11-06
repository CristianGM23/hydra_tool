(function () {
  "use strict";
  var HM = window.HM, T = HM.T;
  var FT = ["functional", "technical"], TECH = ["technical"];
  var NA = ["No aplica", "Not applicable"];

  // ---- facet builder: every variant gets the same eight answers --------------------------------
  // o = { r, w, c, p, s, e, k, i } each a [es, en] pair. r/w/e are shown to all audiences (plain language).
  function F(o) {
    function t(x) { return T(x[0], x[1]); }
    return [
      { n: T("Qué representa", "What it represents"), v: t(o.r) },
      { n: T("Cuándo usarlo", "When to use it"), v: t(o.w) },
      { n: T("Configuración", "Configuration"), v: t(o.c), a: FT },
      { n: T("Predecesores válidos", "Valid predecessors"), v: t(o.p), a: FT },
      { n: T("Sucesores válidos", "Valid successors"), v: t(o.s), a: FT },
      { n: T("Efecto / salida", "Effect / output"), v: t(o.e) },
      { n: T("Capacidades relacionadas", "Related capabilities"), v: t(o.k), a: FT },
      { n: T("Implementación", "Implementation"), v: t(o.i), a: TECH }
    ];
  }

  // ---- shared snippets ---------------------------------------------------------------------------
  var P_ANY = ["Cualquier paso que no sea un final. Debe tener al menos una transición entrante: sin ella el comprobador da ORPHAN_NODE y UNREACHABLE_NODE.",
    "Any step that is not an end. It must have at least one incoming transition: without it the checker reports ORPHAN_NODE and UNREACHABLE_NODE."];
  var S_NEXT = ["Cualquier paso, por una transición NEXT u ON_SUCCESS (se sigue la primera por orden; si no hay ninguna, la primera salida). Debe tener al menos una saliente (DEAD_END_NODE). Una transición ON_ERROR opcional recoge los fallos.",
    "Any step, via a NEXT or ON_SUCCESS transition (the first by order is followed; if none, the first exit). It must have at least one outgoing (DEAD_END_NODE). An optional ON_ERROR transition catches failures."];
  var S_NEXT_PLAIN = S_NEXT; // same text; facet is functional/technical only
  var CFG_COMMON = "Comunes (todos opcionales): nombre, reintento (RetryPolicy), límite de tiempo (TimeoutPolicy), compensable, activo (enabled=false lo convierte en paso directo).";
  var CFG_COMMON_EN = "Common (all optional): name, retry (RetryPolicy), time limit (TimeoutPolicy), compensable, enabled (enabled=false turns it into a pass-through).";
  var NODE_IMPL = " Reglas estructurales: EventFlowChecker.checkFlow; elección de salida: EventFlowRuntimeSupport.selectTransitions; índice compilado: EventFlowCompiler.compile.";
  var NODE_IMPL_EN = " Structural rules: EventFlowChecker.checkFlow; exit selection: EventFlowRuntimeSupport.selectTransitions; compiled index: EventFlowCompiler.compile.";
  var PASS_IMPL = "EventFlowRuntimeNodeExecutor.execute: rama del switch que devuelve NodeOutcome.pass (paso directo, sin lógica propia).";
  var PASS_IMPL_EN = "EventFlowRuntimeNodeExecutor.execute: switch branch returning NodeOutcome.pass (pass-through, no logic of its own).";
  var EXT_REL = ["Herramientas del editor (a través del runtime de herramientas de sesión), operaciones del metamodelo y validación; un fallo activa reintentos, ON_ERROR y compensación.", "Editor tools (through the session tool runtime), metamodel operations and validation; a failure triggers retries, ON_ERROR and compensation."];

  function N(r, w, c, p, s, e, k, i, o) {
    return F({
      r: r, w: w,
      c: [c[0] + " " + CFG_COMMON, c[1] + " " + CFG_COMMON_EN],
      p: p || P_ANY, s: s || S_NEXT, e: e, k: k || NA,
      i: [i[0] + (o === 0 ? "" : NODE_IMPL), i[1] + (o === 0 ? "" : NODE_IMPL_EN)]
    });
  }
  var PASS = [PASS_IMPL, PASS_IMPL_EN];
  function V(i, facets) { return { i: i, facets: facets }; }

  // ==================================================================================================
  // 1) FLOW STEP FAMILIES (29 kinds)
  // ==================================================================================================
  var entryExit = [
    V(0, N(["Punto de entrada simple del flujo.", "Simple entry point of the flow."],
      ["Para arrancar un flujo disparado por los eventos de su lista de disparadores, sin filtrar el tipo de evento en el propio paso.", "To start a flow triggered by the events in its trigger list, without filtering the event type on the step itself."],
      ["Obligatorios: identificador y tipo. Opcionales: nombre y las opciones comunes. No lleva evento.", "Required: identifier and kind. Optional: name and the common options. It carries no event."],
      ["Ninguno: es el origen. Cada flujo exige exactamente un START o un SOURCE (FLOW_START_COUNT); el comprobador no pide transiciones entrantes para él.", "None: it is the origin. Every flow requires exactly one START or one SOURCE (FLOW_START_COUNT); the checker does not require incoming transitions for it."],
      S_NEXT,
      ["Deja pasar la ejecución con el mismo evento hacia el siguiente paso.", "Lets the run through with the same event to the next step."],
      NA,
      ["EventFlowRuntimeNodeExecutor.execute (pass); el compilador elige como inicio el primer START o SOURCE en EventFlowCompiler.compile. " + "Regla FLOW_START_COUNT en EventFlowChecker.checkFlow.", "EventFlowRuntimeNodeExecutor.execute (pass); the compiler picks the first START or SOURCE as entry in EventFlowCompiler.compile. FLOW_START_COUNT rule in EventFlowChecker.checkFlow."], 0)),
    V(1, N(["Punto de entrada que solo acepta un tipo de evento concreto.", "Entry point that only accepts one specific event type."],
      ["Cuando el flujo debe nacer de un evento preciso: ese evento se registra además como disparador del flujo.", "When the flow must be born from one precise event: that event is also registered as a trigger of the flow."],
      ["Obligatorio: tipo de evento (eventTypeId; si falta, EVENT_TYPE_REQUIRED; si no existe, UNKNOWN_EVENT_TYPE). Opcionales: nombre y opciones comunes.", "Required: event type (eventTypeId; if missing, EVENT_TYPE_REQUIRED; if unknown, UNKNOWN_EVENT_TYPE). Optional: name and common options."],
      ["Ninguno: es el origen (cuenta como el único START/SOURCE del flujo).", "None: it is the origin (it counts as the single START/SOURCE of the flow)."],
      S_NEXT,
      ["Deja pasar si el evento es del tipo indicado; si no, la ejecución queda filtrada y se descarta.", "Lets through if the event is of the indicated type; otherwise the run is filtered and dropped."],
      ["Eventos: el tipo indicado puede ser reservado (editor, validación) o propio; también los emiten enlaces, otros flujos y gamificación.", "Events: the indicated type may be reserved (editor, validation) or custom; bindings, other flows and gamification also emit them."],
      ["EventFlowRuntimeNodeExecutor.execute: caso SOURCE/EVENT_STEP (compara eventTypeId; NodeOutcome.filtered si no coincide). EventFlowCompiler.compile añade el evento al índice de disparadores.", "EventFlowRuntimeNodeExecutor.execute: SOURCE/EVENT_STEP case (compares eventTypeId; NodeOutcome.filtered on mismatch). EventFlowCompiler.compile adds the event to the trigger index."])),
    V(2, N(["Cierre de una rama que acaba bien.", "Closing of a branch that ends well."],
      ["Al final de cada camino normal del flujo. Hace falta al menos un final (de éxito o de error) por flujo.", "At the end of every normal path of the flow. At least one end (success or error) is required per flow."],
      ["Obligatorios: identificador y tipo. Opcionales: nombre y opciones comunes. Sin evento ni referencia.", "Required: identifier and kind. Optional: name and common options. No event or reference."],
      P_ANY,
      ["Ninguno: es terminal y el comprobador no le exige salidas (no está sujeto a DEAD_END_NODE). El comprobador no lo prohíbe, pero un final se usa sin transiciones salientes.", "None: it is terminal and the checker does not require exits (exempt from DEAD_END_NODE). The checker does not forbid outgoing ones, but an end is used without them."],
      ["Termina la rama: una rama acaba cuando su testigo no tiene transición de salida. No cambia el resultado por sí mismo.", "Ends the branch: a branch ends when its token has no outgoing transition. It does not change the result by itself."],
      NA,
      ["EventFlowRuntimeNodeExecutor.execute (pass); terminal para EventFlowChecker.isTerminal (cuenta para FLOW_END_REQUIRED).", "EventFlowRuntimeNodeExecutor.execute (pass); terminal per EventFlowChecker.isTerminal (counts toward FLOW_END_REQUIRED)."])),
    V(3, N(["Cierre de una rama marcada como error.", "Closing of a branch marked as an error."],
      ["Para dar nombre visible a un camino de fallo en el diagrama (por ejemplo, tras un tratamiento de error).", "To give a visible name to a failure path on the diagram (for example after an error handler)."],
      ["Obligatorios: identificador y tipo. Opcionales: nombre y opciones comunes. Sin evento ni referencia.", "Required: identifier and kind. Optional: name and common options. No event or reference."],
      P_ANY,
      ["Ninguno: es terminal (exento de DEAD_END_NODE).", "None: it is terminal (exempt from DEAD_END_NODE)."],
      ["Aviso verificado: si se llega por una transición normal, el resultado sigue siendo «completado»; no provoca fallo ni compensación por sí mismo.", "Verified caveat: if reached through a normal transition the result is still “completed”; it triggers neither failure nor compensation by itself."],
      NA,
      ["EventFlowRuntimeNodeExecutor.execute (pass, hallazgo F5); terminal en EventFlowChecker.isTerminal.", "EventFlowRuntimeNodeExecutor.execute (pass, finding F5); terminal in EventFlowChecker.isTerminal."]))
  ];

  var eventSteps = [
    V(0, N(["Paso que exige que el evento en curso sea de un tipo concreto.", "Step that requires the event in progress to be of a given type."],
      ["Cuando, a mitad de flujo, solo debe continuar un tipo de evento (por ejemplo tras una unión de ramas).", "When, midway through a flow, only one event type must continue (for example after a merge of branches)."],
      ["Obligatorio: tipo de evento (eventTypeId; EVENT_TYPE_REQUIRED / UNKNOWN_EVENT_TYPE).", "Required: event type (eventTypeId; EVENT_TYPE_REQUIRED / UNKNOWN_EVENT_TYPE)."],
      P_ANY, S_NEXT,
      ["Deja pasar si el tipo coincide; si no, la ejecución queda filtrada (estado «filtrado»).", "Lets through if the type matches; otherwise the run is filtered (status “filtered”)."],
      ["Eventos (tipos reservados y propios).", "Events (reserved and custom types)."],
      ["EventFlowRuntimeNodeExecutor.execute: caso SOURCE/EVENT_STEP (NodeOutcome.pass o NodeOutcome.filtered).", "EventFlowRuntimeNodeExecutor.execute: SOURCE/EVENT_STEP case (NodeOutcome.pass or NodeOutcome.filtered)."])),
    V(1, N(["Pausa del flujo hasta que llegue otro evento (o venza un plazo).", "Pause of the flow until another event arrives (or a deadline passes)."],
      ["Para esperar una respuesta o confirmación externa antes de seguir, con la opción de actuar si no llega a tiempo.", "To wait for an outside reply or confirmation before continuing, with the option to act if it does not arrive in time."],
      ["Obligatorio: tipo de evento esperado. Opcionales: límite de tiempo (timeoutMillis en TimeoutPolicy), clave de agrupación (groupByPath, por defecto correlationId) y las opciones comunes.", "Required: awaited event type. Optional: time limit (timeoutMillis in TimeoutPolicy), grouping key (groupByPath, default correlationId) and the common options."],
      P_ANY,
      ["Por NEXT/ON_SUCCESS cuando llega el evento esperado; por una transición TIMEOUT (o ON_ERROR como alternativa) si vence el plazo. Debe tener al menos una saliente (DEAD_END_NODE).", "Via NEXT/ON_SUCCESS when the awaited event arrives; via a TIMEOUT transition (ON_ERROR as fallback) if the deadline passes. At least one outgoing is required (DEAD_END_NODE)."],
      ["Si el evento en curso ya es el esperado, continúa de inmediato; si no, aparca la ejecución (estado «en espera») y la reanuda al llegar el evento o al avanzar el reloj del motor tras el plazo.", "If the current event is already the awaited one it continues at once; otherwise it parks the run (status “waiting”) and resumes when the event arrives or when the engine clock advances past the deadline."],
      ["Eventos (cualquier productor) y la política de tiempo del paso.", "Events (any producer) and the step's time policy."],
      ["EventFlowRuntimeNodeExecutor.waitForEvent (crea FlowWaitingContinuation); reanudación y vencimiento en EventFlowRuntimeService (resume de esperas y tick con salida TIMEOUT).", "EventFlowRuntimeNodeExecutor.waitForEvent (creates FlowWaitingContinuation); resume and expiry in EventFlowRuntimeService (wait resumption and tick with TIMEOUT exit)."]))
  ];

  var external = [
    V(0, N(["Paso que lanza una validación del modelo y espera el resultado.", "Step that launches a validation of the model and waits for the result."],
      ["Cuando una reacción debe comprobar el modelo (por ejemplo tras guardar) y decidir según haya errores o no.", "When a reaction must check the model (for example after saving) and decide depending on whether there are errors."],
      ["Obligatorio: referencia «validation» o «validation:MODO» (el modo por defecto es MANUAL). Con otra referencia no hay ejecutor y el paso falla salvo que allowNoop sea verdadero.", "Required: reference “validation” or “validation:MODE” (default mode is MANUAL). With another reference there is no executor and the step fails unless allowNoop is true."],
      P_ANY,
      ["NEXT/ON_SUCCESS si la validación no tiene errores; ON_ERROR (opcional) si los tiene o falla. Sin ON_ERROR, entra la compensación.", "NEXT/ON_SUCCESS if validation has no errors; ON_ERROR (optional) if it has or fails. Without ON_ERROR, compensation kicks in."],
      ["Ejecuta la validación y continúa si no hay diagnósticos de error; actualiza los datos de ejecución con el resultado.", "Runs the validation and continues if there are no error diagnostics; updates the run data with the result."],
      ["Validación (reglas y modos de validación).", "Validation (validation rules and modes)."],
      ["ValidationFlowExternalStepExecutor.supports/execute → EditorSessionToolRuntime.execute con ValidateEditorTool; despacho en EventFlowRuntimeNodeExecutor.external.", "ValidationFlowExternalStepExecutor.supports/execute → EditorSessionToolRuntime.execute with ValidateEditorTool; dispatch in EventFlowRuntimeNodeExecutor.external."])),
    V(1, N(["Paso que ejecuta una acción del editor.", "Step that runs an editor action."],
      ["Cuando la reacción debe modificar o consultar el modelo mediante una herramienta de acción ya definida.", "When the reaction must change or query the model through an already defined action tool."],
      ["Obligatorio: referencia a la acción (referenceId no vacío; si está vacío no hay ejecutor y falla salvo allowNoop). Opcional: allowNoop.", "Required: reference to the action (non-blank referenceId; if blank there is no executor and it fails unless allowNoop). Optional: allowNoop."],
      P_ANY,
      ["NEXT/ON_SUCCESS si la acción termina bien; ON_ERROR (opcional) si falla. Marcar el paso como compensable permite deshacerlo tras un fallo posterior.", "NEXT/ON_SUCCESS if the action succeeds; ON_ERROR (optional) if it fails. Marking the step compensable lets it be undone after a later failure."],
      ["Ejecuta la acción sobre el modelo del evento y devuelve su resultado y mensajes.", "Runs the action on the event's model and returns its result and messages."],
      ["Edición de modelos (herramientas de acción del editor).", "Model editor (editor action tools)."],
      ["ActionFlowExternalStepExecutor.supports/execute → EditorSessionToolRuntime.execute con ActionEditorTool; despacho en EventFlowRuntimeNodeExecutor.external.", "ActionFlowExternalStepExecutor.supports/execute → EditorSessionToolRuntime.execute with ActionEditorTool; dispatch in EventFlowRuntimeNodeExecutor.external."])),
    V(2, N(["Paso que ejecuta una operación definida en el metamodelo.", "Step that runs an operation defined in the metamodel."],
      ["Para reutilizar lógica ya modelada como operación (consultas o comandos) dentro de una reacción.", "To reuse logic already modelled as an operation (queries or commands) inside a reaction."],
      ["Obligatorio: referencia a la operación (referenceId no vacío). Opcional: allowNoop.", "Required: reference to the operation (non-blank referenceId). Optional: allowNoop."],
      P_ANY,
      ["NEXT/ON_SUCCESS si la operación termina; ON_ERROR (opcional) si falla.", "NEXT/ON_SUCCESS if the operation finishes; ON_ERROR (optional) if it fails."],
      ["Ejecuta la operación y añade su resultado a los datos de la ejecución.", "Runs the operation and adds its result to the run data."],
      ["Metamodelo (operaciones) y herramientas del editor.", "Metamodel (operations) and editor tools."],
      ["OperationFlowExternalStepExecutor.supports/execute → EditorSessionToolRuntime.execute con OperationEditorTool; despacho en EventFlowRuntimeNodeExecutor.external.", "OperationFlowExternalStepExecutor.supports/execute → EditorSessionToolRuntime.execute with OperationEditorTool; dispatch in EventFlowRuntimeNodeExecutor.external."])),
    V(3, N(["Paso pensado para una intervención humana.", "Step meant for a human intervention."],
      ["Solo como marcador en el diagrama: hoy ningún ejecutor lo atiende.", "Only as a marker on the diagram: no executor handles it today."],
      ["Opcional: allowNoop=true para aceptarlo como no-operación; sin ello el paso falla (no hay ejecutor registrado para MANUAL_STEP; verificado por búsqueda de las implementaciones de FlowExternalStepExecutor: validación, acción y operación).", "Optional: allowNoop=true to accept it as a no-op; without it the step fails (no executor is registered for MANUAL_STEP; verified by searching the FlowExternalStepExecutor implementations: validation, action and operation)."],
      P_ANY,
      ["NEXT/ON_SUCCESS con allowNoop; ON_ERROR (opcional) cuando falla por falta de ejecutor.", "NEXT/ON_SUCCESS with allowNoop; ON_ERROR (optional) when it fails for lack of executor."],
      ["Con allowNoop continúa sin hacer nada; sin él, el paso falla con el mensaje de que ningún ejecutor lo soporta.", "With allowNoop it continues doing nothing; without it the step fails with a message that no executor supports it."],
      NA,
      ["EventFlowRuntimeNodeExecutor.external: sin ejecutor que lo soporte devuelve NodeOutcome.failure salvo allowNoop.", "EventFlowRuntimeNodeExecutor.external: with no supporting executor it returns NodeOutcome.failure unless allowNoop."]))
  ];

  var routing = [
    V(0, N(["Bifurcación según una condición.", "Fork according to a condition."],
      ["Cuando el flujo debe comportarse distinto según un dato del evento.", "When the flow must behave differently depending on a datum of the event."],
      ["Necesita en configuración: ruta del dato (conditionPath o path), valor esperado (expectedValue o equals) y operador (EQUALS por defecto; NOT_EQUALS, EXISTS, NOT_EXISTS, CONTAINS, GREATER_THAN, LESS_THAN). Sin ruta, la condición es siempre verdadera. El runtime lee solo esta configuración; conditionExpressionId, si existe, solo lo valida el comprobador.", "Needs in configuration: data path (conditionPath or path), expected value (expectedValue or equals) and operator (EQUALS default; NOT_EQUALS, EXISTS, NOT_EXISTS, CONTAINS, GREATER_THAN, LESS_THAN). Without a path the condition is always true. The runtime reads only this configuration; conditionExpressionId, if present, is only validated by the checker."],
      P_ANY,
      ["Exactamente dos tipos de salida obligatorios: al menos una transición TRUE y al menos una FALSE (DECISION_BRANCHES). Cada una puede ir a cualquier paso. Si la rama elegida no existe, la rama termina.", "Two mandatory kinds of exit: at least one TRUE and at least one FALSE transition (DECISION_BRANCHES). Each may go to any step. If the chosen branch does not exist, the branch ends."],
      ["Sigue solo por la salida «verdadero» o «falso» según la condición; no modifica el evento.", "Follows only the “true” or “false” exit according to the condition; it does not modify the event."],
      NA,
      ["EventFlowRuntimeNodeExecutor.execute (pass); la elección ocurre en EventFlowRuntimeSupport.selectTransitions con EventValueResolver.matches. Regla DECISION_BRANCHES en EventFlowChecker.checkFlow.", "EventFlowRuntimeNodeExecutor.execute (pass); the choice happens in EventFlowRuntimeSupport.selectTransitions with EventValueResolver.matches. DECISION_BRANCHES rule in EventFlowChecker.checkFlow."], 0)),
    V(1, N(["Encaminamiento: se comporta igual que la decisión.", "Routing: behaves the same as the decision."],
      ["Como alternativa de nombre a la decisión cuando se quiere resaltar el encaminamiento en el diagrama; no hay diferencia de comportamiento verificada.", "As an alternative name to the decision when routing should be highlighted on the diagram; no behavioural difference was verified."],
      ["Igual que la decisión: ruta, valor esperado y operador en la configuración.", "Same as the decision: path, expected value and operator in the configuration."],
      P_ANY,
      ["Igual que la decisión: al menos una transición TRUE y una FALSE (DECISION_BRANCHES).", "Same as the decision: at least one TRUE and one FALSE transition (DECISION_BRANCHES)."],
      ["Sigue por la salida «verdadero» o «falso»; no modifica el evento.", "Follows the “true” or “false” exit; it does not modify the event."],
      NA,
      ["Mismo código que DECISION: caso compartido en EventFlowRuntimeNodeExecutor.execute y en EventFlowRuntimeSupport.selectTransitions / EventFlowChecker.checkFlow.", "Same code as DECISION: shared case in EventFlowRuntimeNodeExecutor.execute and in EventFlowRuntimeSupport.selectTransitions / EventFlowChecker.checkFlow."], 0)),
    V(2, N(["Punto donde varios caminos se reúnen sin esperar.", "Point where several paths meet without waiting."],
      ["Para volver a un único camino tras una bifurcación cuando basta con que llegue cualquiera de las ramas.", "To return to a single path after a fork when it is enough that any of the branches arrives."],
      ["Sin configuración propia; solo las opciones comunes.", "No configuration of its own; only the common options."],
      ["Cualquier paso, uno o varios. A diferencia de la unión sincronizada, no exige un mínimo de entradas (solo ORPHAN_NODE si no hay ninguna).", "Any step, one or several. Unlike the synchronized join it does not require a minimum number of inputs (only ORPHAN_NODE if there are none)."],
      S_NEXT,
      ["Deja pasar cada llegada hacia el siguiente paso: si llegan dos ramas, el siguiente se ejecuta dos veces (no sincroniza).", "Lets each arrival through to the next step: if two branches arrive, the next step runs twice (it does not synchronize)."],
      NA,
      PASS, 0))
  ];

  var parallel = [
    V(0, N(["División de la ejecución en varias ramas.", "Split of the run into several branches."],
      ["Cuando varias reacciones deben salir del mismo evento (por ejemplo avisar y registrar a la vez).", "When several reactions must start from the same event (for example notify and record at once)."],
      ["Sin configuración propia. Las ramas son las transiciones salientes.", "No configuration of its own. The branches are the outgoing transitions."],
      P_ANY,
      ["Se siguen todas las salidas NEXT u ON_SUCCESS (en el orden indicado), una detrás de otra, no a la vez. El comprobador no exige un mínimo de ramas (no verificado que se pida ≥2).", "All NEXT or ON_SUCCESS exits are followed (in the given order), one after the other, not concurrently. The checker does not require a minimum number of branches (a ≥2 requirement was not verified)."],
      ["Genera una rama por salida; el motor las turna en una cola, sin concurrencia real.", "Creates one branch per exit; the engine takes turns on a queue, with no real concurrency."],
      NA,
      ["EventFlowRuntimeSupport.selectTransitions: PARALLEL_SPLIT devuelve todas las NEXT/ON_SUCCESS; la cola se recorre en EventFlowRuntimeService.executeFlow.", "EventFlowRuntimeSupport.selectTransitions: PARALLEL_SPLIT returns all NEXT/ON_SUCCESS; the queue is walked in EventFlowRuntimeService.executeFlow."], 0)),
    V(1, N(["Punto donde varias ramas se sincronizan.", "Point where several branches synchronize."],
      ["Cuando el flujo solo debe continuar después de que hayan llegado todos los eventos esperados de un mismo grupo.", "When the flow must continue only after all awaited events of the same group have arrived."],
      ["Necesita en configuración la lista eventTypes (los tipos que deben llegar; si está vacía, deja pasar sin esperar). Opcional: groupByPath (por defecto correlationId).", "Needs in configuration the eventTypes list (the types that must arrive; if empty it lets through without waiting). Optional: groupByPath (default correlationId)."],
      ["Al menos dos transiciones entrantes (JOIN_INPUT_COUNT); cada una puede venir de cualquier paso.", "At least two incoming transitions (JOIN_INPUT_COUNT); each may come from any step."],
      S_NEXT,
      ["Mientras falte algún tipo, la rama queda en espera; al llegar todos, continúa una sola vez con los datos de todos (tipos y contenidos unidos).", "While any type is missing the branch stays waiting; when all have arrived it continues once with the data of all (joined types and contents)."],
      NA,
      ["EventFlowRuntimeNodeExecutor.join (estado state.joins por clave de grupo); regla JOIN_INPUT_COUNT en EventFlowChecker.checkFlow.", "EventFlowRuntimeNodeExecutor.join (state.joins keyed by group); JOIN_INPUT_COUNT rule in EventFlowChecker.checkFlow."]))
  ];

  var timing = [
    V(0, N(["Marcador gráfico de tiempo agotado.", "Graphical marker for a timeout."],
      ["Solo como apoyo visual; el límite real se define en la política de tiempo del paso que espera.", "Only as visual support; the real limit is defined in the time policy of the step that waits."],
      ["Sin configuración propia: no tiene efecto por sí mismo.", "No configuration of its own: it has no effect by itself."],
      ["Normalmente un paso por una transición TIMEOUT; cualquier paso es válido para el comprobador.", "Normally a step via a TIMEOUT transition; any step is valid for the checker."],
      S_NEXT,
      ["Deja pasar la ejecución sin cambios.", "Lets the run through unchanged."],
      NA, PASS, 0)),
    V(1, N(["Marcador gráfico de reintento.", "Graphical marker for a retry."],
      ["Solo como apoyo visual; los reintentos reales se definen en el paso que puede fallar.", "Only as visual support; the real retries are defined on the step that may fail."],
      ["Sin configuración propia: no tiene efecto por sí mismo.", "No configuration of its own: it has no effect by itself."],
      P_ANY, S_NEXT,
      ["Deja pasar la ejecución sin cambios.", "Lets the run through unchanged."],
      NA, PASS, 0))
  ];

  var transform = [
    V(0, N(["Filtro: solo sigue lo que cumple una condición.", "Filter: only what meets a condition continues."],
      ["Cuando solo algunos eventos deben seguir adelante (por ejemplo, solo los de cierto elemento).", "When only some events must go on (for example only those about a given element)."],
      ["Condición en configuración: ruta (conditionPath o path), valor esperado (expectedValue o equals) y operador (EQUALS por defecto). Sin ruta deja pasar todo.", "Condition in configuration: path (conditionPath or path), expected value (expectedValue or equals) and operator (EQUALS default). Without a path everything passes."],
      P_ANY, S_NEXT,
      ["Si se cumple, continúa con el mismo evento; si no, la ejecución queda filtrada y se descarta.", "If met, continues with the same event; otherwise the run is filtered and dropped."],
      NA,
      ["EventFlowRuntimeNodeExecutor.execute: FILTER → EventValueResolver.matches (NodeOutcome.pass / NodeOutcome.filtered).", "EventFlowRuntimeNodeExecutor.execute: FILTER → EventValueResolver.matches (NodeOutcome.pass / NodeOutcome.filtered)."])),
    V(1, N(["Transformación: reasigna o calcula datos del evento.", "Transformation: reassigns or computes data of the event."],
      ["Para preparar los datos antes de un paso posterior (por ejemplo, copiar un dato a otra ruta).", "To prepare data before a later step (for example copying a datum to another path)."],
      ["Opcional: lista mappings (destino → ruta de origen, o {sourcePath}, o {fixedValue}, o valor literal). Sin lista, no cambia nada.", "Optional: mappings list (target → source path, or {sourcePath}, or {fixedValue}, or a literal value). Without a list nothing changes."],
      P_ANY, S_NEXT,
      ["Continúa con una copia del evento cuyos datos incluyen las asignaciones.", "Continues with a copy of the event whose data include the assignments."],
      NA,
      ["EventFlowRuntimeNodeExecutor.execute → EventFlowRuntimeSupport.mapEvent (putPath sobre el payload).", "EventFlowRuntimeNodeExecutor.execute → EventFlowRuntimeSupport.mapEvent (putPath over the payload)."])),
    V(2, N(["Deduplicación: descarta eventos repetidos.", "Deduplication: discards repeated events."],
      ["Cuando el mismo hecho puede llegar varias veces y solo debe procesarse una.", "When the same fact may arrive several times and must be processed only once."],
      ["Opcionales: keyPath (por defecto id) y ttlMillis (por defecto 0: la clave no caduca). Recuerda como máximo 4096 claves por paso.", "Optional: keyPath (default id) and ttlMillis (default 0: the key never expires). It remembers at most 4096 keys per step."],
      P_ANY, S_NEXT,
      ["La primera vez deja pasar; si la clave ya se vio, la ejecución queda filtrada.", "The first time it lets through; if the key was already seen, the run is filtered."],
      NA,
      ["EventFlowRuntimeNodeExecutor.deduplicate (state.deduplication; EventFlowRuntimeSupport.trim a 4096).", "EventFlowRuntimeNodeExecutor.deduplicate (state.deduplication; EventFlowRuntimeSupport.trim to 4096)."])),
    V(3, N(["Emisión de un evento derivado.", "Emission of a derived event."],
      ["Cuando el flujo debe anunciar un hecho nuevo para que otros enlaces, otros flujos o la gamificación reaccionen.", "When the flow must announce a new fact so other bindings, other flows or gamification react."],
      ["Obligatorio: tipo de evento a emitir (eventTypeId: EVENT_TYPE_REQUIRED si falta, UNKNOWN_EVENT_TYPE si no existe). El runtime acepta como alternativa configuration.targetEventTypeId, pero el comprobador exige antes eventTypeId.", "Required: event type to emit (eventTypeId: EVENT_TYPE_REQUIRED if missing, UNKNOWN_EVENT_TYPE if unknown). The runtime accepts configuration.targetEventTypeId as an alternative, but the checker demands eventTypeId first."],
      P_ANY, S_NEXT,
      ["Publica un evento derivado (con los datos del original y la referencia al flujo y paso de origen) al terminar el procesado, y la rama continúa. El encadenamiento se corta a 32 niveles.", "Publishes a derived event (with the original's data and a reference to the source flow and step) when processing ends, and the branch continues. Chaining stops at 32 levels."],
      ["Gamificación, enlaces y otros flujos, que escuchan los mismos eventos del canal.", "Gamification, bindings and other flows, which listen to the same events on the channel."],
      ["EventFlowRuntimeNodeExecutor.emit → EventFlowRuntimeEventFactory.derived; publicación en EventFlowRuntimeService al cerrar el procesado.", "EventFlowRuntimeNodeExecutor.emit → EventFlowRuntimeEventFactory.derived; publication in EventFlowRuntimeService when processing closes."]))
  ];

  var pattern = [
    V(0, N(["Ventana: acumula varios eventos antes de seguir.", "Window: accumulates several events before continuing."],
      ["Cuando hay que esperar a N eventos seguidos del mismo grupo (por ejemplo, tres errores seguidos).", "When N consecutive events of the same group must be awaited (for example three errors in a row)."],
      ["Opcionales: size (por defecto 2, mínimo 1), sliding (por defecto false) y groupByPath (por defecto correlationId).", "Optional: size (default 2, minimum 1), sliding (default false) and groupByPath (default correlationId)."],
      P_ANY, S_NEXT,
      ["Hasta completar la ventana la rama queda en espera; al completarse continúa con el tamaño y el contenido de los eventos acumulados; si no es deslizante, se vacía.", "Until the window is full the branch stays waiting; when full it continues with the size and contents of the accumulated events; if not sliding, it is emptied."],
      NA,
      ["EventFlowRuntimeNodeExecutor.window (state.windows por clave de grupo).", "EventFlowRuntimeNodeExecutor.window (state.windows keyed by group)."])),
    V(1, N(["Agregación: cuenta (y suma) los eventos que llegan.", "Aggregation: counts (and sums) the events that arrive."],
      ["Para llevar un contador o un total acumulado por grupo y usarlo en pasos posteriores (por ejemplo, en una decisión).", "To keep a counter or running total per group and use it in later steps (for example in a decision)."],
      ["Opcionales: sumPath (ruta del valor a sumar) y groupByPath (por defecto correlationId).", "Optional: sumPath (path of the value to add up) and groupByPath (default correlationId)."],
      P_ANY, S_NEXT,
      ["No espera: continúa siempre, añadiendo al evento el recuento (y la suma si hay ruta).", "Does not wait: always continues, adding the count (and the sum if there is a path) to the event."],
      NA,
      ["EventFlowRuntimeNodeExecutor.aggregate (state.aggregates y state.sums).", "EventFlowRuntimeNodeExecutor.aggregate (state.aggregates and state.sums)."])),
    V(2, N(["Secuencia: exige un orden concreto de tipos de evento.", "Sequence: requires a specific order of event types."],
      ["Cuando importa el orden (A, luego B, luego C) para considerar que ocurrió la situación.", "When order matters (A, then B, then C) for the situation to count as having happened."],
      ["Obligatorio: lista eventTypes en orden (si está vacía, el paso falla). Opcional: groupByPath.", "Required: eventTypes list in order (if empty the step fails). Optional: groupByPath."],
      P_ANY, S_NEXT,
      ["La rama queda en espera con el progreso; un evento fuera de orden reinicia la secuencia (o la reinicia en el primero si coincide). Al completarla, continúa.", "The branch stays waiting with the progress; an out-of-order event resets the sequence (or restarts at the first if it matches). When complete, it continues."],
      NA,
      ["EventFlowRuntimeNodeExecutor.sequence (state.sequences por clave de grupo).", "EventFlowRuntimeNodeExecutor.sequence (state.sequences keyed by group)."])),
    V(3, N(["Patrón: exige que lleguen todos los tipos indicados, en cualquier orden.", "Pattern: requires all indicated types to arrive, in any order."],
      ["Cuando hace falta que hayan ocurrido varios hechos distintos, sin importar el orden.", "When several distinct facts must have happened, regardless of order."],
      ["Obligatorio: lista eventTypes (si está vacía, el paso falla). Opcional: groupByPath.", "Required: eventTypes list (if empty the step fails). Optional: groupByPath."],
      P_ANY, S_NEXT,
      ["Queda en espera hasta ver todos los tipos del grupo; entonces continúa y reinicia el estado.", "Stays waiting until all types of the group are seen; then continues and resets its state."],
      NA,
      ["EventFlowRuntimeNodeExecutor.pattern (state.patterns).", "EventFlowRuntimeNodeExecutor.pattern (state.patterns)."])),
    V(4, N(["Ausencia: detecta que un evento esperado no llegó a tiempo.", "Absence: detects that an expected event did not arrive in time."],
      ["Para reaccionar a lo que NO ocurre (por ejemplo, un recordatorio si no hay respuesta en un plazo).", "To react to what does NOT happen (for example a reminder if there is no reply within a deadline)."],
      ["Obligatorios: tipo de evento a emitir en el paso (eventTypeId; EVENT_TYPE_REQUIRED si falta) y watchEventTypeId en configuración (si falta, el paso falla en ejecución). Opcionales: timeoutMillis (si no, el límite de tiempo del paso; mínimo 1) y groupByPath (por defecto correlationId).", "Required: event type to emit on the step (eventTypeId; EVENT_TYPE_REQUIRED if missing) and watchEventTypeId in configuration (if missing the step fails at run time). Optional: timeoutMillis (else the step's time limit; minimum 1) and groupByPath (default correlationId)."],
      P_ANY, S_NEXT,
      ["Programa una vigilancia y la rama queda en espera; si el evento vigilado con la misma clave llega antes del plazo se cancela, y si no, al avanzar el reloj del motor se emite el evento de aviso. Qué rama sigue tras esa emisión: no verificado.", "Schedules a watch and the branch stays waiting; if the watched event with the same key arrives before the deadline it is cancelled, otherwise when the engine clock advances the warning event is emitted. Which branch continues after that emission: not verified."],
      ["Eventos de aviso consumibles por enlaces, otros flujos y gamificación.", "Warning events consumable by bindings, other flows and gamification."],
      ["EventFlowRuntimeNodeExecutor.absence (FlowAbsenceContinuation en state.absenceWatches); evaluación en EventFlowRuntimeService (tick de ausencia) y cancelación al llegar el evento vigilado. Sin pruebas por nombre.", "EventFlowRuntimeNodeExecutor.absence (FlowAbsenceContinuation in state.absenceWatches); evaluation in EventFlowRuntimeService (absence tick) and cancellation when the watched event arrives. No tests found by name."]))
  ];

  var recovery = [
    V(0, N(["Alternativa: camino de reserva para cuando algo falla.", "Fallback: reserve path for when something fails."],
      ["Para dar lugar en el diagrama al trabajo alternativo, o como destino de un enlace de compensación.", "To give a place on the diagram to the alternative work, or as the target of a compensation link."],
      ["Sin configuración propia. Referencia opcional a lo que atiende el caso.", "No configuration of its own. Optional reference to what handles the case."],
      ["Cualquier paso (normalmente por una transición ON_ERROR). Aunque solo se use como destino de compensación, el comprobador exige que tenga una transición entrante (ORPHAN_NODE / UNREACHABLE_NODE).", "Any step (normally via an ON_ERROR transition). Even if it is only used as a compensation target, the checker requires an incoming transition (ORPHAN_NODE / UNREACHABLE_NODE)."],
      S_NEXT,
      ["Deja pasar. Es un destino válido de enlace de compensación; por sí mismo no ejecuta nada.", "Lets through. It is a valid compensation link target; by itself it runs nothing."],
      NA,
      ["EventFlowRuntimeNodeExecutor.execute (pass); destino válido en EventFlowChecker.checkFlow (INVALID_COMPENSATION_TARGET si no).", "EventFlowRuntimeNodeExecutor.execute (pass); valid target in EventFlowChecker.checkFlow (INVALID_COMPENSATION_TARGET otherwise)."])),
    V(1, N(["Paso que expresa el trabajo de deshacer.", "Step that expresses the work of undoing."],
      ["Cuando un paso ya ejecutado tiene un efecto que hay que revertir si el flujo no llega a buen puerto.", "When an already executed step has an effect that must be reverted if the flow does not succeed."],
      ["Sin configuración propia. Si no está marcado como compensable, el comprobador lo anota como información (COMPENSATION_NODE); no es un error.", "No configuration of its own. If not marked compensable the checker notes it as information (COMPENSATION_NODE); it is not an error."],
      ["Cualquier paso; como en la alternativa, necesita al menos una transición entrante.", "Any step; as with the fallback, it needs at least one incoming transition."],
      S_NEXT,
      ["Deja pasar. Es destino válido de un enlace de compensación; la compensación real la dispara el motor ante un fallo sin salida.", "Lets through. A valid compensation link target; real compensation is triggered by the engine on a failure with no exit."],
      NA,
      ["EventFlowRuntimeNodeExecutor.execute (pass); INFO COMPENSATION_NODE en EventFlowChecker.checkFlow; compensación en orden inverso en EventFlowRuntimeService.", "EventFlowRuntimeNodeExecutor.execute (pass); INFO COMPENSATION_NODE in EventFlowChecker.checkFlow; reverse-order compensation in EventFlowRuntimeService."])),
    V(2, N(["Tratamiento de error: lugar donde se atiende un fallo.", "Error handler: place where a failure is handled."],
      ["Como destino de la transición de error de un paso que puede fallar, para agrupar la respuesta al fallo.", "As the target of the error transition of a step that may fail, to group the response to the failure."],
      ["Sin configuración propia. Referencia opcional.", "No configuration of its own. Optional reference."],
      ["Normalmente un paso que puede fallar, por una transición ON_ERROR (o TIMEOUT); el comprobador acepta cualquier paso con entrada.", "Normally a step that may fail, via an ON_ERROR (or TIMEOUT) transition; the checker accepts any step with an input."],
      ["Cualquier paso, normalmente un final de error o una alternativa; debe tener al menos una saliente (DEAD_END_NODE).", "Any step, normally an error end or a fallback; at least one outgoing is required (DEAD_END_NODE)."],
      ["Deja pasar: marca dónde se atiende el error; no ejecuta nada propio.", "Lets through: marks where the error is handled; it runs nothing of its own."],
      NA, PASS, 0))
  ];

  // ==================================================================================================
  // 2) TRANSITION KINDS (7)
  // ==================================================================================================
  function TR(r, w, c, p, s, e, i) { return F({ r: r, w: w, c: c, p: p, s: s, e: e, k: NA, i: i }); }
  var TC = ["Obligatorios: identificador, paso de origen y paso de destino (UNKNOWN_TRANSITION_SOURCE/TARGET si no existen). Opcionales: tipo (NEXT por defecto), orden, condición (conditionExpressionId, que el comprobador valida) y etiqueta.", "Required: identifier, source step and target step (UNKNOWN_TRANSITION_SOURCE/TARGET if they do not exist). Optional: kind (NEXT default), order, condition (conditionExpressionId, validated by the checker) and label."];
  var TIMPL = " Selección en EventFlowRuntimeSupport.selectTransitions; orden fijado en EventFlowCompiler.compile.";
  var TIMPL_EN = " Selection in EventFlowRuntimeSupport.selectTransitions; order fixed in EventFlowCompiler.compile.";
  function TI(a, b) { return [a + TIMPL, b + TIMPL_EN]; }
  var transitions = [
    V(0, TR(["El camino normal entre dos pasos.", "The normal path between two steps."],
      ["Para encadenar un paso con el siguiente; es el tipo por defecto.", "To chain a step to the next one; it is the default kind."],
      TC,
      ["Sale de cualquier paso que no sea un final y no sea una decisión/ruta (estas usan TRUE/FALSE).", "Leaves any step that is not an end and not a decision/route (those use TRUE/FALSE)."],
      ["Entra en cualquier paso salvo el inicio/entrada (que no necesita entrantes). Una unión necesita al menos dos entrantes.", "Enters any step except the start/source (which needs no incoming). A join needs at least two incoming."],
      ["Se sigue cuando el paso acaba bien (y también si no hay ninguna salida «de éxito»); la división paralela las sigue todas.", "Followed when the step succeeds (also when there is no “success” exit); the parallel split follows all of them."],
      TI("FlowTransitionKind.NEXT; seleccionada entre NEXT/ON_SUCCESS por orden.", "FlowTransitionKind.NEXT; selected among NEXT/ON_SUCCESS by order."))),
    V(1, TR(["La salida que se sigue cuando el paso acabó bien.", "The exit followed when the step succeeded."],
      ["Para dejar explícito en el diagrama el camino feliz junto a otra de error.", "To make the happy path explicit on the diagram alongside an error exit."],
      TC,
      ["Sale de cualquier paso no final y no decisión/ruta; útil en pasos que pueden fallar (externos, esperas).", "Leaves any non-end, non-decision/route step; useful on steps that can fail (external, waits)."],
      ["Entra en cualquier paso salvo el inicio/entrada.", "Enters any step except the start/source."],
      ["Equivale a NEXT en el motor: se sigue la primera de ambos tipos por orden.", "Equivalent to NEXT in the engine: the first of either kind by order is followed."],
      TI("FlowTransitionKind.ON_SUCCESS.", "FlowTransitionKind.ON_SUCCESS."))),
    V(2, TR(["La salida que se sigue cuando el paso falla.", "The exit followed when the step fails."],
      ["En pasos que pueden fallar (acción, operación, comando, espera) para atender el error en vez de compensar.", "On steps that can fail (action, operation, command, wait) to handle the error instead of compensating."],
      TC,
      ["Sale de un paso que puede fallar o agotar su plazo (externos, esperas, ausencias, secuencia o patrón sin tipos, etc.).", "Leaves a step that may fail or time out (external, waits, absence, sequence or pattern without types, etc.)."],
      ["Normalmente un tratamiento de error, una alternativa o un final de error; el comprobador acepta cualquier paso.", "Normally an error handler, a fallback or an error end; the checker accepts any step."],
      ["Si el paso falla se sigue esta salida (también como alternativa si venció el plazo y no hay TIMEOUT). Sin ella, el motor compensa en orden inverso y, si no hay nada que compensar, la ejecución queda «fallida».", "If the step fails this exit is followed (also as fallback when the deadline passed and there is no TIMEOUT). Without it the engine compensates in reverse order and, if nothing to compensate, the run is “failed”."],
      TI("FlowTransitionKind.ON_ERROR; fallback de TIMEOUT.", "FlowTransitionKind.ON_ERROR; fallback of TIMEOUT."))),
    V(3, TR(["Transición de compensación, declarada pero sin uso en el motor.", "Compensation transition, declared but unused by the engine."],
      ["No usarla para deshacer: la compensación real se define con enlaces de compensación. No verificado ningún consumidor.", "Do not use it to undo: real compensation is defined with compensation links. No consumer was verified."],
      TC,
      ["Para el comprobador, cualquier paso (cuenta como entrante/saliente en huérfanos y alcanzabilidad).", "For the checker, any step (it counts as incoming/outgoing for orphan and reachability rules)."],
      ["Cualquier paso.", "Any step."],
      ["El motor no la selecciona expresamente: sólo se tomaría como «primera salida» si no hay ni salida normal ni de éxito.", "The engine does not select it explicitly: it would only be taken as the “first exit” when there is neither a normal nor a success exit."],
      TI("FlowTransitionKind.COMPENSATES (sin consumidor propio encontrado).", "FlowTransitionKind.COMPENSATES (no consumer of its own found)."))),
    V(4, TR(["La salida que se sigue cuando el paso superó su plazo.", "The exit followed when the step exceeded its deadline."],
      ["En esperas y otros pasos con límite de tiempo, para actuar si algo no llega a tiempo.", "On waits and other steps with a time limit, to act if something does not arrive in time."],
      ["Como las demás; el paso de origen debe tener política de tiempo (duración y, opcionalmente, la transición concreta).", "As the others; the source step must have a time policy (duration and, optionally, the specific transition)."],
      ["Un paso con límite de tiempo, típicamente esperar evento.", "A step with a time limit, typically wait for event."],
      ["Cualquier paso (alternativa, tratamiento de error, final).", "Any step (fallback, error handler, end)."],
      ["Se sigue en vez de la salida de error cuando se agota el plazo; si no existe, se usa la salida de error; si tampoco, compensación o fallo.", "Followed instead of the error exit when the deadline runs out; if absent, the error exit is used; if that is absent too, compensation or failure."],
      TI("FlowTransitionKind.TIMEOUT; vencimiento detectado en EventFlowRuntimeService.", "FlowTransitionKind.TIMEOUT; expiry detected in EventFlowRuntimeService."))),
    V(5, TR(["La salida de una decisión o ruta cuando la condición se cumple.", "The exit of a decision or route when the condition holds."],
      ["Solo saliendo de una decisión o ruta, siempre acompañada de su salida «falso».", "Only leaving a decision or route, always accompanied by its “false” exit."],
      TC,
      ["Solo decisión o ruta (para otros pasos el motor ignora el tipo y sigue la primera NEXT/ON_SUCCESS).", "Only decision or route (for other steps the engine ignores the kind and follows the first NEXT/ON_SUCCESS)."],
      ["Cualquier paso.", "Any step."],
      ["Se sigue únicamente si la condición del paso dio verdadero; sin ella la rama termina.", "Followed only if the step's condition was true; without it the branch ends."],
      TI("FlowTransitionKind.TRUE; exigencia en EventFlowChecker.checkFlow (DECISION_BRANCHES).", "FlowTransitionKind.TRUE; required by EventFlowChecker.checkFlow (DECISION_BRANCHES)."))),
    V(6, TR(["La salida de una decisión o ruta cuando la condición no se cumple.", "The exit of a decision or route when the condition does not hold."],
      ["Solo saliendo de una decisión o ruta, siempre junto con su salida «verdadero».", "Only leaving a decision or route, always together with its “true” exit."],
      TC,
      ["Solo decisión o ruta.", "Only decision or route."],
      ["Cualquier paso.", "Any step."],
      ["Se sigue únicamente si la condición dio falso; sin ella la rama termina.", "Followed only if the condition was false; without it the branch ends."],
      TI("FlowTransitionKind.FALSE; exigencia en EventFlowChecker.checkFlow (DECISION_BRANCHES).", "FlowTransitionKind.FALSE; required by EventFlowChecker.checkFlow (DECISION_BRANCHES)).")))
  ];

  // ==================================================================================================
  // 3) EVENT FIELD TYPES (13)
  // ==================================================================================================
  var FC = ["Obligatorios: identificador, nombre y tipo (únicos en el evento). Opcionales: obligatorio, múltiple, descripción.", "Required: identifier, name and type (unique within the event). Optional: required, many, description."];
  var FP = ["No aplica: un campo no es un paso del flujo; pertenece a un único tipo de evento.", "Not applicable: a field is not a flow step; it belongs to a single event type."];
  var FS = ["No aplica; solo puede ser destino de una copia de datos de un enlace (un campo de destino, una copia por campo) y origen de rutas en condiciones.", "Not applicable; it can only be the target of a binding's data copy (one copy per target field) and the source of paths in conditions."];
  var FK = ["Enlaces entre eventos (copia de datos) y condiciones de flujos (rutas de datos).", "Event bindings (data copy) and flow conditions (data paths)."];
  function FI(c) { return ["EventFieldType." + c + " en EventFieldDefinition; el comprobador (EventModelChecker) solo vigila ids/nombres duplicados y los campos obligatorios sin mapear; no hay validación de valor en ejecución.", "EventFieldType." + c + " in EventFieldDefinition; the checker (EventModelChecker) only guards duplicate ids/names and unmapped required fields; there is no run-time value validation."]; }
  function FLD(i, c, r, w, extra) {
    return V(i, F({
      r: r, w: w,
      c: extra ? [FC[0] + " " + extra[0], FC[1] + " " + extra[1]] : FC,
      p: FP, s: FS,
      e: ["Declara qué clase de dato viaja; si es obligatorio, todo enlace hacia este evento debe rellenarlo. HYDRA no comprueba en ejecución que el valor respete el tipo.", "Declares what kind of datum travels; if required, every binding toward this event must fill it. HYDRA does not check at run time that the value respects the type."],
      k: FK, i: FI(c)
    }));
  }
  var fields = [
    FLD(0, "STRING", ["Un texto.", "A text."], ["Para nombres, etiquetas o identificadores libres; es el tipo por defecto de un campo nuevo.", "For names, labels or free identifiers; it is the default type of a new field."]),
    FLD(1, "INTEGER", ["Un número entero.", "A whole number."], ["Contadores, cantidades o posiciones sin decimales.", "Counters, quantities or positions without decimals."]),
    FLD(2, "DECIMAL", ["Un número con decimales.", "A number with decimals."], ["Importes, porcentajes o medidas.", "Amounts, percentages or measures."]),
    FLD(3, "BOOLEAN", ["Un valor verdadero o falso.", "A true or false value."], ["Indicadores de sí/no, útiles en condiciones de decisión.", "Yes/no flags, useful in decision conditions."]),
    FLD(4, "DATE", ["Un día sin hora.", "A day without time."], ["Cuando solo importa la fecha (vencimientos, cumpleaños).", "When only the date matters (due dates, birthdays)."]),
    FLD(5, "DATETIME", ["Un instante concreto.", "A specific instant."], ["Cuando importa el momento exacto (cuándo ocurrió algo).", "When the exact moment matters (when something happened)."]),
    FLD(6, "DURATION", ["Un lapso de tiempo.", "A span of time."], ["Plazos o tiempos transcurridos.", "Deadlines or elapsed times."]),
    FLD(7, "DOCUMENT_ID", ["Referencia a un documento (modelo, metamodelo…).", "Reference to a document (model, metamodel…)."], ["Para señalar sobre qué documento trata el evento.", "To point out which document the event is about."]),
    FLD(8, "ELEMENT_ID", ["Referencia a un elemento de un modelo.", "Reference to an element of a model."], ["Para señalar el elemento concreto afectado.", "To point out the specific element affected."]),
    FLD(9, "ENUM", ["Un valor de entre los de una enumeración.", "A value among those of an enumeration."], ["Cuando el dato solo admite un conjunto cerrado de valores.", "When the datum only admits a closed set of values."], ["Condicional: la enumeración (enumTypeId) es obligatoria solo con este tipo.", "Conditional: the enumeration (enumTypeId) is mandatory only with this type."]),
    FLD(10, "OBJECT", ["Una estructura de otro tipo.", "A structure of another type."], ["Cuando el dato agrupa varios valores con su propia forma.", "When the datum groups several values with their own shape."], ["Condicional: el tipo de objeto (objectTypeId) es obligatorio solo con este tipo.", "Conditional: the object type (objectTypeId) is mandatory only with this type."]),
    FLD(11, "ARRAY", ["Una colección de valores.", "A collection of values."], ["Cuando el evento trae una lista (por ejemplo, varios elementos afectados).", "When the event brings a list (for example several affected elements)."]),
    FLD(12, "ANY", ["Un dato sin restricción de tipo.", "A datum with no type restriction."], ["Cuando el tipo no se conoce o varía; renuncia a la ayuda del tipo.", "When the type is unknown or varies; gives up the help of the type."])
  ];

  // ==================================================================================================
  // 4) CANONICAL EVENT ENVELOPE PARTS (6) and BINDING ERROR POLICIES (3)
  // ==================================================================================================
  var CE_PRED = ["No aplica: es una parte del evento, no un paso. Lo rellena quien emite el evento.", "Not applicable: it is a part of the event, not a step. Whoever emits the event fills it."];
  var CE_SUC = ["No aplica: viaja con el evento hacia todos los suscriptores (enlaces, flujos y gamificación).", "Not applicable: it travels with the event to all subscribers (bindings, flows and gamification)."];
  var CE_K = ["Enlaces, flujos (condiciones sobre rutas del evento) y gamificación, que leen estas partes.", "Bindings, flows (conditions over event paths) and gamification, which read these parts."];
  function CE(i, r, w, c, e, impl) {
    return V(i, F({ r: r, w: w, c: c, p: CE_PRED, s: CE_SUC, e: e, k: CE_K, i: impl }));
  }
  var canonical = [
    CE(0, ["Quién emitió el evento: el componente (y opcionalmente módulo, instancia y versión).", "Who emitted the event: the component (and optionally module, instance and version)."],
      ["Siempre presente; sirve para filtrar o depurar de dónde viene un evento.", "Always present; useful to filter or debug where an event comes from."],
      ["Obligatorio: componente. Opcionales: módulo, instancia, versión.", "Required: component. Optional: module, instance, version."],
      ["Identifica al emisor en la traza y en los eventos derivados.", "Identifies the emitter in the trace and in derived events."],
      ["Registro EventSource en CanonicalEvent.", "EventSource record in CanonicalEvent."]),
    CE(1, ["En qué situación ocurrió el hecho (diseño de metamodelo, edición, validación, corrección rápida, operación, importación o exportación, flujo, gamificación o sistema).", "In which situation the fact happened (metamodel design, editing, validation, quick fix, operation, import/export, flow, gamification or system)."],
      ["Para reaccionar solo a hechos de una situación concreta o para entender el origen de una cadena.", "To react only to facts of a given situation or to understand the origin of a chain."],
      ["Obligatorio, con SYSTEM por defecto si no se indica. Los valores posibles se detallan en los elementos de contexto.", "Mandatory, defaulting to SYSTEM if not given. The possible values are detailed in the context elements."],
      ["Clasifica el evento; no cambia cómo se entrega.", "Classifies the event; it does not change how it is delivered."],
      ["EventContext / EventContextKind (9 valores); por defecto SYSTEM en EventContext y CanonicalEvent.", "EventContext / EventContextKind (9 values); defaults to SYSTEM in EventContext and CanonicalEvent."]),
    CE(2, ["Quién causó el hecho (usuario, sistema, importador, corrección rápida, operación, automatización o servicio externo).", "Who caused the fact (user, system, importer, quick fix, operation, automation or external service)."],
      ["Cuando importa distinguir acciones humanas de automáticas.", "When it matters to tell human actions from automatic ones."],
      ["Opcional; SYSTEM por defecto cuando se construye un actor sin tipo.", "Optional; SYSTEM by default when an actor is built without a kind."],
      ["Anota la autoría del hecho en el evento.", "Records the authorship of the fact in the event."],
      ["EventActor / EventActorKind (7 valores).", "EventActor / EventActorKind (7 values)."]),
    CE(3, ["Sobre qué cosa recae el hecho (metamodelo, clase, atributo, modelo, elemento, regla, diagnóstico, corrección, flujo, premio, editor…).", "What the fact is about (metamodel, class, attribute, model, element, rule, diagnostic, fix, flow, reward, editor…)."],
      ["Para reaccionar según el tipo de cosa afectada, no solo según el tipo de evento.", "To react depending on the kind of thing affected, not only the event type."],
      ["Opcional; sus valores se detallan en los elementos de sujeto.", "Optional; its values are detailed in the subject elements."],
      ["Identifica el objeto afectado en el evento y en las condiciones sobre rutas.", "Identifies the affected object in the event and in path conditions."],
      ["EventSubject / EventSubjectKind (17 valores).", "EventSubject / EventSubjectKind (17 values)."]),
    CE(4, ["Identificadores de transacción, de correlación y de causa para seguir una cadena de reacciones.", "Transaction, correlation and causation identifiers to follow a chain of reactions."],
      ["Para agrupar o seguir eventos relacionados; los pasos con memoria agrupan por la correlación (por defecto).", "To group or follow related events; steps with memory group by correlation (by default)."],
      ["Todos opcionales; si falta la correlación, los pasos con memoria usan el identificador del evento.", "All optional; if correlation is missing, steps with memory use the event identifier."],
      ["Mantiene el hilo entre el evento original y los derivados.", "Keeps the thread between the original event and derived ones."],
      ["Campos transactionId, correlationId, causationId de CanonicalEvent; EventFlowRuntimeSupport.groupingKey.", "transactionId, correlationId, causationId fields of CanonicalEvent; EventFlowRuntimeSupport.groupingKey."]),
    CE(5, ["Dos mapas libres: los datos del evento y la información técnica adicional.", "Two free maps: the event data and extra technical information."],
      ["Los datos son lo que copian los enlaces y consultan las condiciones; los metadatos, información de apoyo.", "The data is what bindings copy and conditions query; the metadata is support information."],
      ["Opcionales; las claves se corresponden con los campos del tipo de evento (sin validación de tipos en ejecución).", "Optional; keys match the event type's fields (no run-time type validation)."],
      ["Transportan el contenido; los pasos de flujo guardan sus resultados en una sección reservada de los datos.", "Carry the content; flow steps store their results in a reserved section of the data."],
      ["Mapas payload y metadata de CanonicalEvent; sección _hydraFlow en EventFlowRuntimeSupport.withRuntimePayload.", "payload and metadata maps of CanonicalEvent; _hydraFlow section in EventFlowRuntimeSupport.withRuntimePayload."])
  ];

  var BP = ["No aplica: es una opción del enlace, no un paso.", "Not applicable: it is an option of the binding, not a step."];
  function BE(i, r, w, e, c, impl) {
    return V(i, F({
      r: r, w: w,
      c: ["Opcional: política de error del enlace (REPORT por defecto). " + c[0], "Optional: error policy of the binding (REPORT by default). " + c[1]],
      p: ["Se aplica al fallar un enlace cuyo evento de origen (reservado o propio) llegó y cuya condición se cumplió.", "Applies when a binding fails whose source event (reserved or custom) arrived and whose condition held."],
      s: ["Tras el fallo, el evento derivado no se publica; el siguiente evento puede volver a aplicar el enlace salvo con la política de desactivar.", "After the failure the derived event is not published; the next event may apply the binding again except with the disable policy."],
      e: e,
      k: ["Enlaces y registro de avisos; el estado desactivado es solo en memoria.", "Bindings and warning log; the disabled state is in memory only."],
      i: impl
    }));
  }
  var bindingPolicies = [
    BE(0, ["Si el enlace falla, se ignora en silencio.", "If the binding fails it is silently ignored."], ["Cuando un fallo ocasional no importa y no quieres ruido en el registro.", "When an occasional failure does not matter and you do not want log noise."],
      ["No se publica el derivado ni se deja aviso.", "No derived event is published and no warning is left."], ["SKIP.", "SKIP."],
      ["EventBindingErrorPolicy.SKIP; tratamiento en EventBindingRuntimeService (fallo de enlace).", "EventBindingErrorPolicy.SKIP; handled in EventBindingRuntimeService (binding failure)."]),
    BE(1, ["Si el enlace falla, se deja un aviso en el registro (por defecto).", "If the binding fails a warning is logged (default)."], ["Valor recomendado para saber que algo falló sin parar nada.", "Recommended value to know something failed without stopping anything."],
      ["Aviso en el registro; el enlace sigue activo.", "Warning in the log; the binding stays active."], ["REPORT (valor por defecto).", "REPORT (default value)."],
      ["EventBindingErrorPolicy.REPORT; tratamiento en EventBindingRuntimeService.", "EventBindingErrorPolicy.REPORT; handled in EventBindingRuntimeService."]),
    BE(2, ["Si el enlace falla, se avisa y deja de aplicarse.", "If the binding fails a warning is logged and it stops applying."], ["Para enlaces cuyo fallo indica un problema que se repetirá y conviene cortar.", "For bindings whose failure signals a problem that will recur and is better cut off."],
      ["Aviso y desactivación hasta reiniciar el motor (el estado no es duradero).", "Warning and deactivation until the engine restarts (the state is not durable)."], ["DISABLE_BINDING.", "DISABLE_BINDING."],
      ["EventBindingErrorPolicy.DISABLE_BINDING; estado desactivado en memoria en EventBindingRuntimeService (hallazgo F6).", "EventBindingErrorPolicy.DISABLE_BINDING; in-memory disabled state in EventBindingRuntimeService (finding F6)."])
  ];

  HM.facets({ caps: { events: { elements: {
    node_entry_exit: { variants: entryExit },
    node_event: { variants: eventSteps },
    node_external: { variants: external },
    node_routing: { variants: routing },
    node_parallel: { variants: parallel },
    node_timing: { variants: timing },
    node_transform: { variants: transform },
    node_pattern: { variants: pattern },
    node_recovery: { variants: recovery },
    transition: { variants: transitions },
    event_field: { variants: fields },
    canonical_event: { variants: canonical },
    binding: { variants: bindingPolicies }
  } } } });

  // ==================================================================================================
  // 5) NEW ELEMENTS: context / actor / subject kinds (one variant per real enum value)
  // ==================================================================================================
  // rows: [constant, nameEs, nameEn, descEs, descEn, usedEs, usedEn, implEs, implEn]
  function KINDS(rows, enumName, relK) {
    var variants = [], facets = [], lens = [];
    rows.forEach(function (r, i) {
      variants.push({ n: T(r[1], r[2]), d: T(r[3], r[4]), req: "ALTERNATIVE" });
      lens.push({ k: r[0], fd: T("Valor «" + r[1] + "». Qué implica: " + r[3].replace(/\.$/, "") + ". Uso funcional: " + r[5], "Value “" + r[2] + "”. What it implies: " + r[4].replace(/\.$/, "") + ". Functional use: " + r[6]), td: T(enumName + "." + r[0] + ". " + r[7], enumName + "." + r[0] + ". " + r[8]) });
      facets.push({ i: i, facets: F({
        r: [r[3], r[4]], w: [r[5], r[6]],
        c: ["Se elige como valor del tipo correspondiente al construir el evento; no tiene opciones propias.", "It is chosen as the value of the corresponding kind when building the event; it has no options of its own."],
        p: ["No aplica: es un valor del evento, no un paso del flujo.", "Not applicable: it is an event value, not a flow step."],
        s: ["No aplica: viaja con el evento hacia todos los suscriptores.", "Not applicable: it travels with the event to all subscribers."],
        e: ["Clasifica el evento; no cambia cómo se entrega, pero permite filtrar y razonar sobre su origen.", "Classifies the event; it does not change how it is delivered, but allows filtering and reasoning about its origin."],
        k: relK, i: [enumName + "." + r[0] + ". " + r[7], enumName + "." + r[0] + ". " + r[8]]
      }) });
    });
    return { variants: variants, facets: facets, lens: lens };
  }
  var K_ALL = ["Enlaces, flujos (condiciones sobre rutas del evento) y gamificación.", "Bindings, flows (conditions over event paths) and gamification."];
  var NOPROD = ["Productor no verificado en el código (solo búsqueda literal).", "Producer not verified in the code (literal search only)."];

  var ctx = KINDS([
    ["METAMODEL_DESIGN", "Diseño de metamodelo", "Metamodel design", "El hecho ocurre al diseñar o cambiar un metamodelo.", "The fact happens while designing or changing a metamodel.", "Para reaccionar a cambios en la definición del lenguaje.", "To react to changes in the language definition.", NOPROD[0], NOPROD[1]],
    ["MODEL_EDITING", "Edición de modelo", "Model editing", "El hecho ocurre al editar un modelo en una sesión del editor.", "The fact happens while editing a model in an editor session.", "Para reaccionar a guardar, crear, editar o borrar elementos; es el contexto que más consume la gamificación.", "To react to saving, creating, editing or deleting elements; it is the context gamification consumes most.", "Producido por EditorSessionEventPublisher y TextualSharedLayerRuntimeService.", "Produced by EditorSessionEventPublisher and TextualSharedLayerRuntimeService."],
    ["VALIDATION", "Validación", "Validation", "El hecho ocurre al ejecutar una validación.", "The fact happens when a validation runs.", "Para reaccionar a validaciones y a sus diagnósticos.", "To react to validations and their diagnostics.", "Producido por ValidationRuntimeEventFactory.", "Produced by ValidationRuntimeEventFactory."],
    ["QUICK_FIX", "Corrección rápida", "Quick fix", "El hecho ocurre al aplicar una corrección rápida.", "The fact happens when a quick fix is applied.", "Para premiar o encadenar acciones tras corregir un problema.", "To reward or chain actions after fixing a problem.", "Producido por ValidationRuntimeEventFactory y TextualSharedLayerRuntimeService.", "Produced by ValidationRuntimeEventFactory and TextualSharedLayerRuntimeService."],
    ["OPERATION", "Operación", "Operation", "El hecho ocurre al ejecutar una operación del metamodelo.", "The fact happens when a metamodel operation runs.", "Para reaccionar a ejecuciones de operaciones.", "To react to operation executions.", NOPROD[0], NOPROD[1]],
    ["IMPORT_EXPORT", "Importación o exportación", "Import or export", "El hecho ocurre al importar o exportar contenido.", "The fact happens when importing or exporting content.", "Para reaccionar a entradas y salidas de datos.", "To react to data coming in or going out.", NOPROD[0], NOPROD[1]],
    ["EVENT_FLOW", "Flujo de eventos", "Event flow", "El hecho lo produce un flujo de eventos.", "The fact is produced by an event flow.", "Para encadenar flujos: un flujo emite y otro reacciona.", "To chain flows: one flow emits and another reacts.", "Producido por EventFlowRuntimeEventFactory.derived.", "Produced by EventFlowRuntimeEventFactory.derived."],
    ["GAMIFICATION", "Gamificación", "Gamification", "El hecho lo produce la gamificación (premio, penalización, rechazo, compensación).", "The fact is produced by gamification (award, penalty, rejection, compensation).", "Para que un flujo reaccione a los resultados de la gamificación.", "For a flow to react to gamification outcomes.", "Producido por GamificationRuntimeEventFactory.", "Produced by GamificationRuntimeEventFactory."],
    ["SYSTEM", "Sistema", "System", "Contexto por defecto cuando no se indica otro.", "Default context when none is given.", "Para hechos generales o cuando el contexto no importa.", "For general facts or when the context does not matter.", "Valor por defecto en EventContext y CanonicalEvent.", "Default value in EventContext and CanonicalEvent."]
  ], "EventContextKind", K_ALL);

  var actor = KINDS([
    ["USER", "Usuario", "User", "Una persona causa el hecho.", "A person causes the fact.", "Para distinguir acciones manuales de automáticas.", "To tell manual actions from automatic ones.", NOPROD[0], NOPROD[1]],
    ["SYSTEM", "Sistema", "System", "HYDRA causa el hecho por sí mismo (valor por defecto).", "HYDRA causes the fact by itself (default value).", "Para hechos sin autor concreto.", "For facts without a specific author.", "Valor por defecto en EventActor.", "Default value in EventActor."],
    ["IMPORTER", "Importador", "Importer", "Un importador causa el hecho.", "An importer causes the fact.", "Para marcar cambios que vienen de una importación.", "To mark changes coming from an import.", NOPROD[0], NOPROD[1]],
    ["QUICK_FIX", "Corrección rápida", "Quick fix", "Una corrección rápida causa el hecho.", "A quick fix causes the fact.", "Para marcar cambios hechos por correcciones automáticas.", "To mark changes made by automatic fixes.", NOPROD[0], NOPROD[1]],
    ["OPERATION", "Operación", "Operation", "Una operación del metamodelo causa el hecho.", "A metamodel operation causes the fact.", "Para marcar cambios producidos por una operación.", "To mark changes produced by an operation.", NOPROD[0], NOPROD[1]],
    ["AUTOMATION", "Automatización", "Automation", "Una automatización (por ejemplo un flujo) causa el hecho.", "An automation (for example a flow) causes the fact.", "Para separar lo automatizado de lo manual.", "To separate what is automated from what is manual.", NOPROD[0], NOPROD[1]],
    ["EXTERNAL_SERVICE", "Servicio externo", "External service", "Un servicio ajeno a HYDRA causa el hecho.", "A service outside HYDRA causes the fact.", "Para eventos que llegan de integraciones externas.", "For events arriving from external integrations.", NOPROD[0], NOPROD[1]]
  ], "EventActorKind", K_ALL);

  var subject = KINDS([
    ["METAMODEL", "Metamodelo", "Metamodel", "El hecho trata de un metamodelo completo.", "The fact is about a whole metamodel.", "Para reaccionar a cambios globales del lenguaje.", "To react to language-wide changes.", NOPROD[0], NOPROD[1]],
    ["META_CLASS", "Clase del metamodelo", "Metamodel class", "El hecho trata de una clase del metamodelo.", "The fact is about a metamodel class.", "Para reaccionar a la creación o cambio de clases.", "To react to class creation or change.", NOPROD[0], NOPROD[1]],
    ["META_ATTRIBUTE", "Atributo del metamodelo", "Metamodel attribute", "El hecho trata de un atributo de una clase.", "The fact is about an attribute of a class.", "Para reaccionar a cambios de atributos.", "To react to attribute changes.", NOPROD[0], NOPROD[1]],
    ["META_REFERENCE", "Referencia del metamodelo", "Metamodel reference", "El hecho trata de una referencia entre clases.", "The fact is about a reference between classes.", "Para reaccionar a cambios de referencias.", "To react to reference changes.", NOPROD[0], NOPROD[1]],
    ["META_OPERATION", "Operación del metamodelo", "Metamodel operation", "El hecho trata de una operación definida en el metamodelo.", "The fact is about an operation defined in the metamodel.", "Para reaccionar a cambios u operaciones ejecutadas.", "To react to changes or executed operations.", NOPROD[0], NOPROD[1]],
    ["MODEL", "Modelo", "Model", "El hecho trata de un modelo completo (por ejemplo, guardarlo).", "The fact is about a whole model (for example saving it).", "Para reaccionar a guardados y validaciones del modelo.", "To react to saves and validations of the model.", "Producido por EditorSessionEventPublisher, TextualSharedLayerRuntimeService y ValidationRuntimeEventFactory.", "Produced by EditorSessionEventPublisher, TextualSharedLayerRuntimeService and ValidationRuntimeEventFactory."],
    ["MODEL_ELEMENT", "Elemento de modelo", "Model element", "El hecho trata de un elemento concreto de un modelo.", "The fact is about a specific element of a model.", "Para reaccionar a crear, editar o borrar elementos.", "To react to creating, editing or deleting elements.", "Producido por EditorSessionEventPublisher y TextualSharedLayerRuntimeService.", "Produced by EditorSessionEventPublisher and TextualSharedLayerRuntimeService."],
    ["MODEL_FEATURE", "Propiedad de elemento", "Element property", "El hecho trata de una propiedad de un elemento de modelo.", "The fact is about a property of a model element.", "Para reaccionar a cambios de un valor concreto.", "To react to changes of a specific value.", NOPROD[0], NOPROD[1]],
    ["VALIDATION_RULE", "Regla de validación", "Validation rule", "El hecho trata de una regla de validación.", "The fact is about a validation rule.", "Para reaccionar a reglas ejecutadas o cambiadas.", "To react to rules run or changed.", NOPROD[0], NOPROD[1]],
    ["VALIDATION_DIAGNOSTIC", "Diagnóstico de validación", "Validation diagnostic", "El hecho trata de un diagnóstico producido por una validación.", "The fact is about a diagnostic produced by a validation.", "Para reaccionar cuando aparece o desaparece un diagnóstico.", "To react when a diagnostic appears or disappears.", "Producido por ValidationRuntimeEventFactory.", "Produced by ValidationRuntimeEventFactory."],
    ["QUICK_FIX", "Corrección rápida", "Quick fix", "El hecho trata de una corrección rápida.", "The fact is about a quick fix.", "Para reaccionar a correcciones aplicadas.", "To react to fixes applied.", "Producido por ValidationRuntimeEventFactory y TextualSharedLayerRuntimeService.", "Produced by ValidationRuntimeEventFactory and TextualSharedLayerRuntimeService."],
    ["ACTION", "Acción", "Action", "El hecho trata de una acción del editor.", "The fact is about an editor action.", "Para reaccionar a acciones ejecutadas.", "To react to actions executed.", NOPROD[0], NOPROD[1]],
    ["EVENT_DEFINITION", "Definición de evento", "Event definition", "El hecho trata de la definición de un tipo de evento.", "The fact is about the definition of an event type.", "Para reaccionar a cambios en el vocabulario de eventos.", "To react to changes in the event vocabulary.", NOPROD[0], NOPROD[1]],
    ["EVENT_FLOW", "Flujo de eventos", "Event flow", "El hecho trata de un flujo de eventos.", "The fact is about an event flow.", "Para seguir qué flujo produjo o consumió un evento.", "To follow which flow produced or consumed an event.", "Producido por EventFlowRuntimeEventFactory.", "Produced by EventFlowRuntimeEventFactory."],
    ["GAMIFICATION_RULE", "Regla de gamificación", "Gamification rule", "El hecho trata de una regla de gamificación.", "The fact is about a gamification rule.", "Para reaccionar a premios o penalizaciones de una regla.", "To react to awards or penalties of a rule.", "Producido por GamificationRuntimeEventFactory.", "Produced by GamificationRuntimeEventFactory."],
    ["GAMIFICATION_REWARD", "Premio de gamificación", "Gamification reward", "El hecho trata de un premio concreto.", "The fact is about a specific reward.", "Para reaccionar a premios concedidos.", "To react to rewards granted.", NOPROD[0], NOPROD[1]],
    ["EDITOR", "Editor", "Editor", "El hecho trata del propio editor.", "The fact is about the editor itself.", "Para hechos generales de la sesión de edición.", "For general facts of the editing session.", NOPROD[0], NOPROD[1]]
  ], "EventSubjectKind", K_ALL);

  function kindElem(id, nes, nen, mes, men, pes, pen, wes, wen, card, k, tech) {
    return {
      id: id, n: T(nes, nen), kind: "variant-set", req: "OPTIONAL",
      mean: T(mes, men), purpose: T(pes, pen), when: T(wes, wen), card: card,
      variants: k.variants,
      props: [P2("Valor", "Value", "OPTIONAL", "Uno de los valores de la lista", "One of the values in the list")],
      io: { i: T("El emisor elige el valor al construir el evento", "The emitter picks the value when building the event"), o: T("Un evento clasificado que los suscriptores pueden filtrar", "A classified event that subscribers can filter") },
      effect: T("No cambia la entrega del evento; solo lo clasifica.", "Does not change delivery of the event; it only classifies it."),
      func: { rel: [T("Pertenece a 1 evento en circulación", "Belongs to 1 event in flight")], lifecycle: T("elegido al emitir → viaja con el evento", "picked on emit → travels with the event"), cond: T("Ver cada valor.", "See each value.") },
      tech: tech
    };
  }
  function P2(nes, nen, req, des, den) { return { n: T(nes, nen), req: req, d: T(des, den) }; }
  var EVB = "src/main/java/com/example/editor/event/";
  function ev1(f, l, label) { return { path: EVB + f, line: l, label: label }; }

  HM.extend("events", {
    elements: [
      kindElem("event_context_kind", "Contexto del evento", "Event context",
        "La situación en la que ocurrió el hecho que describe el evento.", "The situation in which the fact described by the event happened.",
        "Permitir filtrar y entender de dónde procede cada evento.", "Allow filtering and understanding where each event comes from.",
        "Al elegir o leer el contexto de un evento; hay nueve valores.", "When choosing or reading an event's context; there are nine values.",
        "1 evento ↔ exactamente 1 contexto", ctx,
        { type: "EventContext / EventContextKind", schema: "EventContextKind: METAMODEL_DESIGN, MODEL_EDITING, VALIDATION, QUICK_FIX, OPERATION, IMPORT_EXPORT, EVENT_FLOW, GAMIFICATION, SYSTEM", evidence: [ev1("EventContextKind.java", 3, "9 context kinds")] }),
      kindElem("event_actor_kind", "Actor del evento", "Event actor",
        "Quién o qué causó el hecho descrito por el evento.", "Who or what caused the fact described by the event.",
        "Distinguir acciones humanas, automáticas y externas.", "Tell human, automatic and external actions apart.",
        "Al elegir o leer el actor de un evento; es opcional y hay siete valores.", "When choosing or reading an event's actor; it is optional and there are seven values.",
        "1 evento ↔ 0..1 actor", actor,
        { type: "EventActor / EventActorKind", schema: "EventActorKind: USER, SYSTEM, IMPORTER, QUICK_FIX, OPERATION, AUTOMATION, EXTERNAL_SERVICE", evidence: [ev1("EventActorKind.java", 3, "7 actor kinds")] }),
      kindElem("event_subject_kind", "Sujeto del evento", "Event subject",
        "Sobre qué clase de cosa recae el hecho descrito por el evento.", "What kind of thing the fact described by the event is about.",
        "Permitir reaccionar según el tipo de objeto afectado.", "Allow reacting depending on the kind of object affected.",
        "Al elegir o leer el sujeto de un evento; es opcional y hay diecisiete valores.", "When choosing or reading an event's subject; it is optional and there are seventeen values.",
        "1 evento ↔ 0..1 sujeto", subject,
        { type: "EventSubject / EventSubjectKind", schema: "EventSubjectKind (17 values): METAMODEL, META_CLASS, META_ATTRIBUTE, META_REFERENCE, META_OPERATION, MODEL, MODEL_ELEMENT, MODEL_FEATURE, VALIDATION_RULE, VALIDATION_DIAGNOSTIC, QUICK_FIX, ACTION, EVENT_DEFINITION, EVENT_FLOW, GAMIFICATION_RULE, GAMIFICATION_REWARD, EDITOR", evidence: [ev1("EventSubjectKind.java", 3, "17 subject kinds")] })
    ],
    mapNodes: [
      { id: "m_ctx_kind", elem: "event_context_kind", n: T("Contexto", "Context"), k: "variant", req: "ALTERNATIVE", d: T("Situación en que ocurre el hecho", "Situation in which the fact happens") },
      { id: "m_actor_kind", elem: "event_actor_kind", n: T("Actor", "Actor"), k: "variant", req: "ALTERNATIVE", d: T("Quién causa el hecho", "Who causes the fact") },
      { id: "m_subject_kind", elem: "event_subject_kind", n: T("Sujeto", "Subject"), k: "variant", req: "ALTERNATIVE", d: T("Sobre qué recae el hecho", "What the fact is about") }
    ],
    mapEdges: [
      { a: "m_event", b: "m_ctx_kind", rel: "contains", card1: "1", card2: "1" },
      { a: "m_event", b: "m_actor_kind", rel: "contains", card1: "1", card2: "0..1", req: "OPTIONAL" },
      { a: "m_event", b: "m_subject_kind", rel: "contains", card1: "1", card2: "0..1", req: "OPTIONAL" }
    ]
  });

  HM.facets({ caps: { events: { elements: {
    event_context_kind: { variants: ctx.facets },
    event_actor_kind: { variants: actor.facets },
    event_subject_kind: { variants: subject.facets }
  } } } });

  function lensKinds(nf, fm, tm, k) { return { nf: nf, fm: fm, tm: tm, variants: k.lens }; }
  HM.lens({ caps: { events: { elements: {
    event_context_kind: lensKinds(T("Contexto del evento", "Event context"), T("Clasificación de la situación en que ocurre un evento; 9 valores.", "Classification of the situation in which an event happens; 9 values."), T("EventContext.kind: EventContextKind (9 valores; SYSTEM por defecto).", "EventContext.kind: EventContextKind (9 values; SYSTEM default)."), ctx),
    event_actor_kind: lensKinds(T("Actor del evento", "Event actor"), T("Clasificación de quién causa un evento; 7 valores.", "Classification of who causes an event; 7 values."), T("EventActor.kind: EventActorKind (7 valores; SYSTEM por defecto).", "EventActor.kind: EventActorKind (7 values; SYSTEM default)."), actor),
    event_subject_kind: lensKinds(T("Sujeto del evento", "Event subject"), T("Clasificación de sobre qué recae un evento; 17 valores.", "Classification of what an event is about; 17 values."), T("EventSubject.kind: EventSubjectKind (17 valores).", "EventSubject.kind: EventSubjectKind (17 values)."), subject)
  } } } });
})();
