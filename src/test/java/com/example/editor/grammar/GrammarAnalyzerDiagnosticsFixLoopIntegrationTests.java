package com.example.editor.grammar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.editor.grammar.application.GrammarSourceAnalyzer;
import com.example.editor.grammar.core.GrammarAnalysisMode;
import com.example.editor.grammar.core.GrammarAnalysisRequest;
import com.example.editor.grammar.core.GrammarAnalysisResult;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarSemanticBinding;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSeverity;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.diagnostic.GrammarCodeAction;
import com.example.editor.grammar.diagnostic.GrammarDiagnosticCatalog;
import com.example.editor.grammar.diagnostic.GrammarTextEdit;
import com.example.editor.grammar.diagnostic.ReferenceDiagnosticCode;

/**
 * Evidencia real (sin mocks) del ciclo "analizar - proponer correccion - aplicar - reanalizar" del
 * analizador neutral de gramaticas. Etapas certificadas: grammar_define_language.an (correspondencias
 * candidatas con confianza 0,60 / 0,72 / 0,82 marcadas como candidatas) y
 * grammar_diagnostics_actions.sa (la correccion de recursion solo es automatica cuando es segura).
 * El aplicador de ediciones del propio test es independiente del codigo de produccion.
 */
class GrammarAnalyzerDiagnosticsFixLoopIntegrationTests {

    private static GrammarDefinitionModel grammar(GrammarTechnology technology, GrammarAuthoringMode mode, String metamodelId,
            String entryRule, String source) {
        String extension = technology == GrammarTechnology.ANTLR ? "g4" : "langium";
        String language = technology == GrammarTechnology.ANTLR ? "antlr" : "langium";
        String profile = technology == GrammarTechnology.ANTLR ? "antlr-4.13.2-java" : "langium-4.3-browser";
        GrammarSourceDocument document = new GrammarSourceDocument("main", "main." + extension, language, source, "", true, false);
        return new GrammarDefinitionModel("1.0", "fix-loop", "Fix loop", technology, mode, metamodelId, profile, "main",
                entryRule, List.of(document), GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of("hydra.entryRule.mode", "AUTO"));
    }

    private static GrammarAnalysisResult analyze(GrammarDefinitionModel grammar, long revision) {
        return new GrammarSourceAnalyzer().analyze(grammar, new GrammarAnalysisRequest(grammar.id(), revision,
                GrammarAnalysisMode.NORMAL, List.of("main"), grammar.documents(), grammar.toolchainProfileId()));
    }

    /** Aplicador de ediciones independiente: line/column son 0-based y las lineas se separan por salto de linea simple. */
    private static String apply(String original, List<GrammarTextEdit> edits) {
        List<GrammarTextEdit> ordered = new ArrayList<>(edits);
        ordered.sort((left, right) -> offset(original, right.range().start().line(), right.range().start().column())
                - offset(original, left.range().start().line(), left.range().start().column()));
        String result = original;
        for (GrammarTextEdit edit : ordered) {
            int start = offset(original, edit.range().start().line(), edit.range().start().column());
            int end = offset(original, edit.range().end().line(), edit.range().end().column());
            result = result.substring(0, start) + edit.replacement() + result.substring(end);
        }
        return result;
    }

    private static int offset(String source, int line, int column) {
        int offset = 0;
        for (int current = 0; current < line; current++) offset = source.indexOf('\n', offset) + 1;
        return offset + column;
    }

    @Test
    @DisplayName("grammar_define_language.an: las correspondencias candidatas llevan confianza 0,72 / 0,60 / 0,82 y piden revision")
    void candidateBindingsCarryTheDocumentedConfidenceAndAreFlaggedAsCandidates() {
        GrammarAnalysisResult langiumFirst = analyze(grammar(GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST, "", "Model",
                "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\n"), 1);
        GrammarAnalysisResult antlrFirst = analyze(grammar(GrammarTechnology.ANTLR, GrammarAuthoringMode.GRAMMAR_FIRST, "", "expr",
                "grammar Calc;\nexpr: term ('+' term)*;\nterm: INT;\nINT: [0-9]+;\n"), 1);
        GrammarAnalysisResult langiumLinked = analyze(grammar(GrammarTechnology.LANGIUM, GrammarAuthoringMode.LINKED, "stable-metamodel", "Model",
                "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\n"), 1);

        assertThat(langiumFirst.semanticCandidate().bindings()).extracting(GrammarSemanticBinding::grammarElementKind).containsOnly("RULE");
        assertThat(langiumFirst.semanticCandidate().bindings()).extracting(GrammarSemanticBinding::semanticElementId)
                .containsExactlyInAnyOrder("Model", "Node");
        assertThat(langiumFirst.semanticCandidate().bindings()).allSatisfy(binding -> assertThat(binding.confidence()).isCloseTo(0.72d, within(1e-9)));

        assertThat(antlrFirst.semanticCandidate().bindings()).extracting(GrammarSemanticBinding::semanticElementId)
                .containsExactlyInAnyOrder("expr", "term");
        assertThat(antlrFirst.semanticCandidate().bindings()).allSatisfy(binding -> assertThat(binding.confidence()).isCloseTo(0.60d, within(1e-9)));

        assertThat(langiumLinked.semanticCandidate().bindings()).hasSize(2);
        assertThat(langiumLinked.semanticCandidate().bindings()).allSatisfy(binding -> {
            assertThat(binding.confidence()).isCloseTo(0.82d, within(1e-9));
            assertThat(binding.metadata()).containsEntry("metamodelId", "stable-metamodel");
        });

        for (GrammarAnalysisResult result : List.of(langiumFirst, antlrFirst, langiumLinked)) {
            assertThat(result.semanticCandidate().annotations()).containsEntry("candidateOnly", "true").containsEntry("requiresReview", "true");
            assertThat(result.semanticCandidate().bindings()).allSatisfy(binding -> assertThat(binding.metadata()).containsEntry("inferred", "true"));
        }
    }

    @Test
    @DisplayName("grammar_diagnostics_actions.sa: la recursion directa se corrige de forma automatica y segura; reanalizar deja cero errores")
    void directLeftRecursionOffersASafeAutomaticFixThatClearsTheErrorWhenApplied() {
        String source = "grammar Calc\nentry Model: expression=Expression;\nExpression: Expression '+' INT | INT;\n";
        GrammarDefinitionModel broken = grammar(GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST, "", "Model", source);

        GrammarAnalysisResult before = analyze(broken, 1);

        assertThat(before.hasErrors()).isTrue();
        assertThat(before.diagnostics()).anyMatch(item -> item.code().equals(GrammarDiagnosticCatalog.LEFT_RECURSION)
                && item.severity() == GrammarSeverity.ERROR);
        GrammarCodeAction fix = before.codeActions().stream().filter(action -> action.kind().equals("quickfix.grammar.left-recursion"))
                .findFirst().orElseThrow();
        assertThat(fix.safeAutomatic()).isTrue();
        assertThat(fix.preferred()).isEqualTo(fix.safeAutomatic());
        assertThat(fix.metadata()).containsEntry("automaticResolution", "true").containsEntry("semanticReviewRequired", "false");
        assertThat(fix.edits()).hasSize(1);

        String repaired = apply(source, fix.edits());
        assertThat(repaired).isEqualTo("grammar Calc\nentry Model: expression=Expression;\nExpression:INT ('+' INT)*;\n");

        GrammarAnalysisResult after = analyze(grammar(GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST, "", "Model", repaired), 2);
        assertThat(after.hasErrors()).isFalse();
        assertThat(after.metadata()).containsEntry("recursion.leftCount", "0");
        assertThat(after.codeActions()).noneMatch(action -> action.kind().equals("quickfix.grammar.left-recursion"));
    }

    @Test
    @DisplayName("grammar_diagnostics_actions.sa: la recursion indirecta es un error sin correccion automatica")
    void indirectLeftRecursionRemainsABlockingErrorWithoutAnyAutomaticFix() {
        GrammarAnalysisResult result = analyze(grammar(GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST, "", "Model",
                "grammar Main\nentry Model: value=A;\nA: B 'a' | 'x';\nB: A 'b' | 'y';\n"), 1);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.diagnostics()).anyMatch(item -> item.code().equals(GrammarDiagnosticCatalog.LEFT_RECURSION)
                && item.severity() == GrammarSeverity.ERROR && item.codeActionIds().isEmpty());
        assertThat(result.codeActions()).noneMatch(action -> action.kind().equals("quickfix.grammar.left-recursion"));
        assertThat(result.codeActions()).noneMatch(GrammarCodeAction::safeAutomatic);
    }

    @Test
    @DisplayName("grammar_diagnostics_actions.sa: una referencia mal escrita ofrece una correccion que, aplicada, elimina el error")
    void aMistypedReferenceOffersAReplacementThatClearsTheErrorWhenApplied() {
        String source = "grammar Stable\nentry Model: nodes+=node*;\nNode: 'node' name=ID;\n";

        GrammarAnalysisResult before = analyze(grammar(GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST, "", "Model", source), 1);

        assertThat(before.diagnostics()).anyMatch(item -> item.code().equals(ReferenceDiagnosticCode.REFERENCE_CASE_MISMATCH.name())
                && item.severity() == GrammarSeverity.ERROR);
        GrammarCodeAction fix = before.codeActions().stream().filter(action -> action.kind().equals("quickfix.reference.replace"))
                .findFirst().orElseThrow();
        assertThat(fix.edits()).singleElement().satisfies(edit -> assertThat(edit.replacement()).isEqualTo("Node"));

        String repaired = apply(source, fix.edits());
        assertThat(repaired).isEqualTo("grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\n");
        GrammarAnalysisResult after = analyze(grammar(GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST, "", "Model", repaired), 2);
        assertThat(after.hasErrors()).isFalse();
    }
}
