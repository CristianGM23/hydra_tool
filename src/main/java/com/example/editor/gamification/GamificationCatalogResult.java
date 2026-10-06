package com.example.editor.gamification;

import java.util.List;

/** Fault-tolerant gamification catalog used by discovery and readiness screens. */
public record GamificationCatalogResult(List<GamificationSummary> summaries, List<String> diagnostics) {
    public GamificationCatalogResult {
        summaries = summaries == null ? List.of() : List.copyOf(summaries);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de GamificationCatalogResult. Entradas: diagnostic: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static GamificationCatalogResult empty(String diagnostic) {
        return new GamificationCatalogResult(List.of(), diagnostic == null || diagnostic.isBlank()
                ? List.of() : List.of(diagnostic));
    }
}
