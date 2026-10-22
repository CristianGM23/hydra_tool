(function(){ "use strict"; var HM = window.HM, T = HM.T;
  var G = "src/main/java/com/example/editor/gamification/";
  var RT = G + "runtime/GamificationRuntimeService.java";
  var FP = G + "feedback/GamificationFeedbackProjector.java";
  var MT = G + "runtime/GamificationEventMatcher.java";
  var CTL = G + "runtime/GamificationRuntimeController.java";
  var ALL = ["user", "functional", "technical"];
  var NAMES = [
    T("Qué representa", "What it represents"), T("Por qué definirlo", "Why define it"), T("Qué lo activa", "What triggers it"),
    T("Configuración (obligatoria / opcional)", "Configuration (mandatory / optional)"), T("Relaciones", "Relationships"),
    T("Estado o efecto resultante", "Resulting state or effect"), T("Relación con eventos", "Relation to events"), T("Implementación", "Implementation")
  ];
  // f = [[es,en] x 8] in the order of NAMES; the 8th one is technical-only.
  function facets(f) {
    return f.map(function(x, i){ return { n: NAMES[i], v: T(x[0], x[1]), a: i === 7 ? ["technical"] : ALL }; });
  }
  var NA = ["No aplica.", "Not applicable."];

  // ------------------------------------------------------------------------------------------------
  // 1) Facets for the variants that already exist (index-addressed)
  // ------------------------------------------------------------------------------------------------
  var condEvt = ["Lee datos del evento verificado de la regla; antes se ha comprobado que el tipo de evento coincide.", "Reads data from the rule's verified event; the event type has already been checked to match."];
  var condAct = ["Se evalúa cada vez que llega un evento del tipo de la regla y la regla está activa; sin ruta no se evalúa nada y basta el tipo.", "Evaluated each time an event of the rule's type arrives and the rule is enabled; with no path nothing is evaluated and the type alone suffices."];
  var condRel = ["Pertenece a una única regla; comparte con ella el evento y decide si la regla llega a evaluar sus límites y su efecto.", "Belongs to exactly one rule; shares its event and decides whether the rule gets to evaluate its limits and effect."];
  var condImpl = function(t){ return ["GamificationEventMatcher normaliza la ruta (sin origen explícito se antepone payload.) y evalúa con el núcleo de expresiones; si no hay núcleo (pruebas embebidas) usa un conmutador propio. " + t, "GamificationEventMatcher normalises the path (with no explicit root payload. is prepended) and evaluates with the expression kernel; without a kernel (embedded tests) it uses its own switch. " + t]; };
  var condCfg = function(t){ return ["Ruta: opcional. Operador: solo con ruta (por defecto «es igual a»). " + t, "Path: optional. Operator: only with a path (default 'equals'). " + t]; };

  var condFacets = [
    facets([
      ["Se cumple cuando el dato leído es igual al valor esperado.", "Met when the data read equals the expected value."],
      ["Para tratar de forma distinta un mismo tipo de evento según sus datos, por ejemplo solo cierto tipo de problema o de gravedad.", "To treat the same event type differently depending on its data, e.g. only a certain kind or severity of problem."],
      condAct, condCfg("Valor esperado: obligatorio con este operador."), condRel,
      ["Si se cumple, la regla continúa hacia sus límites de repetición y su efecto; si no, se ignora sin dejar entrada en el historial.", "If met, the rule goes on to its repeat limits and effect; if not, it is ignored and leaves no history entry."],
      condEvt, condImpl("Caso EQUALS (valor por defecto de operator en GamificationRule); la comparación normaliza ambos lados.")]),
    facets([
      ["Se cumple cuando el dato leído es distinto del valor esperado.", "Met when the data read differs from the expected value."],
      ["Para excluir un caso concreto sin tener que enumerar todos los demás.", "To exclude one specific case without listing all the others."],
      condAct, condCfg("Valor esperado: obligatorio con este operador."), condRel,
      ["Igual que los demás operadores: si se cumple la regla sigue; si no, se ignora.", "Like the other operators: if met the rule continues; otherwise it is ignored."],
      condEvt, condImpl("Caso NOT_EQUALS: negación de la igualdad normalizada.")]),
    facets([
      ["Se cumple cuando el dato existe en el evento, sea cual sea su valor.", "Met when the data is present in the event, whatever its value."],
      ["Para premiar solo los eventos que traen cierta información, por ejemplo que citan un problema concreto.", "To reward only events that carry certain information, e.g. that cite a specific problem."],
      condAct, condCfg("Valor esperado: no se usa; el diseñador lo oculta."), condRel,
      ["Si el dato está presente la regla sigue; si falta, se ignora.", "If the data is present the rule continues; if missing, it is ignored."],
      condEvt, condImpl("Caso EXISTS: el valor esperado se anula (null) y se cumple si el dato resuelto no es nulo.")]),
    facets([
      ["Se cumple cuando el dato no existe en el evento.", "Met when the data is absent from the event."],
      ["Para reaccionar solo a eventos que no traen cierta información.", "To react only to events that do not carry certain information."],
      condAct, condCfg("Valor esperado: no se usa. El motor lo admite, pero el diseñador todavía no lo ofrece: hoy solo se puede escribir a mano en las reglas."), condRel,
      ["Si el dato falta la regla sigue; si está presente, se ignora.", "If the data is missing the rule continues; if present, it is ignored."],
      condEvt, condImpl("Caso NOT_EXISTS; falta en la lista de operadores de GamificationDesignerParts.tsx (líneas 30-36).")]),
    facets([
      ["Se cumple cuando el dato leído contiene el texto esperado.", "Met when the data read contains the expected text."],
      ["Para filtrar por una parte de un texto, como un nombre o un mensaje.", "To filter by part of a text, such as a name or a message."],
      condAct, condCfg("Valor esperado: obligatorio con este operador."), condRel,
      ["Si el texto contiene el valor esperado la regla sigue; si no, se ignora.", "If the text contains the expected value the rule continues; otherwise it is ignored."],
      condEvt, condImpl("Caso CONTAINS: comparación de subcadena sobre la representación textual del dato.")]),
    facets([
      ["Se cumple cuando el dato numérico es estrictamente mayor que el valor esperado.", "Met when the numeric data is strictly greater than the expected value."],
      ["Para premiar solo a partir de cierta cantidad, por ejemplo más de N problemas resueltos de una vez.", "To reward only above a certain amount, e.g. more than N problems resolved at once."],
      condAct, condCfg("Valor esperado: obligatorio con este operador y numérico."), condRel,
      ["Si el dato supera el umbral la regla sigue; si no, se ignora. Comportamiento con datos no numéricos: no verificado en el código.", "If the data exceeds the threshold the rule continues; otherwise it is ignored. Behaviour with non-numeric data: not verified in the code."],
      condEvt, condImpl("Caso GREATER_THAN con conversión numérica de ambos lados (number()).")]),
    facets([
      ["Se cumple cuando el dato numérico es estrictamente menor que el valor esperado.", "Met when the numeric data is strictly less than the expected value."],
      ["Para premiar o penalizar solo por debajo de cierto valor.", "To reward or penalise only below a certain value."],
      condAct, condCfg("Valor esperado: obligatorio con este operador y numérico."), condRel,
      ["Si el dato queda por debajo del umbral la regla sigue; si no, se ignora. Comportamiento con datos no numéricos: no verificado en el código.", "If the data is below the threshold the rule continues; otherwise it is ignored. Behaviour with non-numeric data: not verified in the code."],
      condEvt, condImpl("Caso LESS_THAN con conversión numérica de ambos lados.")])
  ];

  var effEvt = ["Todos los eventos pueden activar cualquiera de los dos efectos; el efecto no cambia qué evento se escucha, solo qué se hace al encajar.", "Any event may trigger either effect; the effect does not change which event is listened to, only what is done on a match."];
  var effectFacets = [
    facets([
      ["Un premio: la regla suma puntos y puede además desbloquear un logro, avanzar un reto o conceder una recompensa.", "An award: the rule adds points and may also unlock an achievement, advance a challenge or grant a reward."],
      ["Para reforzar un comportamiento deseado, como resolver o corregir un problema.", "To reinforce a desired behaviour, such as resolving or fixing a problem."],
      ["Se activa cuando llega el evento de la regla, se cumple su condición y la política de repetición lo permite.", "Triggered when the rule's event arrives, its condition is met and the repeat policy allows it."],
      ["Cantidad de puntos: obligatoria si se quiere sumar (se guarda en positivo). Tipo de puntos, logro, reto y recompensa: opcionales, como máximo uno de cada.", "Amount of points: mandatory if points are to be added (stored positive). Point type, achievement, challenge and reward: optional, at most one of each."],
      ["Una regla tiene un único efecto. Se relaciona con el tipo de puntos, el logro, el reto y la recompensa que la regla apunte.", "A rule has a single effect. It relates to the point type, achievement, challenge and reward the rule points to."],
      ["Anota una entrada activa: sube el saldo, puede desbloquear el logro, avanza el reto en una unidad y concede la recompensa; genera un aviso favorable.", "Notes an active entry: raises the balance, may unlock the achievement, advances the challenge by one unit and grants the reward; produces a favourable notice."],
      effEvt,
      ["effectKind se deriva del signo de los puntos en GamificationRule; el evento derivado publicado es core.gamification.award-granted (GamificationRuntimeService.publish).", "effectKind is derived from the sign of the points in GamificationRule; the derived event published is core.gamification.award-granted (GamificationRuntimeService.publish)."]]),
    facets([
      ["Una penalización: la regla resta puntos y no puede conceder logro, reto ni recompensa.", "A penalty: the rule subtracts points and cannot grant an achievement, challenge or reward."],
      ["Para desincentivar un comportamiento no deseado, como reabrir un problema o deshacer una corrección.", "To discourage an unwanted behaviour, such as reopening a problem or undoing a fix."],
      ["Se activa igual que un premio: evento de la regla, condición cumplida y política de repetición favorable.", "Triggered like an award: the rule's event, met condition and a favourable repeat policy."],
      ["Cantidad de puntos: obligatoria (se guarda en positivo y se firma como negativa). Al elegir penalización el diseñador vacía logro, reto y recompensa. Tipo de puntos: opcional pero necesario para que reste algo.", "Amount of points: mandatory (stored positive and signed negative). Choosing penalty makes the designer clear achievement, challenge and reward. Point type: optional but needed for it to subtract anything."],
      ["Un efecto por regla; solo actúa sobre un tipo de puntos y queda limitada por su saldo mínimo.", "One effect per rule; it acts on a single point type and is limited by its minimum balance."],
      ["La resta se recorta para no bajar del saldo mínimo; si tras recortar queda en cero se rechaza con el motivo «saldo mínimo alcanzado». Genera un aviso de advertencia.", "The subtraction is clipped so as not to go below the minimum balance; if it ends at zero it is rejected with the reason 'minimum balance reached'. Produces a warning notice."],
      effEvt,
      ["appliedPoints() calcula max(puntos, mínimo − saldo) y el rechazo «Minimum point balance reached» está en GamificationRuntimeService:117; evento derivado core.gamification.penalty-applied.", "appliedPoints() computes max(points, minimum − balance) and the 'Minimum point balance reached' rejection is at GamificationRuntimeService:117; derived event core.gamification.penalty-applied."]])
  ];

  var repWhen = ["Se evalúa en cada evento que encaja con la regla, antes de aplicar el efecto, contra lo ya concedido a esa persona.", "Evaluated on every event matching the rule, before the effect is applied, against what was already granted to that person."];
  var repEvt = ["Mira solo las entradas activas previas de esa persona y esa regla; los eventos de reversión las hacen dejar de contar.", "Looks only at that person's earlier active entries for that rule; reversal events make them stop counting."];
  var repFacets = [
    facets([
      ["La regla solo se aplica una vez por persona.", "The rule applies only once per person."],
      ["Para hitos únicos como «primera corrección» o bienvenidas.", "For one-off milestones such as 'first fix' or welcomes."],
      repWhen, ["Sin campos extra. Máximo por persona y por día: opcionales.", "No extra fields. Max per person and per day: optional."],
      ["Una política por regla; cuenta las entradas activas de esa regla y esa persona.", "One policy per rule; counts that person's active entries for the rule."],
      ["Las siguientes coincidencias dejan una entrada rechazada con 0 puntos y el motivo «solo se puede aplicar una vez».", "Later matches leave a rejected entry with 0 points and the reason 'can only be applied once'."],
      repEvt, ["rejection(): repeatPolicy == ONCE y hay entradas activas → «Rule can only be applied once» (GamificationRuntimeService:219).", "rejection(): repeatPolicy == ONCE with active entries → 'Rule can only be applied once' (GamificationRuntimeService:219)."]]),
    facets([
      ["Cada evento distinto cuenta una vez; es la modalidad por defecto.", "Each distinct event counts once; it is the default mode."],
      ["Para premiar cada ocurrencia real sin permitir que el mismo suceso puntúe dos veces.", "To reward each real occurrence without letting the same occurrence score twice."],
      repWhen, ["Sin campos extra. Es el valor por defecto de una regla nueva. Máximos: opcionales.", "No extra fields. It is the default for a new rule. Maxima: optional."],
      ["Una política por regla; no restringe la cadencia, solo impide repetir el mismo evento.", "One policy per rule; it does not restrict cadence, it only prevents repeating the same event."],
      ["Cada evento nuevo suma una entrada activa; un evento ya procesado no suma nada.", "Each new event adds an active entry; an already-processed event adds nothing."],
      ["La idempotencia depende del identificador del evento, así que un reenvío del mismo evento no puntúa de nuevo.", "Idempotency depends on the event identifier, so a resend of the same event does not score again."],
      ["Idempotencia en process(): sourceEventId ya presente en el historial, o clave evento|regla ya procesada (GamificationRuntimeService:102, 113-114). Es el valor por defecto en GamificationRule:22.", "Idempotency in process(): sourceEventId already in the ledger, or event|rule key already processed (GamificationRuntimeService:102, 113-114). Default in GamificationRule:22."]]),
    facets([
      ["La regla no añade restricción propia de cadencia.", "The rule adds no cadence restriction of its own."],
      ["Para acciones que deben premiarse siempre, asumiendo el riesgo de premios infinitos.", "For actions that should always be rewarded, accepting the risk of endless prizes."],
      repWhen, ["Sin campos extra, aunque conviene fijar un máximo por persona o por día; la comprobación avisa si no hay ningún límite.", "No extra fields, though a per-person or per-day cap is advisable; the check warns if there is no limit at all."],
      ["Una política por regla; solo los máximos opcionales la acotan.", "One policy per rule; only the optional maxima bound it."],
      ["Cada evento distinto da una entrada activa, sin tope salvo los máximos opcionales.", "Each distinct event yields an active entry, with no cap except the optional maxima."],
      repEvt, ["La rama UNLIMITED no añade rechazos propios en rejection(); GamificationChecker emite la advertencia de regla sin límite.", "The UNLIMITED branch adds no rejections of its own in rejection(); GamificationChecker emits the unbounded-rule warning."]]),
    facets([
      ["Exige un intervalo mínimo entre dos concesiones de la misma regla a la misma persona.", "Requires a minimum interval between two grants of the same rule to the same person."],
      ["Para evitar abusos por ráfagas, como corregir cien veces seguidas en un segundo.", "To prevent burst abuse, such as fixing a hundred times in a row within one second."],
      repWhen, ["Segundos de espera: obligatorio con esta política (no negativo). Máximos: opcionales.", "Wait seconds: mandatory with this policy (non-negative). Maxima: optional."],
      ["Una política por regla; compara con la última concesión activa de esa persona.", "One policy per rule; compares with that person's last active grant."],
      ["Una coincidencia dentro del tiempo de espera deja una entrada rechazada con el motivo «espera activa».", "A match within the wait time leaves a rejected entry with the reason 'wait active'."],
      repEvt, ["rejection(): COOLDOWN con entradas activas y última + cooldownSeconds posterior al instante del evento → «Rule cooldown is active» (GamificationRuntimeService:225-227). Usa la hora del evento, no la del reloj.", "rejection(): COOLDOWN with active entries and last + cooldownSeconds after the event instant → 'Rule cooldown is active' (GamificationRuntimeService:225-227). It uses the event time, not the wall clock."]]),
    facets([
      ["Limita las concesiones de la regla a un máximo por día (día UTC) y persona.", "Limits the rule's grants to a maximum per day (UTC day) per person."],
      ["Para permitir repetición pero con un tope diario, como máximo cinco premios al día.", "To allow repetition but with a daily cap, such as five awards a day at most."],
      repWhen, ["Máximo por día: obligatorio con esta política. Máximo por persona: opcional.", "Max per day: mandatory with this policy. Max per person: optional."],
      ["Una política por regla; cuenta las entradas activas del mismo día UTC.", "One policy per rule; counts active entries of the same UTC day."],
      ["Al alcanzar el tope, las siguientes coincidencias del día dejan una entrada rechazada con el motivo de límite diario.", "On reaching the cap, further matches that day leave a rejected entry with the daily-limit reason."],
      repEvt, ["rejection(): maxAwardsPerDay > 0 → «Daily application limit reached»; con DAILY_LIMIT → «Daily repeat policy limit reached» (GamificationRuntimeService:222-224). El día se toma de occurredAt en UTC.", "rejection(): maxAwardsPerDay > 0 → 'Daily application limit reached'; with DAILY_LIMIT → 'Daily repeat policy limit reached' (GamificationRuntimeService:222-224). The day comes from occurredAt in UTC."]])
  ];

  var ledStatusEvt = ["El historial lo escribe el motor al procesar un evento verificado; los eventos de reversión cambian entradas ya existentes.", "The engine writes the history when processing a verified event; reversal events change existing entries."];
  var ledgerFacets = [
    facets([
      ["Una concesión vigente: lo que se otorgó a una persona por una regla y un evento.", "A current grant: what was given to a person by a rule and an event."],
      ["Es lo que cuenta: de las entradas activas se reconstruyen puntos, nivel, logros, retos y recompensas.", "It is what counts: points, level, achievements, challenges and rewards are rebuilt from active entries."],
      ["Se crea cuando una regla encaja, pasa su política de repetición y, si resta puntos, queda algo por restar.", "Created when a rule matches, passes its repeat policy and, if it subtracts, something remains to subtract."],
      ["No se configura a mano: la rellena el motor. Opcional en ella: logro, reto, recompensa y tipo de puntos, según la regla.", "Not configured by hand: the engine fills it in. Optional in it: achievement, challenge, reward and point type, depending on the rule."],
      ["Apunta a un evento, una regla y a lo concedido; puede pasar a compensada si un evento de reversión la deshace.", "Points to one event, one rule and what was granted; may become compensated if a reversal event undoes it."],
      ["Suma puntos, avance de reto y logros al estado de la persona y genera avisos; publica un evento de concesión o de penalización.", "Adds points, challenge progress and achievements to the person's status and produces notices; publishes a grant or penalty event."],
      ledStatusEvt, ["GamificationLedgerEntry con status ACTIVE e id ledger:<uuid>; se crea en entry() con los metadatos eventTypeId, diagnosticId, subjectId, transactionId, effectKind (GamificationRuntimeService:241-255).", "GamificationLedgerEntry with status ACTIVE and id ledger:<uuid>; created in entry() with metadata eventTypeId, diagnosticId, subjectId, transactionId, effectKind (GamificationRuntimeService:241-255)."]]),
    facets([
      ["Una concesión anulada por una reversión posterior; se conserva pero ya no cuenta.", "A grant cancelled by a later reversal; it is kept but no longer counts."],
      ["Mantiene la huella de lo ocurrido sin borrar nada y permite que el estado se corrija sin perder el rastro.", "Keeps a trace of what happened without erasing anything and lets the status be corrected without losing the trail."],
      ["Se produce cuando llega un evento de reversión que cita la concesión: una corrección deshecha, un problema reabierto o una referencia explícita al evento original.", "Produced when a reversal event citing the grant arrives: an undone fix, a reopened problem or an explicit reference to the original event."],
      ["No se configura: lo decide la reversión. Solo se pueden revertir entradas activas de la misma persona.", "Not configured: the reversal decides it. Only active entries of the same person can be reversed."],
      ["Se enlaza con una entrada contraria de puntos de signo opuesto que referencia a la original.", "Linked to an opposite entry with opposite-sign points that references the original."],
      ["Deja de contar para puntos, logros y retos, su aviso original se omite y se emite un aviso de reversión y un evento de compensación.", "Stops counting for points, achievements and challenges, its original notice is skipped and a reversal notice and a compensation event are emitted."],
      ["Es la respuesta a eventos de tipo «corrección deshecha» o «violación reabierta», o a cualquier evento con referencia explícita al original; tiene prioridad sobre las reglas normales.", "It answers events such as 'fix undone' or 'violation reopened', or any event explicitly referencing the original; it takes priority over normal rules."],
      ["compensate() (GamificationRuntimeService:179-208): copyStatus(COMPENSATED) más una entrada ACTIVE con compensationOf=id original y metadata.reason=eventTypeId. Evento derivado core.gamification.award-compensated.", "compensate() (GamificationRuntimeService:179-208): copyStatus(COMPENSATED) plus an ACTIVE entry with compensationOf=original id and metadata.reason=eventTypeId. Derived event core.gamification.award-compensated."]]),
    facets([
      ["Una regla que encajó pero no se pudo aplicar; se anota con 0 puntos y su motivo.", "A rule that matched but could not be applied; noted with 0 points and its reason."],
      ["Deja constancia de por qué no hubo premio (límite, espera, saldo mínimo) y evita repetir el intento con el mismo evento.", "Records why there was no prize (limit, wait, minimum balance) and avoids retrying with the same event."],
      ["Se produce cuando la política de repetición, un máximo o el saldo mínimo impiden aplicar la regla.", "Produced when the repeat policy, a maximum or the minimum balance prevents applying the rule."],
      ["No se configura; el aviso de rechazo solo se muestra si la política de avisos incluye rechazos (apagado por defecto).", "Not configured; the rejection notice is only shown if the notice policy includes rejections (off by default)."],
      ["Apunta al evento y a la regla, sin logro, reto ni recompensa; no tiene entrada contraria.", "Points to the event and the rule, with no achievement, challenge or reward; it has no opposite entry."],
      ["No cambia puntos ni retos; publica un evento de rechazo y, si así se configura, genera un aviso informativo.", "Changes no points or challenges; publishes a rejection event and, if configured, produces an informational notice."],
      ledStatusEvt, ["entry(...REJECTED...) con puntos 0 y metadata.reason; evento core.gamification.award-rejected. No se copian logro, reto ni recompensa en entradas no activas (GamificationRuntimeService:252-253).", "entry(...REJECTED...) with 0 points and metadata.reason; event core.gamification.award-rejected. Achievement, challenge and reward are not copied into non-active entries (GamificationRuntimeService:252-253)."]])
  ];

  var sevAct = ["Se asigna según el resultado de la entrada del historial en el momento de calcular los avisos.", "Assigned according to the outcome of the history entry when notices are calculated."];
  var sevCfg = ["No se configura por aviso; la política de avisos solo decide dónde y cuánto se muestran.", "Not configured per notice; the notice policy only decides where and for how long they are shown."];
  var feedbackFacets = [
    facets([
      ["Gravedad de éxito: resultado favorable para la persona.", "Success severity: a favourable outcome for the person."],
      ["Da refuerzo positivo inmediato y comprensible.", "Gives immediate, understandable positive reinforcement."],
      ["Puntos concedidos, logro desbloqueado, reto completado, recompensa desbloqueada o nivel alcanzado.", "Points granted, achievement unlocked, challenge completed, reward unlocked or level reached."],
      sevCfg, ["Depende de una entrada del historial y se rige por la política de avisos.", "Depends on a history entry and is governed by the notice policy."],
      ["Se muestra como emergente y se anota en la lista de actividad; no se guarda como dato propio.", "Shown as a pop-up and noted in the activity list; not stored as data of its own."],
      sevAct, ["UserFeedbackSeverity.SUCCESS en GamificationFeedbackProjector (líneas 69-98).", "UserFeedbackSeverity.SUCCESS in GamificationFeedbackProjector (lines 69-98)."]]),
    facets([
      ["Gravedad informativa: seguimiento neutro.", "Informational severity: neutral follow-up."],
      ["Mantiene al tanto sin alarmar ni celebrar.", "Keeps people informed without alarming or celebrating."],
      ["Avance parcial de un reto (antes de llegar al objetivo), reversión y rechazo cuando la política incluye rechazos.", "Partial progress of a challenge (before reaching the target), reversal and rejection when the policy includes rejections."],
      sevCfg, ["Depende de una entrada del historial y se rige por la política de avisos.", "Depends on a history entry and is governed by the notice policy."],
      ["Muestra «n / objetivo» en los retos; los avisos de rechazo solo existen si la política los incluye.", "Shows 'n / target' for challenges; rejection notices only exist if the policy includes them."],
      sevAct, ["UserFeedbackSeverity.INFO: CHALLENGE_PROGRESS, APPLICATION_REJECTED y AWARD_COMPENSATED (esta última con INFO en el proyector, aunque el contenido la describe como advertencia).", "UserFeedbackSeverity.INFO: CHALLENGE_PROGRESS, APPLICATION_REJECTED and AWARD_COMPENSATED (the latter is INFO in the projector, although the content describes it as a warning)."]]),
    facets([
      ["Gravedad de advertencia: resultado desfavorable.", "Warning severity: an unfavourable outcome."],
      ["Avisa de que una acción restó puntos para que la persona revise el problema relacionado.", "Warns that an action subtracted points so the person reviews the related problem."],
      ["Penalización aplicada; el contenido original describe también la reversión aquí, pero el proyector la emite como informativa.", "Penalty applied; the original content also lists reversal here, but the projector emits it as informational."],
      sevCfg, ["Depende de una entrada del historial y se rige por la política de avisos; puede incluir la acción de abrir el problema relacionado.", "Depends on a history entry and is governed by the notice policy; it may include the action to open the related problem."],
      ["Se muestra como emergente y en la actividad, con el siguiente paso recomendado.", "Shown as a pop-up and in the activity list, with the recommended next step."],
      sevAct, ["UserFeedbackSeverity.WARNING solo para PENALTY_APPLIED (GamificationFeedbackProjector:69-70). Discrepancia verificada con el contenido del modelo sobre la reversión.", "UserFeedbackSeverity.WARNING only for PENALTY_APPLIED (GamificationFeedbackProjector:69-70). Verified discrepancy with the model content about reversal."]]),
    facets([
      ["Gravedad de error: existe como valor posible.", "Error severity: exists as a possible value."],
      ["Reservada para fallos que la persona deba ver; hoy ningún aviso la usa.", "Reserved for failures the person should see; no notice uses it today."],
      ["Nada la activa en la proyección actual.", "Nothing triggers it in the current projection."],
      ["No aplica: no hay configuración.", "Not applicable: there is no configuration."],
      ["Sin entradas de historial asociadas.", "No associated history entries."],
      ["Sin efecto visible hoy.", "No visible effect today."],
      ["No se activa por ningún evento.", "Not triggered by any event."],
      ["UserFeedbackSeverity.ERROR existe en el enumerado; ninguna llamada a envelope() la emplea (GamificationFeedbackProjector).", "UserFeedbackSeverity.ERROR exists in the enum; no envelope() call uses it (GamificationFeedbackProjector)."]])
  ];

  HM.facets({ caps: { gamification: { elements: {
    condition: { variants: condFacets.map(function(f, i){ return { i: i, facets: f }; }) },
    effect:    { variants: effectFacets.map(function(f, i){ return { i: i, facets: f }; }) },
    repeat:    { variants: repFacets.map(function(f, i){ return { i: i, facets: f }; }) },
    ledger:    { variants: ledgerFacets.map(function(f, i){ return { i: i, facets: f }; }) },
    feedback:  { variants: feedbackFacets.map(function(f, i){ return { i: i, facets: f }; }) }
  } } } });

  // ------------------------------------------------------------------------------------------------
  // 2) New elements (HM.extend + HM.lens)
  // ------------------------------------------------------------------------------------------------
  // item: { k, n:[es,en], d:[es,en], td?:[es,en], f:[8 x [es,en]] }
  function vars(items) { return items.map(function(x){ return { n: T(x.n[0], x.n[1]), d: T(x.d[0], x.d[1]), req: "ALTERNATIVE", facets: facets(x.f) }; }); }
  function lensVars(items) { return items.map(function(x){ var v = { fd: T(x.f[0][0] + " " + x.f[1][0], x.f[0][1] + " " + x.f[1][1]) }; if (x.k) v.k = x.k; v.td = T(x.f[7][0], x.f[7][1]); return v; }); }

  // ---- 2a) Game elements: what each catalog element is (point, level, achievement, challenge, reward, rule)
  var elemItems = [
    { n: ["Tipo de puntos", "Point type"], d: ["Una cuenta de puntos con nombre y saldo mínimo", "A named points account with a minimum balance"],
      f: [
        ["Una moneda de puntos con nombre propio (por ejemplo «experiencia» o «calidad»), de la que cada persona tiene un saldo.", "A named points currency (e.g. 'experience' or 'quality'), of which each person has a balance."],
        ["Permite llevar varias cuentas independientes y fijar hasta dónde puede bajar cada una.", "Allows several independent accounts and setting how low each may go."],
        ["No se activa por sí solo: una regla de premio o penalización que lo apunta suma o resta puntos cuando llega su evento.", "Not triggered on its own: an award or penalty rule pointing to it adds or subtracts points when its event arrives."],
        ["Nombre e identificador: obligatorios. Saldo mínimo: opcional (0 por defecto; negativo permite una deuda acotada). Descripción: opcional.", "Name and identifier: mandatory. Minimum balance: optional (0 by default; negative allows bounded debt). Description: optional."],
        ["Lo referencian 0..N niveles y 0..N reglas. Borrarlo limpia las referencias que lo usaban.", "Referenced by 0..N levels and 0..N rules. Deleting it clears the references that used it."],
        ["Define el saldo de cada persona: suma de sus entradas activas, nunca por debajo del mínimo.", "Defines each person's balance: the sum of their active entries, never below the minimum."],
        ["Se mueve con los eventos que activan reglas que lo usan; sin eventos el saldo no cambia.", "Moves with the events that trigger rules using it; without events the balance does not change."],
        ["PointTypeDefinition (minimumBalance long, 0 por defecto); el recorte está en appliedPoints() y rebuildState() (GamificationRuntimeService:232, 274).", "PointTypeDefinition (long minimumBalance, default 0); clipping is in appliedPoints() and rebuildState() (GamificationRuntimeService:232, 274)."]] },
    { n: ["Nivel", "Level"], d: ["Un rango que se alcanza al llegar a un umbral de puntos", "A rank reached on hitting a points threshold"],
      f: [
        ["Un escalón asociado a un tipo de puntos y a un umbral mínimo.", "A step tied to a point type and a minimum threshold."],
        ["Da sensación de progreso y, opcionalmente, una recompensa al subir.", "Gives a sense of progress and, optionally, a reward on level-up."],
        ["Se alcanza solo cuando el saldo de su tipo de puntos llega al umbral; no hay regla que lo apunte directamente.", "Reached only when the balance of its point type hits the threshold; no rule points to it directly."],
        ["Nombre, identificador, tipo de puntos (debe existir) y puntos mínimos (0 o más): obligatorios. Recompensa: opcional.", "Name, identifier, point type (must exist) and minimum points (0 or more): mandatory. Reward: optional."],
        ["Referencia un tipo de puntos y, si se quiere, una recompensa. Varios niveles pueden compartir tipo: gana el de umbral más alto alcanzado.", "References a point type and optionally a reward. Several levels may share a type: the highest threshold reached wins."],
        ["No se guarda: se calcula del saldo. Al subir de nivel aparece un aviso y se concede su recompensa.", "Not stored: calculated from the balance. Levelling up shows a notice and grants its reward."],
        ["Depende indirectamente de los eventos que mueven sus puntos; un evento de reversión puede hacer que se pierda.", "Depends indirectly on the events that move its points; a reversal event may make it be lost."],
        ["LevelDefinition (pointTypeId obligatorio, minimumPoints >= 0); derivado en rebuildState (GamificationRuntimeService:284-290) y en el proyector para el aviso (GamificationFeedbackProjector:94).", "LevelDefinition (required pointTypeId, minimumPoints >= 0); derived in rebuildState (GamificationRuntimeService:284-290) and in the projector for the notice (GamificationFeedbackProjector:94)."]] },
    { n: ["Logro", "Achievement"], d: ["Un hito que se desbloquea una vez, visible u oculto", "A milestone unlocked once, visible or hidden"],
      f: [
        ["Un hito concreto que se reconoce a una persona.", "A specific milestone recognised for a person."],
        ["Reconoce comportamientos que no son una cantidad de puntos; puede ocultarse hasta desbloquearse.", "Recognises behaviours that are not a points amount; may be hidden until unlocked."],
        ["Lo desbloquea una regla de premio que lo apunta cuando se aplica con éxito; una penalización nunca puede.", "Unlocked by an award rule pointing to it when it applies successfully; a penalty never can."],
        ["Nombre e identificador: obligatorios. Descripción, recompensa y marca de oculto: opcionales.", "Name and identifier: mandatory. Description, reward and hidden flag: optional."],
        ["Lo referencian 0..N reglas; puede apuntar a 0..1 recompensa.", "Referenced by 0..N rules; may point to 0..1 reward."],
        ["Queda en la lista de logros de la persona, avisa una vez y concede su recompensa; si la entrada se revierte deja de contar.", "Added to the person's achievements, notifies once and grants its reward; if the entry is reversed it stops counting."],
        ["Se activa por el evento de la regla que lo apunta, tras condición y política de repetición.", "Triggered by the event of the rule pointing to it, after condition and repeat policy."],
        ["AchievementDefinition (hidden boolean); se acumula en rebuildState y avisa con ACHIEVEMENT_UNLOCKED (GamificationFeedbackProjector:74).", "AchievementDefinition (boolean hidden); accumulated in rebuildState and notified with ACHIEVEMENT_UNLOCKED (GamificationFeedbackProjector:74)."]] },
    { n: ["Reto", "Challenge"], d: ["Una meta que se completa tras N aplicaciones de una regla", "A goal completed after N applications of a rule"],
      f: [
        ["Una tarea que se completa tras repetir cierta acción un número de veces.", "A task completed after repeating a given action a number of times."],
        ["Mide el avance acumulado hacia una meta del tipo «hacer X veces».", "Measures accumulated progress toward a goal of the 'do X times' kind."],
        ["Cada aplicación con éxito de una regla de premio que lo apunta avanza una unidad.", "Each successful application of an award rule pointing to it advances one unit."],
        ["Nombre, identificador y acciones objetivo (mayor que 0): obligatorios. Descripción y recompensa: opcionales.", "Name, identifier and target count (above 0): mandatory. Description and reward: optional."],
        ["Lo referencian 0..N reglas; puede apuntar a 0..1 recompensa.", "Referenced by 0..N rules; may point to 0..1 reward."],
        ["Muestra avance «n / objetivo»; al llegar al objetivo queda completado, avisa y concede su recompensa. Una reversión resta avance.", "Shows progress 'n / target'; on reaching the target it is completed, notifies and grants its reward. A reversal removes progress."],
        ["Lo mueven los eventos de la regla que lo apunta; no tiene eventos propios.", "Moved by the events of the rule pointing to it; it has no events of its own."],
        ["ChallengeDefinition (targetCount > 0); avance contado por entrada activa en rebuildState (GamificationRuntimeService:268); avisos CHALLENGE_PROGRESS / CHALLENGE_COMPLETED.", "ChallengeDefinition (targetCount > 0); progress counted per active entry in rebuildState (GamificationRuntimeService:268); CHALLENGE_PROGRESS / CHALLENGE_COMPLETED notices."]] },
    { n: ["Recompensa", "Reward"], d: ["Un contenido libre que se concede a través de otros elementos", "Free-form content granted through other elements"],
      f: [
        ["Algo que se concede: un contenido libre descrito por quien la define.", "Something granted: free-form content described by its author."],
        ["Da un premio concreto reutilizable por niveles, logros, retos y reglas.", "Provides a concrete prize reusable by levels, achievements, challenges and rules."],
        ["Se concede cuando se alcanza el nivel, se desbloquea el logro, se completa el reto o se aplica la regla que la apunta.", "Granted when the level is reached, the achievement is unlocked, the challenge is completed or the rule pointing to it applies."],
        ["Nombre e identificador: obligatorios. Descripción y contenido libre: opcionales.", "Name and identifier: mandatory. Description and free content: optional."],
        ["La referencian 0..N niveles, logros, retos y reglas. Una regla de penalización no puede otorgarla.", "Referenced by 0..N levels, achievements, challenges and rules. A penalty rule cannot grant it."],
        ["Queda en la lista de recompensas de la persona y se avisa una sola vez por recompensa.", "Added to the person's rewards and notified only once per reward."],
        ["Se activa indirectamente por los eventos que activan el elemento que la concede; ningún evento la apunta directamente.", "Triggered indirectly by the events triggering the element that grants it; no event points to it directly."],
        ["RewardDefinition (payload Map libre); deduplicada con un conjunto en el estado y en el proyector (unlockReward, GamificationFeedbackProjector:105-107). El motor no ejecuta el contenido: solo lo registra.", "RewardDefinition (free Map payload); deduplicated with a set in the status and in the projector (unlockReward, GamificationFeedbackProjector:105-107). The engine does not execute the content: it only records it."]] },
    { n: ["Regla de gamificación", "Gamification rule"], d: ["Liga un evento con un efecto, una condición y límites", "Binds an event to an effect, a condition and limits"],
      f: [
        ["Dice qué evento provoca qué efecto, bajo qué condición y con qué límites.", "States which event causes which effect, under which condition and with which limits."],
        ["Es el motor del juego: sin reglas no se concede nada.", "It is the game's engine: without rules nothing is granted."],
        ["La activa la llegada de un evento verificado de su tipo, si la regla está activa y su condición se cumple.", "Triggered by the arrival of a verified event of its type, if the rule is enabled and its condition is met."],
        ["Evento: obligatorio. Condición, tipo de puntos, logro, reto, recompensa, cantidad, política de repetición, máximos y marca de activa: opcionales; espera o máximo diario según la política.", "Event: mandatory. Condition, point type, achievement, challenge, reward, amount, repeat policy, maxima and enabled flag: optional; wait or daily maximum depending on the policy."],
        ["Referencia exactamente 1 evento y hasta 1 tipo de puntos, 1 logro, 1 reto y 1 recompensa; lleva 0..1 condición y 1 efecto.", "References exactly 1 event and up to 1 point type, 1 achievement, 1 challenge and 1 reward; carries 0..1 condition and 1 effect."],
        ["Cada evaluación termina en una entrada activa o rechazada en el historial; de ahí se derivan estado y avisos.", "Each evaluation ends in an active or rejected history entry; status and notices are derived from it."],
        ["Es el único punto de unión entre un evento y el juego; evalúa el evento original de forma directa, no a través de otras reglas.", "It is the only join point between an event and the game; it evaluates the original event directly, not through other rules."],
        ["GamificationRule dentro de GamificationBindingModel; el bucle de evaluación está en process() (GamificationRuntimeService:110-123).", "GamificationRule inside GamificationBindingModel; the evaluation loop is in process() (GamificationRuntimeService:110-123)."]] }
  ];

  // ---- 2b) Triggers and conditions: how a verified event reaches a rule
  var trigItems = [
    { n: ["Suscripción al bus de eventos", "Event bus subscription"], d: ["Vía automática: el motor escucha todos los eventos verificados del metamodelo", "Automatic route: the engine listens to every verified event of the metamodel"], k: "BUS",
      f: [
        ["La entrada principal: cada evento verificado que circula por el sistema llega al motor sin que nadie lo llame.", "The main entry: every verified event flowing through the system reaches the engine without anyone calling it."],
        ["Para que premiar o penalizar ocurra solo, al editar, sin acciones manuales.", "So rewarding or penalising happens by itself while editing, with no manual action."],
        ["Un evento con metamodelo informado, con la gamificación encendida en el servidor y sin el indicador de apagado por sesión en sus metadatos.", "An event with a metamodel set, gamification enabled on the server and no per-session switch-off flag in its metadata."],
        ["Obligatorio: metamodelo guardado y gamificación activada. Opcional: reglas. El apagado por sesión es poco fiable hoy (ver implementación).", "Mandatory: a saved metamodel and gamification enabled. Optional: rules. The per-session switch-off is unreliable today (see implementation)."],
        ["Alimenta las reglas; sus eventos derivados vuelven al mismo bus y otros flujos pueden escucharlos.", "Feeds the rules; its derived events return to the same bus and other flows may listen to them."],
        ["Cada evento admitido se procesa bajo bloqueo del metamodelo y termina en 0..N entradas de historial.", "Each admitted event is processed under the metamodel lock and ends in 0..N history entries."],
        ["Es la ruta de los eventos del catálogo reservado y de los propios del metamodelo.", "It is the route for events of the reserved catalog and the metamodel's own."],
        ["subscribe() (@PostConstruct, GamificationRuntimeService:82-91): predicado properties.isEnabled() && metadata hydra.gamificationEnabled != false && metamodelId no vacío. Discrepancia verificada: la sesión escribe el indicador en el payload, no en los metadatos, por lo que el apagado por sesión no se aplica.", "subscribe() (@PostConstruct, GamificationRuntimeService:82-91): predicate properties.isEnabled() && metadata hydra.gamificationEnabled != false && non-blank metamodelId. Verified discrepancy: the session writes the flag in the payload, not the metadata, so the per-session switch-off is not honoured."]] },
    { n: ["Entrada HTTP de eventos", "HTTP event entry"], d: ["Vía manual: un cliente envía un evento y recibe el resultado", "Manual route: a client sends an event and gets the result"], k: "HTTP",
      f: [
        ["Una entrada alternativa para inyectar un evento y ver al instante qué concedió, rechazó o revirtió.", "An alternative entry to inject an event and immediately see what it granted, rejected or reversed."],
        ["Para integraciones externas, pruebas y diagnósticos sin pasar por el editor.", "For external integrations, tests and diagnostics without going through the editor."],
        ["Una petición explícita que lleva el evento completo hacia un metamodelo concreto.", "An explicit request carrying the full event to a specific metamodel."],
        ["Obligatorio: metamodelo guardado y evento completo. No verificado en el código: si exige gamificación activada, porque esta vía no pasa por el filtro de la suscripción.", "Mandatory: a saved metamodel and a complete event. Not verified in the code: whether it requires gamification enabled, since this route skips the subscription filter."],
        ["Comparte el mismo procesamiento que la vía automática; no existe otra rama de lógica.", "Shares the same processing as the automatic route; there is no separate branch of logic."],
        ["Devuelve el resultado: estado, entradas concedidas, rechazadas, compensadas y avisos.", "Returns the result: status, granted, rejected and compensated entries, and notices."],
        ["El evento enviado se trata igual que uno del bus: idempotencia por identificador, reversión primero, reglas después.", "The event sent is treated like one from the bus: idempotency by identifier, reversal first, rules afterwards."],
        ["POST /api/canonical-metamodels/{metamodelId}/gamification/runtime/events → GamificationRuntimeController.process → service.process() (GamificationRuntimeController.java:20). Sin prueba de la ruta HTTP.", "POST /api/canonical-metamodels/{metamodelId}/gamification/runtime/events → GamificationRuntimeController.process → service.process() (GamificationRuntimeController.java:20). No test for the HTTP path."]] },
    { n: ["Puertas de admisión del evento", "Event admission gates"], d: ["Qué se comprueba antes de mirar las reglas", "What is checked before looking at the rules"], k: "GATES",
      f: [
        ["Tres comprobaciones previas: profundidad, idempotencia y prioridad de reversión.", "Three prior checks: depth, idempotency and reversal priority."],
        ["Evitan bucles, premios duplicados y puntos que deberían haberse anulado.", "They avoid loops, duplicated prizes and points that should have been cancelled."],
        ["Se aplican a todo evento admitido, antes de evaluar ninguna regla.", "Applied to every admitted event, before any rule is evaluated."],
        ["No se configuran. El tamaño máximo del historial, del que depende la idempotencia, sí es un ajuste del servidor (10000 por defecto).", "Not configurable. The maximum ledger size, on which idempotency depends, is a server setting (10000 by default)."],
        ["Preceden a la coincidencia de reglas, la política de repetición y la reconstrucción del estado.", "They precede rule matching, the repeat policy and status rebuilding."],
        ["Un evento descartado, ya aplicado o de reversión no evalúa reglas normales y no genera premios nuevos.", "A dropped, already-applied or reversal event does not evaluate normal rules and generates no new prizes."],
        ["Un evento derivado de gamificación hereda una profundidad creciente; a partir de 16 niveles se ignora. Un evento ya registrado no se repite.", "A derived gamification event inherits a growing depth; from 16 levels on it is ignored. An already-recorded event is not repeated."],
        ["process(): depth(event) >= 16 (línea 98), sourceEventId ya en el historial (línea 102), compensate() antes del bucle de reglas (líneas 108-110), clave evento|regla (línea 113).", "process(): depth(event) >= 16 (line 98), sourceEventId already in the ledger (line 102), compensate() before the rule loop (lines 108-110), event|rule key (line 113)."]] },
    { n: ["Eventos de compensación", "Compensation events"], d: ["Los eventos que deshacen concesiones previas", "The events that undo earlier grants"], k: "COMPENSATION",
      f: [
        ["Eventos que significan «aquello que premiamos se deshizo»: una corrección deshecha, un problema reabierto o un evento que cita explícitamente otro.", "Events meaning 'what we rewarded was undone': an undone fix, a reopened problem or an event explicitly citing another."],
        ["Para que el juego no premie trabajo que ya no es válido.", "So the game does not reward work that is no longer valid."],
        ["Una corrección deshecha, una violación reabierta, o cualquier evento que indique el evento original que revierte; sin un evento original ni un problema citado no se revierte nada.", "An undone fix, a reopened violation, or any event naming the original event it reverses; with neither an original event nor a cited problem nothing is reversed."],
        ["Obligatorio: referencia al evento original o al problema. Opcional: nada más; no se configura en reglas.", "Mandatory: a reference to the original event or the problem. Optional: nothing else; not configured in rules."],
        ["Actúa sobre entradas activas de la misma persona; sustituye a las reglas normales para ese evento.", "Acts on the same person's active entries; replaces the normal rules for that event."],
        ["Las entradas pasan a compensadas, se añade una entrada contraria y el estado se recalcula; si no hay nada que revertir el evento sigue el camino normal.", "Entries become compensated, an opposite entry is added and the status is recalculated; if there is nothing to reverse the event takes the normal path."],
        ["Es el único caso en que un evento no pasa por las reglas normales; si se refiere a un problema sin evento original, solo se revierte la entrada más reciente.", "It is the only case where an event does not go through the normal rules; if it refers to a problem with no original event, only the most recent entry is reversed."],
        ["compensate(): payload.compensatesEventId, payload.diagnosticId o tipos core.quickfix.undone / core.validation.violation-reopened (GamificationRuntimeService:179-208); filtra por actor, por entradas ACTIVE y, con diagnosticId sin evento, toma solo la más reciente.", "compensate(): payload.compensatesEventId, payload.diagnosticId or types core.quickfix.undone / core.validation.violation-reopened (GamificationRuntimeService:179-208); filters by actor, by ACTIVE entries and, with diagnosticId and no event id, takes only the most recent."]] },
    { n: ["Orígenes de datos de una condición", "Condition data roots"], d: ["De dónde puede leer una regla los datos del evento", "Where a rule may read the event's data from"], k: "ROOTS",
      f: [
        ["La parte del evento de la que una condición lee su dato: contenido, metadatos, actor, sujeto, contexto u origen; además, identificador, tipo, correlación y causa.", "The part of the event a condition reads its data from: content, metadata, actor, subject, context or source; plus identifier, type, correlation and causation."],
        ["Para decidir con precisión qué sucesos puntúan: por quién, sobre qué o en qué contexto ocurrieron.", "To decide precisely which occurrences score: by whom, on what or in which context they happened."],
        ["Se resuelve en cada evaluación de una condición con ruta.", "Resolved on each evaluation of a condition with a path."],
        ["Ruta: opcional; sin origen explícito se lee del contenido del evento. Los orígenes se escriben antes del dato, por ejemplo «actor.id».", "Path: optional; with no explicit root it is read from the event content. Roots are written before the data, e.g. 'actor.id'."],
        ["Es la ruta de la condición de una regla; cada regla tiene 0..1 condición.", "It is the path of a rule's condition; each rule has 0..1 condition."],
        ["Un dato no encontrado hace que operadores como «existe» no se cumplan y «no existe» sí.", "A data point not found makes operators such as 'exists' fail and 'not exists' succeed."],
        ["La ruta lee el evento ya verificado; no consulta el modelo ni otros eventos.", "The path reads the already verified event; it does not query the model or other events."],
        ["GamificationEventMatcher.normalizePath (líneas 65-75): prefijos payload., metadata., actor., subject., context., source. y las claves exactas id, eventTypeId, correlationId y causationId; sin prefijo se antepone payload.", "GamificationEventMatcher.normalizePath (lines 65-75): prefixes payload., metadata., actor., subject., context., source. and the exact keys id, eventTypeId, correlationId and causationId; with no prefix payload. is prepended."]] },
    { n: ["Eventos derivados de gamificación", "Derived gamification events"], d: ["Los eventos que el propio motor publica tras procesar", "The events the engine itself publishes after processing"], k: "DERIVED",
      f: [
        ["Eventos de concesión, penalización, rechazo y compensación que el motor publica de vuelta al bus.", "Grant, penalty, rejection and compensation events the engine publishes back to the bus."],
        ["Permiten que otras reglas o flujos de eventos reaccionen a lo que ocurrió en el juego.", "They let other rules or event flows react to what happened in the game."],
        ["Se publican tras procesar un evento: uno por cada entrada concedida, rechazada o compensada.", "Published after processing an event: one per granted, rejected or compensated entry."],
        ["No se configuran. Se pueden usar como evento de una regla o de un flujo, pero la profundidad limita las cadenas a 16 pasos.", "Not configured. They may be used as a rule's or a flow's event, but depth limits chains to 16 steps."],
        ["Los consumen otras reglas de gamificación y los flujos de eventos; vienen del catálogo reservado.", "Consumed by other gamification rules and event flows; they come from the reserved catalog."],
        ["Si la publicación falla solo se registra: el historial y el estado ya están guardados.", "If publishing fails it is only logged: the history and status are already saved."],
        ["Llevan contexto de gamificación, la regla como sujeto y una profundidad incrementada.", "They carry gamification context, the rule as subject and an incremented depth."],
        ["publish(): core.gamification.award-granted / penalty-applied / award-rejected / award-compensated con GamificationRuntimeEventFactory y profundidad depth+1 (GamificationRuntimeService:304-312).", "publish(): core.gamification.award-granted / penalty-applied / award-rejected / award-compensated via GamificationRuntimeEventFactory and depth+1 (GamificationRuntimeService:304-312)."]] }
  ];

  // ---- 2c) Resulting state
  var stateEvt = ["Se recalcula del historial tras cada evento procesado; ningún evento escribe el estado directamente.", "Recalculated from the history after each processed event; no event writes the status directly."];
  var stateItems = [
    { n: ["Puntos de la persona", "Person's points"], d: ["Saldo por tipo de puntos, nunca por debajo del mínimo", "Balance per point type, never below the minimum"], k: "POINTS",
      f: [
        ["El saldo que tiene cada persona en cada tipo de puntos.", "The balance each person has in each point type."],
        ["Responde a «¿cuántos puntos llevo?» sin cálculo manual.", "Answers 'how many points do I have?' without manual calculation."],
        ["Lo mueven las entradas activas de reglas que apuntan a ese tipo de puntos.", "Moved by active entries of rules pointing to that point type."],
        ["No se configura; depende del saldo mínimo del tipo de puntos.", "Not configured; depends on the point type's minimum balance."],
        ["Alimenta el nivel de la persona y los avisos de puntos.", "Feeds the person's level and the points notices."],
        ["Suma de los puntos de entradas activas, recortada al mínimo; una reversión lo corrige.", "Sum of the points of active entries, clipped to the minimum; a reversal corrects it."],
        stateEvt, ["GamificationActorState.points; rebuildState (GamificationRuntimeService:257-295).", "GamificationActorState.points; rebuildState (GamificationRuntimeService:257-295)."]] },
    { n: ["Nivel de la persona", "Person's level"], d: ["El nivel más alto alcanzado por cada tipo de puntos", "The highest level reached per point type"], k: "LEVEL",
      f: [
        ["El rango actual de la persona en cada tipo de puntos.", "The person's current rank in each point type."],
        ["Resume el progreso en una etiqueta comprensible.", "Summarises progress in an understandable label."],
        ["Se alcanza cuando su saldo llega al umbral del nivel.", "Reached when the balance hits the level's threshold."],
        ["No se configura; depende de los niveles definidos.", "Not configured; depends on the levels defined."],
        ["Se deriva del saldo y concede la recompensa del nivel.", "Derived from the balance and grants the level's reward."],
        ["Puede bajar si una reversión o penalización reduce el saldo bajo el umbral; no hay nivel guardado.", "May go down if a reversal or penalty lowers the balance below the threshold; no level is stored."],
        stateEvt, ["GamificationActorState.levels; max por minimumPoints en rebuildState (líneas 284-291). El aviso de nivel alcanzado se calcula aparte en GamificationFeedbackProjector:92-99.", "GamificationActorState.levels; max by minimumPoints in rebuildState (lines 284-291). The level-reached notice is computed separately in GamificationFeedbackProjector:92-99."]] },
    { n: ["Logros de la persona", "Person's achievements"], d: ["Los logros que ha desbloqueado", "The achievements unlocked"], k: "ACHIEVEMENTS",
      f: [
        ["La lista de logros que la persona ha desbloqueado.", "The list of achievements the person has unlocked."],
        ["Muestra los hitos conseguidos, también los ocultos una vez desbloqueados.", "Shows milestones obtained, hidden ones too once unlocked."],
        ["Una entrada activa de una regla de premio que apunta a un logro.", "An active entry of an award rule pointing to an achievement."],
        ["No se configura.", "Not configured."],
        ["Se vincula con la recompensa del logro.", "Linked to the achievement's reward."],
        ["Es un conjunto: un logro desbloqueado dos veces cuenta una; una reversión puede retirarlo.", "It is a set: an achievement unlocked twice counts once; a reversal may remove it."],
        stateEvt, ["GamificationActorState.achievements (conjunto); alimentado desde entry.achievementId() en rebuildState (línea 267).", "GamificationActorState.achievements (set); fed from entry.achievementId() in rebuildState (line 267)."]] },
    { n: ["Avance de retos", "Challenge progress"], d: ["Cuántas veces se ha aplicado cada reto", "How many times each challenge has been applied"], k: "CHALLENGES",
      f: [
        ["El contador de cada reto en curso o completado.", "The counter of each ongoing or completed challenge."],
        ["Responde a «¿cuánto me falta?».", "Answers 'how much is left?'."],
        ["Una unidad por cada entrada activa de una regla que apunta al reto.", "One unit per active entry of a rule pointing to the challenge."],
        ["No se configura; el objetivo es el del reto.", "Not configured; the target is the challenge's."],
        ["Se compara con el objetivo del reto; al alcanzarlo se concede su recompensa.", "Compared with the challenge's target; on reaching it its reward is granted."],
        ["Un reto está completado cuando el avance llega al objetivo; una reversión lo reduce.", "A challenge is completed when progress reaches the target; a reversal reduces it."],
        stateEvt, ["GamificationActorState.challenges (Map<String,Long>); merge(…, 1L) por entrada activa (rebuildState:268); completado si progress >= targetCount (línea 282).", "GamificationActorState.challenges (Map<String,Long>); merge(…, 1L) per active entry (rebuildState:268); completed if progress >= targetCount (line 282)."]] },
    { n: ["Recompensas de la persona", "Person's rewards"], d: ["Las recompensas obtenidas de cualquier origen", "Rewards obtained from any source"], k: "REWARDS",
      f: [
        ["El conjunto de recompensas que la persona tiene.", "The set of rewards the person has."],
        ["Reúne lo concedido por reglas, niveles, logros y retos en un único lugar.", "Gathers what was granted by rules, levels, achievements and challenges in one place."],
        ["Una regla que la apunta, un logro desbloqueado, un reto completado o un nivel alcanzado que la apunta.", "A rule pointing to it, an unlocked achievement, a completed challenge or a reached level pointing to it."],
        ["No se configura.", "Not configured."],
        ["Procede de cuatro orígenes distintos; la misma recompensa nunca se repite.", "Comes from four different origins; the same reward never repeats."],
        ["Es un conjunto sin duplicados; el aviso de recompensa se emite una sola vez por recompensa.", "A set without duplicates; the reward notice is emitted only once per reward."],
        stateEvt, ["GamificationActorState.rewards; rebuildState (líneas 269, 281-290) y unlockReward en el proyector.", "GamificationActorState.rewards; rebuildState (lines 269, 281-290) and unlockReward in the projector."]] },
    { n: ["Reinicio del estado", "Status reset"], d: ["Borrar estado e historial de un metamodelo", "Clear a metamodel's status and history"], k: "RESET",
      f: [
        ["La operación que vacía el estado y el historial del metamodelo.", "The operation that clears the metamodel's status and history."],
        ["Para empezar de cero una competición o tras pruebas.", "To start a competition from scratch or after tests."],
        ["Una petición explícita; ningún evento la activa.", "An explicit request; no event triggers it."],
        ["Obligatorio: identificar el metamodelo. Las definiciones y reglas no se tocan.", "Mandatory: identify the metamodel. Definitions and rules are untouched."],
        ["Afecta al historial, al estado y a la caché de avisos; las definiciones y reglas siguen.", "Affects the history, status and notice cache; definitions and rules remain."],
        ["Vacía puntos, niveles, logros, retos, recompensas e historial; con ello también desaparece la protección contra repeticiones de los eventos ya procesados.", "Clears points, levels, achievements, challenges, rewards and history; the replay protection of already processed events disappears with it."],
        ["Ninguno: es una acción deliberada, no una reacción a eventos.", "None: it is a deliberate action, not a reaction to events."],
        ["DELETE .../gamification/runtime → GamificationRuntimeController.reset → repository.reset e invalida la caché (GamificationRuntimeService:154-156).", "DELETE .../gamification/runtime → GamificationRuntimeController.reset → repository.reset and invalidates the cache (GamificationRuntimeService:154-156)."]] }
  ];

  // ---- 2d) Notice kinds (9) and surfaces (5)
  function kindItem(k, n, d, what, act, eff, sev, impl) {
    return { k: k, n: n, d: d, f: [
      what,
      ["Explica al instante qué ocurrió y qué hacer a continuación.", "Explains right away what happened and what to do next."],
      act,
      ["No se configura por tipo; la política de avisos (global o por regla) decide si se muestra, dónde y durante cuánto tiempo, y el título y el siguiente paso pueden cambiarse por regla a mano.", "Not configured by kind; the notice policy (global or per rule) decides whether it shows, where and for how long, and the title and next step may be changed per rule by hand."],
      ["Se calcula desde una entrada del historial; puede incluir una acción para abrir el problema relacionado.", "Calculated from a history entry; may include an action to open the related problem."],
      eff, ["Lo calcula el motor al consultar los avisos, no hay evento propio: nace de las entradas del historial. " + sev, "Computed by the engine when notices are requested; there is no event of its own: it is born from history entries. " + sev],
      impl] };
  }
  var kindItems = [
    kindItem("POINTS_AWARDED", ["Puntos concedidos", "Points awarded"], ["Se concedieron puntos", "Points were granted"],
      ["Aviso de que se sumaron puntos.", "Notice that points were added."], ["Una entrada activa con puntos positivos.", "An active entry with positive points."],
      ["Muestra «+N» con el nombre del tipo de puntos y el saldo actual.", "Shows '+N' with the point type's name and the current balance."], "Gravedad: éxito.",
      ["UserFeedbackKind.POINTS_AWARDED, SUCCESS (GamificationFeedbackProjector:64-72).", "UserFeedbackKind.POINTS_AWARDED, SUCCESS (GamificationFeedbackProjector:64-72)."]),
    kindItem("PENALTY_APPLIED", ["Penalización aplicada", "Penalty applied"], ["Se restaron puntos", "Points were subtracted"],
      ["Aviso de que se restaron puntos.", "Notice that points were subtracted."], ["Una entrada activa con puntos negativos.", "An active entry with negative points."],
      ["Muestra la cantidad restada y recomienda revisar el problema relacionado.", "Shows the amount subtracted and recommends reviewing the related problem."], "Gravedad: advertencia.",
      ["UserFeedbackKind.PENALTY_APPLIED, WARNING (GamificationFeedbackProjector:65-72).", "UserFeedbackKind.PENALTY_APPLIED, WARNING (GamificationFeedbackProjector:65-72)."]),
    kindItem("ACHIEVEMENT_UNLOCKED", ["Logro desbloqueado", "Achievement unlocked"], ["Se desbloqueó un logro", "An achievement was unlocked"],
      ["Aviso de un logro nuevo.", "Notice of a new achievement."], ["Una entrada activa que apunta a un logro que la persona aún no tenía.", "An active entry pointing to an achievement the person did not yet have."],
      ["Muestra el nombre y la descripción del logro y dispara el aviso de su recompensa.", "Shows the achievement's name and description and triggers its reward notice."], "Gravedad: éxito.",
      ["UserFeedbackKind.ACHIEVEMENT_UNLOCKED, SUCCESS; condición state.achievements.add(...) (GamificationFeedbackProjector:74-79).", "UserFeedbackKind.ACHIEVEMENT_UNLOCKED, SUCCESS; condition state.achievements.add(...) (GamificationFeedbackProjector:74-79)."]),
    kindItem("LEVEL_REACHED", ["Nivel alcanzado", "Level reached"], ["Se subió de nivel", "A level-up occurred"],
      ["Aviso de nivel nuevo.", "Notice of a new level."], ["Una entrada activa tras la cual el nivel más alto alcanzado cambia respecto al anterior.", "An active entry after which the highest level reached differs from the previous one."],
      ["Muestra el nombre del nivel y el saldo que lo alcanzó; dispara el aviso de su recompensa.", "Shows the level's name and the balance that reached it; triggers its reward notice."], "Gravedad: éxito.",
      ["UserFeedbackKind.LEVEL_REACHED, SUCCESS (GamificationFeedbackProjector:92-99).", "UserFeedbackKind.LEVEL_REACHED, SUCCESS (GamificationFeedbackProjector:92-99)."]),
    kindItem("CHALLENGE_PROGRESS", ["Avance de reto", "Challenge progress"], ["Un reto avanzó sin completarse", "A challenge advanced without completing"],
      ["Aviso de avance parcial de un reto.", "Notice of a challenge's partial progress."], ["Una entrada activa que apunta a un reto cuyo avance aún no llega al objetivo.", "An active entry pointing to a challenge whose progress has not yet reached the target."],
      ["Muestra «n / objetivo».", "Shows 'n / target'."], "Gravedad: informativa.",
      ["UserFeedbackKind.CHALLENGE_PROGRESS, INFO (GamificationFeedbackProjector:81-88).", "UserFeedbackKind.CHALLENGE_PROGRESS, INFO (GamificationFeedbackProjector:81-88)."]),
    kindItem("CHALLENGE_COMPLETED", ["Reto completado", "Challenge completed"], ["Un reto llegó a su objetivo", "A challenge reached its target"],
      ["Aviso de reto completado.", "Notice of a completed challenge."], ["Una entrada activa tras la cual el avance del reto alcanza o supera el objetivo.", "An active entry after which the challenge's progress reaches or exceeds the target."],
      ["Muestra el reto completado y dispara el aviso de su recompensa.", "Shows the completed challenge and triggers its reward notice."], "Gravedad: éxito.",
      ["UserFeedbackKind.CHALLENGE_COMPLETED, SUCCESS (GamificationFeedbackProjector:84-89). El proyector sigue emitiéndolo si el avance pasa del objetivo.", "UserFeedbackKind.CHALLENGE_COMPLETED, SUCCESS (GamificationFeedbackProjector:84-89). The projector keeps emitting it if progress goes beyond the target."]),
    kindItem("REWARD_UNLOCKED", ["Recompensa desbloqueada", "Reward unlocked"], ["Se concedió una recompensa", "A reward was granted"],
      ["Aviso de una recompensa nueva.", "Notice of a new reward."], ["Una entrada activa, un logro, un reto completado o un nivel que apuntan a una recompensa que la persona aún no había recibido.", "An active entry, an achievement, a completed challenge or a level pointing to a reward the person had not received yet."],
      ["Muestra el nombre y la descripción de la recompensa; se avisa una sola vez por recompensa.", "Shows the reward's name and description; notified only once per reward."], "Gravedad: éxito.",
      ["UserFeedbackKind.REWARD_UNLOCKED, SUCCESS; unlockReward() deduplica con state.rewards.add (GamificationFeedbackProjector:105-107).", "UserFeedbackKind.REWARD_UNLOCKED, SUCCESS; unlockReward() deduplicates with state.rewards.add (GamificationFeedbackProjector:105-107)."]),
    kindItem("APPLICATION_REJECTED", ["Aplicación rechazada", "Application rejected"], ["Una regla encajó pero no se aplicó", "A rule matched but was not applied"],
      ["Aviso de que no se concedió algo que la regla preveía.", "Notice that something the rule foresaw was not granted."], ["Una entrada rechazada, solo si la política de avisos incluye rechazos (apagado por defecto).", "A rejected entry, only if the notice policy includes rejections (off by default)."],
      ["Muestra el motivo y recomienda seguir trabajando.", "Shows the reason and recommends keep working."], "Gravedad: informativa.",
      ["UserFeedbackKind.APPLICATION_REJECTED, INFO; filtrado por policy.includeRejected() (GamificationFeedbackProjector:45-49).", "UserFeedbackKind.APPLICATION_REJECTED, INFO; filtered by policy.includeRejected() (GamificationFeedbackProjector:45-49)."]),
    kindItem("AWARD_COMPENSATED", ["Concesión compensada", "Award compensated"], ["Una concesión previa se revirtió", "An earlier grant was reverted"],
      ["Aviso de que se revirtió una concesión previa.", "Notice that an earlier grant was reverted."], ["Una entrada contraria creada por un evento de reversión.", "An opposite entry created by a reversal event."],
      ["Muestra los puntos devueltos y el motivo; el aviso original se omite.", "Shows the points returned and the reason; the original notice is skipped."], "Gravedad: informativa según el proyector (el contenido existente la sitúa en advertencia).",
      ["UserFeedbackKind.AWARD_COMPENSATED, INFO (GamificationFeedbackProjector:51-57).", "UserFeedbackKind.AWARD_COMPENSATED, INFO (GamificationFeedbackProjector:51-57)."])
  ];

  function surf(k, n, d, shown, impl) {
    return { k: k, n: n, d: d, f: [
      [d[0] + ".", d[1] + "."],
      ["Para elegir cómo y dónde ve la persona los avisos sin cambiar las reglas.", "To choose how and where the person sees notices without changing the rules."],
      ["Se activa cuando la política de avisos la incluye entre sus superficies y llega un aviso permitido.", "Triggered when the notice policy includes it among its surfaces and an allowed notice arrives."],
      ["Opcional: se elige en la política de avisos; por defecto están activas las emergentes y la lista de actividad.", "Optional: chosen in the notice policy; pop-ups and the activity list are on by default."],
      ["Se rige por la política de avisos; muestra avisos calculados desde el historial.", "Governed by the notice policy; shows notices calculated from the history."],
      shown,
      ["Reacciona al resultado de eventos ya procesados, no a eventos directamente.", "Reacts to the outcome of already processed events, not directly to events."],
      impl] };
  }
  var surfItems = [
    surf("TOAST", ["Emergente temporal", "Temporary pop-up"], ["Mensaje que aparece unos segundos y se retira", "A message that shows for a few seconds and goes away"],
      ["Tiene pantalla: aparece durante la duración fijada (4 s por defecto), con un máximo de visibles (3 por defecto).", "Has a screen: appears for the set duration (4 s by default), with a maximum number visible (3 by default)."],
      ["UserFeedbackSurface.TOAST; presentada en GamificationFeedbackCenter.tsx, que consulta GET .../gamification/runtime/feedback por sondeo.", "UserFeedbackSurface.TOAST; rendered in GamificationFeedbackCenter.tsx, which polls GET .../gamification/runtime/feedback."]),
    surf("ACTIVITY", ["Lista de actividad", "Activity list"], ["Histórico visible de avisos recientes", "Visible record of recent notices"],
      ["Tiene pantalla: conserva los avisos para consultarlos después, si la política los persiste.", "Has a screen: keeps notices for later reading, if the policy persists them."],
      ["UserFeedbackSurface.ACTIVITY; persistActivity=true por defecto (UserFeedbackPresentationPolicy:17); presentada en GamificationFeedbackCenter.tsx.", "UserFeedbackSurface.ACTIVITY; persistActivity=true by default (UserFeedbackPresentationPolicy:17); rendered in GamificationFeedbackCenter.tsx."]),
    surf("STATUS", ["Estado fijo", "Status area"], ["Zona fija de estado con el saldo o nivel", "A fixed status area with balance or level"],
      ["Se puede configurar, pero hoy ninguna pantalla la muestra: elegirla no produce ningún efecto visible.", "Can be configured, but no screen displays it today: choosing it has no visible effect."],
      ["UserFeedbackSurface.STATUS declarada sin consumidor en el frontend (solo TOAST y ACTIVITY tienen pantalla).", "UserFeedbackSurface.STATUS declared with no frontend consumer (only TOAST and ACTIVITY have a screen)."]),
    surf("PROGRESS", ["Barra de progreso", "Progress bar"], ["Indicador de avance hacia un reto o nivel", "Indicator of progress toward a challenge or level"],
      ["Se puede configurar, pero hoy ninguna pantalla la muestra; los datos de progreso sí viajan en el aviso.", "Can be configured, but no screen displays it today; the progress data does travel in the notice."],
      ["UserFeedbackSurface.PROGRESS declarada sin consumidor; el progreso se envía en progressCurrent / progressTarget del sobre (GamificationFeedbackProjector:110-115).", "UserFeedbackSurface.PROGRESS declared with no consumer; progress is sent in progressCurrent / progressTarget of the envelope (GamificationFeedbackProjector:110-115)."]),
    surf("DIALOG", ["Diálogo", "Dialog"], ["Ventana de diálogo para avisos importantes", "A dialog window for important notices"],
      ["Se puede configurar, pero hoy ninguna pantalla la muestra.", "Can be configured, but no screen displays it today."],
      ["UserFeedbackSurface.DIALOG declarada sin consumidor en el frontend.", "UserFeedbackSurface.DIALOG declared with no frontend consumer."])
  ];

  function el(id, n, mean, purpose, when, items, io, effect, evid) {
    return { id: id, n: n, kind: "variant-set", req: "OPTIONAL",
      mean: mean, purpose: purpose, when: when, card: "1 evento / metamodelo ↔ 0..N",
      variants: vars(items),
      props: [{ n: T("Sin ajustes propios", "No settings of its own"), req: "OPTIONAL", d: T("Cada variante indica qué es obligatorio u opcional", "Each variant says what is mandatory or optional") }],
      io: io, effect: effect,
      func: { rel: [T("Se relaciona con reglas, historial, estado y avisos según la variante", "Relates to rules, history, status and notices depending on the variant")], lifecycle: T("según la variante", "depending on the variant"), cond: T("Ver la ficha de cada variante.", "See each variant's card.") },
      tech: { type: evid.type, schema: evid.schema, evidence: evid.evidence } };
  }
  var EV = function(path, line, label){ return { path: path, line: line, label: label }; };

  var newEls = [
    el("gm_elements", T("Elementos del juego, uno a uno", "Game elements, one by one"),
      T("Ficha completa de cada elemento que se define en el catálogo: tipo de puntos, nivel, logro, reto, recompensa y regla.", "A complete card for each element defined in the catalog: point type, level, achievement, challenge, reward and rule."),
      T("Responder, para cada elemento, qué es, por qué definirlo, qué lo activa, cómo se configura y qué produce.", "Answer, for each element, what it is, why define it, what triggers it, how it is configured and what it produces."),
      T("Al diseñar el catálogo, para decidir qué elementos crear.", "When designing the catalog, to decide which elements to create."),
      elemItems, { i: T("Definición del elemento", "Element definition"), o: T("Elemento utilizable por las reglas", "Element usable by rules") },
      T("Define lo que el motor podrá conceder.", "Defines what the engine can grant."),
      { type: "GamificationDefinitionModel / GamificationBindingModel", schema: "pointTypes, levels, achievements, challenges, rewards + rules", evidence: [EV(G + "GamificationRule.java", 8, "GamificationRule"), EV(RT, 257, "rebuildState")] }),
    el("gm_triggers", T("Disparadores y condiciones", "Triggers and conditions"),
      T("Cómo llega un evento verificado hasta una regla: suscripción al bus, entrada HTTP, puertas de admisión, eventos de compensación, orígenes de datos de las condiciones y eventos derivados.", "How a verified event reaches a rule: bus subscription, HTTP entry, admission gates, compensation events, condition data roots and derived events."),
      T("Dejar claro qué activa el juego y qué lo detiene.", "Make clear what activates the game and what stops it."),
      T("Al entender por qué una regla se activó, no se activó o se revirtió.", "When understanding why a rule fired, did not fire or was reversed."),
      trigItems, { i: T("Evento verificado", "Verified event"), o: T("Reglas evaluadas o reversión", "Rules evaluated or reversal") },
      T("Decide si un evento llega a evaluar reglas y cuáles.", "Decides whether an event gets to evaluate rules and which ones."),
      { type: "GamificationRuntimeService + GamificationEventMatcher + GamificationRuntimeController", schema: "bus subscription | POST runtime/events | compensation | path roots", evidence: [EV(RT, 82, "subscribe"), EV(RT, 108, "compensación primero"), EV(MT, 65, "normalización de rutas"), EV(CTL, 20, "entrada HTTP")] }),
    el("gm_result", T("Estado resultante", "Resulting status"),
      T("Lo que queda de cada persona tras procesar eventos: puntos, nivel, logros, avance de retos, recompensas y su reinicio.", "What is left for each person after processing events: points, level, achievements, challenge progress, rewards and their reset."),
      T("Mostrar el resultado visible del juego y cómo se corrige.", "Show the game's visible result and how it is corrected."),
      T("Al revisar cómo va una persona o al reiniciar una competición.", "When checking how a person is doing or restarting a competition."),
      stateItems, { i: T("Historial", "History"), o: T("Estado por persona", "Per-person status") },
      T("Siempre reconstruible desde el historial; no se edita a mano.", "Always rebuildable from the history; never edited by hand."),
      { type: "GamificationRuntimeState / GamificationActorState", schema: "actors map + processedAwardKeys", evidence: [EV(RT, 257, "rebuildState"), EV(RT, 154, "reset")] }),
    el("gm_notice_kinds", T("Tipos de aviso", "Notice kinds"),
      T("Los nueve tipos de aviso que el motor puede calcular a partir del historial.", "The nine notice kinds the engine can compute from the history."),
      T("Documentar qué mensaje recibe la persona en cada situación.", "Document which message the person receives in each situation."),
      T("Al decidir qué mostrar y cómo personalizarlo.", "When deciding what to show and how to customise it."),
      kindItems, { i: T("Entradas del historial + política", "History entries + policy"), o: T("Avisos con título, mensaje y siguiente paso", "Notices with title, message and next step") },
      T("No se almacenan: se calculan al consultarlos.", "Not stored: calculated when requested."),
      { type: "UserFeedbackKind / UserFeedbackEnvelope", schema: "POINTS_AWARDED | PENALTY_APPLIED | ACHIEVEMENT_UNLOCKED | LEVEL_REACHED | CHALLENGE_PROGRESS | CHALLENGE_COMPLETED | REWARD_UNLOCKED | APPLICATION_REJECTED | AWARD_COMPENSATED", evidence: [EV(FP, 28, "proyección")] }),
    el("gm_surfaces", T("Superficies de aviso", "Notice surfaces"),
      T("Los cinco lugares donde la política de avisos puede dirigir un aviso.", "The five places where the notice policy may direct a notice."),
      T("Distinguir qué superficies existen de verdad en pantalla y cuáles solo están declaradas.", "Tell which surfaces really exist on screen and which are only declared."),
      T("Al configurar la política de avisos.", "When setting the notice policy."),
      surfItems, { i: T("Aviso permitido", "Allowed notice"), o: T("Presentación en pantalla", "On-screen presentation") },
      T("Solo las emergentes y la lista de actividad se ven hoy.", "Only pop-ups and the activity list are seen today."),
      { type: "UserFeedbackSurface", schema: "TOAST | ACTIVITY | STATUS | PROGRESS | DIALOG", evidence: [EV(G + "feedback/UserFeedbackSurface.java", 2, "superficies")] })
  ];

  HM.extend("gamification", {
    elements: newEls,
    mapNodes: [
      { id: "gm_elements", elem: "gm_elements", n: T("Elementos del juego", "Game elements"), k: "concept", req: "OPTIONAL", d: T("Fichas de cada elemento", "Cards for each element") },
      { id: "gm_triggers", elem: "gm_triggers", n: T("Disparadores", "Triggers"), k: "concept", req: "OPTIONAL", d: T("Cómo un evento llega a una regla", "How an event reaches a rule") },
      { id: "gm_result", elem: "gm_result", n: T("Estado resultante", "Resulting status"), k: "derived", req: "DERIVED", d: T("Puntos, nivel, logros, retos", "Points, level, achievements, challenges") },
      { id: "gm_notice_kinds", elem: "gm_notice_kinds", n: T("Tipos de aviso", "Notice kinds"), k: "derived", req: "DERIVED", d: T("Nueve mensajes posibles", "Nine possible messages") },
      { id: "gm_surfaces", elem: "gm_surfaces", n: T("Superficies de aviso", "Notice surfaces"), k: "concept", req: "OPTIONAL", d: T("Dónde se muestran", "Where they show") }
    ],
    mapEdges: [
      { a: "gm_triggers", b: "rule", rel: "ref", card1: "N", card2: "1" },
      { a: "gm_triggers", b: "event", rel: "ref", card1: "N", card2: "1" },
      { a: "gm_elements", b: "catalog", rel: "ref", card1: "N", card2: "1" },
      { a: "gm_result", b: "state", rel: "ref", card1: "1", card2: "1" },
      { a: "gm_notice_kinds", b: "feedback", rel: "ref", card1: "N", card2: "1" },
      { a: "gm_surfaces", b: "policy", rel: "ref", card1: "N", card2: "1" }
    ]
  });

  function lensEl(id, nf, fm, tm, items) { var o = {}; o[id] = { nf: nf, fm: fm, tm: tm, variants: lensVars(items) }; return o; }
  var le = {};
  [
    lensEl("gm_elements", T("Elementos del juego (tipo de puntos, nivel, logro, reto, recompensa, regla)", "Game elements (point type, level, achievement, challenge, reward, rule)"),
      T("Cada elemento del catálogo se describe con las mismas preguntas: qué representa, por qué definirlo, qué lo activa, qué configuración exige, con qué se relaciona y qué produce.", "Each catalog element is described with the same questions: what it represents, why define it, what triggers it, what configuration it demands, what it relates to and what it produces."),
      T("Cada elemento corresponde a una definición del catálogo (PointTypeDefinition, LevelDefinition, AchievementDefinition, ChallengeDefinition, RewardDefinition) o a una GamificationRule.", "Each element corresponds to a catalog definition (PointTypeDefinition, LevelDefinition, AchievementDefinition, ChallengeDefinition, RewardDefinition) or to a GamificationRule."), elemItems),
    lensEl("gm_triggers", T("Disparadores y condiciones (cómo un evento verificado llega a una regla)", "Triggers and conditions (how a verified event reaches a rule)"),
      T("Un evento llega al motor por el bus o por una petición HTTP, pasa tres puertas (activación y profundidad, idempotencia y reversión) y solo entonces se evalúan las condiciones de las reglas sobre datos del propio evento.", "An event reaches the engine via the bus or an HTTP request, passes three gates (activation and depth, idempotency and reversal) and only then are rule conditions evaluated against the event's own data."),
      T("GamificationRuntimeService.subscribe y process, GamificationRuntimeController y GamificationEventMatcher.", "GamificationRuntimeService.subscribe and process, GamificationRuntimeController and GamificationEventMatcher."), trigItems),
    lensEl("gm_result", T("Estado resultante (puntos, nivel, logros, retos, recompensas)", "Resulting status (points, level, achievements, challenges, rewards)"),
      T("Todo el estado visible de una persona se recalcula del historial tras cada evento; no se edita a mano y un reinicio lo vacía.", "A person's whole visible status is recalculated from the history after each event; it is not edited by hand and a reset clears it."),
      T("GamificationActorState dentro de GamificationRuntimeState, reconstruido en rebuildState.", "GamificationActorState inside GamificationRuntimeState, rebuilt in rebuildState."), stateItems),
    lensEl("gm_notice_kinds", T("Tipos de aviso (nueve resultados posibles)", "Notice kinds (nine possible outcomes)"),
      T("Nueve tipos de mensaje calculados al consultar, con gravedad de éxito, información o advertencia.", "Nine message kinds computed on request, with success, info or warning severity."),
      T("UserFeedbackKind proyectado por GamificationFeedbackProjector sobre las entradas del historial.", "UserFeedbackKind projected by GamificationFeedbackProjector over the history entries."), kindItems),
    lensEl("gm_surfaces", T("Superficies de aviso (cinco destinos)", "Notice surfaces (five destinations)"),
      T("La política de avisos puede dirigir un aviso a cinco lugares, pero hoy solo se ven las emergentes y la lista de actividad.", "The notice policy may direct a notice to five places, but today only pop-ups and the activity list are seen."),
      T("UserFeedbackSurface declarado con cinco valores; el frontend solo implementa TOAST y ACTIVITY.", "UserFeedbackSurface declared with five values; the frontend only implements TOAST and ACTIVITY."), surfItems)
  ].forEach(function(o){ Object.keys(o).forEach(function(k){ le[k] = o[k]; }); });
  HM.lens({ caps: { gamification: { elements: le } } });
})();
