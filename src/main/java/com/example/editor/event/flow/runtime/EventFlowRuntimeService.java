package com.example.editor.event.flow.runtime;

import static com.example.editor.event.flow.runtime.EventFlowRuntimeSupport.depth;
import static com.example.editor.event.flow.runtime.EventFlowRuntimeSupport.groupingKey;
import static com.example.editor.event.flow.runtime.EventFlowRuntimeSupport.message;
import static com.example.editor.event.flow.runtime.EventFlowRuntimeSupport.selectTransitions;
import static com.example.editor.event.flow.runtime.EventFlowRuntimeSupport.withRuntimePayload;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.EventDefinitionModel;
import com.example.editor.event.EventModelApplicationException;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.bus.CanonicalEventBus;
import com.example.editor.event.bus.EventSubscription;
import com.example.editor.event.flow.EventFlowApplicationException;
import com.example.editor.event.flow.EventFlowApplicationService;
import com.example.editor.event.flow.EventFlowModel;
import com.example.editor.event.flow.EventFlowProperties;
import com.example.editor.event.flow.FlowDefinition;
import com.example.editor.event.flow.FlowNode;
import com.example.editor.event.flow.FlowNodeKind;
import com.example.editor.event.flow.FlowTransition;
import com.example.editor.event.flow.FlowTransitionKind;
import jakarta.annotation.PreDestroy;

/** Runtime neutral de EventFlowModel. No contiene reglas de gamificación. */
@Service
public class EventFlowRuntimeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventFlowRuntimeService.class);
    private final EventFlowApplicationService flowService;
    private final EventModelApplicationService eventModelService;
    private final EventFlowCompiler compiler;
    private final CanonicalEventBus eventBus;
    private final EventFlowRuntimeNodeExecutor nodeExecutor;
    private final EventFlowProperties properties;
    private final EventFlowRuntimeJournalService journal;
    private final EventFlowRuntimeDeploymentRegistry deployments;
    private final EventFlowConditionEvaluator conditionEvaluator;
    private final Set<String> explicitlyStopped = new CopyOnWriteArraySet<>();
    private final EventSubscription dispatcherSubscription;

    /** Descripción: Inicializa una instancia de EventFlowRuntimeService con sus dependencias y datos de entrada. Entradas: flowService: servicio colaborador utilizado por la operación; eventModelService: servicio colaborador utilizado por la operación; compiler: dato de entrada necesario para la operación; eventBus: dato de entrada necesario para la operación; externalExecutors: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public EventFlowRuntimeService(EventFlowApplicationService flowService, EventModelApplicationService eventModelService,
            EventFlowCompiler compiler, CanonicalEventBus eventBus, List<FlowExternalStepExecutor> externalExecutors,
            EventFlowProperties properties) {
        this(flowService, eventModelService, compiler, eventBus, externalExecutors, properties, null, null);
    }

    @Autowired
    /** Descripción: Inicializa una instancia de EventFlowRuntimeService con sus dependencias y datos de entrada. Entradas: flowService: servicio colaborador utilizado por la operación; eventModelService: servicio colaborador utilizado por la operación; compiler: dato de entrada necesario para la operación; eventBus: dato de entrada necesario para la operación; externalExecutors: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; journal: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EventFlowRuntimeService(EventFlowApplicationService flowService, EventModelApplicationService eventModelService,
            EventFlowCompiler compiler, CanonicalEventBus eventBus, List<FlowExternalStepExecutor> externalExecutors,
            EventFlowProperties properties, EventFlowRuntimeJournalService journal, EventFlowConditionEvaluator conditionEvaluator) {
        this.flowService = flowService;
        this.eventModelService = eventModelService;
        this.compiler = compiler;
        this.eventBus = eventBus;
        this.nodeExecutor = new EventFlowRuntimeNodeExecutor(externalExecutors);
        this.properties = properties;
        this.journal = journal;
        this.conditionEvaluator = conditionEvaluator;
        this.deployments = new EventFlowRuntimeDeploymentRegistry(properties.getMaximumRuntimeDeployments());
        EventSubscription dispatcher = eventBus.subscribe(event -> true, this::onEvent);
        this.dispatcherSubscription = dispatcher == null ? () -> { } : dispatcher;
    }

    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de FlowResult. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ExecutableEventFlowPlan compile(String metamodelId) {
        var loaded = flowService.find(metamodelId);
        if(!loaded.checkReport().valid()) throw new EventFlowApplicationException(EventFlowApplicationException.Reason.NOT_EXECUTABLE,
                "EventFlowModel contains blocking semantic diagnostics");
        return compiler.compile(loaded.eventFlowModel(), definitions(metamodelId));
    }

    /** Activa explícitamente un ámbito. Un único dispatcher global evita suscripciones duplicadas por ámbito. */ /** Descripción: Crea o prepara el resultado de «start» dentro de FlowResult. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EventFlowRuntimeStatus start(String metamodelId) {
        explicitlyStopped.remove(metamodelId);
        EventFlowRuntimeDeployment existing = deployments.get(metamodelId);
        if(existing != null && existing.active) return status(existing);
        EventFlowRuntimeDeployment deployment = deployment(metamodelId, compile(metamodelId));
        deployment.active = true;
        EventFlowRuntimeDeployment previous = deployments.put(metamodelId, deployment);
        if(previous != null && previous != deployment) previous.close();
        return status(deployment);
    }

    /** Detiene un ámbito y bloquea su autoarranque hasta un start explícito. */ /** Descripción: Ejecuta la operación «stop» correspondiente a la responsabilidad de FlowResult. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EventFlowRuntimeStatus stop(String metamodelId) {
        if(explicitlyStopped.size() >= properties.getMaximumRuntimeDeployments()) {
            explicitlyStopped.stream().findFirst().ifPresent(explicitlyStopped::remove);
        }
        explicitlyStopped.add(metamodelId);
        EventFlowRuntimeDeployment deployment = deployments.remove(metamodelId);
        if(deployment == null) return new EventFlowRuntimeStatus(metamodelId, false, 0, 0, 0, 0, "", Instant.now());
        deployment.close();
        return status(deployment);
    }

    /** Recarga el plan conservando si el ámbito estaba activo o explícitamente detenido. */ /** Descripción: Ejecuta la operación «refresh» correspondiente a la responsabilidad de FlowResult. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EventFlowRuntimeStatus refresh(String metamodelId) {
        EventFlowRuntimeDeployment previous = deployments.remove(metamodelId);
        boolean active = previous != null && previous.active && !explicitlyStopped.contains(metamodelId);
        if(previous != null) previous.close();
        if(active) return start(metamodelId);
        EventFlowRuntimeDeployment deployment = deployment(metamodelId, compile(metamodelId));
        return status(deployment);
    }

    /** Descripción: Ejecuta la operación «status» correspondiente a la responsabilidad de FlowResult. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EventFlowRuntimeStatus status(String metamodelId) {
        EventFlowRuntimeDeployment deployment = deployments.get(metamodelId);
        if(deployment == null) return new EventFlowRuntimeStatus(metamodelId, false, 0, 0, 0, 0, "", Instant.now());
        return status(deployment);
    }

    /** Descripción: Procesa la operación «process» coordinando las responsabilidades de FlowResult. Entradas: metamodelId: identificador que delimita el recurso objetivo; event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EventFlowProcessResult process(String metamodelId, CanonicalEvent event) {
        if(event == null) throw new EventFlowApplicationException(EventFlowApplicationException.Reason.INVALID_REQUEST, "CanonicalEvent is required");
        EventFlowRuntimeDeployment deployment = deployments.compute(metamodelId, id -> deployment(id, compile(id)));
        return processWithDeployment(deployment, event, true);
    }

    /** Descripción: Ejecuta la operación «tick» correspondiente a la responsabilidad de FlowResult. Entradas: metamodelId: identificador que delimita el recurso objetivo; now: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EventFlowProcessResult tick(String metamodelId, Instant now) {
        EventFlowRuntimeDeployment deployment = deployments.compute(metamodelId, id -> deployment(id, compile(id)));
        Instant effectiveNow = now == null ? Instant.now() : now;
        List<CanonicalEvent> emitted = new ArrayList<>();
        List<FlowExecutionTrace> traces = new ArrayList<>();
        synchronized(deployment.state) {
            for(FlowResult retried : retryDue(deployment, effectiveNow)) {
                emitted.addAll(retried.emitted());
                traces.add(retried.trace());
            }
            List<FlowAbsenceContinuation> due = deployment.state.absenceWatches.values().stream()
                    .filter(watch -> !watch.deadline().isAfter(effectiveNow)).sorted(Comparator.comparing(watch -> watch.deadline())).toList();
            for(FlowAbsenceContinuation watch : due) {
                deployment.state.absenceWatches.remove(watch.id());
                int depth = depth(watch.sourceEvent()) + 1;
                if(depth > properties.getMaximumExecutionDepth()) continue;
                CanonicalEvent derived = EventFlowRuntimeEventFactory.derived(metamodelId, watch.flowId(), watch.nodeId(),
                        watch.outputEventTypeId(), watch.sourceEvent(), withRuntimePayload(watch.sourceEvent().payload(), Map.of(
                                "absenceKey", watch.key(), "absenceDeadline", watch.deadline().toString(), "absenceWatchEventType", watch.watchEventTypeId())), depth);
                emitted.add(derived);
                traces.add(new FlowExecutionTrace("trace:" + UUID.randomUUID(), watch.flowId(), watch.sourceEvent().id(), watch.createdAt(),
                        effectiveNow, FlowExecutionStatus.COMPLETED,
                        List.of(new FlowNodeExecution(watch.nodeId(), FlowNodeKind.ABSENCE, FlowExecutionStatus.COMPLETED, "Absence condition elapsed", effectiveNow)),
                        List.of(derived.id()), "Absence event emitted"));
            }
            for(FlowResult timedOut : timeoutWaiting(deployment, effectiveNow)) {
                emitted.addAll(timedOut.emitted());
                traces.add(timedOut.trace());
            }
            deployment.state.traces.addAll(traces);
            trimTraces(deployment.state.traces);
        }
        persist(deployment);
        publish(emitted);
        deployment.emittedEvents.addAndGet(emitted.size());
        deployment.updatedAt = Instant.now();
        return new EventFlowProcessResult(metamodelId, "tick:" + effectiveNow, emitted, traces);
    }

    /** Descripción: Elimina o libera el recurso gestionado por «clearState» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void clearState(String metamodelId) {
        EventFlowRuntimeDeployment deployment = deployments.get(metamodelId);
        if(deployment != null) synchronized(deployment.state) { deployment.state.clear(); }
        if(journal != null) journal.clear(metamodelId);
    }

    /** Descripción: Ejecuta la operación «traces» correspondiente a la responsabilidad de FlowResult. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<FlowExecutionTrace> traces(String metamodelId) {
        EventFlowRuntimeDeployment deployment = deployments.get(metamodelId);
        if(deployment != null) synchronized(deployment.state) { return List.copyOf(deployment.state.traces); }
        return journal == null ? List.of() : journal.traces(metamodelId);
    }

    /** Descripción: Ejecuta la operación «snapshot» correspondiente a la responsabilidad de FlowResult. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EventFlowRuntimeSnapshot snapshot(String metamodelId) {
        EventFlowRuntimeDeployment deployment = deployments.get(metamodelId);
        if(deployment == null) return journal == null
                ? new EventFlowRuntimeSnapshot("1.0", metamodelId, metamodelId, Instant.now(), List.of(), List.of(), List.of(), EventFlowRuntimeStateDocument.empty())
                : journal.load(metamodelId);
        synchronized(deployment.state) {
            return new EventFlowRuntimeSnapshot("1.0", metamodelId, metamodelId, Instant.now(),
                    List.copyOf(deployment.state.waitingContinuations.values()),
                    List.copyOf(deployment.state.retryContinuations.values()), List.copyOf(deployment.state.traces),
                    deployment.state.snapshot());
        }
    }

    @PreDestroy
    /** Libera el dispatcher único y todos los estados de ejecución. */ /** Descripción: Elimina o libera el recurso gestionado por «close» de forma controlada. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void close() {
        dispatcherSubscription.close();
        deployments.all().forEach(EventFlowRuntimeDeployment::close);
        deployments.clear();
        explicitlyStopped.clear();
    }

    /** Descripción: Procesa la operación «processWithDeployment» coordinando las responsabilidades de FlowResult. Entradas: deployment: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación; publishDerived: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EventFlowProcessResult processWithDeployment(EventFlowRuntimeDeployment deployment, CanonicalEvent event, boolean publishDerived) {
        int depth = depth(event);
        if(depth >= properties.getMaximumExecutionDepth()) {
            return new EventFlowProcessResult(deployment.metamodelId, event.id(), List.of(), List.of(new FlowExecutionTrace(
                    "trace:" + UUID.randomUUID(), "", event.id(), Instant.now(), Instant.now(), FlowExecutionStatus.DEPTH_LIMIT,
                    List.of(), List.of(), "Maximum flow depth reached")));
        }
        List<CanonicalEvent> emitted = new ArrayList<>();
        List<FlowExecutionTrace> traces = new ArrayList<>();
        synchronized(deployment.state) {
            cancelAbsenceWatches(deployment.state, event);
            for(FlowResult resumed : resumeWaiting(deployment, event, depth + 1)) {
                emitted.addAll(resumed.emitted());
                traces.add(resumed.trace());
            }
            Set<String> selected = new LinkedHashSet<>(deployment.plan.flowsByTriggerEventType().getOrDefault(event.eventTypeId(), List.of()));
            for(ExecutableFlowDefinition flow : deployment.plan.flows()) {
                if(selected.contains(flow.source().id()) && flow.source().enabled()) {
                    FlowResult result = executeFlow(deployment, flow, event, depth + 1);
                    emitted.addAll(result.emitted);
                    traces.add(result.trace);
                }
            }
            deployment.state.traces.addAll(traces);
            trimTraces(deployment.state.traces);
            deployment.state.trim(properties.getMaximumRuntimeStates());
        }
        persist(deployment);
        if(publishDerived) publish(emitted);
        deployment.processedEvents.incrementAndGet();
        deployment.emittedEvents.addAndGet(emitted.size());
        deployment.updatedAt = Instant.now();
        return new EventFlowProcessResult(deployment.metamodelId, event.id(), emitted, traces);
    }

    /** Descripción: Procesa la operación «executeFlow» coordinando las responsabilidades de FlowResult. Entradas: deployment: dato de entrada necesario para la operación; flow: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private FlowResult executeFlow(EventFlowRuntimeDeployment deployment, ExecutableFlowDefinition flow, CanonicalEvent source, int depth) {
        Map<String, Object> variables = new LinkedHashMap<>();
        flow.source().variables().forEach(variable -> variables.put(variable.name(), variable.initialValue()));
        ArrayDeque<Token> queue = new ArrayDeque<>();
        queue.add(new Token(flow.startNodeId(), source, variables, 0, 0));
        return executeQueue(deployment, flow, source, depth, queue, new ArrayDeque<>(), Instant.now(), new ArrayList<>());
    }

    /** Descripción: Procesa la operación «executeQueue» coordinando las responsabilidades de FlowResult. Entradas: deployment: dato de entrada necesario para la operación; flow: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación; queue: dato de entrada necesario para la operación; compensableNodes: dato de entrada necesario para la operación; started: dato de entrada necesario para la operación; executions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private FlowResult executeQueue(EventFlowRuntimeDeployment deployment, ExecutableFlowDefinition flow, CanonicalEvent source, int depth,
            ArrayDeque<Token> queue, Deque<String> compensableNodes, Instant started, List<FlowNodeExecution> executions) {
        List<CanonicalEvent> emitted = new ArrayList<>();
        FlowExecutionStatus finalStatus = FlowExecutionStatus.COMPLETED;
        String finalMessage = "";
        int expanded = 0;
        while(!queue.isEmpty()) {
            Token token = queue.removeFirst();
            if(token.hops > properties.getMaximumExecutionDepth() || expanded++ > properties.getMaximumExecutionDepth() * Math.max(1, flow.nodes().size())) {
                finalStatus = FlowExecutionStatus.DEPTH_LIMIT;
                finalMessage = "Flow graph exceeded the execution depth limit";
                break;
            }
            ExecutableFlowNode executable = flow.nodes().get(token.nodeId);
            if(executable == null) {
                finalStatus = FlowExecutionStatus.FAILED;
                finalMessage = "Compiled node was not found: " + token.nodeId;
                break;
            }
            if(!conditionMatches(deployment, executable.node().conditionExpressionId(), token.event)) {
                executions.add(new FlowNodeExecution(executable.node().id(), executable.node().kind(), FlowExecutionStatus.FILTERED,
                        "Node condition evaluated to false", Instant.now()));
                finalStatus = FlowExecutionStatus.FILTERED;
                continue;
            }
            EventFlowRuntimeNodeExecutor.NodeOutcome outcome = executeNodeAttempt(deployment, flow.source(), executable.node(), token.event,
                    token.variables, depth, compensableNodes);
            emitted.addAll(outcome.emitted());
            if(!outcome.success() && !outcome.waiting() && token.retryAttempt < executable.node().retryPolicy().maxAttempts()) {
                int nextAttempt = token.retryAttempt + 1;
                long delay = retryDelay(executable.node(), nextAttempt);
                executions.add(new FlowNodeExecution(executable.node().id(), executable.node().kind(),
                        FlowExecutionStatus.FAILED, outcome.message(), Instant.now()));
                if(delay <= 0) {
                    queue.addFirst(new Token(token.nodeId, token.event, new LinkedHashMap<>(token.variables),
                            token.hops, nextAttempt));
                } else {
                    Instant dueAt = Instant.now().plusMillis(delay);
                    FlowRetryContinuation retry = new FlowRetryContinuation("retry:" + UUID.randomUUID(),
                            flow.source().id(), executable.node().id(), nextAttempt,
                            executable.node().retryPolicy().maxAttempts(), dueAt, started, source, token.event,
                            token.variables, List.copyOf(compensableNodes), List.copyOf(executions));
                    deployment.state.retryContinuations.put(retry.id(), retry);
                    executions.add(new FlowNodeExecution(executable.node().id(), executable.node().kind(),
                            FlowExecutionStatus.WAITING, "Retry " + nextAttempt + " of "
                                    + executable.node().retryPolicy().maxAttempts() + " scheduled for " + dueAt,
                            Instant.now()));
                    finalStatus = FlowExecutionStatus.WAITING;
                }
                continue;
            }
            FlowExecutionStatus nodeStatus = outcome.waiting() ? FlowExecutionStatus.WAITING
                    : outcome.success() ? FlowExecutionStatus.COMPLETED : FlowExecutionStatus.FAILED;
            executions.add(new FlowNodeExecution(executable.node().id(), executable.node().kind(), nodeStatus,
                    outcome.message(), Instant.now()));
            if(outcome.success() && executable.node().compensable()) compensableNodes.addLast(executable.node().id());
            if(outcome.waiting()) { finalStatus = FlowExecutionStatus.WAITING; continue; }
            List<FlowTransition> transitions = selectTransitions(executable.node(), executable.outgoing(), outcome.success(),
                    outcome.timedOut(), outcome.event()).stream()
                    .filter(transition -> conditionMatches(deployment, transition.conditionExpressionId(), outcome.event())).toList();
            if(!outcome.success() && transitions.isEmpty()) {
                boolean compensated = compensate(deployment, flow, source, depth, compensableNodes, executions, emitted);
                finalStatus = compensated ? FlowExecutionStatus.COMPENSATED : FlowExecutionStatus.FAILED;
                finalMessage = outcome.message();
                continue;
            }
            if(outcome.success() && outcome.filtered()) { finalStatus = FlowExecutionStatus.FILTERED; continue; }
            for(FlowTransition transition : transitions) queue.addLast(new Token(transition.targetNodeId(), outcome.event(),
                    new LinkedHashMap<>(token.variables), token.hops + 1, 0));
        }
        FlowExecutionTrace trace = new FlowExecutionTrace("trace:" + UUID.randomUUID(), flow.source().id(), source.id(), started,
                Instant.now(), finalStatus, executions, emitted.stream().map(CanonicalEvent::id).toList(), finalMessage);
        return new FlowResult(emitted, trace);
    }

    /** Descripción: Procesa la operación «executeNodeAttempt» coordinando las responsabilidades de FlowResult. Entradas: deployment: dato de entrada necesario para la operación; flow: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación; variables: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación; compensableNodes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EventFlowRuntimeNodeExecutor.NodeOutcome executeNodeAttempt(EventFlowRuntimeDeployment deployment, FlowDefinition flow, FlowNode node,
            CanonicalEvent event, Map<String,Object> variables, int depth, Deque<String> compensableNodes) {
        long started = System.nanoTime();
        EventFlowRuntimeNodeExecutor.NodeOutcome outcome;
        try { outcome = nodeExecutor.execute(deployment, flow, node, event, variables, depth, compensableNodes); }
        catch(RuntimeException exception) { outcome = EventFlowRuntimeNodeExecutor.NodeOutcome.failure(event, message(exception)); }
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        if(node.timeoutPolicy().timeoutMillis() > 0 && elapsedMillis > node.timeoutPolicy().timeoutMillis())
            return EventFlowRuntimeNodeExecutor.NodeOutcome.timeout(event, "Timeout after " + elapsedMillis + " ms");
        return outcome == null ? EventFlowRuntimeNodeExecutor.NodeOutcome.failure(event, "Node produced no result") : outcome;
    }

    /** Descripción: Ejecuta la operación «retryDelay» correspondiente a la responsabilidad de FlowResult. Entradas: node: dato de entrada necesario para la operación; retryAttempt: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long retryDelay(FlowNode node, int retryAttempt) {
        if(node.retryPolicy().delayMillis() <= 0) return 0L;
        double multiplier = Math.pow(node.retryPolicy().backoffMultiplier(), Math.max(0, retryAttempt - 1));
        double calculated = node.retryPolicy().delayMillis() * multiplier;
        return calculated >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, Math.round(calculated));
    }

    /** Descripción: Ejecuta la operación «compensate» correspondiente a la responsabilidad de FlowResult. Entradas: deployment: dato de entrada necesario para la operación; flow: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación; completed: dato de entrada necesario para la operación; executions: dato de entrada necesario para la operación; emitted: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean compensate(EventFlowRuntimeDeployment deployment, ExecutableFlowDefinition flow, CanonicalEvent source, int depth,
            Deque<String> completed, List<FlowNodeExecution> executions, List<CanonicalEvent> emitted) {
        boolean executed = false;
        while(!completed.isEmpty()) {
            String sourceNodeId = completed.removeLast();
            var link = flow.source().compensations().stream().filter(item -> item.sourceNodeId().equals(sourceNodeId)).findFirst().orElse(null);
            if(link == null) continue;
            ExecutableFlowNode compensation = flow.nodes().get(link.compensationNodeId());
            if(compensation == null) continue;
            EventFlowRuntimeNodeExecutor.NodeOutcome outcome = executeNodeAttempt(deployment, flow.source(), compensation.node(), source,
                    new LinkedHashMap<>(), depth, new ArrayDeque<>());
            emitted.addAll(outcome.emitted());
            executions.add(new FlowNodeExecution(compensation.node().id(), compensation.node().kind(),
                    outcome.success() ? FlowExecutionStatus.COMPENSATED : FlowExecutionStatus.FAILED,
                    outcome.message().isBlank() ? "Compensation executed for " + sourceNodeId : outcome.message(), Instant.now()));
            executed = true;
        }
        return executed;
    }

    /** Descripción: Evalúa la condición «cancelAbsenceWatches» sin modificar el estado externo. Entradas: state: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void cancelAbsenceWatches(EventFlowRuntimeState state, CanonicalEvent event) {
        state.absenceWatches.entrySet().removeIf(entry -> {
            FlowAbsenceContinuation watch = entry.getValue();
            if(!watch.watchEventTypeId().equals(event.eventTypeId())) return false;
            Object rawCurrent = EventValueResolver.read(event, watch.groupByPath());
            String current = rawCurrent == null || String.valueOf(rawCurrent).isBlank()
                    ? (event.correlationId().isBlank() ? event.id() : event.correlationId()) : String.valueOf(rawCurrent);
            return watch.key().equals(current);
        });
    }

    /** Descripción: Ejecuta la operación «resumeWaiting» correspondiente a la responsabilidad de FlowResult. Entradas: deployment: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<FlowResult> resumeWaiting(EventFlowRuntimeDeployment deployment, CanonicalEvent event, int depth) {
        List<FlowResult> results = new ArrayList<>();
        List<FlowWaitingContinuation> matches = deployment.state.waitingContinuations.values().stream()
                .filter(wait -> wait.awaitedEventTypeId().equals(event.eventTypeId())).toList();
        for(FlowWaitingContinuation wait : matches) {
            ExecutableFlowDefinition flow = deployment.plan.flows().stream()
                    .filter(item -> item.source().id().equals(wait.flowId())).findFirst().orElse(null);
            if(flow == null) { deployment.state.waitingContinuations.remove(wait.id()); continue; }
            ExecutableFlowNode waitNode = flow.nodes().get(wait.waitNodeId());
            if(waitNode == null || !wait.groupKey().equals(groupingKey(waitNode.node(), event))) continue;
            deployment.state.waitingContinuations.remove(wait.id());
            ArrayDeque<Token> queue = new ArrayDeque<>();
            for(FlowTransition transition : selectTransitions(waitNode.node(), waitNode.outgoing(), true, false, event).stream()
                    .filter(item -> conditionMatches(deployment, item.conditionExpressionId(), event)).toList())
                queue.addLast(new Token(transition.targetNodeId(), event, new LinkedHashMap<>(wait.variables()), 0, 0));
            Deque<String> compensable = new ArrayDeque<>(wait.compensableNodeIds());
            List<FlowNodeExecution> executions = new ArrayList<>();
            executions.add(new FlowNodeExecution(wait.waitNodeId(), FlowNodeKind.WAIT_FOR_EVENT, FlowExecutionStatus.COMPLETED,
                    "Awaited event received", Instant.now()));
            results.add(executeQueue(deployment, flow, event, depth, queue, compensable, wait.createdAt(), executions));
        }
        return results;
    }

    /** Descripción: Ejecuta la operación «retryDue» correspondiente a la responsabilidad de FlowResult. Entradas: deployment: dato de entrada necesario para la operación; now: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<FlowResult> retryDue(EventFlowRuntimeDeployment deployment, Instant now) {
        List<FlowResult> results = new ArrayList<>();
        List<FlowRetryContinuation> due = deployment.state.retryContinuations.values().stream()
                .filter(retry -> !retry.dueAt().isAfter(now)).sorted(Comparator.comparing(FlowRetryContinuation::dueAt)).toList();
        for(FlowRetryContinuation retry : due) {
            deployment.state.retryContinuations.remove(retry.id());
            ExecutableFlowDefinition flow = deployment.plan.flows().stream()
                    .filter(item -> item.source().id().equals(retry.flowId())).findFirst().orElse(null);
            if(flow == null || !flow.nodes().containsKey(retry.nodeId())) continue;
            ArrayDeque<Token> queue = new ArrayDeque<>();
            queue.add(new Token(retry.nodeId(), retry.currentEvent(), new LinkedHashMap<>(retry.variables()), 0,
                    retry.attempt()));
            results.add(executeQueue(deployment, flow, retry.sourceEvent(), depth(retry.sourceEvent()) + 1, queue,
                    new ArrayDeque<>(retry.compensableNodeIds()), retry.startedAt(), new ArrayList<>(retry.executions())));
        }
        return results;
    }

    /** Descripción: Ejecuta la operación «timeoutWaiting» correspondiente a la responsabilidad de FlowResult. Entradas: deployment: dato de entrada necesario para la operación; now: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<FlowResult> timeoutWaiting(EventFlowRuntimeDeployment deployment, Instant now) {
        List<FlowResult> results = new ArrayList<>();
        List<FlowWaitingContinuation> due = deployment.state.waitingContinuations.values().stream()
                .filter(wait -> wait.deadline() != null && !wait.deadline().isAfter(now)).toList();
        for(FlowWaitingContinuation wait : due) {
            deployment.state.waitingContinuations.remove(wait.id());
            ExecutableFlowDefinition flow = deployment.plan.flows().stream()
                    .filter(item -> item.source().id().equals(wait.flowId())).findFirst().orElse(null);
            if(flow == null) continue;
            ExecutableFlowNode node = flow.nodes().get(wait.waitNodeId());
            if(node == null) continue;
            var timeout = node.outgoing().stream().filter(item -> item.kind() == FlowTransitionKind.TIMEOUT).findFirst();
            if(timeout.isEmpty()) {
                results.add(new FlowResult(List.of(), new FlowExecutionTrace("trace:" + UUID.randomUUID(), wait.flowId(),
                        wait.sourceEvent().id(), wait.createdAt(), now, FlowExecutionStatus.FAILED,
                        List.of(new FlowNodeExecution(wait.waitNodeId(), FlowNodeKind.WAIT_FOR_EVENT, FlowExecutionStatus.FAILED,
                                "Wait timeout elapsed", now)), List.of(), "Wait timeout has no TIMEOUT transition")));
                continue;
            }
            ArrayDeque<Token> queue = new ArrayDeque<>();
            queue.add(new Token(timeout.get().targetNodeId(), wait.sourceEvent(), new LinkedHashMap<>(wait.variables()), 0, 0));
            List<FlowNodeExecution> executions = new ArrayList<>();
            executions.add(new FlowNodeExecution(wait.waitNodeId(), FlowNodeKind.WAIT_FOR_EVENT, FlowExecutionStatus.FAILED,
                    "Wait timeout elapsed", now));
            results.add(executeQueue(deployment, flow, wait.sourceEvent(), depth(wait.sourceEvent()) + 1, queue,
                    new ArrayDeque<>(wait.compensableNodeIds()), wait.createdAt(), executions));
        }
        return results;
    }

    /** Evalúa una condición neutral. Los runtimes embebidos sin evaluator conservan el comportamiento previo. */
    /** Descripción: Evalúa un guard EventFlow durante la ejecución. Entradas: deployment, expressionId y event. Salida: devuelve true cuando la transición o nodo puede continuar. */ private boolean conditionMatches(EventFlowRuntimeDeployment deployment, String expressionId, CanonicalEvent event) {
        if(expressionId == null || expressionId.isBlank()) return true;
        if(conditionEvaluator == null) return true;
        return conditionEvaluator.matches(deployment.metamodelId, deployment.expressionModelId, expressionId, event);
    }

    /** Descripción: Ejecuta la operación «deployment» correspondiente a la responsabilidad de FlowResult. Entradas: metamodelId: identificador que delimita el recurso objetivo; plan: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EventFlowRuntimeDeployment deployment(String metamodelId, ExecutableEventFlowPlan plan) {
        String expressionModelId = flowService.find(metamodelId).eventFlowModel().expressionModelId();
        EventFlowRuntimeDeployment result = new EventFlowRuntimeDeployment(metamodelId, plan, expressionModelId);
        if(journal != null) {
            EventFlowRuntimeSnapshot snapshot = journal.load(metamodelId);
            snapshot.waitingContinuations().forEach(wait -> result.state.waitingContinuations.put(wait.id(), wait));
            snapshot.retryContinuations().forEach(retry -> result.state.retryContinuations.put(retry.id(), retry));
            result.state.traces.addAll(snapshot.traces());
            result.state.restore(snapshot.runtimeState());
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «persist» correspondiente a la responsabilidad de FlowResult. Entradas: deployment: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void persist(EventFlowRuntimeDeployment deployment) {
        if(journal == null) return;
        try { journal.save(deployment.metamodelId, List.copyOf(deployment.state.waitingContinuations.values()),
                List.copyOf(deployment.state.retryContinuations.values()), deployment.state.traces, deployment.state.snapshot()); }
        catch(RuntimeException exception) { LOGGER.warn("Event-flow runtime journal could not be persisted for {}", deployment.metamodelId, exception); }
    }

    /** Descripción: Ejecuta la operación «trimTraces» correspondiente a la responsabilidad de FlowResult. Entradas: traces: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void trimTraces(List<FlowExecutionTrace> traces) {
        while(traces.size() > properties.getMaximumExecutionTraces()) traces.remove(0);
    }

    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de FlowResult. Entradas: events: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void publish(Collection<CanonicalEvent> events) {
        for(CanonicalEvent event : events) {
            try { eventBus.publish(event); }
            catch(RuntimeException exception) { LOGGER.warn("Derived event {} could not be delivered", event.id(), exception); }
        }
    }

    /** Descripción: Ejecuta la operación «definitions» correspondiente a la responsabilidad de FlowResult. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private EventDefinitionModel definitions(String metamodelId) {
        try { return eventModelService.find(metamodelId).definitions(); }
        catch(EventModelApplicationException exception) {
            if(exception.getReason() == EventModelApplicationException.Reason.NOT_FOUND) return null;
            throw exception;
        }
    }

    /** Despacha perezosamente el primer evento de cada ámbito, incluidos grammar.<id>. */
    /** Descripción: Ejecuta la operación «onEvent» correspondiente a la responsabilidad de FlowResult. Entradas: event: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void onEvent(CanonicalEvent event) {
        if(event == null || "false".equalsIgnoreCase(event.metadata().getOrDefault("hydra.eventFlowsEnabled", "true"))) return;
        String semanticScopeId = event.context().metamodelId();
        if(semanticScopeId == null || semanticScopeId.isBlank() || explicitlyStopped.contains(semanticScopeId)) return;
        try {
            EventFlowRuntimeDeployment deployment = deployments.compute(semanticScopeId, id -> {
                EventFlowRuntimeDeployment created = deployment(id, compile(id));
                created.active = true;
                return created;
            });
            if(!deployment.active) deployment.active = true;
            processWithDeployment(deployment, event, true);
        } catch(EventFlowApplicationException exception) {
            if(exception.getReason() != EventFlowApplicationException.Reason.NOT_FOUND) {
                LOGGER.warn("Event-flow auto deployment failed for {}", semanticScopeId, exception);
            }
        } catch(RuntimeException exception) {
            EventFlowRuntimeDeployment deployment = deployments.get(semanticScopeId);
            if(deployment != null) {
                deployment.lastFailure = message(exception);
                deployment.updatedAt = Instant.now();
            }
            LOGGER.warn("Event-flow listener failed for {}", semanticScopeId, exception);
        }
    }

    /** Descripción: Ejecuta la operación «status» correspondiente a la responsabilidad de FlowResult. Entradas: deployment: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EventFlowRuntimeStatus status(EventFlowRuntimeDeployment deployment) {
        int stateEntries;
        synchronized(deployment.state) { stateEntries = deployment.state.size(); }
        return new EventFlowRuntimeStatus(deployment.metamodelId, deployment.active, deployment.plan.flows().size(),
                deployment.processedEvents.get(), deployment.emittedEvents.get(), stateEntries, deployment.lastFailure, deployment.updatedAt);
    }
    private record Token(String nodeId, CanonicalEvent event, Map<String, Object> variables, int hops, int retryAttempt) { }
    private record FlowResult(List<CanonicalEvent> emitted, FlowExecutionTrace trace) { }
}
