package com.example.editor.transformation.testing;

import java.util.List;

/** Informe agregado y reproducible de la suite persistida de una transformación. */
public record TransformationTestRunReport(
        String transformationId,
        int total,
        int passed,
        int failed,
        int errors,
        List<TransformationTestCaseResult> cases,
        TransformationCoverageReport coverage) {
    public TransformationTestRunReport {
        transformationId = transformationId == null ? "" : transformationId;
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}
