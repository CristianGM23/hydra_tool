package com.example.editor.textual.interaction;

import java.util.List;
import java.util.Map;
import com.example.editor.grammar.diagnostic.GrammarTextEdit;

/** Plan preview-first de renombrado; no muta documentos por sí mismo. */
public record TextualRenamePlan(boolean safe,String oldId,String newId,List<GrammarTextEdit> edits,List<String> conflicts,List<String> affectedDocuments,Map<String,String> metadata){
    public TextualRenamePlan{oldId=text(oldId);newId=text(newId);edits=edits==null?List.of():List.copyOf(edits);conflicts=conflicts==null?List.of():List.copyOf(conflicts);affectedDocuments=affectedDocuments==null?List.of():List.copyOf(affectedDocuments);metadata=metadata==null?Map.of():Map.copyOf(metadata);}
    /** Descripción: Normaliza texto opcional del plan de renombrado. Entradas: value: valor de entrada. Salida: devuelve texto no nulo y recortado. */ private static String text(String value){return value==null?"":value.trim();}
}
