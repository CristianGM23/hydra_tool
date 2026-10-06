package com.example.editor.standalone.distribution;

/** One actionable validation result associated with a generated path. */
public record StandaloneDistributionDiagnostic(String code, StandaloneDistributionDiagnosticSeverity severity,
        String path, String message) {
    public StandaloneDistributionDiagnostic {
        if(code == null || code.isBlank()){throw new IllegalArgumentException("code is required");}
        if(severity == null){throw new IllegalArgumentException("severity is required");}
        path = path == null ? "" : path.replace('\\', '/').trim();
        if(message == null || message.isBlank()){throw new IllegalArgumentException("message is required");}
        code = code.trim(); message = message.trim();
    }
}
