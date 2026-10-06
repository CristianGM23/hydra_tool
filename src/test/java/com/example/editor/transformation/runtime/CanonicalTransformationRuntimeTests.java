package com.example.editor.transformation.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.transformation.TransformationDefinitionChecker;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;

class CanonicalTransformationRuntimeTests {
    private final ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
    private final TransformationPlanCompiler compiler = new TransformationPlanCompiler(new TransformationDefinitionChecker(typeChecker));
    private final CanonicalTransformationRuntime runtime = new CanonicalTransformationRuntime(new ExpressionEvaluator(typeChecker));

    @Test
    void executesAllocationBeforePopulationAndKeepsDirectBindingsDeterministic() {
        MetaModel metamodel = ExpressionTestFixtures.metamodel();
        TransformationRule person = personRule();
        TransformationDefinitionDocument document = document(List.of(person));
        CompiledTransformationPlan plan = compiler.compile(document, metamodel, metamodel);

        TransformationExecutionResult result = runtime.execute(plan, metamodel, metamodel, ExpressionTestFixtures.model());

        assertThat(result.targetModel().elements()).hasSize(2);
        assertThat(result.targetModel().elements()).allSatisfy(element -> assertThat(element.classId()).isEqualTo(ExpressionTestFixtures.PERSON));
        assertThat(result.targetModel().elements()).extracting(element -> element.attributes().get(ExpressionTestFixtures.NAME))
                .containsExactlyInAnyOrder("Alice", "Bob");
        assertThat(result.trace().links()).hasSize(2);
        assertThat(result.trace().bindingTraces()).hasSize(2);
    }

    @Test
    void evaluatesGuardsAgainstEachSourceElement() {
        MetaModel metamodel = ExpressionTestFixtures.metamodel();
        TransformationRule base = personRule();
        TransformationRule guarded = new TransformationRule(base.id(), base.name(), true, base.sourceEndpointId(), base.sourceTypeId(),
                base.sourceAlias(), ExpressionTestFixtures.positiveAge(), base.targets(), base.bindings(), base.annotations());

        TransformationExecutionResult result = runtime.execute(compiler.compile(document(List.of(guarded)), metamodel, metamodel),
                metamodel, metamodel, ExpressionTestFixtures.model());

        assertThat(result.targetModel().elements()).singleElement().satisfies(element ->
                assertThat(element.attributes().get(ExpressionTestFixtures.NAME)).isEqualTo("Alice"));
    }

    @Test
    void resolvesTraceEquivalentRelationsOnlyAfterAllTargetsExist() {
        MetaModel metamodel = ExpressionTestFixtures.metamodel();
        TransformationRule company = new TransformationRule("rule:company", "Company to Company", true, "source",
                ExpressionTestFixtures.COMPANY, "companySource", null,
                List.of(new TransformationTargetPattern("target:company", "target", ExpressionTestFixtures.COMPANY, "company", Map.of())),
                List.of(new TransformationBinding("binding:employees", "company", ExpressionTestFixtures.EMPLOYEES,
                        TransformationBindingKind.TRACE_EQUIVALENT, ExpressionTestFixtures.EMPLOYEES, null, null, null, Map.of())), Map.of());

        TransformationExecutionResult result = runtime.execute(compiler.compile(document(List.of(company, personRule())), metamodel, metamodel),
                metamodel, metamodel, ExpressionTestFixtures.model());

        assertThat(result.targetModel().relations()).hasSize(2);
        assertThat(result.targetModel().relations()).allSatisfy(relation -> assertThat(relation.relationId()).isEqualTo(ExpressionTestFixtures.EMPLOYEES));
        assertThat(result.targetModel().rootElementIds()).hasSize(1);
    }

    private TransformationRule personRule() {
        return new TransformationRule("rule:person", "Person to Person", true, "source", ExpressionTestFixtures.PERSON, "personSource", null,
                List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(new TransformationBinding("binding:name", "person", ExpressionTestFixtures.NAME, TransformationBindingKind.DIRECT,
                        ExpressionTestFixtures.NAME, null, null, null, Map.of())), Map.of());
    }

    private TransformationDefinitionDocument document(List<TransformationRule> rules) {
        return new TransformationDefinitionDocument("1.0", "transformation-test", "Test transformation", null,
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "source-fingerprint", "source", true, Map.of())),
                List.of(new TransformationEndpoint("target", TransformationEndpointRole.TARGET, ExpressionTestFixtures.METAMODEL_ID, "target-fingerprint", "target", true, Map.of())),
                rules, List.of(), null, null, null, Map.of());
    }
}
