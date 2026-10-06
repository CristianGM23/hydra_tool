package com.example.editor.transformation.runtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;

class TransformationSynchronizationConsistencyValidatorTests {
    private final TransformationSynchronizationConsistencyValidator validator = new TransformationSynchronizationConsistencyValidator();

    @Test void rejectsCurrentModelWithDifferentIdentityThanBaseline() {
        CanonicalModelDocument baselineSource = model("source-model", "source-mm", "etag-1", "source-1");
        CanonicalModelDocument currentSource = model("other-source", "source-mm", "etag-1", "source-1");
        CanonicalModelDocument target = model("target-model", "target-mm", "etag-2", "target-1");
        TransformationSynchronizationRequest request = new TransformationSynchronizationRequest(currentSource, target,
                TransformationSynchronizationSide.SOURCE, TransformationSynchronizationMode.INCREMENTAL,
                baselineSource, target, null);
        assertThatThrownBy(() -> validator.validate(request)).isInstanceOf(TransformationExecutionException.class)
                .hasMessageContaining("model id differs");
    }

    @Test void rejectsMetamodelRevisionChangeAfterBaseline() {
        CanonicalModelDocument baselineSource = model("source-model", "source-mm", "etag-old", "source-1");
        CanonicalModelDocument currentSource = model("source-model", "source-mm", "etag-new", "source-1");
        CanonicalModelDocument target = model("target-model", "target-mm", "etag-2", "target-1");
        TransformationSynchronizationRequest request = new TransformationSynchronizationRequest(currentSource, target,
                TransformationSynchronizationSide.SOURCE, TransformationSynchronizationMode.INCREMENTAL,
                baselineSource, target, null);
        assertThatThrownBy(() -> validator.validate(request)).isInstanceOf(TransformationExecutionException.class)
                .hasMessageContaining("metamodel revision changed");
    }

    @Test void rejectsAmbiguousBaselineTraceCorrespondence() {
        CanonicalModelDocument source = new CanonicalModelDocument("1.0", "source-model", "source-mm", "etag-1", "source",
                List.of("source-1"), List.of(element("source-1")), List.of(), Map.of());
        CanonicalModelDocument target = new CanonicalModelDocument("1.0", "target-model", "target-mm", "etag-2", "target",
                List.of("target-1"), List.of(element("target-1"), element("target-2")), List.of(), Map.of());
        TransformationTrace trace = new TransformationTrace("exec-1", "tx", source.id(), target.id(), List.of(
                new TransformationTraceLink("source-1", "rule", "a", "target-1", "corr", TransformationExecutionDirection.FORWARD),
                new TransformationTraceLink("source-1", "rule", "b", "target-2", "corr", TransformationExecutionDirection.FORWARD)), List.of(),
                TransformationExecutionDirection.FORWARD);
        TransformationSynchronizationRequest request = new TransformationSynchronizationRequest(source, target,
                TransformationSynchronizationSide.BOTH, TransformationSynchronizationMode.INCREMENTAL, source, target, trace);
        assertThatThrownBy(() -> validator.validate(request)).isInstanceOf(TransformationExecutionException.class)
                .hasMessageContaining("ambiguous source correspondence");
    }

    private CanonicalModelDocument model(String id, String metamodel, String etag, String elementId) {
        return new CanonicalModelDocument("1.0", id, metamodel, etag, id, List.of(elementId), List.of(element(elementId)), List.of(), Map.of());
    }

    private CanonicalModelElement element(String id) { return new CanonicalModelElement(id, "Type", Map.of(), Map.of()); }
}
