package com.example.editor.integration.compatibility;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.util.Locale;

/** Versioned native toolchain probe definition. */
public record NativeToolchainProbeDefinition(String technology, String version, Path workingDirectory,
        List<NativeToolchainProbeCommand> commands, Map<String, String> environment) {
    public NativeToolchainProbeDefinition {
        technology = requireText(technology, "technology");
        version = requireText(version, "version");
        workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory").toAbsolutePath().normalize();
        commands = Objects.requireNonNull(commands, "commands").stream()
                .sorted(Comparator.comparing(NativeToolchainProbeCommand::id)).toList();
        Set<String> commandIds = new HashSet<>();
        for(NativeToolchainProbeCommand command : commands){
            if(!commandIds.add(Objects.requireNonNull(command, "command").id())){
                throw new IllegalArgumentException("Duplicate probe command: " + command.id());
            }
        }
        environment = environment == null ? Map.of() : Map.copyOf(environment);
    }
    /** Descripción: Ejecuta la operación «key» correspondiente a la responsabilidad de NativeToolchainProbeDefinition. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String key(){return technology.toLowerCase(Locale.ROOT) + ":" + version.toLowerCase(Locale.ROOT);}
    /** Descripción: Valida las condiciones necesarias para «requireText» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String requireText(String value, String field){
        Objects.requireNonNull(value, field);
        if(value.isBlank()){throw new IllegalArgumentException(field + " must not be blank");}
        return value.trim();
    }
}
