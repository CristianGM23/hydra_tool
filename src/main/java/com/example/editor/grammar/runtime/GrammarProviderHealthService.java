package com.example.editor.grammar.runtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.ProviderHealth;

/** Circuit breaker keyed by provider + source hash with bounded state and manual reset. */
@Component
public class GrammarProviderHealthService {
    private final int threshold;
    private final long openMillis;
    private final int maximumEntries;
    private final Map<Key, State> states = new LinkedHashMap<>();

    /** Descripción: Inicializa una instancia de GrammarProviderHealthService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarProviderHealthService(GrammarProperties properties) {
        threshold = properties.getProviderFailureThreshold();
        openMillis = properties.getProviderCircuitOpenMillis();
        maximumEntries = properties.getMaximumProviderHealthEntries();
    }

    public synchronized boolean allow(String providerId, String sourceHash) {
        Key key = new Key(providerId, sourceHash);
        State state = states.get(key);
        if (state == null || state.openUntil == null) return true;
        if (!state.openUntil.isAfter(Instant.now())) { states.remove(key); return true; }
        return false;
    }

    public synchronized ProviderHealth success(String providerId, String providerInstanceId, String sourceHash) {
        states.remove(new Key(providerId, sourceHash));
        return ProviderHealth.healthy(providerInstanceId);
    }

    public synchronized ProviderHealth failure(String providerId, String providerInstanceId, String sourceHash,
            String detail, List<String> unavailableCapabilities) {
        Instant now = Instant.now();
        Key key = new Key(providerId, sourceHash);
        State previous = states.get(key);
        int failures = previous == null ? 1 : previous.failures + 1;
        Instant openUntil = failures >= threshold ? now.plusMillis(openMillis) : null;
        states.put(key, new State(failures, openUntil, now, detail, copy(unavailableCapabilities)));
        trim();
        return new ProviderHealth(openUntil == null ? "DEGRADED" : "UNAVAILABLE", openUntil != null, providerInstanceId,
                failures, detail, copy(unavailableCapabilities), now,
                Map.of("sourceHash", sourceHash == null ? "" : sourceHash, "retryAfter", openUntil == null ? "" : openUntil.toString()));
    }

    public synchronized ProviderHealth health(String providerId, String providerInstanceId, String sourceHash) {
        Key key = new Key(providerId, sourceHash);
        State state = states.get(key);
        if (state == null) return ProviderHealth.healthy(providerInstanceId);
        boolean open = state.openUntil != null && state.openUntil.isAfter(Instant.now());
        return new ProviderHealth(open ? "UNAVAILABLE" : "DEGRADED", open, providerInstanceId, state.failures,
                state.detail, state.unavailableCapabilities, Instant.now(),
                Map.of("sourceHash", key.sourceHash, "retryAfter", open ? state.openUntil.toString() : ""));
    }

    public synchronized void resetProvider(String providerId) { states.keySet().removeIf(key -> key.providerId.equals(providerId)); }
    public synchronized int size() { return states.size(); }

    /** Descripción: Ejecuta la operación «trim» correspondiente a la responsabilidad de State. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void trim() {
        while (states.size() > maximumEntries) {
            Map.Entry<Key, State> oldest = states.entrySet().stream().min(Comparator.comparing(entry -> entry.getValue().lastTouched)).orElse(null);
            if (oldest == null) break;
            states.remove(oldest.getKey());
        }
    }
    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de State. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> copy(List<String> values){return values==null?List.of():List.copyOf(values);}
    private record Key(String providerId, String sourceHash) {
        private Key { providerId = providerId == null ? "" : providerId.trim(); sourceHash = sourceHash == null ? "" : sourceHash.trim(); }
    }
    private record State(int failures, Instant openUntil, Instant lastTouched, String detail, List<String> unavailableCapabilities) { }
}
