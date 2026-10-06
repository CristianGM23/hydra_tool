package com.example.editor.event.flow;

/** Política portable de timeout de un nodo. */
public record TimeoutPolicy(long timeoutMillis, String timeoutTransitionId) {
    public TimeoutPolicy {
        if(timeoutMillis < 0) throw new IllegalArgumentException("timeoutMillis must be non-negative");
        timeoutTransitionId = timeoutTransitionId == null ? "" : timeoutTransitionId.trim();
    }
    /** Descripción: Ejecuta la operación «none» correspondiente a la responsabilidad de TimeoutPolicy. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static TimeoutPolicy none() { return new TimeoutPolicy(0L, ""); }
}
