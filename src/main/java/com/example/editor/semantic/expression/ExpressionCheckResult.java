package com.example.editor.semantic.expression;

import java.util.List;

/** Resultado de comprobar una expresión concreta. */
public record ExpressionCheckResult(String expressionId, ExpressionValueType inferredType, List<ExpressionDiagnostic> diagnostics, List<ExpressionDependency> dependencies) {
    public ExpressionCheckResult {
        expressionId = expressionId == null ? "" : expressionId;
        inferredType = inferredType == null ? ExpressionValueType.scalar(ExpressionValueKind.ANY) : inferredType;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    /** Descripción: Evalúa la condición «hasErrors» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasErrors() {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == ExpressionDiagnosticSeverity.ERROR);
    }
}
