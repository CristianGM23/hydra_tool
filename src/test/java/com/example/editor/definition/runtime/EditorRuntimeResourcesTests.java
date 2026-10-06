package com.example.editor.definition.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class EditorRuntimeResourcesTests {
    @Test
    void publishesExecutablePlanSchema() throws IOException {
        assertThat(resource("/schemas/editor/executable-editor-plan-1.0.schema.json"))
                .contains("ExecutableEditorPlan 1.0", "representationsById", "formsByTargetClassId");
    }

    @Test
    void publishesPortableExecutablePlanExample() throws IOException {
        assertThat(resource("/examples/editor/people-executable-editor-plan.json"))
                .contains("people-editor:compiled", "representation:main", "MODEL_EDITING");
    }

    private String resource(String path) throws IOException {
        try(var stream = EditorRuntimeResourcesTests.class.getResourceAsStream(path)) {
            assertThat(stream).as(path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
