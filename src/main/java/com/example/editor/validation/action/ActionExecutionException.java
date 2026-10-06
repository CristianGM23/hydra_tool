package com.example.editor.validation.action;

/** Error de ejecución de una acción declarativa. */
public class ActionExecutionException extends RuntimeException {
    /** Descripción: Inicializa una instancia de ActionExecutionException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ActionExecutionException(String message) { super(message); }
    /** Descripción: Inicializa una instancia de ActionExecutionException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ActionExecutionException(String message, Throwable cause) { super(message, cause); }
}
