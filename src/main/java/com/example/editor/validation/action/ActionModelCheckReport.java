package com.example.editor.validation.action;
import java.util.List;
public record ActionModelCheckReport(String actionModelId, List<ActionDiagnostic> diagnostics, List<ActionCheckResult> actions) {
    public ActionModelCheckReport { actionModelId = actionModelId == null ? "" : actionModelId; diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics); actions = actions == null ? List.of() : List.copyOf(actions); }
    /** Descripción: Evalúa la condición «hasErrors» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasErrors() { return diagnostics.stream().anyMatch(item -> item.severity() == ActionDiagnosticSeverity.ERROR) || actions.stream().anyMatch(ActionCheckResult::hasErrors); }
}
