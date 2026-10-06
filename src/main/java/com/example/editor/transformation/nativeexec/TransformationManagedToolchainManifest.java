package com.example.editor.transformation.nativeexec;

import java.util.List;

import com.example.editor.transformation.export.TransformationExportLanguage;

/**
 * Administrator-provisioned manifest for one managed native bridge bundle.
 * The manifest is read only from the server-owned managed toolchain root.
 */
public record TransformationManagedToolchainManifest(
        String schemaVersion,
        TransformationExportLanguage language,
        String bridgeName,
        String bridgeVersion,
        String toolchainName,
        String toolchainVersion,
        List<String> command) {
    public TransformationManagedToolchainManifest {
        schemaVersion = clean(schemaVersion);
        bridgeName = clean(bridgeName);
        bridgeVersion = clean(bridgeVersion);
        toolchainName = clean(toolchainName);
        toolchainVersion = clean(toolchainVersion);
        command = command == null ? List.of() : command.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    /** Descripción: Ejecuta la operación «clean» correspondiente a la responsabilidad de TransformationManagedToolchainManifest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
