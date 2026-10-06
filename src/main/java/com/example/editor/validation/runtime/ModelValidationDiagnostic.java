package com.example.editor.validation.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.validation.validation.ValidationSeverity;

/** Diagnóstico portable producido sobre un modelo concreto. */
public record ModelValidationDiagnostic(String id, ValidationDiagnosticStatus kind, String ruleId, ValidationSeverity severity,
        String message, ValidationDiagnosticLocation location, List<String> quickFixIds, Map<String, String> details) {
    public ModelValidationDiagnostic {
        id = id == null ? "" : id;
        if(kind == null) kind = ValidationDiagnosticStatus.VIOLATION;
        ruleId = ruleId == null ? "" : ruleId;
        if(severity == null) severity = ValidationSeverity.ERROR;
        message = message == null ? "" : message;
        if(location == null) location = new ValidationDiagnosticLocation("", "", "");
        quickFixIds = quickFixIds == null ? List.of() : List.copyOf(quickFixIds);
        details = details == null || details.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }
    /** Descripción: Ejecuta la operación «problemCode» correspondiente a la responsabilidad de ModelValidationDiagnostic. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String problemCode() { return details.getOrDefault("problemCode", ""); }
    /** Descripción: Ejecuta la operación «targetElementId» correspondiente a la responsabilidad de ModelValidationDiagnostic. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String targetElementId() { return location.modelElementId(); }
    /** Descripción: Ejecuta la operación «targetFeatureId» correspondiente a la responsabilidad de ModelValidationDiagnostic. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String targetFeatureId() { return location.featureId(); }
    /** Descripción: Ejecuta la operación «targetClassId» correspondiente a la responsabilidad de ModelValidationDiagnostic. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String targetClassId() { return details.getOrDefault("targetClassId", ""); }
    /** Descripción: Ejecuta la operación «status» correspondiente a la responsabilidad de ModelValidationDiagnostic. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public ValidationDiagnosticStatus status() { return kind; }
}
