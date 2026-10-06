package com.example.editor.event.flow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Definición de un flujo portable, independiente de cualquier motor CEP. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FlowDefinition(String id, String name, String description, boolean enabled,
        List<String> triggerEventTypeIds, List<FlowVariable> variables, List<FlowNode> nodes,
        List<FlowTransition> transitions, List<CompensationLink> compensations, Map<String, String> annotations) {
    public FlowDefinition {
        id = StableIdentifierPolicy.requireElementId(id, "flow.id");
        name = name == null || name.isBlank() ? id : name.trim();
        description = description == null ? "" : description.trim();
        triggerEventTypeIds = triggerEventTypeIds == null ? List.of() : List.copyOf(triggerEventTypeIds);
        variables = variables == null ? List.of() : List.copyOf(variables);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
        compensations = compensations == null ? List.of() : List.copyOf(compensations);
        annotations = annotations == null || annotations.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
    /** Descripción: Obtiene la información correspondiente a «findNode» dentro de FlowDefinition. Entradas: nodeId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<FlowNode> findNode(String nodeId) { return nodes.stream().filter(node -> node.id().equals(nodeId)).findFirst(); }
}
