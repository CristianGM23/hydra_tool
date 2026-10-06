package com.example.editor.portability.orchestration;

import java.util.List;
import com.example.editor.portability.HydraCapabilityPackage;

/** Explainable multiorigin composition plan and replay identity. */
public record CapabilityOrchestrationPreview(String orchestrationId,
        HydraCapabilityPackage mergedPackage, List<String> precedenceOrder,
        List<CapabilityOrchestrationStep> steps, boolean applicable,
        List<String> diagnostics) {
    public CapabilityOrchestrationPreview {
        precedenceOrder = precedenceOrder == null ? List.of() : List.copyOf(precedenceOrder);
        steps = steps == null ? List.of() : List.copyOf(steps);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
