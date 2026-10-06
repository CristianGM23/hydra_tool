package com.example.editor.transformation.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collection;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationBindingErrorPolicy;
import com.example.editor.transformation.core.TransformationCollectionMode;
import com.example.editor.transformation.core.TransformationConversionMode;
import com.example.editor.transformation.core.TransformationValuePolicy;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.semantic.expression.ExpressionEvaluationContext;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;

/**
 * Runtime de referencia de HYDRA para M2M. Ejecuta primero matching/asignación y
 * después población/resolución, de forma que todas las equivalencias existen
 * antes de resolver relaciones entre objetos transformados.
 */
@Service
public class CanonicalTransformationRuntime {
    private final ExpressionEvaluator expressions;

    /** Descripción: Inicializa una instancia de CanonicalTransformationRuntime con sus dependencias y datos de entrada. Entradas: expressions: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalTransformationRuntime(ExpressionEvaluator expressions) {
        this.expressions = expressions;
    }

    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de CanonicalTransformationRuntime. Entradas: plan: dato de entrada necesario para la operación; sourceMetamodel: dato de entrada necesario para la operación; targetMetamodel: dato de entrada necesario para la operación; sourceModel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationExecutionResult execute(CompiledTransformationPlan plan, MetaModel sourceMetamodel,
            MetaModel targetMetamodel, CanonicalModelDocument sourceModel) {
        requireCompatible(plan, sourceMetamodel, targetMetamodel, sourceModel);
        String executionId = "exec-" + hash(plan.transformationId() + "|" + plan.direction() + "|" + sourceModel.id() + "|" + sourceModel.metamodelEtag(), 24);
        String targetModelId = documentId("model-" + hash(plan.transformationId() + "|" + plan.direction() + "|" + sourceModel.id(), 32));
        List<Allocation> allocations = allocate(plan, sourceMetamodel, sourceModel);
        Map<String, Allocation> allocationByTargetId = new LinkedHashMap<>();
        allocations.forEach(item -> allocationByTargetId.put(item.targetElementId(), item));
        Map<String, Map<String, Object>> attributes = new LinkedHashMap<>();
        allocations.forEach(item -> attributes.put(item.targetElementId(), new LinkedHashMap<>()));
        List<TransformationTraceLink> links = plan.tracePolicy().enabled() ? allocations.stream().map(item -> new TransformationTraceLink(
                item.source().id(), item.rule().id(), item.targetAlias(), item.targetElementId(), item.rule().correspondenceId(), plan.direction())).toList() : List.of();
        List<CanonicalModelRelation> relations = new ArrayList<>();
        List<TransformationBindingTrace> bindingTraces = new ArrayList<>();
        populate(plan, sourceMetamodel, targetMetamodel, sourceModel, allocations, allocationByTargetId, attributes, relations, bindingTraces);
        List<CanonicalModelElement> elements = allocations.stream().map(allocation -> new CanonicalModelElement(
                allocation.targetElementId(), allocation.targetTypeId(), attributes.get(allocation.targetElementId()),
                Map.of("hydra.transformation.rule", allocation.rule().id(), "hydra.transformation.source", allocation.source().id()))).toList();
        Set<String> sourceRoots = Set.copyOf(sourceModel.rootElementIds());
        List<String> roots = allocations.stream().filter(item -> sourceRoots.contains(item.source().id())).map(Allocation::targetElementId).distinct().toList();
        CanonicalModelDocument targetModel = new CanonicalModelDocument("1.0", targetModelId, plan.targetMetamodelId(),
                plan.targetMetamodelFingerprint(), sourceModel.name() + (plan.direction().name().equals("REVERSE") ? " ← " : " → ") + plan.transformationName(), roots, elements,
                List.copyOf(relations), Map.of("hydra.transformation.id", plan.transformationId(), "hydra.transformation.execution", executionId,
                        "hydra.transformation.sourceModel", sourceModel.id(), "hydra.transformation.direction", plan.direction().name()));
        List<TransformationBindingTrace> details = plan.tracePolicy().enabled() && plan.tracePolicy().retainBindingDetails()
                ? List.copyOf(bindingTraces) : List.of();
        return new TransformationExecutionResult(targetModel, new TransformationTrace(executionId, plan.transformationId(),
                sourceModel.id(), targetModel.id(), links, details, plan.direction()));
    }

    /** Descripción: Ejecuta la operación «allocate» correspondiente a la responsabilidad de CanonicalTransformationRuntime. Entradas: plan: dato de entrada necesario para la operación; sourceMetamodel: dato de entrada necesario para la operación; sourceModel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<Allocation> allocate(CompiledTransformationPlan plan, MetaModel sourceMetamodel, CanonicalModelDocument sourceModel) {
        List<Allocation> result = new ArrayList<>();
        for(CanonicalModelElement source : sourceModel.elements()) {
            for(TransformationRule rule : plan.rules()) {
                if(!isInstanceOf(sourceMetamodel, source.classId(), rule.sourceTypeId()) || !matchesGuard(rule, sourceMetamodel, sourceModel, source)) continue;
                for(TransformationTargetPattern target : rule.targets()) {
                    String targetId = elementId("target-" + hash(plan.transformationId() + "|" + plan.direction() + "|" + rule.id() + "|" + source.id() + "|" + target.alias(), 40));
                    result.add(new Allocation(source, rule, target.alias(), target.typeId(), targetId));
                }
            }
        }
        return List.copyOf(result);
    }

    /** Descripción: Evalúa la condición «matchesGuard» sin modificar el estado externo. Entradas: rule: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean matchesGuard(TransformationRule rule, MetaModel metamodel, CanonicalModelDocument model, CanonicalModelElement source) {
        if(rule.guard() == null) return true;
        Object value = expressions.evaluate(rule.guard(), new ExpressionEvaluationContext(metamodel, model, source.id(), Map.of())).value();
        if(value instanceof Boolean result) return result;
        throw new TransformationExecutionException("Transformation guard did not evaluate to Boolean: " + rule.id());
    }

    /** Descripción: Ejecuta la operación «populate» correspondiente a la responsabilidad de CanonicalTransformationRuntime. Entradas: plan: dato de entrada necesario para la operación; sourceMetamodel: dato de entrada necesario para la operación; targetMetamodel: dato de entrada necesario para la operación; sourceModel: dato de entrada necesario para la operación; allocations: dato de entrada necesario para la operación; allocationByTargetId: identificador que delimita el recurso objetivo; attributes: dato de entrada necesario para la operación; relations: dato de entrada necesario para la operación; bindingTraces: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void populate(CompiledTransformationPlan plan, MetaModel sourceMetamodel, MetaModel targetMetamodel,
            CanonicalModelDocument sourceModel, List<Allocation> allocations, Map<String, Allocation> allocationByTargetId,
            Map<String, Map<String, Object>> attributes, List<CanonicalModelRelation> relations,
            List<TransformationBindingTrace> bindingTraces) {
        Set<String> relationKeys = new LinkedHashSet<>();
        for(Allocation allocation : allocations) {
            for(TransformationBinding binding : allocation.rule().bindings()) {
                if(!binding.targetAlias().equals(allocation.targetAlias())) continue;
                try {
                    if(binding.kind() == TransformationBindingKind.TRACE_EQUIVALENT) {
                        resolveTraceRelation(binding, allocation, sourceModel, targetMetamodel, allocations, allocationByTargetId, relations, relationKeys);
                    } else {
                        Object value = adaptValue(binding, bindingValue(binding, allocation, sourceMetamodel, sourceModel));
                        if(value != SkipValue.INSTANCE) attributes.get(allocation.targetElementId()).put(binding.targetFeatureId(), value);
                    }
                    bindingTraces.add(new TransformationBindingTrace(allocation.rule().id(), binding.id(), binding.kind(), allocation.source().id(),
                            allocation.targetElementId(), binding.sourceFeatureId(), binding.targetFeatureId()));
                } catch(RuntimeException exception) {
                    throw new TransformationExecutionException("Binding " + binding.id() + " failed for source element " + allocation.source().id(), exception);
                }
            }
        }
    }

    /** Descripción: Ejecuta la operación «bindingValue» correspondiente a la responsabilidad de CanonicalTransformationRuntime. Entradas: binding: dato de entrada necesario para la operación; allocation: dato de entrada necesario para la operación; sourceMetamodel: dato de entrada necesario para la operación; sourceModel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object bindingValue(TransformationBinding binding, Allocation allocation, MetaModel sourceMetamodel, CanonicalModelDocument sourceModel) {
        return switch(binding.kind()) {
            case DIRECT -> allocation.source().attributes().get(binding.sourceFeatureId());
            case CONSTANT -> binding.literalValue();
            case EXPRESSION -> expressions.evaluate(binding.expression(), new ExpressionEvaluationContext(sourceMetamodel, sourceModel,
                    allocation.source().id(), Map.of())).value();
            case TRACE_EQUIVALENT -> throw new TransformationExecutionException("Trace-equivalent bindings create relations, not attribute values");
        };
    }


    /** Adapta un valor mediante la política portable declarada en el binding. */
    /** Descripción: Ejecuta la operación «adaptValue» correspondiente a la responsabilidad de SkipValue. Entradas: binding: dato de entrada necesario para la operación; raw: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object adaptValue(TransformationBinding binding, Object raw) {
        TransformationValuePolicy policy = binding.valuePolicy();
        try {
            Object value = reduceCollection(raw, policy.collectionMode());
            if(value != null && !policy.enumMapping().isEmpty()) {
                String key = String.valueOf(value);
                if(policy.enumMapping().containsKey(key)) value = policy.enumMapping().get(key);
            }
            return convert(value, policy.conversionMode());
        } catch(RuntimeException exception) {
            if(policy.errorPolicy() == TransformationBindingErrorPolicy.SKIP_VALUE) return SkipValue.INSTANCE;
            if(policy.errorPolicy() == TransformationBindingErrorPolicy.USE_FALLBACK) return policy.fallbackValue();
            throw exception;
        }
    }

    /** Reduce colecciones de forma determinista antes de aplicar conversiones escalares. */
    /** Descripción: Ejecuta la operación «reduceCollection» correspondiente a la responsabilidad de SkipValue. Entradas: value: valor de entrada que debe procesarse; mode: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object reduceCollection(Object value, TransformationCollectionMode mode) {
        if(mode == TransformationCollectionMode.PRESERVE || !(value instanceof Collection<?> collection)) return value;
        if(mode == TransformationCollectionMode.COUNT) return collection.size();
        if(collection.isEmpty()) return null;
        if(mode == TransformationCollectionMode.FIRST) return collection.iterator().next();
        Object last = null;
        for(Object item : collection) last = item;
        return last;
    }

    /** Ejecuta conversiones primitivas explícitas y evita coerciones implícitas no trazables. */
    /** Descripción: Ejecuta la operación «convert» correspondiente a la responsabilidad de SkipValue. Entradas: value: valor de entrada que debe procesarse; mode: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object convert(Object value, TransformationConversionMode mode) {
        if(value == null || mode == TransformationConversionMode.NONE) return value;
        return switch(mode) {
            case TO_STRING -> String.valueOf(value);
            case TO_INTEGER -> value instanceof Number number ? number.intValue() : Integer.valueOf(String.valueOf(value).trim());
            case TO_LONG -> value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value).trim());
            case TO_DOUBLE -> value instanceof Number number ? number.doubleValue() : Double.valueOf(String.valueOf(value).trim());
            case TO_BOOLEAN -> value instanceof Boolean flag ? flag : parseBoolean(String.valueOf(value));
            case NONE -> value;
        };
    }

    /** Convierte únicamente literales booleanos inequívocos. */
    /** Descripción: Ejecuta la operación «parseBoolean» correspondiente a la responsabilidad de SkipValue. Entradas: raw: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Boolean parseBoolean(String raw) {
        String normalized = raw.trim();
        if("true".equalsIgnoreCase(normalized)) return Boolean.TRUE;
        if("false".equalsIgnoreCase(normalized)) return Boolean.FALSE;
        throw new IllegalArgumentException("Value is not a Boolean literal: " + raw);
    }

    /** Descripción: Obtiene la información correspondiente a «resolveTraceRelation» dentro de CanonicalTransformationRuntime. Entradas: binding: dato de entrada necesario para la operación; sourceAllocation: dato de entrada necesario para la operación; sourceModel: dato de entrada necesario para la operación; targetMetamodel: dato de entrada necesario para la operación; allocations: dato de entrada necesario para la operación; allocationByTargetId: identificador que delimita el recurso objetivo; targetRelations: dato de entrada necesario para la operación; relationKeys: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void resolveTraceRelation(TransformationBinding binding, Allocation sourceAllocation, CanonicalModelDocument sourceModel,
            MetaModel targetMetamodel, List<Allocation> allocations, Map<String, Allocation> allocationByTargetId,
            List<CanonicalModelRelation> targetRelations, Set<String> relationKeys) {
        var targetRelation = targetMetamodel.relations().stream().filter(item -> item.id().equals(binding.targetFeatureId())).findFirst()
                .orElseThrow(() -> new TransformationExecutionException("Target relation was not found: " + binding.targetFeatureId()));
        List<String> relatedSourceIds = sourceModel.relations().stream()
                .filter(item -> item.relationId().equals(binding.sourceFeatureId()) && item.sourceElementId().equals(sourceAllocation.source().id()))
                .map(CanonicalModelRelation::targetElementId).toList();
        for(String relatedSourceId : relatedSourceIds) {
            for(Allocation candidate : allocations) {
                if(!candidate.source().id().equals(relatedSourceId) || !isInstanceOfTarget(targetMetamodel, candidate.targetTypeId(), targetRelation.targetTypeId())) continue;
                if(!allocationByTargetId.containsKey(candidate.targetElementId())) continue;
                String key = binding.targetFeatureId() + "|" + sourceAllocation.targetElementId() + "|" + candidate.targetElementId();
                if(!relationKeys.add(key)) continue;
                String relationId = elementId("relation-" + hash(key, 40));
                targetRelations.add(new CanonicalModelRelation(relationId, binding.targetFeatureId(), sourceAllocation.targetElementId(),
                        candidate.targetElementId(), Map.of("hydra.transformation.binding", binding.id())));
            }
        }
    }

    /** Descripción: Evalúa la condición «isInstanceOf» sin modificar el estado externo. Entradas: model: dato de entrada necesario para la operación; actualClassId: identificador que delimita el recurso objetivo; expectedClassId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isInstanceOf(MetaModel model, String actualClassId, String expectedClassId) {
        if(actualClassId.equals(expectedClassId)) return true;
        Set<String> visited = new LinkedHashSet<>();
        List<String> pending = new ArrayList<>();
        pending.add(actualClassId);
        while(!pending.isEmpty()) {
            String current = pending.removeFirst();
            if(!visited.add(current)) continue;
            for(var relation : model.relations()) {
                if(relation.kind() != MetaRelationKind.INHERITANCE || !relation.sourceTypeId().equals(current)) continue;
                if(relation.targetTypeId().equals(expectedClassId)) return true;
                pending.add(relation.targetTypeId());
            }
        }
        return false;
    }

    /** Descripción: Evalúa la condición «isInstanceOfTarget» sin modificar el estado externo. Entradas: model: dato de entrada necesario para la operación; actualClassId: identificador que delimita el recurso objetivo; expectedClassId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isInstanceOfTarget(MetaModel model, String actualClassId, String expectedClassId) {
        return isInstanceOf(model, actualClassId, expectedClassId);
    }

    /** Descripción: Valida las condiciones necesarias para «requireCompatible» y rechaza entradas no válidas. Entradas: plan: dato de entrada necesario para la operación; sourceMetamodel: dato de entrada necesario para la operación; targetMetamodel: dato de entrada necesario para la operación; sourceModel: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireCompatible(CompiledTransformationPlan plan, MetaModel sourceMetamodel, MetaModel targetMetamodel,
            CanonicalModelDocument sourceModel) {
        if(!plan.sourceMetamodelId().equals(sourceMetamodel.id()) || !sourceModel.metamodelId().equals(sourceMetamodel.id()))
            throw new TransformationExecutionException("Source model does not conform to the transformation source metamodel");
        if(!plan.targetMetamodelId().equals(targetMetamodel.id()))
            throw new TransformationExecutionException("Target metamodel does not match the compiled transformation plan");
    }

    /** Descripción: Ejecuta la operación «documentId» correspondiente a la responsabilidad de CanonicalTransformationRuntime. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String documentId(String value) { return value.length() <= 120 ? value : value.substring(0, 120); }
    /** Descripción: Ejecuta la operación «elementId» correspondiente a la responsabilidad de CanonicalTransformationRuntime. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String elementId(String value) { return value.length() <= 240 ? value : value.substring(0, 240); }
    /** Descripción: Evalúa la condición «hash» sin modificar el estado externo. Entradas: value: valor de entrada que debe procesarse; length: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private String hash(String value, int length) {
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
            return digest.substring(0, Math.min(length, digest.length()));
        } catch(NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private enum SkipValue { INSTANCE }

    private record Allocation(CanonicalModelElement source, TransformationRule rule, String targetAlias, String targetTypeId, String targetElementId) { }
}
