package com.example.editor.transformation.web;

import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Solicitud de preview que mantiene el draft y el modelo de entrada fuera de persistencia. */
public record TransformationDraftExecutionRequest(
        TransformationDefinitionDocument transformation,
        CanonicalModelDocument sourceModel,
        TransformationExecutionDirection direction) {
    public TransformationDraftExecutionRequest {
        direction = direction == null ? TransformationExecutionDirection.FORWARD : direction;
    }

    /** Descripción: Inicializa una instancia de TransformationDraftExecutionRequest con sus dependencias y datos de entrada. Entradas: transformation: dato de entrada necesario para la operación; sourceModel: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationDraftExecutionRequest(TransformationDefinitionDocument transformation, CanonicalModelDocument sourceModel) {
        this(transformation, sourceModel, TransformationExecutionDirection.FORWARD);
    }
}
