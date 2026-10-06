package com.example.editor.grammar.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GrammarTextualEditorCustomizationTests {
    @Test void sanitizesAndRoundTripsDeclarativeCustomization(){
        var value=new GrammarTextualEditorCustomization("Consolas, monospace",17,25,"#102030","#f8fafc","person person company invalid!",
                Map.of("KEYWORD","#123456|1|0","UNKNOWN","#ffffff|1|1"),
                Map.of("RULE:Person","#334455|1|0","TOKEN:ID","#556677|0|1","SCRIPT:bad","#ffffff|1|1","RULE:bad key!","#ffffff|1|1"),
                Map.of("Person",new GrammarTextualEditorCustomization.HoverDefinition("Persona","Elemento persona"),"bad key!",new GrammarTextualEditorCustomization.HoverDefinition("x","y")));
        Map<String,String> settings=value.applyTo(Map.of("unrelated","kept","hydra.textual.old","removed"));
        var grammar=new GrammarDefinitionModel("1.0","g","g",GrammarTechnology.LANGIUM,GrammarAuthoringMode.GRAMMAR_FIRST,"","","","Model",java.util.List.of(),GrammarSemanticBindingMap.empty(),"",settings,Map.of());
        var restored=GrammarTextualEditorCustomization.from(grammar);
        assertThat(restored.fontFamily()).isEqualTo("Consolas, monospace");
        assertThat(restored.fontSize()).isEqualTo(17);
        assertThat(restored.keywords()).isEqualTo("person,company");
        assertThat(restored.roleStyles()).containsEntry("KEYWORD","#123456|1|0").doesNotContainKey("UNKNOWN");
        assertThat(restored.symbolStyles()).containsEntry("RULE:Person","#334455|1|0").containsEntry("TERMINAL:ID","#556677|0|1").doesNotContainKeys("SCRIPT:bad","RULE:bad key!");
        assertThat(settings).containsEntry("hydra.textual.symbol.RULE.Person","#334455|1|0").containsEntry("hydra.textual.symbol.TERMINAL.ID","#556677|0|1");
        assertThat(restored.hovers()).containsKey("Person").doesNotContainKey("bad key!");
        assertThat(settings).containsEntry("unrelated","kept").doesNotContainKey("hydra.textual.old");
    }

    @Test void projectsPersistedRuleCustomizationOntoGeneratedModelLexemes(){
        var document=new GrammarSourceDocument("main","main.langium","langium","grammar G\nPerson: 'person' name=ID;\nterminal ID: /[a-z]+/;","",true,false);
        var grammar=new GrammarDefinitionModel("1.0","g","g",GrammarTechnology.LANGIUM,GrammarAuthoringMode.GRAMMAR_FIRST,"","","main","Person",java.util.List.of(document),GrammarSemanticBindingMap.empty(),"",Map.of(
                "hydra.textual.symbol.RULE.Person","#112233|1|0",
                "hydra.textual.hover.Person.title","Person declaration",
                "hydra.textual.hover.Person.description","Creates one person."),Map.of());
        var projected=GrammarTextualEditorCustomization.from(grammar);
        assertThat(projected.keywords()).contains("person");
        assertThat(projected.symbolStyles()).containsEntry("RULE:person","#112233|1|0");
        assertThat(projected.hovers()).containsEntry("person",new GrammarTextualEditorCustomization.HoverDefinition("Person declaration","Creates one person."));
    }

    @Test void boundsUnsafeValuesInsteadOfAcceptingExecutableStyling(){
        var value=new GrammarTextualEditorCustomization("url(javascript:alert(1))",999,-1,"red","#fff","",Map.of(),Map.of("RULE:Person","red|1|1"),Map.of());
        assertThat(value.fontSize()).isEqualTo(14);
        assertThat(value.lineHeight()).isEqualTo(21);
        assertThat(value.foreground()).isEqualTo("#1f2937");
        assertThat(value.background()).isEqualTo("#ffffff");
        assertThat(value.fontFamily()).doesNotContain("javascript");
        assertThat(value.symbolStyles()).containsEntry("RULE:Person","#7c3aed|1|1");
    }
}
