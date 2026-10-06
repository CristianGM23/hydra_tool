package com.example.editor.semantic.expression;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;

class OperationCallExpressionTests {

    @Test
    void typesQueryCallsAndPublishesOperationDependency() {
        MetaModel metamodel = metamodel();
        ExpressionDefinition expression = expression("operation:Person:displayName");
        ExpressionCheckResult result = checker().check(expression, metamodel);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.inferredType()).isEqualTo(ExpressionValueType.scalar(ExpressionValueKind.STRING));
        assertThat(result.dependencies()).contains(new ExpressionDependency(ExpressionDependencyKind.OPERATION, "operation:Person:displayName"));
    }

    @Test
    void rejectsCommandCallsInsideExpressions() {
        ExpressionCheckResult result = checker().check(expression("operation:Person:rename"), metamodel());
        assertThat(result.diagnostics()).extracting(ExpressionDiagnostic::code).contains("COMMAND_OPERATION_NOT_ALLOWED_IN_EXPRESSION", "VOID_OPERATION_IN_EXPRESSION");
    }

    @Test
    void evaluatesVariablesAndDelegatesQueryCalls() {
        ExpressionEvaluator evaluator = new ExpressionEvaluator(checker());
        ExpressionDefinition expression = expression("operation:Person:displayName");
        Object value = evaluator.evaluate(expression, context(), (call, target, arguments, context) -> "Alice").value();
        assertThat(value).isEqualTo("Alice");
    }

    private ExpressionDefinition expression(String operationId) {
        return new ExpressionDefinition("expression:operation", "Operation", "", "class:Person", ExpressionValueType.scalar(ExpressionValueKind.STRING),
                new OperationCallExpressionNode("node:call", new SelfExpressionNode("node:self"), operationId, List.of()), Map.of());
    }

    private ExpressionEvaluationContext context() {
        var element = new com.example.editor.validation.model.CanonicalModelElement("person:1", "class:Person", Map.of(), Map.of());
        var document = new com.example.editor.validation.model.CanonicalModelDocument("1.0", "model", "people", "", "People", List.of(element.id()), List.of(element), List.of(), Map.of());
        return new ExpressionEvaluationContext(metamodel(), document, element.id(), Map.of());
    }

    private ExpressionTypeChecker checker() { return new ExpressionTypeChecker(new ExpressionDependencyAnalyzer()); }

    private MetaModel metamodel() {
        MetaOperation query = new MetaOperation("operation:Person:displayName", "displayName", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, List.of(), List.of(), MetaOperationBehaviorKind.QUERY, Map.of());
        MetaParameter name = new MetaParameter("parameter:rename:name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, Map.of());
        MetaOperation command = new MetaOperation("operation:Person:rename", "rename", null, null, true, true, List.of(name), List.of(), MetaOperationBehaviorKind.COMMAND, Map.of());
        MetaClass person = new MetaClass("class:Person", "Person", com.example.editor.metamodel.core.MetaClassKind.CLASS, List.of(), List.of(query, command), Map.of());
        return new MetaModel("2.1", "people", "People", List.of(person), List.of(), List.of(), List.of(), Map.of());
    }
}
