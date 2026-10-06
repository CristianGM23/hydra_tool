package com.example.editor.event;
/** Incidencia de un contrato de eventos. */
public record EventDiagnostic(EventDiagnosticSeverity severity, String code, String elementId, String path, String message) { }
