package com.example.editor.validation.validation;
import java.util.List;
public record ValidationRuleCheckResult(String ruleId, List<ValidationModelDiagnostic> diagnostics, List<ValidationDependency> inferredDependencies) {
    public ValidationRuleCheckResult {
        ruleId = ruleId == null ? "" : ruleId;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        inferredDependencies = inferredDependencies == null ? List.of() : List.copyOf(inferredDependencies);
    }
    /** Descripción: Evalúa la condición «hasErrors» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasErrors() { return diagnostics.stream().anyMatch(item -> item.severity() == ValidationModelDiagnosticSeverity.ERROR); }
}
