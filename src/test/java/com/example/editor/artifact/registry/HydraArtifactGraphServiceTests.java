package com.example.editor.artifact.registry;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HydraArtifactGraphServiceTests {
    @Test
    void detectsMissingDependenciesAndCyclesDeterministically(){
        HydraArtifactDescriptor first = artifact("first", List.of(new HydraArtifactDependency("second", true, "1.0")));
        HydraArtifactDescriptor second = artifact("second", List.of(new HydraArtifactDependency("first", true, "1.0")));
        HydraArtifactDescriptor third = artifact("third", List.of(new HydraArtifactDependency("missing", true, "1.0")));

        HydraArtifactDependencyGraph graph = new HydraArtifactGraphService().build(
                new HydraArtifactRegistrySnapshot("1.0", Instant.EPOCH, List.of(third, second, first)));

        assertThat(graph.ready()).isFalse();
        assertThat(graph.cycles()).hasSize(1);
        assertThat(graph.nodes()).extracting(HydraArtifactGraphNode::artifactId)
                .containsExactly("first", "second", "third");
        assertThat(graph.nodes().get(2).status()).isEqualTo(HydraArtifactStatus.MISSING_DEPENDENCY);
    }

    private HydraArtifactDescriptor artifact(String id, List<HydraArtifactDependency> dependencies){
        return new HydraArtifactDescriptor(id, id, HydraArtifactKind.OTHER, "1.0", "test",
                id + ".json", "", HydraArtifactStatus.AVAILABLE, Instant.EPOCH, dependencies, Map.of());
    }
}
