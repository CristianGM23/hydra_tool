package com.example.editor.gamification;

import java.util.List;

/**
 * Read-only recovery view of one stored gamification pair (GET …/{metamodelId}/gamification/recovery). Building it never changes a file.
 * state: HEALTHY (both documents readable), CORRUPT (at least one is malformed), INCOMPLETE (only one of the two is stored) or ABSENT.
 * Each document reports VALID, MALFORMED or MISSING with the SHA-256 ETag of its exact bytes, the value the explicit quarantine expects in
 * If-Match-Definitions / If-Match-Bindings. A readable document is returned as well, so its data can be reused for a clean configuration.
 */
public record GamificationRecoveryReport(String metamodelId, String state, Document definitions, Document bindings,
        GamificationDefinitionModel readableDefinitions, GamificationBindingModel readableBindings,
        List<GamificationQuarantineRecord> quarantines, String recoveryHint) {
    /** One stored document; etag is empty and sizeBytes 0 when it is missing. */
    public record Document(String role, String fileName, String status, String etag, long sizeBytes) { }

    public GamificationRecoveryReport {
        quarantines = quarantines == null ? List.of() : List.copyOf(quarantines);
    }
}
