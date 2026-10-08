package com.example.editor.textual.interaction;

import java.util.List;
import java.util.Map;
import com.example.editor.grammar.core.TextRange;

/** Símbolo de documento derivado del modelo canónico y su source map. */
public record TextualDocumentSymbol(String name,String kind,String semanticId,TextRange range,List<TextualDocumentSymbol> children,Map<String,String> metadata){
    public TextualDocumentSymbol{name=text(name);kind=text(kind);semanticId=text(semanticId);children=children==null?List.of():List.copyOf(children);metadata=metadata==null?Map.of():Map.copyOf(metadata);if(range==null)throw new IllegalArgumentException("Document symbol requires a range");}
    /** Descripción: Normaliza texto opcional de un símbolo. Entradas: value: valor de entrada. Salida: devuelve texto no nulo y recortado. */ private static String text(String value){return value==null?"":value.trim();}
}
