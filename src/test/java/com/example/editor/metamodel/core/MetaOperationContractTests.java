package com.example.editor.metamodel.core;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.metamodel.validation.ValidationIssue;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba el contrato neutral de operaciones de MetaModel 2.1. */
class MetaOperationContractTests {

    private final NeutralMetamodelValidator validator = new NeutralMetamodelValidator();

    @Test
    void acceptsAttributesAndOperationsOnInterfaces() {
        MetaAttribute identifier = new MetaAttribute("attribute:identifiable:identifier", "identifier", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, "", Map.of());
        MetaParameter prefix = new MetaParameter("parameter:identifiable:displayName:prefix", "prefix", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.optional(), true, true, Map.of());
        MetaOperation displayName = new MetaOperation("operation:identifiable:displayName", "displayName", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, List.of(prefix), List.of(), MetaOperationBehaviorKind.QUERY, Map.of());
        MetaClass contract = new MetaClass("class:Identifiable", "Identifiable", MetaClassKind.INTERFACE, List.of(identifier), List.of(displayName), Map.of());
        MetaModel model = new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "sample", "Sample", List.of(contract), List.of(), List.of(), List.of(), Map.of());

        assertThat(validator.validate(model).hasErrors()).isFalse();
        assertThat(contract.attributes()).containsExactly(identifier);
        assertThat(contract.operations()).containsExactly(displayName);
    }

    @Test
    void defaultsMissingOperationsToAnEmptyImmutableList() {
        MetaClass withoutOperations = new MetaClass("class:plain", "Plain", MetaClassKind.CLASS, List.of(), List.of(), Map.of());

        assertThat(withoutOperations.operations()).isEmpty();
        assertThatThrownByAdd(withoutOperations.operations());
    }

    @Test
    void rejectsDuplicateOperationSignaturesAndParameterNames() {
        MetaParameter first = parameter("parameter:first", "value");
        MetaParameter repeated = parameter("parameter:second", "value");
        MetaOperation invalid = new MetaOperation("operation:invalid", "calculate", MetaTypeReference.primitive(MetaPrimitiveType.INTEGER), Multiplicity.one(), true, true, List.of(first, repeated), List.of(), MetaOperationBehaviorKind.QUERY, Map.of());
        MetaOperation duplicateSignature = new MetaOperation("operation:duplicate", "calculate", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, List.of(parameter("parameter:third", "left"), parameter("parameter:fourth", "right")), List.of(), MetaOperationBehaviorKind.QUERY, Map.of());
        MetaClass owner = new MetaClass("class:Owner", "Owner", MetaClassKind.CLASS, List.of(), List.of(invalid, duplicateSignature), Map.of());

        assertThat(validator.validate(model(owner)).errors()).extracting(ValidationIssue::code)
                .contains("PARAMETER_NAME_DUPLICATE", "OPERATION_SIGNATURE_DUPLICATE");
    }

    @Test
    void rejectsUnknownOperationTypesAndVoidReturnMultiplicity() {
        MetaParameter parameter = new MetaParameter("parameter:missing", "missing", MetaTypeReference.classType("class:Missing"), Multiplicity.one(), true, true, Map.of());
        MetaOperation invalid = new MetaOperation("operation:invalid", "invalid", null, null, true, true, List.of(parameter), List.of(MetaTypeReference.enumType("enum:Missing")), MetaOperationBehaviorKind.COMMAND, Map.of());
        MetaOperation normalizedVoid = new MetaOperation("operation:void", "voidOperation", null, Multiplicity.one(), true, true, List.of(), List.of(), MetaOperationBehaviorKind.COMMAND, Map.of());
        MetaClass owner = new MetaClass("class:Owner", "Owner", MetaClassKind.CLASS, List.of(), List.of(invalid, normalizedVoid), Map.of());

        assertThat(normalizedVoid.returnMultiplicity()).isNull();
        assertThat(validator.validate(model(owner)).errors()).extracting(ValidationIssue::code)
                .contains("PARAMETER_CLASS_UNKNOWN", "OPERATION_EXCEPTION_ENUM_UNKNOWN");
    }

    @Test
    void allowsOverloadingWhenParameterTypesDiffer() {
        MetaOperation byText = operation("operation:text", "find", MetaTypeReference.primitive(MetaPrimitiveType.STRING));
        MetaOperation byNumber = operation("operation:number", "find", MetaTypeReference.primitive(MetaPrimitiveType.INTEGER));
        MetaClass owner = new MetaClass("class:Owner", "Owner", MetaClassKind.CLASS, List.of(), List.of(byText, byNumber), Map.of());

        assertThat(validator.validate(model(owner)).hasErrors()).isFalse();
    }

    private MetaOperation operation(String id, String name, MetaTypeReference parameterType) {
        MetaParameter parameter = new MetaParameter("parameter:" + id, "value", parameterType, Multiplicity.one(), true, true, Map.of());
        return new MetaOperation(id, name, MetaTypeReference.primitive(MetaPrimitiveType.BOOLEAN), Multiplicity.one(), true, true, List.of(parameter), List.of(), MetaOperationBehaviorKind.QUERY, Map.of());
    }

    private MetaParameter parameter(String id, String name) {
        return new MetaParameter(id, name, MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, Map.of());
    }

    private MetaModel model(MetaClass owner) {
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "sample", "Sample", List.of(owner), List.of(), List.of(), List.of(), Map.of());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void assertThatThrownByAdd(List<?> values) {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> ((List) values).add(null))).isInstanceOf(UnsupportedOperationException.class);
    }
}
