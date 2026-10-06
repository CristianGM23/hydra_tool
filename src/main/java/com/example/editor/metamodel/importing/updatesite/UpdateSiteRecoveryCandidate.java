package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Candidato a metamodelo ordenado según la estrategia escalonada. */
public record UpdateSiteRecoveryCandidate(String id, int level, UpdateSiteRecoveryMethod method,
        UpdateSiteConfidence confidence, String sourcePath, String sourceBundleId, String format,
        int score, boolean importable, String rationale, List<String> warnings) {
    public UpdateSiteRecoveryCandidate {
        id = id == null ? "" : id;
        method = method == null ? UpdateSiteRecoveryMethod.CODE_INFERENCE : method;
        confidence = confidence == null ? UpdateSiteConfidence.INFERRED : confidence;
        sourcePath = sourcePath == null ? "" : sourcePath;
        sourceBundleId = sourceBundleId == null ? "" : sourceBundleId;
        format = format == null ? "" : format;
        rationale = rationale == null ? "" : rationale;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
