package com.example.editor.gamification;

import com.example.editor.validation.contract.StableIdentifierPolicy;

public record AchievementDefinition(String id, String name, String description, String rewardId, boolean hidden) {
    public AchievementDefinition {
        id = StableIdentifierPolicy.requireElementId(id, "achievement.id");
        name = name == null || name.isBlank() ? id : name.trim();
        description = description == null ? "" : description.trim();
        rewardId = rewardId == null ? "" : rewardId.trim();
    }
}
