package com.example.editor.integration.workflow;

import java.util.List;
import com.example.editor.integration.IntegrationTechnology;

/** Estado operativo de entrega de un perfil concreto, separado de la madurez global de la tecnología. */
public record TechnologyDeliveryReadiness(
        String profileId,
        IntegrationTechnology technology,
        boolean profileValid,
        boolean compilationReady,
        int generatedFileCount,
        int nativeVerificationStepCount,
        boolean nativeVerificationAvailable,
        List<TechnologyDeliveryActionDescriptor> actions,
        List<String> blockers,
        List<String> warnings) {
    public TechnologyDeliveryReadiness {
        profileId = profileId == null ? "" : profileId.trim();
        if(technology == null) throw new IllegalArgumentException("technology is required");
        generatedFileCount = Math.max(0, generatedFileCount);
        nativeVerificationStepCount = Math.max(0, nativeVerificationStepCount);
        actions = actions == null ? List.of() : List.copyOf(actions);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
