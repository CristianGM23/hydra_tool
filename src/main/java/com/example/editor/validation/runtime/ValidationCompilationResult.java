package com.example.editor.validation.runtime;

import java.util.List;

/** Resultado del compilador; el plan puede conservar reglas válidas aunque otras estén rotas. */
public record ValidationCompilationResult(ExecutableValidationPlan plan, List<ValidationCompilationDiagnostic> diagnostics, boolean cacheHit) {
    public ValidationCompilationResult {
        if(plan == null) throw new IllegalArgumentException("plan must not be null");
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
    /** Descripción: Evalúa la condición «hasErrors» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasErrors() { return diagnostics.stream().anyMatch(item -> item.severity() == ValidationCompilationSeverity.ERROR); }
}
