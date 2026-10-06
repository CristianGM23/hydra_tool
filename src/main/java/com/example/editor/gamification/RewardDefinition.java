package com.example.editor.gamification;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import com.example.editor.validation.contract.StableIdentifierPolicy;

public record RewardDefinition(String id, String name, String description, Map<String, Object> payload) {
    public RewardDefinition {
        id = StableIdentifierPolicy.requireElementId(id, "reward.id");
        name = name == null || name.isBlank() ? id : name.trim();
        description = description == null ? "" : description.trim();
        payload = payload == null || payload.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
