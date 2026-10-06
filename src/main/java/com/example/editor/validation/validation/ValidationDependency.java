package com.example.editor.validation.validation;

import com.example.editor.validation.contract.StableIdentifierPolicy;

/** Dependencia explícita de una regla. */
public record ValidationDependency(ValidationDependencyKind kind, String elementId) {
    public ValidationDependency {
        if(kind == null) throw new IllegalArgumentException("kind must not be null");
        elementId = StableIdentifierPolicy.requireElementId(elementId, "elementId");
    }
}
