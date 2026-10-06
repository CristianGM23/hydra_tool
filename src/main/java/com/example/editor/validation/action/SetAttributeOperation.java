package com.example.editor.validation.action;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;
@JsonTypeName("SET_ATTRIBUTE")
public record SetAttributeOperation(String operationId, ActionSelector target, String attributeId, ActionValueSource value) implements ActionOperation {
    public SetAttributeOperation {
        operationId = StableIdentifierPolicy.requireElementId(operationId, "operationId");
        if(target == null) throw new IllegalArgumentException("target must not be null");
        attributeId = StableIdentifierPolicy.requireElementId(attributeId, "attributeId");
        if(value == null) throw new IllegalArgumentException("value must not be null");
    }
}
