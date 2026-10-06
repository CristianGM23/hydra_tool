package com.example.editor.metamodel.importing.updatesite;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Solicitud de preview o aplicación de una fusión sobre un editor canónico existente. */
public record UpdateSiteLayerMergeRequest(String targetMetamodelId, String targetEditorId,
        String expectedEtag, String candidateId, boolean strict, boolean persist,
        UpdateSiteLayerSelection selection, UpdateSiteMergeDecision defaultDecision,
        Map<String, UpdateSiteMergeDecision> decisions) {
    public UpdateSiteLayerMergeRequest {
        targetMetamodelId = text(targetMetamodelId);
        targetEditorId = text(targetEditorId);
        expectedEtag = text(expectedEtag);
        candidateId = text(candidateId);
        selection = selection == null ? UpdateSiteLayerSelection.all() : selection;
        defaultDecision = defaultDecision == null ? UpdateSiteMergeDecision.KEEP_EXISTING : defaultDecision;
        decisions = decisions == null || decisions.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(decisions));
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteLayerMergeRequest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
