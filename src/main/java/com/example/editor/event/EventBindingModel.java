package com.example.editor.event;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
/** Documento portable de bindings entre eventos. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EventBindingModel(String schemaVersion, String id, String metamodelId, String eventDefinitionModelId, String expressionModelId,
        List<EventBinding> bindings, Map<String, String> annotations) {
    public EventBindingModel {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = EventContractVersions.EVENT_BINDING_MODEL;
        if(!EventContractVersions.EVENT_BINDING_MODEL.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported event binding schemaVersion: " + schemaVersion);
        id = StableIdentifierPolicy.requireDocumentId(id, "id");
        metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "metamodelId");
        eventDefinitionModelId = StableIdentifierPolicy.requireDocumentId(eventDefinitionModelId, "eventDefinitionModelId");
        expressionModelId = expressionModelId == null ? "" : expressionModelId.trim();
        if(!expressionModelId.isBlank()) StableIdentifierPolicy.requireDocumentId(expressionModelId, "expressionModelId");
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
    /** Descripción: Obtiene la información correspondiente a «findBinding» dentro de EventBindingModel. Entradas: bindingId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<EventBinding> findBinding(String bindingId) { return bindings.stream().filter(binding -> binding.id().equals(bindingId)).findFirst(); }
}
