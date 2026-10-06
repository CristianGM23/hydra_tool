package com.example.editor.portability;

import java.util.List;

import com.example.editor.integration.IntegrationDeliveryMode;
import com.example.editor.integration.IntegrationTechnology;

/** Traducción tecnológica esperada para una capacidad seleccionada. */
public record TargetCapabilityCompatibility(IntegrationTechnology technology, HydraCapabilityKind capability,
        CapabilityFidelity fidelity, IntegrationDeliveryMode deliveryMode, String nativeLanguage,
        List<String> generatedArtifacts, List<String> limitations) {

    public TargetCapabilityCompatibility {
        fidelity = fidelity == null ? CapabilityFidelity.UNSUPPORTED : fidelity;
        nativeLanguage = nativeLanguage == null ? "" : nativeLanguage;
        generatedArtifacts = generatedArtifacts == null ? List.of() : List.copyOf(generatedArtifacts);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }
}
