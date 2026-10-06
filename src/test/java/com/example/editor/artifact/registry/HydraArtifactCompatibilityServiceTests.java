package com.example.editor.artifact.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class HydraArtifactCompatibilityServiceTests {
    @Test void requiresMigrationWhenDependencyVersionIsTooOld() {
        HydraArtifactDescriptor target = artifact("mm", "1.5", HydraArtifactStatus.AVAILABLE, List.of());
        HydraArtifactDescriptor consumer = artifact("transform", "1.0", HydraArtifactStatus.AVAILABLE,
                List.of(new HydraArtifactDependency("mm", true, "2.0", HydraArtifactRelationKind.USES)));
        HydraArtifactRegistrySnapshot snapshot = new HydraArtifactRegistrySnapshot("1.0", Instant.EPOCH, List.of(target, consumer));

        HydraArtifactCompatibilityReport report = new HydraArtifactCompatibilityService().evaluate(snapshot, "transform");
        assertThat(report.status()).isEqualTo(HydraArtifactCompatibilityStatus.MIGRATION_REQUIRED);
        assertThat(report.diagnostics()).singleElement().asString().contains("requires at least 2.0");
        assertThat(new HydraArtifactGraphService().build(snapshot).nodes().stream()
                .filter(node -> node.artifactId().equals("transform")).findFirst().orElseThrow().status())
                .isEqualTo(HydraArtifactStatus.INCOMPATIBLE_DEPENDENCY);
    }

    @Test void asksForRevalidationWhenRequiredDependencyIsStale() {
        HydraArtifactDescriptor target = artifact("mm", "2.0", HydraArtifactStatus.STALE, List.of());
        HydraArtifactDescriptor consumer = artifact("editor", "1.0", HydraArtifactStatus.AVAILABLE,
                List.of(new HydraArtifactDependency("mm", true, "1.0")));
        HydraArtifactCompatibilityReport report = new HydraArtifactCompatibilityService().evaluate(
                new HydraArtifactRegistrySnapshot("1.0", Instant.EPOCH, List.of(target, consumer)), "editor");
        assertThat(report.status()).isEqualTo(HydraArtifactCompatibilityStatus.REVALIDATION_REQUIRED);
    }

    private HydraArtifactDescriptor artifact(String id, String version, HydraArtifactStatus status, List<HydraArtifactDependency> dependencies) {
        return new HydraArtifactDescriptor(id, id, HydraArtifactKind.OTHER, version, "test", id + ".json", "", status,
                Instant.EPOCH, dependencies, Map.of());
    }
}
