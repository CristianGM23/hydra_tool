package com.example.editor.metamodel.projection;

/**
 * Representa un error estable al consultar o configurar una proyección.
 */
public class MetamodelProjectionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    /** Descripción: Inicializa una instancia de MetamodelProjectionException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelProjectionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /** Descripción: Inicializa una instancia de MetamodelProjectionException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelProjectionException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /** Descripción: Obtiene la información correspondiente a «getReason» dentro de MetamodelProjectionException. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Reason getReason() {
        return reason;
    }

    public enum Reason {
        INVALID_REQUEST,
        NOT_FOUND,
        STORAGE_ERROR
    }
}
