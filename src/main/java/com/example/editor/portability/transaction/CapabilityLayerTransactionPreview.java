package com.example.editor.portability.transaction;

import java.util.List;

/** Complete precondition and ordering preview before any layer is changed. */
public record CapabilityLayerTransactionPreview(String previewId, String targetMetamodelId,
        List<CapabilityLayerTransactionItem> items, long totalBytes, boolean applicable,
        List<String> diagnostics) {
    public CapabilityLayerTransactionPreview {
        items = items == null ? List.of() : List.copyOf(items);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
