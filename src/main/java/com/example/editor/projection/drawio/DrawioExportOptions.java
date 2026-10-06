package com.example.editor.projection.drawio;

/** Opciones comunes de las proyecciones draw.io. */
public record DrawioExportOptions(String title, boolean includeMetadata) {
    public DrawioExportOptions { title = title == null ? "" : title; }
    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de DrawioExportOptions. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static DrawioExportOptions defaults() { return new DrawioExportOptions("", true); }
}
