package com.example.editor.portability.transaction;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Diario de una transacción; {@code decisions} conserva las decisiones de fusión que la originaron (clave de conflicto → decisión). */
record CapabilityLayerTransactionJournal(String transactionId, String targetMetamodelId,
        Instant createdAt, List<CapabilityLayerTransactionJournalItem> items, Map<String, String> decisions) {
    /** Descripción: Inicializa una instancia de CapabilityLayerTransactionJournal con sus dependencias y datos de entrada. Entradas: transactionId: identificador que delimita el recurso objetivo; targetMetamodelId: identificador que delimita el recurso objetivo; createdAt: dato de entrada necesario para la operación; items: colección de elementos que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ CapabilityLayerTransactionJournal(String transactionId, String targetMetamodelId,
            Instant createdAt, List<CapabilityLayerTransactionJournalItem> items) {
        this(transactionId, targetMetamodelId, createdAt, items, Map.of());
    }
    CapabilityLayerTransactionJournal {
        createdAt = createdAt == null ? Instant.now() : createdAt;
        items = items == null ? List.of() : List.copyOf(items);
        decisions = decisions == null ? Map.of() : Map.copyOf(decisions);
    }
}
