package com.example.editor.transformation.core;

public record TransformationExecutionPolicy(
        TransformationExecutionMode mode,
        boolean deterministic,
        boolean failOnBindingError) {

    public TransformationExecutionPolicy {
        mode = mode == null ? TransformationExecutionMode.BATCH : mode;
    }

    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de TransformationExecutionPolicy. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static TransformationExecutionPolicy defaults() {
        return new TransformationExecutionPolicy(TransformationExecutionMode.BATCH, true, true);
    }
}
