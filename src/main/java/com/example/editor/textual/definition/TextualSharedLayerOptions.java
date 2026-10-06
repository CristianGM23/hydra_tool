package com.example.editor.textual.definition;

import java.util.List;

/** Opciones de capas compartidas compatibles con el contrato semántico de una gramática compilada. */
public record TextualSharedLayerOptions(
        String semanticContractId,
        List<String> validationModelIds,
        List<String> actionModelIds,
        List<String> eventDefinitionIds,
        List<String> eventFlowIds,
        List<String> gamificationDefinitionIds) {
    public TextualSharedLayerOptions {
        semanticContractId = semanticContractId == null ? "" : semanticContractId.trim();
        validationModelIds = copy(validationModelIds);
        actionModelIds = copy(actionModelIds);
        eventDefinitionIds = copy(eventDefinitionIds);
        eventFlowIds = copy(eventFlowIds);
        gamificationDefinitionIds = copy(gamificationDefinitionIds);
    }
    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de TextualSharedLayerOptions. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> copy(List<String> values) {
        return values == null ? List.of() : values.stream().filter(java.util.Objects::nonNull).map(String::trim).filter(v -> !v.isBlank()).distinct().toList();
    }
}
