package com.example.editor.transformation.nativeexec;

import java.util.List;

import com.example.editor.transformation.export.TransformationExportLanguage;

/** Internal server-side command resolution result. Command data is never returned by controllers. */
record TransformationNativeRuntimeCommandResolution(
        TransformationExportLanguage language,
        TransformationNativeRuntimeSource source,
        List<String> command,
        String bridgeName,
        String bridgeVersion,
        String toolchainName,
        String toolchainVersion,
        List<String> diagnostics) {
    TransformationNativeRuntimeCommandResolution {
        source = source == null ? TransformationNativeRuntimeSource.NONE : source;
        command = command == null ? List.of() : List.copyOf(command);
        bridgeName = clean(bridgeName);
        bridgeVersion = clean(bridgeVersion);
        toolchainName = clean(toolchainName);
        toolchainVersion = clean(toolchainVersion);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    /** Descripción: Ejecuta la operación «configured» correspondiente a la responsabilidad de TransformationNativeRuntimeCommandResolution. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean configured() {
        return !command.isEmpty();
    }

    /** Descripción: Ejecuta la operación «valid» correspondiente a la responsabilidad de TransformationNativeRuntimeCommandResolution. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean valid() {
        return configured() && diagnostics.isEmpty();
    }

    /** Descripción: Ejecuta la operación «clean» correspondiente a la responsabilidad de TransformationNativeRuntimeCommandResolution. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
