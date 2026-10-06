package com.example.editor.artifact.registry;

import java.util.List;

/** Impacto transitive de modificar o eliminar un artefacto registrado. */
public record HydraArtifactImpact(String artifactId, List<String> directDependants,
        List<String> transitiveDependants, List<String> blockingDependants, List<String> safeDeletionOrder) {
    public HydraArtifactImpact {
        artifactId = artifactId == null ? "" : artifactId.trim();
        directDependants = copy(directDependants);
        transitiveDependants = copy(transitiveDependants);
        blockingDependants = copy(blockingDependants);
        safeDeletionOrder = copy(safeDeletionOrder);
    }

    /** Descripción: Copia defensivamente una lista de identificadores del análisis de impacto. Entradas: values: valores. Salida: lista inmutable. */
    private static List<String> copy(List<String> values) { return values == null ? List.of() : List.copyOf(values); }
}
