package com.example.editor.audit;

import java.util.UUID;

/**
 * Contexto efímero por petición HTTP. Correlaciona la acción de alto nivel con
 * las mutaciones canónicas que provoca sin introducir estado global no acotado.
 */
public final class AuditRequestContext {
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    /** Descripción: Inicializa una instancia de AuditRequestContext con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private AuditRequestContext() { }

    /** Descripción: Ejecuta la operación «begin» correspondiente a la responsabilidad de State. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static Scope begin() { return begin(UUID.randomUUID().toString()); }

    /** Descripción: Ejecuta la operación «begin» correspondiente a la responsabilidad de State. Entradas: correlationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static Scope begin(String correlationId) {
        State previous = CURRENT.get();
        String effective = correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId.trim();
        State current = new State(effective, false);
        CURRENT.set(current);
        return () -> { if(previous == null) CURRENT.remove(); else CURRENT.set(previous); };
    }

    /** Descripción: Ejecuta la operación «active» correspondiente a la responsabilidad de State. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean active() { return CURRENT.get() != null; }

    /** Descripción: Ejecuta la operación «correlationId» correspondiente a la responsabilidad de State. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static String correlationId() {
        State state = CURRENT.get();
        return state == null ? "" : state.correlationId;
    }

    /** Descripción: Ejecuta la operación «markBusinessMutation» correspondiente a la responsabilidad de State. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public static void markBusinessMutation() {
        State state = CURRENT.get();
        if(state != null) state.businessMutation = true;
    }

    /** Descripción: Ejecuta la operación «businessMutationObserved» correspondiente a la responsabilidad de State. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean businessMutationObserved() {
        State state = CURRENT.get();
        return state != null && state.businessMutation;
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable { @Override void close(); }

    private static final class State {
        private final String correlationId;
        private boolean businessMutation;
        /** Descripción: Inicializa una instancia de State con sus dependencias y datos de entrada. Entradas: correlationId: identificador que delimita el recurso objetivo; businessMutation: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private State(String correlationId, boolean businessMutation) {
            this.correlationId = correlationId;
            this.businessMutation = businessMutation;
        }
    }
}
