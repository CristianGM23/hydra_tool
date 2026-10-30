package com.example.editor.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
 * Evidencia real, sin mocks, de guardar de verdad un catalogo de gamificacion completo a traves del
 * mismo servicio de aplicacion real que respalda el boton final "Guardar" del diseñador. Cubre
 * directamente "def"/"rule"/"save"/"art" de gamification_define: una definicion con un tipo de
 * puntos y una regla real, ligada a un evento conocido, se comprueba sin errores y se guarda de
 * verdad en disco a traves de GamificationApplicationService.create(), y una definicion con una
 * regla invalida nunca llega a persistirse.
 */
class GamificationApplicationServiceSaveIntegrationTests {

    @Test
    void savesARealGamificationCatalogWithAPointTypeAndAVerifiedRule(@TempDir Path temporary) {
        GamificationApplicationService service = service(temporary);

        GamificationDefinitionModel definitions = validDefinitions();
        GamificationBindingModel bindings = validBindings();
        GamificationSaveResponse response = service.create(ExpressionTestFixtures.METAMODEL_ID, definitions, bindings);

        assertThat(response.checkReport().valid()).isTrue();
        assertThat(response.definitions().pointTypes()).extracting(PointTypeDefinition::id).containsExactly("points:quality");
        assertThat(response.bindings().rules()).singleElement().extracting(GamificationRule::eventTypeId)
                .isEqualTo("core.validation.violation-resolved");

        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        GamificationProperties properties = properties(temporary);
        GamificationDefinitionModel reread = new JsonGamificationDefinitionRepository(properties, mapper)
                .find(ExpressionTestFixtures.METAMODEL_ID).orElseThrow().document();
        assertThat(reread.id()).isEqualTo(definitions.id());
        assertThat(reread.pointTypes()).hasSize(1);
    }

    @Test
    void refusesToSaveACatalogWhoseRuleRewardsAQuickFixButtonPressAndPersistsNothing(@TempDir Path temporary) {
        GamificationApplicationService service = service(temporary);

        GamificationRule invalidRule = new GamificationRule("rule:quickfix", "Quick Fix reward", "core.quickfix.applied", "", "EQUALS",
                null, "points:quality", 2, "", "", "", GamificationRepeatPolicy.ONCE_PER_EVENT, 0, 100, 10, GamificationEffectKind.AWARD, true);
        GamificationDefinitionModel definitions = validDefinitions();
        GamificationBindingModel bindings = new GamificationBindingModel("1.0", "gamification-bindings",
                ExpressionTestFixtures.METAMODEL_ID, definitions.id(), List.of(invalidRule), Map.of());

        assertThatThrownBy(() -> service.create(ExpressionTestFixtures.METAMODEL_ID, definitions, bindings))
                .isInstanceOf(GamificationApplicationException.class)
                .extracting(exception -> ((GamificationApplicationException) exception).getReason())
                .isEqualTo(GamificationApplicationException.Reason.INVALID_REQUEST);

        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        assertThat(new JsonGamificationDefinitionRepository(properties(temporary), mapper).find(ExpressionTestFixtures.METAMODEL_ID)).isEmpty();
    }

    /**
     * SEC-ERR (ronda 37c): cuando la lectura pone en cuarentena una configuración malformada, la respuesta 404 debe decirlo (código y mensaje
     * públicos); antes llegaba vacía, indistinguible de «nunca hubo configuración». La ausencia normal sigue siendo un 404 sin cuerpo.
     */
    @Test
    void aQuarantinedConfigurationIsAnsweredWithAnExplicitNotFoundAndAPlainAbsenceKeepsItsEmptyNotFound(@TempDir Path temporary) throws Exception {
        GamificationApplicationService service = service(temporary);
        service.create(ExpressionTestFixtures.METAMODEL_ID, validDefinitions(), validBindings());
        Files.writeString(temporary.resolve("gamification").resolve(ExpressionTestFixtures.METAMODEL_ID + ".gamification-bindings.json"),
                "{ unreadable bindings");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CanonicalGamificationController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        String path = "/api/canonical-metamodels/" + ExpressionTestFixtures.METAMODEL_ID + "/gamification";

        mvc.perform(get(path)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("quarantined")));
        String second = mvc.perform(get(path)).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(second).isEmpty();
    }

    private static GamificationDefinitionModel validDefinitions() {
        return new GamificationDefinitionModel("1.0", "gamification-definitions", ExpressionTestFixtures.METAMODEL_ID, "Gamification",
                List.of(new PointTypeDefinition("points:quality", "Quality", "", 0L)), List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    private static GamificationBindingModel validBindings() {
        GamificationRule rule = new GamificationRule("rule:1", "Rule", "core.validation.violation-resolved", "", "EQUALS", null,
                "points:quality", 2, "", "", "", GamificationRepeatPolicy.ONCE_PER_EVENT, 0, 100, 10, GamificationEffectKind.AWARD, true);
        return new GamificationBindingModel("1.0", "gamification-bindings", ExpressionTestFixtures.METAMODEL_ID,
                "gamification-definitions", List.of(rule), Map.of());
    }

    private static GamificationApplicationService service(Path temporary) {
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

        GamificationProperties properties = properties(temporary);
        return new GamificationApplicationService(
                new JsonGamificationDefinitionRepository(properties, mapper),
                new JsonGamificationBindingRepository(properties, mapper),
                metamodelService, eventModelService, new GamificationChecker(),
                new GamificationCorruptDocumentRecovery(properties));
    }

    private static GamificationProperties properties(Path temporary) {
        GamificationProperties properties = new GamificationProperties();
        properties.setDirectory(temporary.resolve("gamification"));
        return properties;
    }
}
