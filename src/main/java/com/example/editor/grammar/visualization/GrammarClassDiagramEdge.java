package com.example.editor.grammar.visualization;

import java.util.Map;

/** Provider-independent edge in the inferred class diagram. */
public record GrammarClassDiagramEdge(String id, String sourceId, String targetId, String kind, String label,
        String sourceMultiplicity, String targetMultiplicity, int diagnosticCount, GrammarDiagramSourceRef source,
        Map<String,String> metadata) {
    public GrammarClassDiagramEdge {
        id=text(id);sourceId=text(sourceId);targetId=text(targetId);kind=text(kind);label=text(label);
        sourceMultiplicity=text(sourceMultiplicity);targetMultiplicity=text(targetMultiplicity);
        diagnosticCount=Math.max(0,diagnosticCount);source=source==null?GrammarDiagramSourceRef.empty():source;
        metadata=metadata==null?Map.of():Map.copyOf(metadata);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarClassDiagramEdge. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
}
