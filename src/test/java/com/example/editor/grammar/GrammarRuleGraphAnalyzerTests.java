package com.example.editor.grammar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.grammar.application.GrammarRuleGraphAnalyzer;
import com.example.editor.grammar.application.GrammarSourceAnalyzer;
import com.example.editor.grammar.core.GrammarAnalysisMode;
import com.example.editor.grammar.core.GrammarAnalysisRequest;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.diagnostic.GrammarDiagnosticCatalog;

class GrammarRuleGraphAnalyzerTests {

    @Test
    void explicitLangiumEntryWinsAndAllParserRulesRemainSelectable() {
        var graph = GrammarRuleGraphAnalyzer.analyze(grammar(GrammarTechnology.LANGIUM, "Model", """
                grammar Main
                Helper: 'helper';
                entry Model: elements+=Element*;
                Element: 'element' name=ID;
                """));

        assertThat(graph.entry().rule()).isEqualTo("Model");
        assertThat(graph.entry().method()).isEqualTo("EXPLICIT_ENTRY");
        assertThat(graph.entry().confidence()).isEqualTo(1d);
        assertThat(graph.entry().candidates()).containsExactly("Model", "Helper", "Element");
    }

    @Test
    void xtextUsesNativeFirstParserRuleConventionWithoutBlockingManualOverride() {
        var grammar = grammar(GrammarTechnology.XTEXT, "Element", """
                grammar org.example.Main with org.eclipse.xtext.common.Terminals
                Model: elements+=Element*;
                Element: 'element' name=ID;
                """);
        var result = analyze(grammar);

        assertThat(result.metadata()).containsEntry("entryRule.inferred", "Model");
        assertThat(result.metadata()).containsEntry("entryRule.method", "XTEXT_FIRST_PARSER_RULE");
        assertThat(result.diagnostics()).noneMatch(item -> item.code().equals(GrammarDiagnosticCatalog.ENTRY_RULE_NOT_FOUND));
    }

    @Test
    void missingManualEntryIsDiagnosedAndInferredReplacementIsExposed() {
        var result = analyze(grammar(GrammarTechnology.LANGIUM, "DeletedRoot", """
                grammar Main
                entry Model: elements+=Element*;
                Element: 'element' name=ID;
                """));

        assertThat(result.metadata()).containsEntry("entryRule.inferred", "Model");
        assertThat(result.diagnostics()).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(GrammarDiagnosticCatalog.ENTRY_RULE_NOT_FOUND);
            assertThat(item.solution()).contains("Model");
        });
    }

    @Test
    void directLeftRecursionIsBlockingForLangiumAndOffersARewrite() {
        var result = analyze(grammar(GrammarTechnology.LANGIUM, "Model", """
                grammar Main
                entry Model: expression=Expression;
                Expression: Expression '+' INT | INT;
                """));

        assertThat(result.metadata()).containsEntry("recursion.leftCount", "1");
        assertThat(result.diagnostics()).anyMatch(item -> item.code().equals(GrammarDiagnosticCatalog.LEFT_RECURSION)
                && item.severity().name().equals("ERROR") && item.cause().contains("sin haber consumido"));
        assertThat(result.codeActions()).anySatisfy(action -> {
            assertThat(action.kind()).isEqualTo("quickfix.grammar.left-recursion");
            assertThat(action.edits()).singleElement().satisfies(edit -> assertThat(edit.replacement()).contains("INT").contains("*"));
        });
    }

    @Test
    void indirectLeftRecursionIsDetectedWithoutPretendingASingleRuleRewriteIsSafe() {
        var result = analyze(grammar(GrammarTechnology.LANGIUM, "Model", """
                grammar Main
                entry Model: value=A;
                A: B 'a' | 'x';
                B: A 'b' | 'y';
                """));

        assertThat(result.diagnostics()).anyMatch(item -> item.code().equals(GrammarDiagnosticCatalog.LEFT_RECURSION)
                && item.title().contains("indirecta"));
        assertThat(result.codeActions()).noneMatch(action -> action.kind().equals("quickfix.grammar.left-recursion"));
    }

    @Test
    void recursiveStructureThatConsumesInputFirstIsInformationalNotAnError() {
        var result = analyze(grammar(GrammarTechnology.LANGIUM, "Model", """
                grammar Main
                entry Model: root=Node;
                Node: 'node' name=ID ('child' child=Node)?;
                """));

        assertThat(result.diagnostics()).anyMatch(item -> item.code().equals(GrammarDiagnosticCatalog.RECURSION_SAFE)
                && item.severity().name().equals("INFO"));
        assertThat(result.diagnostics()).noneMatch(item -> item.code().equals(GrammarDiagnosticCatalog.LEFT_RECURSION));
    }

    @Test
    void directAntlr4LeftRecursionIsRecognizedAsNativeInsteadOfBeingRewritten() {
        var result = analyze(grammar(GrammarTechnology.ANTLR, "expr", """
                grammar Main;
                expr: expr '+' INT | INT;
                INT: [0-9]+;
                WS: [ \\t\\r\\n]+ -> skip;
                """));

        assertThat(result.diagnostics()).anyMatch(item -> item.code().equals(GrammarDiagnosticCatalog.LEFT_RECURSION_NATIVE)
                && item.severity().name().equals("INFO"));
        assertThat(result.codeActions()).noneMatch(action -> action.kind().equals("quickfix.grammar.left-recursion"));
    }


    @Test
    void unreachableParserRulesAreWarningsAndDoNotBlockCompilation() {
        var result = analyze(grammar(GrammarTechnology.LANGIUM, "Model", """
                grammar Main
                entry Model: elements+=Element*;
                Element: 'element' name=ID;
                Legacy: 'legacy';
                DeadA: DeadB;
                DeadB: 'dead';
                """));

        assertThat(result.diagnostics().stream().filter(item -> item.code().equals(GrammarDiagnosticCatalog.UNUSED_RULE)))
                .extracting(item -> item.metadata().get("rule"))
                .containsExactly("Legacy", "DeadA", "DeadB");
        assertThat(result.diagnostics().stream().filter(item -> item.code().equals(GrammarDiagnosticCatalog.UNUSED_RULE)))
                .allMatch(item -> item.severity().name().equals("WARNING"));
        assertThat(result.metadata()).containsEntry("unusedRules.count", "3").containsEntry("unusedRules.names", "Legacy|DeadA|DeadB");
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void explicitAdditionalEntriesKeepTheirReachableRuleIslandsInUse() {
        var result = analyze(grammar(GrammarTechnology.LANGIUM, "Model", """
                grammar Main
                entry Model: elements+=Element*;
                Element: 'element' name=ID;
                entry FragmentRoot: values+=FragmentItem*;
                FragmentItem: 'fragment' name=ID;
                Legacy: 'legacy';
                """));

        assertThat(result.diagnostics().stream().filter(item -> item.code().equals(GrammarDiagnosticCatalog.UNUSED_RULE)))
                .extracting(item -> item.metadata().get("rule"))
                .containsExactly("Legacy");
    }

    @Test
    void nullablePrefixStillExposesLeftRecursion() {
        var graph = GrammarRuleGraphAnalyzer.analyze(grammar(GrammarTechnology.LANGIUM, "A", """
                grammar Main
                entry A: ('prefix')? A | 'done';
                """));

        assertThat(graph.recursions()).anyMatch(item -> item.kind() == GrammarRuleGraphAnalyzer.RecursionKind.DIRECT_LEFT);
    }

    @Test
    void veryDeepRuleChainDoesNotDependOnJvmStackDepth() {
        int count = 20_000;
        StringBuilder source = new StringBuilder(count * 20);
        for (int i = 0; i < count; i++) {
            source.append("r").append(i).append(": ");
            if (i + 1 < count) source.append("r").append(i + 1); else source.append("'end'");
            source.append(";\n");
        }
        var graph = GrammarRuleGraphAnalyzer.analyze(grammar(GrammarTechnology.LANGIUM, "r0", source.toString()));
        assertThat(graph.rules()).hasSize(count);
        assertThat(graph.recursions()).isEmpty();
    }

    @Test
    void veryLargeStronglyConnectedGrammarDoesNotOverflowAndReportsSingleComponent() {
        int count = 10_000;
        StringBuilder source = new StringBuilder(count * 20);
        for (int i = 0; i < count; i++) {
            source.append("r").append(i).append(": r").append((i + 1) % count).append(";\n");
        }
        var graph = GrammarRuleGraphAnalyzer.analyze(grammar(GrammarTechnology.LANGIUM, "r0", source.toString()));
        assertThat(graph.rules()).hasSize(count);
        assertThat(graph.recursions()).singleElement().satisfies(recursion -> assertThat(recursion.rules()).hasSize(count));
    }

    @Test
    void deeplyNestedGroupsDoNotDependOnJvmStackDepth() {
        int depth = 25_000;
        String source = "entry Model: " + "(".repeat(depth) + "'value'" + ")".repeat(depth) + ";\n";
        var graph = GrammarRuleGraphAnalyzer.analyze(grammar(GrammarTechnology.LANGIUM, "Model", source));
        assertThat(graph.rules()).hasSize(1);
        assertThat(graph.entry().rule()).isEqualTo("Model");
    }

    private static com.example.editor.grammar.core.GrammarAnalysisResult analyze(GrammarDefinitionModel grammar) {
        return new GrammarSourceAnalyzer().analyze(grammar,
                new GrammarAnalysisRequest(grammar.id(), 7, GrammarAnalysisMode.NORMAL, List.of(grammar.entryDocumentId()),
                        grammar.documents(), grammar.toolchainProfileId()));
    }

    private static GrammarDefinitionModel grammar(GrammarTechnology technology, String selectedEntry, String source) {
        String extension = switch (technology) { case XTEXT -> "xtext"; case LANGIUM -> "langium"; case ANTLR -> "g4"; };
        String language = switch (technology) { case XTEXT -> "xtext"; case LANGIUM -> "langium"; case ANTLR -> "antlr"; };
        String toolchain = switch (technology) { case XTEXT -> "xtext-2.44-lsp"; case LANGIUM -> "langium-4.3-browser"; case ANTLR -> "antlr-4.13.2-java"; };
        var document = new GrammarSourceDocument("main", "main." + extension, language, source, "", true, false);
        return new GrammarDefinitionModel("1.0", "main-language", "Main language", technology,
                GrammarAuthoringMode.GRAMMAR_FIRST, "", toolchain, document.id(), selectedEntry, List.of(document),
                GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of("hydra.entryRule.mode", "AUTO"));
    }
}
