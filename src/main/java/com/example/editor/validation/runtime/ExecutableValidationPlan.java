package com.example.editor.validation.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.validation.contract.ValidationContractVersions;

/** Artefacto derivado e inmutable consumido por ValidationEngine. */
public record ExecutableValidationPlan(String schemaVersion, String metamodelId, String metamodelEtag, String validationModelId,
        String validationModelEtag, List<CompiledValidationRule> compiledRules, String id, String expressionModelId,
        String actionModelId, String sourceFingerprint, ValidationPlanSourceVersions sourceVersions,
        ValidationDependencyIndex dependencyIndex, List<ValidationCompilationDiagnostic> diagnostics, Map<String, String> annotations) {
    public static final String CURRENT_SCHEMA_VERSION = ValidationContractVersions.EXECUTABLE_VALIDATION_PLAN;
    public ExecutableValidationPlan {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = CURRENT_SCHEMA_VERSION;
        if(!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported executable validation plan schemaVersion: " + schemaVersion);
        metamodelId = metamodelId == null ? "" : metamodelId;
        metamodelEtag = metamodelEtag == null ? "" : metamodelEtag;
        validationModelId = validationModelId == null ? "" : validationModelId;
        validationModelEtag = validationModelEtag == null ? "" : validationModelEtag;
        compiledRules = compiledRules == null ? List.of() : List.copyOf(compiledRules);
        id = id == null || id.isBlank() ? validationModelId + "-plan" : id;
        expressionModelId = expressionModelId == null ? "" : expressionModelId;
        actionModelId = actionModelId == null ? "" : actionModelId;
        sourceFingerprint = sourceFingerprint == null ? "" : sourceFingerprint;
        if(sourceVersions == null) sourceVersions = new ValidationPlanSourceVersions(metamodelEtag, "", "", validationModelEtag, "");
        if(dependencyIndex == null) dependencyIndex = new ValidationDependencyIndex(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of());
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
    /** Descripción: Ejecuta la operación «rules» correspondiente a la responsabilidad de ExecutableValidationPlan. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<CompiledValidationRule> rules() { return compiledRules; }
    /** Descripción: Evalúa la condición «hasErrors» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasErrors() { return diagnostics.stream().anyMatch(item -> item.severity() == ValidationCompilationSeverity.ERROR); }
    /** Descripción: Evalúa la condición «isCurrent» sin modificar el estado externo. Entradas: versions: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isCurrent(ValidationPlanSourceVersions versions) { return sourceVersions.equals(versions); }
}
