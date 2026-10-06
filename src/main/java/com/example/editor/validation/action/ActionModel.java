package com.example.editor.validation.action;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.example.editor.validation.contract.ValidationContractVersions;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Documento canónico y versionado de acciones declarativas. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ActionModel(String schemaVersion, String id, String metamodelId, String expressionModelId, @JsonInclude(JsonInclude.Include.ALWAYS) List<ActionDefinition> actions, Map<String, String> annotations) {
    public static final String CURRENT_SCHEMA_VERSION = ValidationContractVersions.ACTION_MODEL;
    public ActionModel {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = CURRENT_SCHEMA_VERSION;
        if(!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported action schemaVersion: " + schemaVersion);
        id = StableIdentifierPolicy.requireDocumentId(id, "id");
        metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "metamodelId");
        expressionModelId = StableIdentifierPolicy.requireDocumentId(expressionModelId, "expressionModelId");
        actions = actions == null ? List.of() : List.copyOf(actions);
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
    /** Descripción: Obtiene la información correspondiente a «findAction» dentro de ActionModel. Entradas: actionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<ActionDefinition> findAction(String actionId) { return actions.stream().filter(action -> action.id().equals(actionId)).findFirst(); }
}
