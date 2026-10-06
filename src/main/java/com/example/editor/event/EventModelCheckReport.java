package com.example.editor.event;
import java.util.List;
/** Resultado consolidado de comprobar definiciones y bindings. */
public record EventModelCheckReport(String definitionModelId, String bindingModelId, List<EventDiagnostic> diagnostics) {
    public EventModelCheckReport { diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics); }
    /** Descripción: Ejecuta la operación «valid» correspondiente a la responsabilidad de EventModelCheckReport. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean valid() { return diagnostics.stream().noneMatch(diagnostic -> diagnostic.severity() == EventDiagnosticSeverity.ERROR); }
}
