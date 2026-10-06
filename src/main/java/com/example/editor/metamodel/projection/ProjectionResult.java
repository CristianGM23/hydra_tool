package com.example.editor.metamodel.projection;

import java.util.List;

import com.example.editor.metamodel.core.TransformationWarning;

/**
 * Resume el resultado de generar o comprobar un destino técnico.
 */
public record ProjectionResult(String targetId, ProjectionStatus status, String artifactPath, List<TransformationWarning> warnings, String errorMessage) {

    public ProjectionResult {
        targetId = targetId == null ? "" : targetId;
        status = status == null ? ProjectionStatus.FAILED : status;
        artifactPath = artifactPath == null ? "" : artifactPath;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        errorMessage = errorMessage == null ? "" : errorMessage;
    }

    /** Descripción: Ejecuta la operación «notRequested» correspondiente a la responsabilidad de ProjectionResult. Entradas: targetId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static ProjectionResult notRequested(String targetId) {
        return new ProjectionResult(targetId, ProjectionStatus.NOT_REQUESTED, "", List.of(), "");
    }

    /** Descripción: Ejecuta la operación «stale» correspondiente a la responsabilidad de ProjectionResult. Entradas: targetId: identificador que delimita el recurso objetivo; artifactPath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static ProjectionResult stale(String targetId, String artifactPath) {
        return new ProjectionResult(targetId, ProjectionStatus.STALE, artifactPath, List.of(), "");
    }

    /** Descripción: Ejecuta la operación «failed» correspondiente a la responsabilidad de ProjectionResult. Entradas: targetId: identificador que delimita el recurso objetivo; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static ProjectionResult failed(String targetId, String message) {
        return new ProjectionResult(targetId, ProjectionStatus.FAILED, "", List.of(), message);
    }
}
