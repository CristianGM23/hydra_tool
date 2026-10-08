package com.example.editor.textual.interaction;

/** Solicitud declarativa de renombrado semántico. */
public record TextualRenameRequest(String documentId,int line,int column,String newId){
    public TextualRenameRequest{documentId=documentId==null?"":documentId.trim();line=Math.max(0,line);column=Math.max(0,column);newId=newId==null?"":newId.trim();}
}
