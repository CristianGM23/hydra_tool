package com.example.editor.gamification;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.validation.contract.StableIdentifierPolicy;

/** Catálogo canónico de puntos, niveles, logros, retos y recompensas. */
public record GamificationDefinitionModel(String schemaVersion, String id, String metamodelId, String name,
        List<PointTypeDefinition> pointTypes, List<LevelDefinition> levels, List<AchievementDefinition> achievements,
        List<ChallengeDefinition> challenges, List<RewardDefinition> rewards, Map<String, String> annotations) {
    public GamificationDefinitionModel {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = GamificationContractVersions.DEFINITION_MODEL;
        if(!GamificationContractVersions.DEFINITION_MODEL.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported gamification definition schemaVersion");
        id = StableIdentifierPolicy.requireDocumentId(id, "gamificationDefinition.id");
        metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "gamificationDefinition.metamodelId");
        name = name == null || name.isBlank() ? id : name.trim();
        pointTypes = pointTypes == null ? List.of() : List.copyOf(pointTypes);
        levels = levels == null ? List.of() : List.copyOf(levels);
        achievements = achievements == null ? List.of() : List.copyOf(achievements);
        challenges = challenges == null ? List.of() : List.copyOf(challenges);
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
}
