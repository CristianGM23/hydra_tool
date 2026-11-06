package com.example.editor.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.definition.runtime.session.EditorSessionEventContext;
import com.example.editor.definition.runtime.session.EditorSessionEventPublisher;
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
import com.example.editor.event.flow.runtime.EventFlowCompiler;
import com.example.editor.event.flow.runtime.EventFlowRuntimeService;
import com.example.editor.event.flow.runtime.FlowExecutionStatus;
import com.example.editor.event.flow.runtime.FlowExecutionTrace;
import com.example.editor.event.persistence.JsonEventBindingModelRepository;
import com.example.editor.event.persistence.JsonEventDefinitionModelRepository;
import com.example.editor.gamification.persistence.GamificationRuntimeRepository;
import com.example.editor.gamification.persistence.JsonGamificationBindingRepository;
import com.example.editor.gamification.persistence.JsonGamificationDefinitionRepository;
import com.example.editor.gamification.persistence.JsonGamificationRuntimeRepository;
import com.example.editor.gamification.runtime.GamificationProcessingResult;
import com.example.editor.gamification.runtime.GamificationRuntimeService;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real, sin mocks del comportamiento demostrado, de la gamificacion alimentada por el bus real:
 * gamification_award.gate (interruptor del sistema, de la sesion, evento sin metamodelo y profundidad 16),
 * gamification_award.per (el estado se guarda antes que el historial y, si el historial falla, el estado se
 * deshace), x_event_flow_to_reward.idem (un evento repetido no escribe nada), gamification_author_form.art
 * (el catalogo guardado en disco lo usa un motor nuevo) y events_publish_session.a6/a7 (el evento publicado
 * por la sesion del editor ejecuta el flujo y la gamificacion premia y penaliza). El oraculo es el JSON de
 * estado e historial leido del disco, con literales calculados a mano.
 */
class GamificationAwardGatePersistenceAndSessionListenersIntegrationTests {

    private static final String M = ExpressionTestFixtures.METAMODEL_ID;
    private static final String POINTS = "points:quality";
    private static final String SAVED = "core.model.saved";
    private static final String EXPORTED = "core.model.exported";

    private static final class Env {
        JsonMapper mapper;
        InMemoryCanonicalEventBus bus;
        GamificationProperties properties;
        GamificationApplicationService application;
        EventModelApplicationService events;
        CanonicalMetamodelApplicationService metamodels;
        StorageProperties storage;
        Path directory;
        Path temporary;
    }

    private static Env env(Path temporary, GamificationDefinitionModel definitions, List<GamificationRule> rules) {
        Env env = new Env();
        env.temporary = temporary;
        env.mapper = JsonMapper.builder().findAndAddModules().build();
        env.storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        env.metamodels = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(env.storage, new CanonicalMetamodelProperties(), env.mapper), new NeutralMetamodelValidator());
        env.metamodels.create(ExpressionTestFixtures.metamodel());
        EventModelProperties eventProperties = new EventModelProperties();
        eventProperties.setDirectory(temporary.resolve("events"));
        env.events = new EventModelApplicationService(new JsonEventDefinitionModelRepository(eventProperties, env.mapper),
                new JsonEventBindingModelRepository(eventProperties, env.mapper),
                new JsonExpressionModelRepository(env.storage, new ExpressionModelProperties(), env.mapper), env.metamodels, new EventModelChecker());
        env.properties = new GamificationProperties();
        env.directory = temporary.resolve("gamification");
        env.properties.setDirectory(env.directory);
        env.application = application(env);
        env.application.create(M, definitions, new GamificationBindingModel("1.0", "gamification-bindings", M, "gamification-definitions", rules, Map.of()));
        env.bus = new InMemoryCanonicalEventBus();
        return env;
    }

    private static GamificationApplicationService application(Env env) {
        return new GamificationApplicationService(new JsonGamificationDefinitionRepository(env.properties, env.mapper),
                new JsonGamificationBindingRepository(env.properties, env.mapper), env.metamodels, env.events, new GamificationChecker(),
                new GamificationCorruptDocumentRecovery(env.properties));
    }

    private static GamificationDefinitionModel plainDefinitions() {
        return new GamificationDefinitionModel("1.0", "gamification-definitions", M, "Gamification",
                List.of(new PointTypeDefinition(POINTS, "Quality", "", -20L)), List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    private static GamificationRule rule(String id, String eventTypeId, long points, GamificationEffectKind effect, String achievementId) {
        return new GamificationRule(id, id, eventTypeId, "", "EQUALS", null, POINTS, points, achievementId, "", "",
                GamificationRepeatPolicy.ONCE_PER_EVENT, 0, 100, 100, effect, true);
    }

    private static CanonicalEvent event(String id, String type, String metamodelId, Map<String, String> metadata) {
        return new CanonicalEvent("1.0", id, type, "1.0", Instant.parse("2026-07-28T10:00:00Z"), new EventSource("test", "gamification", "", "1.0"),
                new EventContext(EventContextKind.GAMIFICATION, "", "", metamodelId, "model", "", "session:one"),
                new EventActor("actor:one", EventActorKind.USER, "Actor"), null, "transaction:" + id.replace(':', '-'), "correlation:one", "",
                Map.of(), metadata);
    }

    private JsonNode json(Env env, String fileName) throws Exception {
        return env.mapper.readTree(Files.readString(env.directory.resolve(fileName)));
    }

    @Test
    @DisplayName("gamification_award.gate: apagado del sistema, apagado de la sesion, evento sin metamodelo y profundidad 16 no puntuan; un evento normal si")
    void theGateIgnoresSwitchedOffNoMetamodelAndTooDeeplyChainedEvents(@TempDir Path temporary) throws Exception {
        Env env = env(temporary, plainDefinitions(), List.of(rule("rule:award", SAVED, 2, GamificationEffectKind.AWARD, "")));
        GamificationRuntimeService runtime = new GamificationRuntimeService(env.application,
                new JsonGamificationRuntimeRepository(env.properties, env.mapper), env.properties, env.bus);
        runtime.subscribe();
        Path ledger = env.directory.resolve("library.gamification-ledger.json");

        env.properties.setEnabled(false);
        env.bus.publish(event("event:system-off", SAVED, M, Map.of()));
        assertThat(ledger).doesNotExist();
        env.properties.setEnabled(true);

        env.bus.publish(event("event:session-off", SAVED, M, Map.of("hydra.gamificationEnabled", "false")));
        env.bus.publish(event("event:no-metamodel", SAVED, "", Map.of()));
        assertThat(ledger).doesNotExist();

        // Profundidad: el motor devuelve un resultado vacio y no escribe nada con 16 encadenados.
        GamificationProcessingResult deep = runtime.process(M, event("event:deep", SAVED, M, Map.of("hydra.gamification.depth", "16")));
        assertThat(deep.granted()).isEmpty();
        assertThat(deep.rejected()).isEmpty();
        assertThat(deep.state().actors()).isEmpty();
        env.bus.publish(event("event:deep-bus", SAVED, M, Map.of("hydra.gamification.depth", "16")));
        assertThat(ledger).doesNotExist();

        env.bus.publish(event("event:ok", SAVED, M, Map.of("hydra.gamification.depth", "15")));
        JsonNode entries = json(env, "library.gamification-ledger.json").get("entries");
        assertThat(entries.size()).isEqualTo(1);
        assertThat(entries.get(0).get("sourceEventId").asString()).isEqualTo("event:ok");
        assertThat(entries.get(0).get("pointsDelta").asLong()).isEqualTo(2L);
    }

    @Test
    @DisplayName("x_event_flow_to_reward.idem: un evento repetido no concede otra vez ni reescribe estado ni historial")
    void aRepeatedEventDoesNotGrantTwiceNorRewriteTheDocuments(@TempDir Path temporary) throws Exception {
        Env env = env(temporary, plainDefinitions(), List.of(rule("rule:award", SAVED, 5, GamificationEffectKind.AWARD, "")));
        GamificationRuntimeService runtime = new GamificationRuntimeService(env.application,
                new JsonGamificationRuntimeRepository(env.properties, env.mapper), env.properties, env.bus);
        List<String> published = new ArrayList<>();
        env.bus.subscribe(candidate -> candidate.eventTypeId().startsWith("core.gamification."), candidate -> published.add(candidate.eventTypeId()));

        GamificationProcessingResult first = runtime.process(M, event("event:once", SAVED, M, Map.of()));
        assertThat(first.granted()).hasSize(1);
        byte[] ledgerBefore = Files.readAllBytes(env.directory.resolve("library.gamification-ledger.json"));
        byte[] stateBefore = Files.readAllBytes(env.directory.resolve("library.gamification-state.json"));

        GamificationProcessingResult repeated = runtime.process(M, event("event:once", SAVED, M, Map.of()));

        assertThat(repeated.granted()).isEmpty();
        assertThat(repeated.rejected()).isEmpty();
        assertThat(repeated.feedback()).isEmpty();
        assertThat(Files.readAllBytes(env.directory.resolve("library.gamification-ledger.json"))).isEqualTo(ledgerBefore);
        assertThat(Files.readAllBytes(env.directory.resolve("library.gamification-state.json"))).isEqualTo(stateBefore);
        assertThat(json(env, "library.gamification-ledger.json").get("entries").size()).isEqualTo(1);
        assertThat(json(env, "library.gamification-state.json").get("actors").get("actor:one").get("points").get(POINTS).asLong()).isEqualTo(5L);
        assertThat(published).containsExactly("core.gamification.award-granted");
    }

    @Test
    @DisplayName("gamification_award.per: el estado se guarda antes que el historial y, si el historial falla, el estado se deshace")
    void theStateIsSavedBeforeTheHistoryAndRolledBackWhenTheHistoryFails(@TempDir Path temporary) throws Exception {
        Env env = env(temporary, plainDefinitions(), List.of(rule("rule:award", SAVED, 5, GamificationEffectKind.AWARD, "")));
        RecordingRepository repository = new RecordingRepository(new JsonGamificationRuntimeRepository(env.properties, env.mapper));
        GamificationRuntimeService runtime = new GamificationRuntimeService(env.application, repository, env.properties, env.bus);

        runtime.process(M, event("event:p1", SAVED, M, Map.of()));

        assertThat(repository.calls).containsExactly("saveState", "saveLedger");
        JsonNode state = json(env, "library.gamification-state.json");
        assertThat(state.get("actors").get("actor:one").get("points").get(POINTS).asLong()).isEqualTo(5L);
        assertThat(state.get("processedAwardKeys").get(0).asString()).isEqualTo("event:p1|rule:award");
        assertThat(json(env, "library.gamification-ledger.json").get("entries").size()).isEqualTo(1);

        // El historial falla con un estado previo: el estado vuelve a los 5 puntos y no conoce el evento nuevo.
        repository.calls.clear();
        repository.failLedger = true;
        assertThatThrownBy(() -> runtime.process(M, event("event:p2", SAVED, M, Map.of())))
                .isInstanceOfSatisfying(GamificationApplicationException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo(GamificationApplicationException.Reason.STORAGE_ERROR));
        assertThat(repository.calls).containsExactly("saveState", "saveLedger", "saveState");
        state = json(env, "library.gamification-state.json");
        assertThat(state.get("actors").get("actor:one").get("points").get(POINTS).asLong()).isEqualTo(5L);
        assertThat(state.get("processedAwardKeys").size()).isEqualTo(1);
        assertThat(json(env, "library.gamification-ledger.json").get("entries").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("gamification_award.per: si el historial falla en el primer evento, el estado recien guardado se elimina")
    void aLedgerFailureOnTheFirstEventRemovesTheJustSavedState(@TempDir Path temporary) {
        Env env = env(temporary, plainDefinitions(), List.of(rule("rule:award", SAVED, 5, GamificationEffectKind.AWARD, "")));
        RecordingRepository repository = new RecordingRepository(new JsonGamificationRuntimeRepository(env.properties, env.mapper));
        repository.failLedger = true;
        GamificationRuntimeService runtime = new GamificationRuntimeService(env.application, repository, env.properties, env.bus);

        assertThatThrownBy(() -> runtime.process(M, event("event:first", SAVED, M, Map.of())))
                .isInstanceOfSatisfying(GamificationApplicationException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo(GamificationApplicationException.Reason.STORAGE_ERROR));

        assertThat(repository.calls).containsExactly("saveState", "saveLedger", "deleteState");
        assertThat(env.directory.resolve("library.gamification-state.json")).doesNotExist();
        assertThat(env.directory.resolve("library.gamification-ledger.json")).doesNotExist();
    }

    @Test
    @DisplayName("gamification_author_form.art: el catalogo guardado en disco lo usa un motor nuevo para conceder puntos y logro")
    void aSavedCatalogIsUsedByAFreshEngineToGrantPointsAndAnAchievement(@TempDir Path temporary) throws Exception {
        GamificationDefinitionModel definitions = new GamificationDefinitionModel("1.0", "gamification-definitions", M, "Gamification",
                List.of(new PointTypeDefinition(POINTS, "Quality", "", 0L)), List.of(),
                List.of(new AchievementDefinition("achievement:first", "First save", "", "", false)), List.of(), List.of(), Map.of());
        Env env = env(temporary, definitions, List.of(rule("rule:first", SAVED, 3, GamificationEffectKind.AWARD, "achievement:first")));

        JsonNode saved = json(env, "library.gamification-definitions.json");
        assertThat(saved.get("pointTypes").get(0).get("id").asString()).isEqualTo(POINTS);
        assertThat(saved.get("achievements").get(0).get("id").asString()).isEqualTo("achievement:first");
        assertThat(saved.get("achievements").get(0).get("name").asString()).isEqualTo("First save");
        JsonNode bindings = json(env, "library.gamification-bindings.json");
        assertThat(bindings.get("rules").get(0).get("achievementId").asString()).isEqualTo("achievement:first");
        assertThat(bindings.get("rules").get(0).get("points").asLong()).isEqualTo(3L);

        GamificationRuntimeService fresh = new GamificationRuntimeService(application(env),
                new JsonGamificationRuntimeRepository(env.properties, env.mapper), env.properties, env.bus);
        fresh.process(M, event("event:save-1", SAVED, M, Map.of()));

        JsonNode actor = json(env, "library.gamification-state.json").get("actors").get("actor:one");
        assertThat(actor.get("points").get(POINTS).asLong()).isEqualTo(3L);
        assertThat(actor.get("achievements").size()).isEqualTo(1);
        assertThat(actor.get("achievements").get(0).asString()).isEqualTo("achievement:first");
        JsonNode entry = json(env, "library.gamification-ledger.json").get("entries").get(0);
        assertThat(entry.get("ruleId").asString()).isEqualTo("rule:first");
        assertThat(entry.get("achievementId").asString()).isEqualTo("achievement:first");
    }

    @Test
    @DisplayName("events_publish_session.a6/a7: el evento publicado por la sesion ejecuta el flujo y la gamificacion premia y penaliza; un evento sin disparador no hace nada")
    void aSessionPublishedEventRunsTheFlowAndTheGamificationAwardsAndRetiresPoints(@TempDir Path temporary) throws Exception {
        Env env = env(temporary, plainDefinitions(), List.of(rule("rule:award", SAVED, 5, GamificationEffectKind.AWARD, ""),
                rule("rule:penalty", EXPORTED, 2, GamificationEffectKind.PENALTY, "")));
        EventFlowProperties flowProperties = new EventFlowProperties();
        flowProperties.setDirectory(temporary.resolve("flows"));
        EventFlowChecker checker = new EventFlowChecker();
        EventFlowApplicationService flowService = new EventFlowApplicationService(new JsonEventFlowModelRepository(flowProperties, env.mapper),
                env.metamodels, env.events, checker, new JsonExpressionModelRepository(env.storage, new ExpressionModelProperties(), env.mapper));
        FlowNode start = new FlowNode("node:start", "Start", FlowNodeKind.START, "", "", "", Map.of(), null, null, false, true);
        FlowNode emit = new FlowNode("node:emit", "Emit", FlowNodeKind.EMIT, "", EXPORTED, "", Map.of(), null, null, false, true);
        FlowNode end = new FlowNode("node:end", "End", FlowNodeKind.SUCCESS_END, "", "", "", Map.of(), null, null, false, true);
        FlowDefinition flow = new FlowDefinition("flow:on-save", "On save", "", true, List.of(SAVED), List.of(), List.of(start, emit, end),
                List.of(new FlowTransition("transition:start-emit", "node:start", "node:emit", FlowTransitionKind.NEXT, "", 0, ""),
                        new FlowTransition("transition:emit-end", "node:emit", "node:end", FlowTransitionKind.NEXT, "", 0, "")),
                List.of(), Map.of());
        flowService.create(M, new EventFlowModel(null, "flows-library", M, "", "", List.of(flow), Map.of()));
        EventFlowRuntimeService flows = new EventFlowRuntimeService(flowService, env.events, new EventFlowCompiler(checker), env.bus, List.of(), flowProperties);
        GamificationRuntimeService gamification = new GamificationRuntimeService(env.application,
                new JsonGamificationRuntimeRepository(env.properties, env.mapper), env.properties, env.bus);
        gamification.subscribe();
        List<CanonicalEvent> savedEvents = new ArrayList<>();
        env.bus.subscribe(candidate -> candidate.eventTypeId().equals(SAVED), savedEvents::add);
        EditorSessionEventPublisher publisher = new EditorSessionEventPublisher(env.bus);
        EditorSessionEventContext session = new EditorSessionEventContext("session:one", M, "model-one", "editor:one", 1L);

        publisher.publish(SAVED, session, "", "", Map.of());

        assertThat(savedEvents).hasSize(1);
        List<FlowExecutionTrace> traces = flows.traces(M);
        assertThat(traces).singleElement().satisfies(trace -> {
            assertThat(trace.flowId()).isEqualTo("flow:on-save");
            assertThat(trace.sourceEventId()).isEqualTo(savedEvents.get(0).id());
            assertThat(trace.status()).isEqualTo(FlowExecutionStatus.COMPLETED);
        });
        JsonNode entries = json(env, "library.gamification-ledger.json").get("entries");
        assertThat(entries.size()).isEqualTo(2);
        List<String> summary = new ArrayList<>();
        for(int index = 0; index < entries.size(); index++)
            summary.add(entries.get(index).get("ruleId").asString() + "=" + entries.get(index).get("pointsDelta").asLong());
        assertThat(summary).containsExactlyInAnyOrder("rule:award=5", "rule:penalty=-2");
        assertThat(json(env, "library.gamification-state.json").get("actors").get("session:one").get("points").get(POINTS).asLong()).isEqualTo(3L);

        // Negativo: un evento de sesion sin flujo ni regla asociada no cambia nada.
        publisher.publish("core.model.element-updated", session, "", "", Map.of());
        assertThat(flows.traces(M)).hasSize(1);
        assertThat(json(env, "library.gamification-ledger.json").get("entries").size()).isEqualTo(2);

        // Con la gamificacion apagada en el editor el flujo se ejecuta igual pero no hay puntos nuevos.
        publisher.publish(SAVED, session, "", "", Map.<String, Object>of("hydra.gamificationEnabled", false));
        assertThat(flows.traces(M)).hasSize(2);
        assertThat(json(env, "library.gamification-ledger.json").get("entries").size()).isEqualTo(2);
        assertThat(json(env, "library.gamification-state.json").get("actors").get("session:one").get("points").get(POINTS).asLong()).isEqualTo(3L);
    }

    private static final class RecordingRepository implements GamificationRuntimeRepository {
        private final GamificationRuntimeRepository delegate;
        private final List<String> calls = new ArrayList<>();
        private boolean failLedger;
        private RecordingRepository(GamificationRuntimeRepository delegate) { this.delegate = delegate; }
        @Override public Optional<StoredCanonicalDocument<GamificationRuntimeState>> findState(String id) { return delegate.findState(id); }
        @Override public Optional<StoredCanonicalDocument<GamificationLedger>> findLedger(String id) { return delegate.findLedger(id); }
        @Override public StoredCanonicalDocument<GamificationRuntimeState> saveState(GamificationRuntimeState state, String etag) {
            calls.add("saveState");
            return delegate.saveState(state, etag);
        }
        @Override public StoredCanonicalDocument<GamificationLedger> saveLedger(GamificationLedger ledger, String etag) {
            calls.add("saveLedger");
            if(failLedger) throw new IllegalStateException("simulated ledger write failure");
            return delegate.saveLedger(ledger, etag);
        }
        @Override public void deleteState(String id, String etag) { calls.add("deleteState"); delegate.deleteState(id, etag); }
        @Override public void deleteLedger(String id, String etag) { calls.add("deleteLedger"); delegate.deleteLedger(id, etag); }
        @Override public void reset(String id) { delegate.reset(id); }
    }
}
