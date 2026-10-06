package com.example.editor.integration.verification;

import java.util.List;
import java.util.Map;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.IntegrationVerificationStep;

/** Descriptor portable de los comandos que aceptan una exportación en su herramienta nativa. */
public record TechnologyProjectValidationDescriptor(String schemaVersion, IntegrationTechnology technology,
        List<IntegrationVerificationStep> verificationSteps, Map<String, String> metadata) {
    public TechnologyProjectValidationDescriptor {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion;
        if(technology == null){throw new IllegalArgumentException("technology is required");}
        verificationSteps = verificationSteps == null ? List.of() : List.copyOf(verificationSteps);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
