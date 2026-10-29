package com.example.editor.textual.definition;

/** La gramática de un editor textual todavía no tiene una compilación correcta activa: es un estado del recurso (409), no un error interno. */
public class TextualGrammarSnapshotUnavailableException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    /** Descripción: Crea la excepción con un mensaje seguro para el cliente. Entradas: message: motivo por el que no hay snapshot utilizable. Salida: deja la excepción inicializada. */ public TextualGrammarSnapshotUnavailableException(String message) { super(message); }
}
