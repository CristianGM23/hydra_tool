(function(){ "use strict"; var HM = window.HM, T = HM.T;
  var G = "src/main/java/com/example/editor/gamification/";
  var GD = "frontend/src/gamification-designer/";
  var RT = G + "runtime/GamificationRuntimeService.java";
  var FP = G + "feedback/GamificationFeedbackProjector.java";
  var DES = GD + "GamificationDesigner.tsx";
  var PARTS = GD + "GamificationDesignerParts.tsx";

  HM.cap("gamification", {
    name: T("Gamificación", "Gamification"), group: "behavior",
    user: {
      tagline: T("Premia o penaliza lo que ocurre al editar, con puntos, logros, retos y recompensas",
                 "Rewards or penalises what happens while editing, with points, achievements, challenges and rewards"),
      what: T("Un motor opcional, propio de cada metamodelo. Se definen reglas que ligan un evento verificado (por ejemplo «una violación se resolvió») a un efecto: sumar o restar puntos, desbloquear un logro, avanzar un reto o conceder una recompensa. Todo lo que se concede queda anotado en un historial que no se borra, y desde ese historial se calculan el estado de cada persona y los avisos que ve.",
              "An optional engine, specific to each metamodel. You define rules that bind a verified event (for example 'a violation was resolved') to an effect: add or subtract points, unlock an achievement, advance a challenge or grant a reward. Everything granted is noted in a history that is never erased, and from that history each person's status and the notices they see are calculated."),
      why: T("Motiva a quien edita a corregir problemas, aplicar correcciones y completar tareas, y hace visible el progreso sin tocar el modelo ni el lenguaje que se está definiendo.",
             "It motivates editors to fix problems, apply corrections and complete tasks, and makes progress visible without touching the model or the language being defined."),
      when: T("Cuando se quiere reforzar buenas prácticas de edición, medir avance o dar un marco de juego a un equipo. Solo funciona sobre un metamodelo ya guardado y con la gamificación activada en la sesión de edición.",
              "When you want to reinforce good editing habits, measure progress or give a team a game framework. It only works over a saved metamodel and with gamification enabled in the editing session."),
      canDefine: [
        T("Tipos de puntos, con un saldo mínimo permitido", "Point types, with a minimum allowed balance"),
        T("Niveles que se alcanzan al llegar a una cantidad de puntos", "Levels reached on hitting a points threshold"),
        T("Logros, visibles u ocultos", "Achievements, visible or hidden"),
        T("Retos que se completan tras un número de acciones", "Challenges completed after a number of actions"),
        T("Recompensas con un contenido libre", "Rewards with free-form content"),
        T("Reglas: evento + condición + efecto + política de repetición", "Rules: event + condition + effect + repeat policy"),
        T("Política de avisos (cuánto se muestran y dónde)", "Notice policy (how long and where they show)")
      ],
      needs: [
        T("Un metamodelo guardado", "A saved metamodel"),
        T("Eventos verificados del catálogo reservado o propios", "Verified events, built-in or custom"),
        T("Gamificación activada en la sesión de edición (por defecto está apagada)", "Gamification enabled in the editing session (off by default)"),
        T("Al menos un tipo de puntos para poder crear niveles y reglas con puntos", "At least one point type to create levels and point rules")
      ],
      produces: [
        T("Entradas de historial por cada concesión, rechazo o reversión", "History entries for every grant, rejection or reversal"),
        T("Estado por persona: puntos, nivel, logros, avance de retos, recompensas", "Per-person status: points, level, achievements, challenge progress, rewards"),
        T("Avisos para la persona: toasts y lista de actividad", "Notices for the person: toasts and an activity list"),
        T("Eventos derivados: concesión, penalización, rechazo y reversión", "Derived events: grant, penalty, rejection and reversal"),
        T("Diagnósticos de la comprobación de la definición", "Diagnostics from checking the definition")
      ],
      mandatory: [
        T("Identificador del metamodelo y de cada elemento", "Metamodel identifier and one for every element"),
        T("Tipo de evento de cada regla", "Event type of every rule"),
        T("Tipo de puntos de cada nivel", "Point type of every level"),
        T("Número de acciones objetivo de cada reto (mayor que 0)", "Target number of actions of every challenge (above 0)")
      ],
      optional: [
        T("Descripciones y recompensas asociadas a niveles, logros y retos", "Descriptions and rewards attached to levels, achievements and challenges"),
        T("Condición sobre el contenido del evento", "Condition on the event content"),
        T("Límites por persona y por día, y tiempo de espera entre concesiones", "Per-person and per-day limits, and wait time between grants"),
        T("Política de avisos global", "Global notice policy")
      ],
      conditional: [
        T("Valor esperado (solo si hay una condición y el operador lo necesita)", "Expected value (only if there is a condition and the operator needs it)"),
        T("Segundos de espera (solo con repetición con espera)", "Wait seconds (only with the wait-between-grants policy)"),
        T("Máximo por día (solo con el límite diario)", "Maximum per day (only with the daily limit)"),
        T("Logro, reto o recompensa (solo si la regla suma puntos)", "Achievement, challenge or reward (only if the rule awards, not penalises)")
      ],
      next: [
        T("Comprobar la definición y guardarla", "Check the definition and save it"),
        T("Activar la gamificación en la sesión de edición", "Enable gamification in the editing session"),
        T("Revisar el historial y los avisos generados", "Review the history and the notices generated"),
        T("Reaccionar a los eventos de gamificación desde un EventFlow", "React to gamification events from an EventFlow")
      ],
      interacts: [
        { cap: "events", t: T("Consume los eventos verificados y publica los suyos propios", "Consumes verified events and publishes its own") },
        { cap: "validation", t: T("Premiar la resolución de violaciones es el caso de uso por defecto", "Rewarding the resolution of violations is the default use case") },
        { cap: "metamodel", t: T("Cada definición pertenece a un metamodelo guardado", "Every definition belongs to a saved metamodel") }
      ]
    },
    func: {
      lifecycle: [
        T("definición: borrador → comprobada → guardada (definiciones + reglas + diagrama)", "definition: draft → checked → saved (definitions + rules + diagram)"),
        T("evento recibido → ¿ya procesado? → ¿es una reversión? → reglas que encajan → entrada de historial", "event received → already processed? → is it a reversal? → matching rules → history entry"),
        T("historial → estado reconstruido → avisos proyectados → eventos derivados publicados", "history → rebuilt status → projected notices → derived events published"),
        T("entrada de historial: activa → compensada (cuando se revierte)", "history entry: active → compensated (when reversed)"),
        T("reinicio: se borra el estado y el historial del metamodelo", "reset: the metamodel's status and history are cleared")
      ],
      semantics: [
        T("Una regla liga exactamente un tipo de evento a un efecto; puede añadir una condición. Si la condición está vacía basta el tipo de evento.", "A rule binds exactly one event type to an effect; it may add a condition. With an empty condition the event type alone suffices."),
        T("Cada regla tiene como máximo un tipo de puntos, un logro, un reto y una recompensa. Una penalización conserva los puntos negativos y no puede conceder recompensa.", "A rule has at most one point type, one achievement, one challenge and one reward. A penalty keeps negative points and cannot grant a reward."),
        T("Políticas de repetición: una sola vez, una vez por evento (por defecto), sin límite, con espera entre concesiones, o con límite diario.", "Repeat policies: only once, once per event (default), unlimited, with a wait between grants, or with a daily limit."),
        T("El saldo de un tipo de puntos nunca baja de su mínimo: una penalización se recorta y, si queda a cero, se rechaza con el motivo «saldo mínimo alcanzado».", "A point type's balance never drops below its minimum: a penalty is clipped and, if it ends at zero, it is rejected with the reason 'minimum balance reached'."),
        T("El mismo evento nunca se aplica dos veces (idempotencia), ni de forma global ni por regla.", "The same event is never applied twice (idempotency), neither globally nor per rule."),
        T("Las reversiones tienen prioridad: si un evento revierte una concesión previa, se anulan esas entradas y no se evalúan las reglas normales para ese evento.", "Reversals take priority: if an event reverses an earlier grant, those entries are cancelled and the normal rules are not evaluated for that event."),
        T("Los niveles no se guardan: se derivan de los puntos; con varios niveles sobre el mismo tipo de puntos gana el de umbral más alto alcanzado.", "Levels are not stored: they are derived from points; with several levels on the same point type, the highest threshold reached wins."),
        T("Límites honestos: no existe una entidad insignia, ni un almacén de notificaciones; los avisos se calculan al pedirlos. Solo los toasts y la lista de actividad tienen pantalla; las superficies de estado, progreso y diálogo se pueden configurar pero nadie las muestra.", "Honest limits: there is no badge entity and no notification store; notices are computed when requested. Only toasts and the activity list have a screen; the status, progress and dialog surfaces can be configured but nothing displays them.")
      ],
      states: [
        { n: T("Activa", "Active"), d: T("Entrada de historial vigente: cuenta para puntos, logros y retos", "Current history entry: counts for points, achievements and challenges") },
        { n: T("Compensada", "Compensated"), d: T("Entrada anulada por una reversión posterior", "Entry cancelled by a later reversal") },
        { n: T("Rechazada", "Rejected"), d: T("Regla que encajó pero no se pudo aplicar; se anota con 0 puntos", "A rule matched but could not be applied; noted with 0 points") }
      ],
      cross: [
        T("events → gamification: consume los eventos verificados del bus; el efecto puede publicar eventos propios que otras reglas o EventFlows pueden escuchar", "events → gamification: consumes verified events from the bus; its effect may publish own events that other rules or EventFlows can listen to"),
        T("validation → gamification: la resolución o reapertura de una violación puede premiar o revertir", "validation → gamification: resolving or reopening a violation may reward or reverse"),
        T("Protección contra bucles: un evento derivado lleva una profundidad que se incrementa; a partir de 16 niveles se ignora", "Loop protection: a derived event carries a depth that grows; from 16 levels on it is ignored")
      ]
    },
    tech: {
      runtime: T("Servicio de runtime por metamodelo suscrito al bus de eventos canónico, con bloqueo por metamodelo, ETag y persistencia en documentos JSON separados (definiciones, reglas, estado, historial, diagrama).",
                 "Per-metamodel runtime service subscribed to the canonical event bus, with per-metamodel lock, ETags and persistence in separate JSON documents (definitions, bindings, state, ledger, diagram)."),
      services: ["GamificationRuntimeService", "GamificationEventMatcher", "GamificationRuntimeEventFactory", "GamificationFeedbackProjector", "GamificationFeedbackPolicyResolver", "GamificationChecker", "GamificationApplicationService"],
      apis: [
        "POST /api/canonical-metamodels/{metamodelId}/gamification/runtime/events",
        "GET .../gamification/runtime/feedback",
        "DELETE .../gamification/runtime",
        "POST .../gamification/check"
      ],
      schemas: ["GamificationDefinitionModel (schemaVersion 1.0)", "GamificationBindingModel", "GamificationRule", "GamificationLedgerEntry", "GamificationRuntimeState", "GamificationActorState", "UserFeedbackEnvelope", "UserFeedbackPresentationPolicy"],
      tests: [
        "AggressiveEventGamificationSynergyTests", "GamificationPenaltyRuntimeTests", "GamificationPenaltyTests", "GamificationCheckerTests",
        "GamificationFeedbackProjectorTests", "GamificationNeutralExpressionConditionTests", "GamificationDiagramRepositoryTests",
        "GamificationApplicationServiceCatalogTests", "GamificationCorruptDocumentRecoveryTests", "GamificationDesigner.test.tsx", "gamificationConnections.test.ts", "api.test.ts"
      ],
      evidence: [
        { path: RT, line: 82, label: "subscribe() @PostConstruct: requisitos de entrada" },
        { path: RT, line: 102, label: "idempotencia por evento" },
        { path: RT, line: 108, label: "compensación primero" },
        { path: RT, line: 216, label: "rechazos: ONCE, límites, COOLDOWN" },
        { path: RT, line: 232, label: "recorte por minimumBalance" },
        { path: RT, line: 257, label: "reconstrucción del estado desde el historial" },
        { path: RT, line: 166, label: "persistencia con rollback" },
        { path: G + "GamificationRule.java", line: 8, label: "GamificationRule" },
        { path: G + "runtime/GamificationEventMatcher.java", line: 43, label: "operadores de condición" },
        { path: FP, line: 28, label: "proyección de avisos" },
        { path: G + "GamificationChecker.java", line: 21, label: "diagnósticos de la comprobación" },
        { path: G + "GamificationApplicationService.java", line: 37, label: "guardado con rechazo de errores bloqueantes" }
      ],
      notes: [
        T("Discrepancia verificada: la sesión de edición escribe los indicadores hydra.eventsEnabled / hydra.eventFlowsEnabled / hydra.gamificationEnabled en el PAYLOAD del evento (EditorModelSessionService.java:742-744), pero los consumidores los leen de los METADATOS (GamificationRuntimeService.java:84, EventFlowRuntimeService.java:493, EventBindingRuntimeService.java:67) y el publicador nunca los copia (EditorSessionEventPublisher.java:46). Revisar antes de fiarse de un apagado por sesión.",
          "Verified discrepancy: the editing session writes the flags hydra.eventsEnabled / hydra.eventFlowsEnabled / hydra.gamificationEnabled into the event PAYLOAD (EditorModelSessionService.java:742-744), but the consumers read them from event METADATA (GamificationRuntimeService.java:84, EventFlowRuntimeService.java:493, EventBindingRuntimeService.java:67) and the publisher never copies them (EditorSessionEventPublisher.java:46). Review before relying on a per-session switch-off."),
        T("El historial se recorta a un máximo (por defecto 10000, mínimo 100); la idempotencia depende de las entradas del historial, así que al recortar las más antiguas esos eventos pierden su respaldo.", "The ledger is trimmed to a maximum (default 10000, minimum 100); idempotency depends on ledger entries, so trimming the oldest ones removes the backing for those events."),
        T("Declarado pero sin consumidor: las superficies STATUS, PROGRESS y DIALOG de los avisos. Solo TOAST y ACTIVITY tienen pantalla (GamificationFeedbackCenter.tsx).", "Declared but not wired: the STATUS, PROGRESS and DIALOG notice surfaces. Only TOAST and ACTIVITY have a screen (GamificationFeedbackCenter.tsx)."),
        T("Desajuste del diseñador: el operador NOT_EXISTS lo admite el backend pero falta en la lista del diseñador (GamificationDesignerParts.tsx:30-36).", "Designer gap: the NOT_EXISTS operator is supported by the backend but missing from the designer's list (GamificationDesignerParts.tsx:30-36)."),
        T("El editor de política de avisos solo escribe claves globales hydra.feedback.*; las claves por regla (hydra.feedback.rule.<id>.<clave>) solo se pueden poner a mano en el JSON de reglas.", "The feedback policy editor writes only global hydra.feedback.* keys; per-rule keys (hydra.feedback.rule.<id>.<key>) can only be set by hand in the bindings JSON."),
        T("NOT FOUND como entidades: Badge, Penalty (solo es un tipo de efecto), Level persistido (se deriva en rebuildState), Notification (los avisos se calculan por petición). Sin prueba para GamificationFeedbackCenter.tsx ni para la ruta HTTP /runtime/events.", "NOT FOUND as entities: Badge, Penalty (only an effect kind), persisted Level (derived in rebuildState), Notification (notices are computed per request). No test for GamificationFeedbackCenter.tsx nor for the HTTP /runtime/events path."),
        T("Persistencia: .gamification-definitions/-bindings/-state/-ledger/-diagram.json bajo dsl.gamification.directory; los eventos reservados core.gamification.* están en el catálogo reservado (ReservedEventCatalog.java:13).", "Persistence: .gamification-definitions/-bindings/-state/-ledger/-diagram.json under dsl.gamification.directory; the core.gamification.* events are in the reserved catalog (ReservedEventCatalog.java:13).")
      ]
    },
    elements: [
      { id: "catalog", n: T("Catálogo de gamificación", "Gamification catalog"), kind: "concept", req: "MANDATORY",
        mean: T("El conjunto de puntos, niveles, logros, retos y recompensas de un metamodelo, más las reglas que los activan.", "The set of points, levels, achievements, challenges and rewards of a metamodel, plus the rules that trigger them."),
        purpose: T("Agrupa todo lo que define el juego de un metamodelo en dos documentos: definiciones y reglas.", "Groups everything that defines a metamodel's game in two documents: definitions and rules."),
        when: T("Al activar la gamificación de un metamodelo guardado.", "When enabling gamification for a saved metamodel."),
        card: "1 metamodelo ↔ 0..1 catálogo (definiciones + reglas)",
        props: [
          { n: T("Metamodelo", "Metamodel"), req: "MANDATORY", d: T("A qué metamodelo pertenece; debe coincidir en definiciones y reglas", "Which metamodel it belongs to; must match in definitions and rules") },
          { n: T("Nombre", "Name"), req: "OPTIONAL", d: T("Si se omite se usa el identificador", "If omitted the identifier is used") },
          { n: T("Anotaciones", "Annotations"), req: "OPTIONAL", d: T("Pares clave-valor; también guardan la política de avisos", "Key-value pairs; they also hold the notice policy") }
        ],
        io: { i: T("Elementos definidos + reglas", "Defined elements + rules"), o: T("Motor activo para ese metamodelo", "Active engine for that metamodel") },
        effect: T("Sin catálogo, el metamodelo no concede nada.", "Without a catalog the metamodel grants nothing."),
        func: { rel: [T("Contiene 0..N tipos de puntos, niveles, logros, retos y recompensas", "Contains 0..N point types, levels, achievements, challenges and rewards"), T("Se enlaza con 0..N reglas", "Linked to 0..N rules")], lifecycle: T("borrador → comprobado → guardado", "draft → checked → saved"), cond: T("Definiciones y reglas deben apuntar al mismo metamodelo y a la misma definición.", "Definitions and rules must point to the same metamodel and definition.") },
        tech: { type: "GamificationDefinitionModel / GamificationBindingModel", schema: "schemaVersion 1.0", evidence: [{ path: G + "GamificationDefinitionModel.java", line: 10, label: "modelo de definiciones" }, { path: G + "GamificationBindingModel.java", line: 10, label: "modelo de reglas" }] } },

      { id: "point", n: T("Tipo de puntos", "Point type"), kind: "concept", req: "REPEATABLE",
        mean: T("Una moneda de puntos con nombre propio, por ejemplo «experiencia» o «calidad».", "A named points currency, for example 'experience' or 'quality'."),
        purpose: T("Permite llevar varias cuentas de puntos independientes y fijar hasta dónde puede bajar cada una.", "Allows several independent point accounts and sets how low each may go."),
        when: T("Antes de crear niveles o reglas que den o quiten puntos.", "Before creating levels or rules that give or take points."),
        card: "1 tipo ↔ 0..N niveles, 0..N reglas",
        props: [
          { n: T("Nombre", "Name"), req: "MANDATORY", d: T("Cómo se muestra", "How it is displayed") },
          { n: T("Identificador", "ID"), req: "MANDATORY", d: T("Clave única dentro del catálogo", "Unique key within the catalog") },
          { n: T("Saldo mínimo", "Minimum balance"), req: "OPTIONAL", d: T("Por defecto 0; un valor negativo permite una deuda acotada", "Default 0; a negative value allows bounded debt") },
          { n: T("Descripción", "Description"), req: "OPTIONAL", d: T("Texto explicativo", "Explanatory text") }
        ],
        io: { i: T("Puntos concedidos o retirados por reglas", "Points granted or taken by rules"), o: T("Saldo por persona", "Per-person balance") },
        effect: T("Las penalizaciones se recortan para no bajar del saldo mínimo.", "Penalties are clipped so as not to go below the minimum balance."),
        func: { rel: [T("Referenciado por 0..N niveles y 0..N reglas", "Referenced by 0..N levels and 0..N rules")], lifecycle: T("definido → usado → (borrado: limpia las reglas que lo usaban)", "defined → used → (deleted: clears the rules that used it)"), cond: T("Los identificadores duplicados se señalan al comprobar.", "Duplicate identifiers are flagged on check.") },
        tech: { type: "PointTypeDefinition", schema: "minimumBalance long, default 0", evidence: [{ path: G + "PointTypeDefinition.java", line: 6, label: "PointTypeDefinition" }] } },

      { id: "level", n: T("Nivel", "Level"), kind: "concept", req: "REPEATABLE",
        mean: T("Un escalón que se alcanza al llegar a una cantidad de puntos de un tipo.", "A step reached on hitting a points amount of a type."),
        purpose: T("Dar sensación de progreso y, opcionalmente, una recompensa al subir.", "Give a sense of progress and, optionally, a reward on level-up."),
        when: T("Cuando los puntos acumulados deben traducirse en rangos.", "When accumulated points should translate into ranks."),
        card: "1 nivel ↔ 1 tipo de puntos, 0..1 recompensa",
        props: [
          { n: T("Nombre e identificador", "Name and ID"), req: "MANDATORY", d: T("Cómo se llama y su clave", "What it is called and its key") },
          { n: T("Tipo de puntos", "Point type"), req: "MANDATORY", d: T("De qué cuenta depende; debe existir", "Which account it depends on; must exist") },
          { n: T("Puntos mínimos", "Minimum points"), req: "MANDATORY", d: T("Umbral (0 o más) que activa el nivel", "Threshold (0 or more) that triggers the level") },
          { n: T("Recompensa", "Reward"), req: "OPTIONAL", d: T("Se concede al alcanzar el nivel", "Granted on reaching the level") }
        ],
        io: { i: T("Saldo de puntos", "Points balance"), o: T("Nivel actual de la persona", "The person's current level") },
        effect: T("No se guarda: se calcula a partir de los puntos; gana el umbral más alto alcanzado.", "It is not stored: it is calculated from points; the highest threshold reached wins."),
        func: { rel: [T("Referencia 1 tipo de puntos y 0..1 recompensa", "References 1 point type and 0..1 reward")], lifecycle: T("derivado de los puntos en cada reconstrucción", "derived from points on every rebuild"), cond: T("No se puede crear un nivel sin al menos un tipo de puntos.", "A level cannot be created without at least one point type.") },
        tech: { type: "LevelDefinition", schema: "pointTypeId FK required; minimumPoints >= 0", evidence: [{ path: G + "LevelDefinition.java", line: 5, label: "LevelDefinition" }, { path: RT, line: 284, label: "nivel derivado" }] } },

      { id: "achievement", n: T("Logro", "Achievement"), kind: "concept", req: "REPEATABLE",
        mean: T("Un hito que se desbloquea una vez.", "A milestone that is unlocked once."),
        purpose: T("Reconocer comportamientos concretos; puede ser oculto hasta desbloquearse.", "Recognise specific behaviours; may be hidden until unlocked."),
        when: T("Para hitos puntuales que no son una cantidad de puntos.", "For one-off milestones that are not a points amount."),
        card: "1 logro ↔ 0..1 recompensa; 0..N reglas",
        props: [
          { n: T("Nombre e identificador", "Name and ID"), req: "MANDATORY", d: T("Nombre y clave", "Name and key") },
          { n: T("Descripción", "Description"), req: "OPTIONAL", d: T("Qué hay que hacer", "What must be done") },
          { n: T("Recompensa", "Reward"), req: "OPTIONAL", d: T("Se concede con el logro", "Granted with the achievement") },
          { n: T("Oculto", "Hidden"), req: "OPTIONAL", d: T("Marca de logro secreto", "Secret-achievement flag") }
        ],
        io: { i: T("Regla que lo apunta", "Rule pointing to it"), o: T("Logro en el estado de la persona", "Achievement in the person's status") },
        effect: T("Genera un aviso de logro desbloqueado.", "Generates an achievement-unlocked notice."),
        func: { rel: [T("Referenciado por 0..N reglas; referencia 0..1 recompensa", "Referenced by 0..N rules; references 0..1 reward")], lifecycle: T("definido → desbloqueado al aplicarse una regla activa", "defined → unlocked when an active rule applies"), cond: T("Una penalización no puede desbloquearlo.", "A penalty cannot unlock it.") },
        tech: { type: "AchievementDefinition", schema: "hidden boolean", evidence: [{ path: G + "AchievementDefinition.java", line: 5, label: "AchievementDefinition" }] } },

      { id: "challenge", n: T("Reto", "Challenge"), kind: "concept", req: "REPEATABLE",
        mean: T("Una tarea que se completa tras repetir cierta acción un número de veces.", "A task completed after repeating an action a number of times."),
        purpose: T("Medir avance acumulado hacia una meta.", "Measure accumulated progress toward a goal."),
        when: T("Cuando el objetivo es «hacer X veces» y no una suma de puntos.", "When the goal is 'do X times' rather than a points sum."),
        card: "1 reto ↔ 0..1 recompensa; 0..N reglas",
        props: [
          { n: T("Nombre e identificador", "Name and ID"), req: "MANDATORY", d: T("Nombre y clave", "Name and key") },
          { n: T("Acciones objetivo", "Target count"), req: "MANDATORY", d: T("Mayor que 0", "Above 0") },
          { n: T("Descripción", "Description"), req: "OPTIONAL", d: T("Texto explicativo", "Explanatory text") },
          { n: T("Recompensa", "Reward"), req: "OPTIONAL", d: T("Se concede al completarlo", "Granted on completion") }
        ],
        io: { i: T("Cada aplicación activa de su regla", "Each active application of its rule"), o: T("Avance y, al llegar al objetivo, recompensa", "Progress and, at the target, a reward") },
        effect: T("Avanza una unidad por entrada activa y avisa del avance y de la finalización.", "Advances one unit per active entry and notifies progress and completion."),
        func: { rel: [T("Referenciado por 0..N reglas; referencia 0..1 recompensa", "Referenced by 0..N rules; references 0..1 reward")], lifecycle: T("en curso → completado al alcanzar el objetivo", "in progress → completed on reaching the target"), cond: T("Una reversión resta avance porque la entrada deja de estar activa.", "A reversal removes progress because the entry stops being active.") },
        tech: { type: "ChallengeDefinition", schema: "targetCount > 0", evidence: [{ path: G + "ChallengeDefinition.java", line: 10, label: "ChallengeDefinition" }, { path: RT, line: 268, label: "avance por entrada activa" }] } },

      { id: "reward", n: T("Recompensa", "Reward"), kind: "concept", req: "REPEATABLE",
        mean: T("Algo que se concede: un contenido libre descrito por el autor.", "Something granted: free-form content described by the author."),
        purpose: T("Dar un premio concreto reutilizable por niveles, logros, retos o reglas.", "Provide a concrete prize reusable by levels, achievements, challenges or rules."),
        when: T("Cuando un nivel, logro o reto debe dar algo más que reconocimiento.", "When a level, achievement or challenge should give more than recognition."),
        card: "1 recompensa ↔ 0..N niveles, logros, retos y reglas",
        props: [
          { n: T("Nombre e identificador", "Name and ID"), req: "MANDATORY", d: T("Nombre y clave", "Name and key") },
          { n: T("Descripción", "Description"), req: "OPTIONAL", d: T("Texto explicativo", "Explanatory text") },
          { n: T("Contenido", "Payload"), req: "OPTIONAL", d: T("Objeto JSON libre", "Free JSON object") }
        ],
        io: { i: T("Nivel, logro, reto o regla que la concede", "Level, achievement, challenge or rule that grants it"), o: T("Recompensa en el estado de la persona", "Reward in the person's status") },
        effect: T("Se avisa una sola vez por recompensa.", "Notified only once per reward."),
        func: { rel: [T("Referenciada por 0..N elementos", "Referenced by 0..N elements")], lifecycle: T("definida → concedida", "defined → granted"), cond: T("Una regla de penalización no puede conceder recompensa; una recompensa ligada a una corrección sin verificar se señala.", "A penalty rule cannot grant a reward; a reward tied to an unverified quick fix is flagged.") },
        tech: { type: "RewardDefinition", schema: "payload Map", evidence: [{ path: G + "RewardDefinition.java", line: 8, label: "RewardDefinition" }] } },

      { id: "rule", n: T("Regla de gamificación", "Gamification rule"), kind: "concept", req: "REPEATABLE",
        mean: T("Dice qué evento provoca qué efecto, bajo qué condición y con qué límites.", "States which event causes which effect, under which condition and with which limits."),
        purpose: T("Es el motor: sin reglas, nada se concede.", "It is the engine: without rules nothing is granted."),
        when: T("Cuando se quiere premiar o penalizar un suceso concreto.", "When you want to reward or penalise a specific occurrence."),
        card: "1 regla ↔ 1 evento; 0..1 tipo de puntos, 0..1 logro, 0..1 reto, 0..1 recompensa",
        props: [
          { n: T("Evento verificado", "Verified event"), req: "MANDATORY", d: T("Del catálogo reservado o de los propios", "From the reserved or custom catalog") },
          { n: T("Condición", "Condition"), req: "OPTIONAL", d: T("Ruta + operador + valor esperado sobre el evento", "Path + operator + expected value on the event") },
          { n: T("Efecto", "Effect"), req: "ALTERNATIVE", d: T("Premio o penalización", "Award or penalty") },
          { n: T("Puntos", "Points"), req: "OPTIONAL", d: T("Cantidad; el signo lo marca el efecto", "Amount; the sign follows the effect") },
          { n: T("Política de repetición", "Repeat policy"), req: "OPTIONAL", d: T("Por defecto una vez por evento", "Default once per event") },
          { n: T("Máximo por persona", "Max per person"), req: "OPTIONAL", d: T("Tope de concesiones por persona", "Cap on grants per person") },
          { n: T("Activa", "Enabled"), req: "OPTIONAL", d: T("Permite apagarla sin borrarla", "Lets you switch it off without deleting") }
        ],
        io: { i: T("Evento recibido", "Event received"), o: T("Entrada de historial activa o rechazada", "Active or rejected history entry") },
        effect: T("Si encaja y pasa los límites, anota una entrada activa; si no, una rechazada con el motivo.", "If it matches and passes the limits, notes an active entry; otherwise a rejected one with the reason."),
        func: { rel: [T("Referencia 1 evento y hasta 1 de cada: tipo de puntos, logro, reto, recompensa", "References 1 event and up to 1 each of point type, achievement, challenge, reward")], lifecycle: T("definida → comprobada → evaluada en cada evento", "defined → checked → evaluated on each event"), cond: T("La comprobación avisa si no tiene efecto, si no tiene límite alguno o si falta espera o límite diario según su política.", "The check warns if it has no effect, if it has no limit at all, or if the wait or daily limit is missing for its policy.") },
        tech: { type: "GamificationRule", schema: "operator EQUALS default; effectKind derived from sign; negative cooldown/limits throw", evidence: [{ path: G + "GamificationRule.java", line: 8, label: "GamificationRule" }, { path: G + "GamificationRule.java", line: 22, label: "repeatPolicy por defecto" }] } },

      { id: "condition", n: T("Condición de la regla", "Rule condition"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Filtro opcional sobre el contenido del evento.", "Optional filter over the event content."),
        purpose: T("Que la regla solo actúe cuando el evento cumple algo más que su tipo.", "Make the rule act only when the event meets more than its type."),
        when: T("Cuando el mismo tipo de evento debe tratarse distinto según sus datos.", "When the same event type must be treated differently depending on its data."),
        card: "1 regla ↔ 0..1 condición",
        variants: [
          { n: T("Es igual a", "Equals"), d: T("Por defecto; compara con el valor esperado", "Default; compares with the expected value"), req: "ALTERNATIVE" },
          { n: T("Es distinto de", "Not equals"), d: T("Se cumple si el valor difiere", "Met if the value differs"), req: "ALTERNATIVE" },
          { n: T("Existe", "Exists"), d: T("Se cumple si el dato está presente; no necesita valor", "Met if the data is present; needs no value"), req: "ALTERNATIVE" },
          { n: T("No existe", "Not exists"), d: T("Se cumple si falta; el motor lo admite pero el diseñador aún no lo ofrece", "Met if missing; supported by the engine but not yet offered by the designer"), req: "ALTERNATIVE" },
          { n: T("Contiene", "Contains"), d: T("El valor contiene el esperado", "The value contains the expected one"), req: "ALTERNATIVE" },
          { n: T("Mayor que", "Greater than"), d: T("Comparación numérica", "Numeric comparison"), req: "ALTERNATIVE" },
          { n: T("Menor que", "Less than"), d: T("Comparación numérica", "Numeric comparison"), req: "ALTERNATIVE" }
        ],
        props: [
          { n: T("Ruta", "Path"), req: "OPTIONAL", d: T("Qué dato mirar; una ruta sin origen lee el contenido del evento", "Which data to look at; a bare path reads the event content") },
          { n: T("Operador", "Operator"), req: "CONDITIONAL", d: T("Solo cuando hay ruta", "Only when there is a path") },
          { n: T("Valor esperado", "Expected value"), req: "CONDITIONAL", d: T("Valor JSON; oculto para «existe»", "JSON value; hidden for 'exists'") }
        ],
        io: { i: T("Evento", "Event"), o: T("Encaja o no", "Matches or not") },
        effect: T("Primero se comprueba el tipo de evento; sin ruta basta el tipo.", "The event type is checked first; with no path the type suffices."),
        func: { rel: [T("Pertenece a 1 regla", "Belongs to 1 rule")], lifecycle: T("evaluada en cada evento del tipo indicado", "evaluated on each event of the given type"), cond: T("Orígenes de ruta admitidos: contenido, metadatos, actor, sujeto, contexto y origen; también identificador, tipo, correlación y causa del evento.", "Supported path roots: payload, metadata, actor, subject, context and source; also event id, type, correlation and causation.") },
        tech: { type: "GamificationEventMatcher + expression kernel", schema: "conditionPath/operator/expectedValue", evidence: [{ path: G + "runtime/GamificationEventMatcher.java", line: 43, label: "operadores" }, { path: G + "runtime/GamificationEventMatcher.java", line: 65, label: "normalización de rutas" }] } },

      { id: "effect", n: T("Efecto de la regla", "Rule effect"), kind: "variant-set", req: "ALTERNATIVE",
        mean: T("Si la regla da puntos o los quita.", "Whether the rule gives or takes points."),
        purpose: T("Distinguir premios de penalizaciones.", "Tell awards from penalties."),
        when: T("Al crear la regla.", "When creating the rule."),
        card: "1 regla ↔ 1 efecto",
        variants: [
          { n: T("Premio", "Award"), d: T("Puntos positivos; admite logro, reto y recompensa", "Positive points; allows achievement, challenge and reward"), req: "ALTERNATIVE" },
          { n: T("Penalización", "Penalty"), d: T("Puntos negativos; no admite logro, reto ni recompensa", "Negative points; allows no achievement, challenge or reward"), req: "ALTERNATIVE" }
        ],
        props: [
          { n: T("Cantidad absoluta", "Absolute amount"), req: "MANDATORY", d: T("El diseñador la guarda en positivo y firma según el efecto", "The designer stores it positive and signs it by effect") }
        ],
        io: { i: T("Regla que encaja", "Matching rule"), o: T("Variación de puntos con signo", "Signed points change") },
        effect: T("El efecto por defecto se deduce del signo de los puntos.", "The default effect is derived from the sign of the points."),
        func: { rel: [T("Un efecto por regla", "One effect per rule")], lifecycle: T("fijado al definir la regla", "fixed when defining the rule"), cond: T("Cambiar a penalización en el diseñador limpia logro, reto y recompensa.", "Switching to penalty in the designer clears achievement, challenge and reward.") },
        tech: { type: "GamificationEffectKind", schema: "AWARD | PENALTY", evidence: [{ path: G + "GamificationRule.java", line: 23, label: "effectKind derivado" }] } },

      { id: "repeat", n: T("Política de repetición", "Repeat policy"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Cuántas veces y con qué cadencia puede aplicarse una regla.", "How many times and how often a rule may apply."),
        purpose: T("Evitar abusos y premios infinitos.", "Prevent abuse and endless prizes."),
        when: T("Al definir una regla.", "When defining a rule."),
        card: "1 regla ↔ 1 política",
        variants: [
          { n: T("Una sola vez", "Once"), d: T("La regla se aplica una única vez por persona", "The rule applies a single time per person"), req: "ALTERNATIVE" },
          { n: T("Una vez por evento", "Once per event"), d: T("Por defecto; cada evento cuenta una vez", "Default; each event counts once"), req: "ALTERNATIVE" },
          { n: T("Sin límite", "Unlimited"), d: T("Sin restricciones propias; la comprobación la marca como sin límite", "No own restrictions; the check flags it as unbounded"), req: "ALTERNATIVE" },
          { n: T("Con espera", "With wait"), d: T("Exige segundos entre concesiones", "Requires seconds between grants"), req: "ALTERNATIVE" },
          { n: T("Límite diario", "Daily limit"), d: T("Exige un máximo por día (día UTC)", "Requires a daily maximum (UTC day)"), req: "ALTERNATIVE" }
        ],
        props: [
          { n: T("Segundos de espera", "Wait seconds"), req: "CONDITIONAL", d: T("Solo con espera", "Only with wait") },
          { n: T("Máximo por persona", "Max per person"), req: "OPTIONAL", d: T("Tope total", "Overall cap") },
          { n: T("Máximo por día", "Max per day"), req: "CONDITIONAL", d: T("Solo con límite diario", "Only with daily limit") }
        ],
        io: { i: T("Historial previo de la persona", "The person's earlier history"), o: T("Aplicar o rechazar con motivo", "Apply or reject with a reason") },
        effect: T("Los rechazos se anotan con 0 puntos y su motivo.", "Rejections are noted with 0 points and their reason."),
        func: { rel: [T("Una política por regla", "One policy per rule")], lifecycle: T("evaluada en cada evento", "evaluated on each event"), cond: T("Valores negativos de espera o límites no son válidos.", "Negative wait or limit values are invalid.") },
        tech: { type: "GamificationRepeatPolicy", schema: "ONCE | ONCE_PER_EVENT | UNLIMITED | COOLDOWN | DAILY_LIMIT", evidence: [{ path: RT, line: 216, label: "rechazos" }] } },

      { id: "ledger", n: T("Historial de concesiones", "Award history"), kind: "derived", req: "DERIVED",
        mean: T("El registro que no se borra de todo lo que se concedió, rechazó o revirtió.", "The never-erased record of everything granted, rejected or reversed."),
        purpose: T("Es la fuente de verdad: de él se reconstruyen el estado y los avisos.", "It is the source of truth: status and notices are rebuilt from it."),
        when: T("Se genera solo al procesar eventos.", "Generated automatically on processing events."),
        card: "1 evento ↔ 0..N entradas (una por regla)",
        variants: [
          { n: T("Activa", "Active"), d: T("Concesión vigente", "Current grant"), req: "ALTERNATIVE" },
          { n: T("Compensada", "Compensated"), d: T("Anulada por una reversión", "Cancelled by a reversal"), req: "ALTERNATIVE" },
          { n: T("Rechazada", "Rejected"), d: T("No aplicada; 0 puntos y motivo", "Not applied; 0 points and a reason"), req: "ALTERNATIVE" }
        ],
        props: [
          { n: T("Persona y evento origen", "Person and source event"), req: "DERIVED", d: T("A quién y por qué", "To whom and why") },
          { n: T("Variación de puntos", "Points change"), req: "DERIVED", d: T("Con signo", "Signed") },
          { n: T("Entrada que revierte", "Entry reversed"), req: "OPTIONAL", d: T("Solo en las entradas de reversión", "Only in reversal entries") }
        ],
        io: { i: T("Eventos procesados", "Processed events"), o: T("Estado y avisos", "Status and notices") },
        effect: T("Se recorta al máximo configurado; al recortar se pierde el respaldo contra repeticiones de los eventos más antiguos.", "Trimmed to the configured maximum; trimming loses the anti-replay backing of the oldest events."),
        func: { rel: [T("Cada entrada referencia 1 evento, 1 regla y opcionalmente puntos, logro, reto, recompensa", "Each entry references 1 event, 1 rule and optionally points, achievement, challenge, reward")], lifecycle: T("activa → compensada", "active → compensated"), cond: T("Solo se escribe tras éxito; si falla guardar el historial, se deshace el estado.", "Written only on success; if saving the history fails, the status is rolled back.") },
        tech: { type: "GamificationLedgerEntry", schema: "id ledger:<uuid>; status ACTIVE|COMPENSATED|REJECTED", evidence: [{ path: G + "GamificationLedgerEntry.java", line: 9, label: "GamificationLedgerEntry" }] } },

      { id: "state", n: T("Estado de la persona", "Person status"), kind: "derived", req: "DERIVED",
        mean: T("Lo que cada persona tiene ahora: puntos, nivel, logros, avance de retos y recompensas.", "What each person has now: points, level, achievements, challenge progress and rewards."),
        purpose: T("Responder a «¿cómo voy?» sin recalcular a mano.", "Answer 'how am I doing?' without manual calculation."),
        when: T("Se reconstruye tras cada evento procesado.", "Rebuilt after every processed event."),
        card: "1 metamodelo ↔ 0..N personas",
        props: [
          { n: T("Puntos por tipo", "Points per type"), req: "DERIVED", d: T("Suma del historial, recortada al mínimo", "History sum, clipped to the minimum") },
          { n: T("Nivel por tipo", "Level per type"), req: "DERIVED", d: T("El más alto alcanzado", "The highest reached") },
          { n: T("Logros y recompensas", "Achievements and rewards"), req: "DERIVED", d: T("Conseguidos", "Obtained") },
          { n: T("Avance de retos", "Challenge progress"), req: "DERIVED", d: T("Una unidad por entrada activa", "One unit per active entry") }
        ],
        io: { i: T("Historial", "History"), o: T("Estado actual", "Current status") },
        effect: T("Se persiste antes que el historial; si el historial falla, se revierte.", "Persisted before the history; rolled back if the history fails."),
        func: { rel: [T("Derivado del historial y de las definiciones", "Derived from the history and the definitions")], lifecycle: T("reconstruido en cada evento; vaciado al reiniciar", "rebuilt on every event; cleared on reset"), cond: T("Los puntos no bajan del saldo mínimo.", "Points never fall below the minimum balance.") },
        tech: { type: "GamificationRuntimeState / GamificationActorState", schema: "actors map + processedAwardKeys", evidence: [{ path: RT, line: 257, label: "rebuildState" }] } },

      { id: "feedback", n: T("Aviso a la persona", "Person notice"), kind: "derived", req: "DERIVED",
        mean: T("Mensajes que explican lo ocurrido: puntos, penalización, logro, nivel, reto, recompensa, rechazo o reversión.", "Messages explaining what happened: points, penalty, achievement, level, challenge, reward, rejection or reversal."),
        purpose: T("Dar retroalimentación inmediata y comprensible.", "Give immediate, understandable feedback."),
        when: T("La sesión de edición los consulta periódicamente.", "The editing session polls for them periodically."),
        card: "1 entrada de historial ↔ 0..N avisos",
        variants: [
          { n: T("Éxito", "Success"), d: T("Puntos concedidos, logro, reto completado, recompensa, nivel", "Points granted, achievement, challenge done, reward, level"), req: "ALTERNATIVE" },
          { n: T("Información", "Info"), d: T("Avance de reto", "Challenge progress"), req: "ALTERNATIVE" },
          { n: T("Advertencia", "Warning"), d: T("Penalización aplicada, reversión", "Penalty applied, reversal"), req: "ALTERNATIVE" },
          { n: T("Error", "Error"), d: T("Disponible como gravedad; no la usa la proyección actual", "Available as a severity; not used by the current projection"), req: "ALTERNATIVE" }
        ],
        props: [
          { n: T("Toast", "Toast"), req: "OPTIONAL", d: T("Mensaje emergente temporal", "Temporary pop-up message") },
          { n: T("Lista de actividad", "Activity list"), req: "OPTIONAL", d: T("Histórico visible de avisos", "Visible record of notices") },
          { n: T("Acción «abrir diagnóstico»", "'Open diagnostic' action"), req: "OPTIONAL", d: T("Única acción disponible", "The only action available") }
        ],
        io: { i: T("Historial + política", "History + policy"), o: T("Toasts y actividad", "Toasts and activity") },
        effect: T("No se almacenan: se calculan al consultarlos, se descartan originales compensados y la recompensa se avisa una vez.", "Not stored: computed when requested; compensated originals are skipped and a reward is notified once."),
        func: { rel: [T("Se rigen por 1 política (global y por regla)", "Governed by 1 policy (global and per rule)")], lifecycle: T("calculado → mostrado → descartado automáticamente", "computed → shown → auto-dismissed"), cond: T("Solo toast y actividad tienen pantalla.", "Only toast and activity have a screen.") },
        tech: { type: "UserFeedbackEnvelope", schema: "kinds POINTS_AWARDED..AWARD_COMPENSATED", evidence: [{ path: FP, line: 28, label: "proyección" }, { path: "frontend/src/editor-definition/GamificationFeedbackCenter.tsx", line: 7, label: "sondeo y toasts" }] } },

      { id: "policy", n: T("Política de avisos", "Notice policy"), kind: "concept", req: "OPTIONAL",
        mean: T("Cómo, dónde y por cuánto tiempo se muestran los avisos.", "How, where and for how long notices are shown."),
        purpose: T("Evitar ruido y adaptar los avisos al equipo.", "Avoid noise and adapt notices to the team."),
        when: T("Cuando los avisos por defecto resultan excesivos o insuficientes.", "When the default notices are too many or too few."),
        card: "1 catálogo ↔ 1 política global; 1 regla ↔ 0..1 ajuste",
        props: [
          { n: T("Superficies", "Surfaces"), req: "OPTIONAL", d: T("Toast y actividad por defecto", "Toast and activity by default") },
          { n: T("Duración", "Duration"), req: "OPTIONAL", d: T("Entre 1 y 30 segundos", "Between 1 and 30 seconds") },
          { n: T("Máximo visible", "Max visible"), req: "OPTIONAL", d: T("3 por defecto", "3 by default") },
          { n: T("Agrupación y antirrepetición", "Grouping and de-duplication"), req: "OPTIONAL", d: T("Ventanas de tiempo", "Time windows") },
          { n: T("Incluir rechazos", "Include rejections"), req: "OPTIONAL", d: T("Apagado por defecto", "Off by default") },
          { n: T("Mostrar puntos / progreso", "Show points / progress"), req: "OPTIONAL", d: T("Controles de detalle", "Detail controls") }
        ],
        io: { i: T("Claves de anotación", "Annotation keys"), o: T("Presentación de los avisos", "Notice presentation") },
        effect: T("El editor solo escribe la política global; el ajuste por regla solo se hace a mano.", "The editor only writes the global policy; per-rule tuning is manual only."),
        func: { rel: [T("Global en las definiciones; por regla en las reglas", "Global in the definitions; per rule in the rules")], lifecycle: T("editada → guardada con el catálogo", "edited → saved with the catalog"), cond: T("Un ajuste por regla pisa el global.", "A per-rule setting overrides the global one.") },
        tech: { type: "UserFeedbackPresentationPolicy", schema: "hydra.feedback.<key> / hydra.feedback.rule.<ruleId>.<key>", evidence: [{ path: G + "feedback/GamificationFeedbackPolicyResolver.java", line: 14, label: "resolución de claves" }, { path: GD + "GamificationFeedbackPolicyEditor.tsx", line: 6, label: "editor global" }] } }
    ],
    map: {
      nodes: [
        { id: "catalog", elem: "catalog", n: T("Catálogo", "Catalog"), k: "concept", req: "MANDATORY", d: T("Definiciones y reglas del metamodelo", "The metamodel's definitions and rules") },
        { id: "rule", elem: "rule", n: T("Regla", "Rule"), k: "concept", req: "REPEATABLE", d: T("Evento → efecto", "Event → effect") },
        { id: "event", n: T("Evento verificado", "Verified event"), k: "external", req: "MANDATORY", d: T("Procede de Eventos", "Comes from Events") },
        { id: "cond", elem: "condition", n: T("Condición", "Condition"), k: "concept", req: "OPTIONAL", d: T("Filtro sobre el evento", "Filter on the event") },
        { id: "award", elem: "effect", n: T("Premio", "Award"), k: "variant", req: "ALTERNATIVE", d: T("Suma puntos", "Adds points") },
        { id: "penalty", elem: "effect", n: T("Penalización", "Penalty"), k: "variant", req: "ALTERNATIVE", d: T("Resta puntos", "Subtracts points") },
        { id: "point", elem: "point", n: T("Tipo de puntos", "Point type"), k: "concept", req: "REPEATABLE", d: T("Cuenta de puntos", "Points account") },
        { id: "level", elem: "level", n: T("Nivel", "Level"), k: "concept", req: "REPEATABLE", d: T("Umbral de puntos", "Points threshold") },
        { id: "achievement", elem: "achievement", n: T("Logro", "Achievement"), k: "concept", req: "REPEATABLE", d: T("Hito único", "One-off milestone") },
        { id: "challenge", elem: "challenge", n: T("Reto", "Challenge"), k: "concept", req: "REPEATABLE", d: T("Meta de N acciones", "Goal of N actions") },
        { id: "reward", elem: "reward", n: T("Recompensa", "Reward"), k: "concept", req: "REPEATABLE", d: T("Premio concedido", "Prize granted") },
        { id: "ledger", elem: "ledger", n: T("Historial", "History"), k: "derived", req: "DERIVED", d: T("Fuente de verdad", "Source of truth") },
        { id: "state", elem: "state", n: T("Estado", "Status"), k: "derived", req: "DERIVED", d: T("Puntos, nivel, logros", "Points, level, achievements") },
        { id: "feedback", elem: "feedback", n: T("Aviso", "Notice"), k: "derived", req: "DERIVED", d: T("Toast y actividad", "Toast and activity") },
        { id: "policy", elem: "policy", n: T("Política de avisos", "Notice policy"), k: "concept", req: "OPTIONAL", d: T("Cómo se muestran", "How they are shown") }
      ],
      edges: [
        { a: "catalog", b: "rule", rel: "contains", card1: "1", card2: "0..N" },
        { a: "catalog", b: "point", rel: "contains", card1: "1", card2: "0..N" },
        { a: "catalog", b: "reward", rel: "contains", card1: "1", card2: "0..N" },
        { a: "rule", b: "event", rel: "ref", card1: "N", card2: "1", req: "MANDATORY" },
        { a: "rule", b: "cond", rel: "contains", card1: "1", card2: "0..1", req: "OPTIONAL" },
        { a: "award", b: "rule", rel: "isa" },
        { a: "penalty", b: "rule", rel: "isa" },
        { a: "rule", b: "point", rel: "ref", card1: "N", card2: "0..1", req: "OPTIONAL" },
        { a: "rule", b: "achievement", rel: "ref", card1: "N", card2: "0..1", req: "CONDITIONAL" },
        { a: "rule", b: "challenge", rel: "ref", card1: "N", card2: "0..1", req: "CONDITIONAL" },
        { a: "level", b: "point", rel: "ref", card1: "N", card2: "1", req: "MANDATORY" },
        { a: "level", b: "reward", rel: "ref", card1: "N", card2: "0..1", req: "OPTIONAL" },
        { a: "achievement", b: "reward", rel: "ref", card1: "N", card2: "0..1", req: "OPTIONAL" },
        { a: "challenge", b: "reward", rel: "ref", card1: "N", card2: "0..1", req: "OPTIONAL" },
        { a: "rule", b: "ledger", rel: "gen", card1: "1", card2: "0..N" },
        { a: "ledger", b: "state", rel: "gen", card1: "1", card2: "1" },
        { a: "ledger", b: "feedback", rel: "gen", card1: "1", card2: "0..N" },
        { a: "policy", b: "feedback", rel: "ref", card1: "1", card2: "N", req: "OPTIONAL" }
      ]
    },
    define: [
      { id: "guided", n: T("Modo guiado", "Guided mode"), kind: "wizard", req: "ALTERNATIVE", elems: ["point", "rule"],
        does: T("Se abre la pestaña «Guiado» del diseñador de gamificación de un metamodelo guardado. La pantalla propone los pasos en orden: primero tipos de puntos, luego recompensas, logros y retos, y al final reglas. En cada paso se crea el elemento y se rellena su ficha antes de continuar.",
                "Open the 'Guided' tab of a saved metamodel's gamification designer. The screen proposes the steps in order: point types first, then rewards, achievements and challenges, and finally rules. At each step you create the element and fill in its card before moving on."),
        func: T("Es solo una forma ordenada de recorrer los mismos elementos; no añade otras reglas.", "It is only an ordered way through the same elements; it adds no other rules."),
        tech: T(DES + " (vista GUIDED, línea 604/695)", DES + " (GUIDED view, lines 604/695)"), proc: "gamification_author_guided" },
      { id: "form", n: T("Formulario de elementos", "Element forms"), kind: "form", req: "ALTERNATIVE", elems: ["point", "level", "achievement", "challenge", "reward", "rule", "condition", "effect", "repeat"],
        does: T("En la pestaña «Formulario» (la que se abre por defecto) se elige un tipo de elemento de la paleta y se crea. Cada tipo tiene su ficha: p. ej. en una regla se elige el evento verificado, se escribe una condición opcional con su operador y valor, se escoge el tipo de puntos, premio o penalización, la política de repetición y los límites. Los campos de espera y de máximo diario solo aparecen con la política que los necesita.",
                "In the 'Form' tab (opened by default) you pick an element type from the palette and create it. Each type has its own card: in a rule you choose the verified event, write an optional condition with operator and value, pick the point type, award or penalty, repeat policy and limits. The wait and daily-maximum fields only appear for the policy that needs them."),
        func: T("Las reglas nuevas nacen con un evento de violación resuelta, el primer tipo de puntos, 1 punto, premio, una vez por evento y activa. Un nivel exige un tipo de puntos existente. Renombrar propaga las referencias, borrar limpia lo que apuntaba al elemento y se puede duplicar.", "New rules start with a violation-resolved event, the first point type, 1 point, award, once per event and enabled. A level needs an existing point type. Renaming propagates references, deleting clears what pointed at the element, and elements can be duplicated."),
        tech: T(PARTS + ":86-174; GamificationVisualEditor.tsx:84-98; gamificationFactory.ts:11-16; gamificationDesignerModel.ts:41-125", PARTS + ":86-174; GamificationVisualEditor.tsx:84-98; gamificationFactory.ts:11-16; gamificationDesignerModel.ts:41-125"), proc: "gamification_author_form" },
      { id: "canvas", n: T("Conexiones en el lienzo", "Canvas connections"), kind: "canvas", req: "ALTERNATIVE", elems: ["rule", "point", "level", "achievement", "challenge", "reward"],
        does: T("En la pestaña «Diagrama» se arrastra de un elemento a otro para conectarlos. Las conexiones tienen tipo y solo se permiten las que tienen sentido: evento → regla, regla → tipo de puntos, regla → logro, reto o recompensa, tipo de puntos → nivel, y nivel, logro o reto → recompensa. Al quitar una relación se vacía el campo que la sostenía.",
                "In the 'Diagram' tab you drag from one element to another to connect them. Connections are typed and only meaningful ones are allowed: event → rule, rule → point type, rule → achievement, challenge or reward, point type → level, and level, achievement or challenge → reward. Removing a relation clears the field that held it."),
        func: T("Cada conexión escribe el campo de referencia correspondiente; el diagrama se guarda aparte de las definiciones.", "Each connection writes the matching reference field; the diagram is saved separately from the definitions."),
        tech: T("gamificationConnections.ts:11-47; " + DES + ":451-463", "gamificationConnections.ts:11-47; " + DES + ":451-463"), proc: "gamification_author_canvas" },
      { id: "source", n: T("Edición del JSON fuente", "Source JSON editing"), kind: "text", req: "ALTERNATIVE", elems: ["catalog", "rule", "policy"],
        does: T("En la pestaña «Fuente» se edita directamente el JSON de definiciones y reglas y se pulsa aplicar. Antes de aceptarlo se hace una comprobación local de errores bloqueantes y se exige que el identificador del metamodelo coincida.",
                "In the 'Source' tab you edit the definitions and rules JSON directly and press apply. Before accepting it, a local check for blocking errors runs and the metamodel identifier must match."),
        func: T("Es el único sitio donde se pueden poner ajustes de aviso por regla.", "It is the only place where per-rule notice settings can be entered."),
        tech: T(DES + ":307-329; gamificationValidation.ts:2-14", DES + ":307-329; gamificationValidation.ts:2-14"), proc: "gamification_author_source" },
      { id: "policy", n: T("Editor de la política de avisos", "Notice policy editor"), kind: "form", req: "OPTIONAL", elems: ["policy"],
        does: T("Un panel del diseñador permite ajustar la política de avisos global: superficies, duración, máximo visible, ventanas de agrupación, mostrar puntos o progreso e incluir rechazos. Al guardar el catálogo, los valores viajan con él.",
                "A designer panel lets you tune the global notice policy: surfaces, duration, max visible, grouping windows, show points or progress and include rejections. When the catalog is saved, the values travel with it."),
        func: T("Solo cubre la política global; no hay pantalla para ajustes por regla.", "Covers the global policy only; there is no screen for per-rule settings."),
        tech: T("GamificationFeedbackPolicyEditor.tsx:6-19", "GamificationFeedbackPolicyEditor.tsx:6-19"), proc: "gamification_author_policy" },
      { id: "ledger", n: T("Vista de historial y avisos", "History and notices view"), kind: "direct", req: "OPTIONAL", elems: ["ledger", "state", "feedback"],
        does: T("En la pestaña «Historial» se ven las personas con su estado, la lista de avisos y la tabla de entradas. Se puede actualizar la vista o reiniciar todo el estado y el historial del metamodelo.",
                "In the 'History' tab you see people with their status, the notices list and the entries table. You can refresh the view or reset the metamodel's entire status and history."),
        func: T("Solo lectura salvo el reinicio, que borra estado e historial.", "Read-only except for reset, which clears status and history."),
        tech: T(PARTS + ":176-179; " + DES + ":787; api.ts:12-14", PARTS + ":176-179; " + DES + ":787; api.ts:12-14"), proc: "gamification_author_ledger" }
    ],
    rel: [
      { to: "events", type: "event", dir: "in",
        u: T("Escucha los eventos verificados para decidir qué conceder y publica los suyos.", "Listens to verified events to decide what to grant and publishes its own."),
        f: T("Cada regla se liga a un tipo de evento; los cuatro eventos de gamificación (concesión, penalización, rechazo, reversión) están en el catálogo reservado.", "Each rule binds to an event type; the four gamification events (grant, penalty, rejection, reversal) are in the reserved catalog."),
        t: T("Suscripción al bus canónico en GamificationRuntimeService.java:82-91; ReservedEventCatalog.java:13; la comprobación usa definiciones de eventos y el catálogo reservado (GamificationChecker.java:42-43).", "Canonical bus subscription in GamificationRuntimeService.java:82-91; ReservedEventCatalog.java:13; the checker uses event definitions and the reserved catalog (GamificationChecker.java:42-43).") },
      { to: "events", type: "event", dir: "out",
        u: T("Avisa de lo que concede, penaliza, rechaza o revierte para que otros reaccionen.", "Announces what it grants, penalises, rejects or reverses so others can react."),
        f: T("Los eventos derivados llevan profundidad creciente para evitar bucles.", "Derived events carry growing depth to avoid loops."),
        t: T("GamificationRuntimeEventFactory.java:17-38: contexto GAMIFICATION, sujeto GAMIFICATION_RULE, origen gamification-runtime.", "GamificationRuntimeEventFactory.java:17-38: GAMIFICATION context, GAMIFICATION_RULE subject, source gamification-runtime.") },
      { to: "metamodel", type: "required", dir: "in",
        u: T("Cada catálogo pertenece a un metamodelo guardado.", "Every catalog belongs to a saved metamodel."),
        f: T("Sin metamodelo guardado no se muestra el diseñador de gamificación.", "Without a saved metamodel the gamification designer is not shown."),
        t: T("Documentos separados por metamodelo; el servicio de metamodelo aporta el contexto de expresión (GamificationRuntimeService.java:41,213).", "Per-metamodel documents; the metamodel service provides the expression context (GamificationRuntimeService.java:41,213).") },
      { to: "validation", type: "optional", dir: "in",
        u: T("Resolver o reabrir una violación puede dar puntos o revertirlos.", "Resolving or reopening a violation may give points or reverse them."),
        f: T("El evento de violación resuelta es el predeterminado de las reglas nuevas.", "The violation-resolved event is the default of new rules."),
        t: T("Evento por defecto core.validation.violation-resolved; reversión con core.validation.violation-reopened. No hay dependencia inversa desde el paquete de validación.", "Default event core.validation.violation-resolved; reversal with core.validation.violation-reopened. No reverse dependency from the validation package.") },
      { to: "model_editor", type: "conditional", dir: "in",
        u: T("La sesión de edición emite los eventos y activa la gamificación si se pide.", "The editing session emits events and enables gamification if requested."),
        f: T("Apagada por defecto; se activa con una opción de la sesión.", "Off by default; enabled with a session option."),
        t: T("runtimePolicy.gamificationEnabled monta GamificationFeedbackCenter (EditorRuntimeSession.tsx:365); ver la discrepancia de indicadores en tech.notes.", "runtimePolicy.gamificationEnabled mounts GamificationFeedbackCenter (EditorRuntimeSession.tsx:365); see the flag discrepancy in tech.notes.") }
    ]
  });

  // ------------------------------------------------------------------------------------------------
  // CAPABILITY PROCESSES
  // ------------------------------------------------------------------------------------------------
  HM.proc({ id: "gamification_define", scope: "cap", cap: "gamification", elems: ["catalog", "rule", "point"],
    n: T("Definir y guardar la gamificación", "Define and save gamification"),
    d: T("De un metamodelo guardado a un catálogo comprobado y guardado.", "From a saved metamodel to a checked and saved catalog."),
    nodes: [
      { id: "s", t: "start", cap: "gamification", l: T("Abrir el diseñador", "Open the designer"), u: T("Se abre la gamificación de un metamodelo guardado.", "Gamification of a saved metamodel is opened.") },
      { id: "def", t: "step", a: "user", r: "MANDATORY", cap: "gamification", l: T("Definir puntos, logros, retos y recompensas", "Define points, achievements, challenges and rewards"), u: T("Se crean los elementos que luego usarán las reglas.", "The elements the rules will use are created."), need: T("Un metamodelo guardado", "A saved metamodel"), out: T("Definiciones", "Definitions"), e: "point", x: { rt: "GamificationDefinitionModel", test: ["GamificationApplicationServiceCatalogTests"], ev: [{ path: G + "GamificationDefinitionModel.java", line: 10, label: "definiciones" }] } },
      { id: "lvl", t: "step", a: "user", r: "OPTIONAL", cap: "gamification", l: T("Añadir niveles", "Add levels"), u: T("Los niveles convierten puntos en rangos; requieren un tipo de puntos.", "Levels turn points into ranks; they require a point type."), e: "level", f: T("Solo si existe al menos un tipo de puntos.", "Only if at least one point type exists.") },
      { id: "rule", t: "step", a: "user", r: "MANDATORY", cap: "gamification", l: T("Escribir las reglas", "Write the rules"), u: T("Cada regla liga un evento verificado a un efecto con sus límites.", "Each rule binds a verified event to an effect with its limits."), need: T("Eventos verificados", "Verified events"), out: T("Reglas", "Rules"), e: "rule", x: { rt: "GamificationRule", test: ["GamificationPenaltyTests"], ev: [{ path: G + "GamificationRule.java", line: 8, label: "regla" }] } },
      { id: "pol", t: "step", a: "user", r: "OPTIONAL", cap: "gamification", l: T("Ajustar la política de avisos", "Tune the notice policy"), u: T("Se decide cuánto y dónde se avisa a las personas.", "Decide how much and where people are notified."), e: "policy" },
      { id: "chk", t: "step", a: "auto", cap: "gamification", l: T("Comprobar la definición", "Check the definition"), u: T("HYDRA busca duplicados, referencias que no existen, penalizaciones con recompensa y reglas sin efecto o sin límite.", "HYDRA looks for duplicates, references that do not exist, penalties with rewards and rules without effect or limit."), out: T("Diagnósticos", "Diagnostics"), x: { rt: "GamificationChecker", test: ["GamificationCheckerTests"], ev: [{ path: G + "GamificationChecker.java", line: 21, label: "checker" }] } },
      { id: "ok", t: "decision", a: "auto", cap: "gamification", l: T("¿Hay errores bloqueantes?", "Any blocking errors?"), u: T("Los avisos no impiden guardar; los errores sí.", "Warnings do not block saving; errors do.") },
      { id: "save", t: "step", a: "auto", cap: "gamification", l: T("Guardar definiciones y reglas", "Save definitions and rules"), u: T("Se guardan los dos documentos; si uno falla, se deshace el otro.", "Both documents are saved; if one fails, the other is rolled back."), s: T("catálogo: comprobado → guardado", "catalog: checked → saved"), x: { rt: "GamificationApplicationService", test: ["GamificationApplicationServiceCatalogTests"], ev: [{ path: G + "GamificationApplicationService.java", line: 37, label: "guardado" }] } },
      { id: "dia", t: "step", a: "auto", r: "OPTIONAL", cap: "gamification", l: T("Guardar el diagrama", "Save the diagram"), u: T("La disposición visual se guarda por separado.", "The visual layout is saved separately."), x: { rt: "GamificationDiagramRepository", test: ["GamificationDiagramRepositoryTests"], ev: [{ path: DES, line: 355, label: "diagrama aparte" }] } },
      { id: "art", t: "artifact", cap: "gamification", l: T("Catálogo guardado", "Saved catalog"), u: T("Listo para procesar eventos si la sesión lo activa.", "Ready to process events if the session enables it.") },
      { id: "e", t: "end", cap: "gamification", l: T("Fin", "End") }
    ],
    edges: [["s", "def"], ["def", "lvl", "opt"], ["lvl", "rule"], ["def", "rule"], ["rule", "pol", "opt"], ["pol", "chk"], ["rule", "chk"], ["chk", "ok"], ["ok", "save", "next", T("No", "No")], ["ok", "rule", "loop", T("Sí: corregir", "Yes: fix")], ["save", "dia", "opt"], ["dia", "art"], ["save", "art"], ["art", "e"]] });

  HM.proc({ id: "gamification_award", scope: "cap", cap: "gamification", elems: ["rule", "ledger", "state", "feedback", "repeat"],
    n: T("Procesar un evento y conceder", "Process an event and grant"),
    d: T("Desde un evento recibido hasta el historial, el estado, los avisos y los eventos derivados.", "From a received event to the history, status, notices and derived events."),
    nodes: [
      { id: "s", t: "start", cap: "events", l: T("Llega un evento", "An event arrives"), u: T("Procede del bus de eventos o de una llamada directa.", "It comes from the event bus or a direct call.") },
      { id: "gate", t: "decision", a: "auto", cap: "gamification", l: T("¿Gamificación activa?", "Gamification enabled?"), u: T("Se ignora si está apagada para el sistema o la sesión, si el evento no trae metamodelo o si ya va muy encadenado.", "Ignored if off for the system or session, if the event has no metamodel or is chained too deeply."), f: T("Profundidad máxima 16; falta de metamodelo descarta el evento.", "Maximum depth 16; a missing metamodel drops the event."), x: { rt: "GamificationRuntimeService", test: ["GamificationPenaltyRuntimeTests"], ev: [{ path: RT, line: 82, label: "subscribe" }, { path: RT, line: 98, label: "guarda de profundidad" }] } },
      { id: "idem", t: "decision", a: "auto", cap: "gamification", l: T("¿Evento ya procesado?", "Event already processed?"), u: T("Si ya hay entradas de ese evento, no se hace nada: así un evento repetido no concede dos veces.", "If entries for that event already exist, nothing happens: a repeated event never grants twice."), x: { rt: "GamificationRuntimeService", test: ["GamificationPenaltyRuntimeTests"], ev: [{ path: RT, line: 102, label: "idempotencia" }] } },
      { id: "rev", t: "decision", a: "auto", cap: "gamification", l: T("¿Es una reversión?", "Is it a reversal?"), u: T("Se comprueba primero si el evento deshace algo concedido antes.", "First check whether the event undoes something granted earlier."), f: T("Eventos de corrección deshecha o violación reabierta, o con referencia a un evento o diagnóstico anterior.", "Quick fix undone or violation reopened events, or events referencing an earlier event or diagnostic."), x: { rt: "GamificationRuntimeService", ev: [{ path: RT, line: 180, label: "disparadores de compensación" }] } },
      { id: "comp", t: "step", a: "auto", cap: "gamification", l: T("Revertir lo concedido", "Reverse what was granted"), u: T("Se anulan las entradas activas relacionadas y se anota una entrada contraria.", "Related active entries are cancelled and an opposite entry is noted."), s: T("entrada: activa → compensada", "entry: active → compensated"), e: "ledger" },
      { id: "match", t: "step", a: "auto", cap: "gamification", l: T("Buscar reglas que encajan", "Find matching rules"), u: T("Se evalúan solo las reglas activas: primero el tipo de evento y después la condición.", "Only enabled rules are evaluated: first the event type and then the condition."), e: "condition", x: { rt: "GamificationEventMatcher", test: ["GamificationNeutralExpressionConditionTests"], ev: [{ path: G + "runtime/GamificationEventMatcher.java", line: 30, label: "matcher" }] } },
      { id: "lim", t: "decision", a: "auto", cap: "gamification", l: T("¿Pasa los límites?", "Within limits?"), u: T("Se aplica la política de repetición y los máximos por persona y por día.", "The repeat policy and the per-person and per-day maximums are applied."), e: "repeat", f: T("Motivos de rechazo: ya aplicada, máximo por persona, máximo por día, límite diario, espera.", "Rejection reasons: already applied, per-person max, per-day max, daily limit, wait."), x: { rt: "GamificationRuntimeService", test: ["GamificationPenaltyRuntimeTests"], ev: [{ path: RT, line: 216, label: "rechazos" }] } },
      { id: "min", t: "step", a: "auto", cap: "gamification", l: T("Recortar al saldo mínimo", "Clip to the minimum balance"), u: T("Una penalización no baja de lo permitido; si queda a cero se rechaza.", "A penalty does not go below what is allowed; if it ends at zero it is rejected."), e: "point", x: { rt: "GamificationRuntimeService", ev: [{ path: RT, line: 232, label: "appliedPoints" }] } },
      { id: "rej", t: "step", a: "auto", cap: "gamification", l: T("Anotar rechazo", "Note rejection"), u: T("Se guarda una entrada con 0 puntos y su motivo.", "An entry with 0 points and its reason is stored."), s: T("regla: encaja → rechazada", "rule: matched → rejected"), e: "ledger" },
      { id: "act", t: "step", a: "auto", cap: "gamification", l: T("Anotar concesión", "Note grant"), u: T("Se guarda la entrada activa con puntos, logro, reto o recompensa.", "The active entry is stored with points, achievement, challenge or reward."), s: T("regla: encaja → activa", "rule: matched → active"), e: "ledger" },
      { id: "reb", t: "step", a: "auto", cap: "gamification", l: T("Reconstruir el estado", "Rebuild the status"), u: T("Desde el historial se recalculan puntos, niveles, logros y avance de retos.", "From the history, points, levels, achievements and challenge progress are recalculated."), e: "state", x: { rt: "GamificationRuntimeService", ev: [{ path: RT, line: 257, label: "rebuildState" }] } },
      { id: "per", t: "step", a: "auto", cap: "persistence", l: T("Guardar estado e historial", "Save status and history"), u: T("Se guarda primero el estado y después el historial; si el historial falla se deshace el estado.", "Status is saved first and then history; if history fails, status is rolled back."), x: { rt: "GamificationRuntimeService", test: ["GamificationCorruptDocumentRecoveryTests"], ev: [{ path: RT, line: 166, label: "persistencia con rollback" }] } },
      { id: "fb", t: "step", a: "auto", cap: "gamification", l: T("Proyectar avisos", "Project notices"), u: T("Se prepara lo que la persona verá.", "What the person will see is prepared."), e: "feedback", x: { rt: "GamificationFeedbackProjector", test: ["GamificationFeedbackProjectorTests"], ev: [{ path: FP, line: 28, label: "proyector" }] } },
      { id: "pub", t: "step", a: "auto", cap: "events", l: T("Publicar eventos derivados", "Publish derived events"), u: T("Se publica concesión, penalización, rechazo o reversión; si publicar falla solo se registra.", "Grant, penalty, rejection or reversal is published; if publishing fails it is only logged."), x: { rt: "GamificationRuntimeEventFactory", ev: [{ path: G + "runtime/GamificationRuntimeEventFactory.java", line: 17, label: "eventos derivados" }] } },
      { id: "e", t: "end", cap: "gamification", l: T("Fin", "End") }
    ],
    edges: [["s", "gate"], ["gate", "idem", "next", T("Sí", "Yes")], ["gate", "e", "alt", T("No: se ignora", "No: ignored")],
      ["idem", "rev", "next", T("No", "No")], ["idem", "e", "alt", T("Sí: sin cambios", "Yes: no change")],
      ["rev", "comp", "next", T("Sí", "Yes")], ["rev", "match", "next", T("No", "No")],
      ["match", "lim"], ["lim", "min", "next", T("Sí", "Yes")], ["lim", "rej", "alt", T("No", "No")],
      ["min", "act"], ["min", "rej", "alt", T("Cero puntos", "Zero points")],
      ["comp", "reb"], ["act", "reb"], ["rej", "reb"], ["reb", "per"], ["per", "fb"], ["fb", "pub", "event"], ["pub", "e"]] });

  HM.proc({ id: "gamification_compensate", scope: "cap", cap: "gamification", elems: ["ledger", "state"],
    n: T("Revertir o compensar una concesión", "Reverse or compensate an award"),
    d: T("Qué ocurre cuando se deshace una corrección o se reabre una violación.", "What happens when a fix is undone or a violation is reopened."),
    nodes: [
      { id: "s", t: "start", cap: "events", l: T("Llega un evento de reversión", "A reversal event arrives"), u: T("Corrección deshecha, violación reabierta o evento que cita uno anterior.", "Fix undone, violation reopened or an event citing an earlier one.") },
      { id: "find", t: "step", a: "auto", cap: "gamification", l: T("Buscar concesiones relacionadas", "Find related grants"), u: T("Se localizan las entradas activas del evento o diagnóstico citado.", "Active entries for the cited event or diagnostic are located."), need: T("Entradas activas", "Active entries"), x: { rt: "GamificationRuntimeService", ev: [{ path: RT, line: 179, label: "compensación" }] } },
      { id: "any", t: "decision", a: "auto", cap: "gamification", l: T("¿Hay algo que revertir?", "Anything to reverse?"), u: T("Si no hay concesiones relacionadas, el evento se trata como uno normal.", "If there are no related grants, the event is treated as a normal one.") },
      { id: "mark", t: "step", a: "auto", cap: "gamification", l: T("Marcar como compensadas", "Mark as compensated"), u: T("Las entradas originales dejan de contar.", "The original entries stop counting."), s: T("entrada: activa → compensada", "entry: active → compensated"), e: "ledger" },
      { id: "neg", t: "step", a: "auto", cap: "gamification", l: T("Anotar la entrada contraria", "Note the opposite entry"), u: T("Se añade una entrada con los puntos con signo contrario, enlazada a la original.", "An entry with opposite-sign points is added, linked to the original."), out: T("Entrada de reversión", "Reversal entry"), e: "ledger" },
      { id: "skip", t: "step", a: "auto", cap: "gamification", l: T("No evaluar reglas normales", "Skip normal rules"), u: T("Para ese evento las reglas normales no se aplican.", "For that event the normal rules are not applied.") },
      { id: "reb", t: "step", a: "auto", cap: "gamification", l: T("Reconstruir estado y avisos", "Rebuild status and notices"), u: T("Se recalculan puntos, retos y niveles; el aviso de reversión sustituye al original.", "Points, challenges and levels are recalculated; the reversal notice replaces the original."), e: "state" },
      { id: "pub", t: "step", a: "auto", cap: "events", l: T("Publicar la reversión", "Publish the reversal"), u: T("Se emite el evento de compensación.", "The compensation event is emitted.") },
      { id: "art", t: "artifact", cap: "gamification", l: T("Entrada de reversión", "Reversal entry"), u: T("Queda en el historial.", "Stays in the history.") },
      { id: "e", t: "end", cap: "gamification", l: T("Fin", "End") }
    ],
    edges: [["s", "find"], ["find", "any"], ["any", "mark", "next", T("Sí", "Yes")], ["any", "e", "alt", T("No: evento normal", "No: normal event")], ["mark", "neg"], ["neg", "skip"], ["skip", "reb"], ["reb", "pub", "event"], ["pub", "art"], ["art", "e"]] });

  // ------------------------------------------------------------------------------------------------
  // AUTHORING MICRO-PROCESSES
  // ------------------------------------------------------------------------------------------------
  HM.proc({ id: "gamification_author_guided", scope: "authoring", cap: "gamification", elems: ["point", "reward", "rule"],
    n: T("Diseño con el modo guiado", "Design with guided mode"), d: T("Recorrido ordenado por los elementos.", "Ordered walk through the elements."),
    nodes: [
      { id: "s", t: "start", cap: "gamification", l: T("Abrir pestaña Guiado", "Open the Guided tab"), u: T("Solo disponible para un metamodelo guardado.", "Only available for a saved metamodel.") },
      { id: "p", t: "step", a: "user", r: "MANDATORY", cap: "gamification", l: T("Crear tipos de puntos", "Create point types"), u: T("Primer paso: sin puntos no hay niveles ni reglas de puntos.", "First step: without points there are no levels or point rules."), out: T("Tipo de puntos", "Point type"), e: "point" },
      { id: "r", t: "step", a: "user", r: "OPTIONAL", cap: "gamification", l: T("Crear recompensas, logros y retos", "Create rewards, achievements and challenges"), u: T("Se preparan los premios que las reglas podrán conceder.", "The prizes rules may grant are prepared."), e: "reward" },
      { id: "ru", t: "step", a: "user", r: "MANDATORY", cap: "gamification", l: T("Crear reglas", "Create rules"), u: T("Se liga cada evento a su efecto.", "Each event is bound to its effect."), e: "rule" },
      { id: "c", t: "step", a: "auto", cap: "gamification", l: T("Comprobar", "Check"), u: T("Se muestran errores y avisos.", "Errors and warnings are shown.") },
      { id: "d", t: "decision", a: "auto", cap: "gamification", l: T("¿Sin errores?", "No errors?"), u: T("Los errores impiden guardar.", "Errors block saving.") },
      { id: "sv", t: "step", a: "user", cap: "gamification", l: T("Guardar", "Save"), u: T("Se guardan definiciones, reglas y diagrama.", "Definitions, rules and diagram are saved.") },
      { id: "e", t: "end", cap: "gamification", l: T("Fin", "End") }
    ],
    edges: [["s", "p"], ["p", "r", "opt"], ["r", "ru"], ["p", "ru"], ["ru", "c"], ["c", "d"], ["d", "sv", "next", T("Sí", "Yes")], ["d", "ru", "loop", T("No: corregir", "No: fix")], ["sv", "e"]] });

  HM.proc({ id: "gamification_author_form", scope: "authoring", cap: "gamification", elems: ["rule", "condition", "effect", "repeat", "level"],
    n: T("Diseño con formularios", "Design with forms"), d: T("Crear y completar la ficha de cada elemento, en especial una regla.", "Create and complete each element's card, especially a rule."),
    nodes: [
      { id: "s", t: "start", cap: "gamification", l: T("Abrir pestaña Formulario", "Open the Form tab"), u: T("Es la vista por defecto del diseñador.", "It is the designer's default view.") },
      { id: "k", t: "step", a: "user", r: "MANDATORY", cap: "gamification", l: T("Elegir tipo de elemento", "Choose the element type"), u: T("Regla, tipo de puntos, nivel, logro, reto o recompensa.", "Rule, point type, level, achievement, challenge or reward."), out: T("Elemento con valores por defecto", "Element with default values"), x: { rt: "GamificationVisualEditor", test: ["GamificationDesigner.test.tsx"], ev: [{ path: GD + "GamificationVisualEditor.tsx", line: 84, label: "paleta" }] } },
      { id: "lv", t: "decision", a: "auto", cap: "gamification", l: T("¿Es un nivel sin puntos?", "A level with no point type?"), u: T("Un nivel necesita un tipo de puntos existente.", "A level needs an existing point type.") },
      { id: "m", t: "step", a: "user", r: "MANDATORY", cap: "gamification", l: T("Nombre, identificador y campos obligatorios", "Name, ID and mandatory fields"), u: T("Se completa lo imprescindible: p. ej. evento en una regla, acciones objetivo en un reto.", "The essentials are filled in: e.g. event in a rule, target count in a challenge."), e: "rule", x: { rt: "GamificationDesignerParts", ev: [{ path: PARTS, line: 154, label: "ficha de regla" }] } },
      { id: "cn", t: "step", a: "user", r: "OPTIONAL", cap: "gamification", l: T("Condición del evento", "Event condition"), u: T("Ruta, operador y valor esperado; el valor se oculta para «existe».", "Path, operator and expected value; value hidden for 'exists'."), e: "condition", f: T("Operador y valor solo con una ruta.", "Operator and value only with a path.") },
      { id: "ef", t: "step", a: "user", r: "MANDATORY", cap: "gamification", l: T("Efecto y puntos", "Effect and points"), u: T("Premio o penalización; al elegir penalización se vacían logro, reto y recompensa.", "Award or penalty; choosing penalty clears achievement, challenge and reward."), e: "effect" },
      { id: "rp", t: "step", a: "user", r: "OPTIONAL", cap: "gamification", l: T("Repetición y límites", "Repeat and limits"), u: T("Política, espera, máximo por persona y máximo por día.", "Policy, wait, per-person max and per-day max."), e: "repeat", f: T("Espera solo con espera; máximo diario solo con límite diario.", "Wait only with wait; daily max only with daily limit.") },
      { id: "ck", t: "step", a: "auto", cap: "gamification", l: T("Comprobar", "Check"), u: T("Se revisan referencias, duplicados y límites.", "References, duplicates and limits are reviewed.") },
      { id: "ok", t: "decision", a: "auto", cap: "gamification", l: T("¿Sin errores?", "No errors?"), u: T("Solo los errores bloquean.", "Only errors block.") },
      { id: "sv", t: "step", a: "user", cap: "gamification", l: T("Guardar", "Save"), u: T("Se guarda el catálogo.", "The catalog is saved.") },
      { id: "art", t: "artifact", cap: "gamification", l: T("Elemento guardado", "Saved element"), u: T("Disponible para el motor.", "Available to the engine.") },
      { id: "e", t: "end", cap: "gamification", l: T("Fin", "End") }
    ],
    edges: [["s", "k"], ["k", "lv"], ["lv", "k", "loop", T("Sí: crear antes un tipo de puntos", "Yes: create a point type first")], ["lv", "m", "next", T("No", "No")], ["m", "cn", "opt"], ["cn", "ef"], ["m", "ef"], ["ef", "rp", "opt"], ["rp", "ck"], ["ef", "ck"], ["ck", "ok"], ["ok", "sv", "next", T("Sí", "Yes")], ["ok", "m", "loop", T("No: corregir", "No: fix")], ["sv", "art"], ["art", "e"]] });

  HM.proc({ id: "gamification_author_canvas", scope: "authoring", cap: "gamification", elems: ["rule", "point", "level", "reward"],
    n: T("Diseño con conexiones en el lienzo", "Design with canvas connections"), d: T("Conectar elementos con relaciones tipadas.", "Connect elements with typed relations."),
    nodes: [
      { id: "s", t: "start", cap: "gamification", l: T("Abrir pestaña Diagrama", "Open the Diagram tab"), u: T("Se ve el catálogo como un grafo.", "The catalog is seen as a graph.") },
      { id: "n", t: "step", a: "user", r: "MANDATORY", cap: "gamification", l: T("Añadir elementos", "Add elements"), u: T("Se colocan reglas, puntos, niveles, logros, retos y recompensas.", "Rules, points, levels, achievements, challenges and rewards are placed.") },
      { id: "dr", t: "step", a: "user", r: "MANDATORY", cap: "gamification", l: T("Arrastrar una conexión", "Drag a connection"), u: T("Se une un origen con un destino.", "A source is joined to a target."), x: { rt: "gamificationConnections", test: ["gamificationConnections.test.ts"], ev: [{ path: GD + "gamificationConnections.ts", line: 11, label: "conexiones tipadas" }] } },
      { id: "ty", t: "decision", a: "auto", cap: "gamification", l: T("¿Conexión permitida?", "Connection allowed?"), u: T("Solo valen: evento→regla, regla→puntos/logro/reto/recompensa, puntos→nivel, nivel/logro/reto→recompensa.", "Only valid: event→rule, rule→points/achievement/challenge/reward, points→level, level/achievement/challenge→reward.") },
      { id: "wr", t: "step", a: "auto", cap: "gamification", l: T("Escribir la referencia", "Write the reference"), u: T("La conexión rellena el campo correspondiente del elemento.", "The connection fills the matching field of the element."), s: T("campo: vacío → referenciado", "field: empty → referenced") },
      { id: "rm", t: "step", a: "user", r: "OPTIONAL", cap: "gamification", l: T("Quitar una relación", "Remove a relation"), u: T("Se vacía el campo que la sostenía.", "The field that held it is cleared.") },
      { id: "sv", t: "step", a: "user", cap: "gamification", l: T("Guardar", "Save"), u: T("Se guarda el catálogo y, aparte, el diagrama.", "The catalog is saved and, separately, the diagram.") },
      { id: "e", t: "end", cap: "gamification", l: T("Fin", "End") }
    ],
    edges: [["s", "n"], ["n", "dr"], ["dr", "ty"], ["ty", "wr", "next", T("Sí", "Yes")], ["ty", "dr", "loop", T("No: otra conexión", "No: another connection")], ["wr", "rm", "opt"], ["rm", "sv"], ["wr", "sv"], ["sv", "e"]] });

  HM.proc({ id: "gamification_author_source", scope: "authoring", cap: "gamification", elems: ["catalog", "rule", "policy"],
    n: T("Edición del JSON fuente", "Source JSON editing"), d: T("Editar el texto y aplicarlo con comprobación local.", "Edit the text and apply it with a local check."),
    nodes: [
      { id: "s", t: "start", cap: "gamification", l: T("Abrir pestaña Fuente", "Open the Source tab"), u: T("Se muestra el JSON de definiciones y reglas.", "The definitions and rules JSON is shown.") },
      { id: "ed", t: "step", a: "user", r: "MANDATORY", cap: "gamification", l: T("Editar el JSON", "Edit the JSON"), u: T("Aquí se pueden añadir ajustes de aviso por regla.", "Per-rule notice settings can be added here."), e: "catalog" },
      { id: "ap", t: "step", a: "user", r: "MANDATORY", cap: "gamification", l: T("Aplicar", "Apply"), u: T("Se pide aceptar el texto.", "Accepting the text is requested."), x: { rt: "GamificationDesigner", ev: [{ path: DES, line: 307, label: "applySource" }] } },
      { id: "lc", t: "decision", a: "auto", cap: "gamification", l: T("¿Pasa la comprobación local?", "Passes the local check?"), u: T("Se rechazan errores bloqueantes y un metamodelo que no coincide.", "Blocking errors and a mismatching metamodel are rejected."), x: { rt: "gamificationValidation", ev: [{ path: GD + "gamificationValidation.ts", line: 2, label: "comprobación local" }] } },
      { id: "ck", t: "step", a: "auto", cap: "gamification", l: T("Comprobar en el servidor", "Check on the server"), u: T("Se piden los diagnósticos completos.", "Full diagnostics are requested.") },
      { id: "sv", t: "step", a: "user", cap: "gamification", l: T("Guardar", "Save"), u: T("Se guardan definiciones y reglas.", "Definitions and rules are saved.") },
      { id: "e", t: "end", cap: "gamification", l: T("Fin", "End") }
    ],
    edges: [["s", "ed"], ["ed", "ap"], ["ap", "lc"], ["lc", "ck", "next", T("Sí", "Yes")], ["lc", "ed", "loop", T("No: corregir", "No: fix")], ["ck", "sv"], ["sv", "e"]] });

  HM.proc({ id: "gamification_author_policy", scope: "authoring", cap: "gamification", elems: ["policy", "feedback"],
    n: T("Ajustar la política de avisos", "Tune the notice policy"), d: T("Editar los ajustes globales de aviso.", "Edit the global notice settings."),
    nodes: [
      { id: "s", t: "start", cap: "gamification", l: T("Abrir el panel de avisos", "Open the notice panel"), u: T("Se muestran los valores actuales.", "Current values are shown.") },
      { id: "sf", t: "step", a: "user", r: "OPTIONAL", cap: "gamification", l: T("Elegir superficies y duración", "Choose surfaces and duration"), u: T("Toast y actividad; duración entre 1 y 30 s.", "Toast and activity; duration between 1 and 30 s."), e: "policy", f: T("Solo toast y actividad tienen pantalla.", "Only toast and activity have a screen.") },
      { id: "mx", t: "step", a: "user", r: "OPTIONAL", cap: "gamification", l: T("Máximo visible y ventanas de agrupación", "Max visible and grouping windows"), u: T("Reduce el ruido de avisos repetidos.", "Reduces the noise of repeated notices.") },
      { id: "dt", t: "step", a: "user", r: "OPTIONAL", cap: "gamification", l: T("Mostrar puntos, progreso o rechazos", "Show points, progress or rejections"), u: T("Controla el detalle.", "Controls the detail.") },
      { id: "sv", t: "step", a: "user", cap: "gamification", l: T("Guardar el catálogo", "Save the catalog"), u: T("Los ajustes viajan con las definiciones.", "The settings travel with the definitions.") },
      { id: "art", t: "artifact", cap: "gamification", l: T("Política global guardada", "Global policy saved"), u: T("Se aplica a los próximos avisos.", "Applies to the next notices.") },
      { id: "e", t: "end", cap: "gamification", l: T("Fin", "End") }
    ],
    edges: [["s", "sf"], ["sf", "mx", "opt"], ["mx", "dt", "opt"], ["dt", "sv"], ["sf", "sv"], ["sv", "art"], ["art", "e"]] });

  HM.proc({ id: "gamification_author_ledger", scope: "authoring", cap: "gamification", elems: ["ledger", "state", "feedback"],
    n: T("Revisar historial y avisos", "Review history and notices"), d: T("Consultar y, si procede, reiniciar el estado.", "Inspect and, if needed, reset the status."),
    nodes: [
      { id: "s", t: "start", cap: "gamification", l: T("Abrir pestaña Historial", "Open the History tab"), u: T("Muestra personas, avisos y entradas.", "Shows people, notices and entries.") },
      { id: "rd", t: "step", a: "user", r: "OPTIONAL", cap: "gamification", l: T("Revisar personas, avisos y tabla", "Review people, notices and table"), u: T("Se comprueba qué se concedió y por qué.", "Check what was granted and why."), e: "ledger", x: { rt: "GamificationLedgerView", ev: [{ path: PARTS, line: 176, label: "GamificationLedgerView" }] } },
      { id: "rf", t: "step", a: "user", r: "OPTIONAL", cap: "gamification", l: T("Actualizar", "Refresh"), u: T("Se vuelve a consultar el servidor.", "The server is queried again.") },
      { id: "rs", t: "decision", a: "user", cap: "gamification", l: T("¿Reiniciar?", "Reset?"), u: T("El reinicio borra estado e historial del metamodelo.", "Reset clears the metamodel's status and history.") },
      { id: "do", t: "step", a: "auto", cap: "gamification", l: T("Borrar estado e historial", "Clear status and history"), u: T("Todo vuelve a cero.", "Everything returns to zero."), s: T("estado: con datos → vacío", "status: populated → empty"), e: "state" },
      { id: "e", t: "end", cap: "gamification", l: T("Fin", "End") }
    ],
    edges: [["s", "rd"], ["rd", "rf", "opt"], ["rf", "rs"], ["rd", "rs"], ["rs", "do", "next", T("Sí", "Yes")], ["rs", "e", "alt", T("No", "No")], ["do", "e"]] });
})();
