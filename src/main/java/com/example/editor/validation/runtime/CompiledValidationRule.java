package com.example.editor.validation.runtime;

import java.util.List;
import com.example.editor.validation.validation.ValidationContext;
import com.example.editor.validation.validation.ValidationContextKind;
import com.example.editor.validation.validation.ValidationDependency;
import com.example.editor.validation.validation.ValidationExecutionMode;
import com.example.editor.validation.validation.ValidationSeverity;
import com.example.editor.validation.validation.ValidationTrigger;

/** Regla resuelta y preparada para ejecución. Conserva los campos publicados por el contrato 1.0 y datos operativos opcionales. */
public record CompiledValidationRule(String ruleId, ValidationContextKind contextKind, ValidationSeverity severity,
        List<ValidationTrigger> triggerSet, List<String> dependencyIds, boolean executable, List<String> compilationErrors,
        String name, String code, int order, ValidationContext context, String conditionExpressionId, String messageTemplate,
        List<ValidationDependency> dependencies, List<String> quickFixIds, ValidationExecutionMode executionMode, List<String> ruleSetIds) {
    public CompiledValidationRule {
        ruleId = ruleId == null ? "" : ruleId;
        if(contextKind == null) throw new IllegalArgumentException("contextKind must not be null");
        if(severity == null) throw new IllegalArgumentException("severity must not be null");
        triggerSet = triggerSet == null ? List.of() : List.copyOf(triggerSet);
        dependencyIds = dependencyIds == null ? List.of() : List.copyOf(dependencyIds);
        compilationErrors = compilationErrors == null ? List.of() : List.copyOf(compilationErrors);
        name = name == null ? "" : name;
        code = code == null ? "" : code;
        if(order < 0) throw new IllegalArgumentException("order must not be negative");
        if(context == null) context = new ValidationContext(contextKind, "", "");
        conditionExpressionId = conditionExpressionId == null ? "" : conditionExpressionId;
        messageTemplate = messageTemplate == null ? "" : messageTemplate;
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        quickFixIds = quickFixIds == null ? List.of() : List.copyOf(quickFixIds);
        if(executionMode == null) executionMode = ValidationExecutionMode.EXTERNAL;
        ruleSetIds = ruleSetIds == null ? List.of() : List.copyOf(ruleSetIds);
    }
    /** Alias interno para mantener legible el motor. */
    /** Descripción: Ejecuta la operación «triggers» correspondiente a la responsabilidad de CompiledValidationRule. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<ValidationTrigger> triggers() { return triggerSet; }
}
