package com.example.editor.gamification.runtime;

import java.util.List;
import com.example.editor.gamification.GamificationLedgerEntry;
import com.example.editor.gamification.GamificationRuntimeState;
import com.example.editor.gamification.feedback.UserFeedbackEnvelope;

public record GamificationProcessingResult(String metamodelId, String sourceEventId, GamificationRuntimeState state,
        List<GamificationLedgerEntry> granted, List<GamificationLedgerEntry> rejected,
        List<GamificationLedgerEntry> compensated, List<UserFeedbackEnvelope> feedback) {
    public GamificationProcessingResult {
        granted = granted == null ? List.of() : List.copyOf(granted);
        rejected = rejected == null ? List.of() : List.copyOf(rejected);
        compensated = compensated == null ? List.of() : List.copyOf(compensated);
        feedback = feedback == null ? List.of() : List.copyOf(feedback);
    }
    /** Descripción: Mantiene compatibilidad con consumidores que aún no solicitan la retroalimentación proyectada. Entradas: ámbito, evento, estado y entradas procesadas. Salida: resultado con lista de feedback vacía. */
    /** Descripción: Inicializa una instancia de GamificationProcessingResult con sus dependencias y datos de entrada. Entradas: metamodelId: identificador que delimita el recurso objetivo; sourceEventId: identificador que delimita el recurso objetivo; state: dato de entrada necesario para la operación; granted: dato de entrada necesario para la operación; rejected: dato de entrada necesario para la operación; compensated: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GamificationProcessingResult(String metamodelId, String sourceEventId, GamificationRuntimeState state,
            List<GamificationLedgerEntry> granted, List<GamificationLedgerEntry> rejected, List<GamificationLedgerEntry> compensated) {
        this(metamodelId, sourceEventId, state, granted, rejected, compensated, List.of());
    }
}
