package com.example.editor.textual.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.grammar.core.CompilationStatus;
import com.example.editor.grammar.core.GeneratedGrammarProject;
import com.example.editor.grammar.core.GrammarRuntimeDescriptor;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;
import com.example.editor.textual.source.TextualModelSourceDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;

/**
 * Textual documents of any size the textual module accepts are checked completely against the grammar (FIND-TXT-LARGE-DOCUMENT):
 * a valid document is never rejected, an invalid one is never accepted, and the model that is saved is exactly the one the text denotes.
 */
class NeutralTextualModelScaleTests {
    private static final String GRAMMAR = """
            grammar Plant

            entry Model: 'Plant' name=ID '{' (elements+=Element)* '}';
            Element: Machine | Sensor;
            Machine: 'Machine' name=ID '{' ('code' '=' code=STRING)? ('power' '=' power=NUMBER)? ('feeds' '=' feeds+=[Machine:ID])* '}';
            Sensor: 'Sensor' name=ID '{' ('code' '=' code=STRING)? ('active' '=' active=BOOLEAN_LIT)? ('watches' '=' watches+=[Machine:ID])* '}';
            BOOLEAN_LIT returns boolean: 'true' | 'false';
            NUMBER returns number: '-'? INT ('.' INT)?;
            hidden terminal WS: /\\s+/;
            terminal ID: /[_a-zA-Z][\\w_]*/;
            terminal INT returns number: /[0-9]+/;
            terminal STRING: /"(\\\\.|[^"\\\\])*"/;
            """;

    @Test
    void largeContainerDocumentIsValidatedCompletelyAndYieldsExactlyTheModelItDenotes() {
        int size = 5_000;
        TextualParseResult result = parse(GRAMMAR, plant(size));

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.valid()).isTrue();
        assertThat(result.metadata()).containsEntry("grammarValidationStrategy", "FULL_ITERATIVE");
        assertThat(result.semanticModel().elements()).hasSize(size + 1);
        CanonicalModelElement root = result.semanticModel().findElement("plant").orElseThrow();
        assertThat(root.attributes()).as("the root block owns none of the properties of the elements it contains").isEmpty();
        assertThat(result.semanticModel().findElement("m2499").orElseThrow().attributes()).containsEntry("code", "M-2499")
                .hasEntrySatisfying("power", power -> assertThat(((Number) power).doubleValue()).isEqualTo(2499.0)); // canonical models keep numbers as doubles
        assertThat(result.semanticModel().findElement("s2500").orElseThrow().attributes()).containsEntry("active", true);
        List<CanonicalModelRelation> relations = result.semanticModel().relations();
        assertThat(relations).hasSize(size - 1).allSatisfy(relation -> assertThat(relation.sourceElementId()).isNotEqualTo("plant"));
        assertThat(relations).anySatisfy(relation -> {
            assertThat(relation.sourceElementId()).isEqualTo("s2500");
            assertThat(relation.relationId()).isEqualTo("watches");
            assertThat(relation.targetElementId()).isEqualTo("m2499");
        });
    }

    @Test
    void documentsAroundTheFormerNineHundredAndTwelveHundredTokenLimitsAreAccepted() {
        for (int size : new int[]{60, 70, 75, 80, 85, 90, 100}) { // about 810 to 1350 tokens
            TextualParseResult result = parse(GRAMMAR, plant(size));
            assertThat(result.diagnostics()).as("valid document of %d elements", size).isEmpty();
            assertThat(result.valid()).isTrue();
        }
    }

    @Test
    void anErrorDeepInsideALargeDocumentIsRejectedAtItsLineWithTheRuleThatRejectsIt() {
        String[] lines = plant(5_000).split("\n", -1);
        lines[2_501] = lines[2_501].replace("{ code", "{ colour = 1 code"); // line i holds element i: 2501 is a machine
        TextualParseResult result = parse(GRAMMAR, String.join("\n", lines));

        assertThat(result.valid()).isFalse();
        assertThat(result.diagnostics()).first().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("TEXTUAL_GRAMMAR_MISMATCH");
            assertThat(diagnostic.range().start().line()).isEqualTo(2_501);
            assertThat(diagnostic.metadata()).containsEntry("ruleContext", "Machine").containsEntry("actual", "colour");
            assertThat(diagnostic.metadata().get("expected")).contains("'code'", "'power'", "'feeds'", "'}'");
        });
    }

    @Test
    void aGrammarErrorAtTheEndOfALargeDocumentIsNotHiddenByItsSize() {
        String text = plant(3_000);
        TextualParseResult result = parse(GRAMMAR, text.substring(0, text.lastIndexOf('}')) + "stray }\n");

        assertThat(result.valid()).isFalse();
        assertThat(result.diagnostics()).extracting(TextualModelDiagnostic::code).contains("TEXTUAL_GRAMMAR_MISMATCH");
    }

    @Test
    void aDocumentBeyondTheRecognitionBudgetIsReportedAsNotProvableNeverAsValidNorAsAGrammarError() {
        String ambiguous = """
                grammar Ambiguous
                entry Model: (items+=E)*;
                E: left=E right=E | 'a';
                hidden terminal WS: /\\s+/;
                """;
        TextualParseResult result = parse(ambiguous, "a ".repeat(3_000));

        assertThat(result.valid()).isFalse();
        assertThat(result.diagnostics()).extracting(TextualModelDiagnostic::code).containsExactly("TEXTUAL_GRAMMAR_VALIDATION_UNAVAILABLE");
        assertThat(result.diagnostics().get(0).metadata()).containsEntry("strategy", "FULL_STATE_BUDGET_EXCEEDED");
    }

    @Test
    void nestedElementsOwnTheirAssignmentsAndReferences() {
        TextualParseResult result = parse(GRAMMAR, """
                Plant plant {
                  Machine m1 { code = "A" feeds = m2 }
                  Machine m2 { code = "B" power = 4 }
                }
                """);

        assertThat(result.valid()).isTrue();
        assertThat(result.semanticModel().findElement("plant").orElseThrow().attributes()).isEmpty();
        assertThat(result.semanticModel().findElement("m1").orElseThrow().attributes()).isEqualTo(Map.of("code", "A"));
        assertThat(result.semanticModel().relations()).singleElement().satisfies(relation -> {
            assertThat(relation.sourceElementId()).isEqualTo("m1");
            assertThat(relation.targetElementId()).isEqualTo("m2");
        });
    }

    @Test
    void recognitionTimeGrowsLinearlyWithTheDocument() {
        String text = plant(20_000); // about 3.5 MB, below the 4 MB per-document limit of the textual module
        TextualParseResult result = assertTimeoutPreemptively(Duration.ofSeconds(60), () -> parse(GRAMMAR, text));
        assertThat(result.valid()).isTrue();
        assertThat(result.semanticModel().elements()).hasSize(20_001);
    }

    /** {@code Plant plant { Machine m1 {...} Sensor s2 {...} ... }}: odd elements are machines, even ones sensors watching the previous machine. */
    private static String plant(int size) {
        StringBuilder out = new StringBuilder("Plant plant {\n");
        for (int i = 1; i <= size; i++) {
            if (i % 2 == 1) {
                out.append("  Machine m").append(i).append(" { code = \"M-").append(i).append("\" power = ").append(i);
                if (i > 2) out.append(" feeds = m").append(i - 2);
            } else {
                out.append("  Sensor s").append(i).append(" { code = \"S-").append(i).append("\" active = ").append(i % 4 == 0).append(" watches = m").append(i - 1);
            }
            out.append(" }\n");
        }
        return out.append("}\n").toString();
    }

    private static TextualParseResult parse(String grammar, String content) {
        GrammarCompilationSnapshot snapshot = new GrammarCompilationSnapshot("snapshot", "plant", 1, "source-hash", GrammarTechnology.LANGIUM, "langium", "langium-4.4-browser",
                CompilationStatus.SUCCESS, List.of(), GrammarSemanticBindingMap.empty(), new GrammarRuntimeDescriptor("", "Model", List.of(), "", "", Map.of()),
                new GeneratedGrammarProject("plant", Map.of("src/language/plant.langium", grammar), Map.of()), Map.of(), Instant.now(), Map.of("immutable", "true"));
        return new NeutralTextualModelParser().parse(1, List.of(new TextualModelSourceDocument("model", "model.plant", content, 1)), snapshot);
    }
}
