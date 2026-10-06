package com.example.editor.integration.verification;

import java.util.List;

/** Resultado acotado y auditable de una comprobación estructural o nativa. */
public record TechnologyValidationStepResult(String id, String description,
        TechnologyValidationStepStatus status, List<String> command, Integer exitCode,
        long durationMillis, String stdout, String stderr, List<String> diagnostics) {
    public TechnologyValidationStepResult {
        if(id == null || id.isBlank()){throw new IllegalArgumentException("validation step id is required");}
        id = id.trim();
        description = description == null ? "" : description.trim();
        status = status == null ? TechnologyValidationStepStatus.FAILED : status;
        command = command == null ? List.of() : List.copyOf(command);
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    /** Descripción: Ejecuta la operación «successful» correspondiente a la responsabilidad de TechnologyValidationStepResult. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean successful(){return status == TechnologyValidationStepStatus.PASSED;}
}
