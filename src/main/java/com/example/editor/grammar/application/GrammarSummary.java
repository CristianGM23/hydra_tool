package com.example.editor.grammar.application;
import com.example.editor.grammar.core.GrammarTechnology;
public record GrammarSummary(String id,String name,GrammarTechnology technology,int documentCount,String toolchainProfileId,String etag) { }
