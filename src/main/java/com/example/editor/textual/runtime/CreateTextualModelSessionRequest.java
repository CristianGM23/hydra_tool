package com.example.editor.textual.runtime;
import java.util.List;
import com.example.editor.textual.source.TextualModelSourceDocument;
public record CreateTextualModelSessionRequest(String editorDefinitionId,String sessionId,List<TextualModelSourceDocument>documents){public CreateTextualModelSessionRequest{editorDefinitionId=editorDefinitionId==null?"":editorDefinitionId.trim();sessionId=sessionId==null?"":sessionId.trim();documents=documents==null?List.of():List.copyOf(documents);}}
