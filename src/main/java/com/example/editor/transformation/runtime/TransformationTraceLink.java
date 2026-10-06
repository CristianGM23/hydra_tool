package com.example.editor.transformation.runtime;

import com.example.editor.transformation.core.TransformationExecutionDirection;

/** Correspondencia estable entre elementos utilizada para resolver referencias y reutilizar trazas en ambos sentidos. */
public record TransformationTraceLink(
        String sourceElementId,
        String ruleId,
        String targetAlias,
        String targetElementId,
        String correspondenceId,
        TransformationExecutionDirection direction) {
    public TransformationTraceLink {
        sourceElementId = text(sourceElementId);
        ruleId = text(ruleId);
        targetAlias = text(targetAlias);
        targetElementId = text(targetElementId);
        correspondenceId = text(correspondenceId);
        direction = direction == null ? TransformationExecutionDirection.FORWARD : direction;
    }

    /** Compatibilidad con trazas creadas por el runtime unidireccional previo. */
    /** Descripción: Inicializa una instancia de TransformationTraceLink con sus dependencias y datos de entrada. Entradas: sourceElementId: identificador que delimita el recurso objetivo; ruleId: identificador que delimita el recurso objetivo; targetAlias: dato de entrada necesario para la operación; targetElementId: identificador que delimita el recurso objetivo. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationTraceLink(String sourceElementId, String ruleId, String targetAlias, String targetElementId) {
        this(sourceElementId, ruleId, targetAlias, targetElementId, ruleId, TransformationExecutionDirection.FORWARD);
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TransformationTraceLink. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value; }
}
