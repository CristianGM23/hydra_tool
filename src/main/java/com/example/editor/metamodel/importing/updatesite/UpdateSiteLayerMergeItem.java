package com.example.editor.metamodel.importing.updatesite;

/** Diferencia individual mostrada antes de fusionar una capa recuperada. */
public record UpdateSiteLayerMergeItem(String key, String kind, String displayName,
        boolean existingPresent, boolean recoveredPresent, boolean conflict,
        UpdateSiteMergeDecision recommendedDecision, UpdateSiteMergeDecision selectedDecision,
        String explanation) {
    public UpdateSiteLayerMergeItem {
        key = text(key); kind = text(kind); displayName = text(displayName); explanation = text(explanation);
        recommendedDecision = recommendedDecision == null ? UpdateSiteMergeDecision.USE_RECOVERED : recommendedDecision;
        selectedDecision = selectedDecision == null ? recommendedDecision : selectedDecision;
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteLayerMergeItem. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
