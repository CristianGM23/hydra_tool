package com.example.editor.event.flow.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.EventActor;
import com.example.editor.event.EventActorKind;
import com.example.editor.event.EventContext;
import com.example.editor.event.EventContextKind;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.EventModelChecker;
import com.example.editor.event.EventModelProperties;
import com.example.editor.event.EventSource;
import com.example.editor.event.bus.InMemoryCanonicalEventBus;
import com.example.editor.event.flow.EventFlowApplicationService;
import com.example.editor.event.flow.EventFlowChecker;
import com.example.editor.event.flow.EventFlowModel;
import com.example.editor.event.flow.EventFlowProperties;
import com.example.editor.event.flow.FlowDefinition;
import com.example.editor.event.flow.FlowNode;
import com.example.editor.event.flow.FlowNodeKind;
import com.example.editor.event.flow.FlowTransition;
import com.example.editor.event.flow.FlowTransitionKind;
import com.example.editor.event.flow.persistence.JsonEventFlowModelRepository;
import com.example.editor.event.flow.runtime.persistence.JsonEventFlowRuntimeSnapshotRepository;
import com.example.editor.event.persistence.JsonEventBindingModelRepository;
import com.example.editor.event.persistence.JsonEventDefinitionModelRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real, sin mocks, del motor de flujos de eventos con diario en disco y bus real:
 * events_execute_flow.a7 (elegir la salida segun resultado y tipo de paso, y acabar la rama sin salida),
 * a8 (guardar traza y estado del motor y recuperarlos en un motor nuevo), a9 (los eventos emitidos vuelven al
 * bus y disparan otro flujo) y events_author_run.a5 (detener impide la reactivacion automatica y borrar el
 * estado elimina el diario). El oraculo es el JSON del diario leido del disco y listas literales de nodos.
 */
class EventFlowRuntimeBranchingJournalAndBusIntegrationTests {

    private static final String M = ExpressionTestFixtures.METAMODEL_ID;
    private static final String SAVED = "core.model.saved";

    private record Stack(JsonMapper mapper, EventFlowApplicationService flowService, EventModelApplicationService eventService,
            EventFlowProperties properties, EventFlowChecker checker, Path flowDirectory) { }

    private static Stack stack(Path temporary) {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(ExpressionTestFixtures.metamodel());
        EventModelProperties eventProperties = new EventModelProperties();
        eventProperties.setDirectory(temporary.resolve("events"));
        EventModelApplicationService eventService = new EventModelApplicationService(
                new JsonEventDefinitionModelRepository(eventProperties, mapper),
                new JsonEventBindingModelRepository(eventProperties, mapper),
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper),
                metamodelService, new EventModelChecker());
        EventFlowProperties flowProperties = new EventFlowProperties();
        flowProperties.setDirectory(temporary.resolve("flows"));
        EventFlowChecker checker = new EventFlowChecker();
        EventFlowApplicationService flowService = new EventFlowApplicationService(
                new JsonEventFlowModelRepository(flowProperties, mapper), metamodelService, eventService, checker,
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper));
        return new Stack(mapper, flowService, eventService, flowProperties, checker, temporary.resolve("flows"));
    }

    private static EventFlowRuntimeService runtime(Stack s, InMemoryCanonicalEventBus bus) {
        EventFlowRuntimeJournalService journal = new EventFlowRuntimeJournalService(
                new JsonEventFlowRuntimeSnapshotRepository(s.properties(), s.mapper()), s.properties());
        return new EventFlowRuntimeService(s.flowService(), s.eventService(), new EventFlowCompiler(s.checker()), bus, List.of(),
                s.properties(), journal, null);
    }

    private static FlowNode node(String id, FlowNodeKind kind, String eventTypeId, Map<String, Object> configuration) {
        return new FlowNode(id, id, kind, "", eventTypeId, "", configuration, null, null, false, true);
    }

    private static FlowTransition transition(String id, String from, String to, FlowTransitionKind kind) {
        return new FlowTransition(id, from, to, kind, "", 0, "");
    }

    private static FlowDefinition flow(String id, String trigger, List<FlowNode> nodes, List<FlowTransition> transitions) {
        return new FlowDefinition(id, id, "", true, List.of(trigger), List.of(), nodes, transitions, List.of(), Map.of());
    }

    private static void save(Stack s, FlowDefinition... flows) {
        s.flowService().create(M, new EventFlowModel(null, "flows-library", M, "", "", List.of(flows), Map.of()));
    }

    private static CanonicalEvent event(String id, String type, Map<String, Object> payload, Map<String, String> metadata) {
        return new CanonicalEvent("1.0", id, type, "1.0", Instant.now(), new EventSource("test", "events", "", "1.0"),
                new EventContext(EventContextKind.EVENT_FLOW, "", "", M, "model", "", "session:one"),
                new EventActor("actor:one", EventActorKind.USER, "Actor"), null, "transaction:one", "correlation:one", "",
                payload, metadata);
    }

    private static FlowDefinition dedupeFlow() {
        return flow("flow:dedupe", SAVED,
                List.of(node("node:start", FlowNodeKind.START, "", Map.of()), node("node:dedupe", FlowNodeKind.DEDUPLICATE, "", Map.of()),
                        node("node:end", FlowNodeKind.SUCCESS_END, "", Map.of())),
                List.of(transition("transition:start-dedupe", "node:start", "node:dedupe", FlowTransitionKind.NEXT),
                        transition("transition:dedupe-end", "node:dedupe", "node:end", FlowTransitionKind.NEXT)));
    }

    private static List<String> nodeIds(FlowExecutionTrace trace) {
        return trace.nodes().stream().map(FlowNodeExecution::nodeId).toList();
    }

    @Test
    @DisplayName("events_execute_flow.a7: la salida se elige por el resultado de la decision, por exito o error del paso y la rama acaba sin salida")
    void theExitIsChosenByTheDecisionResultTheStepOutcomeAndABranchWithoutExitEnds(@TempDir Path temporary) {
        Stack s = stack(temporary);
        FlowDefinition branch = flow("flow:branch", SAVED,
                List.of(node("node:start", FlowNodeKind.START, "", Map.of()),
                        node("node:decide", FlowNodeKind.DECISION, "", Map.of("conditionPath", "payload.kind", "expectedValue", "urgent")),
                        node("node:emit-urgent", FlowNodeKind.EMIT, "core.model.exported", Map.of()),
                        node("node:emit-routine", FlowNodeKind.EMIT, "core.import.completed", Map.of()),
                        node("node:end-a", FlowNodeKind.SUCCESS_END, "", Map.of()), node("node:end-b", FlowNodeKind.SUCCESS_END, "", Map.of())),
                List.of(transition("transition:start-decide", "node:start", "node:decide", FlowTransitionKind.NEXT),
                        transition("transition:decide-urgent", "node:decide", "node:emit-urgent", FlowTransitionKind.TRUE),
                        transition("transition:decide-routine", "node:decide", "node:emit-routine", FlowTransitionKind.FALSE),
                        transition("transition:urgent-end", "node:emit-urgent", "node:end-a", FlowTransitionKind.NEXT),
                        transition("transition:routine-end", "node:emit-routine", "node:end-b", FlowTransitionKind.NEXT)));
        FlowDefinition onError = actionFlow("flow:on-error", "core.model.element-updated", Map.of(), true);
        FlowDefinition onSuccess = actionFlow("flow:on-success", "core.model.element-deleted", Map.of("allowNoop", "true"), true);
        FlowDefinition noExit = actionFlow("flow:no-exit", "core.operation.invoked", Map.of(), false);
        save(s, branch, onError, onSuccess, noExit);
        EventFlowRuntimeService runtime = runtime(s, new InMemoryCanonicalEventBus());
        runtime.start(M);

        // Decision: la condicion cierta elige la salida TRUE, la falsa la salida FALSE.
        EventFlowProcessResult urgent = runtime.process(M, event("event:urgent", SAVED, Map.of("kind", "urgent"), Map.of()));
        assertThat(urgent.traces()).singleElement().satisfies(trace -> {
            assertThat(nodeIds(trace)).containsExactly("node:start", "node:decide", "node:emit-urgent", "node:end-a");
            assertThat(trace.status()).isEqualTo(FlowExecutionStatus.COMPLETED);
        });
        assertThat(urgent.emittedEvents()).extracting(CanonicalEvent::eventTypeId).containsExactly("core.model.exported");
        assertThat(urgent.emittedEvents().get(0).metadata()).containsEntry("hydra.flow.id", "flow:branch")
                .containsEntry("hydra.flow.nodeId", "node:emit-urgent");
        assertThat(urgent.emittedEvents().get(0).causationId()).isEqualTo("event:urgent");

        EventFlowProcessResult routine = runtime.process(M, event("event:routine", SAVED, Map.of("kind", "routine"), Map.of()));
        assertThat(routine.traces()).singleElement().satisfies(trace ->
                assertThat(nodeIds(trace)).containsExactly("node:start", "node:decide", "node:emit-routine", "node:end-b"));
        assertThat(routine.emittedEvents()).extracting(CanonicalEvent::eventTypeId).containsExactly("core.import.completed");

        // Paso fallido con salida de error: se sigue la salida de error y no la normal.
        EventFlowProcessResult failed = runtime.process(M, event("event:failing", "core.model.element-updated", Map.of(), Map.of()));
        assertThat(failed.traces()).singleElement().satisfies(trace -> {
            assertThat(nodeIds(trace)).containsExactly("node:start", "node:act", "node:end-error");
            assertThat(trace.nodes()).extracting(FlowNodeExecution::status)
                    .containsExactly(FlowExecutionStatus.COMPLETED, FlowExecutionStatus.FAILED, FlowExecutionStatus.COMPLETED);
        });

        // Paso correcto con las mismas dos salidas: se sigue la normal.
        EventFlowProcessResult succeeded = runtime.process(M, event("event:succeeding", "core.model.element-deleted", Map.of(), Map.of()));
        assertThat(succeeded.traces()).singleElement().satisfies(trace -> {
            assertThat(nodeIds(trace)).containsExactly("node:start", "node:act", "node:end-ok");
            assertThat(trace.nodes()).extracting(FlowNodeExecution::status).containsOnly(FlowExecutionStatus.COMPLETED);
        });

        // Paso fallido sin salida de error ni compensacion: la rama acaba ahi y la traza queda fallida.
        EventFlowProcessResult dead = runtime.process(M, event("event:dead-end", "core.operation.invoked", Map.of(), Map.of()));
        assertThat(dead.traces()).singleElement().satisfies(trace -> {
            assertThat(trace.status()).isEqualTo(FlowExecutionStatus.FAILED);
            assertThat(nodeIds(trace)).containsExactly("node:start", "node:act");
            assertThat(trace.message()).startsWith("No FlowExternalStepExecutor supports");
        });
    }

    private static FlowDefinition actionFlow(String id, String trigger, Map<String, Object> configuration, boolean withErrorExit) {
        List<FlowNode> nodes = new ArrayList<>(List.of(node("node:start", FlowNodeKind.START, "", Map.of()),
                node("node:act", FlowNodeKind.ACTION_STEP, "", configuration), node("node:end-ok", FlowNodeKind.SUCCESS_END, "", Map.of())));
        List<FlowTransition> transitions = new ArrayList<>(List.of(transition("transition:start-act", "node:start", "node:act", FlowTransitionKind.NEXT),
                transition("transition:act-ok", "node:act", "node:end-ok", FlowTransitionKind.NEXT)));
        if(withErrorExit) {
            nodes.add(node("node:end-error", FlowNodeKind.ERROR_END, "", Map.of()));
            transitions.add(transition("transition:act-error", "node:act", "node:end-error", FlowTransitionKind.ON_ERROR));
        }
        return flow(id, trigger, nodes, transitions);
    }

    @Test
    @DisplayName("events_execute_flow.a9: el evento emitido vuelve al bus real y dispara otro flujo; con los flujos apagados no hay traza")
    void anEmittedEventReturnsToTheBusAndTriggersAnotherFlow(@TempDir Path temporary) {
        Stack s = stack(temporary);
        FlowDefinition origin = flow("flow:origin", SAVED,
                List.of(node("node:start", FlowNodeKind.START, "", Map.of()), node("node:emit", FlowNodeKind.EMIT, "core.model.exported", Map.of()),
                        node("node:end", FlowNodeKind.SUCCESS_END, "", Map.of())),
                List.of(transition("transition:start-emit", "node:start", "node:emit", FlowTransitionKind.NEXT),
                        transition("transition:emit-end", "node:emit", "node:end", FlowTransitionKind.NEXT)));
        FlowDefinition reaction = flow("flow:reaction", "core.model.exported",
                List.of(node("node:start", FlowNodeKind.START, "", Map.of()), node("node:end", FlowNodeKind.SUCCESS_END, "", Map.of())),
                List.of(transition("transition:start-end", "node:start", "node:end", FlowTransitionKind.NEXT)));
        save(s, origin, reaction);
        InMemoryCanonicalEventBus bus = new InMemoryCanonicalEventBus();
        EventFlowRuntimeService runtime = runtime(s, bus);
        List<CanonicalEvent> seen = new ArrayList<>();
        bus.subscribe(event -> true, seen::add);

        bus.publish(event("event:origin", SAVED, Map.of(), Map.of()));

        assertThat(seen).extracting(CanonicalEvent::eventTypeId).containsExactly(SAVED, "core.model.exported");
        CanonicalEvent derived = seen.get(1);
        assertThat(derived.causationId()).isEqualTo("event:origin");
        assertThat(derived.context().kind()).isEqualTo(EventContextKind.EVENT_FLOW);
        assertThat(derived.metadata()).containsEntry("hydra.flow.depth", "1").containsEntry("hydra.flow.id", "flow:origin");
        List<FlowExecutionTrace> traces = runtime.traces(M);
        assertThat(traces).extracting(FlowExecutionTrace::flowId).containsExactly("flow:origin", "flow:reaction");
        assertThat(traces.get(1).sourceEventId()).isEqualTo(derived.id());
        assertThat(traces).extracting(FlowExecutionTrace::status).containsOnly(FlowExecutionStatus.COMPLETED);

        // Negativo: con los flujos apagados para el evento no se emite nada derivado ni se anota traza.
        bus.publish(event("event:switched-off", SAVED, Map.of(), Map.of("hydra.eventFlowsEnabled", "false")));
        assertThat(seen).extracting(CanonicalEvent::eventTypeId).containsExactly(SAVED, "core.model.exported", SAVED);
        assertThat(runtime.traces(M)).hasSize(2);
    }

    @Test
    @DisplayName("events_execute_flow.a8: la traza y el estado del motor quedan en el diario en disco y un motor nuevo los recupera")
    void theTraceAndTheEngineStateAreSavedToTheJournalAndRestoredByANewEngine(@TempDir Path temporary) throws Exception {
        Stack s = stack(temporary);
        save(s, dedupeFlow());
        EventFlowRuntimeService first = runtime(s, new InMemoryCanonicalEventBus());
        first.start(M);

        EventFlowProcessResult processed = first.process(M, event("event:persist-1", SAVED, Map.of(), Map.of()));
        assertThat(nodeIds(processed.traces().get(0))).containsExactly("node:start", "node:dedupe", "node:end");

        Path journalFile = s.flowDirectory().resolve("library.event-flow-runtime.json");
        assertThat(journalFile).exists();
        JsonNode journal = s.mapper().readTree(Files.readString(journalFile));
        assertThat(journal.get("traces").size()).isEqualTo(1);
        assertThat(journal.get("traces").get(0).get("flowId").asString()).isEqualTo("flow:dedupe");
        assertThat(journal.get("traces").get(0).get("sourceEventId").asString()).isEqualTo("event:persist-1");
        assertThat(journal.get("traces").get(0).get("status").asString()).isEqualTo("COMPLETED");
        JsonNode seen = journal.get("runtimeState").get("deduplication").get("flow:dedupe:node:dedupe");
        assertThat(seen.size()).isEqualTo(1);
        assertThat(seen.has("event:persist-1")).isTrue();

        // Un motor nuevo sobre el mismo disco recupera el estado: el evento ya visto se descarta y uno nuevo pasa.
        EventFlowRuntimeService second = runtime(s, new InMemoryCanonicalEventBus());
        second.start(M);
        assertThat(second.traces(M)).hasSize(1);
        EventFlowProcessResult repeated = second.process(M, event("event:persist-1", SAVED, Map.of(), Map.of()));
        assertThat(repeated.traces()).singleElement().satisfies(trace -> {
            assertThat(trace.status()).isEqualTo(FlowExecutionStatus.FILTERED);
            assertThat(nodeIds(trace)).containsExactly("node:start", "node:dedupe");
        });
        EventFlowProcessResult fresh = second.process(M, event("event:persist-2", SAVED, Map.of(), Map.of()));
        assertThat(fresh.traces()).singleElement().satisfies(trace -> assertThat(trace.status()).isEqualTo(FlowExecutionStatus.COMPLETED));
        JsonNode after = s.mapper().readTree(Files.readString(journalFile));
        assertThat(after.get("traces").size()).isEqualTo(3);
        assertThat(after.get("runtimeState").get("deduplication").get("flow:dedupe:node:dedupe").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("events_author_run.a5: detener impide la reactivacion automatica hasta un arranque explicito y borrar el estado elimina el diario")
    void stoppingPreventsAutomaticReactivationAndClearingTheStateRemovesTheJournal(@TempDir Path temporary) throws Exception {
        Stack s = stack(temporary);
        save(s, dedupeFlow());
        InMemoryCanonicalEventBus bus = new InMemoryCanonicalEventBus();
        EventFlowRuntimeService runtime = runtime(s, bus);
        Path journalFile = s.flowDirectory().resolve("library.event-flow-runtime.json");
        runtime.start(M);
        bus.publish(event("event:stop-1", SAVED, Map.of(), Map.of()));
        assertThat(traceCount(s, journalFile)).isEqualTo(1);

        EventFlowRuntimeStatus stopped = runtime.stop(M);
        assertThat(stopped.active()).isFalse();
        bus.publish(event("event:stop-2", SAVED, Map.of(), Map.of()));
        assertThat(runtime.status(M).active()).isFalse();
        assertThat(runtime.status(M).processedEvents()).isZero();
        assertThat(traceCount(s, journalFile)).isEqualTo(1);

        runtime.start(M);
        bus.publish(event("event:stop-3", SAVED, Map.of(), Map.of()));
        assertThat(runtime.status(M).active()).isTrue();
        assertThat(traceCount(s, journalFile)).isEqualTo(2);

        runtime.stop(M);
        runtime.clearState(M);
        assertThat(journalFile).doesNotExist();
        assertThat(runtime.snapshot(M).traces()).isEmpty();
        bus.publish(event("event:stop-4", SAVED, Map.of(), Map.of()));
        assertThat(journalFile).doesNotExist();
        assertThat(runtime.status(M).active()).isFalse();
    }

    private static int traceCount(Stack s, Path journalFile) throws Exception {
        return s.mapper().readTree(Files.readString(journalFile)).get("traces").size();
    }
}
