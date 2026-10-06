package com.example.editor.transformation.testing;

import java.util.List;

/** Resultado explicable de un caso de prueba de transformación. */
public record TransformationTestCaseResult(
        String testCaseId,
        String name,
        TransformationTestStatus status,
        List<String> differences) {
    public TransformationTestCaseResult {
        testCaseId = testCaseId == null ? "" : testCaseId;
        name = name == null ? "" : name;
        differences = differences == null ? List.of() : List.copyOf(differences);
    }
}
