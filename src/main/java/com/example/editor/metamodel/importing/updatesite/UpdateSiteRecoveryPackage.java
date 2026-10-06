package com.example.editor.metamodel.importing.updatesite;

import java.time.Instant;
import java.util.List;

/** Paquete auditable de procedencia, logs y capas recuperadas. */
public record UpdateSiteRecoveryPackage(String schemaVersion, String id, String metamodelId,
        Instant generatedAt, String sourceFingerprint, UpdateSiteRecoveryCandidate selectedCandidate,
        List<UpdateSiteElementProvenance> provenance, UpdateSiteRecoveredLayers layers,
        List<String> diagnostics, UpdateSiteImportDashboard dashboard,
        List<UpdateSiteImportLogEntry> logs, List<UpdateSiteFileOutcome> fileOutcomes,
        List<UpdateSiteGrammarInspection> grammarInspections,
        UpdateSiteReconciliationReport reconciliation, UpdateSiteReleaseComparison releaseComparison,
        UpdateSiteSemanticRecovery semanticRecovery) {
    public UpdateSiteRecoveryPackage {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "2.0" : schemaVersion;
        id = id == null ? "" : id;
        metamodelId = metamodelId == null ? "" : metamodelId;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        sourceFingerprint = sourceFingerprint == null ? "" : sourceFingerprint;
        provenance = provenance == null ? List.of() : List.copyOf(provenance);
        layers = layers == null ? UpdateSiteRecoveredLayers.empty() : layers;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        dashboard = dashboard == null ? UpdateSiteImportDashboard.empty() : dashboard;
        logs = logs == null ? List.of() : List.copyOf(logs);
        fileOutcomes = fileOutcomes == null ? List.of() : List.copyOf(fileOutcomes);
        grammarInspections = grammarInspections == null ? List.of() : List.copyOf(grammarInspections);
        reconciliation = reconciliation == null ? UpdateSiteReconciliationReport.empty() : reconciliation;
        releaseComparison = releaseComparison == null ? UpdateSiteReleaseComparison.empty() : releaseComparison;
        semanticRecovery = semanticRecovery == null ? UpdateSiteSemanticRecovery.empty() : semanticRecovery;
    }


}
