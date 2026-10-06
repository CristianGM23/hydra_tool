package com.example.editor.project.recovery;

import java.time.Instant;
import java.util.List;

/** Resultado completo del análisis multi-evidencia de un proyecto externo. */
public record ExternalProjectRecoveryReport(String analysisId, Instant analyzedAt, String fingerprint,
        int resourceCount, long totalBytes, List<String> technologies,
        List<ExternalProjectResource> resources, List<ExternalProjectTopologyNode> topology,
        List<ExternalProjectMappingEvidence> mappingEvidence,
        List<ExternalProjectIntegrationSuggestion> integrationSuggestions,
        List<ExternalProjectGrammarInspection> grammarInspections,
        List<String> diagnostics) {
    public ExternalProjectRecoveryReport {
        analysisId = analysisId == null ? "" : analysisId;
        analyzedAt = analyzedAt == null ? Instant.now() : analyzedAt;
        fingerprint = fingerprint == null ? "" : fingerprint;
        technologies = technologies == null ? List.of() : List.copyOf(technologies);
        resources = resources == null ? List.of() : List.copyOf(resources);
        topology = topology == null ? List.of() : List.copyOf(topology);
        mappingEvidence = mappingEvidence == null ? List.of() : List.copyOf(mappingEvidence);
        integrationSuggestions = integrationSuggestions == null ? List.of() : List.copyOf(integrationSuggestions);
        grammarInspections = grammarInspections == null ? List.of() : List.copyOf(grammarInspections);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
