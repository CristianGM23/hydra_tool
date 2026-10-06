package com.example.editor.audit;

import java.util.Locale;

public enum AuditLanguage {
    EN,
    ES;

    /** Descripción: Ejecuta la operación «from» correspondiente a la responsabilidad de AuditLanguage. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static AuditLanguage from(String value) {
        return value != null && value.trim().toLowerCase(Locale.ROOT).startsWith("es") ? ES : EN;
    }

    /** Descripción: Ejecuta la operación «code» correspondiente a la responsabilidad de AuditLanguage. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String code() { return this == ES ? "es" : "en"; }
}
