package com.example.editor.textual.interaction;

import java.util.Map;
import com.example.editor.grammar.core.TextRange;

/** Información contextual neutral para hover/inspector. */
public record TextualHoverInfo(boolean found,String title,String detail,String semanticId,String semanticKind,String documentId,TextRange range,Map<String,String> metadata){
    public TextualHoverInfo{title=text(title);detail=text(detail);semanticId=text(semanticId);semanticKind=text(semanticKind);documentId=text(documentId);metadata=metadata==null?Map.of():Map.copyOf(metadata);}
    /** Descripción: Normaliza texto opcional del hover. Entradas: value: valor de entrada. Salida: devuelve texto no nulo y recortado. */ private static String text(String value){return value==null?"":value.trim();}
    /** Descripción: Construye un resultado vacío y estable cuando no existe semántica bajo el cursor. Entradas: no requiere parámetros. Salida: devuelve un hover con found=false. */ public static TextualHoverInfo empty(){return new TextualHoverInfo(false,"","","","","",null,Map.of());}
}
