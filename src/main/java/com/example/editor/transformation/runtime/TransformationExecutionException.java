package com.example.editor.transformation.runtime;

public class TransformationExecutionException extends RuntimeException {
    /** Descripción: Inicializa una instancia de TransformationExecutionException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationExecutionException(String message) { super(message); }
    /** Descripción: Inicializa una instancia de TransformationExecutionException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationExecutionException(String message, Throwable cause) { super(message, cause); }
}
