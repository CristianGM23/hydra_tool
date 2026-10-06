package com.example.editor.standalone.publication;

/** Validation diagnostic emitted before publication. */
public record PublicationDiagnostic(String severity, String code, String message, String field) {
}
