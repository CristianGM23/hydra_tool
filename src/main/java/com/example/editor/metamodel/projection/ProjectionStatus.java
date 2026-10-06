package com.example.editor.metamodel.projection;

/**
 * Estado persistido de una proyección derivada del MetaModel canónico.
 */
public enum ProjectionStatus {
    NOT_REQUESTED,
    UP_TO_DATE,
    GENERATED_WITH_WARNINGS,
    FAILED,
    STALE
}
