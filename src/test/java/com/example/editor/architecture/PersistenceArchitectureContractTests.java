package com.example.editor.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class PersistenceArchitectureContractTests {
    private static final List<String> REPOSITORIES = List.of(
            "src/main/java/com/example/editor/definition/runtime/session/diagram/JsonEditorSessionDiagramRepository.java",
            "src/main/java/com/example/editor/definition/runtime/session/persistence/JsonEditorModelSessionRepository.java",
            "src/main/java/com/example/editor/definition/persistence/JsonEditorDefinitionRepository.java",
            "src/main/java/com/example/editor/integration/persistence/JsonIntegrationProfileRepository.java",
            "src/main/java/com/example/editor/event/diagram/JsonEventArchitectureDiagramRepository.java",
            "src/main/java/com/example/editor/event/flow/diagram/JsonEventFlowDiagramRepository.java",
            "src/main/java/com/example/editor/event/flow/runtime/persistence/JsonEventFlowRuntimeSnapshotRepository.java",
            "src/main/java/com/example/editor/event/flow/persistence/JsonEventFlowModelRepository.java",
            "src/main/java/com/example/editor/event/persistence/JsonEventDefinitionModelRepository.java",
            "src/main/java/com/example/editor/event/persistence/JsonEventBindingModelRepository.java",
            "src/main/java/com/example/editor/metamodel/behavior/persistence/JsonOperationBehaviorModelRepository.java",
            "src/main/java/com/example/editor/gamification/diagram/JsonGamificationDiagramRepository.java",
            "src/main/java/com/example/editor/gamification/persistence/JsonGamificationBindingRepository.java",
            "src/main/java/com/example/editor/gamification/persistence/JsonGamificationRuntimeRepository.java",
            "src/main/java/com/example/editor/gamification/persistence/JsonGamificationDefinitionRepository.java",
            "src/main/java/com/example/editor/validation/action/persistence/JsonActionModelRepository.java",
            "src/main/java/com/example/editor/validation/validation/persistence/JsonValidationModelRepository.java");

    @Test
    void canonicalRepositoriesDependOnThePersistenceSpiRatherThanTheLocalStore() throws Exception {
        for(String path : REPOSITORIES) {
            String source = source(path);
            assertThat(source).as(path).contains("CanonicalDocumentStoreFactory", "CanonicalDocumentStoreSpec");
            assertThat(source).as(path).doesNotContain("import com.example.editor.validation.persistence.AtomicJsonDocumentStore;");
        }
    }

    @Test
    void localAtomicJsonRemainsTheDefaultProvider() throws Exception {
        assertThat(source("src/main/java/com/example/editor/persistence/LocalCanonicalDocumentStoreFactory.java"))
                .contains("@Component", "new AtomicJsonDocumentStore<>");
        assertThat(source("src/main/java/com/example/editor/validation/persistence/AtomicJsonDocumentStore.java"))
                .contains("implements CanonicalDocumentStore<T>");
    }

    @Test
    void layerInterchangeUsesTheCanonicalPortableEnvelope() throws Exception {
        String envelope = source("frontend/src/layer-io/layerInterchangeEnvelope.ts");
        assertThat(envelope).contains("schemaVersion: '1.0'", "metamodelId", "dependencies", "payload", "enveloped: false");
        for(String name : List.of("validationInterchange.ts", "quickFixInterchange.ts", "eventInterchange.ts",
                "eventFlowInterchange.ts", "gamificationInterchange.ts")) {
            assertThat(source("frontend/src/layer-io/" + name)).as(name)
                    .contains("makeLayerInterchangeEnvelope", "unwrapLayerInterchangeEnvelope");
        }
    }

    @Test
    void largeSpanishCatalogsAreStableFacadesOverReviewedSlices() throws Exception {
        assertThat(source("frontend/src/i18n/extendedSpanishCatalog.ts"))
                .contains("extendedSpanishCatalogA", "extendedSpanishCatalogB", "extendedSpanishCatalogC");
        assertThat(source("frontend/src/i18n/domainSpanishCatalog.ts"))
                .contains("domainSpanishCatalogA", "domainSpanishCatalogB");
        assertThat(source("frontend/src/i18n/guidedSpanishCatalog.ts"))
                .contains("guidedSpanishCatalogA", "guidedSpanishCatalogB");
        assertThat(source("frontend/src/i18n/translationCatalog.ts"))
                .contains("SPANISH_CONFLICT_RESOLUTIONS", "...SPANISH_CONFLICT_RESOLUTIONS");
    }

    @Test
    void layerInterchangePublishesAPortableSchema() throws Exception {
        assertThat(source("src/main/resources/schemas/portability/layer-interchange-envelope-1.0.schema.json"))
                .contains("HYDRA Layer Interchange Envelope 1.0", "\"EVENT_FLOW\"", "\"dependencies\"", "\"payload\"");
    }

    private String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
