package com.example.editor.portability.merge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.portability.HydraCapabilityPackage;

/** Request for a semantic package merge followed optionally by an AI transaction. */
public record CapabilitySemanticMergeRequest(HydraCapabilityPackage existingPackage,
        HydraCapabilityPackage recoveredPackage, String targetMetamodelId,
        Map<String, String> expectedEtags, List<String> layerOrder,
        CapabilitySemanticMergeDecision defaultDecision,
        Map<String, CapabilitySemanticMergeDecision> decisions, boolean persist) {
    public CapabilitySemanticMergeRequest {
        targetMetamodelId = text(targetMetamodelId);
        expectedEtags = expectedEtags == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(expectedEtags));
        layerOrder = layerOrder == null ? List.of() : List.copyOf(layerOrder);
        defaultDecision = defaultDecision == null ? CapabilitySemanticMergeDecision.MERGE : defaultDecision;
        decisions = decisions == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(decisions));
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de CapabilitySemanticMergeRequest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value == null ? "" : value.trim();}
}
