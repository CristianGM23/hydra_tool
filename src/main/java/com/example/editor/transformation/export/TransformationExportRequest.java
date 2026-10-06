package com.example.editor.transformation.export;

import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationExecutionDirection;

/** Solicitud de exportación de un draft canónico sin necesidad de persistirlo. */
public record TransformationExportRequest(
        TransformationDefinitionDocument transformation,
        TransformationExportLanguage language,
        TransformationExecutionDirection direction) {
    public TransformationExportRequest {
        direction = direction == null ? TransformationExecutionDirection.FORWARD : direction;
    }
    /** Backward-compatible constructor for historical forward-only exports. */
    /** Descripción: Inicializa una instancia de TransformationExportRequest con sus dependencias y datos de entrada. Entradas: transformation: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationExportRequest(TransformationDefinitionDocument transformation, TransformationExportLanguage language) {
        this(transformation, language, TransformationExecutionDirection.FORWARD);
    }
}
