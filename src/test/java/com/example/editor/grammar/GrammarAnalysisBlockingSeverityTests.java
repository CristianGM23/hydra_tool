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
import com.example.editor.grammar.diagnostic.GrammarDiagnostic;
import com.example.editor.grammar.diagnostic.GrammarDiagnosticCatalog;
import com.example.editor.grammar.diagnostic.GrammarTextEdit;

/**
 * Que diagnosticos del analizador real de gramaticas bloquean y cuales no, que correccion de recursion exige
 * revision humana y que metamodelo candidato se deduce en cada tecnologia. Etapas: x_grammar_to_text_model.an,
 * grammar_diagnostics_actions.sa (rama no automatica) y x_language_to_published_editor.g1 (deduccion y bucle
 * corregir-reanalizar en Xtext). Sin mocks: analizador neutral real; los textos, codigos y severidades esperados
 * son literales del test y el aplicador de ediciones es propio.
 */
class GrammarAnalysisBlockingSeverityTests {

    private static GrammarDefinitionModel grammar(GrammarTechnology technology, String entryRule, String source) {
        String extension = technology == GrammarTechnology.ANTLR ? "g4" : technology == GrammarTechnology.XTEXT ? "xtext" : "langium";
        String language = technology.name().toLowerCase();
        String profile = technology == GrammarTechnology.ANTLR ? "antlr-4.13.2-java"
                : technology == GrammarTechnology.XTEXT ? "xtext-2.44-lsp" : "langium-4.3-browser";
        GrammarSourceDocument document = new GrammarSourceDocument("main", "main." + extension, language, source, "", true, false);
        return new GrammarDefinitionModel("1.0", "severity", "Severity", technology, GrammarAuthoringMode.GRAMMAR_FIRST, "", profile, "main",
                entryRule, List.of(document), GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of("hydra.entryRule.mode", "AUTO"));
    }

    private static GrammarAnalysisResult analyze(GrammarDefinitionModel grammar, long revision) {
        return new GrammarSourceAnalyzer().analyze(grammar, new GrammarAnalysisRequest(grammar.id(), revision,
                GrammarAnalysisMode.NORMAL, List.of("main"), grammar.documents(), grammar.toolchainProfileId()));
    }

    private static GrammarDiagnostic diagnostic(GrammarAnalysisResult result, String code) {
        return result.diagnostics().stream().filter(item -> item.code().equals(code)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing diagnostic " + code + " in " + result.diagnostics().stream().map(GrammarDiagnostic::code).toList()));
    }

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
    @DisplayName("x_grammar_to_text_model.an: la recursion izquierda no soportada es un error; la soportada por ANTLR, la segura y una regla sin uso solo informan o avisan")
    void unsupportedLeftRecursionIsAnErrorWhileSupportedSafeAndUnusedRuleFindingsDoNotBlock() {
        GrammarAnalysisResult langiumLeft = analyze(grammar(GrammarTechnology.LANGIUM, "Model",
                "grammar Calc\nentry Model: expression=Expression;\nExpression: Expression '+' INT | INT;\n"), 1);
        assertThat(diagnostic(langiumLeft, GrammarDiagnosticCatalog.LEFT_RECURSION).severity()).isEqualTo(GrammarSeverity.ERROR);
        assertThat(langiumLeft.hasErrors()).isTrue();

        GrammarAnalysisResult antlrLeft = analyze(grammar(GrammarTechnology.ANTLR, "expr",
                "grammar Calc;\nexpr: expr '+' term | term;\nterm: INT;\nINT: [0-9]+;\n"), 1);
        assertThat(diagnostic(antlrLeft, GrammarDiagnosticCatalog.LEFT_RECURSION_NATIVE).severity()).isEqualTo(GrammarSeverity.INFO);
        assertThat(antlrLeft.hasErrors()).isFalse();

        GrammarAnalysisResult safe = analyze(grammar(GrammarTechnology.LANGIUM, "Model",
                "grammar Tree\nentry Model: root=Tree;\nTree: 'tree' (children+=Tree)*;\n"), 1);
        assertThat(diagnostic(safe, GrammarDiagnosticCatalog.RECURSION_SAFE).severity()).isEqualTo(GrammarSeverity.INFO);
        assertThat(safe.hasErrors()).isFalse();

        GrammarAnalysisResult unused = analyze(grammar(GrammarTechnology.LANGIUM, "Model",
                "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\nOrphan: 'orphan' name=ID;\n"), 1);
        GrammarDiagnostic warning = diagnostic(unused, GrammarDiagnosticCatalog.UNUSED_RULE);
        assertThat(warning.severity()).isEqualTo(GrammarSeverity.WARNING);
        assertThat(warning.metadata()).containsEntry("rule", "Orphan");
        assertThat(unused.hasErrors()).isFalse();
        assertThat(unused.metadata()).containsEntry("unusedRules.count", "1").containsEntry("unusedRules.names", "Orphan");

        // Las correspondencias deducidas siguen siendo solo candidatas y piden revision, haya o no errores.
        for (GrammarAnalysisResult result : List.of(langiumLeft, antlrLeft, safe, unused)) {
            assertThat(result.semanticCandidate().annotations()).containsEntry("candidateOnly", "true").containsEntry("requiresReview", "true");
        }
    }

    @Test
    @DisplayName("x_grammar_to_text_model.an (discrepancia): ademas de la recursion izquierda tambien bloquean una regla de entrada inexistente y una referencia sin resolver")
    void otherFindingsBesidesLeftRecursionAlsoBlockSoTheStageDescriptionIsIncomplete() {
        GrammarAnalysisResult missingEntry = analyze(grammar(GrammarTechnology.LANGIUM, "Missing",
                "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\n"), 1);
        assertThat(diagnostic(missingEntry, GrammarDiagnosticCatalog.ENTRY_RULE_NOT_FOUND).severity()).isEqualTo(GrammarSeverity.ERROR);
        assertThat(missingEntry.hasErrors()).isTrue();

        GrammarAnalysisResult unresolved = analyze(grammar(GrammarTechnology.LANGIUM, "Model",
                "grammar Stable\nentry Model: nodes+=Absent*;\nNode: 'node' name=ID;\n"), 1);
        assertThat(unresolved.diagnostics()).anyMatch(item -> item.code().equals("REFERENCE_NOT_FOUND") && item.severity() == GrammarSeverity.ERROR);
        assertThat(unresolved.hasErrors()).isTrue();
        assertThat(unresolved.diagnostics()).noneMatch(item -> item.code().equals(GrammarDiagnosticCatalog.LEFT_RECURSION));
    }

    @Test
    @DisplayName("grammar_diagnostics_actions.sa: si la regla tiene asignaciones la reescritura de la recursion no es automatica, exige revision y aplicada deja cero errores")
    void leftRecursionWithAssignmentsOffersANonAutomaticFixThatRequiresReview() {
        String source = "grammar Calc\nentry Model: expression=Expression;\nExpression: Expression op='+' right=INT | value=INT;\n";

        GrammarAnalysisResult before = analyze(grammar(GrammarTechnology.LANGIUM, "Model", source), 1);

        assertThat(diagnostic(before, GrammarDiagnosticCatalog.LEFT_RECURSION).severity()).isEqualTo(GrammarSeverity.ERROR);
        GrammarCodeAction fix = before.codeActions().stream().filter(action -> action.kind().equals("quickfix.grammar.left-recursion"))
                .findFirst().orElseThrow();
        assertThat(fix.safeAutomatic()).isFalse();
        assertThat(fix.preferred()).isFalse();
        assertThat(fix.metadata()).containsEntry("automaticResolution", "true").containsEntry("semanticReviewRequired", "true");
        assertThat(fix.edits()).hasSize(1);
        assertThat(fix.edits().get(0).replacement()).isEqualTo("value=INT (op='+' right=INT)*");

        String repaired = apply(source, fix.edits());
        assertThat(repaired).contains("value=INT (op='+' right=INT)*").doesNotContain("Expression op='+'");
        GrammarAnalysisResult after = analyze(grammar(GrammarTechnology.LANGIUM, "Model", repaired), 2);
        assertThat(after.hasErrors()).isFalse();
        assertThat(after.metadata()).containsEntry("recursion.leftCount", "0");

        // Contraste con la rama segura: sin asignaciones la misma reescritura si es automatica.
        GrammarAnalysisResult plain = analyze(grammar(GrammarTechnology.LANGIUM, "Model",
                "grammar Calc\nentry Model: expression=Expression;\nExpression: Expression '+' INT | INT;\n"), 1);
        GrammarCodeAction plainFix = plain.codeActions().stream().filter(action -> action.kind().equals("quickfix.grammar.left-recursion"))
                .findFirst().orElseThrow();
        assertThat(plainFix.safeAutomatic()).isTrue();
        assertThat(plainFix.metadata()).containsEntry("semanticReviewRequired", "false");
    }

    @Test
    @DisplayName("x_language_to_published_editor.g1: una gramatica Xtext se analiza, deduce un metamodelo candidato y el bucle corregir-reanalizar la deja sin errores")
    void anXtextGrammarIsAnalysedDeducesACandidateMetamodelAndTheFixLoopClearsItsErrors() {
        String header = "grammar generated.Main with org.eclipse.xtext.common.Terminals\n";
        String broken = header + "entry Model: elements+=Elemant*;\nElement: 'element' name=ID;\n";

        GrammarAnalysisResult before = analyze(grammar(GrammarTechnology.XTEXT, "Model", broken), 1);

        assertThat(before.hasErrors()).isTrue();
        assertThat(before.diagnostics()).anyMatch(item -> item.code().equals("REFERENCE_NOT_FOUND") && item.message().contains("Elemant"));
        GrammarCodeAction fix = before.codeActions().stream().filter(action -> action.kind().equals("quickfix.reference.replace"))
                .findFirst().orElseThrow();
        assertThat(fix.edits()).singleElement().satisfies(edit -> assertThat(edit.replacement()).isEqualTo("Element"));

        String repaired = apply(broken, fix.edits());
        assertThat(repaired).isEqualTo(header + "entry Model: elements+=Element*;\nElement: 'element' name=ID;\n");
        GrammarAnalysisResult after = analyze(grammar(GrammarTechnology.XTEXT, "Model", repaired), 2);
        assertThat(after.hasErrors()).isFalse();

        // Metamodelo deducido (candidato) para Xtext: una clase por regla, confianza 0,72, a revisar.
        assertThat(after.semanticCandidate().bindings()).extracting(GrammarSemanticBinding::semanticElementId)
                .containsExactlyInAnyOrder("Model", "Element");
        assertThat(after.semanticCandidate().bindings()).extracting(GrammarSemanticBinding::semanticElementKind).containsOnly("CLASS");
        assertThat(after.semanticCandidate().bindings()).allSatisfy(binding -> {
            assertThat(binding.confidence()).isCloseTo(0.72d, within(1e-9));
            assertThat(binding.provenance()).isEqualTo("HYDRA_GRAMMAR_FIRST_INFERENCE");
            assertThat(binding.metadata()).containsEntry("inferred", "true");
        });
        assertThat(after.semanticCandidate().annotations()).containsEntry("candidateOnly", "true").containsEntry("requiresReview", "true")
                .containsEntry("technology", "XTEXT");
    }
}
