package com.example.editor.grammar.diagnostic;

import java.util.List;
import java.util.Map;

/** Quick fix neutral con precondiciones y ediciones estructuradas. */
public record GrammarCodeAction(
        String id,
        String title,
        String kind,
        boolean preferred,
        boolean safeAutomatic,
        List<GrammarTextEdit> edits,
        Map<String, String> preconditions,
        Map<String, String> metadata) {
    public GrammarCodeAction {
        id = text(id);
        title = text(title);
        kind = text(kind);
        edits = edits == null ? List.of() : List.copyOf(edits);
        preconditions = preconditions == null ? Map.of() : Map.copyOf(preconditions);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarCodeAction. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
