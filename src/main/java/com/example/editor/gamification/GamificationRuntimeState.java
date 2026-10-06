package com.example.editor.gamification;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import com.example.editor.validation.contract.StableIdentifierPolicy;

public record GamificationRuntimeState(String schemaVersion, String id, String metamodelId, Instant updatedAt,
        Map<String, GamificationActorState> actors, Set<String> processedAwardKeys) {
    public GamificationRuntimeState {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = GamificationContractVersions.RUNTIME_STATE;
        id = StableIdentifierPolicy.requireDocumentId(id, "gamificationRuntimeState.id");
        metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "gamificationRuntimeState.metamodelId");
        if(updatedAt == null) updatedAt = Instant.now();
        actors = actors == null || actors.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(actors));
        processedAwardKeys = processedAwardKeys == null ? Set.of() : Set.copyOf(processedAwardKeys);
    }
    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de GamificationRuntimeState. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static GamificationRuntimeState empty(String metamodelId) { return new GamificationRuntimeState("1.0", metamodelId, metamodelId, Instant.now(), Map.of(), Set.of()); }
}
