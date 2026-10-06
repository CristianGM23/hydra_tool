package com.example.editor.definition.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.editor.definition.EditorDefinitionApplicationException;
import com.example.editor.definition.EditorDefinitionCheckReport;
import com.example.editor.definition.EditorDefinitionDiagnostic;
import com.example.editor.definition.EditorDefinitionDiagnosticSeverity;
import com.example.editor.definition.EditorDefinitionTestFixtures;

class EditorDefinitionCompilerTests {
    private final EditorDefinitionCompiler compiler = new EditorDefinitionCompiler();

    @Test
    void usesStablePlanSchema() {
        assertThat(plan().schemaVersion()).isEqualTo("1.0");
    }

    @Test
    void derivesPlanIdentityFromSourceEditor() {
        assertThat(plan()).extracting(ExecutableEditorPlan::id, ExecutableEditorPlan::sourceEditorDefinitionId)
                .containsExactly("people-editor:compiled", "people-editor");
    }

    @Test
    void recordsSourceEtag() {
        assertThat(plan().sourceEtag()).isEqualTo("\"etag-1\"");
    }

    @Test
    void indexesRepresentationsByStableId() {
        assertThat(plan().representationsById()).containsKey("representation:main");
    }

    @Test
    void indexesNodeMappingsByClass() {
        ExecutableRepresentationPlan representation = plan().representationsById().get("representation:main");
        assertThat(representation.nodeMappingsByClassId().get("class:Person"))
                .extracting(mapping -> mapping.id()).containsExactly("node:person");
    }

    @Test
    void indexesEdgeMappingsByRelation() {
        ExecutableRepresentationPlan representation = plan().representationsById().get("representation:main");
        assertThat(representation.edgeMappingsByRelationId().get("relation:Person:employer"))
                .extracting(mapping -> mapping.id()).containsExactly("edge:employer");
    }

    @Test
    void compilesOnlyRepresentationToolsIntoPalette() {
        ExecutableRepresentationPlan representation = plan().representationsById().get("representation:main");
        assertThat(representation.toolsById()).containsOnlyKeys("tool:create-person", "tool:employer", "tool:display");
        assertThat(representation.paletteGroups()).singleElement()
                .satisfies(group -> assertThat(group.tools()).hasSize(3));
    }

    @Test
    void indexesFormsByIdAndTargetClass() {
        ExecutableEditorPlan plan = plan();
        assertThat(plan.formsById()).containsKey("form:person");
        assertThat(plan.formsByTargetClassId().get("class:Person").id()).isEqualTo("form:person");
    }

    @Test
    void exposesImmutableIndexes() {
        ExecutableEditorPlan plan = plan();
        assertThatThrownBy(() -> plan.representationsById().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.representationsById().get("representation:main").toolsById().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsBlockingDiagnostics() {
        var editor = EditorDefinitionTestFixtures.editor();
        var diagnostic = new EditorDefinitionDiagnostic(EditorDefinitionDiagnosticSeverity.ERROR, "BROKEN",
                editor.id(), "representations", "Broken editor");
        assertThatThrownBy(() -> compiler.compile(editor, "etag", new EditorDefinitionCheckReport(editor.id(), List.of(diagnostic))))
                .isInstanceOf(EditorDefinitionApplicationException.class)
                .hasMessageContaining("blocking diagnostics");
    }

    private ExecutableEditorPlan plan() {
        var editor = EditorDefinitionTestFixtures.editor();
        return compiler.compile(editor, "\"etag-1\"", new EditorDefinitionCheckReport(editor.id(), List.of()));
    }
}
