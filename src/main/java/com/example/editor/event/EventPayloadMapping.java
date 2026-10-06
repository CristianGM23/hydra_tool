package com.example.editor.event;
import com.example.editor.validation.contract.StableIdentifierPolicy;
/** Mapea un valor del evento de origen al payload del evento derivado. */
public record EventPayloadMapping(String targetFieldId, String sourcePath, String expressionId, Object fixedValue) {
    public EventPayloadMapping {
        targetFieldId = StableIdentifierPolicy.requireElementId(targetFieldId, "targetFieldId");
        sourcePath = sourcePath == null ? "" : sourcePath.trim();
        expressionId = normalizeElementId(expressionId, "expressionId");
        int sources = (sourcePath.isBlank() ? 0 : 1) + (expressionId.isBlank() ? 0 : 1) + (fixedValue == null ? 0 : 1);
        if(sources != 1) throw new IllegalArgumentException("Exactly one mapping source must be configured");
    }
    /** Descripción: Ejecuta la operación «normalizeElementId» correspondiente a la responsabilidad de EventPayloadMapping. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalizeElementId(String value, String field) { if(value == null || value.isBlank()) return ""; return StableIdentifierPolicy.requireElementId(value, field); }
}
