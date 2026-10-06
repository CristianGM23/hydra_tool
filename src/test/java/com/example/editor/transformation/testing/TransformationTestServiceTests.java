package com.example.editor.transformation.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.editor.transformation.TransformationApplicationService;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationLoadResult;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.transformation.runtime.TransformationBindingTrace;
import com.example.editor.transformation.runtime.TransformationExecutionResult;
import com.example.editor.transformation.runtime.TransformationRuntimeService;
import com.example.editor.transformation.runtime.TransformationTrace;
import com.example.editor.transformation.runtime.TransformationTraceLink;
import com.example.editor.transformation.text.TransformationTextGenerationService;
import com.example.editor.transformation.testing.persistence.TransformationTestCaseRepository;
import com.example.editor.transformation.textmodel.TransformationTextToModelService;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

class TransformationTestServiceTests {
    @Test
    void runsPersistedM2mCaseAndComputesRuleAndBindingCoverage() {
        TransformationTestCaseRepository repository = mock(TransformationTestCaseRepository.class);
        TransformationApplicationService transformations = mock(TransformationApplicationService.class);
        TransformationRuntimeService m2m = mock(TransformationRuntimeService.class);
        TransformationTextGenerationService m2t = mock(TransformationTextGenerationService.class);
        TransformationTextToModelService t2m = mock(TransformationTextToModelService.class);
        TransformationDefinitionDocument document = document();
        var source = ExpressionTestFixtures.model();
        var expected = ExpressionTestFixtures.model();
        TransformationTestCase testCase = new TransformationTestCase("1.0", "test:person", document.id(), "Person mapping", true,
                source, "", expected, List.of(), Map.of());
        when(transformations.find(document.id())).thenReturn(new TransformationLoadResult(document, "etag-t", null));
        when(repository.find(testCase.id())).thenReturn(Optional.of(new StoredCanonicalDocument<>(testCase, "etag-case", null)));
        TransformationTrace trace = new TransformationTrace("exec", document.id(), source.id(), expected.id(),
                List.of(new TransformationTraceLink("object:alice", "rule:person", "person", "target:alice")),
                List.of(new TransformationBindingTrace("rule:person", "binding:name", com.example.editor.transformation.core.TransformationBindingKind.DIRECT,
                        "object:alice", "target:alice", ExpressionTestFixtures.NAME, ExpressionTestFixtures.NAME)));
        when(m2m.executeDraft(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(source)))
                .thenReturn(new TransformationExecutionResult(expected, trace));

        TransformationTestRunReport report = new TransformationTestService(repository, transformations, m2m, m2t, t2m)
                .runOne(document.id(), testCase.id());

        assertThat(report.passed()).isEqualTo(1);
        assertThat(report.failed()).isZero();
        assertThat(report.coverage().ruleCovered()).isEqualTo(1);
        assertThat(report.coverage().bindingCovered()).isEqualTo(1);
        assertThat(report.coverage().rules()).singleElement().satisfies(item -> assertThat(item.hits()).isEqualTo(1));
    }

    @Test
    void reportsSemanticDifferenceWithoutConvertingItIntoExecutionError() {
        TransformationTestCaseRepository repository = mock(TransformationTestCaseRepository.class);
        TransformationApplicationService transformations = mock(TransformationApplicationService.class);
        TransformationRuntimeService m2m = mock(TransformationRuntimeService.class);
        TransformationDefinitionDocument document = document();
        var source = ExpressionTestFixtures.model();
        var expected = ExpressionTestFixtures.model();
        var actual = new com.example.editor.validation.model.CanonicalModelDocument("1.0", "different-model", expected.metamodelId(), expected.metamodelEtag(),
                expected.name(), expected.rootElementIds(), expected.elements(), expected.relations(), expected.annotations());
        TransformationTestCase testCase = new TransformationTestCase("1.0", "test:difference", document.id(), "Difference", true, source, "", expected, List.of(), Map.of());
        when(transformations.find(document.id())).thenReturn(new TransformationLoadResult(document, "etag-t", null));
        when(repository.find(testCase.id())).thenReturn(Optional.of(new StoredCanonicalDocument<>(testCase, "etag-case", null)));
        when(m2m.executeDraft(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(source)))
                .thenReturn(new TransformationExecutionResult(actual, new TransformationTrace("exec", document.id(), source.id(), actual.id(), List.of(), List.of())));

        TransformationTestRunReport report = new TransformationTestService(repository, transformations, m2m,
                mock(TransformationTextGenerationService.class), mock(TransformationTextToModelService.class)).runOne(document.id(), testCase.id());

        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.errors()).isZero();
        assertThat(report.cases().getFirst().differences()).containsExactly("Canonical model result differs from the expected model");
    }

    private TransformationDefinitionDocument document() {
        TransformationRule rule = new TransformationRule("rule:person", "Person", true, "source", ExpressionTestFixtures.PERSON, "personSource", null,
                List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(new com.example.editor.transformation.core.TransformationBinding("binding:name", "person", ExpressionTestFixtures.NAME,
                        com.example.editor.transformation.core.TransformationBindingKind.DIRECT, ExpressionTestFixtures.NAME, null, null, null, Map.of())), Map.of());
        return new TransformationDefinitionDocument("1.0", "transformation:test-suite", "Test suite", null,
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "source-fingerprint", "source", true, Map.of())),
                List.of(new TransformationEndpoint("target", TransformationEndpointRole.TARGET, ExpressionTestFixtures.METAMODEL_ID, "target-fingerprint", "target", true, Map.of())),
                List.of(rule), List.of(), null, null, null, Map.of());
    }
}
