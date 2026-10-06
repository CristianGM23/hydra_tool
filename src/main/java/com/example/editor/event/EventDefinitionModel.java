package com.example.editor.event;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
/** Documento portable con tipos de eventos personalizados. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EventDefinitionModel(String schemaVersion, String id, String metamodelId, String namespace, List<EventTypeDefinition> eventTypes, Map<String, String> annotations) {
    public EventDefinitionModel {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = EventContractVersions.EVENT_DEFINITION_MODEL;
        if(!EventContractVersions.EVENT_DEFINITION_MODEL.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported event definition schemaVersion: " + schemaVersion);
        id = StableIdentifierPolicy.requireDocumentId(id, "id");
        metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "metamodelId");
        if(namespace == null || namespace.isBlank() || !namespace.matches("[A-Za-z][A-Za-z0-9_.-]*")) throw new IllegalArgumentException("namespace is invalid");
        eventTypes = eventTypes == null ? List.of() : List.copyOf(eventTypes);
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
    /** Descripción: Obtiene la información correspondiente a «findType» dentro de EventDefinitionModel. Entradas: eventTypeId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<EventTypeDefinition> findType(String eventTypeId) { return eventTypes.stream().filter(type -> type.id().equals(eventTypeId)).findFirst(); }
}
