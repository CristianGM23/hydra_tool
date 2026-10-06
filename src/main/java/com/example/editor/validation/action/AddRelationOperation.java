package com.example.editor.validation.action;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;
@JsonTypeName("ADD_RELATION")
public record AddRelationOperation(String operationId, String relationId, ActionSelector source, ActionSelector target) implements ActionOperation {
    public AddRelationOperation {
        operationId = StableIdentifierPolicy.requireElementId(operationId, "operationId");
        relationId = StableIdentifierPolicy.requireElementId(relationId, "relationId");
        if(source == null || target == null) throw new IllegalArgumentException("source and target must not be null");
    }
}
