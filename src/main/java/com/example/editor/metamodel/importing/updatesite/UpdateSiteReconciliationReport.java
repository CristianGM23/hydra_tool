package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Comparación de candidatos y estado de una posible fusión. */
public record UpdateSiteReconciliationReport(List<UpdateSiteCandidateCoverage> candidates,
        List<UpdateSiteReconciliationConflict> conflicts, int agreedElements, int unionElements,
        double agreementPercent, String recommendedCandidateId, boolean mergeReady,
        List<String> diagnostics) {
    public UpdateSiteReconciliationReport {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        agreedElements = Math.max(0, agreedElements);
        unionElements = Math.max(0, unionElements);
        agreementPercent = Math.max(0, Math.min(100, agreementPercent));
        recommendedCandidateId = recommendedCandidateId == null ? "" : recommendedCandidateId;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de UpdateSiteReconciliationReport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static UpdateSiteReconciliationReport empty() {
        return new UpdateSiteReconciliationReport(List.of(), List.of(), 0, 0, 0, "", false, List.of());
    }
}
