package com.example.editor.transformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.artifact.transaction.HydraArtifactTransactionService;
import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.metamodel.validation.MetamodelValidationFingerprint;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.testing.fault.HydraFaultInjectionProperties;
import com.example.editor.testing.fault.HydraFaultInjectionService;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationSaveResponse;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.transformation.persistence.JsonTransformationRepository;
import com.example.editor.transformation.sync.JsonTransformationSynchronizationSessionRepository;
import com.example.editor.transformation.testing.persistence.JsonTransformationTestCaseRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia FUERTE real, sin mocks, de guardar de verdad una definicion de transformacion a traves
 * del mismo servicio de aplicacion real que respalda el boton final «Guardar» del diseñador. Cubre
 * directamente "kind" (elegir nombre y tipo), "ends" (elegir metamodelos), "m2m" (definir reglas y
 * asignaciones), "sv" (guardar) y "art" (definicion guardada) de transformation_define: una
 * definicion modelo-a-modelo completa, con una regla y una asignacion reales, se comprueba y se
 * guarda de verdad en disco a traves de TransformationApplicationService.create(), y una definicion
 * que referencia un metamodelo inexistente nunca llega a persistirse.
 */
class TransformationApplicationServiceSaveIntegrationTests {

    @Test
    void savesARealModelToModelDefinitionWithARuleAndAnAssignment(@TempDir Path temporary) {
        TransformationApplicationService service = service(temporary);

        TransformationDefinitionDocument document = validModelToModelDocument();
        TransformationSaveResponse response = service.create(document);

        // "kind"/"ends": el tipo y los metamodelos de origen/destino elegidos quedan reflejados
        // en la comprobacion y el guardado reales, sin inventar nada por defecto.
        assertThat(response.checkReport().valid()).isTrue();
        assertThat(response.transformation().kind()).isEqualTo(TransformationKind.M2M);
        assertThat(response.transformation().sources()).singleElement().extracting(TransformationEndpoint::metamodelId)
                .isEqualTo(ExpressionTestFixtures.METAMODEL_ID);
        assertThat(response.transformation().targets()).singleElement().extracting(TransformationEndpoint::metamodelId)
                .isEqualTo(ExpressionTestFixtures.METAMODEL_ID);

        // "m2m": la regla y su asignacion de atributos quedan guardadas tal cual.
        assertThat(response.transformation().rules()).singleElement().satisfies(rule -> {
            assertThat(rule.id()).isEqualTo("rule:person");
            assertThat(rule.bindings()).singleElement().extracting(TransformationBinding::targetFeatureId)
                    .isEqualTo(ExpressionTestFixtures.AGE);
        });

        // "sv"/"art": releida desde un repositorio real distinto, construido sobre el mismo
        // directorio, la definicion esta realmente en disco y lista para ejecutarse.
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        TransformationProperties properties = properties(temporary);
        TransformationDefinitionDocument reread = new JsonTransformationRepository(properties, mapper)
                .find(document.id()).orElseThrow().document();
        assertThat(reread.id()).isEqualTo(document.id());
        assertThat(reread.rules()).hasSize(1);
    }

    @Test
    void refusesToSaveADefinitionReferencingAnUnknownMetamodelAndPersistsNothing(@TempDir Path temporary) {
        TransformationApplicationService service = service(temporary);

        TransformationDefinitionDocument document = new TransformationDefinitionDocument("1.0", "transformation-ghost", "Ghost",
                TransformationKind.M2M,
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, "metamodel:does-not-exist", "", "source", true, Map.of())),
                List.of(new TransformationEndpoint("target", TransformationEndpointRole.TARGET, ExpressionTestFixtures.METAMODEL_ID, "", "target", true, Map.of())),
                List.of(), List.of(), null, null, null, Map.of());

        assertThatThrownBy(() -> service.create(document)).isInstanceOf(TransformationApplicationException.class)
                .extracting(exception -> ((TransformationApplicationException) exception).getReason())
                .isEqualTo(TransformationApplicationException.Reason.INVALID_REQUEST);

        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        assertThat(new JsonTransformationRepository(properties(temporary), mapper).find("transformation-ghost")).isEmpty();
    }

    private static TransformationDefinitionDocument validModelToModelDocument() {
        TransformationBinding binding = new TransformationBinding("binding:age", "person", ExpressionTestFixtures.AGE,
                TransformationBindingKind.DIRECT, ExpressionTestFixtures.AGE, null, null, null, Map.of());
        TransformationRule rule = new TransformationRule("rule:person", "Person to Person", true, "source",
                ExpressionTestFixtures.PERSON, "personSource", null,
                List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(binding), Map.of());
        return new TransformationDefinitionDocument("1.0", "transformation-people-sync", "People sync", TransformationKind.M2M,
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "", "source", true, Map.of())),
                List.of(new TransformationEndpoint("target", TransformationEndpointRole.TARGET, ExpressionTestFixtures.METAMODEL_ID, "", "target", true, Map.of())),
                List.of(rule), List.of(), null, null, null, Map.of());
    }

    private static TransformationApplicationService service(Path temporary) {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(ExpressionTestFixtures.metamodel());

        TransformationProperties properties = properties(temporary);
        TransformationDefinitionChecker checker = new TransformationDefinitionChecker(
                new ExpressionTypeChecker(new ExpressionDependencyAnalyzer()));
        HydraArtifactTransactionService transactions = new HydraArtifactTransactionService(
                temporary.resolve("artifact-transactions").toString(), new HydraFaultInjectionService(new HydraFaultInjectionProperties()));

        return new TransformationApplicationService(
                new JsonTransformationRepository(properties, mapper), metamodelService,
                new MetamodelValidationFingerprint(mapper), checker,
                new JsonTransformationTestCaseRepository(properties, mapper, new LocalCanonicalDocumentStoreFactory()),
                new JsonTransformationSynchronizationSessionRepository(properties, mapper, new LocalCanonicalDocumentStoreFactory()),
                transactions);
    }

    private static TransformationProperties properties(Path temporary) {
        TransformationProperties properties = new TransformationProperties();
        properties.setDirectory(temporary.resolve("transformations"));
        properties.setTestDirectory(temporary.resolve("transformation-tests"));
        properties.setSynchronizationSessionDirectory(temporary.resolve("transformation-sync-sessions"));
        properties.setSynchronizationHistoryDirectory(temporary.resolve("transformation-sync-history"));
        return properties;
    }
}
