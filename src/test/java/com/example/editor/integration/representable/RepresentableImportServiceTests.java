package com.example.editor.integration.representable;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class RepresentableImportServiceTests {
    @TempDir Path temporary;

    @Test
    void separatesRepresentableAndUnsupportedEcoreChanges(){
        var registry = new RepresentableImportMapperRegistry(List.of(
                new EcoreRepresentableImportMapper(), new XtextRepresentableImportMapper(),
                new LangiumRepresentableImportMapper()));
        var service = new RepresentableImportService(registry,
                new RepresentableImportStore(JsonMapper.builder().findAndAddModules().build()),
                temporary.toString());
        var request = new RepresentableImportRequest("ecore", "shop", "SAFE", List.of(
                new ExternalChangeCandidate("class", ExternalChangeKind.ADD, "EClass",
                        "shop.Customer", Map.of("abstract", false)),
                new ExternalChangeCandidate("annotation", ExternalChangeKind.ADD, "EAnnotation",
                        "shop.Customer.source", Map.of())), Map.of());

        RepresentableImportPreview preview = service.preview(request);

        assertThat(preview.operations()).hasSize(1);
        assertThat(preview.operations().get(0).elementKind()).isEqualTo(NeutralElementKind.CLASS);
        assertThat(preview.diagnostics()).extracting(RepresentableImportDiagnostic::representability)
                .contains(Representability.FULL, Representability.NONE);
        assertThat(preview.applicable()).isFalse();
    }


    @Test
    void reconcilesAnExternalAddedClassWithAnExistingHydraClassWhenGuidedResolutionRequestsIt(){
        var service = new RepresentableImportService(
                new RepresentableImportMapperRegistry(List.of(new EcoreRepresentableImportMapper())),
                new RepresentableImportStore(JsonMapper.builder().findAndAddModules().build()),
                temporary.toString());
        RepresentableImportPreview preview = service.preview(new RepresentableImportRequest(
                "ECORE", "shop", "SAFE", List.of(new ExternalChangeCandidate(
                "customer", ExternalChangeKind.ADD, "EClass", "external.Customer",
                Map.of("abstract", false, "hydra:resolution", "MAP_EXISTING", "hydra:targetElementId", "class:Customer"))), Map.of()));

        assertThat(preview.applicable()).isTrue();
        assertThat(preview.operations()).singleElement().satisfies(operation -> {
            assertThat(operation.changeKind()).isEqualTo(ExternalChangeKind.UPDATE);
            assertThat(operation.neutralPath()).isEqualTo("class:Customer");
            assertThat(operation.values()).containsEntry("abstract", false)
                    .doesNotContainKeys("hydra:resolution", "hydra:targetElementId");
        });
        assertThat(preview.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.representability()).isEqualTo(Representability.FULL);
            assertThat(diagnostic.message()).contains("fully representable", "existing HYDRA element 'class:Customer'");
            assertThat(diagnostic.suggestion()).contains("no compatibility correction is required");
        });
    }

    @Test
    void persistsACompleteNeutralChangePackage(){
        var service = new RepresentableImportService(
                new RepresentableImportMapperRegistry(List.of(new EcoreRepresentableImportMapper())),
                new RepresentableImportStore(JsonMapper.builder().findAndAddModules().build()),
                temporary.toString());
        RepresentableImportPreview preview = service.apply(new RepresentableImportRequest(
                "ECORE", "shop", "STRICT", List.of(new ExternalChangeCandidate(
                "name", ExternalChangeKind.UPDATE, "EAttribute", "shop.Customer.name", Map.of("type", "EString"))), Map.of()));

        assertThat(preview.applicable()).isTrue();
        assertThat(temporary.resolve(preview.previewId() + ".representable-import.json")).isRegularFile();
    }
}
