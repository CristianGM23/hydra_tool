package com.example.editor.definition.runtime;

/** Métricas acotadas y no sensibles del compilador runtime. */
public record EditorRuntimeCacheMetrics(int size, int maximumSize, long hits, long misses,
        long compilations, long totalCompilationNanos) {
    /** Descripción: Ejecuta la operación «averageCompilationMillis» correspondiente a la responsabilidad de EditorRuntimeCacheMetrics. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public double averageCompilationMillis() {
        return compilations == 0 ? 0D : totalCompilationNanos / 1_000_000D / compilations;
    }
}
