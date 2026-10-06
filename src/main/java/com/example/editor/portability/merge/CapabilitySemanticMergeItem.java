package com.example.editor.portability.merge;

import java.util.List;

/** One rule, action, event, flow node, gamification rule, mapping or binding in the merge preview. */
public record CapabilitySemanticMergeItem(String key, String documentPath, String jsonPointer,
        String semanticKind, String displayName, boolean existingPresent, boolean recoveredPresent,
        boolean conflict, CapabilitySemanticMergeDecision recommendedDecision,
        CapabilitySemanticMergeDecision selectedDecision, List<String> dependencyReferences,
        String explanation) {
    public CapabilitySemanticMergeItem {
        key = text(key); documentPath = text(documentPath); jsonPointer = text(jsonPointer);
        semanticKind = text(semanticKind); displayName = text(displayName);
        recommendedDecision = recommendedDecision == null ? CapabilitySemanticMergeDecision.MERGE : recommendedDecision;
        selectedDecision = selectedDecision == null ? recommendedDecision : selectedDecision;
        dependencyReferences = dependencyReferences == null ? List.of() : List.copyOf(dependencyReferences);
        explanation = text(explanation);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de CapabilitySemanticMergeItem. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value == null ? "" : value.trim();}
}
