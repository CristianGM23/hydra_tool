package com.example.editor.audit;

import java.util.Locale;

/** Modos explícitos de borrado manual expuestos por Activity Log. */
public enum AuditDeletionMode {
    NAVIGATION,
    FILTERED,
    ALL;

    /** Descripción: Ejecuta la operación «from» correspondiente a la responsabilidad de AuditDeletionMode. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static AuditDeletionMode from(String value) {
        if(value == null || value.isBlank()) throw new IllegalArgumentException("deletion mode is required");
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch(IllegalArgumentException exception) { throw new IllegalArgumentException("Unsupported audit deletion mode: " + value, exception); }
    }
}
