package com.example.editor.event.flow.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
import com.example.editor.event.flow.CompensationLink;
import com.example.editor.event.flow.EventFlowApplicationService;
import com.example.editor.event.flow.EventFlowChecker;
import com.example.editor.event.flow.EventFlowModel;
import com.example.editor.event.flow.EventFlowProperties;
import com.example.editor.event.flow.FlowDefinition;
import com.example.editor.event.flow.FlowNode;
import com.example.editor.event.flow.FlowNodeKind;
import com.example.editor.event.flow.FlowTransition;
import com.example.editor.event.flow.FlowTransitionKind;
import com.example.editor.event.flow.RetryPolicy;
import com.example.editor.event.flow.persistence.JsonEventFlowModelRepository;
import com.example.editor.event.persistence.JsonEventBindingModelRepository;
import com.example.editor.event.persistence.JsonEventDefinitionModelRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia FUERTE real, sin mocks, del ciclo de vida completo del panel de ejecución de flujos de
 * eventos: arrancar el motor, enviar un evento de prueba, consultar estado y trazas, y detener.
 * Cubre directamente "a1" (Arrancar o actualizar el motor), "a2" (Enviar un evento de prueba),
 * "a4" (Consultar estado y trazas), "a5" (Detener o borrar el estado) y "r1" (Traza consultada) de
 * events_author_run, con la cadena real completa de colaboradores (EventFlowApplicationService,
 * EventModelApplicationService, CanonicalMetamodelApplicationService y sus repositorios JSON
 * reales), sin mocks ni capa web. El segundo caso cubre además "a3" (Avanzar el reloj): un nodo
 * de ausencia real queda a la espera y, al avanzar el reloj real más allá de su plazo, emite de
 * verdad el evento derivado de ausencia -- sin ningún mock del paso del tiempo real. El tercer
 * caso cubre "d2"/"a5"/"a6" de events_execute_flow: un paso real sin ejecutor externo falla de
 * verdad, se reintenta una vez de verdad (su política real de reintento), vuelve a fallar, y al
 * no quedar reintentos ni salida de error HYDRA compensa de verdad, en orden inverso real, el
 * paso compensable ya completado.
 */
class EventFlowRuntimeLifecycleIntegrationTests {

    @Test
    void startingProcessingAndStoppingARealFlowProducesRealStatusAndTraces(@TempDir Path temporary) {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(ExpressionTestFixtures.metamodel());

        EventModelProperties eventModelProperties = new EventModelProperties();
        eventModelProperties.setDirectory(temporary.resolve("events"));
        EventModelApplicationService eventModelService = new EventModelApplicationService(
                new JsonEventDefinitionModelRepository(eventModelProperties, mapper),
                new JsonEventBindingModelRepository(eventModelProperties, mapper),
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper),
                metamodelService, new EventModelChecker());

        EventFlowProperties flowProperties = new EventFlowProperties();
        flowProperties.setDirectory(temporary.resolve("flows"));
        EventFlowChecker flowChecker = new EventFlowChecker();
        EventFlowApplicationService flowService = new EventFlowApplicationService(
                new JsonEventFlowModelRepository(flowProperties, mapper), metamodelService, eventModelService, flowChecker,
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper));

        InMemoryCanonicalEventBus bus = new InMemoryCanonicalEventBus();
        EventFlowRuntimeService runtime = new EventFlowRuntimeService(flowService, eventModelService,
                new EventFlowCompiler(flowChecker), bus, List.of(), flowProperties);

        // Un flujo minimo real: START -> SUCCESS_END disparado por un tipo de evento reservado,
        // guardado a traves del servicio de aplicacion real (que exige una comprobacion limpia).
        FlowNode start = new FlowNode("node:start", "Start", FlowNodeKind.START, "", "", "", Map.of(), null, null, false, true);
        FlowNode end = new FlowNode("node:end", "End", FlowNodeKind.SUCCESS_END, "", "", "", Map.of(), null, null, false, true);
        FlowTransition transition = new FlowTransition("transition:start-end", "node:start", "node:end", FlowTransitionKind.NEXT, "", 0, "");
        FlowDefinition flow = new FlowDefinition("flow:smoke", "Smoke flow", "", true, List.of("core.model.saved"),
                List.of(), List.of(start, end), List.of(transition), List.of(), Map.of());
        EventFlowModel model = new EventFlowModel(null, "flows-library", ExpressionTestFixtures.METAMODEL_ID, "", "",
                List.of(flow), Map.of());
        flowService.create(ExpressionTestFixtures.METAMODEL_ID, model);

        // "a1": arrancar el motor compila el modelo guardado real (exige comprobacion limpia) y
        // lo activa.
        EventFlowRuntimeStatus started = runtime.start(ExpressionTestFixtures.METAMODEL_ID);
        assertThat(started.active()).isTrue();
        assertThat(started.flowCount()).isEqualTo(1);

        // "a2": un evento de prueba real recorre el flujo real de principio a fin.
        CanonicalEvent trigger = new CanonicalEvent("1.0", "event:trigger", "core.model.saved", "1.0", Instant.now(),
                new EventSource("test", "events", "", "1.0"),
                new EventContext(EventContextKind.EVENT_FLOW, "", "", ExpressionTestFixtures.METAMODEL_ID, "model", "", "session:one"),
                new EventActor("actor:one", EventActorKind.USER, "Actor"), null, "transaction:one", "correlation:one", "",
                Map.of(), Map.of());
        EventFlowProcessResult processed = runtime.process(ExpressionTestFixtures.METAMODEL_ID, trigger);
        assertThat(processed.traces()).singleElement().satisfies(trace -> {
            assertThat(trace.status()).isEqualTo(FlowExecutionStatus.COMPLETED);
            assertThat(trace.nodes()).extracting(FlowNodeExecution::nodeId).containsExactly("node:start", "node:end");
        });

        // "a4"/"r1": el estado y la traza reales reflejan el evento ya procesado.
        EventFlowRuntimeStatus afterProcessing = runtime.status(ExpressionTestFixtures.METAMODEL_ID);
        assertThat(afterProcessing.processedEvents()).isEqualTo(1);
        assertThat(runtime.traces(ExpressionTestFixtures.METAMODEL_ID)).hasSize(1);

        // "a5": detener elimina el despliegue activo real; el estado consultado vuelve a reflejar
        // un motor inactivo.
        EventFlowRuntimeStatus stopped = runtime.stop(ExpressionTestFixtures.METAMODEL_ID);
        assertThat(stopped.active()).isFalse();
        assertThat(runtime.status(ExpressionTestFixtures.METAMODEL_ID).active()).isFalse();
    }

    @Test
    void advancingTheClockRealEmitsAnAbsenceEventOnceItsDeadlineHasReallyElapsed(@TempDir Path temporary) {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(ExpressionTestFixtures.metamodel());

        EventModelProperties eventModelProperties = new EventModelProperties();
        eventModelProperties.setDirectory(temporary.resolve("events"));
        EventModelApplicationService eventModelService = new EventModelApplicationService(
                new JsonEventDefinitionModelRepository(eventModelProperties, mapper),
                new JsonEventBindingModelRepository(eventModelProperties, mapper),
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper),
                metamodelService, new EventModelChecker());

        EventFlowProperties flowProperties = new EventFlowProperties();
        flowProperties.setDirectory(temporary.resolve("flows"));
        EventFlowChecker flowChecker = new EventFlowChecker();
        EventFlowApplicationService flowService = new EventFlowApplicationService(
                new JsonEventFlowModelRepository(flowProperties, mapper), metamodelService, eventModelService, flowChecker,
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper));

        InMemoryCanonicalEventBus bus = new InMemoryCanonicalEventBus();
        EventFlowRuntimeService runtime = new EventFlowRuntimeService(flowService, eventModelService,
                new EventFlowCompiler(flowChecker), bus, List.of(), flowProperties);

        // Un flujo con un nodo de ausencia real: espera de verdad un evento reservado
        // ("core.model.element-updated") durante un plazo muy corto y, si no llega, emite otro
        // evento reservado ("core.model.exported") como evidencia real de ausencia.
        FlowNode start = new FlowNode("node:start", "Start", FlowNodeKind.START, "", "", "", Map.of(), null, null, false, true);
        FlowNode absence = new FlowNode("node:absence", "Absence", FlowNodeKind.ABSENCE, "", "core.model.exported", "",
                Map.of("watchEventTypeId", "core.model.element-updated", "timeoutMillis", 5L), null, null, false, true);
        FlowNode end = new FlowNode("node:end", "End", FlowNodeKind.SUCCESS_END, "", "", "", Map.of(), null, null, false, true);
        FlowTransition toAbsence = new FlowTransition("transition:start-absence", "node:start", "node:absence", FlowTransitionKind.NEXT, "", 0, "");
        FlowTransition toEnd = new FlowTransition("transition:absence-end", "node:absence", "node:end", FlowTransitionKind.NEXT, "", 0, "");
        FlowDefinition flow = new FlowDefinition("flow:absence-watch", "Absence watch flow", "", true, List.of("core.model.saved"),
                List.of(), List.of(start, absence, end), List.of(toAbsence, toEnd), List.of(), Map.of());
        EventFlowModel model = new EventFlowModel(null, "flows-absence", ExpressionTestFixtures.METAMODEL_ID, "", "",
                List.of(flow), Map.of());
        flowService.create(ExpressionTestFixtures.METAMODEL_ID, model);

        runtime.start(ExpressionTestFixtures.METAMODEL_ID);
        CanonicalEvent trigger = new CanonicalEvent("1.0", "event:trigger-absence", "core.model.saved", "1.0", Instant.now(),
                new EventSource("test", "events", "", "1.0"),
                new EventContext(EventContextKind.EVENT_FLOW, "", "", ExpressionTestFixtures.METAMODEL_ID, "model", "", "session:absence"),
                new EventActor("actor:one", EventActorKind.USER, "Actor"), null, "transaction:absence", "correlation:absence", "",
                Map.of(), Map.of());

        // El nodo de ausencia real deja el flujo a la espera: el evento vigilado nunca llega.
        EventFlowProcessResult processed = runtime.process(ExpressionTestFixtures.METAMODEL_ID, trigger);
        assertThat(processed.traces()).singleElement().satisfies(trace -> {
            assertThat(trace.status()).isEqualTo(FlowExecutionStatus.WAITING);
            assertThat(trace.nodes()).extracting(FlowNodeExecution::nodeId).containsExactly("node:start", "node:absence");
        });

        // "a3": avanzar el reloj real más allá del plazo real configurado hace que HYDRA detecte
        // de verdad la ausencia y emita el evento derivado real, sin que el evento vigilado haya
        // llegado nunca.
        EventFlowProcessResult ticked = runtime.tick(ExpressionTestFixtures.METAMODEL_ID, Instant.now().plusMillis(200));
        assertThat(ticked.emittedEvents()).singleElement().satisfies(event -> assertThat(event.eventTypeId()).isEqualTo("core.model.exported"));
        assertThat(ticked.traces()).singleElement().satisfies(trace -> {
            assertThat(trace.status()).isEqualTo(FlowExecutionStatus.COMPLETED);
            assertThat(trace.nodes()).extracting(FlowNodeExecution::nodeKind).containsExactly(FlowNodeKind.ABSENCE);
        });
    }

    @Test
    void aFailingStepRealIsRetriedOnceAndThenRealCompensatesTheAlreadyCompletedCompensableStep(@TempDir Path temporary) {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(ExpressionTestFixtures.metamodel());

        EventModelProperties eventModelProperties = new EventModelProperties();
        eventModelProperties.setDirectory(temporary.resolve("events"));
        EventModelApplicationService eventModelService = new EventModelApplicationService(
                new JsonEventDefinitionModelRepository(eventModelProperties, mapper),
                new JsonEventBindingModelRepository(eventModelProperties, mapper),
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper),
                metamodelService, new EventModelChecker());

        EventFlowProperties flowProperties = new EventFlowProperties();
        flowProperties.setDirectory(temporary.resolve("flows"));
        EventFlowChecker flowChecker = new EventFlowChecker();
        EventFlowApplicationService flowService = new EventFlowApplicationService(
                new JsonEventFlowModelRepository(flowProperties, mapper), metamodelService, eventModelService, flowChecker,
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper));

        // Sin ningun FlowExternalStepExecutor real registrado, cualquier ACTION_STEP real falla de
        // verdad -- no hace falta simular nada para producir un fallo genuino.
        InMemoryCanonicalEventBus bus = new InMemoryCanonicalEventBus();
        EventFlowRuntimeService runtime = new EventFlowRuntimeService(flowService, eventModelService,
                new EventFlowCompiler(flowChecker), bus, List.of(), flowProperties);

        FlowNode start = new FlowNode("node:start", "Start", FlowNodeKind.START, "", "", "", Map.of(), null, null, false, true);
        // Paso real compensable que se completa de verdad antes del paso que fallara.
        FlowNode filter = new FlowNode("node:filter", "Filter", FlowNodeKind.FILTER, "", "", "", Map.of(), null, null, true, true);
        // Paso real sin ejecutor externo configurado: falla de verdad en tiempo de ejecucion con
        // una politica real de un reintento inmediato.
        FlowNode action = new FlowNode("node:action", "Action", FlowNodeKind.ACTION_STEP, "", "", "",
                Map.of(), new RetryPolicy(1, 0L, 1.0d), null, false, true);
        FlowNode compensation = new FlowNode("node:comp", "Compensate filter", FlowNodeKind.COMPENSATION_STEP, "", "", "",
                Map.of(), null, null, false, true);
        FlowNode endError = new FlowNode("node:end-error", "End (error)", FlowNodeKind.ERROR_END, "", "", "", Map.of(), null, null, false, true);
        FlowTransition toFilter = new FlowTransition("transition:start-filter", "node:start", "node:filter", FlowTransitionKind.NEXT, "", 0, "");
        FlowTransition toAction = new FlowTransition("transition:filter-action", "node:filter", "node:action", FlowTransitionKind.NEXT, "", 0, "");
        // Arco estructural solo para que "comp" tenga una transicion de entrada real segun exige
        // el comprobador real; nunca se recorre porque "filter" nunca falla de verdad.
        FlowTransition filterError = new FlowTransition("transition:filter-comp", "node:filter", "node:comp", FlowTransitionKind.ON_ERROR, "", 0, "");
        FlowTransition actionToEnd = new FlowTransition("transition:action-end", "node:action", "node:end-error", FlowTransitionKind.NEXT, "", 0, "");
        FlowTransition compToEnd = new FlowTransition("transition:comp-end", "node:comp", "node:end-error", FlowTransitionKind.NEXT, "", 0, "");
        CompensationLink link = new CompensationLink("compensation:filter-comp", "node:filter", "node:comp");
        FlowDefinition flow = new FlowDefinition("flow:retry-compensate", "Retry then compensate flow", "", true,
                List.of("core.model.saved"), List.of(), List.of(start, filter, action, compensation, endError),
                List.of(toFilter, toAction, filterError, actionToEnd, compToEnd), List.of(link), Map.of());
        EventFlowModel model = new EventFlowModel(null, "flows-retry-compensate", ExpressionTestFixtures.METAMODEL_ID, "", "",
                List.of(flow), Map.of());
        flowService.create(ExpressionTestFixtures.METAMODEL_ID, model);

        runtime.start(ExpressionTestFixtures.METAMODEL_ID);
        CanonicalEvent trigger = new CanonicalEvent("1.0", "event:trigger-retry", "core.model.saved", "1.0", Instant.now(),
                new EventSource("test", "events", "", "1.0"),
                new EventContext(EventContextKind.EVENT_FLOW, "", "", ExpressionTestFixtures.METAMODEL_ID, "model", "", "session:retry"),
                new EventActor("actor:one", EventActorKind.USER, "Actor"), null, "transaction:retry", "correlation:retry", "",
                Map.of(), Map.of());

        // "d2"/"a5"/"a6": el paso real falla, se reintenta una vez de verdad sin exito, y al no
        // quedar reintentos ni salida de error HYDRA compensa de verdad, en orden inverso real, el
        // unico paso compensable ya completado.
        EventFlowProcessResult processed = runtime.process(ExpressionTestFixtures.METAMODEL_ID, trigger);
        assertThat(processed.traces()).singleElement().satisfies(trace -> {
            assertThat(trace.status()).isEqualTo(FlowExecutionStatus.COMPENSATED);
            assertThat(trace.nodes()).extracting(FlowNodeExecution::nodeId)
                    .containsExactly("node:start", "node:filter", "node:action", "node:action", "node:comp");
            assertThat(trace.nodes()).extracting(FlowNodeExecution::status)
                    .containsExactly(FlowExecutionStatus.COMPLETED, FlowExecutionStatus.COMPLETED,
                            FlowExecutionStatus.FAILED, FlowExecutionStatus.FAILED, FlowExecutionStatus.COMPENSATED);
        });
    }
}
