package com.example.editor.gamification;

import java.util.List;
public record GamificationCheckReport(String definitionModelId, String bindingModelId, List<GamificationDiagnostic> diagnostics) {
    public GamificationCheckReport { diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics); }
    /** Descripción: Ejecuta la operación «valid» correspondiente a la responsabilidad de GamificationCheckReport. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean valid() { return diagnostics.stream().noneMatch(item -> item.severity() == GamificationDiagnosticSeverity.ERROR); }
}
