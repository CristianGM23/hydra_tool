package com.example.editor.standalone.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StandaloneDistributionPlannerTests {
    private final StandaloneDistributionPlanner planner = new StandaloneDistributionPlanner();

    @Test
    void createsPwaManifestAndOfflineServiceWorker(){
        List<StandaloneDistributionArtifact> artifacts = planner.plan(profile(Set.of(StandaloneDistributionTarget.PWA), true));
        assertThat(artifacts).extracting(StandaloneDistributionArtifact::path)
                .containsExactly("distribution/hydra-distribution.json", "public/manifest.webmanifest", "public/service-worker.js");
        assertThat(content(artifacts, "public/service-worker.js")).contains("caches.open", "fetch");
    }

    @Test
    void createsTauriConfigurationAndNormalizesFileAssociations(){
        StandaloneDistributionProfile profile = new StandaloneDistributionProfile(
                "Library Editor", "1.2.0", Set.of(StandaloneDistributionTarget.TAURI_WINDOWS), false,
                List.of("hydra", ".xmi", "HYDRA"), "stable");
        List<StandaloneDistributionArtifact> artifacts = planner.plan(profile);
        assertThat(content(artifacts, "src-tauri/tauri.conf.json"))
                .contains("org.hydra.library-editor", "\"hydra\"", "\"xmi\"");
        assertThat(profile.fileAssociations()).containsExactly(".hydra", ".xmi");
    }

    @Test
    void producesStablePathOrderingForCombinedTargets(){
        List<StandaloneDistributionArtifact> artifacts = planner.plan(profile(Set.of(
                StandaloneDistributionTarget.PWA,
                StandaloneDistributionTarget.TAURI_LINUX,
                StandaloneDistributionTarget.TAURI_WINDOWS), true));
        assertThat(artifacts).extracting(StandaloneDistributionArtifact::path).isSorted();
        assertThat(artifacts).hasSize(9);
    }

    @Test
    void rejectsEmptyTargetsAndUnsafeArtifactPaths(){
        assertThatThrownBy(() -> new StandaloneDistributionProfile(
                "Editor", "1", Set.of(), false, List.of(), "stable"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targets");
        assertThatThrownBy(() -> new StandaloneDistributionArtifact("../outside", "text/plain", "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safe relative path");
    }

    private StandaloneDistributionProfile profile(Set<StandaloneDistributionTarget> targets, boolean offline){
        return new StandaloneDistributionProfile("HYDRA Library Editor", "2.8.0", targets, offline,
                List.of(".hydra", ".xmi"), "stable");
    }

    private String content(List<StandaloneDistributionArtifact> artifacts, String path){
        return artifacts.stream().filter(item -> item.path().equals(path)).findFirst().orElseThrow().content();
    }
}
