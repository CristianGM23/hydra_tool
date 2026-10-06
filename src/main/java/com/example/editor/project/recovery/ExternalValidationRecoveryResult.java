package com.example.editor.project.recovery;

import java.util.List;

record ExternalValidationRecoveryResult(int rulesImported, int quickFixesImported, int reviewOnlyQuickFixes,
        List<String> sources, List<String> diagnostics) {
    ExternalValidationRecoveryResult {
        sources = sources == null ? List.of() : List.copyOf(sources);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de ExternalValidationRecoveryResult. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ static ExternalValidationRecoveryResult empty() { return new ExternalValidationRecoveryResult(0,0,0,List.of(),List.of()); }
}
