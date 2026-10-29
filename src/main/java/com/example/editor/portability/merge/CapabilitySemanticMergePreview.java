package com.example.editor.portability.merge;

import java.util.List;
import com.example.editor.portability.HydraCapabilityPackage;

/**
 * Explainable deep merge preview before any canonical layer is changed.
 * {@code unresolvedConflictKeys} lists the conflicts that still have no explicit decision; while it is not empty the preview is not applicable.
 */
public record CapabilitySemanticMergePreview(String previewId, HydraCapabilityPackage mergedPackage,
        List<CapabilitySemanticMergeItem> items, List<CapabilitySemanticMergeConflict> conflicts,
        List<CapabilitySemanticMergeDependency> dependencies, int keptElements,
        int recoveredElements, int mergedElements, int skippedElements,
        boolean applicable, List<String> diagnostics, List<String> unresolvedConflictKeys) {
    /** Descripción: Inicializa una instancia de CapabilitySemanticMergePreview con sus dependencias y datos de entrada. Entradas: previewId: identificador que delimita el recurso objetivo; mergedPackage: dato de entrada necesario para la operación; items: colección de elementos que debe procesarse; conflicts: dato de entrada necesario para la operación; dependencies: dato de entrada necesario para la operación; keptElements: dato de entrada necesario para la operación; recoveredElements: dato de entrada necesario para la operación; mergedElements: dato de entrada necesario para la operación; skippedElements: dato de entrada necesario para la operación; applicable: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CapabilitySemanticMergePreview(String previewId, HydraCapabilityPackage mergedPackage,
            List<CapabilitySemanticMergeItem> items, List<CapabilitySemanticMergeConflict> conflicts,
            List<CapabilitySemanticMergeDependency> dependencies, int keptElements,
            int recoveredElements, int mergedElements, int skippedElements,
            boolean applicable, List<String> diagnostics) {
        this(previewId, mergedPackage, items, conflicts, dependencies, keptElements, recoveredElements, mergedElements,
                skippedElements, applicable, diagnostics, List.of());
    }
    public CapabilitySemanticMergePreview {
        previewId = previewId == null ? "" : previewId.trim();
        items = items == null ? List.of() : List.copyOf(items);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        unresolvedConflictKeys = unresolvedConflictKeys == null ? List.of() : List.copyOf(unresolvedConflictKeys);
    }
}
