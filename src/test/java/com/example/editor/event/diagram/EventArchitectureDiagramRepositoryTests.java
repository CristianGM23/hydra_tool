package com.example.editor.event.diagram;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.event.EventModelProperties;
import com.example.editor.event.EventContractVersions;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventArchitectureDiagramRepositoryTests {
    @TempDir Path temporary;
    private JsonEventArchitectureDiagramRepository repository;

    @BeforeEach void setUp() {
        EventModelProperties properties = new EventModelProperties();
        properties.setDirectory(temporary);
        repository = new JsonEventArchitectureDiagramRepository(properties, JsonMapper.builder().findAndAddModules().build());
    }

    @Test void createsAndReadsTheSeparateDiagramFile() {
        StoredCanonicalDocument<EventArchitectureDiagramDocument> stored = repository.create(diagram(10));
        assertThat(Files.isRegularFile(temporary.resolve("people.event-architecture-diagram.json"))).isTrue();
        assertThat(repository.find("people").orElseThrow().document()).isEqualTo(stored.document());
    }

    @Test void replacesOnlyWithTheCurrentEtag() {
        StoredCanonicalDocument<EventArchitectureDiagramDocument> first = repository.create(diagram(10));
        StoredCanonicalDocument<EventArchitectureDiagramDocument> second = repository.replace("people", diagram(90), first.etag());
        assertThat(second.document().nodes().getFirst().x()).isEqualTo(90);
        assertThatThrownBy(() -> repository.replace("people", diagram(100), first.etag()))
                .isInstanceOf(CanonicalDocumentStoreException.class)
                .extracting("reason").isEqualTo(CanonicalDocumentStoreException.Reason.PRECONDITION_FAILED);
    }

    @Test void deletesWithEtagAndRejectsUnsafeIds() {
        StoredCanonicalDocument<EventArchitectureDiagramDocument> stored = repository.create(diagram(10));
        repository.delete("people", stored.etag());
        assertThat(repository.find("people")).isEmpty();
        assertThatThrownBy(() -> repository.find("../people")).isInstanceOf(CanonicalDocumentStoreException.class);
    }

    @Test void defaultsNodeDimensionsAndCopiesCollections() {
        var mutable = new java.util.ArrayList<EventArchitectureDiagramNodeView>();
        mutable.add(new EventArchitectureDiagramNodeView("core.model.saved", 10, 20, 0, 0, false));
        EventArchitectureDiagramDocument document = new EventArchitectureDiagramDocument(EventContractVersions.EVENT_ARCHITECTURE_DIAGRAM, "people-event-diagram", "people",
                "people-events", "people-event-bindings", mutable, null);
        mutable.clear();
        assertThat(document.nodes()).singleElement().satisfies(node -> {
            assertThat(node.width()).isEqualTo(230);
            assertThat(node.height()).isEqualTo(92);
        });
        assertThat(document.edges()).isEmpty();
    }

    @Test void rejectsUnsupportedSchemaAndInvalidElementIds() {
        assertThatThrownBy(() -> new EventArchitectureDiagramDocument("2.0", "people-event-diagram", "people",
                "people-events", "people-event-bindings", List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventArchitectureDiagramNodeView("../event", 0, 0, 1, 1, false)).isInstanceOf(IllegalArgumentException.class);
    }

    private EventArchitectureDiagramDocument diagram(double x) {
        return new EventArchitectureDiagramDocument(EventContractVersions.EVENT_ARCHITECTURE_DIAGRAM, "people-event-diagram", "people", "people-events",
                "people-event-bindings", List.of(new EventArchitectureDiagramNodeView("core.model.saved", x, 20, 230, 92, false)),
                List.of(new EventArchitectureDiagramEdgeView("binding:test", List.of(new EventArchitectureDiagramPoint(30, 40)))));
    }
}
