package com.example.editor.metamodel.importing.updatesite;

import java.time.Instant;
import java.util.List;

import com.example.editor.metamodel.importing.MetamodelImportPreview;

/** Resultado completo del análisis previo de un update site. */
public record UpdateSiteImportAnalysis(String analysisId, Instant generatedAt,
        UpdateSiteRepositorySummary repository, List<UpdateSiteRecoveryCandidate> candidates,
        String selectedCandidateId, MetamodelImportPreview metamodelPreview,
        List<UpdateSiteElementProvenance> provenance, UpdateSiteRecoveredLayers recoveredLayers,
        boolean importable, List<String> diagnostics, UpdateSiteImportDashboard dashboard,
        List<UpdateSiteImportLogEntry> logs, List<UpdateSiteFileOutcome> fileOutcomes,
        List<UpdateSiteGrammarInspection> grammarInspections,
        UpdateSiteReconciliationReport reconciliation, UpdateSiteReleaseComparison releaseComparison,
        UpdateSiteSemanticRecovery semanticRecovery, UpdateSiteAnalysisPerformance performance) {
    public UpdateSiteImportAnalysis {
        analysisId = analysisId == null ? "" : analysisId;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        selectedCandidateId = selectedCandidateId == null ? "" : selectedCandidateId;
        provenance = provenance == null ? List.of() : List.copyOf(provenance);
        recoveredLayers = recoveredLayers == null ? UpdateSiteRecoveredLayers.empty() : recoveredLayers;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        dashboard = dashboard == null ? UpdateSiteImportDashboard.empty() : dashboard;
        logs = logs == null ? List.of() : List.copyOf(logs);
        fileOutcomes = fileOutcomes == null ? List.of() : List.copyOf(fileOutcomes);
        grammarInspections = grammarInspections == null ? List.of() : List.copyOf(grammarInspections);
        reconciliation = reconciliation == null ? UpdateSiteReconciliationReport.empty() : reconciliation;
        releaseComparison = releaseComparison == null ? UpdateSiteReleaseComparison.empty() : releaseComparison;
        semanticRecovery = semanticRecovery == null ? UpdateSiteSemanticRecovery.empty() : semanticRecovery;
        performance = performance == null ? UpdateSiteAnalysisPerformance.empty() : performance;
    }


}
