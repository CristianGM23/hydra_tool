package com.example.editor.grammar.core;
import java.util.List;
import java.util.Map;
public record GrammarRuntimeDescriptor(String mode, String entryRule, List<String> fileExtensions, String languageServerCommand, String browserWorkerEntry, Map<String,String> settings) {
    public GrammarRuntimeDescriptor { mode=text(mode); entryRule=text(entryRule); fileExtensions=fileExtensions==null?List.of():List.copyOf(fileExtensions); languageServerCommand=text(languageServerCommand); browserWorkerEntry=text(browserWorkerEntry); settings=settings==null?Map.of():Map.copyOf(settings); }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarRuntimeDescriptor. Entradas: v: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String v){return v==null?"":v.trim();}
}
