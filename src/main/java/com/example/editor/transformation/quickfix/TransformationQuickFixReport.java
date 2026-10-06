package com.example.editor.transformation.quickfix;

import java.util.List;

/** Conjunto de quick-fixes disponibles para el draft actual. */
public record TransformationQuickFixReport(String transformationId, List<TransformationQuickFix> fixes) {
    public TransformationQuickFixReport {
        transformationId = transformationId == null ? "" : transformationId;
        fixes = fixes == null ? List.of() : List.copyOf(fixes);
    }
}
