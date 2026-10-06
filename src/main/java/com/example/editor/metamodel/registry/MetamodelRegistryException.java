package com.example.editor.metamodel.registry;

/**
 * Representa un error producido al registrar, buscar o almacenar un metamodelo.
 */
public class MetamodelRegistryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    /**
     * Descripción:
     * Crea una excepción con un tipo de error y un mensaje.
     *
     * Entrada:
     * @param reason indica la categoría del error.
     * @param message contiene la descripción del error.
     *
     * Salida:
     * Crea una instancia de MetamodelRegistryException.
     */
    /** Descripción: Inicializa una instancia de MetamodelRegistryException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelRegistryException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /**
     * Descripción:
     * Crea una excepción con un tipo, un mensaje y la causa original.
     *
     * Entrada:
     * @param reason indica la categoría del error.
     * @param message contiene la descripción del error.
     * @param cause contiene la excepción original.
     *
     * Salida:
     * Crea una instancia de MetamodelRegistryException.
     */
    /** Descripción: Inicializa una instancia de MetamodelRegistryException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelRegistryException(Reason reason, String message, Throwable cause) {
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
     * @return el tipo de error producido.
     */
    /** Descripción: Obtiene la información correspondiente a «getReason» dentro de MetamodelRegistryException. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Reason getReason() {
        return reason;
    }

    /**
     * Define los tipos de errores que puede producir el registro.
     */
    public enum Reason {
        INVALID_REQUEST,
        NOT_FOUND,
        CONFLICT,
        STORAGE_ERROR
    }
}