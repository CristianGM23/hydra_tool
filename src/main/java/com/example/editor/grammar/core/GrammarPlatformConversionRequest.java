package com.example.editor.grammar.core;

/** Solicitud no destructiva de previsualización de conversión de una gramática. */
public record GrammarPlatformConversionRequest(GrammarDefinitionModel grammar, GrammarTechnology targetTechnology) {
    public GrammarPlatformConversionRequest {
        if(grammar==null)throw new IllegalArgumentException("grammar must not be null");
        if(targetTechnology==null)throw new IllegalArgumentException("targetTechnology must not be null");
    }
}
