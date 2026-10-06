package com.example.editor.event.flow;

import java.util.List;

/** Resultado de comprobar todos los flujos de un documento. */
public record EventFlowCheckReport(String eventFlowModelId, List<FlowDiagnostic> diagnostics) {
    public EventFlowCheckReport { diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics); }
    /** Descripción: Ejecuta la operación «valid» correspondiente a la responsabilidad de EventFlowCheckReport. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean valid() { return diagnostics.stream().noneMatch(diagnostic -> diagnostic.severity() == FlowDiagnosticSeverity.ERROR); }
}
