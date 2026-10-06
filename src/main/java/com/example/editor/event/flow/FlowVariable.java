package com.example.editor.event.flow;

import com.example.editor.validation.contract.StableIdentifierPolicy;

/** Variable declarada por un flujo y disponible durante su ejecución. */
public record FlowVariable(String id, String name, String type, Object initialValue) {
    public FlowVariable {
        id = StableIdentifierPolicy.requireElementId(id, "flowVariable.id");
        name = requireText(name, "flowVariable.name");
        type = type == null || type.isBlank() ? "ANY" : type.trim();
    }
    /** Descripción: Valida las condiciones necesarias para «requireText» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String requireText(String value, String field) {
        if(value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
