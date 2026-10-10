package com.example.editor.integration.parity;

import java.util.List;
import java.util.Objects;

/** Explicit native validation request; never accepts arbitrary shell commands or proof IDs. */
public record NativeParityCertificationRequest(List<NativeParityCapability> capabilities, int timeoutSeconds) {
    public NativeParityCertificationRequest {
        capabilities = List.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        if(capabilities.isEmpty() || capabilities.size() > NativeParityCapability.values().length
                || capabilities.stream().anyMatch(Objects::isNull)
                || capabilities.stream().distinct().count() != capabilities.size()) {
            throw new IllegalArgumentException("Select unique functional capabilities to certify");
        }
        if(timeoutSeconds <= 0) timeoutSeconds = 180;
        if(timeoutSeconds > 900) throw new IllegalArgumentException("Native validation timeout exceeds 900 seconds");
    }
}
