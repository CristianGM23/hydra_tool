package com.example.editor.workbench.capability;

import java.util.Map;

/** Summary of one independently accessible HYDRA capability layer. */
public record HydraCapabilityLayerSummary(String id, String displayName, boolean configured,
        int artifactCount, Map<String, Integer> metrics, String recommendedModule) {
    public HydraCapabilityLayerSummary {
        if(id == null || id.isBlank()){throw new IllegalArgumentException("id is required");}
        if(displayName == null || displayName.isBlank()){throw new IllegalArgumentException("displayName is required");}
        if(artifactCount < 0){throw new IllegalArgumentException("artifactCount must not be negative");}
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        recommendedModule = recommendedModule == null ? "" : recommendedModule.trim();
    }
}
