package com.example.editor.grammar.core;
import java.util.List;
import java.util.Map;
public record GrammarDefinitionModel(String schemaVersion, String id, String name, GrammarTechnology technology, GrammarAuthoringMode authoringMode, String metamodelId, String toolchainProfileId, String entryDocumentId, String entryRule, List<GrammarSourceDocument> documents, GrammarSemanticBindingMap semanticBindings, String syntaxStyleDefinitionId, Map<String,String> settings, Map<String,String> annotations) {
    public GrammarDefinitionModel {
        schemaVersion=text(schemaVersion); if(schemaVersion.isEmpty()) schemaVersion="1.0"; id=text(id); name=text(name); technology=technology==null?GrammarTechnology.LANGIUM:technology; authoringMode=authoringMode==null?GrammarAuthoringMode.GRAMMAR_FIRST:authoringMode; metamodelId=text(metamodelId); toolchainProfileId=text(toolchainProfileId); entryDocumentId=text(entryDocumentId); entryRule=text(entryRule); documents=documents==null?List.of():List.copyOf(documents); semanticBindings=semanticBindings==null?GrammarSemanticBindingMap.empty():semanticBindings; syntaxStyleDefinitionId=text(syntaxStyleDefinitionId); settings=settings==null?Map.of():Map.copyOf(settings); annotations=annotations==null?Map.of():Map.copyOf(annotations);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarDefinitionModel. Entradas: v: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String v){return v==null?"":v.trim();}
}
