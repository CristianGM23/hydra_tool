package com.example.editor.transformation.core;

public record TransformationSummary(
        String id,
        String name,
        TransformationKind kind,
        TransformationDirectionality directionality,
        String sourceMetamodelId,
        String targetMetamodelId,
        int ruleCount,
        String etag) {

    /** Compatibility constructor for callers compiled against the pre-bidirectional summary shape. */
    /** Descripción: Inicializa una instancia de TransformationSummary con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; sourceMetamodelId: identificador que delimita el recurso objetivo; targetMetamodelId: identificador que delimita el recurso objetivo; ruleCount: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationSummary(String id, String name, TransformationKind kind, String sourceMetamodelId,
            String targetMetamodelId, int ruleCount, String etag) {
        this(id, name, kind, TransformationDirectionality.UNIDIRECTIONAL, sourceMetamodelId, targetMetamodelId, ruleCount, etag);
    }
}
