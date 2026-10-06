package com.example.editor.metamodel.importing.updatesite;

import java.util.List;
import java.util.Map;

/** Resultado interno del análisis de content.xml y artifacts.xml. */
record P2MetadataSnapshot(String repositoryName, boolean contentFound, boolean artifactsFound,
        Map<String, P2Unit> units, List<String> diagnostics) {
    P2MetadataSnapshot {
        repositoryName = repositoryName == null ? "" : repositoryName;
        units = units == null ? Map.of() : Map.copyOf(units);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    record P2Unit(String id, String version, String name, boolean featureGroup,
            List<UpdateSiteDependency> dependencies, List<String> artifactClassifiers) {
        P2Unit {
            id = id == null ? "" : id;
            version = version == null ? "" : version;
            name = name == null || name.isBlank() ? id : name;
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
            artifactClassifiers = artifactClassifiers == null ? List.of() : List.copyOf(artifactClassifiers);
        }
    }
}
