package com.example.editor.transformation.nativeexec;

import java.util.List;

import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.export.TransformationExportLanguage;

/** Process-level evidence exposed without leaking configured commands or temporary paths. */
public record TransformationNativeRuntimeReport(
        TransformationExportLanguage language,
        TransformationKind kind,
        TransformationExecutionDirection direction,
        TransformationNativeRuntimeStatus status,
        long durationMillis,
        Integer exitCode,
        List<String> diagnostics) {
    public TransformationNativeRuntimeReport {
        direction = direction == null ? TransformationExecutionDirection.FORWARD : direction;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        durationMillis = Math.max(0L, durationMillis);
    }

    /** Backward-compatible constructor for historical forward-only runtime reports. */
    /** Descripción: Inicializa una instancia de TransformationNativeRuntimeReport con sus dependencias y datos de entrada. Entradas: language: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; status: dato de entrada necesario para la operación; durationMillis: dato de entrada necesario para la operación; exitCode: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationNativeRuntimeReport(TransformationExportLanguage language, TransformationKind kind,
            TransformationNativeRuntimeStatus status, long durationMillis, Integer exitCode, List<String> diagnostics) {
        this(language, kind, TransformationExecutionDirection.FORWARD, status, durationMillis, exitCode, diagnostics);
    }
}
