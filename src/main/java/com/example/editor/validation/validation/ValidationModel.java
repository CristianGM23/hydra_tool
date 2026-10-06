package com.example.editor.validation.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.example.editor.validation.contract.ValidationContractVersions;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Documento canónico y versionado de reglas de validación. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ValidationModel(String schemaVersion, String id, String metamodelId, String expressionModelId, String actionModelId, @JsonInclude(JsonInclude.Include.ALWAYS) List<ValidationRuleSet> ruleSets, @JsonInclude(JsonInclude.Include.ALWAYS) List<ValidationRule> rules, @JsonInclude(JsonInclude.Include.ALWAYS) List<ImportedValidationSource> importedSources, Map<String, String> annotations) {
    public static final String CURRENT_SCHEMA_VERSION = ValidationContractVersions.VALIDATION_MODEL;
    public ValidationModel {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = CURRENT_SCHEMA_VERSION;
        if(!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported validation schemaVersion: " + schemaVersion);
        id = StableIdentifierPolicy.requireDocumentId(id, "id");
        metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "metamodelId");
        expressionModelId = StableIdentifierPolicy.requireDocumentId(expressionModelId, "expressionModelId");
        actionModelId = actionModelId == null ? "" : actionModelId;
        if(!actionModelId.isBlank()) StableIdentifierPolicy.requireDocumentId(actionModelId, "actionModelId");
        ruleSets = ruleSets == null ? List.of() : List.copyOf(ruleSets);
        rules = rules == null ? List.of() : List.copyOf(rules);
        importedSources = importedSources == null ? List.of() : List.copyOf(importedSources);
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
    /** Descripción: Obtiene la información correspondiente a «findRule» dentro de ValidationModel. Entradas: ruleId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<ValidationRule> findRule(String ruleId) { return rules.stream().filter(rule -> rule.id().equals(ruleId)).findFirst(); }
}
