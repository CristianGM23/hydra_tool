package com.example.editor.definition;
/** Incidencia detectada en una definición de editor. */
public record EditorDefinitionDiagnostic(EditorDefinitionDiagnosticSeverity severity, String code, String elementId, String path, String message) { }
