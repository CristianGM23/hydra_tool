package com.example.editor.grammar.visualization;

import com.example.editor.grammar.core.TextRange;

/** Navigable source location for an inferred diagram element. */
public record GrammarDiagramSourceRef(String documentId, TextRange range, String grammarElementId, String ruleName) {
    public GrammarDiagramSourceRef {
        documentId = text(documentId);
        range = range == null ? new TextRange(null, null) : range;
        grammarElementId = text(grammarElementId);
        ruleName = text(ruleName);
    }
    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de GrammarDiagramSourceRef. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static GrammarDiagramSourceRef empty(){ return new GrammarDiagramSourceRef("", new TextRange(null,null), "", ""); }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarDiagramSourceRef. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){ return value == null ? "" : value.trim(); }
}
