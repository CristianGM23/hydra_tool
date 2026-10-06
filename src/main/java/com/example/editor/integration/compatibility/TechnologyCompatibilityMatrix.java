package com.example.editor.integration.compatibility;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Informe determinista de compatibilidad de toolchains externas. */
public record TechnologyCompatibilityMatrix(
        String hydraVersion,
        Instant generatedAt,
        CompatibilityStatus overallStatus,
        List<NativeToolchainProbeResult> results) {

    public TechnologyCompatibilityMatrix {
        hydraVersion = Objects.requireNonNull(hydraVersion, "hydraVersion").trim();
        if (hydraVersion.isEmpty()) {
            throw new IllegalArgumentException("hydraVersion must not be blank");
        }
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
        overallStatus = Objects.requireNonNull(overallStatus, "overallStatus");
        results = List.copyOf(Objects.requireNonNull(results, "results"));
    }
}
