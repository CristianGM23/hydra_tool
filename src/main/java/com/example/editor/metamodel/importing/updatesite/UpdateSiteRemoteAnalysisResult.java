package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Resultado remoto con el mismo análisis canónico que una subida local. */
public record UpdateSiteRemoteAnalysisResult(UpdateSiteImportAnalysis analysis,
        List<UpdateSiteRemoteRepositoryFetch> fetchedResources, long downloadedBytes,
        int cacheHits, List<String> diagnostics) {
    public UpdateSiteRemoteAnalysisResult {
        fetchedResources = fetchedResources == null ? List.of() : List.copyOf(fetchedResources);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
