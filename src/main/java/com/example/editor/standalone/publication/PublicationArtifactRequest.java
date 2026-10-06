package com.example.editor.standalone.publication;

import java.util.Objects;

/** Relative artifact selected for one platform publication. */
public record PublicationArtifactRequest(String platform, String relativePath, String mediaType) {
    public PublicationArtifactRequest {
        platform = required(platform, "platform").toLowerCase();
        relativePath = required(relativePath, "relativePath").replace('\\', '/');
        mediaType = Objects.requireNonNullElse(mediaType, "application/octet-stream").trim();
    }
    /** Descripción: Valida las condiciones necesarias para «required» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String required(String value, String field){
        String result = Objects.requireNonNullElse(value, "").trim();
        if(result.isBlank()){throw new IllegalArgumentException(field + " must not be blank");}
        return result;
    }
}
