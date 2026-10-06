package com.example.editor.transformation.nativeexec;

import java.util.List;
import com.example.editor.transformation.parity.TransformationParityArtifact;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Internal process outcome carrying evidence only long enough for parity comparison. */
record TransformationNativeRuntimeOutcome(
        TransformationNativeRuntimeReport report,
        CanonicalModelDocument targetModel,
        List<TransformationParityArtifact> artifacts) {
    TransformationNativeRuntimeOutcome {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }
}
