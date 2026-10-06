package com.example.editor.textual.runtime;

import com.example.editor.grammar.core.TextRange;

/** Structured, reviewable text edit produced while reconciling a canonical semantic change. */
public record TextualReconciliationEdit(String documentId, TextRange range, String replacement,
        String semanticId, String changeKind) {
    public TextualReconciliationEdit {
        documentId = text(documentId);
        replacement = replacement == null ? "" : replacement;
        semanticId = text(semanticId);
        changeKind = text(changeKind);
        range = range == null ? new TextRange(null, null) : range;
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TextualReconciliationEdit. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
