package com.example.editor.validation.action;

import com.example.editor.validation.contract.StableIdentifierPolicy;

/** Parámetro declarado por una acción. */
public record ActionParameter(String name, ActionParameterType type, boolean required) {
    public ActionParameter {
        name = StableIdentifierPolicy.requireElementId(name, "name");
        if(type == null) throw new IllegalArgumentException("type must not be null");
    }
}
