package com.example.editor.integration.compiler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.integration.IntegrationDependencyChange;
import com.example.editor.integration.IntegrationDiagnostic;
import com.example.editor.integration.IntegrationDiagnosticSeverity;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.IntegrationVerificationStep;

/** Resultado completo y todavía no aplicado de una compilación tecnológica. */
public record TechnologyCompilationResult(IntegrationTechnology technology, List<TechnologyGeneratedFile> files,
        List<IntegrationDependencyChange> dependencies, List<String> installationSteps, List<String> rollbackSteps,
        List<IntegrationVerificationStep> verificationSteps, List<IntegrationDiagnostic> diagnostics,
        Map<String, String> metadata) {
    public TechnologyCompilationResult {
        if(technology == null){throw new IllegalArgumentException("technology is required");}
        files = files == null ? List.of() : List.copyOf(files);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        installationSteps = installationSteps == null ? List.of() : List.copyOf(installationSteps);
        rollbackSteps = rollbackSteps == null ? List.of() : List.copyOf(rollbackSteps);
        verificationSteps = verificationSteps == null ? List.of() : List.copyOf(verificationSteps);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        metadata = metadata == null || metadata.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
    /** Descripción: Ejecuta la operación «valid» correspondiente a la responsabilidad de TechnologyCompilationResult. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean valid(){return diagnostics.stream().noneMatch(value -> value.severity() == IntegrationDiagnosticSeverity.ERROR);}
}
