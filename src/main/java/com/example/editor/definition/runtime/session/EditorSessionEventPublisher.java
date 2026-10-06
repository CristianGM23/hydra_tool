package com.example.editor.definition.runtime.session;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.EventContext;
import com.example.editor.event.EventContextKind;
import com.example.editor.event.EventSource;
import com.example.editor.event.EventSubject;
import com.example.editor.event.EventSubjectKind;
import com.example.editor.event.bus.CanonicalEventBus;

/** Publica hechos del editor solo después de confirmar la persistencia de la sesión. */
@Component
public class EditorSessionEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(EditorSessionEventPublisher.class);
    private final CanonicalEventBus bus;
    /** Descripción: Inicializa una instancia de EditorSessionEventPublisher con sus dependencias y datos de entrada. Entradas: bus: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorSessionEventPublisher(CanonicalEventBus bus) { this.bus = bus; }

    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de EditorSessionEventPublisher. Entradas: event: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void publish(PendingEditorEvent event) {
        if(event==null) return;
        publish(event.eventTypeId(),event.context(),event.elementId(),event.typeId(),event.payload());
    }

    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de EditorSessionEventPublisher. Entradas: eventTypeId: identificador que delimita el recurso objetivo; session: dato de entrada necesario para la operación; elementId: identificador que delimita el recurso objetivo; typeId: identificador que delimita el recurso objetivo; payload: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void publish(String eventTypeId, EditorModelSession session, String elementId, String typeId,
            Map<String, Object> payload) {
        publish(eventTypeId,EditorSessionEventContext.from(session),elementId,typeId,payload);
    }

    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de EditorSessionEventPublisher. Entradas: eventTypeId: identificador que delimita el recurso objetivo; context: dato de entrada necesario para la operación; elementId: identificador que delimita el recurso objetivo; typeId: identificador que delimita el recurso objetivo; payload: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void publish(String eventTypeId, EditorSessionEventContext context, String elementId, String typeId,
            Map<String, Object> payload) {
        try {
            String transactionId = "transaction:" + UUID.randomUUID();
            bus.publish(new CanonicalEvent("1.0", "event:" + UUID.randomUUID(), eventTypeId, "1.0", Instant.now(),
                    new EventSource("HYDRA", "editor-runtime", context.sessionId(), "1.0"),
                    new EventContext(EventContextKind.MODEL_EDITING, "", "", context.metamodelId(), context.modelId(), context.editorId(), context.sessionId()),
                    null, new EventSubject(elementId == null || elementId.isBlank() ? EventSubjectKind.MODEL : EventSubjectKind.MODEL_ELEMENT,
                            context.modelId(), elementId == null ? "" : elementId, typeId == null ? "" : typeId, "", ""),
                    transactionId, "correlation:" + context.sessionId(), "", payload == null ? Map.of() : payload,
                    Map.of("sessionRevision", Long.toString(context.revision()))));
        } catch(RuntimeException exception) {
            LOGGER.warn("Editor runtime event could not be delivered after commit: {}", eventTypeId, exception);
        }
    }
}
