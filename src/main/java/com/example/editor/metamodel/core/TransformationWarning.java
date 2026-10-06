package com.example.editor.metamodel.core;

/**
 * Describe una pérdida o adaptación realizada durante una transformación.
 *
 * @param code contiene un identificador estable.
 * @param path identifica el elemento afectado.
 * @param message contiene la explicación.
 */
public record TransformationWarning(String code, String path, String message) {
}
