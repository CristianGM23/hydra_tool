package com.example.editor.textual.runtime;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.example.editor.textual.source.TextualSemanticSourceMapEntry;

/** A cross-reference names an element by its identifier: a numeric or boolean literal is a value, whatever the property is called. */
class NeutralTextualModelParserReferenceTests {

    @Test
    void numericAndBooleanValuesOfPropertiesWithReferenceLikeNamesAreAttributes() {
        TextualParseResult result = parse("node n1 { targetPercent = 95 sourceCount = 3 typeWeight = 603.33 ownerApproved = true }\n");

        assertThat(result.diagnostics()).extracting(TextualModelDiagnostic::code).doesNotContain("TEXTUAL_REFERENCE_NOT_FOUND");
        assertThat(result.sourceMap()).filteredOn(entry -> entry.semanticKind().equals("MODEL_FEATURE")).extracting(TextualSemanticSourceMapEntry::semanticId)
                .contains("n1:targetPercent", "n1:sourceCount", "n1:typeWeight", "n1:ownerApproved");
    }

    @Test
    void identifierValuesStillResolveAsReferencesOrAreReportedWhenMissing() {
        TextualParseResult result = parse("node n1 { target = n2 }\nnode n2 { owner = ghost }\n");

        assertThat(result.sourceMap()).filteredOn(entry -> entry.semanticKind().equals("MODEL_RELATION")).isNotEmpty();
        assertThat(result.diagnostics()).filteredOn(diagnostic -> diagnostic.code().equals("TEXTUAL_REFERENCE_NOT_FOUND"))
                .singleElement().satisfies(diagnostic -> assertThat(diagnostic.metadata()).containsEntry("target", "ghost"));
    }

    private static TextualParseResult parse(String content) {
        return new NeutralTextualModelParser().parse(1, List.of(new TextualModelSourceDocument("model", "model.xtext.model", content, 1)), snapshot());
    }

    private static GrammarCompilationSnapshot snapshot() {
        return new GrammarCompilationSnapshot("snapshot", "grammar", 1, "source-hash", GrammarTechnology.XTEXT, "provider", "toolchain", CompilationStatus.SUCCESS, List.of(),
                GrammarSemanticBindingMap.empty(), new GrammarRuntimeDescriptor("", "", List.of(), "", "", Map.of()), new GeneratedGrammarProject("", Map.of(), Map.of()),
                Map.of(), Instant.now(), Map.of("immutable", "true"));
    }
}
