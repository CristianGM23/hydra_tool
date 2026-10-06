package com.example.editor.portability;

import java.util.List;

public record CapabilityPackageCheckReport(String packageId, List<CapabilityPackageDiagnostic> diagnostics) {
    public CapabilityPackageCheckReport {
        packageId = packageId == null ? "" : packageId;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    /** Descripción: Ejecuta la operación «valid» correspondiente a la responsabilidad de CapabilityPackageCheckReport. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean valid() {
        return diagnostics.stream().noneMatch(item -> item.severity() == CapabilityPackageDiagnosticSeverity.ERROR);
    }
}
