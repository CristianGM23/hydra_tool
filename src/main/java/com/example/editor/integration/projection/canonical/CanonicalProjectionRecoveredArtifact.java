package com.example.editor.integration.projection.canonical;

import java.util.Arrays;
import java.util.Map;
import com.example.editor.integration.IntegrationArtifactKind;

/** Documento canónico recuperado de una proyección que conservaba provenance HYDRA. */
public record CanonicalProjectionRecoveredArtifact(IntegrationArtifactKind kind, String documentId,
        String mediaType, byte[] content, String sha256, Map<String, String> metadata) {
    public CanonicalProjectionRecoveredArtifact {
        if(kind == null || documentId == null || documentId.isBlank()){throw new IllegalArgumentException("kind and documentId are required");}
        documentId = documentId.trim(); mediaType = mediaType == null ? "application/json" : mediaType.trim();
        content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
        sha256 = sha256 == null ? "" : sha256.trim(); metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
    /** Descripción: Ejecuta la operación «content» correspondiente a la responsabilidad de CanonicalProjectionRecoveredArtifact. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public byte[] content(){return Arrays.copyOf(content, content.length);}
}
