package com.example.editor.transformation.core;

public record TransformationTracePolicy(
        boolean enabled,
        boolean retainBindingDetails) {

    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de TransformationTracePolicy. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static TransformationTracePolicy defaults() {
        return new TransformationTracePolicy(true, true);
    }
}
