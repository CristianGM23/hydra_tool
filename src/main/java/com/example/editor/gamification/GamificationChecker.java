package com.example.editor.gamification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.event.EventDefinitionModel;
import com.example.editor.event.ReservedEventCatalog;

/** Comprueba contratos, límites anti-farming y evidencia verificable. */
@Component
public class GamificationChecker {
    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: definitions: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación; eventDefinitions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GamificationCheckReport check(GamificationDefinitionModel definitions, GamificationBindingModel bindings,
            EventDefinitionModel eventDefinitions) {
        List<GamificationDiagnostic> diagnostics = new ArrayList<>();
        if(definitions == null || bindings == null) return new GamificationCheckReport(definitions == null ? "" : definitions.id(),
                bindings == null ? "" : bindings.id(), List.of(error("GAMIFICATION_DOCUMENTS_REQUIRED", "", "$", "Definition and binding documents are required")));
        if(!definitions.metamodelId().equals(bindings.metamodelId())) diagnostics.add(error("METAMODEL_MISMATCH", bindings.id(), "$.metamodelId", "Both documents must belong to the same metamodel"));
        if(!definitions.id().equals(bindings.definitionModelId())) diagnostics.add(error("DEFINITION_REFERENCE_MISMATCH", bindings.id(), "$.definitionModelId", "Binding model must reference the supplied definition model"));
        Set<String> pointTypes = ids(definitions.pointTypes().stream().map(PointTypeDefinition::id).toList(), "DUPLICATE_POINT_TYPE", "$.pointTypes", diagnostics);
        Set<String> rewards = ids(definitions.rewards().stream().map(RewardDefinition::id).toList(), "DUPLICATE_REWARD", "$.rewards", diagnostics);
        Set<String> achievements = ids(definitions.achievements().stream().map(AchievementDefinition::id).toList(), "DUPLICATE_ACHIEVEMENT", "$.achievements", diagnostics);
        Set<String> challenges = ids(definitions.challenges().stream().map(ChallengeDefinition::id).toList(), "DUPLICATE_CHALLENGE", "$.challenges", diagnostics);
        Set<String> levels = new HashSet<>();
        for(int index = 0; index < definitions.levels().size(); index++) {
            LevelDefinition level = definitions.levels().get(index);
            String path = "$.levels[" + index + "]";
            if(!levels.add(level.id())) diagnostics.add(error("DUPLICATE_LEVEL", level.id(), path + ".id", "Level id is duplicated"));
            if(!pointTypes.contains(level.pointTypeId())) diagnostics.add(error("UNKNOWN_LEVEL_POINT_TYPE", level.id(), path + ".pointTypeId", "Level references an unknown point type"));
            checkReward(level.rewardId(), level.id(), path + ".rewardId", rewards, diagnostics);
        }
        for(int index = 0; index < definitions.achievements().size(); index++) {
            AchievementDefinition value = definitions.achievements().get(index);
            checkReward(value.rewardId(), value.id(), "$.achievements[" + index + "].rewardId", rewards, diagnostics);
        }
        for(int index = 0; index < definitions.challenges().size(); index++) {
            ChallengeDefinition value = definitions.challenges().get(index);
            checkReward(value.rewardId(), value.id(), "$.challenges[" + index + "].rewardId", rewards, diagnostics);
        }
        Set<String> eventTypes = new LinkedHashSet<>(ReservedEventCatalog.eventTypeIds());
        if(eventDefinitions != null) eventDefinitions.eventTypes().forEach(type -> eventTypes.add(type.id()));
        Set<String> ruleIds = new HashSet<>();
        for(int index = 0; index < bindings.rules().size(); index++) {
            GamificationRule rule = bindings.rules().get(index);
            String path = "$.rules[" + index + "]";
            if(!ruleIds.add(rule.id())) diagnostics.add(error("DUPLICATE_GAMIFICATION_RULE", rule.id(), path + ".id", "Rule id is duplicated"));
            if(!eventTypes.contains(rule.eventTypeId())) diagnostics.add(error("UNKNOWN_GAMIFICATION_EVENT", rule.id(), path + ".eventTypeId", "Rule references an unknown event type"));
            if(!rule.pointsTypeId().isBlank() && !pointTypes.contains(rule.pointsTypeId())) diagnostics.add(error("UNKNOWN_RULE_POINT_TYPE", rule.id(), path + ".pointsTypeId", "Rule references an unknown point type"));
            if(rule.points() != 0 && rule.pointsTypeId().isBlank()) diagnostics.add(error("POINT_TYPE_REQUIRED", rule.id(), path + ".pointsTypeId", "A pointsTypeId is required when points are changed"));
            if(rule.effectKind() == GamificationEffectKind.PENALTY && (!rule.achievementId().isBlank() || !rule.challengeId().isBlank() || !rule.rewardId().isBlank()))
                diagnostics.add(error("PENALTY_CANNOT_GRANT_REWARD", rule.id(), path, "Penalty rules can subtract points but cannot grant achievements, challenges or rewards"));
            if(!rule.achievementId().isBlank() && !achievements.contains(rule.achievementId())) diagnostics.add(error("UNKNOWN_RULE_ACHIEVEMENT", rule.id(), path + ".achievementId", "Rule references an unknown achievement"));
            if(!rule.challengeId().isBlank() && !challenges.contains(rule.challengeId())) diagnostics.add(error("UNKNOWN_RULE_CHALLENGE", rule.id(), path + ".challengeId", "Rule references an unknown challenge"));
            checkReward(rule.rewardId(), rule.id(), path + ".rewardId", rewards, diagnostics);
            boolean grantsValue = rule.points() != 0 || !rule.achievementId().isBlank() || !rule.challengeId().isBlank() || !rule.rewardId().isBlank();
            if(!grantsValue) diagnostics.add(warning("RULE_WITHOUT_EFFECT", rule.id(), path, "Rule does not change points, objectives or rewards"));
            if("core.quickfix.applied".equals(rule.eventTypeId()) && grantsValue) diagnostics.add(error("UNVERIFIED_QUICK_FIX_REWARD", rule.id(), path + ".eventTypeId", "Rewards cannot be granted for pressing a quick fix; bind to core.validation.violation-resolved instead"));
            if(rule.repeatPolicy() == GamificationRepeatPolicy.COOLDOWN && rule.cooldownSeconds() <= 0) diagnostics.add(error("COOLDOWN_REQUIRED", rule.id(), path + ".cooldownSeconds", "COOLDOWN policy requires a positive cooldown"));
            if(rule.repeatPolicy() == GamificationRepeatPolicy.DAILY_LIMIT && rule.maxAwardsPerDay() <= 0) diagnostics.add(error("DAILY_LIMIT_REQUIRED", rule.id(), path + ".maxAwardsPerDay", "DAILY_LIMIT requires maxAwardsPerDay"));
            if(rule.repeatPolicy() == GamificationRepeatPolicy.UNLIMITED && rule.maxAwardsPerActor() == 0 && rule.maxAwardsPerDay() == 0 && rule.cooldownSeconds() == 0)
                diagnostics.add(warning("UNBOUNDED_RULE", rule.id(), path, "Unlimited rule has no anti-farming limit"));
        }
        return new GamificationCheckReport(definitions.id(), bindings.id(), diagnostics);
    }
    /** Descripción: Ejecuta la operación «ids» correspondiente a la responsabilidad de GamificationChecker. Entradas: values: colección de elementos que debe procesarse; code: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Set<String> ids(List<String> values, String code, String path, List<GamificationDiagnostic> diagnostics) {
        Set<String> result = new LinkedHashSet<>();
        for(String value : values) if(!result.add(value)) diagnostics.add(error(code, value, path, "Identifier is duplicated: " + value));
        return result;
    }
    /** Descripción: Valida las condiciones necesarias para «checkReward» y rechaza entradas no válidas. Entradas: rewardId: identificador que delimita el recurso objetivo; elementId: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; rewards: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkReward(String rewardId, String elementId, String path, Set<String> rewards, List<GamificationDiagnostic> diagnostics) {
        if(!rewardId.isBlank() && !rewards.contains(rewardId)) diagnostics.add(error("UNKNOWN_REWARD", elementId, path, "Unknown reward: " + rewardId));
    }
    /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de GamificationChecker. Entradas: code: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GamificationDiagnostic error(String code, String id, String path, String message) { return new GamificationDiagnostic(GamificationDiagnosticSeverity.ERROR, code, id, path, message); }
    /** Descripción: Ejecuta la operación «warning» correspondiente a la responsabilidad de GamificationChecker. Entradas: code: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GamificationDiagnostic warning(String code, String id, String path, String message) { return new GamificationDiagnostic(GamificationDiagnosticSeverity.WARNING, code, id, path, message); }
}
