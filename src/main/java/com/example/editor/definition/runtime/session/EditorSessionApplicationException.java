package com.example.editor.definition.runtime.session;
public class EditorSessionApplicationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public enum Reason { DISABLED, INVALID_REQUEST, NOT_FOUND, CONFLICT, PRECONDITION_FAILED, NOT_EXECUTABLE, STORAGE_ERROR }
    private final Reason reason;
    /** Descripción: Inicializa una instancia de EditorSessionApplicationException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorSessionApplicationException(Reason reason, String message) { super(message); this.reason = reason; }
    /** Descripción: Inicializa una instancia de EditorSessionApplicationException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorSessionApplicationException(Reason reason, String message, Throwable cause) { super(message, cause); this.reason = reason; }
    /** Descripción: Obtiene la información correspondiente a «getReason» dentro de EditorSessionApplicationException. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Reason getReason() { return reason; }
}
