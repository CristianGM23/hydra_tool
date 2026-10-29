package com.example.editor.event.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
import com.example.editor.event.flow.diagram.EventFlowDiagramDocument;
import com.example.editor.event.flow.diagram.EventFlowDiagramLoadResult;
import com.example.editor.event.flow.diagram.EventFlowDiagramService;
import com.example.editor.event.flow.diagram.EventFlowEdgeView;
import com.example.editor.event.flow.diagram.EventFlowNodeView;
import com.example.editor.event.flow.diagram.EventFlowPoint;
import com.example.editor.event.flow.diagram.JsonEventFlowDiagramRepository;
import com.example.editor.event.flow.persistence.JsonEventFlowModelRepository;
import com.example.editor.event.flow.runtime.EventFlowCompiler;
import com.example.editor.event.flow.runtime.EventFlowProcessResult;
import com.example.editor.event.flow.runtime.EventFlowRuntimeService;
import com.example.editor.event.flow.runtime.EventFlowRuntimeStatus;
import com.example.editor.event.flow.runtime.ExecutableEventFlowPlan;
import com.example.editor.event.flow.runtime.FlowExecutionStatus;
import com.example.editor.event.flow.runtime.FlowNodeExecution;
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
 * Evidencia FUERTE de la composición y persistencia de flujos de eventos: EventFlowApplicationService,
 * JsonEventFlowModelRepository, EventFlowChecker, EventFlowCompiler, EventFlowDiagramService y
 * EventFlowRuntimeService reales sobre {@code @TempDir}, sin mocks. El oráculo es el JSON crudo del
 * disco, literales esperados y un SHA-256 recalculado aquí. Etapas: events_compose_flow.a1..a8,
 * events_author_flow.a4 y events_author_flow.a6.
 */
class EventFlowAuthoringPersistenceIntegrationTests {

    private static final String LIB = ExpressionTestFixtures.METAMODEL_ID;
    private static final List<String> TRIGGER = List.of("core.model.saved");

    private record Stack(JsonMapper mapper, EventFlowApplicationService flowService, EventFlowChecker checker,
            JsonEventFlowModelRepository repository, EventModelApplicationService eventService, EventFlowProperties properties,
            EventFlowDiagramService diagramService, Path flowFile, Path diagramFile) { }

    private static Stack stack(Path temporary, boolean createMetamodel) {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        if(createMetamodel) metamodelService.create(ExpressionTestFixtures.metamodel());
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
        JsonEventFlowModelRepository repository = new JsonEventFlowModelRepository(flowProperties, mapper);
        EventFlowApplicationService flowService = new EventFlowApplicationService(repository, metamodelService, eventService, checker,
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper));
        EventFlowDiagramService diagramService = new EventFlowDiagramService(new JsonEventFlowDiagramRepository(flowProperties, mapper), flowService);
        return new Stack(mapper, flowService, checker, repository, eventService, flowProperties, diagramService,
                temporary.resolve("flows").resolve("library.event-flows.json"),
                temporary.resolve("flows").resolve("library.event-flow-diagram.json"));
    }

    private static FlowNode node(String id, FlowNodeKind kind, String eventTypeId, boolean compensable) {
        return new FlowNode(id, id, kind, "", eventTypeId, "", Map.of(), null, null, compensable, true);
    }

    private static FlowTransition transition(String id, String from, String to, FlowTransitionKind kind) {
        return new FlowTransition(id, from, to, kind, "", 0, "");
    }

    private static FlowDefinition flow(String id, List<String> triggers, List<FlowNode> nodes, List<FlowTransition> transitions) {
        return new FlowDefinition(id, id, "", true, triggers, List.of(), nodes, transitions, List.of(), Map.of());
    }

    private static FlowDefinition minimalFlow(String id, List<String> triggers) {
        return flow(id, triggers,
                List.of(node("node:start", FlowNodeKind.START, "", false), node("node:end", FlowNodeKind.SUCCESS_END, "", false)),
                List.of(transition("transition:start-end", "node:start", "node:end", FlowTransitionKind.NEXT)));
    }

    private static EventFlowModel model(FlowDefinition... flows) {
        return new EventFlowModel(null, "flows-library", LIB, "", "", List.of(flows), Map.of());
    }

    private static List<String> codes(EventFlowCheckReport report) {
        return report.diagnostics().stream().map(FlowDiagnostic::code).toList();
    }

    private static String etagOf(Path file) throws Exception {
        return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))) + "\"";
    }

    private static JsonNode read(Stack s, Path file) throws Exception {
        return s.mapper().readTree(Files.readString(file, StandardCharsets.UTF_8));
    }

    private static JsonNode nodeById(JsonNode flow, String id) {
        JsonNode nodes = flow.get("nodes");
        for(int index = 0; index < nodes.size(); index++) if(id.equals(nodes.get(index).get("id").asString())) return nodes.get(index);
        throw new AssertionError("node not found in JSON: " + id);
    }

    @Test
    @DisplayName("events_compose_flow.a1/a2/a4/a5/a6/a7/a8: un flujo valido con disparadores, variables, reintentos, plazos y compensaciones queda en el JSON crudo")
    void aValidFlowWithTriggersVariablesRetriesDeadlinesAndCompensationsIsPersistedAsRawJson(@TempDir Path temporary) throws Exception {
        Stack s = stack(temporary, true);
        FlowNode start = node("node:start", FlowNodeKind.START, "", false);
        FlowNode filter = node("node:filter", FlowNodeKind.FILTER, "", true);
        FlowNode action = new FlowNode("node:action", "Action", FlowNodeKind.ACTION_STEP, "", "", "", Map.of(),
                new RetryPolicy(3, 100L, 2.0d), new TimeoutPolicy(5000L, "transition:action-timeout"), false, true);
        FlowNode compensation = node("node:comp", FlowNodeKind.COMPENSATION_STEP, "", false);
        FlowNode endOk = node("node:end-ok", FlowNodeKind.SUCCESS_END, "", false);
        FlowNode endError = node("node:end-error", FlowNodeKind.ERROR_END, "", false);
        FlowDefinition flow = new FlowDefinition("flow:loans", "Loans", "", true, List.of("core.model.saved", "core.model.exported"),
                List.of(new FlowVariable("variable:attempts", "attempts", "INTEGER", 3)),
                List.of(start, filter, action, compensation, endOk, endError),
                List.of(transition("transition:start-filter", "node:start", "node:filter", FlowTransitionKind.NEXT),
                        transition("transition:filter-action", "node:filter", "node:action", FlowTransitionKind.NEXT),
                        transition("transition:filter-comp", "node:filter", "node:comp", FlowTransitionKind.ON_ERROR),
                        transition("transition:action-ok", "node:action", "node:end-ok", FlowTransitionKind.ON_SUCCESS),
                        transition("transition:action-error", "node:action", "node:end-error", FlowTransitionKind.ON_ERROR),
                        transition("transition:action-timeout", "node:action", "node:end-error", FlowTransitionKind.TIMEOUT),
                        transition("transition:comp-end", "node:comp", "node:end-error", FlowTransitionKind.NEXT)),
                List.of(new CompensationLink("compensation:filter-comp", "node:filter", "node:comp")), Map.of());

        EventFlowSaveResponse saved = s.flowService().create(LIB, model(flow));

        assertThat(saved.checkReport().valid()).isTrue();
        JsonNode root = read(s, s.flowFile());
        assertThat(root.get("id").asString()).isEqualTo("flows-library");
        assertThat(root.get("metamodelId").asString()).isEqualTo("library");
        JsonNode rawFlow = root.get("flows").get(0);
        assertThat(rawFlow.get("id").asString()).isEqualTo("flow:loans");
        assertThat(rawFlow.get("triggerEventTypeIds").size()).isEqualTo(2);
        assertThat(rawFlow.get("triggerEventTypeIds").get(0).asString()).isEqualTo("core.model.saved");
        assertThat(rawFlow.get("triggerEventTypeIds").get(1).asString()).isEqualTo("core.model.exported");
        assertThat(rawFlow.get("variables").get(0).get("name").asString()).isEqualTo("attempts");
        assertThat(rawFlow.get("variables").get(0).get("type").asString()).isEqualTo("INTEGER");
        assertThat(rawFlow.get("variables").get(0).get("initialValue").asInt()).isEqualTo(3);
        assertThat(rawFlow.get("nodes").size()).isEqualTo(6);
        JsonNode rawAction = nodeById(rawFlow, "node:action");
        assertThat(rawAction.get("kind").asString()).isEqualTo("ACTION_STEP");
        assertThat(rawAction.get("retryPolicy").get("maxAttempts").asInt()).isEqualTo(3);
        assertThat(rawAction.get("retryPolicy").get("delayMillis").asLong()).isEqualTo(100L);
        assertThat(rawAction.get("retryPolicy").get("backoffMultiplier").asDouble()).isEqualTo(2.0d);
        assertThat(rawAction.get("timeoutPolicy").get("timeoutMillis").asLong()).isEqualTo(5000L);
        assertThat(rawAction.get("timeoutPolicy").get("timeoutTransitionId").asString()).isEqualTo("transition:action-timeout");
        assertThat(nodeById(rawFlow, "node:filter").get("compensable").asBoolean()).isTrue();
        assertThat(rawFlow.get("transitions").size()).isEqualTo(7);
        assertThat(rawFlow.get("compensations").get(0).get("sourceNodeId").asString()).isEqualTo("node:filter");
        assertThat(rawFlow.get("compensations").get(0).get("compensationNodeId").asString()).isEqualTo("node:comp");
        assertThat(saved.etag()).isEqualTo(etagOf(s.flowFile()));
        assertThat(s.flowService().check(LIB).diagnostics()).extracting(FlowDiagnostic::severity)
                .containsOnly(FlowDiagnosticSeverity.INFO);
    }

    @Test
    @DisplayName("events_compose_flow.a1/a2/a4/a7 + events_author_flow.a4/a6: 0 o 2 START, decision sin rama, JOIN con una entrada, huerfano y disparador desconocido se rechazan sin tocar el fichero")
    void structurallyInvalidFlowsAreRejectedWithExactCodesAndTheFileDoesNotChange(@TempDir Path temporary) throws Exception {
        Stack s = stack(temporary, true);
        List<Object[]> cases = new ArrayList<>();
        cases.add(new Object[] {
                model(flow("flow:no-start", TRIGGER,
                        List.of(node("node:a", FlowNodeKind.FILTER, "", false), node("node:end", FlowNodeKind.SUCCESS_END, "", false)),
                        List.of(transition("transition:a-end", "node:a", "node:end", FlowTransitionKind.NEXT)))),
                List.of("FLOW_START_COUNT", "ORPHAN_NODE") });
        cases.add(new Object[] {
                model(flow("flow:two-starts", TRIGGER,
                        List.of(node("node:s1", FlowNodeKind.START, "", false), node("node:s2", FlowNodeKind.START, "", false),
                                node("node:end", FlowNodeKind.SUCCESS_END, "", false)),
                        List.of(transition("transition:s1-end", "node:s1", "node:end", FlowTransitionKind.NEXT),
                                transition("transition:s2-end", "node:s2", "node:end", FlowTransitionKind.NEXT)))),
                List.of("FLOW_START_COUNT", "UNREACHABLE_NODE") });
        cases.add(new Object[] {
                model(flow("flow:decision", TRIGGER,
                        List.of(node("node:start", FlowNodeKind.START, "", false), node("node:dec", FlowNodeKind.DECISION, "", false),
                                node("node:end", FlowNodeKind.SUCCESS_END, "", false)),
                        List.of(transition("transition:start-dec", "node:start", "node:dec", FlowTransitionKind.NEXT),
                                transition("transition:dec-end", "node:dec", "node:end", FlowTransitionKind.TRUE)))),
                List.of("DECISION_BRANCHES") });
        cases.add(new Object[] {
                model(flow("flow:join", TRIGGER,
                        List.of(node("node:start", FlowNodeKind.START, "", false), node("node:join", FlowNodeKind.JOIN, "", false),
                                node("node:end", FlowNodeKind.SUCCESS_END, "", false)),
                        List.of(transition("transition:start-join", "node:start", "node:join", FlowTransitionKind.NEXT),
                                transition("transition:join-end", "node:join", "node:end", FlowTransitionKind.NEXT)))),
                List.of("JOIN_INPUT_COUNT") });
        cases.add(new Object[] {
                model(flow("flow:orphan", TRIGGER,
                        List.of(node("node:start", FlowNodeKind.START, "", false), node("node:end", FlowNodeKind.SUCCESS_END, "", false),
                                node("node:lost", FlowNodeKind.FILTER, "", false)),
                        List.of(transition("transition:start-end", "node:start", "node:end", FlowTransitionKind.NEXT),
                                transition("transition:lost-end", "node:lost", "node:end", FlowTransitionKind.NEXT)))),
                List.of("ORPHAN_NODE", "UNREACHABLE_NODE") });
        cases.add(new Object[] { model(minimalFlow("flow:trigger", List.of("custom.unknown"))), List.of("UNKNOWN_TRIGGER_EVENT") });

        // create sobre un almacen vacio: rechazado y sin fichero
        for(Object[] item : cases) {
            EventFlowModel invalid = (EventFlowModel) item[0];
            @SuppressWarnings("unchecked")
            List<String> expected = (List<String>) item[1];
            assertThat(codes(s.flowService().checkDraft(LIB, invalid))).containsExactlyInAnyOrderElementsOf(expected);
            assertThatThrownBy(() -> s.flowService().create(LIB, invalid)).isInstanceOfSatisfying(EventFlowApplicationException.class,
                    e -> assertThat(e.getReason()).isEqualTo(EventFlowApplicationException.Reason.INVALID_REQUEST));
        }
        assertThat(s.flowFile()).doesNotExist();

        // replace sobre un flujo valido ya guardado: rechazado y el fichero queda byte a byte igual
        EventFlowSaveResponse valid = s.flowService().create(LIB, model(minimalFlow("flow:smoke", TRIGGER)));
        byte[] before = Files.readAllBytes(s.flowFile());
        for(Object[] item : cases) {
            EventFlowModel invalid = (EventFlowModel) item[0];
            assertThatThrownBy(() -> s.flowService().replace(LIB, invalid, valid.etag())).isInstanceOfSatisfying(
                    EventFlowApplicationException.class,
                    e -> assertThat(e.getReason()).isEqualTo(EventFlowApplicationException.Reason.INVALID_REQUEST));
        }
        assertThat(Files.readAllBytes(s.flowFile())).isEqualTo(before);
        assertThat(s.flowService().checkDraft(LIB, model(minimalFlow("flow:smoke", TRIGGER))).valid()).isTrue();
    }

    @Test
    @DisplayName("events_compose_flow.a3: un tipo de cada una de las 29 clases de paso se guarda y se compila")
    void oneNodeOfEveryOneOfTheTwentyNineKindsIsSavedAndCompiled(@TempDir Path temporary) throws Exception {
        Stack s = stack(temporary, true);
        List<FlowDefinition> flows = new ArrayList<>();
        flows.add(minimalFlow("flow:kind-start-success-end", TRIGGER));
        flows.add(flow("flow:kind-error-end", TRIGGER,
                List.of(node("node:start", FlowNodeKind.START, "", false), node("node:end", FlowNodeKind.ERROR_END, "", false)),
                List.of(transition("transition:start-end", "node:start", "node:end", FlowTransitionKind.NEXT))));
        flows.add(flow("flow:kind-source", TRIGGER,
                List.of(node("node:source", FlowNodeKind.SOURCE, "core.model.saved", false), node("node:end", FlowNodeKind.SUCCESS_END, "", false)),
                List.of(transition("transition:source-end", "node:source", "node:end", FlowTransitionKind.NEXT))));
        Set<FlowNodeKind> eventKinds = Set.of(FlowNodeKind.EVENT_STEP, FlowNodeKind.WAIT_FOR_EVENT, FlowNodeKind.EMIT, FlowNodeKind.ABSENCE);
        for(FlowNodeKind kind : FlowNodeKind.values()) {
            if(kind == FlowNodeKind.START || kind == FlowNodeKind.SUCCESS_END || kind == FlowNodeKind.ERROR_END || kind == FlowNodeKind.SOURCE) continue;
            String eventType = eventKinds.contains(kind) ? "core.model.saved" : "";
            List<FlowNode> nodes = List.of(node("node:start", FlowNodeKind.START, "", false),
                    node("node:subject", kind, eventType, false), node("node:end", FlowNodeKind.SUCCESS_END, "", false));
            List<FlowTransition> transitions;
            if(kind == FlowNodeKind.DECISION || kind == FlowNodeKind.ROUTE) {
                transitions = List.of(transition("transition:in", "node:start", "node:subject", FlowTransitionKind.NEXT),
                        transition("transition:true", "node:subject", "node:end", FlowTransitionKind.TRUE),
                        transition("transition:false", "node:subject", "node:end", FlowTransitionKind.FALSE));
            } else if(kind == FlowNodeKind.JOIN) {
                transitions = List.of(transition("transition:in-one", "node:start", "node:subject", FlowTransitionKind.NEXT),
                        transition("transition:in-two", "node:start", "node:subject", FlowTransitionKind.NEXT),
                        transition("transition:out", "node:subject", "node:end", FlowTransitionKind.NEXT));
            } else {
                transitions = List.of(transition("transition:in", "node:start", "node:subject", FlowTransitionKind.NEXT),
                        transition("transition:out", "node:subject", "node:end", FlowTransitionKind.NEXT));
            }
            flows.add(flow("flow:kind-" + kind.name().toLowerCase().replace('_', '-'), TRIGGER, nodes, transitions));
        }

        EventFlowSaveResponse saved = s.flowService().create(LIB, model(flows.toArray(new FlowDefinition[0])));

        assertThat(saved.checkReport().valid()).isTrue();
        JsonNode raw = read(s, s.flowFile());
        assertThat(raw.get("flows").size()).isEqualTo(28);
        Set<String> kindsOnDisk = new TreeSet<>();
        for(int f = 0; f < raw.get("flows").size(); f++) {
            JsonNode nodes = raw.get("flows").get(f).get("nodes");
            for(int n = 0; n < nodes.size(); n++) kindsOnDisk.add(nodes.get(n).get("kind").asString());
        }
        assertThat(kindsOnDisk).hasSize(29).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(FlowNodeKind.values()).map(Enum::name).toList());

        ExecutableEventFlowPlan plan = new EventFlowCompiler(s.checker()).compile(s.flowService().find(LIB).eventFlowModel(), null);
        assertThat(plan.flows()).hasSize(28);
        plan.flows().forEach(compiled -> assertThat(compiled.nodes()).hasSize(compiled.source().nodes().size()));
        assertThat(plan.flowsByTriggerEventType().get("core.model.saved")).hasSize(28);
    }

    @Test
    @DisplayName("events_compose_flow.a8: las posiciones de las cajas se guardan en el JSON del diagrama, se releen y respetan el ETag")
    void boxPositionsAreSavedRereadAndGuardedByTheEtag(@TempDir Path temporary) throws Exception {
        Stack s = stack(temporary, true);
        s.flowService().create(LIB, model(minimalFlow("flow:smoke", TRIGGER)));
        EventFlowDiagramDocument diagram = new EventFlowDiagramDocument(null, "diagram.flows", LIB, "flows-library",
                List.of(new EventFlowNodeView("node:start", 10, 20, 0, 0, false), new EventFlowNodeView("node:end", 300, 40, 200, 90, true)),
                List.of(new EventFlowEdgeView("transition:start-end", List.of(new EventFlowPoint(150, 30)))));

        EventFlowDiagramLoadResult saved = s.diagramService().save(LIB, diagram, null);

        JsonNode raw = read(s, s.diagramFile());
        assertThat(raw.get("eventFlowModelId").asString()).isEqualTo("flows-library");
        assertThat(raw.get("nodes").size()).isEqualTo(2);
        assertThat(raw.get("nodes").get(0).get("nodeId").asString()).isEqualTo("node:start");
        assertThat(raw.get("nodes").get(0).get("x").asDouble()).isEqualTo(10.0d);
        assertThat(raw.get("nodes").get(0).get("y").asDouble()).isEqualTo(20.0d);
        assertThat(raw.get("nodes").get(0).get("width").asDouble()).isEqualTo(180.0d);
        assertThat(raw.get("nodes").get(0).get("height").asDouble()).isEqualTo(72.0d);
        assertThat(raw.get("nodes").get(1).get("x").asDouble()).isEqualTo(300.0d);
        assertThat(raw.get("nodes").get(1).get("collapsed").asBoolean()).isTrue();
        assertThat(raw.get("edges").get(0).get("transitionId").asString()).isEqualTo("transition:start-end");
        assertThat(raw.get("edges").get(0).get("points").get(0).get("x").asDouble()).isEqualTo(150.0d);
        assertThat(saved.etag()).isEqualTo(etagOf(s.diagramFile()));

        EventFlowDiagramLoadResult reread = s.diagramService().find(LIB);
        assertThat(reread.diagram().nodes()).extracting(EventFlowNodeView::nodeId).containsExactly("node:start", "node:end");
        assertThat(reread.diagram().nodes().get(1).x()).isEqualTo(300.0d);
        assertThat(reread.etag()).isEqualTo(saved.etag());

        EventFlowDiagramDocument moved = new EventFlowDiagramDocument(null, "diagram.flows", LIB, "flows-library",
                List.of(new EventFlowNodeView("node:start", 55, 66, 0, 0, false)), List.of());
        byte[] before = Files.readAllBytes(s.diagramFile());
        assertThatThrownBy(() -> s.diagramService().save(LIB, moved, "\"stale\"")).isInstanceOfSatisfying(
                EventFlowApplicationException.class, e -> assertThat(e.getReason()).isEqualTo(EventFlowApplicationException.Reason.PRECONDITION_FAILED));
        assertThat(Files.readAllBytes(s.diagramFile())).isEqualTo(before);
        s.diagramService().save(LIB, moved, saved.etag());
        assertThat(read(s, s.diagramFile()).get("nodes").get(0).get("x").asDouble()).isEqualTo(55.0d);

        EventFlowDiagramDocument wrongModel = new EventFlowDiagramDocument(null, "diagram.flows", LIB, "other-model", List.of(), List.of());
        assertThatThrownBy(() -> s.diagramService().save(LIB, wrongModel, null)).isInstanceOfSatisfying(
                EventFlowApplicationException.class, e -> assertThat(e.getReason()).isEqualTo(EventFlowApplicationException.Reason.INVALID_REQUEST));
    }

    @Test
    @DisplayName("events_compose_flow.a7/a8: tras recargar desde disco el flujo guardado se compila y arranca; el invalido es NOT_EXECUTABLE")
    void aReloadedSavedFlowCompilesAndStartsWhileAnInvalidStoredFlowIsNotExecutable(@TempDir Path temporary) throws Exception {
        Stack first = stack(temporary, true);
        first.flowService().create(LIB, model(minimalFlow("flow:smoke", TRIGGER)));

        Stack reloaded = stack(temporary, false);
        EventFlowLoadResult loaded = reloaded.flowService().find(LIB);
        assertThat(loaded.eventFlowModel().flows()).extracting(FlowDefinition::id).containsExactly("flow:smoke");
        assertThat(loaded.eventFlowModel().flows().get(0).nodes()).extracting(FlowNode::id).containsExactly("node:start", "node:end");
        assertThat(loaded.etag()).isEqualTo(etagOf(reloaded.flowFile()));
        EventFlowRuntimeService runtime = new EventFlowRuntimeService(reloaded.flowService(), reloaded.eventService(),
                new EventFlowCompiler(reloaded.checker()), new InMemoryCanonicalEventBus(), List.of(), reloaded.properties());
        try {
            assertThat(runtime.compile(LIB).flows()).hasSize(1);
            EventFlowRuntimeStatus started = runtime.start(LIB);
            assertThat(started.active()).isTrue();
            assertThat(started.flowCount()).isEqualTo(1);
            CanonicalEvent trigger = new CanonicalEvent("1.0", "event:trigger", "core.model.saved", "1.0", Instant.now(),
                    new EventSource("test", "events", "", "1.0"),
                    new EventContext(EventContextKind.EVENT_FLOW, "", "", LIB, "model", "", "session:one"),
                    new EventActor("actor:one", EventActorKind.USER, "Actor"), null, "transaction:one", "correlation:one", "",
                    Map.of(), Map.of());
            EventFlowProcessResult processed = runtime.process(LIB, trigger);
            assertThat(processed.traces()).singleElement().satisfies(trace -> {
                assertThat(trace.status()).isEqualTo(FlowExecutionStatus.COMPLETED);
                assertThat(trace.nodes()).extracting(FlowNodeExecution::nodeId).containsExactly("node:start", "node:end");
            });
        } finally {
            runtime.close();
        }
    }

    @Test
    @DisplayName("events_compose_flow.a7: un flujo invalido ya presente en disco no se compila ni arranca (NOT_EXECUTABLE)")
    void anInvalidStoredFlowIsNotExecutable(@TempDir Path temporary) {
        Stack s = stack(temporary, true);
        EventFlowModel invalid = model(flow("flow:no-start", TRIGGER,
                List.of(node("node:a", FlowNodeKind.FILTER, "", false), node("node:end", FlowNodeKind.SUCCESS_END, "", false)),
                List.of(transition("transition:a-end", "node:a", "node:end", FlowTransitionKind.NEXT))));
        s.repository().create(invalid);
        EventFlowRuntimeService runtime = new EventFlowRuntimeService(s.flowService(), s.eventService(),
                new EventFlowCompiler(s.checker()), new InMemoryCanonicalEventBus(), List.of(), s.properties());
        try {
            assertThatThrownBy(() -> runtime.compile(LIB)).isInstanceOfSatisfying(EventFlowApplicationException.class,
                    e -> assertThat(e.getReason()).isEqualTo(EventFlowApplicationException.Reason.NOT_EXECUTABLE));
            assertThatThrownBy(() -> runtime.start(LIB)).isInstanceOfSatisfying(EventFlowApplicationException.class,
                    e -> assertThat(e.getReason()).isEqualTo(EventFlowApplicationException.Reason.NOT_EXECUTABLE));
            assertThatThrownBy(() -> new EventFlowCompiler(s.checker()).compile(invalid, null)).isInstanceOfSatisfying(
                    EventFlowApplicationException.class,
                    e -> assertThat(e.getReason()).isEqualTo(EventFlowApplicationException.Reason.NOT_EXECUTABLE));
            assertThat(runtime.status(LIB).active()).isFalse();
        } finally {
            runtime.close();
        }
    }
}
