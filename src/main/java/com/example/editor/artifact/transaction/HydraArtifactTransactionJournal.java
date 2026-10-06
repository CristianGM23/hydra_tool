package com.example.editor.artifact.transaction;

import java.time.Instant;
import java.util.List;

/** Journal mínimo e idempotente para poder detectar operaciones de artefactos interrumpidas. */
public record HydraArtifactTransactionJournal(String schemaVersion, String id, HydraArtifactTransactionOperation operation,
        String subjectId, List<String> resources, HydraArtifactTransactionStatus status, String message,
        Instant createdAt, Instant updatedAt) {
    public static final String CURRENT_SCHEMA_VERSION = "1.0";
    public HydraArtifactTransactionJournal {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? CURRENT_SCHEMA_VERSION : schemaVersion;
        if (!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported artifact transaction schemaVersion: " + schemaVersion);
        id = text(id); subjectId = text(subjectId); message = text(message);
        operation = operation == null ? HydraArtifactTransactionOperation.UPDATE : operation;
        status = status == null ? HydraArtifactTransactionStatus.PREPARED : status;
        resources = resources == null ? List.of() : resources.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().sorted().toList();
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
        if (id.isBlank()) throw new IllegalArgumentException("Artifact transaction id is required");
        if (subjectId.isBlank()) throw new IllegalArgumentException("Artifact transaction subjectId is required");
    }

    /** Descripción: Normaliza textos opcionales del journal. Entradas: value: valor de entrada. Salida: cadena no nula. */
    private static String text(String value) { return value == null ? "" : value.trim(); }
}
