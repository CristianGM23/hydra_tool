package com.example.editor.metamodel.importing.updatesite;

import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;

/** Caché LRU en memoria de resultados inmutables del análisis de update sites. */
@Component
public class UpdateSiteAnalysisCache {
    private final UpdateSiteImportProperties properties;
    private final Map<String, CacheEntry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long hits;
    private long misses;
    private long writes;

    /** Descripción: Inicializa una instancia de UpdateSiteAnalysisCache con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteAnalysisCache(UpdateSiteImportProperties properties) { this.properties = properties; }

    public synchronized Optional<UpdateSiteImportAnalysis> find(String key) {
        if(properties.getAnalysisCacheEntries() == 0) { misses++; return Optional.empty(); }
        evictExpired();
        CacheEntry value = entries.get(key);
        if(value == null) { misses++; return Optional.empty(); }
        hits++; return Optional.of(value.analysis());
    }

    public synchronized void put(String key, UpdateSiteImportAnalysis value) {
        if(properties.getAnalysisCacheEntries() == 0 || key == null || value == null) return;
        evictExpired(); entries.put(key, new CacheEntry(value, Instant.now())); writes++;
        while(entries.size() > properties.getAnalysisCacheEntries()) entries.remove(entries.keySet().iterator().next());
    }

    /** Finds one immutable analysis by its public analysisId independently from its cache key. */
    /** Descripción: Obtiene la información correspondiente a «findByAnalysisId» dentro de UpdateSiteAnalysisCache. Entradas: analysisId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public synchronized Optional<UpdateSiteImportAnalysis> findByAnalysisId(String analysisId) {
        if(properties.getAnalysisCacheEntries() == 0 || analysisId == null || analysisId.isBlank()) {
            misses++;
            return Optional.empty();
        }
        evictExpired();
        Optional<UpdateSiteImportAnalysis> result = entries.values().stream().map(CacheEntry::analysis)
                .filter(value -> analysisId.equals(value.analysisId())).findFirst();
        if(result.isPresent()) hits++; else misses++;
        return result;
    }

    /** Descripción: Ejecuta la operación «evictExpired» correspondiente a la responsabilidad de CacheEntry. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void evictExpired() {
        Instant cutoff=Instant.now().minusSeconds(properties.getAnalysisCacheTtlSeconds());
        entries.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(cutoff));
    }

    public synchronized void clear() { entries.clear(); }
    public synchronized UpdateSiteAnalysisCacheMetrics metrics() { evictExpired(); return new UpdateSiteAnalysisCacheMetrics(entries.size(), hits, misses, writes, properties.getAnalysisCacheEntries()); }

    private record CacheEntry(UpdateSiteImportAnalysis analysis, Instant createdAt) { }
}

