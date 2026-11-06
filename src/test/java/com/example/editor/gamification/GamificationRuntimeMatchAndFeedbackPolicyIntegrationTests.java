package com.example.editor.gamification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.example.editor.gamification.feedback.UserFeedbackEnvelope;
import com.example.editor.gamification.feedback.UserFeedbackKind;
import com.example.editor.gamification.feedback.UserFeedbackSurface;
import com.example.editor.gamification.persistence.JsonGamificationBindingRepository;
import com.example.editor.gamification.persistence.JsonGamificationDefinitionRepository;
import com.example.editor.gamification.persistence.JsonGamificationRuntimeRepository;
import com.example.editor.gamification.runtime.GamificationProcessingResult;
import com.example.editor.gamification.runtime.GamificationRuntimeService;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real (catalogo y runtime en disco, motor neutral de expresiones real, sin mocks) de
 * gamification_award.match (solo puntuan las reglas activas cuyo tipo de evento y condicion encajan)
 * y de gamification_define.pol (la politica de avisos guardada en las anotaciones gobierna lo que
 * muestra el runtime).
 */
class GamificationRuntimeMatchAndFeedbackPolicyIntegrationTests {
    private static final String M = ExpressionTestFixtures.METAMODEL_ID;
    private static final String RESOLVED = "core.validation.violation-resolved";
    private static final String CREATED = "core.model.element-created";
    private static final String UPDATED = "core.model.element-updated";

    @TempDir Path temporary;
    private JsonMapper mapper;
    private GamificationApplicationService application;
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
        GamificationProperties properties = new GamificationProperties();
        properties.setDirectory(temporary.resolve("gamification"));
        application = new GamificationApplicationService(new JsonGamificationDefinitionRepository(properties, mapper),
                new JsonGamificationBindingRepository(properties, mapper), metamodels, events, new GamificationChecker(),
                new GamificationCorruptDocumentRecovery(properties));
        runtime = new GamificationRuntimeService(application, new JsonGamificationRuntimeRepository(properties, mapper), properties,
                new InMemoryCanonicalEventBus(), metamodels, new ExpressionEvaluator(new ExpressionTypeChecker(new ExpressionDependencyAnalyzer())));
    }

    @Test void onlyActiveRulesWhoseEventTypeAndConditionMatchScoreExactPoints() throws Exception {
        application.create(M, definitions(Map.of()), bindings(Map.of(), List.of(
                rule("rule:equals", RESOLVED, "kind", "EQUALS", "syntax", 3, true),
                rule("rule:greater", RESOLVED, "score", "GREATER_THAN", 10, 5, true),
                rule("rule:exists", RESOLVED, "evidence", "EXISTS", null, 7, true),
                rule("rule:empty", CREATED, "", "EQUALS", null, 1, true),
                rule("rule:inactive", RESOLVED, "", "EQUALS", null, 100, false),
                rule("rule:other-type", UPDATED, "", "EQUALS", null, 1000, true))));

        GamificationProcessingResult first = process("event:1", RESOLVED, 1, payload("kind", "syntax", "score", 4, "evidence", "y"));
        assertThat(first.granted()).extracting(GamificationLedgerEntry::ruleId).containsExactlyInAnyOrder("rule:equals", "rule:exists");
        GamificationProcessingResult second = process("event:2", RESOLVED, 2, payload("kind", "semantic", "score", 12, "evidence", "x"));
        assertThat(second.granted()).extracting(GamificationLedgerEntry::ruleId).containsExactlyInAnyOrder("rule:greater", "rule:exists");
        GamificationProcessingResult third = process("event:3", CREATED, 3, Map.of());
        assertThat(third.granted()).extracting(GamificationLedgerEntry::ruleId).containsExactly("rule:empty");
        GamificationProcessingResult fourth = process("event:4", RESOLVED, 4, payload("kind", "syntax", "score", 11, "evidence", true));
        assertThat(fourth.granted()).extracting(GamificationLedgerEntry::ruleId).containsExactlyInAnyOrder("rule:equals", "rule:greater", "rule:exists");
        assertThat(fourth.rejected()).isEmpty();

        // (3 + 7) + (5 + 7) + 1 + (3 + 5 + 7) = 38, calculado a mano. El evaluador exige que la variable
        // de una condicion EXISTS este presente, asi que todos los eventos RESOLVED llevan "evidence".
        assertThat(fourth.state().actors().get("actor:one").points()).containsEntry("points:quality", 38L);

        // Oraculo independiente: el libro persistido se lee del disco.
        Path ledgerFile = temporary.resolve("gamification").resolve("library.gamification-ledger.json");
        Map<?, ?> ledger = mapper.readValue(Files.readString(ledgerFile), Map.class);
        List<?> entries = (List<?>) ledger.get("entries");
        assertThat(entries).hasSize(8);
        long sum = 0;
        for (Object entry : entries) {
            Map<?, ?> item = (Map<?, ?>) entry;
            assertThat(item.get("ruleId")).isNotIn("rule:inactive", "rule:other-type");
            sum += ((Number) item.get("pointsDelta")).longValue();
        }
        assertThat(sum).isEqualTo(38L);
    }

    @Test void savedFeedbackPolicyShowsHidesAndBoundsTheNotices() throws Exception {
        Map<String, String> global = new LinkedHashMap<>();
        global.put("hydra.feedback.surfaces", "toast,status");
        global.put("hydra.feedback.durationMillis", "99999");
        global.put("hydra.feedback.maxVisible", "50");
        global.put("hydra.feedback.showPoints", "false");
        global.put("hydra.feedback.dedupeWindowSeconds", "10");
        global.put("hydra.feedback.aggregateWindowMillis", "2000");
        global.put("hydra.feedback.includeRejected", "true");
        Map<String, String> perRule = new LinkedHashMap<>();
        perRule.put("hydra.feedback.rule.rule:b.enabled", "false");
        perRule.put("hydra.feedback.rule.rule:c.surfaces", "dialog");
        perRule.put("hydra.feedback.rule.rule:c.durationMillis", "5");
        List<GamificationRule> rules = List.of(rule("rule:a", RESOLVED, "", "EQUALS", null, 2, true),
                rule("rule:b", RESOLVED, "", "EQUALS", null, 3, true), rule("rule:c", RESOLVED, "", "EQUALS", null, 4, true),
                new GamificationRule("rule:lim", "rule:lim", RESOLVED, "", "EQUALS", null, "points:quality", 1, "", "", "",
                        GamificationRepeatPolicy.ONCE_PER_EVENT, 0, 1, 100, GamificationEffectKind.AWARD, true));
        GamificationSaveResponse saved = application.create(M, definitions(global), bindings(perRule, rules));

        process("event:1", RESOLVED, 1, Map.of());
        process("event:2", RESOLVED, 2, Map.of());

        List<UserFeedbackEnvelope> feedback = runtime.feedback(M, "actor:one", 50);
        assertThat(feedback).filteredOn(item -> item.metadata().get("ruleId").equals("rule:b")).isEmpty();
        assertThat(feedback).filteredOn(item -> item.kind() == UserFeedbackKind.POINTS_AWARDED).hasSize(5);
        UserFeedbackEnvelope a = only(feedback, "rule:a", UserFeedbackKind.POINTS_AWARDED, "event:1");
        assertThat(a.presentation().surfaces()).containsExactlyInAnyOrder(UserFeedbackSurface.TOAST, UserFeedbackSurface.STATUS);
        assertThat(a.presentation().durationMillis()).isEqualTo(30000);
        assertThat(a.presentation().maxVisible()).isEqualTo(20);
        assertThat(a.presentation().showPoints()).isFalse();
        assertThat(a.presentation().dedupeWindowSeconds()).isEqualTo(10);
        assertThat(a.presentation().aggregateWindowMillis()).isEqualTo(2000);
        UserFeedbackEnvelope c = only(feedback, "rule:c", UserFeedbackKind.POINTS_AWARDED, "event:1");
        assertThat(c.presentation().surfaces()).containsExactly(UserFeedbackSurface.DIALOG);
        assertThat(c.presentation().durationMillis()).isEqualTo(1000);
        // rule:lim solo admite una aplicacion: la segunda queda rechazada y la politica global la muestra.
        UserFeedbackEnvelope rejected = only(feedback, "rule:lim", UserFeedbackKind.APPLICATION_REJECTED, "event:2");
        assertThat(rejected.reason()).isEqualTo("Maximum applications per actor reached");
        assertThat(runtime.feedback(M, "actor:one", 2)).hasSize(2);

        // Se guarda el catalogo sin politica: lo rechazado se oculta y la presentacion vuelve a los valores por defecto.
        application.replace(M, definitions(Map.of()), saved.definitionEtag(), bindings(Map.of(), rules), saved.bindingEtag());
        List<UserFeedbackEnvelope> defaults = runtime.feedback(M, "actor:one", 50);
        assertThat(defaults).noneMatch(item -> item.kind() == UserFeedbackKind.APPLICATION_REJECTED);
        assertThat(defaults).filteredOn(item -> item.metadata().get("ruleId").equals("rule:b")).isNotEmpty();
        UserFeedbackEnvelope defaultA = only(defaults, "rule:a", UserFeedbackKind.POINTS_AWARDED, "event:1");
        assertThat(defaultA.presentation().surfaces()).containsExactlyInAnyOrder(UserFeedbackSurface.TOAST, UserFeedbackSurface.ACTIVITY);
        assertThat(defaultA.presentation().durationMillis()).isEqualTo(4000);
        assertThat(defaultA.presentation().maxVisible()).isEqualTo(3);
        assertThat(defaultA.presentation().showPoints()).isTrue();
    }

    private static UserFeedbackEnvelope only(List<UserFeedbackEnvelope> feedback, String ruleId, UserFeedbackKind kind, String eventId) {
        List<UserFeedbackEnvelope> found = feedback.stream().filter(item -> item.metadata().get("ruleId").equals(ruleId)
                && item.kind() == kind && item.sourceEventId().equals(eventId)).toList();
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    private GamificationProcessingResult process(String id, String type, int second, Map<String, Object> payload) {
        CanonicalEvent event = new CanonicalEvent("1.0", id, type, "1.0", Instant.parse("2026-07-28T10:00:00Z").plusSeconds(second),
                new EventSource("test", "gamification", "", "1.0"),
                new EventContext(EventContextKind.GAMIFICATION, "", "", M, "model", "", "session:one"),
                new EventActor("actor:one", EventActorKind.USER, "Actor"), null, "transaction:" + id, "correlation:" + id, "", payload, Map.of());
        return runtime.process(M, event);
    }

    private static Map<String, Object> payload(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) result.put((String) pairs[index], pairs[index + 1]);
        return result;
    }

    private static GamificationDefinitionModel definitions(Map<String, String> annotations) {
        return new GamificationDefinitionModel("1.0", "gamification-definitions", M, "Gamification",
                List.of(new PointTypeDefinition("points:quality", "Quality", "", 0L)), List.of(), List.of(), List.of(), List.of(), annotations);
    }

    private static GamificationBindingModel bindings(Map<String, String> annotations, List<GamificationRule> rules) {
        return new GamificationBindingModel("1.0", "gamification-bindings", M, "gamification-definitions", rules, annotations);
    }

    private static GamificationRule rule(String id, String event, String path, String operator, Object expected, long points, boolean enabled) {
        return new GamificationRule(id, id, event, path, operator, expected, "points:quality", points, "", "", "",
                GamificationRepeatPolicy.ONCE_PER_EVENT, 0, 100, 100, GamificationEffectKind.AWARD, enabled);
    }
}
