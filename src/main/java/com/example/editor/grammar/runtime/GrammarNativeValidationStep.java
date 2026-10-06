package com.example.editor.grammar.runtime;
import java.util.List;
public record GrammarNativeValidationStep(String id,String status,long durationMillis,Integer exitCode,List<String>diagnostics){public GrammarNativeValidationStep{id=id==null?"":id;status=status==null?"FAILED":status;diagnostics=diagnostics==null?List.of():List.copyOf(diagnostics);}}
