package com.example.editor.portability.orchestration;

import java.util.List;

/** Reproducible replay step created from one AM preview. */
public record CapabilityOrchestrationStep(int index, String sourceId, String previewId,
        String policyFingerprint, String inputFingerprint, String outputFingerprint,
        boolean applicable, List<String> diagnostics) {
    public CapabilityOrchestrationStep {
        sourceId = sourceId == null ? "" : sourceId.trim();
        previewId = previewId == null ? "" : previewId.trim();
        policyFingerprint = policyFingerprint == null ? "" : policyFingerprint.trim();
        inputFingerprint = inputFingerprint == null ? "" : inputFingerprint.trim();
        outputFingerprint = outputFingerprint == null ? "" : outputFingerprint.trim();
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
