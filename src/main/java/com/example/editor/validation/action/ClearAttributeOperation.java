package com.example.editor.validation.action;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;
@JsonTypeName("CLEAR_ATTRIBUTE")
public record ClearAttributeOperation(String operationId, ActionSelector target, String attributeId) implements ActionOperation {
    public ClearAttributeOperation {
        operationId = StableIdentifierPolicy.requireElementId(operationId, "operationId");
        if(target == null) throw new IllegalArgumentException("target must not be null");
        attributeId = StableIdentifierPolicy.requireElementId(attributeId, "attributeId");
    }
}
