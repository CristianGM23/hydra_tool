package com.example.editor.portability;

public record CapabilityPackageDiagnostic(CapabilityPackageDiagnosticSeverity severity, String code, String elementId, String path, String message) {
    public CapabilityPackageDiagnostic {
        elementId = elementId == null ? "" : elementId;
        path = path == null ? "" : path;
        message = message == null ? "" : message;
    }
}
