package com.example.editor.validation.action;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;
@JsonTypeName("COPY_ATTRIBUTE")
public record CopyAttributeOperation(String operationId, ActionSelector source, String sourceAttributeId, ActionSelector target, String targetAttributeId) implements ActionOperation {
    public CopyAttributeOperation {
        operationId = StableIdentifierPolicy.requireElementId(operationId, "operationId");
        if(source == null || target == null) throw new IllegalArgumentException("source and target must not be null");
        sourceAttributeId = StableIdentifierPolicy.requireElementId(sourceAttributeId, "sourceAttributeId");
        targetAttributeId = StableIdentifierPolicy.requireElementId(targetAttributeId, "targetAttributeId");
    }
}
