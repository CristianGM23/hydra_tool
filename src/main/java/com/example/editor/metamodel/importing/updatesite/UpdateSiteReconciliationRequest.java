package com.example.editor.metamodel.importing.updatesite;

import java.util.Map;

/** Decisiones explícitas para resolver conflictos; la clave es elementKey. */
public record UpdateSiteReconciliationRequest(String targetId, String primaryCandidateId,
        Map<String, String> conflictCandidateIds) {
    public UpdateSiteReconciliationRequest {
        targetId = targetId == null ? "" : targetId.trim();
        primaryCandidateId = primaryCandidateId == null ? "" : primaryCandidateId.trim();
        conflictCandidateIds = conflictCandidateIds == null ? Map.of() : Map.copyOf(conflictCandidateIds);
    }
}
