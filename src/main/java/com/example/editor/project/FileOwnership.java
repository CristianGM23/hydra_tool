package com.example.editor.project;

/** Indica quién controla el ciclo de vida de un archivo. */
public enum FileOwnership {
    OWNED,
    GENERATED,
    EXTERNAL,
    USER_MANAGED
}
