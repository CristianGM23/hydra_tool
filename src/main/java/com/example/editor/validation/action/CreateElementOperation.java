package com.example.editor.validation.action;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
@JsonTypeName("CREATE_ELEMENT")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CreateElementOperation(String operationId, String classId, String resultName, String containmentRelationId, ActionSelector container) implements ActionOperation {
    public CreateElementOperation {
        operationId = StableIdentifierPolicy.requireElementId(operationId, "operationId");
        classId = StableIdentifierPolicy.requireElementId(classId, "classId");
        resultName = StableIdentifierPolicy.requireElementId(resultName, "resultName");
        containmentRelationId = containmentRelationId == null ? "" : containmentRelationId;
        if(!containmentRelationId.isBlank()) StableIdentifierPolicy.requireElementId(containmentRelationId, "containmentRelationId");
        if(containmentRelationId.isBlank() != (container == null)) throw new IllegalArgumentException("containmentRelationId and container must be provided together");
    }
}
