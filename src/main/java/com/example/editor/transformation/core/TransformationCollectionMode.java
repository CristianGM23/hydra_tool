package com.example.editor.transformation.core;

/** Estrategia neutral para adaptar valores multivaluados antes de escribir el destino. */
public enum TransformationCollectionMode {
    PRESERVE,
    FIRST,
    LAST,
    COUNT
}
