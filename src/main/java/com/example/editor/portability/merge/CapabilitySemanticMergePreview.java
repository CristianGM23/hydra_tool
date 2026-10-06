package com.example.editor.portability.merge;

import java.util.List;
import com.example.editor.portability.HydraCapabilityPackage;

/** Explainable deep merge preview before any canonical layer is changed. */
public record CapabilitySemanticMergePreview(String previewId, HydraCapabilityPackage mergedPackage,
        List<CapabilitySemanticMergeItem> items, List<CapabilitySemanticMergeConflict> conflicts,
        List<CapabilitySemanticMergeDependency> dependencies, int keptElements,
        int recoveredElements, int mergedElements, int skippedElements,
        boolean applicable, List<String> diagnostics) {
    public CapabilitySemanticMergePreview {
        previewId = previewId == null ? "" : previewId.trim();
        items = items == null ? List.of() : List.copyOf(items);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
