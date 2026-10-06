package com.example.editor.project.recovery;

import java.util.List;

/** Perfil de Integration propuesto a partir de evidencias del proyecto anfitrión. */
public record ExternalProjectIntegrationSuggestion(String technology, String projectPath,
        String moduleName, String buildSystem, List<String> sourceRoots, String generatedFolder,
        List<String> evidencePaths, int confidenceScore, String rationale) {
    public ExternalProjectIntegrationSuggestion {
        technology = technology == null ? "" : technology;
        projectPath = projectPath == null ? "" : projectPath;
        moduleName = moduleName == null ? "" : moduleName;
        buildSystem = buildSystem == null ? "UNKNOWN" : buildSystem;
        sourceRoots = sourceRoots == null ? List.of() : List.copyOf(sourceRoots);
        generatedFolder = generatedFolder == null ? "" : generatedFolder;
        evidencePaths = evidencePaths == null ? List.of() : List.copyOf(evidencePaths);
        confidenceScore = Math.max(0, Math.min(100, confidenceScore));
        rationale = rationale == null ? "" : rationale;
    }
}
