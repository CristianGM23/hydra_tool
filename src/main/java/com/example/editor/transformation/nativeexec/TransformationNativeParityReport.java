package com.example.editor.transformation.nativeexec;

import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.export.TransformationExportLanguage;
import com.example.editor.transformation.parity.TransformationParityReport;

/** Combined process evidence and semantic parity result for a native runtime execution. */
public record TransformationNativeParityReport(
        String transformationId,
        TransformationExportLanguage language,
        TransformationKind kind,
        TransformationNativeRuntimeReport execution,
        TransformationParityReport parity) {
    public TransformationNativeParityReport {
        transformationId = transformationId == null ? "" : transformationId;
    }
}
