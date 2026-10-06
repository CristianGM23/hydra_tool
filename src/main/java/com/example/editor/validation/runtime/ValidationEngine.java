package com.example.editor.validation.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.editor.metamodel.behavior.OperationQueryRuntime;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionEvaluationContext;
import com.example.editor.semantic.expression.ExpressionEvaluationException;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.semantic.expression.ExpressionOperationInvoker;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.support.CanonicalMetamodelIndex;
import com.example.editor.validation.validation.ValidationContextKind;
import com.example.editor.validation.validation.ValidationExecutionMode;
import com.example.editor.validation.validation.ValidationSeverity;
import com.example.editor.validation.validation.ValidationTrigger;

/** Ejecuta planes compilados sobre CanonicalModelDocument sin depender de EMF o de la UI. */
@Service
public class ValidationEngine {
    private final ExpressionEvaluator evaluator;
    private final OperationQueryRuntime operationRuntime;
    private final Clock clock;

    @Autowired
    /** Descripción: Inicializa una instancia de ValidationEngine con sus dependencias y datos de entrada. Entradas: evaluator: dato de entrada necesario para la operación; operationRuntime: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ValidationEngine(ExpressionEvaluator evaluator, OperationQueryRuntime operationRuntime) {
        this(evaluator, operationRuntime, Clock.systemUTC());
    }

    /** Descripción: Inicializa una instancia de ValidationEngine con sus dependencias y datos de entrada. Entradas: evaluator: dato de entrada necesario para la operación; operationRuntime: dato de entrada necesario para la operación; clock: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ ValidationEngine(ExpressionEvaluator evaluator, OperationQueryRuntime operationRuntime, Clock clock) {
        this.evaluator = evaluator;
        this.operationRuntime = operationRuntime;
        this.clock = clock;
    }

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: plan: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; operationBehaviors: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ModelValidationReport validate(ExecutableValidationPlan plan, MetaModel metamodel, ExpressionModel expressions,
            OperationBehaviorModel operationBehaviors, ValidationExecutionRequest request) {
        if(plan == null) throw new IllegalArgumentException("plan must not be null");
        if(metamodel == null) throw new IllegalArgumentException("metamodel must not be null");
        if(expressions == null) throw new IllegalArgumentException("expressions must not be null");
        if(request == null) throw new IllegalArgumentException("request must not be null");
        if(!request.model().metamodelId().equals(metamodel.id()) || !plan.metamodelId().equals(metamodel.id())) {
            throw new IllegalArgumentException("Plan, metamodel and model document must use the same metamodel id");
        }

        Instant started = clock.instant();
        Map<String, ExpressionDefinition> expressionById = expressions.expressions().stream()
                .collect(Collectors.toMap(ExpressionDefinition::id, value -> value, (left, right) -> left, LinkedHashMap::new));
        ExpressionOperationInvoker operationInvoker = operationRuntime.create(metamodel, expressions, operationBehaviors);
        Set<String> selectedRuleIds = selectRuleIds(plan, request);
        List<ModelValidationDiagnostic> diagnostics = new ArrayList<>();
        List<String> executed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for(ValidationCompilationDiagnostic compilationDiagnostic : plan.diagnostics()) {
            if(compilationDiagnostic.severity() != ValidationCompilationSeverity.ERROR || compilationDiagnostic.ruleId().isBlank()) continue;
            diagnostics.add(new ModelValidationDiagnostic(
                    stableDiagnosticId(request.model().id(), compilationDiagnostic.ruleId(), "", "BROKEN"),
                    ValidationDiagnosticStatus.BROKEN_RULE,
                    compilationDiagnostic.ruleId(),
                    ValidationSeverity.ERROR,
                    compilationDiagnostic.message(),
                    new ValidationDiagnosticLocation("", "", ""),
                    List.of(),
                    Map.of("problemCode", compilationDiagnostic.code())));
        }

        for(CompiledValidationRule rule : plan.rules()) {
            if(!selectedRuleIds.contains(rule.ruleId()) || !eligibleForMode(rule, request.mode())) {
                skipped.add(rule.ruleId());
                continue;
            }
            ExpressionDefinition expression = expressionById.get(rule.conditionExpressionId());
            if(expression == null) {
                diagnostics.add(new ModelValidationDiagnostic(
                        stableDiagnosticId(request.model().id(), rule.ruleId(), "", "MISSING_EXPRESSION"),
                        ValidationDiagnosticStatus.BROKEN_RULE,
                        rule.ruleId(),
                        ValidationSeverity.ERROR,
                        "Compiled rule references a missing expression: " + rule.conditionExpressionId(),
                        new ValidationDiagnosticLocation("", rule.context().featureId(), ""),
                        rule.quickFixIds(),
                        Map.of("problemCode", rule.code(), "targetClassId", rule.context().classId())));
                skipped.add(rule.ruleId());
                continue;
            }
            executed.add(rule.ruleId());
            List<CanonicalModelElement> candidates = candidates(rule, metamodel, request);
            if(isGlobal(rule.context().kind())) {
                evaluate(rule, expression, null, metamodel, request.model(), request.variables(), operationInvoker, diagnostics);
            } else {
                for(CanonicalModelElement candidate : candidates) {
                    evaluate(rule, expression, candidate, metamodel, request.model(), request.variables(), operationInvoker, diagnostics);
                }
            }
        }

        Instant completed = clock.instant();
        long duration = Math.max(0L, Duration.between(started, completed).toMillis());
        ValidationReportScope scope = scope(request);
        return new ModelValidationReport(
                "1.0",
                "report:" + request.model().id() + ":" + request.mode().name().toLowerCase(java.util.Locale.ROOT),
                request.model().id(),
                metamodel.id(),
                plan.validationModelId(),
                completed,
                scope,
                diagnostics,
                new ValidationExecutionMetrics(executed.size(), skipped.size(), duration),
                plan.id(),
                request.mode(),
                started,
                completed,
                executed,
                skipped);
    }

    /** Descripción: Ejecuta la operación «evaluate» correspondiente a la responsabilidad de ValidationEngine. Entradas: rule: dato de entrada necesario para la operación; expression: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; variables: dato de entrada necesario para la operación; operationInvoker: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void evaluate(CompiledValidationRule rule, ExpressionDefinition expression, CanonicalModelElement target,
            MetaModel metamodel, CanonicalModelDocument model, Map<String, Object> variables,
            ExpressionOperationInvoker operationInvoker, List<ModelValidationDiagnostic> diagnostics) {
        String elementId = target == null ? "" : target.id();
        String classId = target == null ? rule.context().classId() : target.classId();
        try {
            ExpressionEvaluationContext context = new ExpressionEvaluationContext(metamodel, model, elementId, variables);
            Object value = evaluator.evaluate(expression, context, operationInvoker).value();
            if(!(value instanceof Boolean satisfied)) {
                throw new ExpressionEvaluationException("Validation condition must return BOOLEAN but returned "
                        + (value == null ? "null" : value.getClass().getSimpleName()));
            }
            if(!satisfied) {
                diagnostics.add(new ModelValidationDiagnostic(
                        stableDiagnosticId(model.id(), rule.ruleId(), elementId, rule.context().featureId()),
                        ValidationDiagnosticStatus.VIOLATION,
                        rule.ruleId(),
                        rule.severity(),
                        message(rule, target),
                        new ValidationDiagnosticLocation(elementId, rule.context().featureId(), ""),
                        rule.quickFixIds(),
                        Map.of("problemCode", rule.code(), "targetClassId", classId)));
            }
        } catch(RuntimeException exception) {
            diagnostics.add(new ModelValidationDiagnostic(
                    stableDiagnosticId(model.id(), rule.ruleId(), elementId, "EXECUTION"),
                    ValidationDiagnosticStatus.RULE_EXECUTION_ERROR,
                    rule.ruleId(),
                    ValidationSeverity.ERROR,
                    "Rule execution failed: " + safeMessage(exception),
                    new ValidationDiagnosticLocation(elementId, rule.context().featureId(), ""),
                    List.of(),
                    Map.of("problemCode", rule.code(), "targetClassId", classId)));
        }
    }

    /** Descripción: Ejecuta la operación «selectRuleIds» correspondiente a la responsabilidad de ValidationEngine. Entradas: plan: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Set<String> selectRuleIds(ExecutableValidationPlan plan, ValidationExecutionRequest request) {
        if(request.mode() != ValidationRunMode.INCREMENTAL || request.changeSet().isEmpty()) {
            return plan.rules().stream().map(CompiledValidationRule::ruleId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        ValidationDependencyIndex index = plan.dependencyIndex();
        Set<String> result = new LinkedHashSet<>(index.modelRules());
        request.changeSet().classIds().forEach(id -> result.addAll(index.classRules().getOrDefault(id, List.of())));
        request.changeSet().attributeIds().forEach(id -> result.addAll(index.attributeRules().getOrDefault(id, List.of())));
        request.changeSet().relationIds().forEach(id -> result.addAll(index.relationRules().getOrDefault(id, List.of())));
        request.changeSet().operationIds().forEach(id -> result.addAll(index.operationRules().getOrDefault(id, List.of())));
        if(request.changeSet().modelChanged()) result.addAll(index.modelRules());
        boolean hasSpecificDependencyChanges = !request.changeSet().classIds().isEmpty()
                || !request.changeSet().attributeIds().isEmpty()
                || !request.changeSet().relationIds().isEmpty()
                || !request.changeSet().operationIds().isEmpty()
                || request.changeSet().modelChanged();
        if(result.isEmpty() && !hasSpecificDependencyChanges && !request.changeSet().elementIds().isEmpty()) {
            for(CanonicalModelElement element : request.model().elements()) {
                if(request.changeSet().elementIds().contains(element.id())) {
                    result.addAll(index.classRules().getOrDefault(element.classId(), List.of()));
                }
            }
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «eligibleForMode» correspondiente a la responsabilidad de ValidationEngine. Entradas: rule: dato de entrada necesario para la operación; mode: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean eligibleForMode(CompiledValidationRule rule, ValidationRunMode mode) {
        if(!rule.executable() || rule.executionMode() == ValidationExecutionMode.EXTERNAL) return false;
        return switch(mode) {
            case FULL -> rule.executionMode() != ValidationExecutionMode.MANUAL_ONLY;
            case INCREMENTAL -> rule.executionMode() == ValidationExecutionMode.INCREMENTAL;
            case MANUAL -> rule.triggers().contains(ValidationTrigger.MANUAL)
                    || rule.executionMode() == ValidationExecutionMode.MANUAL_ONLY;
            case ON_OPEN -> rule.triggers().contains(ValidationTrigger.ON_OPEN);
            case ON_SAVE -> rule.triggers().contains(ValidationTrigger.ON_SAVE);
            case BEFORE_EXPORT -> rule.triggers().contains(ValidationTrigger.BEFORE_EXPORT);
            case SELECTION, CLASS -> rule.executionMode() != ValidationExecutionMode.FULL_ONLY
                    && rule.executionMode() != ValidationExecutionMode.MANUAL_ONLY;
        };
    }

    /** Descripción: Evalúa la condición «candidates» sin modificar el estado externo. Entradas: rule: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private List<CanonicalModelElement> candidates(CompiledValidationRule rule, MetaModel metamodel,
            ValidationExecutionRequest request) {
        CanonicalMetamodelIndex index = new CanonicalMetamodelIndex(metamodel);
        List<CanonicalModelElement> base = request.model().elements();
        if(request.mode() == ValidationRunMode.SELECTION || rule.context().kind() == ValidationContextKind.SELECTION) {
            Set<String> selected = new LinkedHashSet<>(request.selectionElementIds());
            base = base.stream().filter(element -> selected.contains(element.id())).toList();
        }
        if(request.mode() == ValidationRunMode.CLASS && !request.classId().isBlank()) {
            String requestedClass = request.classId();
            base = base.stream().filter(element -> index.isSameOrSubtype(element.classId(), requestedClass)).toList();
        }
        String contextClass = rule.context().classId();
        if(rule.context().kind() == ValidationContextKind.RELATION && contextClass.isBlank()) {
            contextClass = index.findRelation(rule.context().featureId()).map(MetaRelation::sourceTypeId).orElse("");
        }
        if(!contextClass.isBlank()) {
            String expected = contextClass;
            base = base.stream().filter(element -> index.isSameOrSubtype(element.classId(), expected)).toList();
        }
        if(request.mode() == ValidationRunMode.INCREMENTAL && !request.changeSet().elementIds().isEmpty()) {
            Set<String> affected = affectedElementIds(request.model(), request.changeSet());
            base = base.stream().filter(element -> affected.contains(element.id())).toList();
        }
        return base;
    }

    /** Descripción: Ejecuta la operación «affectedElementIds» correspondiente a la responsabilidad de ValidationEngine. Entradas: model: dato de entrada necesario para la operación; changes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Set<String> affectedElementIds(CanonicalModelDocument model, ValidationChangeSet changes) {
        Set<String> result = new LinkedHashSet<>(changes.elementIds());
        if(!changes.relationIds().isEmpty()) {
            model.relations().stream()
                    .filter(relation -> changes.relationIds().contains(relation.relationId()))
                    .forEach(relation -> {
                        result.add(relation.sourceElementId());
                        result.add(relation.targetElementId());
                    });
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «scope» correspondiente a la responsabilidad de ValidationEngine. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationReportScope scope(ValidationExecutionRequest request) {
        return switch(request.mode()) {
            case SELECTION -> new ValidationReportScope(ValidationReportScopeKind.SELECTION,
                    request.selectionElementIds().size() == 1 ? request.selectionElementIds().getFirst() : "", "", "", "");
            case CLASS -> new ValidationReportScope(ValidationReportScopeKind.OBJECT, "", "", "", "");
            default -> ValidationReportScope.model();
        };
    }

    /** Descripción: Evalúa la condición «isGlobal» sin modificar el estado externo. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isGlobal(ValidationContextKind kind) {
        return kind == ValidationContextKind.MODEL || kind == ValidationContextKind.PROJECT
                || kind == ValidationContextKind.REPRESENTATION;
    }

    /** Descripción: Ejecuta la operación «message» correspondiente a la responsabilidad de ValidationEngine. Entradas: rule: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String message(CompiledValidationRule rule, CanonicalModelElement target) {
        String value = rule.messageTemplate();
        value = value.replace("${rule}", rule.name())
                .replace("${code}", rule.code())
                .replace("${featureId}", rule.context().featureId())
                .replace("${elementId}", target == null ? "" : target.id())
                .replace("${classId}", target == null ? rule.context().classId() : target.classId());
        return value;
    }

    /** Descripción: Ejecuta la operación «stableDiagnosticId» correspondiente a la responsabilidad de ValidationEngine. Entradas: modelId: identificador que delimita el recurso objetivo; ruleId: identificador que delimita el recurso objetivo; elementId: identificador que delimita el recurso objetivo; featureId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String stableDiagnosticId(String modelId, String ruleId, String elementId, String featureId) {
        String source = String.join("|", modelId, ruleId, elementId == null ? "" : elementId,
                featureId == null ? "" : featureId);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return "diagnostic-" + java.util.HexFormat.of().formatHex(digest).substring(0, 24);
        } catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /** Descripción: Ejecuta la operación «safeMessage» correspondiente a la responsabilidad de ValidationEngine. Entradas: throwable: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
