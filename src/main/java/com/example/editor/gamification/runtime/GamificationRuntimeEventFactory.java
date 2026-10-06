package com.example.editor.gamification.runtime;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.EventContext;
import com.example.editor.event.EventContextKind;
import com.example.editor.event.EventSource;
import com.example.editor.event.EventSubject;
import com.example.editor.event.EventSubjectKind;
import com.example.editor.gamification.GamificationLedgerEntry;

final class GamificationRuntimeEventFactory {
    /** Descripción: Inicializa una instancia de GamificationRuntimeEventFactory con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private GamificationRuntimeEventFactory() { }
    /** Descripción: Crea o prepara el resultado de «create» dentro de GamificationRuntimeEventFactory. Entradas: eventTypeId: identificador que delimita el recurso objetivo; source: dato de entrada necesario para la operación; entry: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static CanonicalEvent create(String eventTypeId, CanonicalEvent source, GamificationLedgerEntry entry, int depth) {
        EventContext original = source.context();
        EventContext context = new EventContext(EventContextKind.GAMIFICATION, original.workspaceId(), original.projectId(),
                entry.metamodelId(), original.modelId(), original.editorId(), original.sessionId());
        EventSubject subject = new EventSubject(EventSubjectKind.GAMIFICATION_RULE,
                source.subject() == null ? entry.metamodelId() : source.subject().documentId(), entry.ruleId(), "", "", "");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ledgerEntryId", entry.id());
        payload.put("ruleId", entry.ruleId());
        payload.put("actorId", entry.actorId());
        payload.put("pointsTypeId", entry.pointsTypeId());
        payload.put("pointsDelta", entry.pointsDelta());
        payload.put("achievementId", entry.achievementId());
        payload.put("challengeId", entry.challengeId());
        payload.put("rewardId", entry.rewardId());
        payload.put("sourceEventId", entry.sourceEventId());
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("hydra.gamification.depth", String.valueOf(depth));
        return new CanonicalEvent("1.0", "event:" + UUID.randomUUID(), eventTypeId, "1.0", Instant.now(),
                new EventSource("HYDRA", "gamification-runtime", "", "1.0"), context, source.actor(), subject,
                source.transactionId(), source.correlationId().isBlank() ? source.id() : source.correlationId(), source.id(), payload, metadata);
    }
}
