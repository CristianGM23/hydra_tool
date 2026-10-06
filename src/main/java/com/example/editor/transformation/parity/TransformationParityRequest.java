package com.example.editor.transformation.parity;

import java.util.List;

import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.export.TransformationExportLanguage;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Solicitud side-effect free para contrastar una ejecución externa con el runtime de referencia. */
public record TransformationParityRequest(
        TransformationDefinitionDocument transformation,
        TransformationExportLanguage language,
        TransformationExecutionDirection direction,
        CanonicalModelDocument sourceModel,
        CanonicalModelDocument nativeTargetModel,
        List<TransformationParityArtifact> nativeArtifacts) {
    public TransformationParityRequest {
        direction = direction == null ? TransformationExecutionDirection.FORWARD : direction;
        nativeArtifacts = nativeArtifacts == null ? List.of() : List.copyOf(nativeArtifacts);
    }
    /** Backward-compatible constructor for historical forward-only parity requests. */
    /** Descripción: Inicializa una instancia de TransformationParityRequest con sus dependencias y datos de entrada. Entradas: transformation: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; sourceModel: dato de entrada necesario para la operación; nativeTargetModel: dato de entrada necesario para la operación; nativeArtifacts: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationParityRequest(TransformationDefinitionDocument transformation, TransformationExportLanguage language,
            CanonicalModelDocument sourceModel, CanonicalModelDocument nativeTargetModel, List<TransformationParityArtifact> nativeArtifacts) {
        this(transformation, language, TransformationExecutionDirection.FORWARD, sourceModel, nativeTargetModel, nativeArtifacts);
    }
}
