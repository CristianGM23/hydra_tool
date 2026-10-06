package com.example.editor.semantic.expression;

public class ExpressionEvaluationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /** Descripción: Inicializa una instancia de ExpressionEvaluationException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ExpressionEvaluationException(String message) { super(message); }
    /** Descripción: Inicializa una instancia de ExpressionEvaluationException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ExpressionEvaluationException(String message, Throwable cause) { super(message, cause); }
}
