package com.example.editor.transformation.nativeexec;

import java.util.List;

import com.example.editor.transformation.export.TransformationExportLanguage;

/** Public installation state without exposing server paths or launch commands. */
public record TransformationToolchainInstallationDescriptor(
        TransformationExportLanguage language,
        boolean present,
        boolean hydraManaged,
        String packageId,
        String packageVersion,
        List<String> diagnostics) {
    public TransformationToolchainInstallationDescriptor {
        packageId = packageId == null ? "" : packageId.trim();
        packageVersion = packageVersion == null ? "" : packageVersion.trim();
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
