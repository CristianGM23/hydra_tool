package com.example.editor.grammar.application;
import com.example.editor.grammar.core.GrammarDefinitionModel;
public record GrammarSaveResponse(GrammarDefinitionModel grammar, String etag) { }
