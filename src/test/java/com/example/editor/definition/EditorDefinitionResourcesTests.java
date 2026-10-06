package com.example.editor.definition;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
class EditorDefinitionResourcesTests {
    @Test void publishesEditorDefinitionSchema() { assertThat(resource("/schemas/editor/editor-definition-model-1.0.schema.json")).contains("EditorDefinitionModel 1.0", "representations", "palette"); }
    @Test void exampleSeparatesEditorPaletteFromMetamodelDesigner() { assertThat(resource("/examples/editor/people-basic-editor.json")).contains("people-basic-editor", "tool:create-person", "representation:main"); }
    private String resource(String path) { try(var stream=getClass().getResourceAsStream(path)){if(stream==null)throw new AssertionError("Missing resource "+path);return new String(stream.readAllBytes(),StandardCharsets.UTF_8);}catch(Exception exception){throw new AssertionError(exception);} }
}
