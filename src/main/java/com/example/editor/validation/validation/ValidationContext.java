package com.example.editor.validation.validation;

import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Indica el elemento estructural al que se aplica una regla. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ValidationContext(ValidationContextKind kind, String classId, String featureId) {
    public ValidationContext {
        if(kind == null) throw new IllegalArgumentException("kind must not be null");
        classId = classId == null ? "" : classId;
        featureId = featureId == null ? "" : featureId;
        if(!classId.isBlank()) StableIdentifierPolicy.requireElementId(classId, "classId");
        if(!featureId.isBlank()) StableIdentifierPolicy.requireElementId(featureId, "featureId");
    }
}
