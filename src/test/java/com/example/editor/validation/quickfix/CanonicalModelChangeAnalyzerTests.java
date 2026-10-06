package com.example.editor.validation.quickfix;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;
import static org.assertj.core.api.Assertions.assertThat;

class CanonicalModelChangeAnalyzerTests {
    private final CanonicalModelChangeAnalyzer analyzer = new CanonicalModelChangeAnalyzer();

    @Test void identicalDocumentsProduceEmptyChangeSet() {
        CanonicalModelDocument model = model(List.of(element("a", "class:A", Map.of("attribute:name", "A"))), List.of());
        assertThat(analyzer.analyze(model, model).changed()).isFalse();
    }

    @Test void detectsCreatedElementAndClass() {
        CanonicalModelDocument before = model(List.of(element("a", "class:A", Map.of())), List.of());
        CanonicalModelDocument after = model(List.of(element("a", "class:A", Map.of()), element("b", "class:B", Map.of())), List.of());
        CanonicalModelChangeSummary result = analyzer.analyze(before, after);
        assertThat(result.createdElementIds()).containsExactly("b");
        assertThat(result.classIds()).contains("class:B");
    }

    @Test void detectsDeletedElement() {
        CanonicalModelDocument before = model(List.of(element("a", "class:A", Map.of()), element("b", "class:B", Map.of())), List.of());
        CanonicalModelDocument after = model(List.of(element("a", "class:A", Map.of())), List.of());
        assertThat(analyzer.analyze(before, after).deletedElementIds()).containsExactly("b");
    }

    @Test void detectsChangedAttributeAndOwningElement() {
        CanonicalModelDocument before = model(List.of(element("a", "class:A", Map.of("attribute:name", "before"))), List.of());
        CanonicalModelDocument after = model(List.of(element("a", "class:A", Map.of("attribute:name", "after"))), List.of());
        CanonicalModelChangeSummary result = analyzer.analyze(before, after);
        assertThat(result.modifiedElementIds()).containsExactly("a");
        assertThat(result.attributeIds()).containsExactly("attribute:name");
        assertThat(result.validationChangeSet().elementIds()).contains("a");
        assertThat(result.validationChangeSet().modelChanged()).isTrue();
    }

    @Test void detectsAddedSemanticRelationAndEndpoints() {
        CanonicalModelDocument before = model(List.of(element("a", "class:A", Map.of()), element("b", "class:B", Map.of())), List.of());
        CanonicalModelDocument after = model(before.elements(), List.of(new CanonicalModelRelation("instance:1", "relation:links", "a", "b", Map.of())));
        CanonicalModelChangeSummary result = analyzer.analyze(before, after);
        assertThat(result.relationIds()).containsExactly("relation:links");
        assertThat(result.validationChangeSet().elementIds()).containsExactlyInAnyOrder("a", "b");
    }

    @Test void relationInstanceIdentifiersDoNotCreateFalseChanges() {
        CanonicalModelDocument before = model(List.of(element("a", "class:A", Map.of()), element("b", "class:B", Map.of())),
                List.of(new CanonicalModelRelation("runtime:1", "relation:links", "a", "b", Map.of())));
        CanonicalModelDocument after = model(before.elements(), List.of(new CanonicalModelRelation("runtime:99", "relation:links", "a", "b", Map.of())));
        assertThat(analyzer.analyze(before, after).changed()).isFalse();
    }

    @Test void rootChangesMarkWholeModelAsChanged() {
        CanonicalModelDocument before = model(List.of(element("a", "class:A", Map.of()), element("b", "class:B", Map.of())), List.of());
        CanonicalModelDocument after = new CanonicalModelDocument("1.0", "model", "meta", "", "Model", List.of("b"), before.elements(), List.of(), Map.of());
        assertThat(analyzer.analyze(before, after).validationChangeSet().modelChanged()).isTrue();
    }

    private CanonicalModelElement element(String id, String classId, Map<String, Object> attributes) {
        return new CanonicalModelElement(id, classId, attributes, Map.of());
    }
    private CanonicalModelDocument model(List<CanonicalModelElement> elements, List<CanonicalModelRelation> relations) {
        return new CanonicalModelDocument("1.0", "model", "meta", "", "Model", List.of("a"), elements, relations, Map.of());
    }
}
