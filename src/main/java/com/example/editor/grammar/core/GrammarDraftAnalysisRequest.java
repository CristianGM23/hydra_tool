package com.example.editor.grammar.core;
/** Live-analysis envelope used before a new grammar has been persisted. */
public record GrammarDraftAnalysisRequest(GrammarDefinitionModel grammar, GrammarAnalysisRequest analysis) {
    public GrammarDraftAnalysisRequest {
        if (grammar == null) throw new IllegalArgumentException("Draft grammar is required");
        if (analysis == null) analysis = new GrammarAnalysisRequest(grammar.id(), 0, GrammarAnalysisMode.FAST, java.util.List.of(), grammar.documents(), grammar.toolchainProfileId());
    }
}
