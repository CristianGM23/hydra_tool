package com.example.editor.metamodel;

/**
 * Representa un error producido al interpretar la estructura de un metamodelo.
 */
public class MetamodelIntrospectionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Descripción:
     * Crea una excepción con un mensaje explicativo.
     *
     * Entrada:
     * @param message contiene la descripción del error.
     *
     * Salida:
     * Crea una instancia de MetamodelIntrospectionException.
     */
    /** Descripción: Inicializa una instancia de MetamodelIntrospectionException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelIntrospectionException(String message) {
        super(message);
    }
}