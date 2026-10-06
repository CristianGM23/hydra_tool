package com.example.editor.performance;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Browser/server measurement for one capability execution. */
public record CapabilityPerformanceSample(String runId, String capabilityId, Instant capturedAt,
        long renderMillis, long heapBytes, int requestCount, long responseBytes,
        long analysisMillis, Map<String, Long> stageMillis, Map<String, Long> chunkBytes) {
    private static final int MAX_DETAIL_ENTRIES = 256;
    public CapabilityPerformanceSample {
        runId = runId == null ? "" : runId.trim();
        capabilityId = capabilityId == null ? "" : capabilityId.trim();
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
        renderMillis = Math.max(0, renderMillis); heapBytes = Math.max(0, heapBytes);
        requestCount = Math.max(0, requestCount); responseBytes = Math.max(0, responseBytes);
        analysisMillis = Math.max(0, analysisMillis);
        stageMillis = normalized(stageMillis, "stage");
        chunkBytes = normalized(chunkBytes, "chunk");
    }

    /** Descripción: Ejecuta la operación «normalized» correspondiente a la responsabilidad de CapabilityPerformanceSample. Entradas: source: dato de entrada necesario para la operación; label: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, Long> normalized(Map<String, Long> source, String label) {
        if(source == null || source.isEmpty()) return Map.of();
        if(source.size() > MAX_DETAIL_ENTRIES) throw new IllegalArgumentException("Too many " + label + " measurements");
        Map<String, Long> result = new LinkedHashMap<>();
        source.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            if(key.isBlank() || key.length() > 180) throw new IllegalArgumentException("Invalid " + label + " measurement name");
            result.put(key, Math.max(0L, entry.getValue() == null ? 0L : entry.getValue()));
        });
        return Map.copyOf(result);
    }
}
