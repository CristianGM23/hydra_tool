package com.example.editor.gamification;

import java.util.List;

/**
 * Manifest of one explicit quarantine of a gamification pair, stored as {@code quarantine.json} next to the moved documents and returned
 * by the recovery API. It names documents by role and file name only (never a server path) and records the SHA-256 ETag of the exact
 * bytes that were observed, so an interrupted quarantine can be resumed and a repeated request answered with the same result.
 *
 * <p>status: IN_PROGRESS while documents are being moved, COMPLETE afterwards, LEGACY for a quarantine folder written without a manifest
 * by earlier HYDRA versions (the implicit quarantine of a read) and UNREADABLE when the manifest itself cannot be parsed. Document outcome:
 * PENDING, MOVED (the document is in the quarantine folder) or VANISHED (it disappeared from the store before it could be moved).
 */
public record GamificationQuarantineRecord(String schemaVersion, String metamodelId, String quarantineId, String status, String createdAt,
        String completedAt, String correlationId, List<Document> documents) {
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETE = "COMPLETE";
    public static final String LEGACY = "LEGACY";
    public static final String UNREADABLE = "UNREADABLE";

    /** One document of the pair: role (definitions or bindings), file name, observed ETag and size, state when observed and outcome. */
    public record Document(String role, String fileName, String etag, long sizeBytes, String state, String outcome) {
        public static final String PENDING = "PENDING";
        public static final String MOVED = "MOVED";
        public static final String VANISHED = "VANISHED";

        /** Descripción: Ejecuta la operación «withOutcome» correspondiente a la responsabilidad de GamificationQuarantineRecord. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ Document withOutcome(String value) { return new Document(role, fileName, etag, sizeBytes, state, value); }
    }

    public GamificationQuarantineRecord {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }

    /** Descripción: Ejecuta la operación «withDocuments» correspondiente a la responsabilidad de GamificationQuarantineRecord. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ GamificationQuarantineRecord withDocuments(List<Document> value) {
        return new GamificationQuarantineRecord(schemaVersion, metamodelId, quarantineId, status, createdAt, completedAt, correlationId, value);
    }

    /** Descripción: Ejecuta la operación «completed» correspondiente a la responsabilidad de GamificationQuarantineRecord. Entradas: at: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ GamificationQuarantineRecord completed(String at) {
        return new GamificationQuarantineRecord(schemaVersion, metamodelId, quarantineId, COMPLETE, createdAt, at, correlationId, documents);
    }

    /** Descripción: ETag recorded for a role, or null when the quarantine did not include a document with that role. Entradas: role: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */
    String etagOf(String role) {
        return documents.stream().filter(document -> role.equals(document.role())).map(Document::etag).findFirst().orElse(null);
    }
}
