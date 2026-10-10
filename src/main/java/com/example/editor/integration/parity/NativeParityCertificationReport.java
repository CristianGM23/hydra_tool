package com.example.editor.integration.parity;

import java.util.List;
import java.util.Objects;
import com.example.editor.integration.IntegrationTechnology;

/** Runtime-proof coverage. 'certified' requires a native-required successful validation. */
public record NativeParityCertificationReport(IntegrationTechnology technology,
        boolean certified, int provenCount, int requestedCount,
        List<NativeParityCapabilityResult> capabilities, String validationStatus) {
    public NativeParityCertificationReport {
        Objects.requireNonNull(technology, "technology");
        capabilities = List.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        Objects.requireNonNull(validationStatus, "validationStatus");
        if(provenCount < 0 || requestedCount != capabilities.size() || provenCount > requestedCount) {
            throw new IllegalArgumentException("Inconsistent parity report counts");
        }
        if(certified && (requestedCount == 0 || provenCount != requestedCount)) {
            throw new IllegalArgumentException("Cannot certify unproven capabilities");
        }
    }
}
