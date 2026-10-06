package com.example.editor.semantic.expression;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExpressionTypeCheckerTests {

    private final ExpressionDependencyAnalyzer dependencyAnalyzer = new ExpressionDependencyAnalyzer();
    private final ExpressionTypeChecker checker = new ExpressionTypeChecker(dependencyAnalyzer);

    @Test
    void infersBooleanForValidComparison() {
        ExpressionCheckResult result = checker.check(ExpressionTestFixtures.positiveAge(), ExpressionTestFixtures.metamodel());
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.inferredType().kind()).isEqualTo(ExpressionValueKind.BOOLEAN);
    }

    @Test
    void reportsUnknownAttribute() {
        ExpressionDefinition expression = new ExpressionDefinition("expression:unknown", "Unknown", "", ExpressionTestFixtures.PERSON, null,
                new AttributeAccessExpressionNode("node:unknown", new SelfExpressionNode("node:self"), "attribute:missing"), Map.of());
        assertThat(checker.check(expression, ExpressionTestFixtures.metamodel()).diagnostics()).extracting(ExpressionDiagnostic::code).contains("UNKNOWN_ATTRIBUTE");
    }

    @Test
    void rejectsOrderedComparisonBetweenStringAndInteger() {
        ExpressionDefinition expression = new ExpressionDefinition("expression:bad-order", "Bad order", "", ExpressionTestFixtures.PERSON, null,
                new BinaryExpressionNode("node:root", BinaryExpressionOperator.GREATER_THAN,
                        new AttributeAccessExpressionNode("node:name", new SelfExpressionNode("node:self"), ExpressionTestFixtures.NAME),
                        ExpressionTestFixtures.integer("node:one", 1)), Map.of());
        assertThat(checker.check(expression, ExpressionTestFixtures.metamodel()).diagnostics()).extracting(ExpressionDiagnostic::code).contains("ORDERED_OPERANDS_REQUIRED");
    }

    @Test
    void relationNavigationInfersCollectionOfTargetObjects() {
        ExpressionDefinition expression = new ExpressionDefinition("expression:employees", "Employees", "", ExpressionTestFixtures.COMPANY, null,
                new RelationNavigationExpressionNode("node:employees", new SelfExpressionNode("node:self"), ExpressionTestFixtures.EMPLOYEES), Map.of());
        ExpressionValueType type = checker.check(expression, ExpressionTestFixtures.metamodel()).inferredType();
        assertThat(type.kind()).isEqualTo(ExpressionValueKind.COLLECTION);
        assertThat(type.elementType().classId()).isEqualTo(ExpressionTestFixtures.PERSON);
    }

    @Test
    void collectionExistsRequiresBooleanPredicate() {
        ExpressionDefinition expression = new ExpressionDefinition("expression:bad-exists", "Bad exists", "", ExpressionTestFixtures.COMPANY, null,
                new CollectionExpressionNode("node:exists", CollectionExpressionOperator.EXISTS,
                        new RelationNavigationExpressionNode("node:employees", new SelfExpressionNode("node:self"), ExpressionTestFixtures.EMPLOYEES),
                        new AttributeAccessExpressionNode("node:name", new SelfExpressionNode("node:item"), ExpressionTestFixtures.NAME)), Map.of());
        assertThat(checker.check(expression, ExpressionTestFixtures.metamodel()).diagnostics()).extracting(ExpressionDiagnostic::code).contains("COLLECTION_PREDICATE_BOOLEAN_REQUIRED");
    }

    @Test
    void detectsDuplicateNodeIds() {
        ExpressionDefinition expression = new ExpressionDefinition("expression:duplicates", "Duplicates", "", ExpressionTestFixtures.PERSON, null,
                new BinaryExpressionNode("node:duplicate", BinaryExpressionOperator.EQUALS,
                        new SelfExpressionNode("node:duplicate"), new SelfExpressionNode("node:right")), Map.of());
        assertThat(checker.check(expression, ExpressionTestFixtures.metamodel()).diagnostics()).extracting(ExpressionDiagnostic::code).contains("DUPLICATE_NODE_ID");
    }

    @Test
    void reportsDeclaredReturnTypeMismatch() {
        ExpressionDefinition expression = new ExpressionDefinition("expression:return", "Return", "", ExpressionTestFixtures.PERSON,
                ExpressionValueType.scalar(ExpressionValueKind.STRING), ExpressionTestFixtures.positiveAge().root(), Map.of());
        assertThat(checker.check(expression, ExpressionTestFixtures.metamodel()).diagnostics()).extracting(ExpressionDiagnostic::code).contains("DECLARED_RETURN_TYPE_MISMATCH");
    }

    @Test
    void classInstancesInfersObjectCollection() {
        ExpressionDefinition expression = new ExpressionDefinition("expression:instances", "Instances", "", "", null,
                new ClassInstancesExpressionNode("node:instances", ExpressionTestFixtures.PERSON), Map.of());
        ExpressionValueType type = checker.check(expression, ExpressionTestFixtures.metamodel()).inferredType();
        assertThat(type.kind()).isEqualTo(ExpressionValueKind.COLLECTION);
        assertThat(type.elementType().classId()).isEqualTo(ExpressionTestFixtures.PERSON);
    }

    @Test
    void dependencyAnalyzerReturnsClassAttributeAndRelationIds() {
        ExpressionDefinition expression = new ExpressionDefinition("expression:dependencies", "Dependencies", "", ExpressionTestFixtures.COMPANY, null,
                new CollectionExpressionNode("node:all", CollectionExpressionOperator.FOR_ALL,
                        new RelationNavigationExpressionNode("node:employees", new SelfExpressionNode("node:self"), ExpressionTestFixtures.EMPLOYEES),
                        ExpressionTestFixtures.positiveAgeRoot("dependency-item")), Map.of());
        assertThat(dependencyAnalyzer.analyze(expression)).contains(
                new ExpressionDependency(ExpressionDependencyKind.CLASS, ExpressionTestFixtures.COMPANY),
                new ExpressionDependency(ExpressionDependencyKind.RELATION, ExpressionTestFixtures.EMPLOYEES),
                new ExpressionDependency(ExpressionDependencyKind.ATTRIBUTE, ExpressionTestFixtures.AGE));
    }

    @Test
    void inheritedClassCanAccessParentAttribute() {
        ExpressionDefinition expression = new ExpressionDefinition("expression:employee-name", "Employee name", "", ExpressionTestFixtures.EMPLOYEE, null,
                new AttributeAccessExpressionNode("node:name", new SelfExpressionNode("node:self"), ExpressionTestFixtures.NAME), Map.of());
        assertThat(checker.check(expression, ExpressionTestFixtures.metamodel()).hasErrors()).isFalse();
    }

    @Test
    void documentReportsDuplicateExpressionIds() {
        ExpressionDefinition first = ExpressionTestFixtures.positiveAge();
        ExpressionDefinition second = new ExpressionDefinition(first.id(), "Duplicate", "", first.contextClassId(), null, first.root(), Map.of());
        ExpressionBatchCheckResult report = checker.checkBatch("library", "library", List.of(first, second), ExpressionTestFixtures.metamodel());
        assertThat(report.diagnostics()).extracting(ExpressionDiagnostic::code).contains("DUPLICATE_EXPRESSION_ID");
    }
}
