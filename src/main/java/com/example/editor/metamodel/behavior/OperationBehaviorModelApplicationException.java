package com.example.editor.metamodel.behavior;

/** Error estable de la aplicación de implementaciones de operaciones. */
public class OperationBehaviorModelApplicationException extends RuntimeException {
    public enum Reason { INVALID_REQUEST, NOT_FOUND, CONFLICT, PRECONDITION_FAILED, VALIDATION_ERROR, STORAGE_ERROR }
    private final Reason reason;
    /** Descripción: Inicializa una instancia de OperationBehaviorModelApplicationException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public OperationBehaviorModelApplicationException(Reason reason, String message) { super(message); this.reason = reason; }
    /** Descripción: Inicializa una instancia de OperationBehaviorModelApplicationException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public OperationBehaviorModelApplicationException(Reason reason, String message, Throwable cause) { super(message, cause); this.reason = reason; }
    /** Descripción: Obtiene la información correspondiente a «getReason» dentro de Reason. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Reason getReason() { return reason; }
}
