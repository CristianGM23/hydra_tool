package com.example.editor.portability.merge;

/** Cross-layer reference discovered while validating a semantic merge. */
public record CapabilitySemanticMergeDependency(String sourceKey, String targetReference,
        boolean resolved, String explanation) {
    public CapabilitySemanticMergeDependency {
        sourceKey = text(sourceKey); targetReference = text(targetReference); explanation = text(explanation);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de CapabilitySemanticMergeDependency. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value == null ? "" : value.trim();}
}
