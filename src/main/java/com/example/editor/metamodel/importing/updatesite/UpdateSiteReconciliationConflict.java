package com.example.editor.metamodel.importing.updatesite;

import java.util.Map;

/** Discrepancia por elemento entre dos o más fuentes del mismo update site. */
public record UpdateSiteReconciliationConflict(String elementKey, String elementKind, String displayName,
        Map<String, String> candidateSignatures, String recommendedCandidateId, String explanation,
        boolean blocking) {
    public UpdateSiteReconciliationConflict {
        elementKey = text(elementKey);
        elementKind = text(elementKind);
        displayName = text(displayName);
        candidateSignatures = candidateSignatures == null ? Map.of() : Map.copyOf(candidateSignatures);
        recommendedCandidateId = text(recommendedCandidateId);
        explanation = text(explanation);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteReconciliationConflict. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
