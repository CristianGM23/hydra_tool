package com.example.editor.metamodel.application;

/**
 * Representa un error de la aplicación canónica independiente del transporte HTTP.
 */
public class MetamodelApplicationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    /** Descripción: Inicializa una instancia de MetamodelApplicationException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /** Descripción: Inicializa una instancia de MetamodelApplicationException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelApplicationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /** Descripción: Obtiene la información correspondiente a «getReason» dentro de MetamodelApplicationException. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Reason getReason() {
        return reason;
    }

    public enum Reason {
        INVALID_REQUEST,
        NOT_FOUND,
        CONFLICT,
        VALIDATION_ERROR,
        STORAGE_ERROR,
        PRECONDITION_FAILED
    }
}
