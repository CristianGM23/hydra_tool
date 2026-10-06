package com.example.editor.portability.merge;

/** Semantic conflict detected even when identifiers differ. */
public record CapabilitySemanticMergeConflict(String key, String type, String severity,
        String existingFingerprint, String recoveredFingerprint, String explanation) {
    public CapabilitySemanticMergeConflict {
        key = text(key); type = text(type); severity = severity == null || severity.isBlank() ? "WARNING" : severity.trim();
        existingFingerprint = text(existingFingerprint); recoveredFingerprint = text(recoveredFingerprint);
        explanation = text(explanation);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de CapabilitySemanticMergeConflict. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value == null ? "" : value.trim();}
}
