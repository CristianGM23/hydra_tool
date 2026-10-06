package com.example.editor.validation.action;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;
@JsonTypeName("DELETE_ELEMENT")
public record DeleteElementOperation(String operationId, ActionSelector target) implements ActionOperation {
    public DeleteElementOperation {
        operationId = StableIdentifierPolicy.requireElementId(operationId, "operationId");
        if(target == null) throw new IllegalArgumentException("target must not be null");
    }
}
