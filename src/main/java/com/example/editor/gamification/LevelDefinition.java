package com.example.editor.gamification;

import com.example.editor.validation.contract.StableIdentifierPolicy;

public record LevelDefinition(String id, String name, String pointTypeId, long minimumPoints, String rewardId) {
    public LevelDefinition {
        id = StableIdentifierPolicy.requireElementId(id, "level.id");
        name = name == null || name.isBlank() ? id : name.trim();
        pointTypeId = StableIdentifierPolicy.requireElementId(pointTypeId, "level.pointTypeId");
        if(minimumPoints < 0) throw new IllegalArgumentException("minimumPoints must be non-negative");
        rewardId = rewardId == null ? "" : rewardId.trim();
    }
}
