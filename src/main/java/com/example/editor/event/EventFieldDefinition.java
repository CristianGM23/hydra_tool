package com.example.editor.event;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
/** Campo estructurado de un payload personalizado. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EventFieldDefinition(String id, String name, EventFieldType type, boolean required, boolean many, String enumTypeId, String objectTypeId, String description) {
    public EventFieldDefinition {
        id = StableIdentifierPolicy.requireElementId(id, "field.id");
        name = requireText(name, "field.name");
        if(type == null) throw new IllegalArgumentException("field.type must not be null");
        enumTypeId = normalizeElementId(enumTypeId, "field.enumTypeId");
        objectTypeId = normalizeElementId(objectTypeId, "field.objectTypeId");
        description = description == null ? "" : description.trim();
        if(type == EventFieldType.ENUM && enumTypeId.isBlank()) throw new IllegalArgumentException("ENUM fields require enumTypeId");
        if(type == EventFieldType.OBJECT && objectTypeId.isBlank()) throw new IllegalArgumentException("OBJECT fields require objectTypeId");
    }
    /** Descripción: Valida las condiciones necesarias para «requireText» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String requireText(String value, String field) { if(value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank"); return value.trim(); }
    /** Descripción: Ejecuta la operación «normalizeElementId» correspondiente a la responsabilidad de EventFieldDefinition. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalizeElementId(String value, String field) { if(value == null || value.isBlank()) return ""; return StableIdentifierPolicy.requireElementId(value, field); }
}
