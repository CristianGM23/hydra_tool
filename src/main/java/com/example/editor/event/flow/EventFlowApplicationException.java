package com.example.editor.event.flow;

/** Error controlado del servicio de flujos. */
public class EventFlowApplicationException extends RuntimeException {
    public enum Reason { INVALID_REQUEST, NOT_FOUND, CONFLICT, PRECONDITION_FAILED, NOT_EXECUTABLE, STORAGE_ERROR, EXECUTION_FAILED }
    private final Reason reason;
    /** Descripción: Inicializa una instancia de EventFlowApplicationException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EventFlowApplicationException(Reason reason, String message) { super(message); this.reason = reason; }
    /** Descripción: Inicializa una instancia de EventFlowApplicationException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EventFlowApplicationException(Reason reason, String message, Throwable cause) { super(message, cause); this.reason = reason; }
    /** Descripción: Obtiene la información correspondiente a «getReason» dentro de EventFlowApplicationException. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Reason getReason() { return reason; }
}
