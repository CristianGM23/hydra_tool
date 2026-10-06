package com.example.editor.transformation.runtime;

import java.util.List;

import com.example.editor.transformation.core.TransformationDirectionality;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationTracePolicy;

/** Plan M2M neutral validado antes de ejecutar una dirección concreta. */
public record CompiledTransformationPlan(
        String transformationId,
        String transformationName,
        TransformationDirectionality directionality,
        TransformationExecutionDirection direction,
        String sourceMetamodelId,
        String targetMetamodelId,
        String targetMetamodelFingerprint,
        List<TransformationRule> rules,
        TransformationTracePolicy tracePolicy) {
    public CompiledTransformationPlan {
        transformationId = transformationId == null ? "" : transformationId;
        transformationName = transformationName == null ? "" : transformationName;
        directionality = directionality == null ? TransformationDirectionality.UNIDIRECTIONAL : directionality;
        direction = direction == null ? TransformationExecutionDirection.FORWARD : direction;
        sourceMetamodelId = sourceMetamodelId == null ? "" : sourceMetamodelId;
        targetMetamodelId = targetMetamodelId == null ? "" : targetMetamodelId;
        targetMetamodelFingerprint = targetMetamodelFingerprint == null ? "" : targetMetamodelFingerprint;
        rules = rules == null ? List.of() : List.copyOf(rules);
        tracePolicy = tracePolicy == null ? TransformationTracePolicy.defaults() : tracePolicy;
    }

    /** Backward-compatible constructor for historical forward-only plans. */
    /** Descripción: Inicializa una instancia de CompiledTransformationPlan con sus dependencias y datos de entrada. Entradas: transformationId: identificador que delimita el recurso objetivo; transformationName: dato de entrada necesario para la operación; sourceMetamodelId: identificador que delimita el recurso objetivo; targetMetamodelId: identificador que delimita el recurso objetivo; targetMetamodelFingerprint: dato de entrada necesario para la operación; rules: dato de entrada necesario para la operación; tracePolicy: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CompiledTransformationPlan(String transformationId, String transformationName, String sourceMetamodelId,
            String targetMetamodelId, String targetMetamodelFingerprint, List<TransformationRule> rules,
            TransformationTracePolicy tracePolicy) {
        this(transformationId, transformationName, TransformationDirectionality.UNIDIRECTIONAL,
                TransformationExecutionDirection.FORWARD, sourceMetamodelId, targetMetamodelId,
                targetMetamodelFingerprint, rules, tracePolicy);
    }
}
