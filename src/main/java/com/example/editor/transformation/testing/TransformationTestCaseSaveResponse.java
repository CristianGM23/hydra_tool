package com.example.editor.transformation.testing;

/** Respuesta estable de creación o reemplazo de un caso de prueba. */
public record TransformationTestCaseSaveResponse(TransformationTestCase testCase, String etag) {
}
