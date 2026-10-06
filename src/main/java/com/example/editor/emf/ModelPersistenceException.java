package com.example.editor.emf;

/**
 * Representa un error producido al guardar o cargar un modelo XMI.
 */
public class ModelPersistenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Descripción:
     * Crea una excepción con un mensaje explicativo.
     *
     * Entrada:
     * @param message contiene la descripción del error.
     *
     * Salida:
     * Crea una instancia de ModelPersistenceException.
     */
    /** Descripción: Inicializa una instancia de ModelPersistenceException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ModelPersistenceException(String message) {
        super(message);
    }

    /**
     * Descripción:
     * Crea una excepción con un mensaje y la causa original.
     *
     * Entrada:
     * @param message contiene la descripción del error.
     * @param cause contiene la excepción original.
     *
     * Salida:
     * Crea una instancia de ModelPersistenceException.
     */
    /** Descripción: Inicializa una instancia de ModelPersistenceException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ModelPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}