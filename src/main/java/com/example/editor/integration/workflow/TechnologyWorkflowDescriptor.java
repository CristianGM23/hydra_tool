package com.example.editor.integration.workflow;

import java.util.List;
import com.example.editor.integration.IntegrationTargetKind;
import com.example.editor.integration.IntegrationTechnology;

/** Resumen de paridad de una tecnología a lo largo del proceso completo de integración. */
public record TechnologyWorkflowDescriptor(
        IntegrationTechnology technology,
        String displayName,
        IntegrationTargetKind targetKind,
        TechnologyWorkflowMaturity maturity,
        int parityScore,
        boolean adapterImplemented,
        boolean validatorImplemented,
        List<TechnologyWorkflowStageDescriptor> stages,
        List<String> blockers,
        List<String> recommendations) {
    public TechnologyWorkflowDescriptor {
        if(technology == null || targetKind == null || maturity == null) throw new IllegalArgumentException("technology, targetKind and maturity are required");
        displayName = displayName == null || displayName.isBlank() ? technology.name() : displayName.trim();
        parityScore = Math.max(0, Math.min(100, parityScore));
        stages = stages == null ? List.of() : List.copyOf(stages);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
    }
}
