package com.example.editor.metamodel.lifecycle;

import java.time.Instant;
import java.util.List;

/** Persistent state of a metamodel deletion that may require residual cleanup. */
public record MetamodelDeletionJournal(
        String transactionId,
        String metamodelId,
        Instant createdAt,
        Instant updatedAt,
        boolean deleteNotations,
        boolean deleteExports,
        List<String> expectedFiles,
        List<String> pendingFiles,
        Status status,
        String message) {

    public enum Status { PREPARED, PRIMARY_DELETED, RECOVERY_REQUIRED, COMPLETED, ABORTED }

    public MetamodelDeletionJournal {
        transactionId = transactionId == null ? "" : transactionId.trim();
        metamodelId = metamodelId == null ? "" : metamodelId.trim();
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
        expectedFiles = expectedFiles == null ? List.of() : List.copyOf(expectedFiles);
        pendingFiles = pendingFiles == null ? List.of() : List.copyOf(pendingFiles);
        status = status == null ? Status.PREPARED : status;
        message = message == null ? "" : message;
    }
}
