package com.example.editor.grammar.runtime;

import java.time.Instant;

/** Bounded, ephemeral progress snapshot for one grammar compilation request. */
public record GrammarCompilationProgress(
        String requestId,
        String grammarId,
        String stage,
        String status,
        int completedStages,
        int totalStages,
        String detail,
        Instant updatedAt) {
    public GrammarCompilationProgress {
        requestId = text(requestId);
        grammarId = text(grammarId);
        stage = text(stage);
        status = text(status);
        totalStages = Math.max(1, totalStages);
        completedStages = Math.min(totalStages, Math.max(0, completedStages));
        detail = text(detail);
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarCompilationProgress. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
}
