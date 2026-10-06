package com.example.editor.grammar.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.GrammarAnalysisMode;
import com.example.editor.grammar.core.GrammarAnalysisResult;

/** Bounded observable LRU cache for deterministic grammar analysis results. */
@Component
public class GrammarAnalysisCache {
    private final int maximumEntries;
    private final long ttlMillis;
    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>(32, 0.75f, true);
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    /** Descripción: Inicializa una instancia de GrammarAnalysisCache con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarAnalysisCache(GrammarProperties properties) {
        this.maximumEntries = properties.getMaximumAnalysisCacheEntries();
        this.ttlMillis = properties.getAnalysisCacheTtlMillis();
    }

    public synchronized Optional<GrammarAnalysisResult> get(Key key) {
        Entry entry = entries.get(key);
        if (entry == null) { misses.incrementAndGet(); return Optional.empty(); }
        if (entry.createdAt.plusMillis(ttlMillis).isBefore(Instant.now())) {
            entries.remove(key); misses.incrementAndGet(); return Optional.empty();
        }
        hits.incrementAndGet();
        return Optional.of(entry.result);
    }

    public synchronized void put(Key key, GrammarAnalysisResult result) {
        if (key == null || result == null || result.stale()) return;
        entries.put(key, new Entry(result, Instant.now()));
        while (entries.size() > maximumEntries) {
            Key eldest = entries.keySet().iterator().next();
            entries.remove(eldest);
            evictions.incrementAndGet();
        }
    }

    public synchronized void invalidateGrammar(String grammarId) { entries.keySet().removeIf(key -> key.grammarId.equals(grammarId)); }
    public synchronized int size() { return entries.size(); }
    /** Descripción: Ejecuta la operación «statistics» correspondiente a la responsabilidad de Entry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Statistics statistics() { return new Statistics(size(), maximumEntries, hits.get(), misses.get(), evictions.get()); }

    public record Key(String grammarId, String providerId, String providerVersion, String toolchainHash,
            String sourceHash, GrammarAnalysisMode mode, String settingsHash) {
        public Key {
            grammarId = text(grammarId); providerId = text(providerId); providerVersion = text(providerVersion);
            toolchainHash = text(toolchainHash); sourceHash = text(sourceHash); mode = mode == null ? GrammarAnalysisMode.FAST : mode;
            settingsHash = text(settingsHash);
        }
        /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de Entry. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
    }
    public record Statistics(int entries, int maximumEntries, long hits, long misses, long evictions) { }
    private record Entry(GrammarAnalysisResult result, Instant createdAt) { }
}
