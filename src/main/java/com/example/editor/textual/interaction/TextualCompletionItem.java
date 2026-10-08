package com.example.editor.textual.interaction;

import java.util.Map;

/** Propuesta neutral de autocompletado. */
public record TextualCompletionItem(String label,String kind,String detail,String insertText,String semanticId,int score,Map<String,String> metadata){
    public TextualCompletionItem{label=text(label);kind=text(kind);detail=text(detail);insertText=insertText==null?label:insertText;semanticId=text(semanticId);score=Math.max(0,score);metadata=metadata==null?Map.of():Map.copyOf(metadata);}
    /** Descripción: Normaliza texto opcional de una propuesta. Entradas: value: valor de entrada. Salida: devuelve texto no nulo y recortado. */ private static String text(String value){return value==null?"":value.trim();}
}
