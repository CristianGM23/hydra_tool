package com.example.editor.remote.auth;

import java.util.regex.Pattern;

/** Sanitiza textos de proveedores antes de incorporarlos a logs o errores de usuario. */
public final class RemoteSecretRedactor {
    private static final Pattern JSON_TOKEN=Pattern.compile("(?i)(\\\"?(?:access_token|refresh_token|client_secret|token)\\\"?\\s*[:=]\\s*\\\"?)[^\\\"&\\s,}]+",Pattern.CASE_INSENSITIVE);
    private static final Pattern BEARER=Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]{12,}");
    private static final Pattern GITHUB=Pattern.compile("\\b(?:gh[opusr]_[A-Za-z0-9_]{12,}|github_pat_[A-Za-z0-9_]{12,})\\b");
    /** Descripción: Inicializa una instancia de RemoteSecretRedactor con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private RemoteSecretRedactor(){}
    /** Descripción: Ejecuta la operación «redact» correspondiente a la responsabilidad de RemoteSecretRedactor. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String redact(String value){
        if(value==null||value.isBlank()) return "";
        String safe=JSON_TOKEN.matcher(value).replaceAll("$1[REDACTED]");
        safe=BEARER.matcher(safe).replaceAll("Bearer [REDACTED]");
        safe=GITHUB.matcher(safe).replaceAll("[REDACTED]");
        return safe.length()>800?safe.substring(0,800)+"…":safe;
    }
}
