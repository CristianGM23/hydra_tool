package com.example.editor.validation.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.validation.AuthoritativeRepresentation;
import com.example.editor.validation.validation.ConversionStatus;
import com.example.editor.validation.validation.ValidationDependency;
import com.example.editor.validation.validation.ValidationDependencyKind;
import com.example.editor.validation.validation.ValidationExecutionMode;
import com.example.editor.validation.validation.ValidationModel;
import com.example.editor.validation.validation.ValidationModelCheckReport;
import com.example.editor.validation.validation.ValidationModelChecker;
import com.example.editor.validation.validation.ValidationModelDiagnostic;
import com.example.editor.validation.validation.ValidationModelDiagnosticSeverity;
import com.example.editor.validation.validation.ValidationRule;
import com.example.editor.validation.validation.ValidationRuleCheckResult;
import com.example.editor.validation.validation.ValidationRuleSet;

/** Compila definiciones editables a un plan inmutable con índices de dependencias. */
@Service
public class ValidationCompiler {
    private final ValidationModelChecker checker;
    private final ValidationPlanCache cache;

    /** Descripción: Inicializa una instancia de ValidationCompiler con sus dependencias y datos de entrada. Entradas: checker: dato de entrada necesario para la operación; cache: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ValidationCompiler(ValidationModelChecker checker, ValidationPlanCache cache) {
        this.checker = checker;
        this.cache = cache;
    }

    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de ValidationCompiler. Entradas: metamodel: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación; validations: dato de entrada necesario para la operación; versions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ValidationCompilationResult compile(MetaModel metamodel, ExpressionModel expressions, ActionModel actions,
            ValidationModel validations, ValidationPlanSourceVersions versions) {
        requireInputs(metamodel, expressions, validations);
        String fingerprint = fingerprint(metamodel, expressions, actions, validations, versions);
        var cached = cache.find(fingerprint);
        if(cached.isPresent()) return new ValidationCompilationResult(cached.get(), cached.get().diagnostics(), true);

        ValidationModelCheckReport check = checker.check(validations, metamodel, expressions, actions);
        List<ValidationCompilationDiagnostic> diagnostics = new ArrayList<>();
        check.diagnostics().forEach(item -> diagnostics.add(convert(item)));
        Map<String, ValidationRuleCheckResult> checkByRule = new LinkedHashMap<>();
        check.rules().forEach(item -> checkByRule.put(item.ruleId(), item));

        List<ValidationRule> ordered = orderedRules(validations);
        Map<String, List<String>> ruleSetsByRule = ruleSetsByRule(validations);
        List<CompiledValidationRule> compiled = new ArrayList<>();
        int order = 0;
        for(ValidationRule rule : ordered) {
            if(!rule.enabled()) {
                diagnostics.add(info("RULE_DISABLED", rule.id(), "Disabled rule was omitted from the executable plan"));
                continue;
            }
            ValidationRuleCheckResult ruleCheck = checkByRule.get(rule.id());
            if(ruleCheck != null) ruleCheck.diagnostics().forEach(item -> diagnostics.add(convert(item)));
            if(ruleCheck != null && ruleCheck.hasErrors()) continue;
            if(rule.executionMode() == ValidationExecutionMode.EXTERNAL || rule.conversionStatus() == ConversionStatus.EXTERNAL || rule.conversionStatus() == ConversionStatus.UNSUPPORTED) {
                diagnostics.add(warning("RULE_NOT_EXECUTABLE_IN_CANONICAL_RUNTIME", rule.id(), "External or unsupported rule remains preserved but was omitted from the canonical plan"));
                continue;
            }
            if(rule.authoritativeRepresentation() != AuthoritativeRepresentation.CANONICAL && rule.conversionStatus() != ConversionStatus.FULL) {
                diagnostics.add(warning("PARTIAL_EXTERNAL_RULE_OMITTED", rule.id(), "Partially converted external rule was not selected as executable canonical behavior"));
                continue;
            }
            Set<ValidationDependency> dependencies = new LinkedHashSet<>(rule.dependencies());
            if(ruleCheck != null) dependencies.addAll(ruleCheck.inferredDependencies());
            List<ValidationDependency> resolvedDependencies = List.copyOf(dependencies);
            compiled.add(new CompiledValidationRule(rule.id(), rule.context().kind(), rule.severity(), rule.triggers(),
                    resolvedDependencies.stream().map(ValidationDependency::elementId).distinct().toList(), true, List.of(),
                    rule.name(), rule.code(), order++, rule.context(), rule.conditionExpressionId(), rule.messageTemplate(),
                    resolvedDependencies, rule.quickFixIds(), rule.executionMode(), ruleSetsByRule.getOrDefault(rule.id(), List.of())));
        }
        ValidationDependencyIndex index = buildIndex(compiled);
        ExecutableValidationPlan plan = new ExecutableValidationPlan("1.0", metamodel.id(), versions.metamodelEtag(), validations.id(),
                versions.validationModelEtag(), compiled, validations.id() + "-plan", expressions.id(), validations.actionModelId(),
                fingerprint, versions, index, diagnostics,
                Map.of("metamodelSchemaVersion", metamodel.schemaVersion(), "validationSchemaVersion", validations.schemaVersion()));
        cache.put(plan);
        return new ValidationCompilationResult(plan, diagnostics, false);
    }

    /** Descripción: Valida las condiciones necesarias para «requireInputs» y rechaza entradas no válidas. Entradas: metamodel: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; validations: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireInputs(MetaModel metamodel, ExpressionModel expressions, ValidationModel validations) {
        if(metamodel == null) throw new IllegalArgumentException("metamodel must not be null");
        if(expressions == null) throw new IllegalArgumentException("expressions must not be null");
        if(validations == null) throw new IllegalArgumentException("validations must not be null");
        if(!metamodel.id().equals(validations.metamodelId()) || !metamodel.id().equals(expressions.metamodelId())) throw new IllegalArgumentException("All validation compilation sources must belong to the same metamodel");
    }

    /** Descripción: Ejecuta la operación «orderedRules» correspondiente a la responsabilidad de ValidationCompiler. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<ValidationRule> orderedRules(ValidationModel model) {
        Map<String, ValidationRule> byId = new LinkedHashMap<>();
        model.rules().forEach(rule -> byId.put(rule.id(), rule));
        List<ValidationRule> result = new ArrayList<>();
        Set<String> added = new LinkedHashSet<>();
        model.ruleSets().stream().filter(ValidationRuleSet::enabled).sorted(Comparator.comparingInt(ValidationRuleSet::order).thenComparing(ValidationRuleSet::id)).forEach(set -> {
            for(String ruleId : set.ruleIds()) if(added.add(ruleId) && byId.containsKey(ruleId)) result.add(byId.get(ruleId));
        });
        for(ValidationRule rule : model.rules()) if(added.add(rule.id())) result.add(rule);
        return result;
    }

    /** Descripción: Ejecuta la operación «ruleSetsByRule» correspondiente a la responsabilidad de ValidationCompiler. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, List<String>> ruleSetsByRule(ValidationModel model) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for(ValidationRuleSet set : model.ruleSets()) {
            if(!set.enabled()) continue;
            for(String ruleId : set.ruleIds()) result.computeIfAbsent(ruleId, ignored -> new ArrayList<>()).add(set.id());
        }
        result.replaceAll((key, value) -> List.copyOf(value));
        return result;
    }

    /** Descripción: Crea o prepara el resultado de «buildIndex» dentro de ValidationCompiler. Entradas: rules: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationDependencyIndex buildIndex(List<CompiledValidationRule> rules) {
        Map<String, List<String>> classes = new LinkedHashMap<>();
        Map<String, List<String>> attributes = new LinkedHashMap<>();
        Map<String, List<String>> relations = new LinkedHashMap<>();
        Map<String, List<String>> operations = new LinkedHashMap<>();
        Map<String, List<String>> triggers = new LinkedHashMap<>();
        Set<String> modelRules = new LinkedHashSet<>();
        for(CompiledValidationRule rule : rules) {
            if(!rule.context().classId().isBlank()) add(classes, rule.context().classId(), rule.ruleId());
            if(!rule.context().featureId().isBlank()) {
                switch(rule.context().kind()) {
                    case ATTRIBUTE -> add(attributes, rule.context().featureId(), rule.ruleId());
                    case RELATION -> add(relations, rule.context().featureId(), rule.ruleId());
                    default -> { }
                }
            }
            for(ValidationDependency dependency : rule.dependencies()) {
                switch(dependency.kind()) {
                    case CLASS -> add(classes, dependency.elementId(), rule.ruleId());
                    case ATTRIBUTE -> add(attributes, dependency.elementId(), rule.ruleId());
                    case RELATION -> add(relations, dependency.elementId(), rule.ruleId());
                    case OPERATION -> add(operations, dependency.elementId(), rule.ruleId());
                    case MODEL -> modelRules.add(rule.ruleId());
                }
            }
            rule.triggers().forEach(trigger -> add(triggers, trigger.name(), rule.ruleId()));
            switch(rule.context().kind()) {
                case MODEL, PROJECT, REPRESENTATION -> modelRules.add(rule.ruleId());
                default -> { }
            }
        }
        return new ValidationDependencyIndex(classes, attributes, relations, operations, triggers, List.copyOf(modelRules));
    }

    /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de ValidationCompiler. Entradas: index: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo; ruleId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void add(Map<String, List<String>> index, String key, String ruleId) {
        List<String> values = new ArrayList<>(index.getOrDefault(key, List.of()));
        if(!values.contains(ruleId)) values.add(ruleId);
        index.put(key, List.copyOf(values));
    }

    /** Descripción: Ejecuta la operación «convert» correspondiente a la responsabilidad de ValidationCompiler. Entradas: diagnostic: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationCompilationDiagnostic convert(ValidationModelDiagnostic diagnostic) {
        ValidationCompilationSeverity severity = switch(diagnostic.severity()) {
            case ERROR -> ValidationCompilationSeverity.ERROR;
            case WARNING -> ValidationCompilationSeverity.WARNING;
            case INFO -> ValidationCompilationSeverity.INFO;
        };
        return new ValidationCompilationDiagnostic(severity, diagnostic.code(), diagnostic.ruleId(), diagnostic.message());
    }
    /** Descripción: Ejecuta la operación «warning» correspondiente a la responsabilidad de ValidationCompiler. Entradas: code: dato de entrada necesario para la operación; ruleId: identificador que delimita el recurso objetivo; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationCompilationDiagnostic warning(String code, String ruleId, String message) { return new ValidationCompilationDiagnostic(ValidationCompilationSeverity.WARNING, code, ruleId, message); }
    /** Descripción: Ejecuta la operación «info» correspondiente a la responsabilidad de ValidationCompiler. Entradas: code: dato de entrada necesario para la operación; ruleId: identificador que delimita el recurso objetivo; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationCompilationDiagnostic info(String code, String ruleId, String message) { return new ValidationCompilationDiagnostic(ValidationCompilationSeverity.INFO, code, ruleId, message); }

    /** Descripción: Ejecuta la operación «fingerprint» correspondiente a la responsabilidad de ValidationCompiler. Entradas: metamodel: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación; validations: dato de entrada necesario para la operación; versions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fingerprint(MetaModel metamodel, ExpressionModel expressions, ActionModel actions, ValidationModel validations, ValidationPlanSourceVersions versions) {
        String source = String.join("|", metamodel.id(), metamodel.schemaVersion(), expressions.id(), expressions.schemaVersion(),
                actions == null ? "" : actions.id(), validations.id(), validations.schemaVersion(), versions.toString(),
                metamodel.toString(), expressions.toString(), actions == null ? "" : actions.toString(), validations.toString());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch(NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is not available", exception); }
    }
}
