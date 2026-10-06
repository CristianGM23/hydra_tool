package com.example.editor.integration.roundtrip.resolution;

import java.util.Arrays;
import java.util.Objects;

/** One ordered write action. KEEP_NATIVE and KEEP_MANUAL intentionally produce no action. */
public record RoundTripResolutionAction(String path, RoundTripResolutionActionKind kind,
        String mediaType, byte[] generatedContent, String conflictKey) {
    public RoundTripResolutionAction {
        path = safePath(path);
        kind = Objects.requireNonNull(kind, "kind");
        mediaType = mediaType == null || mediaType.isBlank() ? "application/octet-stream" : mediaType.trim();
        generatedContent = generatedContent == null ? new byte[0] : Arrays.copyOf(generatedContent, generatedContent.length);
        conflictKey = conflictKey == null ? "" : conflictKey.trim();
    }
    /** Descripción: Crea o prepara el resultado de «generatedContent» dentro de RoundTripResolutionAction. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public byte[] generatedContent(){return Arrays.copyOf(generatedContent, generatedContent.length);}
    /** Descripción: Ejecuta la operación «safePath» correspondiente a la responsabilidad de RoundTripResolutionAction. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String safePath(String value){
        if(value == null || value.isBlank()){throw new IllegalArgumentException("path is required");}
        String normalized = value.replace('\\', '/');
        if(normalized.startsWith("/") || normalized.contains("../") || normalized.equals("..")){
            throw new IllegalArgumentException("path must be a safe relative path");
        }
        return normalized;
    }
}
