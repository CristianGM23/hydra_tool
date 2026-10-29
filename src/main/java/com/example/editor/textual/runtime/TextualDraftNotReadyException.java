package com.example.editor.textual.runtime;

/**
 * La operación pedida exige que el borrador textual actual sea el último válido y no lo es (borrador roto o sin modelo válido).
 * Extiende {@link IllegalStateException} para conservar el contrato interno; el controlador de sesiones textuales lo traduce a un conflicto de cliente (4xx).
 */
public class TextualDraftNotReadyException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    /** Descripción: Crea la excepción con un mensaje seguro para el cliente. Entradas: message: motivo por el que el borrador no admite la operación. Salida: deja la excepción inicializada. */ public TextualDraftNotReadyException(String message) { super(message); }
}
