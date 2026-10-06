package com.example.editor.validation.action;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;
@JsonTypeName("TRANSFORM_TEXT")
public record TransformTextOperation(String operationId, ActionSelector target, String attributeId, TextTransformation transformation) implements ActionOperation {
    public TransformTextOperation {
        operationId = StableIdentifierPolicy.requireElementId(operationId, "operationId");
        if(target == null) throw new IllegalArgumentException("target must not be null");
        attributeId = StableIdentifierPolicy.requireElementId(attributeId, "attributeId");
        if(transformation == null) throw new IllegalArgumentException("transformation must not be null");
    }
}
