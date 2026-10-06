package com.example.editor.validation.validation;
import java.util.List;
public record ValidationModelCheckReport(String validationModelId, List<ValidationModelDiagnostic> diagnostics, List<ValidationRuleCheckResult> rules) {
    public ValidationModelCheckReport {
        validationModelId = validationModelId == null ? "" : validationModelId;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
    /** Descripción: Evalúa la condición «hasErrors» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasErrors() { return diagnostics.stream().anyMatch(item -> item.severity() == ValidationModelDiagnosticSeverity.ERROR) || rules.stream().anyMatch(ValidationRuleCheckResult::hasErrors); }
}
