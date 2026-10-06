package com.example.editor.gamification.diagram;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.gamification.GamificationProperties;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GamificationDiagramRepositoryTests {
    @TempDir Path temporary;
    private JsonGamificationDiagramRepository repository;

    @BeforeEach void setUp() {
        GamificationProperties properties = new GamificationProperties();
        properties.setDirectory(temporary);
        repository = new JsonGamificationDiagramRepository(properties, JsonMapper.builder().findAndAddModules().build());
    }

    @Test void createsAndReadsTheSeparateDiagramFile() {
        StoredCanonicalDocument<GamificationDiagramDocument> stored = repository.create(diagram(10));
        assertThat(Files.isRegularFile(temporary.resolve("people.gamification-diagram.json"))).isTrue();
        assertThat(repository.find("people").orElseThrow().document()).isEqualTo(stored.document());
    }

    @Test void replacesOnlyWithTheCurrentEtag() {
        StoredCanonicalDocument<GamificationDiagramDocument> first = repository.create(diagram(10));
        StoredCanonicalDocument<GamificationDiagramDocument> second = repository.replace("people", diagram(90), first.etag());
        assertThat(second.document().nodes().getFirst().x()).isEqualTo(90);
        assertThatThrownBy(() -> repository.replace("people", diagram(100), first.etag()))
                .isInstanceOf(CanonicalDocumentStoreException.class)
                .extracting("reason").isEqualTo(CanonicalDocumentStoreException.Reason.PRECONDITION_FAILED);
    }

    @Test void deletesWithEtagAndRejectsUnsafeIds() {
        StoredCanonicalDocument<GamificationDiagramDocument> stored = repository.create(diagram(10));
        repository.delete("people", stored.etag());
        assertThat(repository.find("people")).isEmpty();
        assertThatThrownBy(() -> repository.find("../people")).isInstanceOf(CanonicalDocumentStoreException.class);
    }

    @Test void defaultsNodeDimensionsAndCopiesCollections() {
        var mutable = new java.util.ArrayList<GamificationDiagramNodeView>();
        mutable.add(new GamificationDiagramNodeView("rule:quality", 10, 20, 0, 0, false));
        GamificationDiagramDocument document = new GamificationDiagramDocument("1.0", "people-gamification-diagram", "people",
                "people-gamification", "people-gamification-bindings", mutable, null);
        mutable.clear();
        assertThat(document.nodes()).singleElement().satisfies(node -> {
            assertThat(node.width()).isEqualTo(210);
            assertThat(node.height()).isEqualTo(84);
        });
        assertThat(document.edges()).isEmpty();
    }

    @Test void rejectsUnsupportedSchemaAndInvalidElementIds() {
        assertThatThrownBy(() -> new GamificationDiagramDocument("2.0", "people-gamification-diagram", "people",
                "people-gamification", "people-gamification-bindings", List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GamificationDiagramNodeView("../rule", 0, 0, 1, 1, false)).isInstanceOf(IllegalArgumentException.class);
    }

    private GamificationDiagramDocument diagram(double x) {
        return new GamificationDiagramDocument("1.0", "people-gamification-diagram", "people", "people-gamification",
                "people-gamification-bindings", List.of(new GamificationDiagramNodeView("rule:quality", x, 20, 210, 84, false)),
                List.of(new GamificationDiagramEdgeView("rule-points:quality", List.of(new GamificationDiagramPoint(30, 40)))));
    }
}
