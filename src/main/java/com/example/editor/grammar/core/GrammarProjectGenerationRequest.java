package com.example.editor.grammar.core;
import java.util.List;
import java.util.Map;
public record GrammarProjectGenerationRequest(GrammarDefinitionModel grammar, GrammarToolchainProfile profile, List<GrammarSourceDocument> documents, Map<String,String> options) {
    public GrammarProjectGenerationRequest { documents=documents==null?List.of():List.copyOf(documents); options=options==null?Map.of():Map.copyOf(options); }
}
