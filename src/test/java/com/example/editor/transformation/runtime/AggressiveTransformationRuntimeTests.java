package com.example.editor.transformation.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.transformation.TransformationDefinitionChecker;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;

/** Stress/metamorphic execution tests for the neutral transformation runtime. */
class AggressiveTransformationRuntimeTests {
    private final ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
    private final TransformationPlanCompiler compiler = new TransformationPlanCompiler(new TransformationDefinitionChecker(typeChecker));
    private final CanonicalTransformationRuntime runtime = new CanonicalTransformationRuntime(new ExpressionEvaluator(typeChecker));

    @Test
    void transformsFiveHundredElementsWithOneTraceAndOneBindingEvidencePerTarget() {
        MetaModel metamodel = ExpressionTestFixtures.metamodel();
        CanonicalModelDocument source = people(500);
        CompiledTransformationPlan plan = compiler.compile(document(personRule(null)), metamodel, metamodel);
        TransformationExecutionResult result = runtime.execute(plan, metamodel, metamodel, source);

        assertThat(result.targetModel().elements()).hasSize(500);
        assertThat(result.trace().links()).hasSize(500);
        assertThat(result.trace().bindingTraces()).hasSize(500);
        assertThat(result.targetModel().elements()).extracting(item -> item.attributes().get(ExpressionTestFixtures.NAME))
                .contains("Person 0", "Person 249", "Person 499");
        assertThat(source.elements()).hasSize(500);
        assertThat(source.elements().getFirst().attributes().get(ExpressionTestFixtures.NAME)).isEqualTo("Person 0");
    }

    @Test
    void repeatedExecutionIsDeterministicAndDoesNotAccumulateTargetsOrTrace() {
        MetaModel metamodel = ExpressionTestFixtures.metamodel(); CanonicalModelDocument source = people(250);
        CompiledTransformationPlan plan = compiler.compile(document(personRule(null)), metamodel, metamodel);
        TransformationExecutionResult first = runtime.execute(plan, metamodel, metamodel, source);
        TransformationExecutionResult second = runtime.execute(plan, metamodel, metamodel, source);

        assertThat(second.targetModel()).isEqualTo(first.targetModel());
        assertThat(second.trace().links()).isEqualTo(first.trace().links());
        assertThat(second.trace().bindingTraces()).isEqualTo(first.trace().bindingTraces());
        assertThat(second.targetModel().elements()).hasSize(250);
    }

    @Test
    void guardFiltersHostileNegativeDataWithoutBreakingTraceCardinality() {
        MetaModel metamodel = ExpressionTestFixtures.metamodel(); CanonicalModelDocument source = people(401);
        CompiledTransformationPlan plan = compiler.compile(document(personRule(ExpressionTestFixtures.positiveAge())), metamodel, metamodel);
        TransformationExecutionResult result = runtime.execute(plan, metamodel, metamodel, source);

        // Ages alternate between positive and negative; only the 201 non-negative elements survive.
        assertThat(result.targetModel().elements()).hasSize(201);
        assertThat(result.trace().links()).hasSize(201);
        assertThat(result.trace().bindingTraces()).hasSize(201);
        assertThat(result.targetModel().elements()).allSatisfy(item -> assertThat(item.attributes().get(ExpressionTestFixtures.NAME)).isNotNull());
    }

    private CanonicalModelDocument people(int count) {
        List<CanonicalModelElement> elements = new ArrayList<>(count); List<String> roots = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String id = "object:person:" + index; roots.add(id);
            elements.add(new CanonicalModelElement(id, ExpressionTestFixtures.PERSON,
                    Map.of(ExpressionTestFixtures.AGE, index % 2 == 0 ? index : -index,
                            ExpressionTestFixtures.NAME, "Person " + index), Map.of()));
        }
        return new CanonicalModelDocument("1.0", "aggressive-model", ExpressionTestFixtures.METAMODEL_ID,
                "etag-aggressive", "Aggressive model", roots, elements, List.of(), Map.of());
    }

    private TransformationRule personRule(com.example.editor.semantic.expression.ExpressionDefinition guard) {
        return new TransformationRule("rule:person", "Person to Person", true, "source", ExpressionTestFixtures.PERSON, "personSource", guard,
                List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(new TransformationBinding("binding:name", "person", ExpressionTestFixtures.NAME, TransformationBindingKind.DIRECT,
                        ExpressionTestFixtures.NAME, null, null, null, Map.of())), Map.of());
    }

    private TransformationDefinitionDocument document(TransformationRule rule) {
        return new TransformationDefinitionDocument("1.0", "aggressive-transformation", "Aggressive transformation", null,
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "source-fingerprint", "source", true, Map.of())),
                List.of(new TransformationEndpoint("target", TransformationEndpointRole.TARGET, ExpressionTestFixtures.METAMODEL_ID, "target-fingerprint", "target", true, Map.of())),
                List.of(rule), List.of(), null, null, null, Map.of());
    }
}
