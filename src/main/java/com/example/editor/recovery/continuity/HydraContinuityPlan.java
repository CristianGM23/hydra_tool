package com.example.editor.recovery.continuity;

import java.time.Instant;
import java.util.Map;

/** BE local continuity policy with measurable recovery objectives. */
public record HydraContinuityPlan(String planId, String name, boolean enabled,
        long rpoMinutes, long rtoSeconds, int retentionCount, int drillCadenceDays,
        boolean simulateCorruption, Map<String,String> annotations, Instant updatedAt) {
    public HydraContinuityPlan {
        planId=planId==null?"":planId.trim(); name=name==null?"":name.trim();
        rpoMinutes=Math.max(1,rpoMinutes); rtoSeconds=Math.max(1,rtoSeconds);
        retentionCount=Math.max(1,retentionCount); drillCadenceDays=Math.max(1,drillCadenceDays);
        annotations=annotations==null?Map.of():Map.copyOf(annotations); updatedAt=updatedAt==null?Instant.now():updatedAt;
    }
}
