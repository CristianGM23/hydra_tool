package com.example.editor.validation.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.semantic.expression.ExpressionCheckResult;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionDependency;
import com.example.editor.semantic.expression.ExpressionDependencyKind;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.semantic.expression.ExpressionValueKind;
import com.example.editor.validation.support.CanonicalMetamodelIndex;

/** Comprueba coherencia, referencias y dependencias de ValidationModel 1.0. */
@Service
public class ValidationModelChecker {
    private final ExpressionTypeChecker expressionTypeChecker;

    /** Descripción: Inicializa una instancia de ValidationModelChecker con sus dependencias y datos de entrada. Entradas: expressionTypeChecker: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ValidationModelChecker(ExpressionTypeChecker expressionTypeChecker) {
        this.expressionTypeChecker = expressionTypeChecker;
    }

    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: model: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ValidationModelCheckReport check(ValidationModel model, MetaModel metamodel, ExpressionModel expressions, ActionModel actions) {
        List<ValidationModelDiagnostic> global = new ArrayList<>();
        List<ValidationRuleCheckResult> results = new ArrayList<>();
        if(model == null) return new ValidationModelCheckReport("", List.of(error("VALIDATION_MODEL_REQUIRED", "", "$", "Validation model must not be null")), List.of());
        if(metamodel == null) return new ValidationModelCheckReport(model.id(), List.of(error("METAMODEL_REQUIRED", "", "$", "Metamodel must not be null")), List.of());
        if(expressions == null) global.add(error("EXPRESSION_MODEL_REQUIRED", "", "$.expressionModelId", "Expression model must exist before validations can be checked"));
        if(!model.metamodelId().equals(metamodel.id())) global.add(error("METAMODEL_ID_MISMATCH", "", "$.metamodelId", "Validation model metamodelId does not match the canonical metamodel"));
        if(expressions != null && !model.expressionModelId().equals(expressions.id())) global.add(error("EXPRESSION_MODEL_ID_MISMATCH", "", "$.expressionModelId", "Validation model expressionModelId does not match the expression document"));
        if(!model.actionModelId().isBlank() && actions == null) global.add(error("ACTION_MODEL_REQUIRED", "", "$.actionModelId", "Configured action model was not found"));
        if(actions != null && !model.actionModelId().isBlank() && !model.actionModelId().equals(actions.id())) global.add(error("ACTION_MODEL_ID_MISMATCH", "", "$.actionModelId", "Validation model actionModelId does not match the action document"));

        Set<String> ruleIds = new HashSet<>();
        Set<String> ruleCodes = new HashSet<>();
        Map<String, ValidationRule> ruleIndex = new LinkedHashMap<>();
        for(int index = 0; index < model.rules().size(); index++) {
            ValidationRule rule = model.rules().get(index);
            if(!ruleIds.add(rule.id())) global.add(error("DUPLICATE_RULE_ID", rule.id(), "$.rules[" + index + "].id", "Rule id is duplicated: " + rule.id()));
            if(!ruleCodes.add(rule.code())) global.add(error("DUPLICATE_RULE_CODE", rule.id(), "$.rules[" + index + "].code", "Rule code is duplicated: " + rule.code()));
            ruleIndex.putIfAbsent(rule.id(), rule);
        }
        checkRuleSets(model, ruleIndex, global);
        checkImportedSources(model, global);

        CanonicalMetamodelIndex metamodelIndex = new CanonicalMetamodelIndex(metamodel);
        for(int index = 0; index < model.rules().size(); index++) {
            results.add(checkRule(model.rules().get(index), index, metamodel, metamodelIndex, expressions, actions));
        }
        return new ValidationModelCheckReport(model.id(), global, results);
    }

    /** Descripción: Valida las condiciones necesarias para «checkRuleSets» y rechaza entradas no válidas. Entradas: model: dato de entrada necesario para la operación; ruleIndex: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkRuleSets(ValidationModel model, Map<String, ValidationRule> ruleIndex, List<ValidationModelDiagnostic> diagnostics) {
        Set<String> ids = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for(int index = 0; index < model.ruleSets().size(); index++) {
            ValidationRuleSet ruleSet = model.ruleSets().get(index);
            String path = "$.ruleSets[" + index + "]";
            if(!ids.add(ruleSet.id())) diagnostics.add(error("DUPLICATE_RULE_SET_ID", "", path + ".id", "Rule set id is duplicated: " + ruleSet.id()));
            if(!orders.add(ruleSet.order())) diagnostics.add(warning("DUPLICATE_RULE_SET_ORDER", "", path + ".order", "Several rule sets use order " + ruleSet.order()));
            Set<String> contained = new HashSet<>();
            for(int ruleIndexValue = 0; ruleIndexValue < ruleSet.ruleIds().size(); ruleIndexValue++) {
                String ruleId = ruleSet.ruleIds().get(ruleIndexValue);
                if(!contained.add(ruleId)) diagnostics.add(error("DUPLICATE_RULE_IN_SET", ruleId, path + ".ruleIds[" + ruleIndexValue + "]", "Rule is duplicated inside the rule set: " + ruleId));
                if(!ruleIndex.containsKey(ruleId)) diagnostics.add(error("UNKNOWN_RULE_IN_SET", ruleId, path + ".ruleIds[" + ruleIndexValue + "]", "Rule set references an unknown rule: " + ruleId));
            }
        }
    }

    /** Descripción: Valida las condiciones necesarias para «checkImportedSources» y rechaza entradas no válidas. Entradas: model: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkImportedSources(ValidationModel model, List<ValidationModelDiagnostic> diagnostics) {
        Set<String> ids = new HashSet<>();
        Map<String, String> hashes = new HashMap<>();
        for(int index = 0; index < model.importedSources().size(); index++) {
            ImportedValidationSource source = model.importedSources().get(index);
            String path = "$.importedSources[" + index + "]";
            if(!ids.add(source.id())) diagnostics.add(error("DUPLICATE_IMPORTED_SOURCE_ID", "", path + ".id", "Imported source id is duplicated: " + source.id()));
            String previous = hashes.putIfAbsent(source.contentHash(), source.id());
            if(previous != null) diagnostics.add(warning("DUPLICATE_IMPORTED_SOURCE_CONTENT", "", path + ".contentHash", "Imported source has the same content as " + previous));
        }
    }

    /** Descripción: Valida las condiciones necesarias para «checkRule» y rechaza entradas no válidas. Entradas: rule: dato de entrada necesario para la operación; indexValue: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationRuleCheckResult checkRule(ValidationRule rule, int indexValue, MetaModel metamodel, CanonicalMetamodelIndex index,
            ExpressionModel expressions, ActionModel actions) {
        List<ValidationModelDiagnostic> diagnostics = new ArrayList<>();
        String base = "$.rules[" + indexValue + "]";
        checkContext(rule, base, index, diagnostics);

        ExpressionDefinition expression = expressions == null ? null : expressions.findExpression(rule.conditionExpressionId()).orElse(null);
        List<ValidationDependency> inferredDependencies = List.of();
        if(expression == null) {
            ValidationModelDiagnostic diagnostic = rule.executionMode() == ValidationExecutionMode.EXTERNAL
                    ? warning("EXTERNAL_RULE_WITHOUT_CANONICAL_EXPRESSION", rule.id(), base + ".conditionExpressionId", "External rule has no canonical executable expression")
                    : error("UNKNOWN_CONDITION_EXPRESSION", rule.id(), base + ".conditionExpressionId", "Condition expression does not exist: " + rule.conditionExpressionId());
            diagnostics.add(diagnostic);
        } else {
            ExpressionCheckResult result = expressionTypeChecker.check(expression, metamodel);
            inferredDependencies = result.dependencies().stream().map(this::toValidationDependency).distinct().toList();
            if(result.hasErrors()) diagnostics.add(error("INVALID_CONDITION_EXPRESSION", rule.id(), base + ".conditionExpressionId", "Condition expression contains errors"));
            if(result.inferredType().kind() != ExpressionValueKind.BOOLEAN && result.inferredType().kind() != ExpressionValueKind.ANY) diagnostics.add(error("CONDITION_NOT_BOOLEAN", rule.id(), base + ".conditionExpressionId", "Validation condition must return BOOLEAN"));
            if(!expression.contextClassId().isBlank() && !rule.context().classId().isBlank() && !expression.contextClassId().equals(rule.context().classId())) diagnostics.add(error("CONTEXT_EXPRESSION_MISMATCH", rule.id(), base + ".context.classId", "Rule context class does not match the expression context class"));
        }

        checkDependencies(rule, base, index, inferredDependencies, diagnostics);
        checkQuickFixes(rule, base, actions, diagnostics);
        checkExecution(rule, base, diagnostics);
        checkProvenance(rule, base, diagnostics);
        return new ValidationRuleCheckResult(rule.id(), diagnostics, inferredDependencies);
    }

    /** Descripción: Valida las condiciones necesarias para «checkContext» y rechaza entradas no válidas. Entradas: rule: dato de entrada necesario para la operación; base: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkContext(ValidationRule rule, String base, CanonicalMetamodelIndex index, List<ValidationModelDiagnostic> diagnostics) {
        ValidationContext context = rule.context();
        switch(context.kind()) {
            case CLASS, OBJECT -> {
                if(context.classId().isBlank()) diagnostics.add(error("CONTEXT_CLASS_REQUIRED", rule.id(), base + ".context.classId", "Class context requires classId"));
                else if(!index.hasClass(context.classId())) diagnostics.add(error("UNKNOWN_CONTEXT_CLASS", rule.id(), base + ".context.classId", "Context class does not exist: " + context.classId()));
                if(!context.featureId().isBlank()) diagnostics.add(warning("UNUSED_CONTEXT_FEATURE", rule.id(), base + ".context.featureId", "Class context ignores featureId"));
            }
            case ATTRIBUTE -> {
                if(context.classId().isBlank()) diagnostics.add(error("CONTEXT_CLASS_REQUIRED", rule.id(), base + ".context.classId", "Attribute context requires classId"));
                if(context.featureId().isBlank()) diagnostics.add(error("CONTEXT_FEATURE_REQUIRED", rule.id(), base + ".context.featureId", "Attribute context requires featureId"));
                else if(!index.hasAttribute(context.featureId())) diagnostics.add(error("UNKNOWN_CONTEXT_ATTRIBUTE", rule.id(), base + ".context.featureId", "Context attribute does not exist: " + context.featureId()));
                else if(!context.classId().isBlank() && !index.featureBelongsToClass(context.classId(), context.featureId())) diagnostics.add(error("CONTEXT_FEATURE_OWNER_MISMATCH", rule.id(), base + ".context", "Attribute does not belong to the context class"));
            }
            case RELATION -> {
                if(context.featureId().isBlank()) diagnostics.add(error("CONTEXT_FEATURE_REQUIRED", rule.id(), base + ".context.featureId", "Relation context requires featureId"));
                else if(!index.hasRelation(context.featureId())) diagnostics.add(error("UNKNOWN_CONTEXT_RELATION", rule.id(), base + ".context.featureId", "Context relation does not exist: " + context.featureId()));
                else if(!context.classId().isBlank() && !index.featureBelongsToClass(context.classId(), context.featureId())) diagnostics.add(error("CONTEXT_FEATURE_OWNER_MISMATCH", rule.id(), base + ".context", "Relation does not start at the context class"));
            }
            case MODEL, PROJECT, REPRESENTATION, SELECTION -> {
                if(!context.classId().isBlank() || !context.featureId().isBlank()) diagnostics.add(warning("GLOBAL_CONTEXT_HAS_STRUCTURAL_IDS", rule.id(), base + ".context", "Global context normally does not use classId or featureId"));
            }
        }
    }

    /** Descripción: Valida las condiciones necesarias para «checkDependencies» y rechaza entradas no válidas. Entradas: rule: dato de entrada necesario para la operación; base: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; inferred: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkDependencies(ValidationRule rule, String base, CanonicalMetamodelIndex index, List<ValidationDependency> inferred,
            List<ValidationModelDiagnostic> diagnostics) {
        Set<ValidationDependency> declared = new LinkedHashSet<>(rule.dependencies());
        if(declared.size() != rule.dependencies().size()) diagnostics.add(error("DUPLICATE_DEPENDENCY", rule.id(), base + ".dependencies", "Rule contains duplicated dependencies"));
        for(ValidationDependency dependency : declared) {
            boolean exists = switch(dependency.kind()) {
                case CLASS -> index.hasClass(dependency.elementId());
                case ATTRIBUTE -> index.hasAttribute(dependency.elementId());
                case RELATION -> index.hasRelation(dependency.elementId());
                case OPERATION -> index.hasOperation(dependency.elementId());
                case MODEL -> dependency.elementId().equals("model") || dependency.elementId().equals(rule.context().classId()) || !dependency.elementId().isBlank();
            };
            if(!exists) diagnostics.add(error("UNKNOWN_DEPENDENCY", rule.id(), base + ".dependencies", "Dependency does not exist: " + dependency.elementId()));
        }
        Set<ValidationDependency> inferredSet = new LinkedHashSet<>(inferred);
        for(ValidationDependency dependency : inferredSet) if(!declared.contains(dependency)) diagnostics.add(warning("MISSING_DECLARED_DEPENDENCY", rule.id(), base + ".dependencies", "Expression uses undeclared dependency: " + dependency.elementId()));
        for(ValidationDependency dependency : declared) if(!inferredSet.contains(dependency)) diagnostics.add(warning("UNUSED_DECLARED_DEPENDENCY", rule.id(), base + ".dependencies", "Declared dependency is not used by the expression: " + dependency.elementId()));
    }

    /** Descripción: Valida las condiciones necesarias para «checkQuickFixes» y rechaza entradas no válidas. Entradas: rule: dato de entrada necesario para la operación; base: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkQuickFixes(ValidationRule rule, String base, ActionModel actions, List<ValidationModelDiagnostic> diagnostics) {
        Set<String> ids = new HashSet<>();
        for(int index = 0; index < rule.quickFixIds().size(); index++) {
            String actionId = rule.quickFixIds().get(index);
            if(!ids.add(actionId)) diagnostics.add(error("DUPLICATE_QUICK_FIX", rule.id(), base + ".quickFixIds[" + index + "]", "Quick fix is duplicated: " + actionId));
            if(actions == null) diagnostics.add(error("ACTION_MODEL_REQUIRED", rule.id(), base + ".quickFixIds[" + index + "]", "Quick fixes require an action model"));
            else if(actions.findAction(actionId).isEmpty()) diagnostics.add(error("UNKNOWN_QUICK_FIX", rule.id(), base + ".quickFixIds[" + index + "]", "Quick fix action does not exist: " + actionId));
        }
    }

    /** Descripción: Valida las condiciones necesarias para «checkExecution» y rechaza entradas no válidas. Entradas: rule: dato de entrada necesario para la operación; base: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkExecution(ValidationRule rule, String base, List<ValidationModelDiagnostic> diagnostics) {
        if(rule.executionMode() != ValidationExecutionMode.EXTERNAL && rule.triggers().isEmpty()) diagnostics.add(error("TRIGGER_REQUIRED", rule.id(), base + ".triggers", "Executable rules require at least one trigger"));
        if(rule.executionMode() == ValidationExecutionMode.MANUAL_ONLY && rule.triggers().stream().anyMatch(trigger -> trigger != ValidationTrigger.MANUAL)) diagnostics.add(error("MANUAL_RULE_TRIGGER_MISMATCH", rule.id(), base + ".triggers", "MANUAL_ONLY rules can only use MANUAL trigger"));
        if(rule.executionMode() == ValidationExecutionMode.FULL_ONLY && rule.triggers().stream().anyMatch(trigger -> trigger != ValidationTrigger.FULL_VALIDATION && trigger != ValidationTrigger.ON_SAVE && trigger != ValidationTrigger.BEFORE_EXPORT)) diagnostics.add(error("FULL_RULE_TRIGGER_MISMATCH", rule.id(), base + ".triggers", "FULL_ONLY rule contains an incremental trigger"));
        if(rule.executionMode() == ValidationExecutionMode.EXTERNAL && rule.authoritativeRepresentation() == AuthoritativeRepresentation.CANONICAL) diagnostics.add(error("EXTERNAL_RULE_CANNOT_BE_CANONICAL", rule.id(), base + ".authoritativeRepresentation", "External execution mode requires an external authoritative representation"));
    }

    /** Descripción: Valida las condiciones necesarias para «checkProvenance» y rechaza entradas no válidas. Entradas: rule: dato de entrada necesario para la operación; base: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkProvenance(ValidationRule rule, String base, List<ValidationModelDiagnostic> diagnostics) {
        if(rule.authoritativeRepresentation() != AuthoritativeRepresentation.CANONICAL && rule.provenance() == null) diagnostics.add(warning("EXTERNAL_RULE_WITHOUT_PROVENANCE", rule.id(), base + ".provenance", "External authoritative rule should preserve its provenance"));
        if(rule.authoritativeRepresentation() == AuthoritativeRepresentation.CANONICAL && rule.conversionStatus() == ConversionStatus.EXTERNAL) diagnostics.add(error("CANONICAL_RULE_CANNOT_BE_EXTERNAL", rule.id(), base + ".conversionStatus", "Canonical rule cannot have EXTERNAL conversion status"));
    }

    /** Descripción: Ejecuta la operación «toValidationDependency» correspondiente a la responsabilidad de ValidationModelChecker. Entradas: dependency: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationDependency toValidationDependency(ExpressionDependency dependency) {
        ValidationDependencyKind kind = switch(dependency.kind()) {
            case CLASS -> ValidationDependencyKind.CLASS;
            case ATTRIBUTE -> ValidationDependencyKind.ATTRIBUTE;
            case RELATION -> ValidationDependencyKind.RELATION;
            case OPERATION -> ValidationDependencyKind.OPERATION;
            case MODEL -> ValidationDependencyKind.MODEL;
        };
        return new ValidationDependency(kind, dependency.kind() == ExpressionDependencyKind.MODEL && dependency.elementId().isBlank() ? "model" : dependency.elementId());
    }

    /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de ValidationModelChecker. Entradas: code: dato de entrada necesario para la operación; ruleId: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationModelDiagnostic error(String code, String ruleId, String path, String message) { return new ValidationModelDiagnostic(ValidationModelDiagnosticSeverity.ERROR, code, ruleId, path, message); }
    /** Descripción: Ejecuta la operación «warning» correspondiente a la responsabilidad de ValidationModelChecker. Entradas: code: dato de entrada necesario para la operación; ruleId: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationModelDiagnostic warning(String code, String ruleId, String path, String message) { return new ValidationModelDiagnostic(ValidationModelDiagnosticSeverity.WARNING, code, ruleId, path, message); }
}
