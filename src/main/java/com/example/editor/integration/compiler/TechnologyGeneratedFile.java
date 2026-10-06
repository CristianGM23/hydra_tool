package com.example.editor.integration.compiler;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.integration.InjectionFileOwnership;

/** Archivo generado por un adaptador tecnológico antes de escribirlo en el proyecto anfitrión. */
public record TechnologyGeneratedFile(String relativePath, String mediaType, byte[] content,
        InjectionFileOwnership ownership, boolean replaceable, List<String> sourceDocumentIds,
        Map<String, String> metadata) {
    public TechnologyGeneratedFile {
        if(relativePath == null || relativePath.isBlank()){throw new IllegalArgumentException("relativePath is required");}
        relativePath = normalizePath(relativePath);
        mediaType = mediaType == null || mediaType.isBlank() ? "application/octet-stream" : mediaType.trim();
        content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
        if(ownership == null){ownership = InjectionFileOwnership.GENERATED;}
        sourceDocumentIds = sourceDocumentIds == null ? List.of() : List.copyOf(sourceDocumentIds);
        metadata = metadata == null || metadata.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    /** Descripción: Ejecuta la operación «content» correspondiente a la responsabilidad de TechnologyGeneratedFile. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public byte[] content(){return Arrays.copyOf(content, content.length);}

    /** Descripción: Ejecuta la operación «normalizePath» correspondiente a la responsabilidad de TechnologyGeneratedFile. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalizePath(String value){
        String normalized = value == null ? "" : value.replace('\\', '/');
        if(normalized.isBlank() || normalized.startsWith("/") || normalized.startsWith("//") || normalized.matches("^[A-Za-z]:.*") || normalized.contains("//")){throw new TechnologyCompilationException("Unsafe generated path: " + value);}
        String[] segments = normalized.split("/", -1);
        for(String segment : segments){if(segment.isBlank() || segment.equals(".") || segment.equals("..")){throw new TechnologyCompilationException("Unsafe generated path: " + value);}}
        return String.join("/", segments);
    }
}
