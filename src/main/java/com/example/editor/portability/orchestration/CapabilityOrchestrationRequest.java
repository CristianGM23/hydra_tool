package com.example.editor.portability.orchestration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.portability.HydraCapabilityPackage;

/** Request to compose more than two portable sources and optionally persist the result. */
public record CapabilityOrchestrationRequest(HydraCapabilityPackage basePackage,
        List<CapabilityOrchestrationSource> sources, String targetMetamodelId,
        Map<String, String> expectedEtags, List<String> layerOrder, boolean persist) {
    public CapabilityOrchestrationRequest {
        sources = sources == null ? List.of() : List.copyOf(sources);
        targetMetamodelId = targetMetamodelId == null ? "" : targetMetamodelId.trim();
        expectedEtags = expectedEtags == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(expectedEtags));
        layerOrder = layerOrder == null ? List.of() : List.copyOf(layerOrder);
    }
}
