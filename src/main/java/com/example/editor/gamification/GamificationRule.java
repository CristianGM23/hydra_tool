package com.example.editor.gamification;

import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Binding que transforma evidencia verificada en premio o penalización opcional. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GamificationRule(String id, String name, String eventTypeId, String conditionPath, String operator,
        Object expectedValue, String pointsTypeId, long points, String achievementId, String challengeId,
        String rewardId, GamificationRepeatPolicy repeatPolicy, long cooldownSeconds, int maxAwardsPerActor,
        int maxAwardsPerDay, GamificationEffectKind effectKind, boolean enabled) {
    public GamificationRule {
        id = StableIdentifierPolicy.requireElementId(id, "gamificationRule.id");
        name = name == null || name.isBlank() ? id : name.trim();
        eventTypeId = StableIdentifierPolicy.requireElementId(eventTypeId, "gamificationRule.eventTypeId");
        conditionPath = conditionPath == null ? "" : conditionPath.trim();
        operator = operator == null || operator.isBlank() ? "EQUALS" : operator.trim().toUpperCase();
        pointsTypeId = pointsTypeId == null ? "" : pointsTypeId.trim();
        achievementId = achievementId == null ? "" : achievementId.trim();
        challengeId = challengeId == null ? "" : challengeId.trim();
        rewardId = rewardId == null ? "" : rewardId.trim();
        if(repeatPolicy == null) repeatPolicy = GamificationRepeatPolicy.ONCE_PER_EVENT;
        if(effectKind == null) effectKind = points < 0 ? GamificationEffectKind.PENALTY : GamificationEffectKind.AWARD;
        if(effectKind == GamificationEffectKind.PENALTY && points > 0) points = -points;
        if(effectKind == GamificationEffectKind.AWARD && points < 0) points = Math.abs(points);
        if(cooldownSeconds < 0) throw new IllegalArgumentException("cooldownSeconds must be non-negative");
        if(maxAwardsPerActor < 0 || maxAwardsPerDay < 0) throw new IllegalArgumentException("Award limits must be non-negative");
    }
}
