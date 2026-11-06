package com.example.editor.integration.roundtrip.resolution;

import java.util.List;
import java.util.Map;

/** Durable journal with before/after digests for fail-closed model-preserving rollback. */
public record RoundTripBackupJournal(String operationId, List<RoundTripBackupEntry> entries,
        Map<String, String> beforeSha256, Map<String, String> afterSha256) {
    /** Descripción: Valida la identidad de la operación e inmoviliza los listados de copias y hashes del diario de recuperación. Entradas: operationId, entries, beforeSha256 y afterSha256. Salida: inicializa un diario inmutable o rechaza una identidad vacía. */
    public RoundTripBackupJournal {
        if(operationId == null || operationId.isBlank()){throw new IllegalArgumentException("operationId is required");}
        entries = entries == null ? List.of() : List.copyOf(entries);
        beforeSha256 = beforeSha256 == null ? Map.of() : Map.copyOf(beforeSha256);
        afterSha256 = afterSha256 == null ? Map.of() : Map.copyOf(afterSha256);
    }
    /** Descripción: Construye un diario de recuperación compatible con entradas existentes sin introducir hashes de respaldo ficticios. Entradas: operationId como identidad de la operación y entries como archivos afectados. Salida: diario inicializado con los mapas de hashes vacíos. */
    public RoundTripBackupJournal(String operationId, List<RoundTripBackupEntry> entries){
        this(operationId, entries, Map.of(), Map.of());
    }
}
