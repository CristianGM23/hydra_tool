package com.example.editor.definition;
import java.util.List;
/** Resultado de comprobar una definición de editor. */
public record EditorDefinitionCheckReport(String editorDefinitionId, List<EditorDefinitionDiagnostic> diagnostics) {
    public EditorDefinitionCheckReport { diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics); }
    /** Descripción: Ejecuta la operación «valid» correspondiente a la responsabilidad de EditorDefinitionCheckReport. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean valid() { return diagnostics.stream().noneMatch(value -> value.severity() == EditorDefinitionDiagnosticSeverity.ERROR); }
}
