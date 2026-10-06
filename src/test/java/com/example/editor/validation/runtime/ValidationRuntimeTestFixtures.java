package com.example.editor.validation.runtime;

import java.util.List;
import java.util.Map;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.semantic.expression.AttributeAccessExpressionNode;
import com.example.editor.semantic.expression.BinaryExpressionNode;
import com.example.editor.semantic.expression.BinaryExpressionOperator;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.semantic.expression.ExpressionValueKind;
import com.example.editor.semantic.expression.ExpressionValueType;
import com.example.editor.semantic.expression.LiteralExpressionNode;
import com.example.editor.semantic.expression.SelfExpressionNode;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.validation.AuthoritativeRepresentation;
import com.example.editor.validation.validation.ConversionStatus;
import com.example.editor.validation.validation.ValidationContext;
import com.example.editor.validation.validation.ValidationContextKind;
import com.example.editor.validation.validation.ValidationDependency;
import com.example.editor.validation.validation.ValidationDependencyKind;
import com.example.editor.validation.validation.ValidationExecutionMode;
import com.example.editor.validation.validation.ValidationModel;
import com.example.editor.validation.validation.ValidationRule;
import com.example.editor.validation.validation.ValidationRuleSet;
import com.example.editor.validation.validation.ValidationSeverity;
import com.example.editor.validation.validation.ValidationTrigger;

final class ValidationRuntimeTestFixtures {
    static final String METAMODEL = "people";
    static final String PERSON = "class:Person";
    static final String AGE = "attribute:Person:age";
    static final String EXPRESSION = "expression:positive-age";
    static final String RULE = "rule:positive-age";

    private ValidationRuntimeTestFixtures() { }

    static MetaModel metamodel() {
        MetaAttribute age = new MetaAttribute(AGE, "age", MetaTypeReference.primitive(MetaPrimitiveType.INTEGER), Multiplicity.one(), true, true, "", Map.of());
        MetaClass person = new MetaClass(PERSON, "Person", MetaClassKind.CLASS, List.of(age), List.of(), Map.of());
        return new MetaModel("2.1", METAMODEL, "People", List.of(person), List.of(), List.of(), List.of(), Map.of());
    }

    static ExpressionDefinition positiveAge() {
        return new ExpressionDefinition(EXPRESSION, "Positive age", "", PERSON, ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN),
                new BinaryExpressionNode("node:positive-age", BinaryExpressionOperator.GREATER_THAN,
                        new AttributeAccessExpressionNode("node:age", new SelfExpressionNode("node:self"), AGE),
                        new LiteralExpressionNode("node:zero", ExpressionValueType.scalar(ExpressionValueKind.INTEGER), 0)), Map.of());
    }

    static ExpressionModel expressions() { return new ExpressionModel("1.0", "people-expressions", METAMODEL, List.of(positiveAge()), Map.of()); }
    static ActionModel actions() { return new ActionModel("1.0", "people-actions", METAMODEL, "people-expressions", List.of(), Map.of()); }

    static ValidationRule rule() { return rule(RULE, true, ValidationExecutionMode.INCREMENTAL, List.of(ValidationTrigger.ON_SAVE, ValidationTrigger.FULL_VALIDATION)); }
    static ValidationRule rule(String id, boolean enabled, ValidationExecutionMode mode, List<ValidationTrigger> triggers) {
        return new ValidationRule(id, "Positive age", "PERSON_AGE_POSITIVE", "", enabled,
                new ValidationContext(ValidationContextKind.ATTRIBUTE, PERSON, AGE), ValidationSeverity.ERROR, EXPRESSION,
                "${elementId} must have a positive age", triggers,
                List.of(new ValidationDependency(ValidationDependencyKind.ATTRIBUTE, AGE)), List.of(), mode,
                AuthoritativeRepresentation.CANONICAL, ConversionStatus.FULL, null, Map.of());
    }

    static ValidationModel validations(ValidationRule... rules) {
        List<ValidationRule> values = rules.length == 0 ? List.of(rule()) : List.of(rules);
        return new ValidationModel("1.0", "people-validations", METAMODEL, "people-expressions", "people-actions",
                List.of(new ValidationRuleSet("rules:quality", "Quality", "", true, 0, values.stream().map(ValidationRule::id).toList(), Map.of())),
                values, List.of(), Map.of());
    }

    static CanonicalModelDocument model() {
        return new CanonicalModelDocument("1.0", "people-model", METAMODEL, "", "People", List.of("person:alice", "person:bob"),
                List.of(new CanonicalModelElement("person:alice", PERSON, Map.of(AGE, 30), Map.of()),
                        new CanonicalModelElement("person:bob", PERSON, Map.of(AGE, -2), Map.of())), List.of(), Map.of());
    }

    static ValidationPlanSourceVersions versions() { return new ValidationPlanSourceVersions("m1", "e1", "a1", "v1", "o1"); }
    static ValidationExecutionRequest request(ValidationRunMode mode) { return new ValidationExecutionRequest(mode, model(), List.of(), "", ValidationChangeSet.empty(), Map.of()); }
}
