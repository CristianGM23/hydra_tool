package com.example.editor.gamification;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.validation.contract.StableIdentifierPolicy;

/** Reglas que conectan eventos canónicos con el catálogo de gamificación. */
public record GamificationBindingModel(String schemaVersion, String id, String metamodelId, String definitionModelId,
        List<GamificationRule> rules, Map<String, String> annotations) {
    public GamificationBindingModel {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = GamificationContractVersions.BINDING_MODEL;
        if(!GamificationContractVersions.BINDING_MODEL.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported gamification binding schemaVersion");
        id = StableIdentifierPolicy.requireDocumentId(id, "gamificationBinding.id");
        metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "gamificationBinding.metamodelId");
        definitionModelId = StableIdentifierPolicy.requireDocumentId(definitionModelId, "gamificationBinding.definitionModelId");
        rules = rules == null ? List.of() : List.copyOf(rules);
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
}
