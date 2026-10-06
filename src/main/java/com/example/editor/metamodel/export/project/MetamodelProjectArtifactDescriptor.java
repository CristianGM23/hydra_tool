package com.example.editor.metamodel.export.project;

import com.example.editor.integration.IntegrationArtifactKind;

/** Describe una capa disponible y su versión para que el usuario pueda seleccionar la exportación exacta. */
public record MetamodelProjectArtifactDescriptor(IntegrationArtifactKind kind, String documentId, String displayName,
        String etag, boolean editorDefinition) {
    public MetamodelProjectArtifactDescriptor {
        documentId = documentId == null ? "" : documentId.trim();
        displayName = displayName == null || displayName.isBlank() ? documentId : displayName.trim();
        etag = etag == null ? "" : etag.trim();
    }
}
