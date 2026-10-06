package com.example.editor.registry.federation;

import java.time.Instant;
import java.util.Map;

/** Stored identity and trust policy for one offline catalog peer. */
public record HydraFederatedCatalogSource(String sourceId, String displayName, HydraCatalogTrustLevel trustLevel,
        String expectedIndexSha256, boolean enabled, Map<String,String> annotations, Instant updatedAt) {
    public HydraFederatedCatalogSource {
        sourceId=sourceId==null?"":sourceId.trim(); displayName=displayName==null?"":displayName.trim();
        trustLevel=trustLevel==null?HydraCatalogTrustLevel.REVIEW_REQUIRED:trustLevel;
        expectedIndexSha256=expectedIndexSha256==null?"":expectedIndexSha256.trim();
        annotations=annotations==null?Map.of():Map.copyOf(annotations); updatedAt=updatedAt==null?Instant.now():updatedAt;
    }
}
