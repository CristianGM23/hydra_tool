package com.example.editor.integration;
/** Estrategia no destructiva utilizada al generar la integración. */
public enum InjectionStrategy {
    ADD_MODULE,
    ADD_PLUGIN,
    ADD_PACKAGE,
    GENERATE_SIDECAR,
    GENERATE_PROJECTION,
    PATCH_EXISTING
}
