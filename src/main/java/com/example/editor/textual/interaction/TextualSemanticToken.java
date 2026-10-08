package com.example.editor.textual.interaction;

import java.util.Map;

/** Token semántico neutral; la UI decide su estilo final. */
public record TextualSemanticToken(int line,int column,int length,String tokenType,String semanticId,Map<String,String> metadata){
    public TextualSemanticToken{line=Math.max(0,line);column=Math.max(0,column);length=Math.max(0,length);tokenType=tokenType==null?"":tokenType.trim();semanticId=semanticId==null?"":semanticId.trim();metadata=metadata==null?Map.of():Map.copyOf(metadata);}
}
