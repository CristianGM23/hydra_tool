package com.example.editor.generated.project;

import java.util.List;
import java.util.Objects;

/** Configurable lifecycle profile for one generated project technology. */
public record GeneratedProjectRuntimeProfile(String key, String name, String technology,
        String description, List<GeneratedProjectStageDefinition> stages) {
    public GeneratedProjectRuntimeProfile {
        key = required(key, "key");
        name = Objects.requireNonNullElse(name, key).trim();
        technology = Objects.requireNonNullElse(technology, "GENERIC").trim().toUpperCase();
        description = Objects.requireNonNullElse(description, "").trim();
        stages = stages == null ? List.of() : stages.stream()
                .sorted(java.util.Comparator.comparing(item -> item.stage().ordinal())).toList();
    }
    /** Descripción: Valida las condiciones necesarias para «required» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String required(String value, String field){
        String result = Objects.requireNonNullElse(value, "").trim();
        if(result.isBlank()){throw new IllegalArgumentException(field + " must not be blank");}
        return result;
    }
}
