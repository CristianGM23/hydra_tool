package com.example.editor.semantic.expression;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionEvaluatorTests {

    private final ExpressionTypeChecker checker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
    private final ExpressionEvaluator evaluator = new ExpressionEvaluator(checker);

    @Test
    void evaluatesValidComparisonToTrue() {
        assertThat(evaluator.evaluate(ExpressionTestFixtures.positiveAge(), ExpressionTestFixtures.context("object:alice")).value()).isEqualTo(true);
    }

    @Test
    void evaluatesInvalidAgeToFalse() {
        assertThat(evaluator.evaluate(ExpressionTestFixtures.positiveAge(), ExpressionTestFixtures.context("object:bob")).value()).isEqualTo(false);
    }

    @Test
    void countsNavigatedRelations() {
        ExpressionDefinition expression = new ExpressionDefinition("expression:count", "Count", "", ExpressionTestFixtures.COMPANY, null,
                new CollectionExpressionNode("node:count", CollectionExpressionOperator.COUNT,
                        new RelationNavigationExpressionNode("node:employees", new SelfExpressionNode("node:self"), ExpressionTestFixtures.EMPLOYEES), null), Map.of());
        assertThat(evaluator.evaluate(expression, ExpressionTestFixtures.context("object:company")).value()).isEqualTo(2);
    }

    @Test
    void evaluatesExistsPredicate() {
        ExpressionDefinition expression = collectionPredicate("expression:exists", CollectionExpressionOperator.EXISTS);
        assertThat(evaluator.evaluate(expression, ExpressionTestFixtures.context("object:company")).value()).isEqualTo(true);
    }

    @Test
    void evaluatesForAllPredicate() {
        ExpressionDefinition expression = collectionPredicate("expression:forall", CollectionExpressionOperator.FOR_ALL);
        assertThat(evaluator.evaluate(expression, ExpressionTestFixtures.context("object:company")).value()).isEqualTo(false);
    }

    @Test
    void selectsMatchingElements() {
        ExpressionDefinition expression = collectionPredicate("expression:select", CollectionExpressionOperator.SELECT);
        assertThat((List<?>) evaluator.evaluate(expression, ExpressionTestFixtures.context("object:company")).value()).extracting("id").containsExactly("object:alice");
    }

    @Test
    void checksUniqueProjectedNames() {
        ExpressionDefinition expression = new ExpressionDefinition("expression:unique", "Unique", "", ExpressionTestFixtures.COMPANY, null,
                new CollectionExpressionNode("node:unique", CollectionExpressionOperator.IS_UNIQUE,
                        new RelationNavigationExpressionNode("node:employees", new SelfExpressionNode("node:self"), ExpressionTestFixtures.EMPLOYEES),
                        new AttributeAccessExpressionNode("node:name", new SelfExpressionNode("node:item"), ExpressionTestFixtures.NAME)), Map.of());
        assertThat(evaluator.evaluate(expression, ExpressionTestFixtures.context("object:company")).value()).isEqualTo(true);
    }

    @Test
    void evaluatesStringFunctions() {
        ExpressionDefinition expression = new ExpressionDefinition("expression:upper", "Upper", "", ExpressionTestFixtures.PERSON, null,
                new FunctionExpressionNode("node:upper", "upper", List.of(new AttributeAccessExpressionNode("node:name", new SelfExpressionNode("node:self"), ExpressionTestFixtures.NAME))), Map.of());
        assertThat(evaluator.evaluate(expression, ExpressionTestFixtures.context("object:alice")).value()).isEqualTo("ALICE");
    }

    @Test
    void evaluatesConditionalExpression() {
        ExpressionDefinition expression = new ExpressionDefinition("expression:conditional", "Conditional", "", ExpressionTestFixtures.PERSON, null,
                new ConditionalExpressionNode("node:conditional", ExpressionTestFixtures.positiveAge().root(),
                        new LiteralExpressionNode("node:adult", ExpressionValueType.scalar(ExpressionValueKind.STRING), "valid"),
                        new LiteralExpressionNode("node:invalid", ExpressionValueType.scalar(ExpressionValueKind.STRING), "invalid")), Map.of());
        assertThat(evaluator.evaluate(expression, ExpressionTestFixtures.context("object:bob")).value()).isEqualTo("invalid");
    }

    @Test
    void rejectsDivisionByZero() {
        ExpressionDefinition expression = new ExpressionDefinition("expression:division", "Division", "", "", null,
                new BinaryExpressionNode("node:divide", BinaryExpressionOperator.DIVIDE, ExpressionTestFixtures.integer("node:one", 1), ExpressionTestFixtures.integer("node:zero", 0)), Map.of());
        assertThatThrownBy(() -> evaluator.evaluate(expression, ExpressionTestFixtures.context("object:alice"))).isInstanceOf(ExpressionEvaluationException.class).hasMessageContaining("Division by zero");
    }

    @Test
    void classInstancesIncludesSubclasses() {
        ExpressionDefinition expression = new ExpressionDefinition("expression:all-persons", "All persons", "", "", null,
                new ClassInstancesExpressionNode("node:instances", ExpressionTestFixtures.PERSON), Map.of());
        assertThat((List<?>) evaluator.evaluate(expression, ExpressionTestFixtures.context("")).value()).extracting("id").containsExactly("object:alice", "object:bob");
    }

    @Test
    void sumsProjectedAges() {
        ExpressionDefinition expression = new ExpressionDefinition("expression:sum", "Sum", "", ExpressionTestFixtures.COMPANY, null,
                new CollectionExpressionNode("node:sum", CollectionExpressionOperator.SUM,
                        new RelationNavigationExpressionNode("node:employees", new SelfExpressionNode("node:self"), ExpressionTestFixtures.EMPLOYEES),
                        new AttributeAccessExpressionNode("node:age", new SelfExpressionNode("node:item"), ExpressionTestFixtures.AGE)), Map.of());
        Object result = evaluator.evaluate(expression, ExpressionTestFixtures.context("object:company")).value();
        assertThat(result).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) result).isEqualByComparingTo(new BigDecimal("33"));
    }

    private ExpressionDefinition collectionPredicate(String id, CollectionExpressionOperator operator) {
        return new ExpressionDefinition(id, id, "", ExpressionTestFixtures.COMPANY, null,
                new CollectionExpressionNode("node:" + id.substring(id.indexOf(':') + 1), operator,
                        new RelationNavigationExpressionNode("node:employees", new SelfExpressionNode("node:self"), ExpressionTestFixtures.EMPLOYEES),
                        ExpressionTestFixtures.positiveAgeRoot(id.substring(id.indexOf(':') + 1))), Map.of());
    }
}
