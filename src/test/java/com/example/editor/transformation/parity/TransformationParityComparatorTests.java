package com.example.editor.transformation.parity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.transformation.text.TransformationTextArtifact;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;

/**
 * Evidencia real, sin mocks, de la comparacion semantica que respalda "Comparar resultados" de
 * transformation_parity: una prueba de paridad modelo-a-modelo que coincide y otra que difiere en
 * un atributo, mas una comparacion de artefactos de texto (modelo-a-texto) que coincide y otra que
 * difiere en el contenido de un fichero generado.
 */
class TransformationParityComparatorTests {

    private final TransformationParityComparator comparator = new TransformationParityComparator();

    @Test
    void anIdenticalCanonicalAndNativeModelMatchWithTheSameFingerprintOnBothSides() {
        CanonicalModelDocument expected = personModel("person-1", "Ada");
        CanonicalModelDocument actual = personModel("person-1", "Ada");

        TransformationParityComparator.Comparison comparison = comparator.compareModels(expected, actual);

        assertThat(comparison.matched()).isTrue();
        assertThat(comparison.differences()).isEmpty();
        assertThat(comparison.canonicalFingerprint()).isNotBlank().isEqualTo(comparison.nativeFingerprint());
    }

    @Test
    void aNativeModelWithADifferentAttributeValueIsReportedAsAMismatchWithTheDifference() {
        CanonicalModelDocument expected = personModel("person-1", "Ada");
        CanonicalModelDocument actual = personModel("person-1", "Grace");

        TransformationParityComparator.Comparison comparison = comparator.compareModels(expected, actual);

        assertThat(comparison.matched()).isFalse();
        assertThat(comparison.canonicalFingerprint()).isNotEqualTo(comparison.nativeFingerprint());
        assertThat(comparison.differences()).isNotEmpty()
                .anySatisfy(difference -> assertThat(difference.path()).startsWith("$.targetModel.elements"))
                .allSatisfy(difference -> assertThat(difference.path()).startsWith("$.targetModel."));
    }

    @Test
    void aMissingRelationTargetOnTheNativeSideIsReportedAsAStructuralDifference() {
        CanonicalModelDocument expected = modelWithRelation("rel-1", "target-1");
        CanonicalModelDocument actual = modelWithRelation("rel-1", "target-missing");

        TransformationParityComparator.Comparison comparison = comparator.compareModels(expected, actual);

        assertThat(comparison.matched()).isFalse();
        assertThat(comparison.differences()).anySatisfy(difference -> assertThat(difference.path()).startsWith("$.targetModel.relations"));
    }

    @Test
    void generatedTextArtifactsWithIdenticalPathsAndContentMatchRegardlessOfPathSeparatorStyle() {
        List<TransformationTextArtifact> expected = List.of(new TransformationTextArtifact("docs/out.txt", "hello\nworld", "rule:r1", "person-1"));
        List<TransformationParityArtifact> actual = List.of(new TransformationParityArtifact("docs\\out.txt", "hello\r\nworld"));

        TransformationParityComparator.Comparison comparison = comparator.compareArtifacts(expected, actual);

        assertThat(comparison.matched()).isTrue();
        assertThat(comparison.differences()).isEmpty();
    }

    @Test
    void anArtifactWithDifferentContentOnTheNativeSideIsReportedAsAMismatch() {
        List<TransformationTextArtifact> expected = List.of(new TransformationTextArtifact("docs/out.txt", "hello", "rule:r1", "person-1"));
        List<TransformationParityArtifact> actual = List.of(new TransformationParityArtifact("docs/out.txt", "goodbye"));

        TransformationParityComparator.Comparison comparison = comparator.compareArtifacts(expected, actual);

        assertThat(comparison.matched()).isFalse();
        assertThat(comparison.differences()).isNotEmpty()
                .allSatisfy(difference -> assertThat(difference.path()).startsWith("$.artifacts"));
    }

    private static CanonicalModelDocument personModel(String elementId, String name) {
        CanonicalModelElement person = new CanonicalModelElement(elementId, "Person", Map.of("name", name), Map.of());
        return new CanonicalModelDocument(null, "doc-1", "metamodel-people", "", "People", List.of(elementId), List.of(person), List.of(), Map.of());
    }

    private static CanonicalModelDocument modelWithRelation(String relationId, String targetId) {
        CanonicalModelElement source = new CanonicalModelElement("source-1", "Person", Map.of(), Map.of());
        CanonicalModelElement target = new CanonicalModelElement("target-1", "Person", Map.of(), Map.of());
        CanonicalModelRelation relation = new CanonicalModelRelation("edge-1", relationId, "source-1", targetId, Map.of());
        return new CanonicalModelDocument(null, "doc-2", "metamodel-people", "", "People", List.of("source-1"), List.of(source, target), List.of(relation), Map.of());
    }
}
