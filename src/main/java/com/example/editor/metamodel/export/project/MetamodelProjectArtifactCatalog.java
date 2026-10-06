package com.example.editor.metamodel.export.project;

import java.util.List;

/** Catálogo determinista de capas y editores asociados a un metamodelo. */
public record MetamodelProjectArtifactCatalog(String metamodelId, List<MetamodelProjectArtifactDescriptor> artifacts) {
    public MetamodelProjectArtifactCatalog {
        metamodelId = metamodelId == null ? "" : metamodelId.trim();
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }
}
