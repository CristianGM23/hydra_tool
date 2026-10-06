package com.example.editor.event.flow;

/** Incidencia estructural o semántica detectada en un flujo. */
public record FlowDiagnostic(FlowDiagnosticSeverity severity, String code, String elementId, String path, String message) { }
