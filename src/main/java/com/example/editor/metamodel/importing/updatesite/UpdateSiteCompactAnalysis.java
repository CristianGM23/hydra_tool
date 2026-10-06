package com.example.editor.metamodel.importing.updatesite;

import java.util.Map;

/** Small initial analysis response with large collections available through paged endpoints. */
public record UpdateSiteCompactAnalysis(UpdateSiteImportAnalysis analysis,
        Map<String, Integer> collectionSizes, long estimatedFullResponseBytes) {
    public UpdateSiteCompactAnalysis {
        collectionSizes = collectionSizes == null ? Map.of() : Map.copyOf(collectionSizes);
    }
}
