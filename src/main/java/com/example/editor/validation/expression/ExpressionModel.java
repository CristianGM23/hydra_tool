package com.example.editor.validation.expression;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.example.editor.validation.contract.ValidationContractVersions;

/** Documento canónico y versionado de expresiones. */
public record ExpressionModel(String schemaVersion, String id, String metamodelId, List<ExpressionDefinition> expressions, Map<String, String> annotations) {
    public static final String CURRENT_SCHEMA_VERSION = ValidationContractVersions.EXPRESSION_MODEL;

    public ExpressionModel {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = CURRENT_SCHEMA_VERSION;
        if(!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported expression schemaVersion: " + schemaVersion);
        id = StableIdentifierPolicy.requireDocumentId(id, "id");
        metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "metamodelId");
        expressions = expressions == null ? List.of() : List.copyOf(expressions);
        if(annotations == null || annotations.isEmpty()) annotations = Map.of();
        else annotations = Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }

    /** Descripción: Obtiene la información correspondiente a «findExpression» dentro de ExpressionModel. Entradas: expressionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<ExpressionDefinition> findExpression(String expressionId) {
        return expressions.stream().filter(expression -> expression.id().equals(expressionId)).findFirst();
    }
}
