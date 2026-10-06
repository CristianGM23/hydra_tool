package com.example.editor.event;
/** Componente que publicó un evento. */
public record EventSource(String component, String module, String instanceId, String version) {
    public EventSource {
        component = requireText(component, "component");
        module = normalize(module);
        instanceId = normalize(instanceId);
        version = normalize(version);
    }
    /** Descripción: Valida las condiciones necesarias para «requireText» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String requireText(String value, String field) {
        if(value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de EventSource. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
