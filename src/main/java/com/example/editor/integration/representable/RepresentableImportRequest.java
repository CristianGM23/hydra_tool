package com.example.editor.integration.representable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Configurable import request produced by a native analyzer or adapter. */
public record RepresentableImportRequest(String technology, String metamodelId, String strategy,
        List<ExternalChangeCandidate> changes, Map<String, String> options) {
    public RepresentableImportRequest {
        technology = required(technology, "technology").toUpperCase();
        metamodelId = required(metamodelId, "metamodelId");
        strategy = Objects.requireNonNullElse(strategy, "SAFE").trim().toUpperCase();
        changes = changes == null ? List.of() : List.copyOf(changes);
        options = options == null ? Map.of() : Map.copyOf(options);
    }
    /** Descripción: Valida las condiciones necesarias para «required» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String required(String value, String field){
        String result = Objects.requireNonNullElse(value, "").trim();
        if(result.isBlank()){throw new IllegalArgumentException(field + " must not be blank");}
        return result;
    }
}
