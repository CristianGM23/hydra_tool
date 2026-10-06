package com.example.editor.integration.verification;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import com.example.editor.integration.IntegrationTechnology;

/** Informe final de aceptación de un proyecto generado para una tecnología destino. */
public record TechnologyProjectValidationReport(IntegrationTechnology technology,
        TechnologyValidationStatus status, Path workspaceDirectory,
        List<TechnologyValidationStepResult> steps, Map<String, String> metadata) {
    public TechnologyProjectValidationReport {
        if(technology == null){throw new IllegalArgumentException("technology is required");}
        status = status == null ? TechnologyValidationStatus.FAILED : status;
        steps = steps == null ? List.of() : List.copyOf(steps);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Descripción: Ejecuta la operación «passed» correspondiente a la responsabilidad de TechnologyProjectValidationReport. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean passed(){return status == TechnologyValidationStatus.PASSED;}
}
