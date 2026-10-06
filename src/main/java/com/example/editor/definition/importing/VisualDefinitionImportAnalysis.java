package com.example.editor.definition.importing;

/** High-level quality report for one visual-definition recovery. */
public record VisualDefinitionImportAnalysis(int filesInspected, int elementsInspected, int referencesChecked,
        int unresolvedReferences, int ambiguousReferences, int invalidParameters, int structuralProblems,
        int warnings, int safeCorrections, int reviewCorrections, int manualActions, boolean importReady) {
}
