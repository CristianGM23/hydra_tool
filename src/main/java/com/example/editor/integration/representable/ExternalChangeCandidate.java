package com.example.editor.integration.representable;

import java.util.Map;
import java.util.Objects;

/** Technology-specific change candidate discovered outside HYDRA. */
public record ExternalChangeCandidate(String externalId, ExternalChangeKind changeKind,
        String nativeKind, String qualifiedName, Map<String, Object> properties) {
    public ExternalChangeCandidate {
        externalId = required(externalId, "externalId");
        changeKind = changeKind == null ? ExternalChangeKind.UPDATE : changeKind;
        nativeKind = required(nativeKind, "nativeKind");
        qualifiedName = Objects.requireNonNullElse(qualifiedName, externalId).trim();
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }
    /** Descripción: Valida las condiciones necesarias para «required» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String required(String value, String field){
        String result = Objects.requireNonNullElse(value, "").trim();
        if(result.isBlank()){throw new IllegalArgumentException(field + " must not be blank");}
        return result;
    }
}
