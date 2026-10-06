package com.example.editor.gamification;

import com.example.editor.validation.contract.StableIdentifierPolicy;

public record ChallengeDefinition(String id, String name, String description, long targetCount, String rewardId) {
    public ChallengeDefinition {
        id = StableIdentifierPolicy.requireElementId(id, "challenge.id");
        name = name == null || name.isBlank() ? id : name.trim();
        description = description == null ? "" : description.trim();
        if(targetCount <= 0) throw new IllegalArgumentException("targetCount must be positive");
        rewardId = rewardId == null ? "" : rewardId.trim();
    }
}
