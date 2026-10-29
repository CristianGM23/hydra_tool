package com.example.editor.gamification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.definition.EditorCapability;
import com.example.editor.definition.EditorRuntimePolicy;
import com.example.editor.definition.runtime.session.EditorElementUpdateRequest;
import com.example.editor.definition.runtime.session.EditorModelSessionService;
import com.example.editor.definition.runtime.session.EditorNodeCreationRequest;
import com.example.editor.definition.runtime.session.EditorSessionCreateRequest;
import com.example.editor.definition.runtime.session.EditorSessionEventPublisher;
import com.example.editor.definition.runtime.session.EditorSessionPendingEventBuffer;
import com.example.editor.definition.runtime.session.EditorSessionProperties;
import com.example.editor.definition.runtime.session.EditorSessionRealStackFixtures;
import com.example.editor.definition.runtime.session.EditorSessionView;
import com.example.editor.definition.runtime.session.persistence.JsonEditorModelSessionRepository;
import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.EventContextKind;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.EventModelChecker;
import com.example.editor.event.EventModelProperties;
import com.example.editor.event.bus.InMemoryCanonicalEventBus;
import com.example.editor.event.persistence.JsonEventBindingModelRepository;
import com.example.editor.event.persistence.JsonEventDefinitionModelRepository;
import com.example.editor.gamification.feedback.UserFeedbackEnvelope;
import com.example.editor.gamification.feedback.UserFeedbackKind;
import com.example.editor.gamification.persistence.JsonGamificationBindingRepository;
import com.example.editor.gamification.persistence.JsonGamificationDefinitionRepository;
import com.example.editor.gamification.persistence.JsonGamificationRuntimeRepository;
import com.example.editor.gamification.runtime.GamificationRuntimeService;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real, sin mocks de comportamiento, del camino de una accion del editor a una
 * recompensa: sesion real (sin autoguardado) -> publicador real -> bus real -> runtime de
 * gamificacion real con repositorios JSON en disco. Etapas: x_event_flow_to_reward.build/sv/bus/
 * match/act/pub/fb.
 */
class EditorSessionEventToRewardIntegrationTests {
    private static final String M = EditorSessionRealStackFixtures.METAMODEL;
    private static final Set<EditorCapability> CAPABILITIES = Set.of(EditorCapability.MODEL_EDITING, EditorCapability.UNDO_REDO);

    @TempDir Path temporary;
    private JsonMapper mapper;
    private CanonicalMetamodelApplicationService metamodels;
    private InMemoryCanonicalEventBus bus;
    private EditorSessionProperties sessionProperties;
    private GamificationRuntimeService runtime;
    private final List<CanonicalEvent> seen = new ArrayList<>();

    @BeforeEach void setUp() {
        mapper = JsonMapper.builder().findAndAddModules().build();
        metamodels = EditorSessionRealStackFixtures.metamodels(temporary, mapper);
        bus = new InMemoryCanonicalEventBus();
        bus.subscribe(event -> true, seen::add);
        sessionProperties = new EditorSessionProperties();
        sessionProperties.setDirectory(temporary.resolve("sessions"));
        sessionProperties.setPendingEventSpoolDirectory(temporary.resolve("spool"));

        EventModelProperties eventProperties = new EventModelProperties();
        eventProperties.setDirectory(temporary.resolve("events"));
        EventModelApplicationService events = new EventModelApplicationService(
                new JsonEventDefinitionModelRepository(eventProperties, mapper), new JsonEventBindingModelRepository(eventProperties, mapper),
                new JsonExpressionModelRepository(EditorSessionRealStackFixtures.storage(temporary), new ExpressionModelProperties(), mapper),
                metamodels, new EventModelChecker());
        GamificationProperties properties = new GamificationProperties();
        properties.setDirectory(temporary.resolve("gamification"));
        GamificationApplicationService application = new GamificationApplicationService(new JsonGamificationDefinitionRepository(properties, mapper),
                new JsonGamificationBindingRepository(properties, mapper), metamodels, events, new GamificationChecker(),
                new GamificationCorruptDocumentRecovery(properties));
        GamificationRule rule = new GamificationRule("rule:created", "Element created", "core.model.element-created", "", "EQUALS", null,
                "points:quality", 5, "", "", "", GamificationRepeatPolicy.ONCE_PER_EVENT, 0, 2, 100, GamificationEffectKind.AWARD, true);
        application.create(M,
                new GamificationDefinitionModel("1.0", "gamification-definitions", M, "Gamification",
                        List.of(new PointTypeDefinition("points:quality", "Quality", "", 0L)), List.of(), List.of(), List.of(), List.of(),
                        Map.of("hydra.feedback.includeRejected", "true")),
                new GamificationBindingModel("1.0", "gamification-bindings", M, "gamification-definitions", List.of(rule), Map.of()));
        runtime = new GamificationRuntimeService(application, new JsonGamificationRuntimeRepository(properties, mapper), properties, bus);
        runtime.subscribe();
    }

    @Test void savingTheSessionReleasesTheHeldEventsAndTheRuntimeGrantsAndRejectsPerTheRule() throws IOException {
        EditorModelSessionService service = service("editor-manual", false, true);
        EditorSessionView view = service.create(M, "editor-manual", new EditorSessionCreateRequest("session-reward", "model-reward", "People", "representation:main", "", Map.of()));
        view = create(service, view);
        String firstPersonId = view.nodes().get(0).elementId();
        view = create(service, view);
        view = service.updateElement(M, "editor-manual", "session-reward", firstPersonId,
                new EditorElementUpdateRequest("representation:main", "form:person", Map.of(EditorSessionRealStackFixtures.NAME, "Ada")), view.etag());
        view = create(service, view);

        // sv: sin guardar no hay ni un solo apunte de gamificacion en disco.
        Path ledgerFile = temporary.resolve("gamification").resolve("people.gamification-ledger.json");
        // (core.model.saved, publicado al crear la sesion, ya crea un libro vacio: se comprueba que no tiene apuntes.)
        if (Files.exists(ledgerFile)) {
            assertThat((List<?>) mapper.readValue(Files.readString(ledgerFile), Map.class).get("entries")).isEmpty();
        }

        service.save(M, "editor-manual", "session-reward", view.etag());

        // build: el evento de elemento creado lleva el contexto de edicion y la correlacion de la sesion.
        List<CanonicalEvent> created = seen.stream().filter(event -> event.eventTypeId().equals("core.model.element-created")).toList();
        assertThat(created).hasSize(3);
        assertThat(created.get(0).context().kind()).isEqualTo(EventContextKind.MODEL_EDITING);
        assertThat(created.get(0).context().sessionId()).isEqualTo("session-reward");
        assertThat(created.get(0).correlationId()).isEqualTo("correlation:session-reward");
        assertThat(created.get(0).subject().elementId()).isEqualTo(firstPersonId);

        // bus + match + act: se apuntan 3 entradas (solo el tipo de evento con regla); la tercera se rechaza.
        assertThat(Files.isRegularFile(ledgerFile)).isTrue();
        Map<?, ?> ledger = mapper.readValue(Files.readString(ledgerFile), Map.class);
        List<?> entries = (List<?>) ledger.get("entries");
        assertThat(entries).hasSize(3);
        assertThat(entries).extracting(entry -> ((Number) ((Map<?, ?>) entry).get("pointsDelta")).longValue()).containsExactly(5L, 5L, 0L);
        assertThat(entries).extracting(entry -> String.valueOf(((Map<?, ?>) entry).get("status"))).containsExactly("ACTIVE", "ACTIVE", "REJECTED");
        assertThat(((Map<?, ?>) ((Map<?, ?>) entries.get(2)).get("metadata")).get("reason")).isEqualTo("Maximum applications per actor reached");
        CanonicalEvent updated = seen.stream().filter(event -> event.eventTypeId().equals("core.model.element-updated")).findFirst().orElseThrow();
        assertThat(entries).extracting(entry -> String.valueOf(((Map<?, ?>) entry).get("sourceEventId"))).doesNotContain(updated.id());
        assertThat(entries).extracting(entry -> String.valueOf(((Map<?, ?>) entry).get("sourceEventId")))
                .containsExactly(created.get(0).id(), created.get(1).id(), created.get(2).id());

        Path stateFile = temporary.resolve("gamification").resolve("people.gamification-state.json");
        Map<?, ?> state = mapper.readValue(Files.readString(stateFile), Map.class);
        Map<?, ?> actor = (Map<?, ?>) ((Map<?, ?>) state.get("actors")).get("session-reward");
        assertThat(((Number) ((Map<?, ?>) actor.get("points")).get("points:quality")).longValue()).isEqualTo(10L);

        // pub: el resultado se anuncia como eventos nuevos en el bus.
        assertThat(seen).filteredOn(event -> event.eventTypeId().equals("core.gamification.award-granted")).hasSize(2);
        assertThat(seen).filteredOn(event -> event.eventTypeId().equals("core.gamification.award-rejected")).hasSize(1);

        // fb: el aviso para la persona refleja lo ganado y lo rechazado.
        List<UserFeedbackEnvelope> feedback = runtime.feedback(M, "session-reward", 50);
        assertThat(feedback).filteredOn(item -> item.kind() == UserFeedbackKind.POINTS_AWARDED).hasSize(2);
        assertThat(feedback).filteredOn(item -> item.kind() == UserFeedbackKind.APPLICATION_REJECTED).hasSize(1)
                .allSatisfy(item -> assertThat(item.reason()).isEqualTo("Maximum applications per actor reached"));
        assertThat(feedback).filteredOn(item -> item.kind() == UserFeedbackKind.POINTS_AWARDED).allSatisfy(item -> assertThat(item.pointsDelta()).isEqualTo(5L));

        // Republicar el mismo evento (mismo id) no duplica la concesion.
        bus.publish(created.get(0));
        Map<?, ?> afterReplay = mapper.readValue(Files.readString(ledgerFile), Map.class);
        assertThat((List<?>) afterReplay.get("entries")).hasSize(3);
    }

    @Test void withEventPublicationDisabledNothingReachesTheBusOrTheLedger() {
        EditorModelSessionService service = service("editor-silent", true, false);
        EditorSessionView view = service.create(M, "editor-silent", new EditorSessionCreateRequest("session-silent", "model-silent", "People", "representation:main", "", Map.of()));
        create(service, view);

        assertThat(seen).isEmpty();
        assertThat(Files.exists(temporary.resolve("gamification").resolve("people.gamification-ledger.json"))).isFalse();
    }

    private EditorSessionView create(EditorModelSessionService service, EditorSessionView view) {
        String editorId = view.session().editorId();
        return service.createNode(M, editorId, view.session().id(), "tool:create-person",
                new EditorNodeCreationRequest("representation:main", "", "", Map.of()), view.etag());
    }

    private EditorModelSessionService service(String editorId, boolean autosave, boolean publishEvents) {
        EditorRuntimePolicy policy = new EditorRuntimePolicy(Set.of(), autosave, true, publishEvents, false, true, 2, 30_000L);
        return new EditorModelSessionService(
                EditorSessionRealStackFixtures.runtimes(EditorSessionRealStackFixtures.editor(editorId, policy, CAPABILITIES)),
                metamodels, new JsonEditorModelSessionRepository(sessionProperties, mapper), null, sessionProperties, null, null, null, null,
                new EditorSessionEventPublisher(bus), null, new EditorSessionPendingEventBuffer(sessionProperties, mapper), null, null, null);
    }
}
