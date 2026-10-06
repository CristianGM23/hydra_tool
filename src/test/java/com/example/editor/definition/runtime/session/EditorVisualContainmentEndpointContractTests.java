package com.example.editor.definition.runtime.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source-level guard for the additive semantic containment endpoint used by graphical drag-and-drop. */
class EditorVisualContainmentEndpointContractTests {
    @Test void controllerAndServiceKeepThePortableContainmentContract() throws Exception {
        String controller = Files.readString(project("src/main/java/com/example/editor/definition/runtime/session/web/EditorModelSessionController.java"));
        String service = Files.readString(project("src/main/java/com/example/editor/definition/runtime/session/EditorModelSessionService.java"));
        assertThat(controller).contains("/{sessionId}/elements/{elementId}/containment", "EditorElementContainmentRequest", "If-Match");
        assertThat(service).contains("reparentElement", "MetaRelationKind.CONTAINMENT", "visual-drag-and-drop",
                "An element cannot contain itself", "would create a cycle", "roots.remove(elementId)", "roots.add(elementId)");
    }
    private Path project(String relative) {
        Path current = Path.of("").toAbsolutePath();
        if(Files.exists(current.resolve(relative))) return current.resolve(relative);
        return current.resolve("..").resolve(relative).normalize();
    }
}
