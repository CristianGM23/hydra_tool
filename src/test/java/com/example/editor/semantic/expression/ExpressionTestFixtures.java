package com.example.editor.semantic.expression;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;

public final class ExpressionTestFixtures {
    public static final String METAMODEL_ID = "library";
    public static final String PERSON = "class:Person";
    public static final String EMPLOYEE = "class:Employee";
    public static final String COMPANY = "class:Company";
    public static final String AGE = "attribute:Person:age";
    public static final String NAME = "attribute:Person:name";
    public static final String EMPLOYEES = "relation:Company:employees";

    private ExpressionTestFixtures() { }

    public static MetaModel metamodel() {
        MetaAttribute age = new MetaAttribute(AGE, "age", MetaTypeReference.primitive(MetaPrimitiveType.INTEGER), Multiplicity.optional(), true, true, "", Map.of());
        MetaAttribute name = new MetaAttribute(NAME, "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.optional(), true, true, "", Map.of());
        MetaClass person = new MetaClass(PERSON, "Person", MetaClassKind.CLASS, List.of(age, name), List.of(), Map.of());
        MetaClass employee = new MetaClass(EMPLOYEE, "Employee", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaClass company = new MetaClass(COMPANY, "Company", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation inheritance = new MetaRelation("relation:Employee:Person", "inherits", EMPLOYEE, PERSON, MetaRelationKind.INHERITANCE, null, null, "", true, true, Map.of());
        MetaRelation employees = new MetaRelation(EMPLOYEES, "employees", COMPANY, PERSON, MetaRelationKind.CONTAINMENT, Multiplicity.one(), Multiplicity.many(), "", true, true, Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, METAMODEL_ID, "Library", List.of(person, employee, company), List.of(), List.of(), List.of(inheritance, employees), Map.of());
    }

    public static ExpressionDefinition positiveAge() {
        return new ExpressionDefinition("expression:positive-age", "Positive age", "", PERSON, ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN),
                new BinaryExpressionNode("node:positive-age", BinaryExpressionOperator.GREATER_OR_EQUAL,
                        new AttributeAccessExpressionNode("node:age", new SelfExpressionNode("node:self"), AGE),
                        new LiteralExpressionNode("node:zero", ExpressionValueType.scalar(ExpressionValueKind.INTEGER), 0)), Map.of());
    }

    public static ExpressionNode positiveAgeRoot(String namespace) {
        String prefix = "node:" + namespace;
        return new BinaryExpressionNode(prefix + ":positive-age", BinaryExpressionOperator.GREATER_OR_EQUAL,
                new AttributeAccessExpressionNode(prefix + ":age", new SelfExpressionNode(prefix + ":self"), AGE),
                new LiteralExpressionNode(prefix + ":zero", ExpressionValueType.scalar(ExpressionValueKind.INTEGER), 0));
    }

    public static ExpressionModel expressionModel(ExpressionDefinition... expressions) {
        return new ExpressionModel("1.0", METAMODEL_ID, METAMODEL_ID, List.of(expressions), Map.of());
    }

    public static CanonicalModelDocument model() {
        CanonicalModelElement company = new CanonicalModelElement("object:company", COMPANY, Map.of(), Map.of());
        CanonicalModelElement alice = new CanonicalModelElement("object:alice", PERSON, Map.of(AGE, 35, NAME, "Alice"), Map.of());
        CanonicalModelElement bob = new CanonicalModelElement("object:bob", EMPLOYEE, Map.of(AGE, -2, NAME, "Bob"), Map.of());
        return new CanonicalModelDocument("1.0", "library-model", METAMODEL_ID, "etag-1", "Library model", List.of("object:company"), List.of(company, alice, bob), List.of(
                new CanonicalModelRelation("link:alice", EMPLOYEES, company.id(), alice.id(), Map.of()),
                new CanonicalModelRelation("link:bob", EMPLOYEES, company.id(), bob.id(), Map.of())), Map.of());
    }

    public static ExpressionEvaluationContext context(String selfId) {
        return new ExpressionEvaluationContext(metamodel(), model(), selfId, Map.of());
    }

    public static LiteralExpressionNode integer(String id, int value) {
        return new LiteralExpressionNode(id, ExpressionValueType.scalar(ExpressionValueKind.INTEGER), value);
    }

    public static LiteralExpressionNode decimal(String id, String value) {
        return new LiteralExpressionNode(id, ExpressionValueType.scalar(ExpressionValueKind.DECIMAL), new BigDecimal(value));
    }
}
