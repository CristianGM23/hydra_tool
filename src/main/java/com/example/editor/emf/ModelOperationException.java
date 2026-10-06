package com.example.editor.emf;

/**
 * Representa un error producido al crear o modificar un objeto EMF.
 *
 * Se utiliza cuando una clase, atributo o referencia no existe, no es
 * compatible o no puede modificarse.
 */
public class ModelOperationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Descripción:
     * Crea una excepción con un mensaje explicativo.
     *
     * Entrada:
     * @param message contiene la descripción del error.
     *
     * Salida:
     * Crea una instancia de ModelOperationException.
     */
    /** Descripción: Inicializa una instancia de ModelOperationException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ModelOperationException(String message) {
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
     * Crea una instancia de ModelOperationException.
     */
    /** Descripción: Inicializa una instancia de ModelOperationException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ModelOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}