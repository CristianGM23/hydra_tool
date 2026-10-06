package com.example.editor.event.flow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Documento canónico que agrupa los flujos asociados a un metamodelo. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EventFlowModel(String schemaVersion, String id, String metamodelId, String eventDefinitionModelId,
        String expressionModelId, List<FlowDefinition> flows, Map<String, String> annotations) {
    public EventFlowModel {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = EventFlowContractVersions.EVENT_FLOW_MODEL;
        if(!EventFlowContractVersions.EVENT_FLOW_MODEL.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported EventFlowModel schemaVersion: " + schemaVersion);
        id = StableIdentifierPolicy.requireDocumentId(id, "eventFlowModel.id");
        metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "eventFlowModel.metamodelId");
        eventDefinitionModelId = eventDefinitionModelId == null ? "" : eventDefinitionModelId.trim();
        expressionModelId = expressionModelId == null ? "" : expressionModelId.trim();
        flows = flows == null ? List.of() : List.copyOf(flows);
        annotations = annotations == null || annotations.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
    /** Descripción: Obtiene la información correspondiente a «findFlow» dentro de EventFlowModel. Entradas: flowId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<FlowDefinition> findFlow(String flowId) { return flows.stream().filter(flow -> flow.id().equals(flowId)).findFirst(); }
}
