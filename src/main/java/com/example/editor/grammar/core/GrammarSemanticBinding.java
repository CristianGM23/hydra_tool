package com.example.editor.grammar.core;
import java.util.Map;
public record GrammarSemanticBinding(String grammarElementId, String grammarElementKind, String semanticElementId, String semanticElementKind, double confidence, String provenance, Map<String,String> metadata) {
    public GrammarSemanticBinding {
        grammarElementId=text(grammarElementId); grammarElementKind=text(grammarElementKind); semanticElementId=text(semanticElementId); semanticElementKind=text(semanticElementKind);
        confidence=Math.max(0d,Math.min(1d,confidence)); provenance=text(provenance); metadata=metadata==null?Map.of():Map.copyOf(metadata);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarSemanticBinding. Entradas: v: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String v){return v==null?"":v.trim();}
}
