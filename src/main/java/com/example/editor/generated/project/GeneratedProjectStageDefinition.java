package com.example.editor.generated.project;

import java.util.List;
import java.util.Map;

/** Server-owned bounded command definition for one lifecycle stage. */
public record GeneratedProjectStageDefinition(GeneratedProjectStage stage, String description,
        List<String> windowsCommand, List<String> unixCommand, int timeoutSeconds,
        boolean optional, Map<String, String> environment) {
    public GeneratedProjectStageDefinition {
        if(stage == null){throw new IllegalArgumentException("stage is required");}
        description = description == null ? stage.name() : description.trim();
        windowsCommand = windowsCommand == null ? List.of() : List.copyOf(windowsCommand);
        unixCommand = unixCommand == null ? List.of() : List.copyOf(unixCommand);
        timeoutSeconds = Math.max(1, Math.min(timeoutSeconds <= 0 ? 120 : timeoutSeconds, 3600));
        environment = environment == null ? Map.of() : Map.copyOf(environment);
    }

    /** Descripción: Ejecuta la operación «command» correspondiente a la responsabilidad de GeneratedProjectStageDefinition. Entradas: windows: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> command(boolean windows){
        return windows ? windowsCommand : unixCommand;
    }
}
