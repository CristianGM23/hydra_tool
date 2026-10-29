package com.example.editor.event.flow.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.event.CanonicalEvent;
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
import com.example.editor.event.persistence.JsonEventBindingModelRepository;
import com.example.editor.event.persistence.JsonEventDefinitionModelRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.semantic.expression.BinaryExpressionNode;
import com.example.editor.semantic.expression.BinaryExpressionOperator;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.semantic.expression.ExpressionValueKind;
import com.example.editor.semantic.expression.ExpressionValueType;
import com.example.editor.semantic.expression.LiteralExpressionNode;
import com.example.editor.semantic.expression.VariableExpressionNode;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia FUERTE del despacho real de flujos de eventos: EventFlowRuntimeService sin Mockito, con
 * EventFlowConditionEvaluator real (repositorio de expresiones JSON y ExpressionEvaluator reales), bus
 * real, repositorios JSON reales sobre {@code @TempDir} y los eventos entregados por el propio bus.
 * Etapas de events_execute_flow: d0 (autodespliegue y parada manual), a2 (flujos disparados por el
 * tipo de evento y habilitados), d1 (condición del paso con FILTERED) y a1 (reanudar esperas y
 * cancelar vigilancias de ausencia).
 */
class EventFlowDispatchRealChainIntegrationTests {

    private static final String LIB = ExpressionTestFixtures.METAMODEL_ID;

    private record Stack(EventFlowApplicationService flowService, JsonExpressionModelRepository expressions,
            EventFlowRuntimeService runtime, InMemoryCanonicalEventBus bus) { }

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
        JsonExpressionModelRepository expressions = new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper);
        EventModelApplicationService eventService = new EventModelApplicationService(
                new JsonEventDefinitionModelRepository(eventProperties, mapper),
                new JsonEventBindingModelRepository(eventProperties, mapper), expressions, metamodelService, new EventModelChecker());
        EventFlowProperties flowProperties = new EventFlowProperties();
        flowProperties.setDirectory(temporary.resolve("flows"));
        EventFlowChecker flowChecker = new EventFlowChecker();
        EventFlowApplicationService flowService = new EventFlowApplicationService(
                new JsonEventFlowModelRepository(flowProperties, mapper), metamodelService, eventService, flowChecker, expressions);
        InMemoryCanonicalEventBus bus = new InMemoryCanonicalEventBus();
        EventFlowConditionEvaluator conditions = new EventFlowConditionEvaluator(expressions, metamodelService,
                new ExpressionEvaluator(new ExpressionTypeChecker(new ExpressionDependencyAnalyzer())));
        EventFlowRuntimeService runtime = new EventFlowRuntimeService(flowService, eventService, new EventFlowCompiler(flowChecker), bus,
                List.of(), flowProperties, null, conditions);
        return new Stack(flowService, expressions, runtime, bus);
    }

    private static FlowNode node(String id, FlowNodeKind kind, String eventTypeId, String condition, Map<String, Object> configuration) {
        return new FlowNode(id, id, kind, "", eventTypeId, condition, configuration, null, null, false, true);
    }

    private static FlowTransition next(String id, String from, String to) {
        return new FlowTransition(id, from, to, FlowTransitionKind.NEXT, "", 0, "");
    }

    private static FlowDefinition flow(String id, boolean enabled, String trigger, List<FlowNode> nodes, List<FlowTransition> transitions) {
        return new FlowDefinition(id, id, "", enabled, List.of(trigger), List.of(), nodes, transitions, List.of(), Map.of());
    }

    private static FlowDefinition simpleFlow(String id, boolean enabled, String trigger) {
        return flow(id, enabled, trigger, List.of(node("node:start", FlowNodeKind.START, "", "", Map.of()),
                node("node:end", FlowNodeKind.SUCCESS_END, "", "", Map.of())), List.of(next("transition:start-end", "node:start", "node:end")));
    }

    private static EventFlowModel model(String expressionModelId, FlowDefinition... flows) {
        return new EventFlowModel(null, "flows-library", LIB, "", expressionModelId, List.of(flows), Map.of());
    }

    private static CanonicalEvent event(String id, String type, String correlationId, Map<String, Object> payload) {
        return new CanonicalEvent(null, id, type, "1.0", Instant.now(), new EventSource("TEST", "", "", ""),
                new EventContext(EventContextKind.SYSTEM, "", "", LIB, "", "", ""), null, null, "", correlationId, "", payload, Map.of());
    }

    @Test
    @DisplayName("events_execute_flow.d0: el primer evento autodespliega, stop(scope) bloquea el rearranque y start(scope) lo reactiva")
    void theFirstEventAutoDeploysAnExplicitStopBlocksRestartAndStartReactivates(@TempDir Path temporary) {
        Stack s = stack(temporary);
        s.flowService().create(LIB, model("", simpleFlow("flow:smoke", true, "core.model.saved")));
        try {
            assertThat(s.runtime().status(LIB).active()).isFalse();

            s.bus().publish(event("event:one", "core.model.saved", "", Map.of()));
            EventFlowRuntimeStatus deployed = s.runtime().status(LIB);
            assertThat(deployed.active()).isTrue();
            assertThat(deployed.flowCount()).isEqualTo(1);
            assertThat(deployed.processedEvents()).isEqualTo(1);
            assertThat(s.runtime().traces(LIB)).singleElement().satisfies(trace -> {
                assertThat(trace.status()).isEqualTo(FlowExecutionStatus.COMPLETED);
                assertThat(trace.flowId()).isEqualTo("flow:smoke");
                assertThat(trace.nodes()).extracting(FlowNodeExecution::nodeId).containsExactly("node:start", "node:end");
            });

            assertThat(s.runtime().stop(LIB).active()).isFalse();
            s.bus().publish(event("event:two", "core.model.saved", "", Map.of()));
            EventFlowRuntimeStatus afterStop = s.runtime().status(LIB);
            assertThat(afterStop.active()).isFalse();
            assertThat(afterStop.processedEvents()).isZero();
            assertThat(s.runtime().traces(LIB)).isEmpty();

            EventFlowRuntimeStatus restarted = s.runtime().start(LIB);
            assertThat(restarted.active()).isTrue();
            assertThat(restarted.flowCount()).isEqualTo(1);
            s.bus().publish(event("event:three", "core.model.saved", "", Map.of()));
            assertThat(s.runtime().status(LIB).processedEvents()).isEqualTo(1);
            assertThat(s.runtime().traces(LIB)).singleElement().satisfies(trace -> assertThat(trace.sourceEventId()).isEqualTo("event:three"));
        } finally {
            s.runtime().close();
        }
    }

    @Test
    @DisplayName("events_execute_flow.a2: un evento ejecuta solo el flujo cuyo disparador coincide y que esta habilitado")
    void anEventRunsOnlyTheMatchingEnabledFlow(@TempDir Path temporary) {
        Stack s = stack(temporary);
        s.flowService().create(LIB, model("", simpleFlow("flow:on-saved", true, "core.model.saved"),
                simpleFlow("flow:on-exported", true, "core.model.exported"), simpleFlow("flow:saved-disabled", false, "core.model.saved")));
        try {
            s.bus().publish(event("event:saved", "core.model.saved", "", Map.of()));
            assertThat(s.runtime().traces(LIB)).extracting(FlowExecutionTrace::flowId).containsExactly("flow:on-saved");

            s.bus().publish(event("event:exported", "core.model.exported", "", Map.of()));
            assertThat(s.runtime().traces(LIB)).extracting(FlowExecutionTrace::flowId).containsExactly("flow:on-saved", "flow:on-exported");

            s.bus().publish(event("event:created", "core.model.element-created", "", Map.of()));
            assertThat(s.runtime().traces(LIB)).hasSize(2);
            assertThat(s.runtime().status(LIB).processedEvents()).isEqualTo(3);
            assertThat(s.runtime().traces(LIB)).extracting(FlowExecutionTrace::flowId).doesNotContain("flow:saved-disabled");
        } finally {
            s.runtime().close();
        }
    }

    @Test
    @DisplayName("events_execute_flow.d1: la condicion del paso falsa da traza FILTERED y descarta el testigo; la verdadera completa el flujo")
    void aFalseStepConditionFiltersTheTokenAndATrueOneCompletesTheFlow(@TempDir Path temporary) {
        Stack s = stack(temporary);
        s.expressions().create(ExpressionTestFixtures.expressionModel(new ExpressionDefinition("expression:approved", "Approved", "", "",
                ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN),
                new BinaryExpressionNode("node:approved", BinaryExpressionOperator.EQUALS,
                        new VariableExpressionNode("node:approved-var", "payload.approved"),
                        new LiteralExpressionNode("node:approved-literal", ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN), true)), Map.of())));
        s.flowService().create(LIB, model(LIB, flow("flow:gated", true, "core.model.saved",
                List.of(node("node:start", FlowNodeKind.START, "", "", Map.of()),
                        node("node:gate", FlowNodeKind.FILTER, "", "expression:approved", Map.of()),
                        node("node:end", FlowNodeKind.SUCCESS_END, "", "", Map.of())),
                List.of(next("transition:start-gate", "node:start", "node:gate"), next("transition:gate-end", "node:gate", "node:end")))));
        try {
            s.bus().publish(event("event:rejected", "core.model.saved", "", Map.of("approved", false)));
            assertThat(s.runtime().traces(LIB)).singleElement().satisfies(trace -> {
                assertThat(trace.status()).isEqualTo(FlowExecutionStatus.FILTERED);
                assertThat(trace.nodes()).extracting(FlowNodeExecution::nodeId).containsExactly("node:start", "node:gate");
                assertThat(trace.nodes()).extracting(FlowNodeExecution::status)
                        .containsExactly(FlowExecutionStatus.COMPLETED, FlowExecutionStatus.FILTERED);
            });

            s.bus().publish(event("event:approved", "core.model.saved", "", Map.of("approved", true)));
            assertThat(s.runtime().traces(LIB)).hasSize(2);
            assertThat(s.runtime().traces(LIB).get(1)).satisfies(trace -> {
                assertThat(trace.status()).isEqualTo(FlowExecutionStatus.COMPLETED);
                assertThat(trace.nodes()).extracting(FlowNodeExecution::nodeId).containsExactly("node:start", "node:gate", "node:end");
            });
        } finally {
            s.runtime().close();
        }
    }

    @Test
    @DisplayName("events_execute_flow.a1: WAIT_FOR_EVENT queda en espera y solo el evento esperado con la misma clave de agrupacion lo reanuda")
    void aWaitingStepIsResumedOnlyByTheAwaitedEventWithTheSameGroupingKey(@TempDir Path temporary) {
        Stack s = stack(temporary);
        s.flowService().create(LIB, model("", flow("flow:waiting", true, "core.model.saved",
                List.of(node("node:start", FlowNodeKind.START, "", "", Map.of()),
                        node("node:wait", FlowNodeKind.WAIT_FOR_EVENT, "core.model.exported", "", Map.of()),
                        node("node:end", FlowNodeKind.SUCCESS_END, "", "", Map.of())),
                List.of(next("transition:start-wait", "node:start", "node:wait"), next("transition:wait-end", "node:wait", "node:end")))));
        try {
            s.bus().publish(event("event:saved", "core.model.saved", "corr:1", Map.of()));
            assertThat(s.runtime().traces(LIB)).singleElement().satisfies(trace -> {
                assertThat(trace.status()).isEqualTo(FlowExecutionStatus.WAITING);
                assertThat(trace.nodes()).extracting(FlowNodeExecution::nodeId).containsExactly("node:start", "node:wait");
            });
            assertThat(s.runtime().snapshot(LIB).waitingContinuations()).hasSize(1);

            s.bus().publish(event("event:exported-other", "core.model.exported", "corr:2", Map.of()));
            assertThat(s.runtime().traces(LIB)).hasSize(1);
            assertThat(s.runtime().snapshot(LIB).waitingContinuations()).hasSize(1);

            s.bus().publish(event("event:exported-same", "core.model.exported", "corr:1", Map.of()));
            assertThat(s.runtime().snapshot(LIB).waitingContinuations()).isEmpty();
            assertThat(s.runtime().traces(LIB)).hasSize(2);
            assertThat(s.runtime().traces(LIB).get(1)).satisfies(trace -> {
                assertThat(trace.status()).isEqualTo(FlowExecutionStatus.COMPLETED);
                assertThat(trace.flowId()).isEqualTo("flow:waiting");
                assertThat(trace.nodes()).extracting(FlowNodeExecution::nodeId).containsExactly("node:wait", "node:end");
            });
        } finally {
            s.runtime().close();
        }
    }

    @Test
    @DisplayName("events_execute_flow.a1: la vigilancia de ausencia se cancela al llegar su evento y el tick posterior no emite nada; sin ese evento si emite")
    void anAbsenceWatchIsCancelledByItsEventSoALaterTickEmitsNothingWhileAnUncancelledOneDoes(@TempDir Path temporary) {
        Stack s = stack(temporary);
        s.flowService().create(LIB, model("", flow("flow:absence", true, "core.model.saved",
                List.of(node("node:start", FlowNodeKind.START, "", "", Map.of()),
                        node("node:absence", FlowNodeKind.ABSENCE, "core.model.element-deleted", "",
                                Map.of("watchEventTypeId", "core.model.exported", "timeoutMillis", 5L)),
                        node("node:end", FlowNodeKind.SUCCESS_END, "", "", Map.of())),
                List.of(next("transition:start-absence", "node:start", "node:absence"), next("transition:absence-end", "node:absence", "node:end")))));
        try {
            s.bus().publish(event("event:saved-one", "core.model.saved", "corr:1", Map.of()));
            assertThat(s.runtime().status(LIB).stateEntries()).isEqualTo(1);

            s.bus().publish(event("event:exported-one", "core.model.exported", "corr:1", Map.of()));
            assertThat(s.runtime().status(LIB).stateEntries()).isZero();
            EventFlowProcessResult cancelledTick = s.runtime().tick(LIB, Instant.now().plusSeconds(60));
            assertThat(cancelledTick.emittedEvents()).isEmpty();
            assertThat(cancelledTick.traces()).isEmpty();

            s.bus().publish(event("event:saved-two", "core.model.saved", "corr:2", Map.of()));
            assertThat(s.runtime().status(LIB).stateEntries()).isEqualTo(1);
            EventFlowProcessResult firedTick = s.runtime().tick(LIB, Instant.now().plusSeconds(60));
            assertThat(firedTick.emittedEvents()).singleElement().satisfies(emitted -> {
                assertThat(emitted.eventTypeId()).isEqualTo("core.model.element-deleted");
                assertThat(emitted.causationId()).isEqualTo("event:saved-two");
                assertThat(((Map<?, ?>) emitted.payload().get("_hydraFlow")).get("absenceKey")).isEqualTo("corr:2");
            });
            assertThat(s.runtime().status(LIB).stateEntries()).isZero();
        } finally {
            s.runtime().close();
        }
    }
}
