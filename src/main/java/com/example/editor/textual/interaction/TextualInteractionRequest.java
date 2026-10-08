package com.example.editor.textual.interaction;

/** Posición y límites de una consulta de edición textual. */
public record TextualInteractionRequest(String documentId,int line,int column,String prefix,int maximumResults){
    public TextualInteractionRequest{documentId=documentId==null?"":documentId.trim();line=Math.max(0,line);column=Math.max(0,column);prefix=prefix==null?"":prefix;maximumResults=maximumResults<=0?100:Math.min(1000,maximumResults);}
}
