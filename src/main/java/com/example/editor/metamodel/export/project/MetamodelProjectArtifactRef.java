package com.example.editor.metamodel.export.project;

import com.example.editor.integration.IntegrationArtifactKind;

/** Identifica de forma estable un documento canónico seleccionado para la exportación. */
public record MetamodelProjectArtifactRef(IntegrationArtifactKind kind, String documentId) {
    public MetamodelProjectArtifactRef {
        if(kind == null){throw new IllegalArgumentException("artifact kind is required");}
        documentId = documentId == null ? "" : documentId.trim();
        if(documentId.isBlank()){throw new IllegalArgumentException("artifact documentId is required");}
    }
}
