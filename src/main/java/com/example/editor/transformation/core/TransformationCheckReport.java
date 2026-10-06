package com.example.editor.transformation.core;

import java.util.List;

public record TransformationCheckReport(
        String transformationId,
        List<TransformationDiagnostic> diagnostics) {

    public TransformationCheckReport {
        transformationId = transformationId == null ? "" : transformationId;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    /** Descripción: Ejecuta la operación «valid» correspondiente a la responsabilidad de TransformationCheckReport. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean valid() {
        return diagnostics.stream().noneMatch(item -> item.severity() == TransformationDiagnosticSeverity.ERROR);
    }

    /** Descripción: Ejecuta la operación «errorCount» correspondiente a la responsabilidad de TransformationCheckReport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long errorCount() {
        return diagnostics.stream().filter(item -> item.severity() == TransformationDiagnosticSeverity.ERROR).count();
    }

    /** Descripción: Ejecuta la operación «warningCount» correspondiente a la responsabilidad de TransformationCheckReport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long warningCount() {
        return diagnostics.stream().filter(item -> item.severity() == TransformationDiagnosticSeverity.WARNING).count();
    }
}
