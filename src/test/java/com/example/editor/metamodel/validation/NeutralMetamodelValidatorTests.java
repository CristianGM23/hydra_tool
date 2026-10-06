package com.example.editor.metamodel.validation;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba las validaciones independientes de EMF.
 */
class NeutralMetamodelValidatorTests {

    private final NeutralMetamodelValidator validator = new NeutralMetamodelValidator();

    @Test
    void acceptsValidMetamodel() {
        assertThat(validator.validate(validModel()).hasErrors()).isFalse();
    }

    @Test
    void rejectsDuplicateClassifierNames() {
        MetaClass first = new MetaClass("class-a", "Duplicate", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaClass second = new MetaClass("class-b", "Duplicate", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        assertThat(validator.validate(model(List.of(first, second), List.of(), List.of())).errors()).extracting(ValidationIssue::code).contains("CLASSIFIER_NAME_DUPLICATE");
    }

    @Test
    void rejectsUnknownEnumType() {
        MetaAttribute attribute = new MetaAttribute("attribute-1", "state", MetaTypeReference.enumType("missing"), Multiplicity.optional(), true, true, "", Map.of());
        MetaClass root = new MetaClass("class-root", "Root", MetaClassKind.CLASS, List.of(attribute), List.of(), Map.of());
        assertThat(validator.validate(model(List.of(root), List.of(), List.of())).errors()).extracting(ValidationIssue::code).contains("ATTRIBUTE_ENUM_UNKNOWN");
    }

    @Test
    void rejectsInvalidImplementationDirection() {
        MetaClass interfaceType = new MetaClass("interface-a", "Contract", MetaClassKind.INTERFACE, List.of(), List.of(), Map.of());
        MetaClass classType = new MetaClass("class-a", "Service", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation relation = new MetaRelation("implementation-1", "", "interface-a", "class-a", MetaRelationKind.IMPLEMENTATION, null, null, "", true, true, Map.of());
        assertThat(validator.validate(model(List.of(interfaceType, classType), List.of(relation), List.of())).errors()).extracting(ValidationIssue::code).contains("IMPLEMENTATION_INVALID");
    }

    @Test
    void rejectsInheritanceCycles() {
        MetaClass first = new MetaClass("class-a", "A", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaClass second = new MetaClass("class-b", "B", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation firstEdge = new MetaRelation("inheritance-a", "", "class-a", "class-b", MetaRelationKind.INHERITANCE, null, null, "", true, true, Map.of());
        MetaRelation secondEdge = new MetaRelation("inheritance-b", "", "class-b", "class-a", MetaRelationKind.INHERITANCE, null, null, "", true, true, Map.of());
        assertThat(validator.validate(model(List.of(first, second), List.of(firstEdge, secondEdge), List.of())).errors()).extracting(ValidationIssue::code).contains("INHERITANCE_CYCLE");
    }

    @Test
    void rejectsDuplicateEnumLiterals() {
        MetaEnumLiteral first = new MetaEnumLiteral("literal-a", "VALUE", 1, Map.of());
        MetaEnumLiteral second = new MetaEnumLiteral("literal-b", "VALUE", 1, Map.of());
        MetaEnum enumDesign = new MetaEnum("enum-a", "State", List.of(first, second), Map.of());
        assertThat(validator.validate(model(List.of(), List.of(), List.of(enumDesign))).errors()).extracting(ValidationIssue::code).contains("ENUM_LITERAL_NAME_DUPLICATE", "ENUM_LITERAL_VALUE_DUPLICATE");
    }

    @Test
    void rejectsInvalidMultiplicity() {
        MetaAttribute attribute = new MetaAttribute("attribute-1", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), new Multiplicity(2, 1), true, true, "", Map.of());
        MetaClass root = new MetaClass("class-root", "Root", MetaClassKind.CLASS, List.of(attribute), List.of(), Map.of());
        assertThat(validator.validate(model(List.of(root), List.of(), List.of())).errors()).extracting(ValidationIssue::code).contains("MULTIPLICITY_UPPER_INVALID");
    }

    @Test
    void rejectsIncompatibleOpposites() {
        MetaClass first = new MetaClass("class-a", "A", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaClass second = new MetaClass("class-b", "B", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation firstEdge = new MetaRelation("reference-a", "b", "class-a", "class-b", MetaRelationKind.REFERENCE, null, Multiplicity.optional(), "reference-b", true, true, Map.of());
        MetaRelation secondEdge = new MetaRelation("reference-b", "other", "class-a", "class-b", MetaRelationKind.REFERENCE, null, Multiplicity.optional(), "reference-a", true, true, Map.of());
        assertThat(validator.validate(model(List.of(first, second), List.of(firstEdge, secondEdge), List.of())).errors()).extracting(ValidationIssue::code).contains("RELATION_OPPOSITE_INCOMPATIBLE");
    }

    @Test
    void reportsExternalTypesAsWarnings() {
        MetaAttribute attribute = new MetaAttribute("attribute-1", "custom", MetaTypeReference.external("custom:Type"), Multiplicity.optional(), true, true, "", Map.of());
        MetaClass root = new MetaClass("class-root", "Root", MetaClassKind.CLASS, List.of(attribute), List.of(), Map.of());
        ValidationReport report = validator.validate(model(List.of(root), List.of(), List.of()));
        assertThat(report.hasErrors()).isFalse();
        assertThat(report.warnings()).extracting(ValidationIssue::code).contains("ATTRIBUTE_EXTERNAL_TYPE");
    }

    @Test
    void rejectsStructuralRelationsToInterfaces() {
        MetaClass source = new MetaClass("class-a", "A", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaClass target = new MetaClass("interface-b", "B", MetaClassKind.INTERFACE, List.of(), List.of(), Map.of());
        MetaRelation relation = new MetaRelation("reference-a", "b", "class-a", "interface-b", MetaRelationKind.REFERENCE, null, Multiplicity.optional(), "", true, true, Map.of());
        assertThat(validator.validate(model(List.of(source, target), List.of(relation), List.of())).errors()).extracting(ValidationIssue::code).contains("STRUCTURAL_RELATION_TO_INTERFACE");
    }

    private MetaModel validModel() {
        MetaClass interfaceType = new MetaClass("interface-a", "Contract", MetaClassKind.INTERFACE, List.of(), List.of(), Map.of());
        MetaClass classType = new MetaClass("class-a", "Service", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation implementation = new MetaRelation("implementation-a", "", "class-a", "interface-a", MetaRelationKind.IMPLEMENTATION, null, null, "", true, true, Map.of());
        return model(List.of(classType, interfaceType), List.of(implementation), List.of());
    }

    private MetaModel model(List<MetaClass> classes, List<MetaRelation> relations, List<MetaEnum> enums) {
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "sample", "sample", classes, enums, List.of(), relations, Map.of());
    }
}
