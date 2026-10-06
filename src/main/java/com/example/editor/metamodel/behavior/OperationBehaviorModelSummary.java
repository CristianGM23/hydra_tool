package com.example.editor.metamodel.behavior;

/** Resumen ligero de un documento de bindings de operaciones. */
public record OperationBehaviorModelSummary(String metamodelId, String schemaVersion, int bindingCount, String etag) { }
