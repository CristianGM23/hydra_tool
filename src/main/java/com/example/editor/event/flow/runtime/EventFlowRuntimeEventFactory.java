package com.example.editor.event.flow.runtime;

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

/** Crea eventos derivados conservando correlación y causalidad. */
final class EventFlowRuntimeEventFactory {
    /** Descripción: Inicializa una instancia de EventFlowRuntimeEventFactory con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private EventFlowRuntimeEventFactory() { }
    /** Descripción: Ejecuta la operación «derived» correspondiente a la responsabilidad de EventFlowRuntimeEventFactory. Entradas: metamodelId: identificador que delimita el recurso objetivo; flowId: identificador que delimita el recurso objetivo; nodeId: identificador que delimita el recurso objetivo; eventTypeId: identificador que delimita el recurso objetivo; source: dato de entrada necesario para la operación; payload: valor de entrada que debe procesarse; depth: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static CanonicalEvent derived(String metamodelId, String flowId, String nodeId, String eventTypeId,
            CanonicalEvent source, Map<String, Object> payload, int depth) {
        Map<String, String> metadata = new LinkedHashMap<>(source.metadata());
        metadata.put("hydra.flow.id", flowId);
        metadata.put("hydra.flow.nodeId", nodeId);
        metadata.put("hydra.flow.depth", String.valueOf(depth));
        EventContext original = source.context();
        EventContext context = new EventContext(EventContextKind.EVENT_FLOW, original.workspaceId(), original.projectId(),
                metamodelId, original.modelId(), original.editorId(), original.sessionId());
        EventSubject subject = new EventSubject(EventSubjectKind.EVENT_FLOW,
                source.subject() == null ? metamodelId : source.subject().documentId(), flowId, "", "", "");
        return new CanonicalEvent("1.0", "event:" + UUID.randomUUID(), eventTypeId, "1.0", Instant.now(),
                new EventSource("HYDRA", "event-flow-runtime", "", "1.0"), context, source.actor(), subject,
                source.transactionId(), source.correlationId().isBlank() ? source.id() : source.correlationId(), source.id(),
                payload, metadata);
    }
}
