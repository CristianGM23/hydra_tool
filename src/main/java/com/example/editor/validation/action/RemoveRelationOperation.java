package com.example.editor.validation.action;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;
@JsonTypeName("REMOVE_RELATION")
public record RemoveRelationOperation(String operationId, String relationId, ActionSelector source, ActionSelector target) implements ActionOperation {
    public RemoveRelationOperation {
        operationId = StableIdentifierPolicy.requireElementId(operationId, "operationId");
        relationId = StableIdentifierPolicy.requireElementId(relationId, "relationId");
        if(source == null || target == null) throw new IllegalArgumentException("source and target must not be null");
    }
}
