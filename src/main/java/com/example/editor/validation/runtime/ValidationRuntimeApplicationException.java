package com.example.editor.validation.runtime;

/** Error estable de la capa de aplicación del runtime de validaciones. */
public class ValidationRuntimeApplicationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public enum Reason { INVALID_REQUEST, NOT_FOUND, NOT_EXECUTABLE, EXECUTION_FAILED }
    private final Reason reason;
    /** Descripción: Inicializa una instancia de ValidationRuntimeApplicationException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ValidationRuntimeApplicationException(Reason reason, String message) { super(message); this.reason = reason; }
    /** Descripción: Inicializa una instancia de ValidationRuntimeApplicationException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ValidationRuntimeApplicationException(Reason reason, String message, Throwable cause) { super(message, cause); this.reason = reason; }
    /** Descripción: Obtiene la información correspondiente a «getReason» dentro de Reason. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Reason getReason() { return reason; }
}
