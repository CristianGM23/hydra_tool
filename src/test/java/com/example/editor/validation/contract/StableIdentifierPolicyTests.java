package com.example.editor.validation.contract;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StableIdentifierPolicyTests {

    @Test
    void acceptsCurrentCanonicalIdentifierShapes() {
        assertThat(StableIdentifierPolicy.isValidDocumentId("library-editor")).isTrue();
        assertThat(StableIdentifierPolicy.isValidElementId("class:Book")).isTrue();
        assertThat(StableIdentifierPolicy.isValidElementId("attribute:class:Book:title")).isTrue();
        assertThat(StableIdentifierPolicy.isValidElementId("c700a783-d63d-4de5-88da-7bfe9a8f7e88")).isTrue();
    }

    @Test
    void rejectsPathsWhitespaceAndEditableLabels() {
        assertThat(StableIdentifierPolicy.isValidDocumentId("../library")).isFalse();
        assertThat(StableIdentifierPolicy.isValidDocumentId("library editor")).isFalse();
        assertThat(StableIdentifierPolicy.isValidElementId("Book title")).isFalse();
        assertThat(StableIdentifierPolicy.isValidElementId("")).isFalse();
    }

    @Test
    void canonicalReferenceRequiresStableDocumentAndElementIds() {
        CanonicalElementReference reference = new CanonicalElementReference("library", "attribute:class:Book:pages");
        assertThat(reference.documentId()).isEqualTo("library");
        assertThat(reference.elementId()).isEqualTo("attribute:class:Book:pages");

        assertThatThrownBy(() -> new CanonicalElementReference("../library", "class:Book"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
