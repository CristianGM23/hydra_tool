package com.example.editor.validation.action;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("EXECUTE_ACTION")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExecuteActionOperation(String operationId, String actionId, Map<String, ActionValueSource> arguments) implements ActionOperation {
    public ExecuteActionOperation {
        operationId = StableIdentifierPolicy.requireElementId(operationId, "operationId");
        actionId = StableIdentifierPolicy.requireElementId(actionId, "actionId");
        arguments = arguments == null || arguments.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }

    /** Descripción: Inicializa una instancia de ExecuteActionOperation con sus dependencias y datos de entrada. Entradas: operationId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo. Salida: deja la instancia inicializada y lista para su uso. */ public ExecuteActionOperation(String operationId, String actionId) {
        this(operationId, actionId, Map.of());
    }
}
