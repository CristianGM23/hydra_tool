package com.example.editor.transformation.parity;

import java.util.List;

import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.export.TransformationExportDiagnostic;
import com.example.editor.transformation.export.TransformationExportLanguage;

/** Resultado auditable de una comprobación de paridad ejecutada. */
public record TransformationParityReport(
        String transformationId,
        TransformationExportLanguage language,
        TransformationKind kind,
        TransformationExecutionDirection direction,
        TransformationParityStatus status,
        boolean compatible,
        boolean matched,
        String canonicalFingerprint,
        String nativeFingerprint,
        List<TransformationExportDiagnostic> exportDiagnostics,
        List<TransformationParityDifference> differences) {
    public TransformationParityReport {
        transformationId = transformationId == null ? "" : transformationId;
        direction = direction == null ? TransformationExecutionDirection.FORWARD : direction;
        canonicalFingerprint = canonicalFingerprint == null ? "" : canonicalFingerprint;
        nativeFingerprint = nativeFingerprint == null ? "" : nativeFingerprint;
        exportDiagnostics = exportDiagnostics == null ? List.of() : List.copyOf(exportDiagnostics);
        differences = differences == null ? List.of() : List.copyOf(differences);
    }
    /** Backward-compatible constructor for historical forward-only callers. */
    /** Descripción: Inicializa una instancia de TransformationParityReport con sus dependencias y datos de entrada. Entradas: transformationId: identificador que delimita el recurso objetivo; language: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; status: dato de entrada necesario para la operación; compatible: dato de entrada necesario para la operación; matched: dato de entrada necesario para la operación; canonicalFingerprint: dato de entrada necesario para la operación; nativeFingerprint: dato de entrada necesario para la operación; exportDiagnostics: dato de entrada necesario para la operación; differences: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationParityReport(String transformationId, TransformationExportLanguage language, TransformationKind kind,
            TransformationParityStatus status, boolean compatible, boolean matched, String canonicalFingerprint,
            String nativeFingerprint, List<TransformationExportDiagnostic> exportDiagnostics,
            List<TransformationParityDifference> differences) {
        this(transformationId, language, kind, TransformationExecutionDirection.FORWARD, status, compatible, matched,
                canonicalFingerprint, nativeFingerprint, exportDiagnostics, differences);
    }
}
