package com.example.editor.transformation.nativeexec;

import java.util.List;

/** Trusted package catalog and current managed-installation state. */
public record TransformationToolchainCatalog(
        boolean provisioningEnabled,
        List<TransformationToolchainPackageDescriptor> packages,
        List<TransformationToolchainInstallationDescriptor> installations,
        List<String> diagnostics) {
    public TransformationToolchainCatalog {
        packages = packages == null ? List.of() : List.copyOf(packages);
        installations = installations == null ? List.of() : List.copyOf(installations);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
