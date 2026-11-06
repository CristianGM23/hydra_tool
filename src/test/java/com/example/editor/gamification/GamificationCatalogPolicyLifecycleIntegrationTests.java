package com.example.editor.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
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
import com.example.editor.event.persistence.JsonEventBindingModelRepository;
import com.example.editor.event.persistence.JsonEventDefinitionModelRepository;
import com.example.editor.gamification.diagram.GamificationDiagramDocument;
import com.example.editor.gamification.diagram.GamificationDiagramEdgeView;
import com.example.editor.gamification.diagram.GamificationDiagramNodeView;
import com.example.editor.gamification.diagram.GamificationDiagramPoint;
import com.example.editor.gamification.diagram.GamificationDiagramService;
import com.example.editor.gamification.diagram.JsonGamificationDiagramRepository;
import com.example.editor.gamification.feedback.UserFeedbackEnvelope;
import com.example.editor.gamification.feedback.UserFeedbackKind;
import com.example.editor.gamification.feedback.UserFeedbackSurface;
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
 * Evidencia real (servicio de aplicacion, repositorios JSON y diagrama en @TempDir, runtime real) del
 * ciclo de autoria del catalogo con politica de avisos. Etapas: gamification_author_form.lv/ck,
 * gamification_author_guided.c/sv, gamification_author_policy.sf/mx/dt/sv/art.
 */
class GamificationCatalogPolicyLifecycleIntegrationTests {
    private static final String M = ExpressionTestFixtures.METAMODEL_ID;
    private static final String EVENT = "core.validation.violation-resolved";

    @TempDir Path temporary;
    private JsonMapper mapper;
    private GamificationProperties properties;
    private GamificationApplicationService service;
    private GamificationDiagramService diagrams;
    private GamificationRuntimeService runtime;

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
        properties = new GamificationProperties();
        properties.setDirectory(temporary.resolve("gamification"));
        service = new GamificationApplicationService(new JsonGamificationDefinitionRepository(properties, mapper),
                new JsonGamificationBindingRepository(properties, mapper), metamodels, events, new GamificationChecker(),
                new GamificationCorruptDocumentRecovery(properties));
        diagrams = new GamificationDiagramService(new JsonGamificationDiagramRepository(properties, mapper), service);
        runtime = new GamificationRuntimeService(service, new JsonGamificationRuntimeRepository(properties, mapper), properties,
                new InMemoryCanonicalEventBus());
    }

    @Test void catalogWithPolicyAnnotationsAndDiagramIsSavedToDiskAndReadBack() throws Exception {
        service.create(M, definitions(policy(), List.of()), bindings(Map.of("hydra.feedback.rule.rule:a.showPoints", "false"), goodRule("rule:a")));
        diagrams.save(M, diagram(), null);

        Path directory = temporary.resolve("gamification");
        Map<?, ?> definitionsOnDisk = mapper.readValue(Files.readString(directory.resolve("library.gamification-definitions.json")), Map.class);
        Map<?, ?> annotations = (Map<?, ?>) definitionsOnDisk.get("annotations");
        assertThat(annotations.get("hydra.feedback.surfaces")).isEqualTo("toast,status");
        assertThat(annotations.get("hydra.feedback.durationMillis")).isEqualTo("2500");
        assertThat(annotations.get("hydra.feedback.maxVisible")).isEqualTo("7");
        assertThat(annotations.get("hydra.feedback.dedupeWindowSeconds")).isEqualTo("15");
        assertThat(annotations.get("hydra.feedback.aggregateWindowMillis")).isEqualTo("1200");
        assertThat(annotations.get("hydra.feedback.showPoints")).isEqualTo("false");
        Map<?, ?> bindingsOnDisk = mapper.readValue(Files.readString(directory.resolve("library.gamification-bindings.json")), Map.class);
        assertThat(((Map<?, ?>) bindingsOnDisk.get("annotations")).get("hydra.feedback.rule.rule:a.showPoints")).isEqualTo("false");
        Map<?, ?> diagramOnDisk = mapper.readValue(Files.readString(directory.resolve("library.gamification-diagram.json")), Map.class);
        List<?> nodes = (List<?>) diagramOnDisk.get("nodes");
        assertThat(nodes).hasSize(1);
        assertThat(((Map<?, ?>) nodes.get(0)).get("nodeId")).isEqualTo("rule:a");
        assertThat(((Number) ((Map<?, ?>) nodes.get(0)).get("x")).doubleValue()).isEqualTo(10.0);

        GamificationLoadResult reread = service.find(M);
        assertThat(reread.definitions().annotations()).containsEntry("hydra.feedback.maxVisible", "7");
        assertThat(diagrams.find(M).diagram().nodes()).extracting(GamificationDiagramNodeView::nodeId).containsExactly("rule:a");
    }

    @Test void invalidLevelsDuplicatesAndLimitsAreRejectedWithoutPersistingAnything() {
        GamificationRule daily = new GamificationRule("rule:daily", "rule:daily", EVENT, "", "EQUALS", null, "points:quality", 2, "", "", "",
                GamificationRepeatPolicy.DAILY_LIMIT, 0, 0, 0, GamificationEffectKind.AWARD, true);
        GamificationDefinitionModel unknownLevelType = definitions(Map.of(),
                List.of(new LevelDefinition("level:one", "One", "points:missing", 10, "")));
        GamificationDefinitionModel duplicatePointTypes = new GamificationDefinitionModel("1.0", "gamification-definitions", M, "G",
                List.of(new PointTypeDefinition("points:quality", "Q", "", 0L), new PointTypeDefinition("points:quality", "Q2", "", 0L)),
                List.of(), List.of(), List.of(), List.of(), Map.of());

        assertRejected(unknownLevelType, bindings(Map.of(), goodRule("rule:a")));
        assertRejected(duplicatePointTypes, bindings(Map.of(), goodRule("rule:a")));
        assertRejected(definitions(Map.of(), List.of()), bindings(Map.of(), daily));
        assertThat(Files.exists(temporary.resolve("gamification").resolve("library.gamification-definitions.json"))).isFalse();
        assertThat(Files.exists(temporary.resolve("gamification").resolve("library.gamification-bindings.json"))).isFalse();
    }

    @Test void checkDraftReturnsBlockingErrorsAndNonBlockingWarnings() {
        GamificationRule unbounded = new GamificationRule("rule:unbounded", "rule:unbounded", EVENT, "", "EQUALS", null, "points:quality", 2, "", "", "",
                GamificationRepeatPolicy.UNLIMITED, 0, 0, 0, GamificationEffectKind.AWARD, true);
        GamificationCheckReport warnings = service.checkDraft(M, definitions(Map.of(), List.of()), bindings(Map.of(), unbounded));
        assertThat(warnings.valid()).isTrue();
        assertThat(warnings.diagnostics()).extracting(GamificationDiagnostic::code).containsExactly("UNBOUNDED_RULE");
        assertThat(warnings.diagnostics().get(0).severity()).isEqualTo(GamificationDiagnosticSeverity.WARNING);

        GamificationCheckReport errors = service.checkDraft(M,
                definitions(Map.of(), List.of(new LevelDefinition("level:one", "One", "points:missing", 10, ""))), bindings(Map.of(), unbounded));
        assertThat(errors.valid()).isFalse();
        assertThat(errors.diagnostics()).extracting(GamificationDiagnostic::code).containsExactlyInAnyOrder("UNKNOWN_LEVEL_POINT_TYPE", "UNBOUNDED_RULE");
        assertThat(errors.diagnostics()).filteredOn(item -> item.severity() == GamificationDiagnosticSeverity.ERROR)
                .extracting(GamificationDiagnostic::elementId).containsExactly("level:one");
    }

    @Test void savedPolicyGovernsTheFeedbackOfARealGrant() {
        service.create(M, definitions(policy(), List.of()), bindings(Map.of("hydra.feedback.rule.rule:a.surfaces", "dialog"), goodRule("rule:a")));

        runtime.process(M, new CanonicalEvent("1.0", "event:1", EVENT, "1.0", Instant.parse("2026-07-28T10:00:00Z"),
                new EventSource("test", "gamification", "", "1.0"),
                new EventContext(EventContextKind.GAMIFICATION, "", "", M, "model", "", "session:one"),
                new EventActor("actor:one", EventActorKind.USER, "Actor"), null, "transaction:one", "correlation:one", "", Map.of(), Map.of()));

        List<UserFeedbackEnvelope> feedback = runtime.feedback(M, "actor:one", 10);
        assertThat(feedback).hasSize(1);
        UserFeedbackEnvelope notice = feedback.get(0);
        assertThat(notice.kind()).isEqualTo(UserFeedbackKind.POINTS_AWARDED);
        assertThat(notice.pointsDelta()).isEqualTo(2L);
        assertThat(notice.presentation().surfaces()).containsExactly(UserFeedbackSurface.DIALOG);
        assertThat(notice.presentation().durationMillis()).isEqualTo(2500);
        assertThat(notice.presentation().maxVisible()).isEqualTo(7);
        assertThat(notice.presentation().dedupeWindowSeconds()).isEqualTo(15);
        assertThat(notice.presentation().aggregateWindowMillis()).isEqualTo(1200);
        assertThat(notice.presentation().showPoints()).isFalse();
    }

    private void assertRejected(GamificationDefinitionModel definitions, GamificationBindingModel bindings) {
        assertThatThrownBy(() -> service.create(M, definitions, bindings)).isInstanceOf(GamificationApplicationException.class)
                .extracting(exception -> ((GamificationApplicationException) exception).getReason())
                .isEqualTo(GamificationApplicationException.Reason.INVALID_REQUEST);
    }

    private static Map<String, String> policy() {
        Map<String, String> annotations = new LinkedHashMap<>();
        annotations.put("hydra.feedback.surfaces", "toast,status");
        annotations.put("hydra.feedback.durationMillis", "2500");
        annotations.put("hydra.feedback.maxVisible", "7");
        annotations.put("hydra.feedback.dedupeWindowSeconds", "15");
        annotations.put("hydra.feedback.aggregateWindowMillis", "1200");
        annotations.put("hydra.feedback.showPoints", "false");
        return annotations;
    }

    private static GamificationDefinitionModel definitions(Map<String, String> annotations, List<LevelDefinition> levels) {
        return new GamificationDefinitionModel("1.0", "gamification-definitions", M, "Gamification",
                List.of(new PointTypeDefinition("points:quality", "Quality", "", 0L)), levels, List.of(), List.of(), List.of(), annotations);
    }

    private static GamificationBindingModel bindings(Map<String, String> annotations, GamificationRule rule) {
        return new GamificationBindingModel("1.0", "gamification-bindings", M, "gamification-definitions", List.of(rule), annotations);
    }

    private static GamificationRule goodRule(String id) {
        return new GamificationRule(id, id, EVENT, "", "EQUALS", null, "points:quality", 2, "", "", "",
                GamificationRepeatPolicy.ONCE_PER_EVENT, 0, 100, 100, GamificationEffectKind.AWARD, true);
    }

    private static GamificationDiagramDocument diagram() {
        return new GamificationDiagramDocument("1.0", "library-gamification-diagram", M, "gamification-definitions", "gamification-bindings",
                List.of(new GamificationDiagramNodeView("rule:a", 10, 20, 210, 84, false)),
                List.of(new GamificationDiagramEdgeView("rule-points:quality", List.of(new GamificationDiagramPoint(30, 40)))));
    }
}
