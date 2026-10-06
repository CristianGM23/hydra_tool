package com.example.editor.event;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
/** Regla que transforma un evento canónico o personalizado en otro evento. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EventBinding(String id, String name, String sourceEventTypeId, String targetEventTypeId, String conditionExpressionId,
        List<EventPayloadMapping> payloadMappings, boolean enabled, EventBindingErrorPolicy errorPolicy, Map<String, String> annotations) {
    public EventBinding {
        id = StableIdentifierPolicy.requireElementId(id, "binding.id");
        name = name == null || name.isBlank() ? id : name.trim();
        sourceEventTypeId = StableIdentifierPolicy.requireElementId(sourceEventTypeId, "sourceEventTypeId");
        targetEventTypeId = StableIdentifierPolicy.requireElementId(targetEventTypeId, "targetEventTypeId");
        conditionExpressionId = normalizeElementId(conditionExpressionId, "conditionExpressionId");
        payloadMappings = payloadMappings == null ? List.of() : List.copyOf(payloadMappings);
        if(errorPolicy == null) errorPolicy = EventBindingErrorPolicy.REPORT;
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
    /** Descripción: Ejecuta la operación «normalizeElementId» correspondiente a la responsabilidad de EventBinding. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalizeElementId(String value, String field) { if(value == null || value.isBlank()) return ""; return StableIdentifierPolicy.requireElementId(value, field); }
}
