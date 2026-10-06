package com.example.editor.validation.action;

import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Selector neutral de un elemento de modelo. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ActionSelector(ActionSelectorKind kind, String parameterName, String elementId) {
    public ActionSelector {
        if(kind == null) throw new IllegalArgumentException("kind must not be null");
        parameterName = parameterName == null ? "" : parameterName;
        elementId = elementId == null ? "" : elementId;
        if(!parameterName.isBlank()) StableIdentifierPolicy.requireElementId(parameterName, "parameterName");
        if(!elementId.isBlank()) StableIdentifierPolicy.requireElementId(elementId, "elementId");
    }
}
