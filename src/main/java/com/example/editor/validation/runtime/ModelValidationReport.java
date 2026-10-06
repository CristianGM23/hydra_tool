package com.example.editor.validation.runtime;

import java.time.Instant;
import java.util.List;
import com.example.editor.validation.contract.ValidationContractVersions;
import com.example.editor.validation.validation.ValidationSeverity;

/** Informe portable de una ejecución concreta del motor de validación. */
public record ModelValidationReport(String schemaVersion, String id, String modelId, String metamodelId, String validationModelId,
        Instant generatedAt, ValidationReportScope scope, List<ModelValidationDiagnostic> diagnostics, ValidationExecutionMetrics metrics,
        String planId, ValidationRunMode mode, Instant startedAt, Instant completedAt, List<String> executedRuleIds, List<String> skippedRuleIds) {
    public static final String CURRENT_SCHEMA_VERSION = ValidationContractVersions.VALIDATION_REPORT;
    public ModelValidationReport {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = CURRENT_SCHEMA_VERSION;
        if(!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported validation report schemaVersion: " + schemaVersion);
        id = id == null ? "" : id;
        modelId = modelId == null ? "" : modelId;
        metamodelId = metamodelId == null ? "" : metamodelId;
        validationModelId = validationModelId == null ? "" : validationModelId;
        if(generatedAt == null) generatedAt = Instant.EPOCH;
        if(scope == null) scope = ValidationReportScope.model();
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        if(metrics == null) metrics = new ValidationExecutionMetrics(0, 0, 0);
        planId = planId == null ? "" : planId;
        if(mode == null) mode = ValidationRunMode.FULL;
        if(startedAt == null) startedAt = generatedAt;
        if(completedAt == null) completedAt = generatedAt;
        executedRuleIds = executedRuleIds == null ? List.of() : List.copyOf(executedRuleIds);
        skippedRuleIds = skippedRuleIds == null ? List.of() : List.copyOf(skippedRuleIds);
    }
    /** Descripción: Ejecuta la operación «durationMillis» correspondiente a la responsabilidad de ModelValidationReport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long durationMillis() { return metrics.durationMillis(); }
    /** Descripción: Ejecuta la operación «errorCount» correspondiente a la responsabilidad de ModelValidationReport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long errorCount() { return diagnostics.stream().filter(item -> item.severity() == ValidationSeverity.ERROR).count(); }
    /** Descripción: Ejecuta la operación «warningCount» correspondiente a la responsabilidad de ModelValidationReport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long warningCount() { return diagnostics.stream().filter(item -> item.severity() == ValidationSeverity.WARNING).count(); }
    /** Descripción: Ejecuta la operación «infoCount» correspondiente a la responsabilidad de ModelValidationReport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long infoCount() { return diagnostics.stream().filter(item -> item.severity() == ValidationSeverity.INFO).count(); }
    /** Descripción: Ejecuta la operación «valid» correspondiente a la responsabilidad de ModelValidationReport. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean valid() { return diagnostics.stream().noneMatch(item -> item.kind() == ValidationDiagnosticStatus.VIOLATION && item.severity() == ValidationSeverity.ERROR); }
}
