package com.example.editor.metamodel.design;

/**
 * Representa un error de validación o almacenamiento del diseñador de metamodelos.
 */
public class MetamodelDesignException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    /**
     * Descripción:
     * Crea una excepción con categoría y mensaje.
     *
     * Entrada:
     * @param reason contiene la categoría.
     * @param message contiene el mensaje.
     *
     * Salida:
     * Crea una instancia de MetamodelDesignException.
     */
    /** Descripción: Inicializa una instancia de MetamodelDesignException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelDesignException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /**
     * Descripción:
     * Crea una excepción con categoría, mensaje y causa.
     *
     * Entrada:
     * @param reason contiene la categoría.
     * @param message contiene el mensaje.
     * @param cause contiene el error original.
     *
     * Salida:
     * Crea una instancia de MetamodelDesignException.
     */
    /** Descripción: Inicializa una instancia de MetamodelDesignException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelDesignException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /**
     * Descripción:
     * Devuelve la categoría del error.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return la categoría del error.
     */
    /** Descripción: Obtiene la información correspondiente a «getReason» dentro de Reason. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Reason getReason() {
        return reason;
    }

    /**
     * Define las categorías posibles.
     */
    public enum Reason {
        INVALID_REQUEST,
        CONFLICT,
        STORAGE_ERROR
    }
}
