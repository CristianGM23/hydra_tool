package com.example.editor.artifact.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class HydraArtifactGraphImpactTests {
    @Test void calculatesTypedTransitiveImpactAndSafeDeletionOrder() {
        HydraArtifactDescriptor metamodel = artifact("mm", "2.0", List.of());
        HydraArtifactDescriptor validation = artifact("validation", "1.0",
                List.of(new HydraArtifactDependency("mm", true, "1.0", HydraArtifactRelationKind.USES_METAMODEL)));
        HydraArtifactDescriptor editor = artifact("editor", "1.0",
                List.of(new HydraArtifactDependency("validation", true, "1.0", HydraArtifactRelationKind.USES)));
        HydraArtifactRegistrySnapshot snapshot = new HydraArtifactRegistrySnapshot("1.0", Instant.EPOCH, List.of(metamodel, validation, editor));

        HydraArtifactGraphService service = new HydraArtifactGraphService();
        HydraArtifactImpact impact = service.impact(snapshot, "mm");
        assertThat(impact.directDependants()).containsExactly("validation");
        assertThat(impact.transitiveDependants()).containsExactly("editor", "validation");
        assertThat(impact.blockingDependants()).containsExactly("editor", "validation");
        assertThat(impact.safeDeletionOrder()).containsExactly("editor", "validation", "mm");
        assertThat(service.build(snapshot).edges()).anySatisfy(edge -> {
            assertThat(edge.fromArtifactId()).isEqualTo("validation");
            assertThat(edge.relationKind()).isEqualTo(HydraArtifactRelationKind.USES_METAMODEL);
        });
    }

    private HydraArtifactDescriptor artifact(String id, String version, List<HydraArtifactDependency> dependencies) {
        return new HydraArtifactDescriptor(id, id, HydraArtifactKind.OTHER, version, "test", id + ".json", "",
                HydraArtifactStatus.AVAILABLE, Instant.EPOCH, dependencies, Map.of());
    }
}
