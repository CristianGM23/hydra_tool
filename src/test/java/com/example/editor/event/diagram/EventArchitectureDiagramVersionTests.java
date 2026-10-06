package com.example.editor.event.diagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.editor.event.EventContractVersions;

class EventArchitectureDiagramVersionTests {
    @Test
    void usesCuratedCanvasSchemaForNewDocuments() {
        EventArchitectureDiagramDocument document = document(EventContractVersions.EVENT_ARCHITECTURE_DIAGRAM);
        assertThat(document.schemaVersion()).isEqualTo("1.1");
    }

    @Test
    void rejectsPreviousDiagramSchema() {
        assertThatThrownBy(() -> document("1.0")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownDiagramSchema() {
        assertThatThrownBy(() -> document("9.9")).isInstanceOf(IllegalArgumentException.class);
    }

    private EventArchitectureDiagramDocument document(String version) {
        return new EventArchitectureDiagramDocument(version, "people-event-diagram", "people", "people-events",
                "people-event-bindings", List.of(), List.of());
    }
}
