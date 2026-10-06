package com.example.editor.diagram;

/**
 * Representa un error al validar, guardar o cargar un diagrama.
 */
public class DiagramStorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    /**
     * Descripción:
     * Crea una excepción con una categoría y un mensaje.
     *
     * Entrada:
     * @param reason contiene la categoría del error.
     * @param message contiene el mensaje explicativo.
     *
     * Salida:
     * Crea una instancia de DiagramStorageException.
     */
    /** Descripción: Inicializa una instancia de DiagramStorageException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public DiagramStorageException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /**
     * Descripción:
     * Crea una excepción con una categoría, un mensaje y una causa.
     *
     * Entrada:
     * @param reason contiene la categoría del error.
     * @param message contiene el mensaje explicativo.
     * @param cause contiene el error original.
     *
     * Salida:
     * Crea una instancia de DiagramStorageException.
     */
    /** Descripción: Inicializa una instancia de DiagramStorageException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public DiagramStorageException(Reason reason, String message, Throwable cause) {
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
     * Define las categorías de error.
     */
    public enum Reason {
        INVALID_REQUEST,
        NOT_FOUND,
        STORAGE_ERROR
    }
}