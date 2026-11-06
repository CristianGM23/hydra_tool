package com.example.editor.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.editor.config.StorageProperties;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.EventModelChecker;
import com.example.editor.event.EventModelProperties;
import com.example.editor.event.persistence.JsonEventBindingModelRepository;
import com.example.editor.event.persistence.JsonEventDefinitionModelRepository;
import com.example.editor.gamification.persistence.JsonGamificationBindingRepository;
import com.example.editor.gamification.persistence.JsonGamificationDefinitionRepository;
import com.example.editor.gamification.web.CanonicalGamificationController;
import com.example.editor.gamification.web.GamificationModelRequest;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import com.example.editor.web.ApiExceptionHandler;

import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real por HTTP (MockMvc sobre el controlador real, servicio real, repositorios JSON en
 * disco) de la comprobacion y del guardado con puerta del catalogo de gamificacion.
 * Etapas: gamification_author_source.ck, gamification_define.lvl, gamification_define.chk.
 */
class GamificationCheckSaveGateAndLevelsIntegrationTests {
    private static final String BASE = "/api/canonical-metamodels/" + ExpressionTestFixtures.METAMODEL_ID + "/gamification";
    private static final String EVENT = "core.validation.violation-resolved";

    @TempDir Path temporary;
    private MockMvc mockMvc;
    private JsonMapper mapper;

    @BeforeEach void setUp() {
        mapper = JsonMapper.builder().findAndAddModules().build();
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
        GamificationProperties properties = new GamificationProperties();
        properties.setDirectory(temporary.resolve("gamification"));
        GamificationApplicationService service = new GamificationApplicationService(
                new JsonGamificationDefinitionRepository(properties, mapper), new JsonGamificationBindingRepository(properties, mapper),
                metamodelService, eventService, new GamificationChecker(), new GamificationCorruptDocumentRecovery(properties));
        mockMvc = MockMvcBuilders.standaloneSetup(new CanonicalGamificationController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test void checkEndpointReportsEveryDefectOfABrokenCatalogIncludingLevels() throws Exception {
        String body = mockMvc.perform(post(BASE + "/check").contentType(MediaType.APPLICATION_JSON).content(json(brokenDefinitions(), brokenBindings())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(diagnostics(body)).containsExactlyInAnyOrder(
                "ERROR:DUPLICATE_POINT_TYPE@points:quality", "ERROR:DUPLICATE_LEVEL@level:one",
                "ERROR:UNKNOWN_LEVEL_POINT_TYPE@level:ghost", "ERROR:UNKNOWN_GAMIFICATION_EVENT@rule:unknown-event",
                "ERROR:UNKNOWN_RULE_POINT_TYPE@rule:unknown-point", "ERROR:UNKNOWN_REWARD@rule:unknown-reward",
                "ERROR:PENALTY_CANNOT_GRANT_REWARD@rule:penalty-reward", "WARNING:RULE_WITHOUT_EFFECT@rule:no-effect",
                "WARNING:UNBOUNDED_RULE@rule:unbounded", "ERROR:DAILY_LIMIT_REQUIRED@rule:daily",
                "ERROR:DUPLICATE_GAMIFICATION_RULE@rule:clean");
    }

    @Test void warningOnlyCatalogIsSavedWithItsLevelsOnDiskAndCheckSavedMatchesCheckDraft() throws Exception {
        String request = json(levelDefinitions("points:quality"), warningBindings());
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Gamification-Definitions-ETag"))
                .andExpect(header().exists("X-Gamification-Bindings-ETag"));

        // Oraculo independiente: se lee el JSON del disco sin pasar por el repositorio.
        Path definitionsFile = temporary.resolve("gamification").resolve("library.gamification-definitions.json");
        Path bindingsFile = temporary.resolve("gamification").resolve("library.gamification-bindings.json");
        assertThat(Files.isRegularFile(definitionsFile)).isTrue();
        Map<?, ?> definitions = mapper.readValue(Files.readString(definitionsFile), Map.class);
        List<?> levels = (List<?>) definitions.get("levels");
        assertThat(levels).hasSize(2);
        assertThat(((Map<?, ?>) levels.get(0)).get("id")).isEqualTo("level:one");
        assertThat(((Number) ((Map<?, ?>) levels.get(0)).get("minimumPoints")).longValue()).isEqualTo(10L);
        assertThat(((Map<?, ?>) levels.get(1)).get("id")).isEqualTo("level:two");
        assertThat(((Number) ((Map<?, ?>) levels.get(1)).get("minimumPoints")).longValue()).isEqualTo(50L);
        assertThat(((Map<?, ?>) levels.get(1)).get("pointTypeId")).isEqualTo("points:quality");
        Map<?, ?> bindings = mapper.readValue(Files.readString(bindingsFile), Map.class);
        assertThat(((List<?>) bindings.get("rules"))).extracting(rule -> String.valueOf(((Map<?, ?>) rule).get("id")))
                .containsExactly("rule:unbounded", "rule:no-effect");

        String draft = mockMvc.perform(post(BASE + "/check").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String saved = mockMvc.perform(post(BASE + "/check-saved"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(diagnostics(saved)).containsExactlyInAnyOrder("WARNING:UNBOUNDED_RULE@rule:unbounded", "WARNING:RULE_WITHOUT_EFFECT@rule:no-effect");
        assertThat(diagnostics(saved)).isEqualTo(diagnostics(draft));
    }

    @Test void catalogWithALevelOfAnUnknownPointTypeIsRejectedAndNothingIsPersisted() throws Exception {
        String request = json(levelDefinitions("points:missing"), warningBindings());

        String body = mockMvc.perform(post(BASE + "/check").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(diagnostics(body)).contains("ERROR:UNKNOWN_LEVEL_POINT_TYPE@level:one", "ERROR:UNKNOWN_LEVEL_POINT_TYPE@level:two");

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isBadRequest());
        assertThat(Files.exists(temporary.resolve("gamification").resolve("library.gamification-definitions.json"))).isFalse();
        assertThat(Files.exists(temporary.resolve("gamification").resolve("library.gamification-bindings.json"))).isFalse();
    }

    private List<String> diagnostics(String body) {
        Map<?, ?> report = mapper.readValue(body, Map.class);
        List<String> result = new ArrayList<>();
        for (Object item : (List<?>) report.get("diagnostics")) {
            Map<?, ?> diagnostic = (Map<?, ?>) item;
            result.add(diagnostic.get("severity") + ":" + diagnostic.get("code") + "@" + diagnostic.get("elementId"));
        }
        return result;
    }

    private String json(GamificationDefinitionModel definitions, GamificationBindingModel bindings) {
        return mapper.writeValueAsString(new GamificationModelRequest(definitions, bindings));
    }

    private static GamificationDefinitionModel levelDefinitions(String levelPointType) {
        return new GamificationDefinitionModel("1.0", "gamification-definitions", ExpressionTestFixtures.METAMODEL_ID, "Gamification",
                List.of(new PointTypeDefinition("points:quality", "Quality", "", 0L)),
                List.of(new LevelDefinition("level:one", "One", levelPointType, 10, ""), new LevelDefinition("level:two", "Two", levelPointType, 50, "reward:badge")),
                List.of(), List.of(), List.of(new RewardDefinition("reward:badge", "Badge", "", Map.of())), Map.of());
    }

    private static GamificationBindingModel warningBindings() {
        return bindings(List.of(rule("rule:unbounded", EVENT, "points:quality", 2, "", GamificationRepeatPolicy.UNLIMITED, 0, 0, 0, GamificationEffectKind.AWARD),
                rule("rule:no-effect", EVENT, "", 0, "", GamificationRepeatPolicy.ONCE_PER_EVENT, 0, 0, 0, GamificationEffectKind.AWARD)));
    }

    private static GamificationDefinitionModel brokenDefinitions() {
        return new GamificationDefinitionModel("1.0", "gamification-definitions", ExpressionTestFixtures.METAMODEL_ID, "Gamification",
                List.of(new PointTypeDefinition("points:quality", "Quality", "", 0L), new PointTypeDefinition("points:quality", "Quality again", "", 0L)),
                List.of(new LevelDefinition("level:one", "One", "points:quality", 10, ""), new LevelDefinition("level:one", "One again", "points:quality", 20, ""),
                        new LevelDefinition("level:ghost", "Ghost", "points:missing", 5, "")),
                List.of(), List.of(), List.of(new RewardDefinition("reward:badge", "Badge", "", Map.of())), Map.of());
    }

    private static GamificationBindingModel brokenBindings() {
        GamificationRepeatPolicy once = GamificationRepeatPolicy.ONCE_PER_EVENT;
        return bindings(List.of(
                rule("rule:unknown-event", "custom.unknown", "points:quality", 2, "", once, 0, 0, 0, GamificationEffectKind.AWARD),
                rule("rule:unknown-point", EVENT, "points:missing", 2, "", once, 0, 0, 0, GamificationEffectKind.AWARD),
                rule("rule:unknown-reward", EVENT, "points:quality", 2, "reward:missing", once, 0, 0, 0, GamificationEffectKind.AWARD),
                rule("rule:penalty-reward", EVENT, "points:quality", -2, "reward:badge", once, 0, 0, 0, GamificationEffectKind.PENALTY),
                rule("rule:no-effect", EVENT, "", 0, "", once, 0, 0, 0, GamificationEffectKind.AWARD),
                rule("rule:unbounded", EVENT, "points:quality", 2, "", GamificationRepeatPolicy.UNLIMITED, 0, 0, 0, GamificationEffectKind.AWARD),
                rule("rule:daily", EVENT, "points:quality", 2, "", GamificationRepeatPolicy.DAILY_LIMIT, 0, 0, 0, GamificationEffectKind.AWARD),
                rule("rule:clean", EVENT, "points:quality", 2, "", once, 0, 100, 10, GamificationEffectKind.AWARD),
                rule("rule:clean", EVENT, "points:quality", 2, "", once, 0, 100, 10, GamificationEffectKind.AWARD)));
    }

    private static GamificationBindingModel bindings(List<GamificationRule> rules) {
        return new GamificationBindingModel("1.0", "gamification-bindings", ExpressionTestFixtures.METAMODEL_ID, "gamification-definitions", rules, Map.of());
    }

    private static GamificationRule rule(String id, String event, String pointType, long points, String rewardId,
            GamificationRepeatPolicy policy, long cooldown, int perActor, int perDay, GamificationEffectKind kind) {
        return new GamificationRule(id, id, event, "", "EQUALS", null, pointType, points, "", "", rewardId, policy, cooldown, perActor, perDay, kind, true);
    }
}
