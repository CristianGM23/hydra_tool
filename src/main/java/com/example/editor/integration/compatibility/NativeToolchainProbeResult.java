package com.example.editor.integration.compatibility;

import java.util.List;
import java.util.Objects;
import java.util.Locale;

/** Immutable evidence returned by one native toolchain probe. */
public record NativeToolchainProbeResult(
        String technology,
        String version,
        CompatibilityStatus status,
        String message,
        List<String> evidence,
        long durationMillis) {

    public NativeToolchainProbeResult {
        technology = requireText(technology, "technology");
        version = requireText(version, "version");
        status = Objects.requireNonNull(status, "status");
        message = requireText(message, "message");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        if(durationMillis < 0){
            throw new IllegalArgumentException("durationMillis must be greater than or equal to zero");
        }
    }

    /** Descripción: Ejecuta la operación «key» correspondiente a la responsabilidad de NativeToolchainProbeResult. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String key(){
        return technology.toLowerCase(Locale.ROOT) + ":" + version.toLowerCase(Locale.ROOT);
    }

    /** Descripción: Valida las condiciones necesarias para «requireText» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String requireText(String value, String field){
        Objects.requireNonNull(value, field);
        if(value.isBlank()){
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
