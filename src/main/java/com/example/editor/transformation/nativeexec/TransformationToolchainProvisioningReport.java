package com.example.editor.transformation.nativeexec;

import java.util.List;

import com.example.editor.transformation.export.TransformationExportLanguage;

/** Bounded result of a server-side toolchain install/update/remove operation. */
public record TransformationToolchainProvisioningReport(
        TransformationExportLanguage language,
        TransformationToolchainProvisioningStatus status,
        String packageId,
        String packageVersion,
        TransformationNativeRuntimeDiagnostic diagnostic,
        List<String> diagnostics) {
    public TransformationToolchainProvisioningReport {
        status = status == null ? TransformationToolchainProvisioningStatus.BLOCKED : status;
        packageId = packageId == null ? "" : packageId.trim();
        packageVersion = packageVersion == null ? "" : packageVersion.trim();
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
