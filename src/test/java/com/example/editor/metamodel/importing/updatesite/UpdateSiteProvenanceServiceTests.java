package com.example.editor.metamodel.importing.updatesite;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateSiteProvenanceServiceTests {

    @Test
    void storesOriginPathMethodConfidenceAndOriginalElementOnEveryRecoveredElement() {
        MetaClass book = new MetaClass("class:Book", "Book", MetaClassKind.CLASS, List.of(new MetaAttribute(
                "attribute:Book:title", "title", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(),
                true, true, "", Map.of())), List.of(), Map.of());
        MetaClass author = new MetaClass("class:Author", "Author", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation authors = new MetaRelation("relation:Book:authors", "authors", book.id(), author.id(),
                MetaRelationKind.REFERENCE, Multiplicity.one(), Multiplicity.many(), "", true, true, Map.of());
        MetaModel model = new MetaModel("2.1", "library", "Library", List.of(book, author), List.of(), List.of(),
                List.of(authors), Map.of());
        UpdateSiteRecoveryCandidate candidate = new UpdateSiteRecoveryCandidate("candidate", 1,
                UpdateSiteRecoveryMethod.DIRECT_METAMODEL, UpdateSiteConfidence.EXACT,
                "plugins/com.example.library_1.2.0.jar!/model/library.ecore", "com.example.library", "ECORE",
                100, true, "Original Ecore", List.of());

        UpdateSiteProvenanceService.ProvenancedMetamodel result = new UpdateSiteProvenanceService(new UpdateSiteImportProperties())
                .enrich(model, candidate);

        assertThat(result.provenance()).extracting(UpdateSiteElementProvenance::elementPath)
                .contains("/", "//Book", "//Book/title", "//Book/authors");
        assertThat(result.provenance()).allSatisfy(item -> {
            assertThat(item.origin()).isEqualTo("plugins/com.example.library_1.2.0.jar");
            assertThat(item.sourcePath()).endsWith("model/library.ecore");
            assertThat(item.method()).isEqualTo(UpdateSiteRecoveryMethod.DIRECT_METAMODEL);
            assertThat(item.confidence()).isEqualTo(UpdateSiteConfidence.EXACT);
        });
        assertThat(result.metamodel().classes().get(0).attributes().get(0).annotations())
                .containsEntry("hydra.provenance.confidence", "EXACT")
                .containsEntry("hydra.provenance.originalElement", "//Book/title")
                .containsEntry("hydra.provenance.path", "plugins/com.example.library_1.2.0.jar!/model/library.ecore");
    }

    @Test
    void capsTheSerializedProvenanceIndexWithoutRemovingAnnotations() {
        UpdateSiteImportProperties properties = new UpdateSiteImportProperties();
        properties.setMaxProvenanceElements(2);
        MetaClass sample = new MetaClass("sample", "Sample", MetaClassKind.CLASS, List.of(
                attribute("a"), attribute("b"), attribute("c")), List.of(), Map.of());
        MetaModel model = new MetaModel("2.1", "sample", "Sample", List.of(sample), List.of(), List.of(), List.of(), Map.of());
        UpdateSiteRecoveryCandidate candidate = new UpdateSiteRecoveryCandidate("candidate", 5,
                UpdateSiteRecoveryMethod.CODE_INFERENCE, UpdateSiteConfidence.INFERRED, "plugin.jar!/Sample.class",
                "plugin", "JAVA_CLASS", 30, true, "inferred", List.of("Review inferred types"));

        var result = new UpdateSiteProvenanceService(properties).enrich(model, candidate);

        assertThat(result.provenance()).hasSize(2);
        assertThat(result.metamodel().classes().get(0).attributes()).allSatisfy(attribute ->
                assertThat(attribute.annotations()).containsEntry("hydra.provenance.confidence", "INFERRED"));
    }

    private MetaAttribute attribute(String name) {
        return new MetaAttribute(name, name, MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.optional(),
                true, true, "", Map.of());
    }
}
