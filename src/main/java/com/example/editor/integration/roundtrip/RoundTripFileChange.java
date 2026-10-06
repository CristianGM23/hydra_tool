package com.example.editor.integration.roundtrip;

/** Cambio detectado para un archivo nativo administrado. */
public record RoundTripFileChange(String path, RoundTripChangeKind kind,
        String previousSha256, String currentSha256, String generatedSha256,
        boolean selectable, String message) {
    public RoundTripFileChange {
        if(path == null || path.isBlank()){throw new IllegalArgumentException("path is required");}
        path = path.replace('\\', '/').trim();
        previousSha256 = normalize(previousSha256); currentSha256 = normalize(currentSha256); generatedSha256 = normalize(generatedSha256);
        message = normalize(message);
    }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de RoundTripFileChange. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value){return value == null ? "" : value.trim();}
}
