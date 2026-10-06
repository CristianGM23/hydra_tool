package com.example.editor.grammar.diagnostic;

import com.example.editor.grammar.core.TextRange;

/** Edición textual declarativa. Nunca contiene código ejecutable. */
public record GrammarTextEdit(String documentId, TextRange range, String replacement) {
    public GrammarTextEdit {
        documentId = documentId == null ? "" : documentId.trim();
        replacement = replacement == null ? "" : replacement;
        if (range == null) throw new IllegalArgumentException("A grammar text edit requires a range");
    }
}
