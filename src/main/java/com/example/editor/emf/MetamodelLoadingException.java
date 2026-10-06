package com.example.editor.emf;

/**
 * Representa un error producido durante la carga o validación inicial
 * de un metamodelo Ecore.
 */
public class MetamodelLoadingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Descripción:
     * Crea una excepción con un mensaje explicativo.
     *
     * Entrada:
     * @param message contiene la descripción del error.
     *
     * Salida:
     * Crea una instancia de MetamodelLoadingException.
     */
    /** Descripción: Inicializa una instancia de MetamodelLoadingException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelLoadingException(String message) {
        super(message);
    }

    /**
     * Descripción:
     * Crea una excepción con un mensaje y el error original.
     *
     * Entrada:
     * @param message contiene la descripción del error.
     * @param cause contiene la excepción que provocó el error.
     *
     * Salida:
     * Crea una instancia de MetamodelLoadingException.
     */
    /** Descripción: Inicializa una instancia de MetamodelLoadingException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelLoadingException(String message, Throwable cause) {
        super(message, cause);
    }
}