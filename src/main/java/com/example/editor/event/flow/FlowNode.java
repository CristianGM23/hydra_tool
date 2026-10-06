package com.example.editor.event.flow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Nodo semántico de un flujo de eventos. La geometría se conserva en un documento de diagrama separado. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FlowNode(String id, String name, FlowNodeKind kind, String referenceId, String eventTypeId,
        String conditionExpressionId, Map<String, Object> configuration, RetryPolicy retryPolicy,
        TimeoutPolicy timeoutPolicy, boolean compensable, boolean enabled) {
    public FlowNode {
        id = StableIdentifierPolicy.requireElementId(id, "flowNode.id");
        name = name == null || name.isBlank() ? id : name.trim();
        if(kind == null) throw new IllegalArgumentException("flowNode.kind must not be null");
        referenceId = normalize(referenceId);
        eventTypeId = normalize(eventTypeId);
        conditionExpressionId = normalize(conditionExpressionId);
        configuration = configuration == null || configuration.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(configuration));
        if(retryPolicy == null) retryPolicy = RetryPolicy.none();
        if(timeoutPolicy == null) timeoutPolicy = TimeoutPolicy.none();
    }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de FlowNode. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
