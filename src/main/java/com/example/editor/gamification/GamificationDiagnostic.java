package com.example.editor.gamification;
public record GamificationDiagnostic(GamificationDiagnosticSeverity severity, String code, String elementId, String path, String message) { }
