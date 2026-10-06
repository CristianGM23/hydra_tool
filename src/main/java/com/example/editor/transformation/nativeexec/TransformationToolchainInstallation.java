package com.example.editor.transformation.nativeexec;

import com.example.editor.transformation.export.TransformationExportLanguage;

/** HYDRA-owned installation marker stored inside a provisioned managed toolchain slot. */
public record TransformationToolchainInstallation(
        String schemaVersion,
        TransformationExportLanguage language,
        String packageId,
        String packageVersion,
        String packageFingerprint) {
    public TransformationToolchainInstallation {
        schemaVersion = clean(schemaVersion);
        packageId = clean(packageId);
        packageVersion = clean(packageVersion);
        packageFingerprint = clean(packageFingerprint);
    }

    /** Normalizes optional textual metadata. */
    /** Descripción: Ejecuta la operación «clean» correspondiente a la responsabilidad de TransformationToolchainInstallation. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
