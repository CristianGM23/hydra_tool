package com.example.editor.gamification;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import com.example.editor.validation.contract.StableIdentifierPolicy;

public record GamificationLedgerEntry(String id, String metamodelId, String actorId, String sourceEventId, String ruleId,
        Instant occurredAt, String pointsTypeId, long pointsDelta, String achievementId, String challengeId, String rewardId,
        GamificationLedgerStatus status, String compensationOf, String correlationId, Map<String, String> metadata) {
    public GamificationLedgerEntry {
        id = StableIdentifierPolicy.requireElementId(id, "gamificationLedgerEntry.id");
        metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "gamificationLedgerEntry.metamodelId");
        actorId = actorId == null || actorId.isBlank() ? "system" : actorId.trim();
        sourceEventId = StableIdentifierPolicy.requireElementId(sourceEventId, "gamificationLedgerEntry.sourceEventId");
        ruleId = StableIdentifierPolicy.requireElementId(ruleId, "gamificationLedgerEntry.ruleId");
        if(occurredAt == null) occurredAt = Instant.now();
        pointsTypeId = pointsTypeId == null ? "" : pointsTypeId.trim();
        achievementId = achievementId == null ? "" : achievementId.trim();
        challengeId = challengeId == null ? "" : challengeId.trim();
        rewardId = rewardId == null ? "" : rewardId.trim();
        if(status == null) status = GamificationLedgerStatus.ACTIVE;
        compensationOf = compensationOf == null ? "" : compensationOf.trim();
        correlationId = correlationId == null ? "" : correlationId.trim();
        metadata = metadata == null || metadata.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
