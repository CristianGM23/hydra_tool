package com.example.editor.audit;

import java.util.Locale;

public enum AuditExportFormat {
    JSON("json", "application/json"),
    CSV("csv", "text/csv"),
    MARKDOWN("md", "text/markdown"),
    TXT("txt", "text/plain"),
    DOC("doc", "application/msword");

    private final String extension;
    private final String mediaType;
    /** Descripción: Inicializa una instancia de AuditExportFormat con sus dependencias y datos de entrada. Entradas: extension: dato de entrada necesario para la operación; mediaType: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ AuditExportFormat(String extension, String mediaType) { this.extension = extension; this.mediaType = mediaType; }
    /** Descripción: Ejecuta la operación «extension» correspondiente a la responsabilidad de AuditExportFormat. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String extension() { return extension; }
    /** Descripción: Ejecuta la operación «mediaType» correspondiente a la responsabilidad de AuditExportFormat. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String mediaType() { return mediaType; }
    /** Descripción: Ejecuta la operación «from» correspondiente a la responsabilidad de AuditExportFormat. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static AuditExportFormat from(String value) {
        if(value == null || value.isBlank()) return JSON;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if("MD".equals(normalized)) normalized = "MARKDOWN";
        return valueOf(normalized);
    }
}
