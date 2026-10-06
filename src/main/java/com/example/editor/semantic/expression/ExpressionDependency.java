package com.example.editor.semantic.expression;

/** Elemento canónico consultado por una expresión. */
public record ExpressionDependency(ExpressionDependencyKind kind, String elementId) {
    public ExpressionDependency {
        if(kind == null) throw new IllegalArgumentException("kind must not be null");
        elementId = elementId == null ? "" : elementId;
    }
}
