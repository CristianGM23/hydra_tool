package com.example.editor.validation.orchestration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.validation.runtime.ValidationChangeSet;
import com.example.editor.validation.runtime.ValidationRunMode;

/** Solicitud de validación sobre una sesión de modelo abierta en HYDRA. */
public record LiveValidationRequest(ValidationRunMode mode, List<String> selectionElementIds, String classId,
        ValidationChangeSet changeSet, Map<String, Object> variables, ValidationGate gate) {
    public LiveValidationRequest {
        if(mode == null) mode = ValidationRunMode.FULL;
        selectionElementIds = selectionElementIds == null ? List.of() : selectionElementIds.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
        classId = classId == null ? "" : classId;
        if(changeSet == null) changeSet = ValidationChangeSet.empty();
        variables = variables == null || variables.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        if(gate == null) gate = ValidationGate.NONE;
    }
    /** Descripción: Ejecuta la operación «full» correspondiente a la responsabilidad de LiveValidationRequest. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static LiveValidationRequest full() { return new LiveValidationRequest(ValidationRunMode.FULL, List.of(), "", ValidationChangeSet.empty(), Map.of(), ValidationGate.NONE); }
}
