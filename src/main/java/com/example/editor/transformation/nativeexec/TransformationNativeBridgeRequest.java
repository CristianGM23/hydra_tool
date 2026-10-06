package com.example.editor.transformation.nativeexec;

import java.util.List;

import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.export.TransformationExportLanguage;

/** Stable filesystem protocol passed to an administrator-configured native bridge. */
public record TransformationNativeBridgeRequest(
        String schemaVersion,
        String transformationId,
        TransformationExportLanguage language,
        TransformationKind kind,
        TransformationExecutionDirection direction,
        String transformationFile,
        String sourceModelFile,
        String sourceMetamodelFile,
        String targetMetamodelFile,
        List<String> exportArtifactFiles,
        String outputDirectory,
        String resultFile) {
    public TransformationNativeBridgeRequest {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion;
        transformationId = transformationId == null ? "" : transformationId;
        direction = direction == null ? TransformationExecutionDirection.FORWARD : direction;
        transformationFile = clean(transformationFile);
        sourceModelFile = clean(sourceModelFile);
        sourceMetamodelFile = clean(sourceMetamodelFile);
        targetMetamodelFile = clean(targetMetamodelFile);
        exportArtifactFiles = exportArtifactFiles == null ? List.of() : List.copyOf(exportArtifactFiles);
        outputDirectory = clean(outputDirectory);
        resultFile = clean(resultFile);
    }

    /** Backward-compatible constructor for protocol 1.0 forward-only bridge fixtures. */
    /** Descripción: Inicializa una instancia de TransformationNativeBridgeRequest con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; transformationId: identificador que delimita el recurso objetivo; language: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; transformationFile: archivo o contenido que debe procesarse; sourceModelFile: archivo o contenido que debe procesarse; sourceMetamodelFile: archivo o contenido que debe procesarse; targetMetamodelFile: archivo o contenido que debe procesarse; exportArtifactFiles: archivo o contenido que debe procesarse; outputDirectory: dato de entrada necesario para la operación; resultFile: archivo o contenido que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationNativeBridgeRequest(String schemaVersion, String transformationId,
            TransformationExportLanguage language, TransformationKind kind, String transformationFile,
            String sourceModelFile, String sourceMetamodelFile, String targetMetamodelFile,
            List<String> exportArtifactFiles, String outputDirectory, String resultFile) {
        this(schemaVersion, transformationId, language, kind, TransformationExecutionDirection.FORWARD,
                transformationFile, sourceModelFile, sourceMetamodelFile, targetMetamodelFile,
                exportArtifactFiles, outputDirectory, resultFile);
    }

    /** Descripción: Ejecuta la operación «clean» correspondiente a la responsabilidad de TransformationNativeBridgeRequest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String clean(String value){return value==null?"":value.trim();}
}
