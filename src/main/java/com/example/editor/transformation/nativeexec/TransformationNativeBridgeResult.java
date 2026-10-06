package com.example.editor.transformation.nativeexec;

import java.util.List;

import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.export.TransformationExportLanguage;

/** Result descriptor written by a native runtime bridge inside HYDRA's isolated workspace. */
public record TransformationNativeBridgeResult(
        String schemaVersion,
        String transformationId,
        TransformationExportLanguage language,
        TransformationKind kind,
        TransformationExecutionDirection direction,
        String targetModelFile,
        List<TransformationNativeBridgeArtifact> artifacts,
        List<String> diagnostics) {
    public TransformationNativeBridgeResult {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "" : schemaVersion.trim();
        transformationId = transformationId == null ? "" : transformationId.trim();
        direction = direction == null ? TransformationExecutionDirection.FORWARD : direction;
        targetModelFile = targetModelFile == null ? "" : targetModelFile.trim();
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        diagnostics = diagnostics == null ? List.of() : diagnostics.stream().filter(value->value!=null&&!value.isBlank()).map(String::trim).toList();
    }

    /** Backward-compatible constructor for protocol 1.0 forward-only bridge fixtures. */
    /** Descripción: Inicializa una instancia de TransformationNativeBridgeResult con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; transformationId: identificador que delimita el recurso objetivo; language: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; targetModelFile: archivo o contenido que debe procesarse; artifacts: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationNativeBridgeResult(String schemaVersion, String transformationId,
            TransformationExportLanguage language, TransformationKind kind, String targetModelFile,
            List<TransformationNativeBridgeArtifact> artifacts, List<String> diagnostics) {
        this(schemaVersion, transformationId, language, kind, TransformationExecutionDirection.FORWARD,
                targetModelFile, artifacts, diagnostics);
    }
}
