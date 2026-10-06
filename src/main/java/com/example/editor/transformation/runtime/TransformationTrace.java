package com.example.editor.transformation.runtime;

import java.util.List;

import com.example.editor.transformation.core.TransformationExecutionDirection;

/** Traza neutral de una ejecución M2M, incluyendo el sentido ejecutado. */
public record TransformationTrace(
        String executionId,
        String transformationId,
        String sourceModelId,
        String targetModelId,
        List<TransformationTraceLink> links,
        List<TransformationBindingTrace> bindingTraces,
        TransformationExecutionDirection direction) {
    public TransformationTrace {
        links = links == null ? List.of() : List.copyOf(links);
        bindingTraces = bindingTraces == null ? List.of() : List.copyOf(bindingTraces);
        direction = direction == null ? TransformationExecutionDirection.FORWARD : direction;
    }

    /** Constructor compatible con trazas anteriores, interpretadas como ejecución forward. */
    /** Descripción: Inicializa una instancia de TransformationTrace con sus dependencias y datos de entrada. Entradas: executionId: identificador que delimita el recurso objetivo; transformationId: identificador que delimita el recurso objetivo; sourceModelId: identificador que delimita el recurso objetivo; targetModelId: identificador que delimita el recurso objetivo; links: dato de entrada necesario para la operación; bindingTraces: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationTrace(String executionId, String transformationId, String sourceModelId, String targetModelId,
            List<TransformationTraceLink> links, List<TransformationBindingTrace> bindingTraces) {
        this(executionId, transformationId, sourceModelId, targetModelId, links, bindingTraces, TransformationExecutionDirection.FORWARD);
    }
}
