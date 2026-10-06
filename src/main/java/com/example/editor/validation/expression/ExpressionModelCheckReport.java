package com.example.editor.validation.expression;

import java.util.List;

import com.example.editor.semantic.expression.ExpressionCheckResult;
import com.example.editor.semantic.expression.ExpressionDiagnostic;
import com.example.editor.semantic.expression.ExpressionDiagnosticSeverity;

/** Resultado completo de validar y tipar un documento de expresiones. */
public record ExpressionModelCheckReport(String expressionModelId, List<ExpressionDiagnostic> diagnostics, List<ExpressionCheckResult> expressions) {
    public ExpressionModelCheckReport {
        expressionModelId = expressionModelId == null ? "" : expressionModelId;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        expressions = expressions == null ? List.of() : List.copyOf(expressions);
    }

    /** Descripción: Evalúa la condición «hasErrors» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasErrors() {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == ExpressionDiagnosticSeverity.ERROR)
                || expressions.stream().anyMatch(ExpressionCheckResult::hasErrors);
    }
}
