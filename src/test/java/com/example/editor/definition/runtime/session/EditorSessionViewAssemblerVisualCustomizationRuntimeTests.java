package com.example.editor.definition.runtime.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.editor.definition.ConditionalStyleRule;
import com.example.editor.definition.EdgeMapping;
import com.example.editor.definition.EditorDefinitionCheckReport;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.EditorDefinitionTestFixtures;
import com.example.editor.definition.EditorImageFit;
import com.example.editor.definition.EditorNodeShape;
import com.example.editor.definition.NodeMapping;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.definition.runtime.EditorDefinitionCompiler;
import com.example.editor.validation.expression.ExpressionModelApplicationService;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EditorSessionViewAssemblerVisualCustomizationRuntimeTests {

    @Test
    void executesConditionalVisualCustomizationForHydraNodesAndEdges() {
        var expressions = mock(ExpressionModelApplicationService.class);
        when(expressions.evaluateBooleans(anyString(), anyCollection(), any(), anyString()))
                .thenAnswer(invocation -> matchingExpressions(invocation.getArgument(1)));

        EditorSessionView view = assemble(expressions, editorDefinition());

        EditorRenderedNode person = view.nodes().stream()
                .filter(node -> node.elementId().equals("person-1"))
                .findFirst().orElseThrow();
        assertThat(person.shape()).isEqualTo(EditorNodeShape.ELLIPSE);
        assertThat(person.styles())
                .containsEntry("fillColor", "#ffcc00")
                .containsEntry("strokeWidth", "3");

        EditorRenderedEdge employer = view.edges().stream()
                .filter(edge -> edge.relationInstanceId().equals("relation-instance:employer"))
                .findFirst().orElseThrow();
        assertThat(employer.styles())
                .containsEntry("strokeColor", "#dc2626")
                .containsEntry("strokeWidth", "4")
                .containsEntry("routeStyle", "BEZIER")
                .containsEntry("borderStyle", "DASHED");
    }

    @Test
    void fallsBackToBaseVisualCustomizationWhenExpressionRuntimeFails() {
        var expressions = mock(ExpressionModelApplicationService.class);
        when(expressions.evaluateBooleans(anyString(), anyCollection(), any(), anyString()))
                .thenThrow(new IllegalStateException("synthetic evaluator failure"));

        EditorSessionView view = assemble(expressions, editorDefinition());

        EditorRenderedNode person = view.nodes().stream()
                .filter(node -> node.elementId().equals("person-1"))
                .findFirst().orElseThrow();
        assertThat(person.shape()).isNotEqualTo(EditorNodeShape.ELLIPSE);
        assertThat(person.styles())
                .containsEntry("fillColor", "#dbeafe")
                .containsEntry("strokeColor", "#1d4ed8")
                .doesNotContainEntry("strokeWidth", "3");

        EditorRenderedEdge employer = view.edges().stream()
                .filter(edge -> edge.relationInstanceId().equals("relation-instance:employer"))
                .findFirst().orElseThrow();
        assertThat(employer.styles())
                .containsEntry("strokeColor", "#475569")
                .doesNotContainEntry("routeStyle", "BEZIER");
    }

    private static Map<String, Boolean> matchingExpressions(Collection<String> expressionIds) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        for (String expressionId : expressionIds) {
            values.put(expressionId, expressionId.equals("expression:vip")
                    || expressionId.equals("expression:important"));
        }
        return values;
    }

    private static EditorSessionView assemble(
            ExpressionModelApplicationService expressions,
            EditorDefinitionModel editorDefinition) {
        var metamodel = EditorDefinitionTestFixtures.metamodel();
        var plan = new EditorDefinitionCompiler().compile(
                editorDefinition,
                "\"etag\"",
                new EditorDefinitionCheckReport(editorDefinition.id(), List.of()));
        var representation = plan.representationsById().get("representation:main");
        assertThat(representation).isNotNull();

        CanonicalModelDocument model = new CanonicalModelDocument(
                null,
                "model-visual-runtime",
                metamodel.id(),
                "\"metamodel-etag\"",
                "Visual runtime model",
                List.of("person-1", "company-1"),
                List.of(
                        new CanonicalModelElement(
                                "person-1", "class:Person",
                                Map.of("attribute:Person:name", "Ada"), Map.of()),
                        new CanonicalModelElement("company-1", "class:Company", Map.of(), Map.of())),
                List.of(new CanonicalModelRelation(
                        "relation-instance:employer",
                        "relation:Person:employer",
                        "person-1",
                        "company-1",
                        Map.of())),
                Map.of());
        Instant now = Instant.parse("2026-09-09T10:00:00Z");
        EditorModelSession session = new EditorModelSession(
                null,
                "session-visual-runtime",
                metamodel.id(),
                editorDefinition.id(),
                "\"etag\"",
                "representation:main",
                model,
                1L,
                now,
                now,
                Map.of());

        return new EditorSessionViewAssembler(expressions).assemble(
                new StoredCanonicalDocument<>(session, "\"session-etag\"", Path.of("session.json")),
                plan,
                metamodel,
                representation,
                "",
                false,
                false);
    }

    private static EditorDefinitionModel editorDefinition() {
        EditorDefinitionModel base = EditorDefinitionTestFixtures.editor();
        RepresentationDefinition original = base.representations().get(0);
        NodeMapping originalPerson = original.nodeMappings().get(0);
        EdgeMapping originalEmployer = original.edgeMappings().get(0);

        NodeMapping person = new NodeMapping(
                originalPerson.id(), originalPerson.targetClassId(), originalPerson.representationName(),
                originalPerson.shape(), originalPerson.defaultWidth(), originalPerson.defaultHeight(),
                originalPerson.minimumWidth(), originalPerson.minimumHeight(), originalPerson.labelExpressionId(),
                originalPerson.imageAssetPath(), originalPerson.imageFit(), originalPerson.container(),
                originalPerson.containmentRelationIds(), originalPerson.containerPadding(),
                Map.of("fillColor", "#dbeafe", "strokeColor", "#1d4ed8"),
                List.of(
                        new ConditionalStyleRule(
                                "style:vip-base", "VIP base", true, 10, "expression:vip",
                                Map.of("fillColor", "#f59e0b")),
                        new ConditionalStyleRule(
                                "style:vip-priority", "VIP priority", true, 20, "expression:vip",
                                Map.of("fillColor", "#ffcc00", "strokeWidth", "3", "shape", "ELLIPSE"))),
                originalPerson.annotations());
        NodeMapping company = new NodeMapping(
                "node:company", "class:Company", "Company", EditorNodeShape.ROUNDED_RECTANGLE,
                220, 140, 120, 80, "", "", EditorImageFit.CONTAIN, false, List.of(), 18,
                Map.of("fillColor", "#e2e8f0"), List.of(), Map.of());
        EdgeMapping employer = new EdgeMapping(
                originalEmployer.id(), originalEmployer.relationId(), originalEmployer.sourceClassId(),
                originalEmployer.targetClassId(), originalEmployer.lineType(), originalEmployer.labelExpressionId(),
                Map.of("strokeColor", "#475569"),
                List.of(new ConditionalStyleRule(
                        "style:important", "Important", true, 10, "expression:important",
                        Map.of("strokeColor", "#dc2626", "strokeWidth", "4",
                                "routeStyle", "BEZIER", "borderStyle", "DASHED"))),
                originalEmployer.annotations());

        RepresentationDefinition representation = new RepresentationDefinition(
                original.id(), original.name(), original.kind(), original.rootClassIds(),
                List.of(person, company), List.of(employer), original.toolIds(), original.layers(), original.filters(),
                original.editable(), original.annotations());
        return new EditorDefinitionModel(
                base.schemaVersion(), base.id(), base.metamodelId(), base.name(), List.of(representation),
                base.palette(), base.forms(), base.capabilityManifest(), base.runtimePolicy(),
                base.codeGeneration(), base.annotations());
    }
}
