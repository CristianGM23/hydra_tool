package com.example.editor.metamodel.importing.updatesite;

/** Estado observable de la caché de análisis. */
public record UpdateSiteAnalysisCacheMetrics(int entries, long hits, long misses,
        long writes, int maximumEntries) { }
