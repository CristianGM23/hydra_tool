package com.example.editor.transformation.nativeexec;

/** Optimistic removal request for a HYDRA-provisioned toolchain bundle. */
public record TransformationToolchainRemovalRequest(
        String expectedPackageId,
        String expectedPackageVersion) {
    public TransformationToolchainRemovalRequest {
        expectedPackageId = expectedPackageId == null ? "" : expectedPackageId.trim();
        expectedPackageVersion = expectedPackageVersion == null ? "" : expectedPackageVersion.trim();
    }
}
