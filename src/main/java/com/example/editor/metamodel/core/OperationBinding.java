package com.example.editor.metamodel.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Vincula una firma de operación con una implementación independiente del metamodelo. */
public record OperationBinding(String operationId, OperationImplementationKind implementationKind, String expressionId, String actionId, String externalReference, Map<String, String> annotations) {
    public OperationBinding {
        operationId = operationId == null ? "" : operationId;
        implementationKind = implementationKind == null ? OperationImplementationKind.UNIMPLEMENTED : implementationKind;
        expressionId = expressionId == null ? "" : expressionId;
        actionId = actionId == null ? "" : actionId;
        externalReference = externalReference == null ? "" : externalReference;
        if(annotations == null || annotations.isEmpty()) {
            annotations = Map.of();
        } else {
            annotations = Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
        }
    }
}
