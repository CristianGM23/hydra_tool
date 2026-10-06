package com.example.editor.textual.runtime;
import java.util.List;
import com.example.editor.textual.source.TextualModelSourceDocument;
public record ParseTextualModelRequest(long draftRevision,List<TextualModelSourceDocument>documents){public ParseTextualModelRequest{draftRevision=Math.max(0,draftRevision);documents=documents==null?List.of():List.copyOf(documents);}}
