package com.example.editor.grammar.runtime;

import java.util.List;
import java.util.Map;

/** Evidence for one compilation stage. Snapshots retain it for diagnostics and reproducibility. */
public record GrammarCompilationStageResult(GrammarCompilationStage stage, String status, long durationMillis,
        List<String> diagnostics, Map<String, String> metadata) {
    public GrammarCompilationStageResult {
        if (stage == null) throw new IllegalArgumentException("Compilation stage is required");
        status = status == null || status.isBlank() ? "FAILED" : status.trim().toUpperCase();
        durationMillis = Math.max(0, durationMillis);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
    /** Descripción: Ejecuta la operación «successful» correspondiente a la responsabilidad de GrammarCompilationStageResult. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean successful() { return "SUCCESS".equals(status) || "SKIPPED".equals(status); }
}
