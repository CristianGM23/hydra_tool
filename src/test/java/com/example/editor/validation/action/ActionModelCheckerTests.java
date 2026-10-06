package com.example.editor.validation.action;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.semantic.expression.ExpressionValueKind;
import com.example.editor.semantic.expression.ExpressionValueType;
import com.example.editor.semantic.expression.LiteralExpressionNode;
import static org.assertj.core.api.Assertions.assertThat;

class ActionModelCheckerTests {
    private ActionModelChecker checker;
    @BeforeEach void setUp() { checker = new ActionModelChecker(new ExpressionTypeChecker(new ExpressionDependencyAnalyzer())); }

    @Test void acceptsADeclarativeQuickFix() {
        ActionModelCheckReport report = checker.check(model(validAction()), ExpressionTestFixtures.metamodel(), ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge()));
        assertThat(report.hasErrors()).isFalse();
        assertThat(report.actions()).singleElement().satisfies(result -> assertThat(result.diagnostics()).isEmpty());
    }

    @Test void rejectsUnknownAttributesAndAtomicActionsWithoutUndo() {
        ActionDefinition action = new ActionDefinition("action:invalid", "Invalid", "", true, List.of(), "",
                List.of(new SetAttributeOperation("operation:set", self(), "attribute:missing", ActionValueSource.literal(1))),
                ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.NOT_SUPPORTED, Map.of());
        ActionModelCheckReport report = checker.check(model(action), ExpressionTestFixtures.metamodel(), ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge()));
        assertThat(report.actions().getFirst().diagnostics()).extracting(ActionDiagnostic::code)
                .contains("UNKNOWN_ATTRIBUTE", "ATOMIC_ACTION_REQUIRES_UNDO");
    }

    @Test void rejectsNonBooleanPreconditions() {
        ExpressionDefinition numeric = new ExpressionDefinition("expression:numeric", "Numeric", "", "", null,
                new LiteralExpressionNode("node:number", ExpressionValueType.scalar(ExpressionValueKind.INTEGER), 1), Map.of());
        ActionDefinition action = new ActionDefinition("action:invalid", "Invalid", "", true, List.of(), numeric.id(),
                List.of(new ClearAttributeOperation("operation:clear", self(), ExpressionTestFixtures.AGE)),
                ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        ActionModelCheckReport report = checker.check(model(action), ExpressionTestFixtures.metamodel(), ExpressionTestFixtures.expressionModel(numeric));
        assertThat(report.actions().getFirst().diagnostics()).extracting(ActionDiagnostic::code).contains("PRECONDITION_NOT_BOOLEAN");
    }

    @Test void detectsIndirectActionCycles() {
        ActionDefinition first = new ActionDefinition("action:first", "First", "", true, List.of(), "",
                List.of(new ExecuteActionOperation("operation:first", "action:second")), ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        ActionDefinition second = new ActionDefinition("action:second", "Second", "", true, List.of(), "",
                List.of(new ExecuteActionOperation("operation:second", "action:first")), ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        ActionModelCheckReport report = checker.check(model(first, second), ExpressionTestFixtures.metamodel(), ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge()));
        assertThat(report.diagnostics()).extracting(ActionDiagnostic::code).contains("ACTION_EXECUTION_CYCLE");
    }

    @Test void createdElementsCanOnlyBeUsedAfterTheirCreation() {
        ActionDefinition action = new ActionDefinition("action:create", "Create", "", true, List.of(), "", List.of(
                new ClearAttributeOperation("operation:premature", new ActionSelector(ActionSelectorKind.CREATED_ELEMENT, "created", ""), ExpressionTestFixtures.AGE),
                new CreateElementOperation("operation:create", ExpressionTestFixtures.PERSON, "created", "", null)),
                ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        ActionModelCheckReport report = checker.check(model(action), ExpressionTestFixtures.metamodel(), ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge()));
        assertThat(report.actions().getFirst().diagnostics()).extracting(ActionDiagnostic::code).contains("UNKNOWN_CREATED_ELEMENT");
    }

    private ActionDefinition validAction() {
        return new ActionDefinition("action:set-age-zero", "Set age to zero", "", true, List.of(), "",
                List.of(new SetAttributeOperation("operation:set-age", self(), ExpressionTestFixtures.AGE, ActionValueSource.literal(0))),
                ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
    }
    private ActionSelector self() { return new ActionSelector(ActionSelectorKind.SELF, "", ""); }
    private ActionModel model(ActionDefinition... actions) { return new ActionModel("1.0", "library-actions", ExpressionTestFixtures.METAMODEL_ID, ExpressionTestFixtures.METAMODEL_ID, List.of(actions), Map.of()); }
}
