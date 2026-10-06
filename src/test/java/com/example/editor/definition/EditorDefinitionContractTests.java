package com.example.editor.definition;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.Map;
import com.example.editor.metamodel.core.Multiplicity;
import org.junit.jupiter.api.Test;
class EditorDefinitionContractTests {
    @Test void supportsSeveralRepresentationsForOneEditor() { var editor = EditorDefinitionTestFixtures.editor(); var second = new RepresentationDefinition("representation:review", "Review", RepresentationKind.DIAGRAM, List.of(), List.of(), List.of(), List.of(), false, Map.of()); var changed = new EditorDefinitionModel("1.0", editor.id(), editor.metamodelId(), editor.name(), List.of(editor.representations().getFirst(), second), editor.palette(), editor.forms(), editor.capabilityManifest(), Map.of()); assertThat(changed.representations()).hasSize(2); }
    @Test void paletteBelongsToEditorDefinition() { var editor = EditorDefinitionTestFixtures.editor(); assertThat(editor.palette().tools()).extracting(EditorTool::id).contains("tool:create-person"); }
    @Test void keepsMetamodelDesignerPaletteOutsideContract() { var editor = EditorDefinitionTestFixtures.editor(); assertThat(editor.annotations()).doesNotContainKey("metamodelDesignerPalette"); }
    @Test void fixtureUsesCanonicalRelationSignature() { var relation = EditorDefinitionTestFixtures.metamodel().relations().getFirst(); assertThat(relation.sourceTypeId()).isEqualTo("class:Person"); assertThat(relation.targetTypeId()).isEqualTo("class:Company"); assertThat(relation.sourceMultiplicity()).isEqualTo(Multiplicity.one()); assertThat(relation.targetMultiplicity()).isEqualTo(Multiplicity.optional()); }
    @Test void rejectsDefaultSizeBelowMinimum() { assertThatThrownBy(() -> new NodeMapping("node:bad", "class:Person", "Bad", EditorNodeShape.RECTANGLE, 50, 50, 100, 80, "", "", com.example.editor.definition.EditorImageFit.CONTAIN, false, List.of(), 18, Map.of(), List.of(), Map.of())).isInstanceOf(IllegalArgumentException.class); }
    @Test void requiresAtLeastOneFormColumn() { assertThatThrownBy(() -> new FormSectionDefinition("section:bad", "Bad", 0, 0, List.of())).isInstanceOf(IllegalArgumentException.class); }
}
