package com.example.editor.transformation.core;

/**
 * Neutral binding intents. They describe what the user means without choosing ATL,
 * ETL, Java, Xtend or another execution technology.
 */
public enum TransformationBindingKind {
    DIRECT,
    CONSTANT,
    EXPRESSION,
    TRACE_EQUIVALENT
}
