package com.example.editor.runtime.job;

import java.time.Instant;

/** Cross-layer summary of a long-running HYDRA operation. */
public record HydraJobSnapshot(String id, String type, String label, HydraJobStatus status, int progress,
        String phase, String message, String correlationId, Instant createdAt, Instant updatedAt, boolean cancellable) {
    public HydraJobSnapshot {
        id = text(id); type = text(type); label = text(label); phase = text(phase); message = text(message);
        correlationId = text(correlationId); status = status == null ? HydraJobStatus.QUEUED : status;
        progress = Math.max(0, Math.min(100, progress));
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de HydraJobSnapshot. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
