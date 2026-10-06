package com.example.editor.grammar.core;

/** One editable grammar source owned by the neutral GrammarDefinitionModel. */
public record GrammarSourceDocument(String id, String relativePath, String languageId, String content,
        String sourceHash, boolean entryPoint, boolean generated) {
    public GrammarSourceDocument {
        id = required(id, "grammarDocument.id");
        relativePath = GrammarPathPolicy.requirePortableRelative(relativePath, "grammarDocument.relativePath");
        languageId = text(languageId);
        content = content == null ? "" : content;
        String calculated = GrammarHashing.sha256(content);
        String supplied = text(sourceHash);
        if (!supplied.isEmpty() && !supplied.equalsIgnoreCase(calculated)) {
            throw new IllegalArgumentException("grammarDocument.sourceHash does not match its content for " + relativePath);
        }
        sourceHash = calculated;
    }

    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de GrammarSourceDocument. Entradas: text: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String sha256(String text) { return GrammarHashing.sha256(text); }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarSourceDocument. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
    /** Descripción: Valida las condiciones necesarias para «required» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String required(String value, String field) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
