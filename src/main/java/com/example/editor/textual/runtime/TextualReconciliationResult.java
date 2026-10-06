package com.example.editor.textual.runtime;

import java.util.List;
import com.example.editor.textual.source.TextualModelSourceDocument;

/** Result of projecting a canonical candidate back to source text and reparsing it. */
public record TextualReconciliationResult(boolean safe, String reason,
        List<TextualModelSourceDocument> documents, List<TextualReconciliationEdit> edits,
        TextualParseResult parsed) {
    public TextualReconciliationResult {
        reason = reason == null ? "" : reason;
        documents = documents == null ? List.of() : List.copyOf(documents);
        edits = edits == null ? List.of() : List.copyOf(edits);
    }
    /** Descripción: Ejecuta la operación «unsafe» correspondiente a la responsabilidad de TextualReconciliationResult. Entradas: reason: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static TextualReconciliationResult unsafe(String reason) {
        return new TextualReconciliationResult(false, reason, List.of(), List.of(), null);
    }
}
