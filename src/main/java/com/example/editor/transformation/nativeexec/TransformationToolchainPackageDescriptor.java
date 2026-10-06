package com.example.editor.transformation.nativeexec;

import java.util.List;

import com.example.editor.transformation.export.TransformationExportLanguage;

/** Public, path-free description of one trusted server-side toolchain package. */
public record TransformationToolchainPackageDescriptor(
        TransformationExportLanguage language,
        String packageId,
        String packageVersion,
        String bridgeName,
        String bridgeVersion,
        String toolchainName,
        String toolchainVersion,
        boolean installable,
        boolean installed,
        List<String> diagnostics) {
    public TransformationToolchainPackageDescriptor {
        packageId = clean(packageId);
        packageVersion = clean(packageVersion);
        bridgeName = clean(bridgeName);
        bridgeVersion = clean(bridgeVersion);
        toolchainName = clean(toolchainName);
        toolchainVersion = clean(toolchainVersion);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    /** Normalizes optional textual metadata. */
    /** Descripción: Ejecuta la operación «clean» correspondiente a la responsabilidad de TransformationToolchainPackageDescriptor. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
