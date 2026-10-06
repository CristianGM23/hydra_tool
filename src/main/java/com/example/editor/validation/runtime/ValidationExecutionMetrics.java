package com.example.editor.validation.runtime;
/** Métricas mínimas del contrato ValidationReport 1.0. */
public record ValidationExecutionMetrics(int evaluatedRules, int skippedRules, long durationMillis) {
    public ValidationExecutionMetrics {
        if(evaluatedRules < 0) evaluatedRules = 0;
        if(skippedRules < 0) skippedRules = 0;
        if(durationMillis < 0) durationMillis = 0;
    }
}
