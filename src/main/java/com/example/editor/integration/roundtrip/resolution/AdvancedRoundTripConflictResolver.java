package com.example.editor.integration.roundtrip.resolution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Validates element-level decisions without overwriting manual code implicitly. */
@Component
public final class AdvancedRoundTripConflictResolver {

    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de AdvancedRoundTripConflictResolver. Entradas: conflicts: dato de entrada necesario para la operación; decisions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RoundTripResolutionResult resolve(List<ElementConflict> conflicts, List<ElementConflictDecision> decisions){
        Objects.requireNonNull(conflicts, "conflicts");
        Objects.requireNonNull(decisions, "decisions");
        Map<String, ElementConflict> byKey = uniqueConflicts(conflicts);
        Map<String, ElementConflictDecision> decisionByKey = uniqueDecisions(decisions);
        if(!byKey.keySet().containsAll(decisionByKey.keySet())){
            Set<String> unknown = new HashSet<>(decisionByKey.keySet());
            unknown.removeAll(byKey.keySet());
            throw new IllegalArgumentException("Decision references unknown conflicts: " + unknown);
        }

        List<ResolvedElementConflict> resolved = new ArrayList<>();
        List<ElementConflict> unresolved = new ArrayList<>();
        byKey.values().stream().sorted(Comparator.comparing(ElementConflict::key)).forEach(conflict -> {
            ElementConflictDecision decision = decisionByKey.get(conflict.key());
            if(decision == null || decision.choice() == ConflictResolutionChoice.SKIP){
                unresolved.add(conflict);
                return;
            }
            if(!conflict.supportedChoices().contains(decision.choice())){
                throw new IllegalArgumentException("Unsupported decision " + decision.choice() + " for " + conflict.key());
            }
            if(conflict.containsManualRegion() && decision.choice() == ConflictResolutionChoice.KEEP_HYDRA){
                throw new IllegalArgumentException("KEEP_HYDRA cannot overwrite a manual region for " + conflict.key());
            }
            resolved.add(new ResolvedElementConflict(conflict, decision));
        });
        return new RoundTripResolutionResult(resolved, unresolved, unresolved.isEmpty());
    }

    /** Descripción: Ejecuta la operación «uniqueConflicts» correspondiente a la responsabilidad de AdvancedRoundTripConflictResolver. Entradas: conflicts: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, ElementConflict> uniqueConflicts(List<ElementConflict> conflicts){
        Map<String, ElementConflict> result = new HashMap<>();
        for(ElementConflict conflict : conflicts){
            ElementConflict value = Objects.requireNonNull(conflict, "conflict");
            if(result.putIfAbsent(value.key(), value) != null){
                throw new IllegalArgumentException("Duplicate conflict: " + value.key());
            }
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «uniqueDecisions» correspondiente a la responsabilidad de AdvancedRoundTripConflictResolver. Entradas: decisions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, ElementConflictDecision> uniqueDecisions(List<ElementConflictDecision> decisions){
        Map<String, ElementConflictDecision> result = new HashMap<>();
        for(ElementConflictDecision decision : decisions){
            ElementConflictDecision value = Objects.requireNonNull(decision, "decision");
            if(result.putIfAbsent(value.conflictKey(), value) != null){
                throw new IllegalArgumentException("Duplicate decision: " + value.conflictKey());
            }
        }
        return result;
    }
}
