package com.example.editor.integration.projection;
import java.util.List;
import com.example.editor.integration.IntegrationDiagnostic;
/** Resultado en memoria de una proyección antes de escribir archivos. */
public record ProjectionGenerationResult(List<GeneratedProjectionArtifact> artifacts,
        List<IntegrationDiagnostic> diagnostics) {
    public ProjectionGenerationResult {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
    /** Descripción: Ejecuta la operación «valid» correspondiente a la responsabilidad de ProjectionGenerationResult. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean valid() { return diagnostics.stream().noneMatch(value -> value.severity().name().equals("ERROR")); }
}
