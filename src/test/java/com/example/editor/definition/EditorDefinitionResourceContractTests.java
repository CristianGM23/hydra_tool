package com.example.editor.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import org.junit.jupiter.api.Test;

class EditorDefinitionResourceContractTests {

    @Test
    void editorAndEventContractResourcesAreAvailableFromTheClasspath() {
        assertThat(resource("/schemas/editor/editor-definition-model-1.0.schema.json")).isNotNull();
        assertThat(resource("/examples/editor/people-basic-editor.json")).isNotNull();
        assertThat(resource("/schemas/event/canonical-event-1.0.schema.json")).isNotNull();
        assertThat(resource("/schemas/event/event-definition-model-1.0.schema.json")).isNotNull();
        assertThat(resource("/schemas/event/event-binding-model-1.0.schema.json")).isNotNull();
        assertThat(resource("/examples/event/people-events.json")).isNotNull();
    }

    private URL resource(String path) {
        return getClass().getResource(path);
    }
}
