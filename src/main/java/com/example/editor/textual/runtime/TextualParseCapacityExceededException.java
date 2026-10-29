package com.example.editor.textual.runtime;

/**
 * Todos los permisos de análisis textual están ocupados durante el tiempo de espera configurado: la petición puede repetirse más tarde.
 * Extiende {@link IllegalStateException} para conservar el contrato interno; la API lo traduce a 503 con Retry-After, nunca a un error interno.
 */
public class TextualParseCapacityExceededException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    /** Descripción: Crea la excepción con un mensaje seguro para el cliente. Entradas: message: motivo del rechazo temporal. Salida: deja la excepción inicializada. */ public TextualParseCapacityExceededException(String message) { super(message); }
}
