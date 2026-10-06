import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.EventActor;
import com.example.editor.event.EventActorKind;
import com.example.editor.event.EventBindingModel;
import com.example.editor.event.EventContext;
import com.example.editor.event.EventContextKind;
import com.example.editor.event.EventDefinitionModel;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.EventModelCheckReport;
import com.example.editor.event.EventModelLoadResult;
import com.example.editor.event.EventSource;
import com.example.editor.event.EventSubject;
import com.example.editor.event.EventSubjectKind;
import com.example.editor.event.bus.InMemoryCanonicalEventBus;
import com.example.editor.event.flow.EventFlowApplicationService;
import com.example.editor.event.flow.EventFlowCheckReport;
import com.example.editor.event.flow.EventFlowChecker;
import com.example.editor.event.flow.EventFlowLoadResult;
import com.example.editor.event.flow.EventFlowModel;
import com.example.editor.event.flow.EventFlowProperties;
import com.example.editor.event.flow.FlowDefinition;
import com.example.editor.event.flow.FlowNode;
import com.example.editor.event.flow.FlowNodeKind;
import com.example.editor.event.flow.FlowTransition;
import com.example.editor.event.flow.FlowTransitionKind;
import com.example.editor.event.flow.RetryPolicy;
import com.example.editor.event.flow.TimeoutPolicy;
import com.example.editor.event.flow.runtime.EventFlowCompiler;
import com.example.editor.event.flow.runtime.EventFlowProcessResult;
import com.example.editor.event.flow.runtime.EventFlowRuntimeService;
import com.example.editor.event.flow.runtime.ExecutableEventFlowPlan;
import com.example.editor.gamification.GamificationApplicationService;
import com.example.editor.gamification.GamificationBindingModel;
import com.example.editor.gamification.GamificationCheckReport;
import com.example.editor.gamification.GamificationChecker;
import com.example.editor.gamification.GamificationDefinitionModel;
import com.example.editor.gamification.GamificationEffectKind;
import com.example.editor.gamification.GamificationLedger;
import com.example.editor.gamification.GamificationRepeatPolicy;
import com.example.editor.gamification.GamificationRule;
import com.example.editor.gamification.GamificationRuntimeState;
import com.example.editor.gamification.GamificationProperties;
import com.example.editor.gamification.GamificationLoadResult;
import com.example.editor.gamification.PointTypeDefinition;
import com.example.editor.gamification.persistence.GamificationRuntimeRepository;
import com.example.editor.gamification.runtime.GamificationProcessingResult;
import com.example.editor.gamification.runtime.GamificationRuntimeService;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

/** Ejecutable independiente para comprobar los tres bloques sin depender de JUnit. */
public final class HydraEventFlowGamificationSmoke {
    private static int checks;
    public static void main(String[] args) {
        EventFlowModel flowModel = flowModel();
        EventFlowChecker flowChecker = new EventFlowChecker();
        EventFlowCheckReport flowReport = flowChecker.check(flowModel, null);
        check(flowReport.valid(), "The flow contract must be valid");
        check(flowReport.diagnostics().isEmpty(), "The valid flow has no diagnostics");
        ExecutableEventFlowPlan plan = new EventFlowCompiler(flowChecker).compile(flowModel, null);
        check(plan.flows().size() == 1, "One flow is compiled");
        check(plan.flowsByTriggerEventType().get("core.validation.violation-resolved").contains("flow:quality"), "The source event index is built");
        check(plan.flows().get(0).nodes().size() == 5, "Every node is indexed");
        check(plan.flows().get(0).nodes().get("node:map").outgoing().get(0).targetNodeId().equals("node:dedupe"), "Outgoing transitions preserve topology");

        InMemoryCanonicalEventBus bus = new InMemoryCanonicalEventBus();
        EventFlowProperties flowProperties = new EventFlowProperties();
        flowProperties.setMaximumExecutionDepth(16);
        flowProperties.setMaximumRuntimeStates(32);
        EventFlowRuntimeService flowRuntime = new EventFlowRuntimeService(flowService(flowModel, flowReport), eventService(),
                new EventFlowCompiler(flowChecker), bus, List.of(), flowProperties);
        CanonicalEvent source = event("event:resolved-1", "core.validation.violation-resolved", Map.of("diagnosticId", "diagnostic:1", "value", 7), Map.of());
        EventFlowProcessResult first = flowRuntime.process("metamodel", source);
        check(first.emittedEvents().size() == 1, "The flow emits one neutral derived event");
        check(first.emittedEvents().get(0).eventTypeId().equals("core.event-flow.derived-event"), "The emitted event type is neutral");
        check(first.emittedEvents().get(0).payload().containsKey("_hydraFlow"), "Flow provenance is included");
        check(first.traces().get(0).nodes().size() == 5, "Every flow step is traced");
        EventFlowProcessResult duplicate = flowRuntime.process("metamodel", source);
        check(duplicate.emittedEvents().isEmpty(), "Deduplication suppresses repeated evidence");
        check(flowRuntime.status("metamodel").stateEntries() > 0, "Stateful operators expose bounded state");

        GamificationDefinitionModel gamificationDefinitions = new GamificationDefinitionModel("1.0", "gamification-definitions", "metamodel", "Quality",
                List.of(new PointTypeDefinition("points:quality", "Quality", "", 0)), List.of(), List.of(), List.of(), List.of(), Map.of());
        GamificationRule rule = new GamificationRule("rule:resolved", "Verified resolution", "core.validation.violation-resolved", "", "EQUALS", null,
                "points:quality", 3, "", "", "", GamificationRepeatPolicy.ONCE_PER_EVENT, 0, 100, 20, GamificationEffectKind.AWARD, true);
        GamificationBindingModel gamificationBindings = new GamificationBindingModel("1.0", "gamification-bindings", "metamodel", "gamification-definitions", List.of(rule), Map.of());
        GamificationChecker gamificationChecker = new GamificationChecker();
        GamificationCheckReport gamificationReport = gamificationChecker.check(gamificationDefinitions, gamificationBindings, null);
        check(gamificationReport.valid(), "Verified evidence is accepted by gamification");
        GamificationRule unsafe = new GamificationRule("rule:unsafe", "Unsafe", "core.quickfix.applied", "", "EQUALS", null,
                "points:quality", 3, "", "", "", GamificationRepeatPolicy.ONCE_PER_EVENT, 0, 100, 20, GamificationEffectKind.AWARD, true);
        check(gamificationChecker.check(gamificationDefinitions,
                new GamificationBindingModel("1.0", "unsafe-bindings", "metamodel", "gamification-definitions", List.of(unsafe), Map.of()), null)
                .diagnostics().stream().anyMatch(item -> item.code().equals("UNVERIFIED_QUICK_FIX_REWARD")), "Quick-fix button presses cannot grant value");

        MemoryGamificationRepository gamificationRepository = new MemoryGamificationRepository();
        GamificationProperties gamificationProperties = new GamificationProperties();
        gamificationProperties.setMaximumLedgerEntries(100);
        GamificationRuntimeService gamificationRuntime = new GamificationRuntimeService(
                gamificationService(gamificationDefinitions, gamificationBindings, gamificationReport), gamificationRepository, gamificationProperties, bus);
        GamificationProcessingResult awarded = gamificationRuntime.process("metamodel", source);
        check(awarded.granted().size() == 1, "Verified evidence creates one ledger award");
        check(awarded.state().actors().get("actor:user").points().get("points:quality") == 3L, "The actor receives points");
        check(gamificationRepository.ledger.entries().size() == 1, "The award is persisted in the ledger");
        GamificationProcessingResult repeated = gamificationRuntime.process("metamodel", source);
        check(repeated.granted().isEmpty(), "The same event is idempotent");
        check(gamificationRepository.ledger.entries().size() == 1, "Idempotence avoids duplicate ledger entries");
        CanonicalEvent undo = event("event:undo-1", "core.quickfix.undone", Map.of("compensatesEventId", source.id()), Map.of());
        GamificationProcessingResult compensated = gamificationRuntime.process("metamodel", undo);
        check(compensated.compensated().size() == 1, "Undo creates a compensating ledger entry");
        check(compensated.state().actors().get("actor:user") == null || compensated.state().actors().get("actor:user").points().getOrDefault("points:quality", 0L) == 0L, "Compensation reverses points");
        check(gamificationRepository.ledger.entries().stream().anyMatch(entry -> !entry.compensationOf().isBlank() && entry.pointsDelta() == -3L), "The ledger preserves an auditable reversal");
        check(bus.subscriptionCount() == 0, "Direct smoke execution does not create hidden subscriptions");
        flowRuntime.start("metamodel");
        check(bus.subscriptionCount() == 1, "Event-flow start installs exactly one subscription");
        gamificationRuntime.subscribe();
        check(bus.subscriptionCount() == 2, "Gamification subscribe installs exactly one additional subscription");
        gamificationRuntime.close();
        flowRuntime.close();
        check(bus.subscriptionCount() == 0, "Runtime close releases every event subscription");
        check(!flowRuntime.status("metamodel").active(), "Event-flow runtime is inactive after close");
        System.out.println("HYDRA_NEXT_BLOCKS_SMOKE_OK " + checks + "/" + checks);
    }

    private static EventFlowModel flowModel() {
        FlowNode source = node("node:source", FlowNodeKind.SOURCE, "core.validation.violation-resolved", Map.of());
        FlowNode map = node("node:map", FlowNodeKind.MAP, "", Map.of("mappings", Map.of("payload.quality", Map.of("fixedValue", "verified"))));
        FlowNode dedupe = node("node:dedupe", FlowNodeKind.DEDUPLICATE, "", Map.of("keyPath", "payload.diagnosticId", "ttlMillis", 60000));
        FlowNode emit = node("node:emit", FlowNodeKind.EMIT, "core.event-flow.derived-event", Map.of());
        FlowNode end = node("node:end", FlowNodeKind.SUCCESS_END, "", Map.of());
        FlowDefinition flow = new FlowDefinition("flow:quality", "Quality", "", true, List.of("core.validation.violation-resolved"), List.of(),
                List.of(source, map, dedupe, emit, end), List.of(
                        transition("transition:1", source.id(), map.id()), transition("transition:2", map.id(), dedupe.id()),
                        transition("transition:3", dedupe.id(), emit.id()), transition("transition:4", emit.id(), end.id())), List.of(), Map.of());
        return new EventFlowModel("1.0", "event-flow-model", "metamodel", "", "", List.of(flow), Map.of());
    }
    private static FlowNode node(String id, FlowNodeKind kind, String eventType, Map<String, Object> configuration) {
        return new FlowNode(id, kind.name(), kind, "", eventType, "", configuration, RetryPolicy.none(), TimeoutPolicy.none(), false, true);
    }
    private static FlowTransition transition(String id, String source, String target) { return new FlowTransition(id, source, target, FlowTransitionKind.NEXT, "", 0, ""); }
    private static EventFlowApplicationService flowService(EventFlowModel model, EventFlowCheckReport report) {
        return new EventFlowApplicationService(null, null, null, null) {
            @Override public EventFlowLoadResult find(String metamodelId) { return new EventFlowLoadResult(model, "flow-etag", report); }
        };
    }
    private static EventModelApplicationService eventService() {
        EventDefinitionModel definitions = new EventDefinitionModel("1.0", "event-definitions", "metamodel", "hydra.smoke", List.of(), Map.of());
        EventBindingModel bindings = new EventBindingModel("1.0", "event-bindings", "metamodel", definitions.id(), "", List.of(), Map.of());
        EventModelCheckReport report = new EventModelCheckReport(definitions.id(), bindings.id(), List.of());
        return new EventModelApplicationService(null, null, null, null, null) {
            @Override public EventModelLoadResult find(String metamodelId) { return new EventModelLoadResult(definitions, "", bindings, "", report); }
        };
    }
    private static GamificationApplicationService gamificationService(GamificationDefinitionModel definitions,
            GamificationBindingModel bindings, GamificationCheckReport report) {
        return new GamificationApplicationService(null, null, null, null, null) {
            @Override public GamificationLoadResult find(String metamodelId) { return new GamificationLoadResult(definitions, "", bindings, "", report); }
        };
    }
    private static CanonicalEvent event(String id, String type, Map<String, Object> payload, Map<String, String> metadata) {
        return new CanonicalEvent("1.0", id, type, "1.0", Instant.now(), new EventSource("smoke", "validation", "", "1.0"),
                new EventContext(EventContextKind.VALIDATION, "", "", "metamodel", "model", "", "session"),
                new EventActor("actor:user", EventActorKind.USER, "User"),
                new EventSubject(EventSubjectKind.VALIDATION_DIAGNOSTIC, "model", "diagnostic:1", "", "", ""),
                "transaction:1", "correlation:1", "", payload, metadata);
    }
    private static void check(boolean condition, String message) { checks++; if(!condition) throw new IllegalStateException("Smoke check failed: " + message); }

    private static final class MemoryGamificationRepository implements GamificationRuntimeRepository {
        private GamificationRuntimeState state;
        private GamificationLedger ledger;
        private int revision;
        public Optional<StoredCanonicalDocument<GamificationRuntimeState>> findState(String metamodelId) { return Optional.ofNullable(state).map(value -> stored(value)); }
        public Optional<StoredCanonicalDocument<GamificationLedger>> findLedger(String metamodelId) { return Optional.ofNullable(ledger).map(value -> stored(value)); }
        public StoredCanonicalDocument<GamificationRuntimeState> saveState(GamificationRuntimeState value, String etag) { state = value; return stored(value); }
        public StoredCanonicalDocument<GamificationLedger> saveLedger(GamificationLedger value, String etag) { ledger = value; return stored(value); }
        public void deleteState(String metamodelId, String etag) { state = null; }
        public void deleteLedger(String metamodelId, String etag) { ledger = null; }
        public void reset(String metamodelId) { state = null; ledger = null; }
        private <T> StoredCanonicalDocument<T> stored(T value) { return new StoredCanonicalDocument<>(value, "etag-" + (++revision), Path.of("memory")); }
    }
}
