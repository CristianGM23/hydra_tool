package com.example.editor.integration.compatibility;

import java.util.List;
import java.util.Objects;

/** One deterministic native command that contributes evidence to a compatibility probe. */
public record NativeToolchainProbeCommand(String id, String description, List<String> command,
        int expectedExitCode, int timeoutSeconds, boolean required) {
    public NativeToolchainProbeCommand {
        id = requireText(id, "id");
        description = requireText(description, "description");
        command = List.copyOf(Objects.requireNonNull(command, "command"));
        if(command.isEmpty()){throw new IllegalArgumentException("command must not be empty");}
        if(timeoutSeconds < 1){throw new IllegalArgumentException("timeoutSeconds must be greater than zero");}
    }
    /** Descripción: Valida las condiciones necesarias para «requireText» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String requireText(String value, String field){
        Objects.requireNonNull(value, field);
        if(value.isBlank()){throw new IllegalArgumentException(field + " must not be blank");}
        return value.trim();
    }
}
