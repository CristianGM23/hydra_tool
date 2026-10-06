package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Bounded page for large analysis collections. */
public record UpdateSiteAnalysisPage<T>(String analysisId, String collection, int page, int size,
        int totalElements, int totalPages, long estimatedBytes, List<T> items) {
    public UpdateSiteAnalysisPage {
        analysisId = analysisId == null ? "" : analysisId.trim(); collection = collection == null ? "" : collection.trim();
        items = items == null ? List.of() : List.copyOf(items);
    }
}
