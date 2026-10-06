package com.example.editor.transformation.nativeexec;

import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.export.TransformationExportLanguage;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Request for export, isolated native execution and canonical parity verification in one operation. */
public record TransformationNativeParityRequest(
        TransformationDefinitionDocument transformation,
        TransformationExportLanguage language,
        TransformationExecutionDirection direction,
        CanonicalModelDocument sourceModel) {
    public TransformationNativeParityRequest {
        direction = direction == null ? TransformationExecutionDirection.FORWARD : direction;
    }

    /** Backward-compatible constructor for historical forward-only native parity requests. */
    /** Descripción: Inicializa una instancia de TransformationNativeParityRequest con sus dependencias y datos de entrada. Entradas: transformation: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; sourceModel: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationNativeParityRequest(TransformationDefinitionDocument transformation,
            TransformationExportLanguage language, CanonicalModelDocument sourceModel) {
        this(transformation, language, TransformationExecutionDirection.FORWARD, sourceModel);
    }
}
