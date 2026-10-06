package com.example.editor.event;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Hecho inmutable y neutral publicado por cualquier módulo del editor. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CanonicalEvent(String schemaVersion, String id, String eventTypeId, String eventTypeVersion, Instant occurredAt,
        EventSource source, EventContext context, EventActor actor, EventSubject subject, String transactionId,
        String correlationId, String causationId, Map<String, Object> payload, Map<String, String> metadata) {
    public CanonicalEvent {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = EventContractVersions.CANONICAL_EVENT;
        if(!EventContractVersions.CANONICAL_EVENT.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported canonical event schemaVersion: " + schemaVersion);
        id = StableIdentifierPolicy.requireElementId(id, "event.id");
        eventTypeId = StableIdentifierPolicy.requireElementId(eventTypeId, "eventTypeId");
        eventTypeVersion = eventTypeVersion == null || eventTypeVersion.isBlank() ? "1.0" : eventTypeVersion.trim();
        if(occurredAt == null) occurredAt = Instant.now();
        if(source == null) throw new IllegalArgumentException("source must not be null");
        if(context == null) context = new EventContext(EventContextKind.SYSTEM, "", "", "", "", "", "");
        transactionId = normalizeElementId(transactionId, "transactionId");
        correlationId = normalizeElementId(correlationId, "correlationId");
        causationId = normalizeElementId(causationId, "causationId");
        payload = immutableObjectMap(payload);
        metadata = immutableStringMap(metadata);
    }
    /** Descripción: Ejecuta la operación «normalizeElementId» correspondiente a la responsabilidad de CanonicalEvent. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalizeElementId(String value, String field) {
        if(value == null || value.isBlank()) return "";
        return StableIdentifierPolicy.requireElementId(value, field);
    }
    /** Descripción: Ejecuta la operación «immutableObjectMap» correspondiente a la responsabilidad de CanonicalEvent. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, Object> immutableObjectMap(Map<String, Object> values) {
        return values == null || values.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
    /** Descripción: Ejecuta la operación «immutableStringMap» correspondiente a la responsabilidad de CanonicalEvent. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> immutableStringMap(Map<String, String> values) {
        return values == null || values.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
