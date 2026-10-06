package com.example.editor.validation.action;

import java.util.List;

import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;

/** Invoca una operación COMMAND declarada por una clase o interfaz. */
@JsonTypeName("INVOKE_OPERATION")
public record InvokeOperationOperation(String operationId, ActionSelector target, String metamodelOperationId, List<ActionValueSource> arguments) implements ActionOperation {
    public InvokeOperationOperation {
        operationId = StableIdentifierPolicy.requireElementId(operationId, "operationId");
        if(target == null) throw new IllegalArgumentException("target must not be null");
        metamodelOperationId = StableIdentifierPolicy.requireElementId(metamodelOperationId, "metamodelOperationId");
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
}
