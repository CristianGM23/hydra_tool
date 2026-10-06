package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateSiteP2AdvancedAnalysisServiceTests {
    private final UpdateSiteP2AdvancedAnalysisService service = new UpdateSiteP2AdvancedAnalysisService();

    @Test
    void recordsCompositeChildrenAndComparesMultipleBundleVersions() {
        String composite = "<repository><children size=\"2\"><child location=\"child-a\"/><child location=\"https://example.org/remote\"/></children></repository>";
        UpdateSiteRepositoryContent repository = new UpdateSiteRepositoryContent(Map.of(
                "compositeContent.xml", composite.getBytes(StandardCharsets.UTF_8),
                "child-a/content.xml", "<repository/>".getBytes(StandardCharsets.UTF_8)), "f", 100);
        UpdateSiteArtifactDescriptor oldEcore = artifact("plugins/library_1.0.0.jar!/model/library.ecore", "old-hash");
        UpdateSiteArtifactDescriptor newEcore = artifact("plugins/library_2.0.0.jar!/model/library.ecore", "new-hash");
        UpdateSiteArtifactDescriptor newDiagram = artifact("plugins/library_2.0.0.jar!/diagram/library.odesign", "diagram-hash");
        UpdateSiteRepositorySummary summary = new UpdateSiteRepositorySummary("site", "f", true, true, 2, 100,
                List.of(), List.of(
                        bundle("library", "1.0.0", List.of(oldEcore)),
                        bundle("library", "2.0.0", List.of(newEcore, newDiagram))),
                List.of(), List.of(), List.of(), List.of());

        UpdateSiteReleaseComparison result = service.analyze(repository, summary);

        assertThat(result.compositeRepositories()).hasSize(2);
        assertThat(result.compositeRepositories()).anySatisfy(value -> {
            assertThat(value.location()).isEqualTo("child-a");
            assertThat(value.presentInUpload()).isTrue();
        });
        assertThat(result.unresolvedCompositeChildren()).isEqualTo(1);
        assertThat(result.versionFamilies()).anySatisfy(value -> {
            assertThat(value.id()).isEqualTo("library");
            assertThat(value.multipleVersions()).isTrue();
            assertThat(value.oldestVersion()).isEqualTo("1.0.0");
            assertThat(value.latestVersion()).isEqualTo("2.0.0");
        });
        assertThat(result.deltas()).anySatisfy(value -> {
            assertThat(value.id()).isEqualTo("library");
            assertThat(value.modelBearing()).isTrue();
            assertThat(value.changedArtifacts()).containsExactly("model/library.ecore");
            assertThat(value.addedArtifacts()).containsExactly("diagram/library.odesign");
            assertThat(value.risk()).isEqualTo(UpdateSiteReleaseRisk.HIGH);
            assertThat(value.recommendation()).contains("Re-run metamodel");
        });
    }

    private UpdateSiteBundleDescriptor bundle(String id, String version, List<UpdateSiteArtifactDescriptor> artifacts) {
        return new UpdateSiteBundleDescriptor(id, version, id, UpdateSiteBundleKind.MODEL_PLUGIN,
                "plugins/" + id + '_' + version + ".jar", List.of(), List.of(), artifacts, Map.of());
    }

    private UpdateSiteArtifactDescriptor artifact(String path, String hash) {
        return new UpdateSiteArtifactDescriptor(path, UpdateSiteArtifactKinds.fromPath(path), "library", 10, hash);
    }
}
