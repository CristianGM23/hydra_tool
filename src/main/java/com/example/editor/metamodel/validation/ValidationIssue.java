package com.example.editor.metamodel.validation;

/**
 * Describe un resultado de validación.
 *
 * @param severity contiene la gravedad.
 * @param code contiene un identificador estable.
 * @param path identifica el elemento.
 * @param message contiene la explicación.
 */
public record ValidationIssue(ValidationSeverity severity, String code, String path, String message) {
}
