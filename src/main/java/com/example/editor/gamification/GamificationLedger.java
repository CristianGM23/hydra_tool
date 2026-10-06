package com.example.editor.gamification;

import java.time.Instant;
import java.util.List;
import com.example.editor.validation.contract.StableIdentifierPolicy;

public record GamificationLedger(String schemaVersion, String id, String metamodelId, Instant updatedAt,
        List<GamificationLedgerEntry> entries) {
    public GamificationLedger {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = GamificationContractVersions.LEDGER;
        id = StableIdentifierPolicy.requireDocumentId(id, "gamificationLedger.id");
        metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "gamificationLedger.metamodelId");
        if(updatedAt == null) updatedAt = Instant.now();
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de GamificationLedger. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static GamificationLedger empty(String metamodelId) { return new GamificationLedger("1.0", metamodelId, metamodelId, Instant.now(), List.of()); }
}
