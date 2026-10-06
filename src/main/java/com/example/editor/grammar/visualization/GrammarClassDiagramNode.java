package com.example.editor.grammar.visualization;

import java.util.List;
import java.util.Map;

/** Provider-independent node in the inferred class diagram. */
public record GrammarClassDiagramNode(String id, String semanticId, String name, String kind, boolean root,
        double confidence, int diagnosticCount, List<GrammarDiagramFeature> features, GrammarDiagramSourceRef source,
        Map<String,String> metadata) {
    public GrammarClassDiagramNode {
        id=text(id);semanticId=text(semanticId);name=text(name);kind=text(kind);confidence=clamp(confidence);
        diagnosticCount=Math.max(0,diagnosticCount);features=features==null?List.of():List.copyOf(features);
        source=source==null?GrammarDiagramSourceRef.empty():source;metadata=metadata==null?Map.of():Map.copyOf(metadata);
    }
    /** Descripción: Ejecuta la operación «clamp» correspondiente a la responsabilidad de GrammarClassDiagramNode. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static double clamp(double value){return Math.max(0d,Math.min(1d,value));}
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarClassDiagramNode. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
}
