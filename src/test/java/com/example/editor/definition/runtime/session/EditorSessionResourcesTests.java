package com.example.editor.definition.runtime.session;
import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
class EditorSessionResourcesTests {
    @Test void publishesPortableSessionSchema() throws IOException { assertThat(resource("/schemas/editor/editor-model-session-1.0.schema.json")).contains("EditorModelSession 1.0", "activeRepresentationId", "revision"); }
    @Test void publishesPortableDiagramSchema() throws IOException { assertThat(resource("/schemas/editor/editor-session-diagram-1.0.schema.json")).contains("EditorSessionDiagram 1.0", "relationInstanceId", "representationId"); }
    @Test void publishesPortableDiagramExample() throws IOException { assertThat(resource("/examples/editor/people-editor-session-diagram.json")).contains("people-editor-session", "person:ada", "relation-instance:knows"); }
    @Test void publishesPortableSessionExample() throws IOException { assertThat(resource("/examples/editor/people-editor-session.json")).contains("people-session", "element:ada", "representation:main"); }
    private String resource(String path) throws IOException { try(var stream=getClass().getResourceAsStream(path)){ assertThat(stream).as(path).isNotNull(); return new String(stream.readAllBytes(),StandardCharsets.UTF_8); } }
}
