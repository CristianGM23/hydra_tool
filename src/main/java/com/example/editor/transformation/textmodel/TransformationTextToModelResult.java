package com.example.editor.transformation.textmodel;

import java.util.List;
import com.example.editor.textual.runtime.TextualModelDiagnostic;
import com.example.editor.transformation.runtime.TransformationTrace;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Resultado T2M con modelo parseado, proyección M2M opcional y diagnósticos del parser. */
public record TransformationTextToModelResult(
        CanonicalModelDocument parsedModel,
        CanonicalModelDocument projectedModel,
        TransformationTrace projectionTrace,
        List<TextualModelDiagnostic> diagnostics,
        String grammarSnapshotId) {
    public TransformationTextToModelResult {
        diagnostics=diagnostics==null?List.of():List.copyOf(diagnostics);
        grammarSnapshotId=grammarSnapshotId==null?"":grammarSnapshotId;
    }
}
