package com.example.editor.grammar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.grammar.application.GrammarSourceAnalyzer;
import com.example.editor.grammar.core.GrammarAnalysisMode;
import com.example.editor.grammar.core.GrammarAnalysisRequest;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.diagnostic.GrammarTextEdit;

class GrammarSourceAnalyzerLexicalIsolationTests {

    @Test
    void langiumTerminalRegexDoesNotCreateFalseReferencesOrCrossReferenceSymbols() {
        String source = """
                grammar Main
                entry Model: elements+=Element*;
                Element: 'element' name=ID;
                hidden terminal WS: /\\s+/;
                terminal ID: /[_a-zA-Z][\\w_]*/;
                """;
        var result = analyze(source);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.symbols()).noneMatch(symbol -> symbol.kind().equals("CROSS_REFERENCE"));
        assertThat(result.symbols()).noneMatch(symbol -> symbol.name().equals("_a") || symbol.name().equals("w_"));
    }

    @Test
    void realUnresolvedRuleOutsideRegexRemainsDiagnosed() {
        String source = """
                grammar Main
                entry Model: elements+=Missing*;
                terminal ID: /[_a-zA-Z][\\w_]*/;
                """;
        var result = analyze(source);

        assertThat(result.diagnostics()).anyMatch(diagnostic -> diagnostic.code().startsWith("REFERENCE_")
                && diagnostic.message().contains("Missing"));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.diagnostics()).noneMatch(diagnostic -> diagnostic.message().contains("_a")
                || diagnostic.message().contains("w_"));
    }

    @Test
    void anUnresolvedReferenceWithARealCandidateAnalysesCleanAfterApplyingItsOwnRealCodeAction() {
        String source = """
                grammar Main
                entry Model: elements+=Elemant*;
                Element: 'element' name=ID;
                hidden terminal WS: /\\s+/;
                terminal ID: /[_a-zA-Z][\\w_]*/;
                """;
        var result = analyze(source);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.diagnostics()).anyMatch(diagnostic -> diagnostic.code().equals("REFERENCE_NOT_FOUND")
                && diagnostic.message().contains("Elemant"));
        assertThat(result.codeActions()).isNotEmpty();
        GrammarTextEdit edit = result.codeActions().get(0).edits().get(0);
        assertThat(edit.replacement()).isEqualTo("Element");

        String corrected = applyEdit(source, edit);
        var reanalyzed = analyze(corrected);

        assertThat(reanalyzed.hasErrors()).isFalse();
        assertThat(reanalyzed.diagnostics()).isEmpty();
    }

    private static String applyEdit(String source, GrammarTextEdit edit) {
        String[] lines = source.split("\n", -1);
        int startOffset = 0;
        for (int i = 0; i < edit.range().start().line(); i++) startOffset += lines[i].length() + 1;
        startOffset += edit.range().start().column();
        int endOffset = 0;
        for (int i = 0; i < edit.range().end().line(); i++) endOffset += lines[i].length() + 1;
        endOffset += edit.range().end().column();
        return source.substring(0, startOffset) + edit.replacement() + source.substring(endOffset);
    }


    @Test
    void antlrLexerCharacterSetsDoNotBecomeFalseRuleReferences() {
        String source = """
                grammar Main;
                model: element* EOF;
                element: ID;
                ID: [a-zA-Z_] [a-zA-Z_0-9]*;
                WS: [ \t\r\n]+ -> skip;
                LINE_COMMENT: '//' ~[\r\n]* -> skip;
                """;
        var result = analyze(source, GrammarTechnology.ANTLR, "antlr", "main.g4", "model");

        assertThat(result.diagnostics()).noneMatch(diagnostic -> diagnostic.severity().name().equals("ERROR"));
        assertThat(result.diagnostics()).noneMatch(diagnostic -> diagnostic.message().contains("\"a\"")
                || diagnostic.message().contains("\"zA\"") || diagnostic.message().contains("\"r\""));
    }

    @Test
    void xtextQualifiedCrossReferencesResolveAgainstTheLocalSemanticType() {
        String source = """
                grammar generated.Main with org.eclipse.xtext.common.Terminals
                import "urn:main" as model
                entry Model: elements+=Element*;
                Element: Person;
                Person returns model::Person: 'person' name=ID ('friend' friend=[model::Person|ID])?;
                """;
        var result = analyze(source, GrammarTechnology.XTEXT, "xtext", "main.xtext", "Model");

        assertThat(result.diagnostics()).noneMatch(diagnostic -> diagnostic.severity().name().equals("ERROR"));
        assertThat(result.diagnostics()).noneMatch(diagnostic -> diagnostic.message().contains("model"));
    }

    @Test
    void antlrGrammarFirstProducesCandidateSemanticBindingsFromParserRules() {
        String source = """
                grammar Main;
                model: element* EOF;
                element: 'element' ID ';';
                ID: [a-zA-Z_] [a-zA-Z_0-9]*;
                WS: [ \t\r\n]+ -> skip;
                """;
        var result = analyze(source, GrammarTechnology.ANTLR, "antlr", "Main.g4", "model");

        assertThat(result.diagnostics()).noneMatch(diagnostic -> diagnostic.severity().name().equals("ERROR"));
        assertThat(result.semanticCandidate().bindings()).extracting(binding -> binding.semanticElementId())
                .containsExactlyInAnyOrder("model", "element");
        assertThat(result.semanticCandidate().bindings()).allMatch(binding -> binding.provenance().equals("HYDRA_ANTLR_RULE_INFERENCE"));
    }

    private static com.example.editor.grammar.core.GrammarAnalysisResult analyze(String source) {
        return analyze(source, GrammarTechnology.LANGIUM, "langium", "main.langium", "Model");
    }

    private static com.example.editor.grammar.core.GrammarAnalysisResult analyze(String source, GrammarTechnology technology,
            String languageId, String relativePath, String entryRule) {
        var document = new GrammarSourceDocument("main", relativePath, languageId, source, "", true, false);
        String profile = switch (technology) { case XTEXT -> "xtext-2.44-lsp"; case ANTLR -> "antlr-4.13.2-java"; default -> "langium-4.3-browser"; };
        var grammar = new GrammarDefinitionModel("1.0", "main-language", "Main language", technology,
                GrammarAuthoringMode.GRAMMAR_FIRST, "", profile, "main", entryRule, List.of(document),
                GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());
        return new GrammarSourceAnalyzer().analyze(grammar,
                new GrammarAnalysisRequest(grammar.id(), 1, GrammarAnalysisMode.NORMAL, List.of("main"),
                        grammar.documents(), grammar.toolchainProfileId()));
    }
}
