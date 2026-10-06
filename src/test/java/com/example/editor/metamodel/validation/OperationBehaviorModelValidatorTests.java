package com.example.editor.metamodel.validation;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.metamodel.core.OperationBinding;
import com.example.editor.metamodel.core.OperationImplementationKind;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba que firmas e implementaciones permanezcan desacopladas y coherentes. */
class OperationBehaviorModelValidatorTests {

    private final OperationBehaviorModelValidator validator = new OperationBehaviorModelValidator();

    @Test
    void acceptsExpressionForQueryAndActionForCommand() {
        OperationBehaviorModel behavior = new OperationBehaviorModel("1.0", "sample", List.of(
                new OperationBinding("operation:query", OperationImplementationKind.EXPRESSION, "expression:query", "", "", Map.of()),
                new OperationBinding("operation:command", OperationImplementationKind.ACTION, "", "action:command", "", Map.of())), Map.of());

        assertThat(validator.validate(model(), behavior).hasErrors()).isFalse();
    }

    @Test
    void rejectsMismatchedMetamodelUnknownOperationsAndDuplicateBindings() {
        OperationBehaviorModel behavior = new OperationBehaviorModel("1.0", "other", List.of(
                new OperationBinding("operation:missing", OperationImplementationKind.UNIMPLEMENTED, "", "", "", Map.of()),
                new OperationBinding("operation:query", OperationImplementationKind.EXPRESSION, "expression:first", "", "", Map.of()),
                new OperationBinding("operation:query", OperationImplementationKind.EXPRESSION, "expression:second", "", "", Map.of())), Map.of());

        assertThat(validator.validate(model(), behavior).errors()).extracting(ValidationIssue::code)
                .contains("OPERATION_BEHAVIOR_METAMODEL_MISMATCH", "OPERATION_BINDING_UNKNOWN", "OPERATION_BINDING_DUPLICATE");
    }

    @Test
    void validatesRequiredImplementationReferencesAndBehaviorKind() {
        OperationBehaviorModel behavior = new OperationBehaviorModel("1.0", "sample", List.of(
                new OperationBinding("operation:query", OperationImplementationKind.ACTION, "", "", "", Map.of()),
                new OperationBinding("operation:command", OperationImplementationKind.EXPRESSION, "", "", "", Map.of()),
                new OperationBinding("operation:external", OperationImplementationKind.EXTERNAL, "", "", "", Map.of())), Map.of());

        ValidationReport report = validator.validate(model(), behavior);
        assertThat(report.errors()).extracting(ValidationIssue::code)
                .contains("OPERATION_ACTION_ID_EMPTY", "QUERY_OPERATION_ACTION_BINDING", "OPERATION_EXPRESSION_ID_EMPTY", "OPERATION_EXTERNAL_REFERENCE_EMPTY", "COMMAND_OPERATION_EXPRESSION_BINDING");
        assertThat(report.warnings()).isEmpty();
    }

    private MetaModel model() {
        MetaOperation query = operation("operation:query", MetaOperationBehaviorKind.QUERY);
        MetaOperation command = operation("operation:command", MetaOperationBehaviorKind.COMMAND);
        MetaOperation external = operation("operation:external", MetaOperationBehaviorKind.UNSPECIFIED);
        MetaClass owner = new MetaClass("class:Owner", "Owner", MetaClassKind.CLASS, List.of(), List.of(query, command, external), Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "sample", "Sample", List.of(owner), List.of(), List.of(), List.of(), Map.of());
    }

    private MetaOperation operation(String id, MetaOperationBehaviorKind behaviorKind) {
        return new MetaOperation(id, id.substring(id.indexOf(':') + 1), MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, List.of(), List.of(), behaviorKind, Map.of());
    }
}
