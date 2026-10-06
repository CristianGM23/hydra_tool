package com.example.editor.transformation.nativeexec;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.editor.transformation.export.TransformationExportLanguage;

/** Trusted server-side distribution manifest for one provisionable native transformation toolchain bundle. */
public record TransformationToolchainPackageManifest(
        String schemaVersion,
        String packageId,
        String packageVersion,
        TransformationExportLanguage language,
        String bridgeName,
        String bridgeVersion,
        String toolchainName,
        String toolchainVersion,
        Map<String, String> sha256) {
    public TransformationToolchainPackageManifest {
        schemaVersion = clean(schemaVersion);
        packageId = clean(packageId);
        packageVersion = clean(packageVersion);
        bridgeName = clean(bridgeName);
        bridgeVersion = clean(bridgeVersion);
        toolchainName = clean(toolchainName);
        toolchainVersion = clean(toolchainVersion);
        sha256 = sha256 == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(sha256));
    }

    /** Normalizes optional textual metadata without changing its semantic value. */
    /** Descripción: Ejecuta la operación «clean» correspondiente a la responsabilidad de TransformationToolchainPackageManifest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
