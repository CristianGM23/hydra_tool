package com.example.editor.integration.projection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
/** Archivo generado sin escribirlo todavía en el proyecto de destino. */
public record GeneratedProjectionArtifact(String relativePath, String mediaType, byte[] content,
        List<String> sourceElementIds, Map<String, String> metadata) {
    public GeneratedProjectionArtifact {
        if(relativePath == null || relativePath.isBlank()) throw new IllegalArgumentException("relativePath is required");
        relativePath = relativePath.trim(); mediaType = mediaType == null ? "application/octet-stream" : mediaType.trim();
        content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
        sourceElementIds = sourceElementIds == null ? List.of() : List.copyOf(sourceElementIds);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
    /** Descripción: Ejecuta la operación «content» correspondiente a la responsabilidad de GeneratedProjectionArtifact. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public byte[] content() { return Arrays.copyOf(content, content.length); }
    /** Descripción: Ejecuta la operación «equals» correspondiente a la responsabilidad de GeneratedProjectionArtifact. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ @Override public boolean equals(Object value) {
        if(this == value){return true;}
        if(!(value instanceof GeneratedProjectionArtifact other)){return false;}
        return relativePath.equals(other.relativePath) && mediaType.equals(other.mediaType)
                && Arrays.equals(content, other.content) && sourceElementIds.equals(other.sourceElementIds)
                && metadata.equals(other.metadata);
    }
    /** Descripción: Evalúa la condición «hashCode» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ @Override public int hashCode() {
        int result = Objects.hash(relativePath, mediaType, sourceElementIds, metadata);
        return 31 * result + Arrays.hashCode(content);
    }
}

