package com.example.editor.grammar.visualization;

/** Attribute/reference/literal row rendered inside one inferred class-diagram node. */
public record GrammarDiagramFeature(String id, String name, String type, String multiplicity, String kind,
        GrammarDiagramSourceRef source, int diagnosticCount) {
    public GrammarDiagramFeature {
        id=text(id);name=text(name);type=text(type);multiplicity=text(multiplicity);kind=text(kind);
        source=source==null?GrammarDiagramSourceRef.empty():source;
        diagnosticCount=Math.max(0,diagnosticCount);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarDiagramFeature. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
}
