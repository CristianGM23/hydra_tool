package com.example.editor.metamodel.importing.updatesite;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Métricas acotadas de una ejecución del analizador p2, incluida cada etapa determinista. */
public record UpdateSiteAnalysisPerformance(boolean cacheHit, long totalMillis,
        long repositoryBytes, int repositoryFiles, int cacheEntries,
        Map<String, Long> stageMillis) {
    public UpdateSiteAnalysisPerformance {
        totalMillis = Math.max(0, totalMillis);
        repositoryBytes = Math.max(0, repositoryBytes);
        repositoryFiles = Math.max(0, repositoryFiles);
        cacheEntries = Math.max(0, cacheEntries);
        stageMillis = stageMillis == null || stageMillis.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(stageMillis));
    }

    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de UpdateSiteAnalysisPerformance. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static UpdateSiteAnalysisPerformance empty() {
        return new UpdateSiteAnalysisPerformance(false, 0, 0, 0, 0, Map.of());
    }
}
