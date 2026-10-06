package com.example.editor.transformation.nativeexec;

import java.util.List;

import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.export.TransformationExportLanguage;

/** Bounded, path-free diagnostic result for one server-managed transformation runtime. */
public record TransformationNativeRuntimeDiagnostic(
        TransformationExportLanguage language,
        TransformationNativeRuntimeHealth health,
        TransformationNativeRuntimeSource source,
        boolean configured,
        boolean compatible,
        String bridgeName,
        String bridgeVersion,
        String toolchainName,
        String toolchainVersion,
        List<String> protocolVersions,
        List<TransformationKind> supportedKinds,
        List<TransformationExecutionDirection> supportedDirections,
        boolean fullBidirectionalSynchronization,
        boolean incrementalSynchronization,
        long durationMillis,
        List<String> diagnostics) {
    public TransformationNativeRuntimeDiagnostic {
        health = health == null ? TransformationNativeRuntimeHealth.FAILED : health;
        source = source == null ? TransformationNativeRuntimeSource.NONE : source;
        bridgeName = clean(bridgeName);
        bridgeVersion = clean(bridgeVersion);
        toolchainName = clean(toolchainName);
        toolchainVersion = clean(toolchainVersion);
        protocolVersions = protocolVersions == null ? List.of() : List.copyOf(protocolVersions);
        supportedKinds = supportedKinds == null ? List.of() : List.copyOf(supportedKinds);
        supportedDirections = supportedDirections == null ? List.of() : List.copyOf(supportedDirections);
        durationMillis = Math.max(0L, durationMillis);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    /** Descripción: Ejecuta la operación «clean» correspondiente a la responsabilidad de TransformationNativeRuntimeDiagnostic. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
