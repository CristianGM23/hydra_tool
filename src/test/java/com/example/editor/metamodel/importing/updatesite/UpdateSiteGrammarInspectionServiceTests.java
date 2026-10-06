package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateSiteGrammarInspectionServiceTests {
    private final UpdateSiteGrammarInspectionService service = new UpdateSiteGrammarInspectionService();

    @Test
    void explainsXtextRulesAssignmentsReferencesAndPossibleLeftRecursion() {
        String grammar = """
                grammar org.example.Library with org.eclipse.xtext.common.Terminals
                generate library "urn:library"
                Model: elements+=Element*;
                Element: Element name=ID | 'book' name=ID author=[Author|ID];
                Author: 'author' name=ID;
                terminal SL_COMMENT: '//' !('\\n'|'\\r')* ('\\r'? '\\n')?;
                """;
        UpdateSiteRepositoryContent repository = new UpdateSiteRepositoryContent(
                Map.of("plugins/library.jar!/src/Library.xtext", grammar.getBytes(StandardCharsets.UTF_8)), "f", grammar.length());

        List<UpdateSiteGrammarInspection> inspections = service.inspect(repository);

        assertThat(inspections).singleElement().satisfies(result -> {
            assertThat(result.language()).isEqualTo("Xtext");
            assertThat(result.grammarName()).isEqualTo("org.example.Library");
            assertThat(result.parserRules()).isGreaterThanOrEqualTo(3);
            assertThat(result.terminalRules()).isEqualTo(1);
            assertThat(result.assignments()).isGreaterThanOrEqualTo(4);
            assertThat(result.crossReferences()).isEqualTo(1);
            assertThat(result.possibleLeftRecursions()).contains("Element");
            assertThat(result.diagnostics()).anyMatch(value -> value.contains("left recursion"));
        });
    }

    @Test
    void inspectsLangiumWithoutPretendingAnIncompleteFileWasRecoveredExactly() {
        String grammar = """
                grammar Library
                entry Model: books+=Book*;
                Book: 'book' name=ID ('by' author=[Author:ID])?;
                terminal ID returns string: /[_a-zA-Z][\\w_]*/;
                """;
        UpdateSiteRepositoryContent repository = new UpdateSiteRepositoryContent(
                Map.of("language/library.langium", grammar.getBytes(StandardCharsets.UTF_8)), "f", grammar.length());

        UpdateSiteGrammarInspection result = service.inspect(repository).getFirst();

        assertThat(result.language()).isEqualTo("Langium");
        assertThat(result.parserRules()).isGreaterThanOrEqualTo(2);
        assertThat(result.crossReferences()).isEqualTo(1);
        assertThat(result.duplicateRules()).isEmpty();
    }
}
