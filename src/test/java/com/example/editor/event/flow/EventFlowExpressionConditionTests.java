package com.example.editor.event.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionValueKind;
import com.example.editor.semantic.expression.ExpressionValueType;
import com.example.editor.semantic.expression.LiteralExpressionNode;
import com.example.editor.validation.expression.ExpressionModel;

class EventFlowExpressionConditionTests {
    @Test void validatesReferencedBooleanConditionsWithNeutralKernel() {
        ExpressionDefinition expression = new ExpressionDefinition("expression:allowed", "Allowed", "", "",
                ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN),
                new LiteralExpressionNode("node:allowed", ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN), true), Map.of());
        ExpressionModel expressions = new ExpressionModel(null, "expressions.test", "meta.test", List.of(expression), Map.of());
        FlowNode start = new FlowNode("node:start", "Start", FlowNodeKind.START, "", "", "expression:allowed", Map.of(), null, null, false, true);
        FlowNode end = new FlowNode("node:end", "End", FlowNodeKind.SUCCESS_END, "", "", "", Map.of(), null, null, false, true);
        FlowTransition transition = new FlowTransition("transition:next", start.id(), end.id(), FlowTransitionKind.NEXT, "expression:allowed", 0, "");
        FlowDefinition flow = new FlowDefinition("flow:test", "Flow", "", true, List.of(), List.of(), List.of(start, end), List.of(transition), List.of(), Map.of());
        EventFlowModel model = new EventFlowModel(null, "flow-model.test", "meta.test", "", expressions.id(), List.of(flow), Map.of());
        MetaModel metamodel = new MetaModel(null, "meta.test", "Meta", List.of(), List.of(), List.of(), List.of(), Map.of());

        assertThat(new EventFlowChecker().check(model, null, expressions, metamodel).diagnostics())
                .noneMatch(item -> item.code().contains("CONDITION") || item.code().contains("EXPRESSION_MODEL"));
    }

    @Test void rejectsMissingReferencedCondition() {
        ExpressionModel expressions = new ExpressionModel(null, "expressions.test", "meta.test", List.of(), Map.of());
        FlowNode start = new FlowNode("node:start", "Start", FlowNodeKind.START, "", "", "expression:missing", Map.of(), null, null, false, true);
        FlowNode end = new FlowNode("node:end", "End", FlowNodeKind.SUCCESS_END, "", "", "", Map.of(), null, null, false, true);
        FlowDefinition flow = new FlowDefinition("flow:test", "Flow", "", true, List.of(), List.of(), List.of(start, end),
                List.of(new FlowTransition("transition:next", start.id(), end.id(), FlowTransitionKind.NEXT, "", 0, "")), List.of(), Map.of());
        EventFlowModel model = new EventFlowModel(null, "flow-model.test", "meta.test", "", expressions.id(), List.of(flow), Map.of());
        MetaModel metamodel = new MetaModel(null, "meta.test", "Meta", List.of(), List.of(), List.of(), List.of(), Map.of());

        assertThat(new EventFlowChecker().check(model, null, expressions, metamodel).diagnostics())
                .anyMatch(item -> item.code().equals("UNKNOWN_CONDITION_EXPRESSION"));
    }
}
