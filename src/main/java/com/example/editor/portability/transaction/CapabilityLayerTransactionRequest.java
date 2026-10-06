package com.example.editor.portability.transaction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.portability.HydraCapabilityPackage;

/** Request to preview or atomically apply a portable capability package. */
public record CapabilityLayerTransactionRequest(HydraCapabilityPackage capabilityPackage,
        String targetMetamodelId, Map<String, String> expectedEtags, List<String> layerOrder,
        boolean persist) {
    public CapabilityLayerTransactionRequest {
        targetMetamodelId = targetMetamodelId == null ? "" : targetMetamodelId.trim();
        expectedEtags = expectedEtags == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(expectedEtags));
        layerOrder = layerOrder == null ? List.of() : List.copyOf(layerOrder);
    }
}
