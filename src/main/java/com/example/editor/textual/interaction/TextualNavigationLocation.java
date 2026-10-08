package com.example.editor.textual.interaction;

import java.util.Map;
import com.example.editor.grammar.core.TextRange;

/** Localización navegable dentro de un workspace textual. */
public record TextualNavigationLocation(String documentId,TextRange range,String semanticId,String semanticKind,Map<String,String> metadata){
    public TextualNavigationLocation{documentId=text(documentId);semanticId=text(semanticId);semanticKind=text(semanticKind);metadata=metadata==null?Map.of():Map.copyOf(metadata);if(range==null)throw new IllegalArgumentException("Textual navigation location requires a range");}
    /** Descripción: Normaliza texto opcional de una localización. Entradas: value: valor de entrada. Salida: devuelve texto no nulo y recortado. */ private static String text(String value){return value==null?"":value.trim();}
}
