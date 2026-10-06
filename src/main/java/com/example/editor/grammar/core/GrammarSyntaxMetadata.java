package com.example.editor.grammar.core;
import java.util.List;
import java.util.Map;
public record GrammarSyntaxMetadata(String languageId, List<String> keywords, Map<String,String> tokenRoles, List<String> fileExtensions) {
    public GrammarSyntaxMetadata { languageId=languageId==null?"":languageId.trim(); keywords=keywords==null?List.of():List.copyOf(keywords); tokenRoles=tokenRoles==null?Map.of():Map.copyOf(tokenRoles); fileExtensions=fileExtensions==null?List.of():List.copyOf(fileExtensions); }
}
