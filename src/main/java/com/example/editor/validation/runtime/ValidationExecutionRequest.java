package com.example.editor.validation.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Solicitud neutral de ejecución, reutilizable desde web, EMF, Xtext o Langium. */
public record ValidationExecutionRequest(ValidationRunMode mode, CanonicalModelDocument model, List<String> selectionElementIds,
        String classId, ValidationChangeSet changeSet, Map<String, Object> variables) {
    public ValidationExecutionRequest {
        if(mode == null) mode = ValidationRunMode.FULL;
        if(model == null) throw new IllegalArgumentException("model must not be null");
        selectionElementIds = selectionElementIds == null ? List.of() : selectionElementIds.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
        classId = classId == null ? "" : classId;
        if(changeSet == null) changeSet = ValidationChangeSet.empty();
        variables = variables == null || variables.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }
}
