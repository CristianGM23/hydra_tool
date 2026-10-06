package com.example.editor.generated.project;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Registered generated project referencing a safe server-owned lifecycle profile. */
public record GeneratedProjectDefinition(String id, String name, String profileKey,
        String relativeDirectory, Instant registeredAt, Map<String, String> metadata) {
    public GeneratedProjectDefinition {
        id = required(id, "id");
        name = Objects.requireNonNullElse(name, id).trim();
        profileKey = required(profileKey, "profileKey");
        relativeDirectory = required(relativeDirectory, "relativeDirectory").replace('\\', '/');
        registeredAt = registeredAt == null ? Instant.now() : registeredAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
    /** Descripción: Valida las condiciones necesarias para «required» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String required(String value, String field){
        String result = Objects.requireNonNullElse(value, "").trim();
        if(result.isBlank()){throw new IllegalArgumentException(field + " must not be blank");}
        return result;
    }
}
