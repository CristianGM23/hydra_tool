package com.example.editor.metamodel.importing.sql;

import java.util.List;

/** Validation result kept separate from parsing so semantic errors stay actionable and testable. */
public record SqlDdlValidationResult(List<SqlDdlAst.Diagnostic> diagnostics) {
    public SqlDdlValidationResult { diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics); }
    /** Descripción: Evalúa la condición «hasErrors» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasErrors() { return diagnostics.stream().anyMatch(item -> item.severity() == SqlDdlAst.Severity.ERROR); }
}
