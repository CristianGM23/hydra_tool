package com.example.editor.gamification;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record GamificationActorState(String actorId, Map<String, Long> points, Map<String, String> levels,
        Set<String> achievements, Map<String, Long> challengeProgress, Set<String> rewards) {
    public GamificationActorState {
        actorId = actorId == null || actorId.isBlank() ? "system" : actorId.trim();
        points = immutableMap(points);
        levels = levels == null || levels.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(levels));
        achievements = achievements == null || achievements.isEmpty() ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(achievements));
        challengeProgress = immutableMap(challengeProgress);
        rewards = rewards == null || rewards.isEmpty() ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(rewards));
    }
    /** Descripción: Ejecuta la operación «immutableMap» correspondiente a la responsabilidad de GamificationActorState. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, Long> immutableMap(Map<String, Long> values) { return values == null || values.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
}
