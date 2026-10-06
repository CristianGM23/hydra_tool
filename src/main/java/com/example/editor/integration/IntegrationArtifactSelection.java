package com.example.editor.integration;
import com.example.editor.validation.contract.StableIdentifierPolicy;
/** Referencia a un documento canónico incluido por el perfil. */
public record IntegrationArtifactSelection(IntegrationArtifactKind kind, String documentId, boolean required) {
    public IntegrationArtifactSelection {
        if(kind == null) throw new IllegalArgumentException("artifact.kind must not be null");
        documentId = StableIdentifierPolicy.requireDocumentId(documentId, "artifact.documentId");
    }
}
