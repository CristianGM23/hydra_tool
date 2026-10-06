package com.example.editor.validation.action;
import java.util.List;
public record ActionCheckResult(String actionId, List<ActionDiagnostic> diagnostics) {
    public ActionCheckResult { actionId = actionId == null ? "" : actionId; diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics); }
    /** Descripción: Evalúa la condición «hasErrors» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasErrors() { return diagnostics.stream().anyMatch(item -> item.severity() == ActionDiagnosticSeverity.ERROR); }
}
