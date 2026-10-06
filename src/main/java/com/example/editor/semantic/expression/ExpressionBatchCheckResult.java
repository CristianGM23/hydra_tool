package com.example.editor.semantic.expression;

import java.util.List;

/** Resultado neutral de validar un conjunto de expresiones contra un metamodelo. */
public record ExpressionBatchCheckResult(String ownerId, List<ExpressionDiagnostic> diagnostics, List<ExpressionCheckResult> expressions) {
    public ExpressionBatchCheckResult {
        ownerId = ownerId == null ? "" : ownerId;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        expressions = expressions == null ? List.of() : List.copyOf(expressions);
    }

    /** Descripción: Comprueba si el lote contiene errores globales o de alguna expresión. Entradas: ninguna. Salida: true cuando existe al menos un error. */
    /** Descripción: Evalúa la condición «hasErrors» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasErrors() {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == ExpressionDiagnosticSeverity.ERROR)
                || expressions.stream().anyMatch(ExpressionCheckResult::hasErrors);
    }
}
