package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Internal complete capture used by remote analysis and reproducible snapshots. */
record UpdateSiteRemoteCapture(String rootUrl, List<UpdateSiteInputFile> inputs,
        List<UpdateSiteRemoteRepositoryFetch> fetchedResources, long downloadedBytes,
        int cacheHits, List<String> diagnostics) {
    UpdateSiteRemoteCapture {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        fetchedResources = fetchedResources == null ? List.of() : List.copyOf(fetchedResources);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
