package com.example.editor.event.flow.runtime;
import java.time.Instant;
import com.example.editor.event.CanonicalEvent;
/** Espera durable que emite un evento cuando no aparece el evento observado antes del plazo. */
public record FlowAbsenceContinuation(String id, String flowId, String nodeId, String key, String groupByPath,
        String watchEventTypeId, String outputEventTypeId, Instant createdAt, Instant deadline, CanonicalEvent sourceEvent) {
    public FlowAbsenceContinuation {
        id = text(id); flowId = text(flowId); nodeId = text(nodeId); key = text(key); groupByPath = text(groupByPath);
        watchEventTypeId = text(watchEventTypeId); outputEventTypeId = text(outputEventTypeId);
        if(createdAt == null) createdAt = Instant.now();
        if(deadline == null) deadline = createdAt;
        if(sourceEvent == null) throw new IllegalArgumentException("sourceEvent must not be null");
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de FlowAbsenceContinuation. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
