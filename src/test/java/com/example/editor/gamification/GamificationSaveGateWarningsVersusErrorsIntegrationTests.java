package com.example.editor.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.EventModelChecker;
import com.example.editor.event.EventModelProperties;
import com.example.editor.event.persistence.JsonEventBindingModelRepository;
import com.example.editor.event.persistence.JsonEventDefinitionModelRepository;
import com.example.editor.gamification.persistence.JsonGamificationBindingRepository;
import com.example.editor.gamification.persistence.JsonGamificationDefinitionRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real de la puerta de guardado de gamificacion: gamification_define.ok.
 * Solo los errores bloquean el guardado; un aviso (regla sin tope) se persiste. El oraculo son los dos
 * ficheros JSON del directorio temporal leidos con Jackson y comparados byte a byte.
 */
class GamificationSaveGateWarningsVersusErrorsIntegrationTests {
    private static final String M = ExpressionTestFixtures.METAMODEL_ID;
    private static final String EVENT = "core.validation.violation-resolved";

    @TempDir Path temporary;
    private JsonMapper mapper;
    private GamificationApplicationService service;

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
        service = new GamificationApplicationService(new JsonGamificationDefinitionRepository(properties, mapper),
                new JsonGamificationBindingRepository(properties, mapper), metamodels, events, new GamificationChecker(),
                new GamificationCorruptDocumentRecovery(properties));
    }

    @Test @DisplayName("gamification_define.ok: una regla sin tope solo da aviso y se guarda en disco")
    void aWarningOnlyRuleIsPersistedToDisk() throws Exception {
        GamificationRule unbounded = rule("rule:unbounded", GamificationRepeatPolicy.UNLIMITED);
        GamificationSaveResponse response = service.create(M, definitions(List.of()), bindings(unbounded));

        assertThat(response.checkReport().valid()).isTrue();
        assertThat(response.checkReport().diagnostics()).extracting(GamificationDiagnostic::code).containsExactly("UNBOUNDED_RULE");
        assertThat(response.checkReport().diagnostics().get(0).severity()).isEqualTo(GamificationDiagnosticSeverity.WARNING);

        Path definitionsFile = directory().resolve("library.gamification-definitions.json");
        Path bindingsFile = directory().resolve("library.gamification-bindings.json");
        assertThat(definitionsFile).exists();
        assertThat(bindingsFile).exists();
        Map<?, ?> bindingsOnDisk = mapper.readValue(Files.readString(bindingsFile), Map.class);
        List<?> rules = (List<?>) bindingsOnDisk.get("rules");
        assertThat(rules).hasSize(1);
        assertThat(((Map<?, ?>) rules.get(0)).get("id")).isEqualTo("rule:unbounded");
        assertThat(((Map<?, ?>) rules.get(0)).get("repeatPolicy")).isEqualTo("UNLIMITED");
        Map<?, ?> definitionsOnDisk = mapper.readValue(Files.readString(definitionsFile), Map.class);
        assertThat(((List<?>) ((Map<?, ?>) definitionsOnDisk).get("pointTypes"))).hasSize(1);
    }

    @Test @DisplayName("gamification_define.ok: un nivel con tipo de puntos inexistente se rechaza y no escribe nada")
    void anErrorIsRejectedAndNothingIsWritten() {
        GamificationDefinitionModel unknownLevelType = definitions(
                List.of(new LevelDefinition("level:one", "One", "points:missing", 10, "")));

        assertThatThrownBy(() -> service.create(M, unknownLevelType, bindings(rule("rule:a", GamificationRepeatPolicy.ONCE_PER_EVENT))))
                .isInstanceOf(GamificationApplicationException.class)
                .extracting(exception -> ((GamificationApplicationException) exception).getReason())
                .isEqualTo(GamificationApplicationException.Reason.INVALID_REQUEST);

        assertThat(directory().resolve("library.gamification-definitions.json")).doesNotExist();
        assertThat(directory().resolve("library.gamification-bindings.json")).doesNotExist();
    }

    @Test @DisplayName("gamification_define.ok: reemplazar con un error deja intactos los dos ficheros previos")
    void replacingWithAnErrorLeavesThePreviousFilesUntouched() throws Exception {
        GamificationSaveResponse saved = service.create(M, definitions(List.of()),
                bindings(rule("rule:a", GamificationRepeatPolicy.ONCE_PER_EVENT)));
        Path definitionsFile = directory().resolve("library.gamification-definitions.json");
        Path bindingsFile = directory().resolve("library.gamification-bindings.json");
        byte[] definitionsBefore = Files.readAllBytes(definitionsFile);
        byte[] bindingsBefore = Files.readAllBytes(bindingsFile);

        GamificationDefinitionModel broken = definitions(List.of(new LevelDefinition("level:one", "One", "points:missing", 10, "")));
        assertThatThrownBy(() -> service.replace(M, broken, saved.definitionEtag(),
                bindings(rule("rule:changed", GamificationRepeatPolicy.ONCE_PER_EVENT)), saved.bindingEtag()))
                .isInstanceOf(GamificationApplicationException.class)
                .extracting(exception -> ((GamificationApplicationException) exception).getReason())
                .isEqualTo(GamificationApplicationException.Reason.INVALID_REQUEST);

        assertThat(Files.readAllBytes(definitionsFile)).isEqualTo(definitionsBefore);
        assertThat(Files.readAllBytes(bindingsFile)).isEqualTo(bindingsBefore);
        assertThat(Files.readString(bindingsFile)).contains("rule:a").doesNotContain("rule:changed");
    }

    private Path directory() { return temporary.resolve("gamification"); }

    private static GamificationDefinitionModel definitions(List<LevelDefinition> levels) {
        return new GamificationDefinitionModel("1.0", "gamification-definitions", M, "Gamification",
                List.of(new PointTypeDefinition("points:quality", "Quality", "", 0L)), levels, List.of(), List.of(), List.of(), Map.of());
    }

    private static GamificationBindingModel bindings(GamificationRule rule) {
        return new GamificationBindingModel("1.0", "gamification-bindings", M, "gamification-definitions", List.of(rule), Map.of());
    }

    private static GamificationRule rule(String id, GamificationRepeatPolicy policy) {
        boolean unlimited = policy == GamificationRepeatPolicy.UNLIMITED;
        return new GamificationRule(id, id, EVENT, "", "EQUALS", null, "points:quality", 2, "", "", "",
                policy, 0, unlimited ? 0 : 100, unlimited ? 0 : 100, GamificationEffectKind.AWARD, true);
    }
}
