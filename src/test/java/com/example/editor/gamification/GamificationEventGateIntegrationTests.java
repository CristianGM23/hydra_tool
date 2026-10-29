package com.example.editor.gamification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
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
import com.example.editor.event.persistence.JsonEventBindingModelRepository;
import com.example.editor.event.persistence.JsonEventDefinitionModelRepository;
import com.example.editor.gamification.persistence.JsonGamificationBindingRepository;
import com.example.editor.gamification.persistence.JsonGamificationDefinitionRepository;
import com.example.editor.gamification.persistence.JsonGamificationRuntimeRepository;
import com.example.editor.gamification.runtime.GamificationRuntimeService;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real de la puerta de entrada de la gamificacion (x_event_flow_to_reward.gate): el runtime se
 * suscribe al bus real y solo puntua eventos con metamodelo, con interruptor no apagado y por debajo del
 * limite de profundidad. El oraculo es el libro JSON leido del disco.
 *
 * <p>Nota: el limite de profundidad de gamificacion se lee de la clave hydra.gamification.depth (no de la de
 * los enlaces de eventos). Los dos ultimos tests cubren la etapa x_event_flow_to_reward.disc: el editor
 * copia el interruptor de gamificacion del cuerpo del evento a los metadatos, que es lo que lee el runtime.
 */
class GamificationEventGateIntegrationTests {
    private static final String M = ExpressionTestFixtures.METAMODEL_ID;
    private static final String EVENT = "core.validation.violation-resolved";

    @TempDir Path temporary;
    private JsonMapper mapper;
    private InMemoryCanonicalEventBus bus;
    private Path ledgerFile;

    @BeforeEach void setUp() {
        mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodels = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodels.create(ExpressionTestFixtures.metamodel());
        EventModelProperties eventProperties = new EventModelProperties();
        eventProperties.setDirectory(temporary.resolve("events"));
        EventModelApplicationService events = new EventModelApplicationService(
                new JsonEventDefinitionModelRepository(eventProperties, mapper), new JsonEventBindingModelRepository(eventProperties, mapper),
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper), metamodels, new EventModelChecker());
        GamificationProperties properties = new GamificationProperties();
        properties.setDirectory(temporary.resolve("gamification"));
        GamificationApplicationService application = new GamificationApplicationService(
                new JsonGamificationDefinitionRepository(properties, mapper), new JsonGamificationBindingRepository(properties, mapper),
                metamodels, events, new GamificationChecker(), new GamificationCorruptDocumentRecovery(properties));
        application.create(M,
                new GamificationDefinitionModel("1.0", "gamification-definitions", M, "Gamification",
                        List.of(new PointTypeDefinition("points:quality", "Quality", "", 0L)), List.of(), List.of(), List.of(), List.of(), Map.of()),
                new GamificationBindingModel("1.0", "gamification-bindings", M, "gamification-definitions",
                        List.of(new GamificationRule("rule:a", "rule:a", EVENT, "", "EQUALS", null, "points:quality", 2, "", "", "",
                                GamificationRepeatPolicy.ONCE_PER_EVENT, 0, 100, 100, GamificationEffectKind.AWARD, true)), Map.of()));
        bus = new InMemoryCanonicalEventBus();
        GamificationRuntimeService runtime = new GamificationRuntimeService(application,
                new JsonGamificationRuntimeRepository(properties, mapper), properties, bus);
        runtime.subscribe();
        ledgerFile = temporary.resolve("gamification").resolve("library.gamification-ledger.json");
    }

    @Test @DisplayName("x_event_flow_to_reward.gate: sin metamodelo, en el limite de profundidad o apagado por metadatos no se puntua")
    void onlyAnEventWithMetamodelBelowTheDepthLimitAndNotSwitchedOffScores() throws Exception {
        bus.publish(event("event:no-metamodel", "", Map.of()));
        assertThat(ledgerFile).doesNotExist();
        bus.publish(event("event:depth-16", M, Map.of("hydra.gamification.depth", "16")));
        assertThat(ledgerFile).doesNotExist();
        bus.publish(event("event:depth-17", M, Map.of("hydra.gamification.depth", "17")));
        assertThat(ledgerFile).doesNotExist();
        bus.publish(event("event:switched-off", M, Map.of("hydra.gamificationEnabled", "false")));
        assertThat(ledgerFile).doesNotExist();

        bus.publish(event("event:depth-15", M, Map.of("hydra.gamification.depth", "15")));

        assertThat(ledgerFile).exists();
        List<?> entries = (List<?>) mapper.readValue(Files.readString(ledgerFile), Map.class).get("entries");
        assertThat(entries).hasSize(1);
        Map<?, ?> entry = (Map<?, ?>) entries.get(0);
        assertThat(entry.get("sourceEventId")).isEqualTo("event:depth-15");
        assertThat(entry.get("ruleId")).isEqualTo("rule:a");
        assertThat(((Number) entry.get("pointsDelta")).longValue()).isEqualTo(2L);
    }

    @Test @DisplayName("x_event_flow_to_reward.gate: un evento normal sin metadatos puntua y el conteo del libro es literal")
    void anOrdinaryEventScoresOnceAndTheLedgerCountIsLiteral() throws Exception {
        bus.publish(event("event:ordinary", M, Map.of()));
        bus.publish(event("event:no-metamodel-again", "", Map.of()));
        bus.publish(event("event:ordinary-two", M, Map.of("hydra.gamificationEnabled", "true")));

        List<?> entries = (List<?>) mapper.readValue(Files.readString(ledgerFile), Map.class).get("entries");
        List<String> sources = new ArrayList<>();
        long total = 0;
        for (Object item : entries) {
            Map<?, ?> entry = (Map<?, ?>) item;
            sources.add((String) entry.get("sourceEventId"));
            total += ((Number) entry.get("pointsDelta")).longValue();
        }
        assertThat(sources).containsExactly("event:ordinary", "event:ordinary-two");
        assertThat(total).isEqualTo(4L);
    }

    @Test @DisplayName("x_event_flow_to_reward.disc: el interruptor de gamificacion apagado en el editor impide puntuar")
    void anEditorEventWithGamificationSwitchedOffDoesNotScore() throws Exception {
        List<CanonicalEvent> seen = new ArrayList<>();
        bus.subscribe(event -> true, seen::add);

        new EditorSessionEventPublisher(bus).publish(EVENT,
                new EditorSessionEventContext("session:one", M, "model-one", "editor:one", 1L), "", "",
                Map.of("hydra.gamificationEnabled", false));

        CanonicalEvent published = seen.stream().filter(event -> event.id().startsWith("event:")
                && event.eventTypeId().equals(EVENT)).findFirst().orElseThrow();
        // El interruptor viaja en el cuerpo y tambien en los metadatos que consulta el runtime.
        assertThat(published.payload()).containsEntry("hydra.gamificationEnabled", false);
        assertThat(published.metadata()).containsEntry("hydra.gamificationEnabled", "false");
        // Con la gamificacion apagada en el editor el evento no puntua: no existe libro.
        assertThat(ledgerFile).doesNotExist();
    }

    @Test @DisplayName("Un evento del editor con la gamificacion encendida si puntua")
    void anEditorEventWithGamificationSwitchedOnScores() throws Exception {
        new EditorSessionEventPublisher(bus).publish(EVENT,
                new EditorSessionEventContext("session:one", M, "model-one", "editor:one", 1L), "", "",
                Map.of("hydra.gamificationEnabled", true));

        assertThat(ledgerFile).exists();
        List<?> entries = (List<?>) mapper.readValue(Files.readString(ledgerFile), Map.class).get("entries");
        assertThat(entries).hasSize(1);
    }

    private static CanonicalEvent event(String id, String metamodelId, Map<String, String> metadata) {
        return new CanonicalEvent("1.0", id, EVENT, "1.0", Instant.parse("2026-07-28T10:00:00Z"),
                new EventSource("test", "gamification", "", "1.0"),
                new EventContext(EventContextKind.GAMIFICATION, "", "", metamodelId, "model", "", "session:one"),
                new EventActor("actor:one", EventActorKind.USER, "Actor"), null, "transaction:" + id.replace(':', '-'),
                "correlation:one", "", Map.of(), metadata);
    }
}
