package com.example.editor.textual.interaction;

/** Rango plegable 0-based compatible con Monaco/LSP. */
public record TextualFoldingRange(int startLine,int endLine,String kind,String semanticId){
    public TextualFoldingRange{startLine=Math.max(0,startLine);endLine=Math.max(startLine,endLine);kind=kind==null?"region":kind.trim();semanticId=semanticId==null?"":semanticId.trim();}
}
