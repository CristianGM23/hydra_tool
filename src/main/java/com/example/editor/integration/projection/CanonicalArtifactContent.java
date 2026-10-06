package com.example.editor.integration.projection;
import java.util.Arrays;
import com.example.editor.integration.IntegrationArtifactKind;
/** Documento canónico serializado que puede consumir un generador o importador externo. */
public record CanonicalArtifactContent(IntegrationArtifactKind kind, String documentId, String mediaType, byte[] content) {
    public CanonicalArtifactContent {
        if(kind == null || documentId == null || documentId.isBlank()) throw new IllegalArgumentException("artifact kind and documentId are required");
        documentId = documentId.trim(); mediaType = mediaType == null || mediaType.isBlank() ? "application/json" : mediaType.trim();
        content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
    }
    /** Descripción: Ejecuta la operación «content» correspondiente a la responsabilidad de CanonicalArtifactContent. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public byte[] content() { return Arrays.copyOf(content, content.length); }
}
