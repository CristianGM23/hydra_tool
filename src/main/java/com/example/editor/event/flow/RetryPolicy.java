package com.example.editor.event.flow;

/** Política portable de reintento de un nodo. */
public record RetryPolicy(int maxAttempts, long delayMillis, double backoffMultiplier) {
    public RetryPolicy {
        if(maxAttempts < 0) throw new IllegalArgumentException("maxAttempts must be non-negative");
        if(delayMillis < 0) throw new IllegalArgumentException("delayMillis must be non-negative");
        if(backoffMultiplier <= 0) backoffMultiplier = 1.0d;
    }
    /** Descripción: Ejecuta la operación «none» correspondiente a la responsabilidad de RetryPolicy. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static RetryPolicy none() { return new RetryPolicy(0, 0L, 1.0d); }
}
