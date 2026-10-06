package com.example.editor.definition.importing;

/** A deterministic or reviewable correction proposed by the visual-import analyzer. */
public record VisualDefinitionImportFix(String id, String code, String source, String target, String title,
        String description, String safety, String confidence, boolean automatic, boolean applied) {
    public VisualDefinitionImportFix {
        id = text(id, "fix");
        code = text(code, "IMPORT_FIX");
        source = text(source, "");
        target = text(target, "");
        title = text(title, "Import correction");
        description = text(description, "");
        safety = text(safety, "REVIEW").toUpperCase();
        confidence = text(confidence, "UNKNOWN").toUpperCase();
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de VisualDefinitionImportFix. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
}
