package com.example.editor.portability;

/** Dependencia explícita entre dos capacidades de un paquete. */
public record CapabilityDependency(String sourceCapabilityId, String targetCapabilityId, boolean required, String versionRange, String reason) {
    public CapabilityDependency {
        sourceCapabilityId = sourceCapabilityId == null ? "" : sourceCapabilityId;
        targetCapabilityId = targetCapabilityId == null ? "" : targetCapabilityId;
        versionRange = versionRange == null ? "" : versionRange;
        reason = reason == null ? "" : reason;
    }
}
