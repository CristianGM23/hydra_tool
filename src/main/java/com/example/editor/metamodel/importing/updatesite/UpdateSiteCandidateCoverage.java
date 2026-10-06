package com.example.editor.metamodel.importing.updatesite;

/** Cobertura de un candidato respecto a la unión de elementos recuperables. */
public record UpdateSiteCandidateCoverage(String candidateId, UpdateSiteConfidence confidence,
        int elements, int uniqueElements, int conflictingElements, double coveragePercent,
        String recommendation) {
    public UpdateSiteCandidateCoverage {
        candidateId = candidateId == null ? "" : candidateId;
        confidence = confidence == null ? UpdateSiteConfidence.INFERRED : confidence;
        elements = Math.max(0, elements);
        uniqueElements = Math.max(0, uniqueElements);
        conflictingElements = Math.max(0, conflictingElements);
        coveragePercent = Math.max(0, Math.min(100, coveragePercent));
        recommendation = recommendation == null ? "" : recommendation;
    }
}
