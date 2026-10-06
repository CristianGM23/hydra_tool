package com.example.editor.validation.orchestration;

import java.util.List;
import com.example.editor.validation.runtime.ModelValidationDiagnostic;

/** Diferencia estable entre el informe anterior y el informe consolidado actual. */
public record ValidationReportDelta(List<ModelValidationDiagnostic> created, List<ModelValidationDiagnostic> unchanged,
        List<ModelValidationDiagnostic> resolved) {
    public ValidationReportDelta {
        created = created == null ? List.of() : List.copyOf(created);
        unchanged = unchanged == null ? List.of() : List.copyOf(unchanged);
        resolved = resolved == null ? List.of() : List.copyOf(resolved);
    }
    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de ValidationReportDelta. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static ValidationReportDelta empty() { return new ValidationReportDelta(List.of(), List.of(), List.of()); }
}
