package com.example.editor.transformation.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationConflictPolicy;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationDirectionality;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationRuleDirection;
import com.example.editor.transformation.core.TransformationSynchronizationPolicy;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.transformation.core.TransformationTracePolicy;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;

/**
 * Regresión del defecto #12 de HALLAZGOS_PRODUCCION: cuando una política de conflicto descarta todos los cambios de un lado,
 * la sincronización incremental seguía anunciando esa dirección entre las aplicadas aunque no propagara nada.
 * Motor incremental real, trazas y modelos reales; el ejecutor es un sustituto determinista porque la lista de direcciones
 * que se demuestra no depende del contenido que genere. Los resultados esperados son literales.
 */
class TransformationIncrementalSynchronizationDirectionTests {
    private static final String ALICE = "object:alice";
    private static final String TARGET_ALICE = "object:target-alice";
    private static final String NAME = ExpressionTestFixtures.NAME;

    private final TransformationIncrementalSynchronizationEngine engine = new TransformationIncrementalSynchronizationEngine();

    @Test
    void sourceWinsDoesNotAnnounceTheReverseDirectionWhenEveryTargetChangeIsDiscarded() {
        TransformationSynchronizationResult result = synchronize(TransformationConflictPolicy.FORWARD_WINS);

        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflictResolution()).isEqualTo("FORWARD_WINS");
        assertThat(result.appliedDirections()).containsExactly(TransformationExecutionDirection.FORWARD);
        assertThat(result.appliedDirection()).isEqualTo(TransformationExecutionDirection.FORWARD);
        assertThat(nameOf(result.targetModel(), TARGET_ALICE)).as("el destino editado no se sobrescribe con un cambio ignorado").isEqualTo("Alison");
    }

    @Test
    void targetWinsDoesNotAnnounceTheForwardDirectionWhenEverySourceChangeIsDiscarded() {
        TransformationSynchronizationResult result = synchronize(TransformationConflictPolicy.REVERSE_WINS);

        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflictResolution()).isEqualTo("REVERSE_WINS");
        assertThat(result.appliedDirections()).containsExactly(TransformationExecutionDirection.REVERSE);
        assertThat(result.appliedDirection()).isEqualTo(TransformationExecutionDirection.REVERSE);
        assertThat(nameOf(result.sourceModel(), ALICE)).as("el origen editado no se sobrescribe con un cambio ignorado").isEqualTo("Alicia");
    }

    private TransformationSynchronizationResult synchronize(TransformationConflictPolicy policy) {
        CanonicalModelDocument sourceBaseline = model("src-model", ALICE, "Alice");
        CanonicalModelDocument targetBaseline = model("tgt-model", TARGET_ALICE, "Alice");
        CanonicalModelDocument source = model("src-model", ALICE, "Alicia");
        CanonicalModelDocument target = model("tgt-model", TARGET_ALICE, "Alison");
        TransformationTrace baselineTrace = new TransformationTrace("baseline", "transformation-direction", "src-model", "tgt-model",
                List.of(new TransformationTraceLink(ALICE, "rule:person", "target", TARGET_ALICE, "corr:person", TransformationExecutionDirection.FORWARD)),
                List.of(), TransformationExecutionDirection.FORWARD);
        TransformationSynchronizationRequest request = new TransformationSynchronizationRequest(source, target,
                TransformationSynchronizationSide.BOTH, TransformationSynchronizationMode.INCREMENTAL,
                sourceBaseline, targetBaseline, baselineTrace);
        BiFunction<CanonicalModelDocument, TransformationExecutionDirection, TransformationExecutionResult> executor = (input, direction) ->
                new TransformationExecutionResult(direction == TransformationExecutionDirection.FORWARD ? target : source,
                        new TransformationTrace("candidate", "transformation-direction", "src-model", "tgt-model", List.of(), List.of(), direction));
        return engine.synchronize(document(policy), request, executor);
    }

    private static CanonicalModelDocument model(String id, String elementId, String name) {
        CanonicalModelElement person = new CanonicalModelElement(elementId, ExpressionTestFixtures.PERSON, Map.of(NAME, name), Map.of());
        return new CanonicalModelDocument("1.0", id, ExpressionTestFixtures.METAMODEL_ID, "etag-1", id, List.of(elementId), List.of(person), List.of(), Map.of());
    }

    private static Object nameOf(CanonicalModelDocument model, String elementId) {
        return model.elements().stream().filter(element -> element.id().equals(elementId)).findFirst().orElseThrow().attributes().get(NAME);
    }

    private static TransformationDefinitionDocument document(TransformationConflictPolicy policy) {
        TransformationRule forward = new TransformationRule("rule:person", "Person to Person", true, "source",
                ExpressionTestFixtures.PERSON, "personSource", null,
                List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(new TransformationBinding("binding:name", "person", NAME, TransformationBindingKind.DIRECT, NAME, null, null, null, Map.of())),
                Map.of(), TransformationRuleDirection.FORWARD, "corr:person");
        TransformationRule reverse = new TransformationRule("rule:person-reverse", "Person to Person (reverse)", true, "target",
                ExpressionTestFixtures.PERSON, "personTarget", null,
                List.of(new TransformationTargetPattern("source:person", "source", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(new TransformationBinding("binding:name-reverse", "person", NAME, TransformationBindingKind.DIRECT, NAME, null, null, null, Map.of())),
                Map.of(), TransformationRuleDirection.REVERSE, "corr:person");
        return new TransformationDefinitionDocument("1.1", "transformation-direction", "Direction", null,
                TransformationDirectionality.BIDIRECTIONAL,
                new TransformationSynchronizationPolicy(policy, false, true),
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "", "source", true, Map.of())),
                List.of(new TransformationEndpoint("target", TransformationEndpointRole.TARGET, ExpressionTestFixtures.METAMODEL_ID, "", "target", true, Map.of())),
                List.of(forward, reverse), List.of(), null, null, TransformationTracePolicy.defaults(), Map.of());
    }
}
