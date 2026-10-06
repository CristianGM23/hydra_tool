package com.example.editor.semantic.expression;

/** Tipos neutrales utilizados por el comprobador y el evaluador de expresiones. */
public enum ExpressionValueKind {
    BOOLEAN,
    INTEGER,
    DECIMAL,
    STRING,
    DATE,
    DATETIME,
    BINARY,
    ENUM,
    OBJECT,
    COLLECTION,
    MODEL,
    ANY,
    NULL
}
