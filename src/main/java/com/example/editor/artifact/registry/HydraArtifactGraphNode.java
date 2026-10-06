package com.example.editor.artifact.registry;

import java.util.List;

/** Read-only dependency graph projection for one artifact. */
public record HydraArtifactGraphNode(String artifactId, HydraArtifactStatus status,
        List<String> dependencies, List<String> dependants, List<String> diagnostics) {
    public HydraArtifactGraphNode {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        dependants = dependants == null ? List.of() : List.copyOf(dependants);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
