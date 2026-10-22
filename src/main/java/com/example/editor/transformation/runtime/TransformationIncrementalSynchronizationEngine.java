package com.example.editor.transformation.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationConflictPolicy;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationRuleDirection;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;

/**
 * Motor neutral de sincronización incremental bidireccional.
 *
 * Calcula deltas contra una baseline, reutiliza correspondencias de traza y mezcla
 * únicamente los elementos/features afectados. No conoce ATL, ETL ni ningún backend
 * tecnológico concreto.
 */
final class TransformationIncrementalSynchronizationEngine {

    /** Descripción: Actualiza el estado gestionado por TraceIndex mediante la operación «synchronize». Entradas: document: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; executor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ TransformationSynchronizationResult synchronize(TransformationDefinitionDocument document,
            TransformationSynchronizationRequest request,
            BiFunction<CanonicalModelDocument, TransformationExecutionDirection, TransformationExecutionResult> executor) {
        requireBaseline(request);
        new TransformationSynchronizationConsistencyValidator().validate(request);
        TransformationModelDelta sourceDelta = delta(request.baselineSourceModel(), request.sourceModel());
        TransformationModelDelta targetDelta = delta(request.baselineTargetModel(), request.targetModel());
        boolean sourceChanged = !sourceDelta.emptyDelta();
        boolean targetChanged = !targetDelta.emptyDelta();
        if(!sourceChanged && !targetChanged) {
            return result(request.sourceModel(), request.targetModel(), TransformationExecutionDirection.FORWARD, "NO_CHANGES",
                    request.baselineTrace(), List.of(), sourceDelta, targetDelta, List.of(), false, true);
        }
        validateDeclaredSide(request.changedSide(), sourceChanged, targetChanged);
        requireTraceWhenNeeded(document, request, sourceDelta, targetDelta);
        TraceIndex baseline = TraceIndex.from(request.baselineTrace());
        List<TransformationSynchronizationConflict> conflicts = sourceChanged && targetChanged
                ? conflicts(document, request, sourceDelta, targetDelta, baseline) : List.of();
        TransformationConflictPolicy policy = document.synchronizationPolicy().conflictPolicy();
        if(!conflicts.isEmpty() && policy == TransformationConflictPolicy.FAIL_ON_CONFLICT) {
            return result(request.sourceModel(), request.targetModel(), TransformationExecutionDirection.FORWARD,
                    "BLOCKED_BY_CONFLICT", request.baselineTrace(), List.of(), sourceDelta, targetDelta, conflicts, true, false);
        }

        Set<String> ignoredSource = new LinkedHashSet<>();
        Set<String> ignoredTarget = new LinkedHashSet<>();
        if(!conflicts.isEmpty()) {
            for(TransformationSynchronizationConflict conflict : conflicts) {
                if(policy == TransformationConflictPolicy.FORWARD_WINS) ignoredTarget.add(conflict.targetElementId());
                else if(policy == TransformationConflictPolicy.REVERSE_WINS) ignoredSource.add(conflict.sourceElementId());
            }
        }

        CanonicalModelDocument source = request.sourceModel();
        CanonicalModelDocument target = request.targetModel();
        TransformationTrace trace = request.baselineTrace();
        List<TransformationExecutionDirection> directions = new ArrayList<>();
        String resolution = conflicts.isEmpty() ? "MERGED_NON_CONFLICTING_DELTAS" : policy.name();

        if(policy == TransformationConflictPolicy.REVERSE_WINS && targetChanged) {
            Propagation reverse = propagate(document, TransformationExecutionDirection.REVERSE, target, source,
                    request.baselineTargetModel(), request.baselineSourceModel(), targetDelta, ignoredTarget, trace, executor);
            source = reverse.output(); trace = reverse.trace(); if(reverse.applied()) directions.add(TransformationExecutionDirection.REVERSE);
        }
        if(sourceChanged) {
            Propagation forward = propagate(document, TransformationExecutionDirection.FORWARD, source, target,
                    request.baselineSourceModel(), request.baselineTargetModel(), sourceDelta, ignoredSource, trace, executor);
            target = forward.output(); trace = forward.trace(); if(forward.applied()) directions.add(TransformationExecutionDirection.FORWARD);
        }
        if(policy != TransformationConflictPolicy.REVERSE_WINS && targetChanged) {
            Propagation reverse = propagate(document, TransformationExecutionDirection.REVERSE, target, source,
                    request.baselineTargetModel(), request.baselineSourceModel(), targetDelta, ignoredTarget, trace, executor);
            source = reverse.output(); trace = reverse.trace(); if(reverse.applied()) directions.add(TransformationExecutionDirection.REVERSE);
        }
        TransformationExecutionDirection primary = directions.isEmpty() ? TransformationExecutionDirection.FORWARD : directions.getLast();
        return result(source, target, primary, resolution, trace, directions, sourceDelta, targetDelta, conflicts, false, false);
    }

    /** Descripción: Ejecuta la operación «propagate» correspondiente a la responsabilidad de TraceIndex. Entradas: document: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación; input: valor de entrada que debe procesarse; currentOutput: dato de entrada necesario para la operación; baselineInput: dato de entrada necesario para la operación; baselineOutput: dato de entrada necesario para la operación; inputDelta: dato de entrada necesario para la operación; ignoredInputIds: dato de entrada necesario para la operación; baselineTrace: dato de entrada necesario para la operación; executor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Propagation propagate(TransformationDefinitionDocument document, TransformationExecutionDirection direction,
            CanonicalModelDocument input, CanonicalModelDocument currentOutput,
            CanonicalModelDocument baselineInput, CanonicalModelDocument baselineOutput,
            TransformationModelDelta inputDelta, Set<String> ignoredInputIds, TransformationTrace baselineTrace,
            BiFunction<CanonicalModelDocument, TransformationExecutionDirection, TransformationExecutionResult> executor) {
        Set<String> affectedInputIds = affectedElementIds(inputDelta, baselineInput, input);
        affectedInputIds.removeAll(ignoredInputIds);
        if(affectedInputIds.isEmpty()) return new Propagation(currentOutput, baselineTrace, false);

        TransformationExecutionResult candidateExecution = executor.apply(input, direction);
        CanonicalModelDocument candidate = candidateExecution.targetModel();
        TransformationTrace candidateTrace = candidateExecution.trace();
        TraceIndex existing = TraceIndex.from(baselineTrace);
        TraceIndex generated = TraceIndex.from(candidateTrace);
        Map<String,String> outputIdRemap = outputIdRemap(direction, existing, generated);

        Set<String> affectedOutputIds = new LinkedHashSet<>();
        for(TracePair pair : generated.pairs()) {
            String inputId = direction == TransformationExecutionDirection.FORWARD ? pair.sourceId() : pair.targetId();
            if(!affectedInputIds.contains(inputId)) continue;
            String generatedOutputId = direction == TransformationExecutionDirection.FORWARD ? pair.targetId() : pair.sourceId();
            affectedOutputIds.add(outputIdRemap.getOrDefault(generatedOutputId, generatedOutputId));
        }
        Set<String> removedInputIds = new LinkedHashSet<>(inputDelta.removedElementIds());
        removedInputIds.removeAll(ignoredInputIds);
        Set<String> removedOutputIds = mappedExistingOutputIds(direction, existing, removedInputIds);
        if(document.synchronizationPolicy().propagateDeletions()) affectedOutputIds.addAll(removedOutputIds);

        Map<String,CanonicalModelElement> mergedElements = indexElements(currentOutput.elements());
        Map<String,CanonicalModelElement> candidateElements = indexElements(candidate.elements());
        Map<String,Set<String>> featureSelection = selectedOutputFeatures(document, direction, inputDelta, affectedInputIds, existing);
        for(TracePair pair : generated.pairs()) {
            String inputId = direction == TransformationExecutionDirection.FORWARD ? pair.sourceId() : pair.targetId();
            if(!affectedInputIds.contains(inputId)) continue;
            String generatedOutputId = direction == TransformationExecutionDirection.FORWARD ? pair.targetId() : pair.sourceId();
            CanonicalModelElement generatedElement = candidateElements.get(generatedOutputId);
            if(generatedElement == null) continue;
            String outputId = outputIdRemap.getOrDefault(generatedOutputId, generatedOutputId);
            CanonicalModelElement previous = mergedElements.get(outputId);
            Set<String> selected = featureSelection.get(selectionKey(inputId, pair.correspondenceId()));
            mergedElements.put(outputId, mergeElement(outputId, previous, generatedElement, selected));
        }
        if(document.synchronizationPolicy().propagateDeletions()) removedOutputIds.forEach(mergedElements::remove);

        List<CanonicalModelRelation> mergedRelations = mergeRelations(currentOutput, candidate, affectedOutputIds,
                removedOutputIds, outputIdRemap, document.synchronizationPolicy().propagateDeletions());
        List<String> roots = mergeRoots(currentOutput.rootElementIds(), candidate.rootElementIds(), affectedOutputIds,
                removedOutputIds, outputIdRemap, document.synchronizationPolicy().propagateDeletions());
        Map<String,String> annotations = new LinkedHashMap<>(currentOutput.annotations());
        annotations.put("hydra.transformation.sync.mode", TransformationSynchronizationMode.INCREMENTAL.name());
        annotations.put("hydra.transformation.sync.direction", direction.name());
        CanonicalModelDocument output = new CanonicalModelDocument(currentOutput.schemaVersion(), currentOutput.id(),
                currentOutput.metamodelId(), currentOutput.metamodelEtag(), currentOutput.name(), roots,
                mergedElements.values().stream().sorted(Comparator.comparing(CanonicalModelElement::id)).toList(),
                mergedRelations, annotations);
        TransformationTrace mergedTrace = mergeTrace(direction, baselineTrace, candidateTrace, affectedInputIds,
                removedInputIds, outputIdRemap, document.synchronizationPolicy().propagateDeletions());
        return new Propagation(output, mergedTrace, true);
    }

    /** Descripción: Actualiza el estado gestionado por TraceIndex mediante la operación «mergeTrace». Entradas: direction: dato de entrada necesario para la operación; baseline: dato de entrada necesario para la operación; candidate: dato de entrada necesario para la operación; affectedInputIds: dato de entrada necesario para la operación; removedInputIds: dato de entrada necesario para la operación; outputIdRemap: dato de entrada necesario para la operación; propagateDeletions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationTrace mergeTrace(TransformationExecutionDirection direction, TransformationTrace baseline,
            TransformationTrace candidate, Set<String> affectedInputIds, Set<String> removedInputIds,
            Map<String,String> outputIdRemap, boolean propagateDeletions) {
        List<TransformationTraceLink> links = new ArrayList<>();
        Set<String> candidateInputs = new LinkedHashSet<>();
        for(TransformationTraceLink link : candidate.links()) {
            if(affectedInputIds.contains(link.sourceElementId())) candidateInputs.add(link.sourceElementId());
        }
        if(baseline != null) {
            for(TransformationTraceLink link : baseline.links()) {
                String canonicalInput = direction == TransformationExecutionDirection.FORWARD
                        ? canonicalSource(link) : canonicalTarget(link);
                if(affectedInputIds.contains(canonicalInput)) {
                    boolean removed = removedInputIds.contains(canonicalInput);
                    boolean replaced = candidateInputs.contains(canonicalInput);
                    if(replaced || (removed && propagateDeletions)) continue;
                }
                links.add(link);
            }
        }
        for(TransformationTraceLink link : candidate.links()) {
            String executionInput = link.sourceElementId();
            if(!affectedInputIds.contains(executionInput)) continue;
            String targetId = outputIdRemap.getOrDefault(link.targetElementId(), link.targetElementId());
            links.add(new TransformationTraceLink(link.sourceElementId(), link.ruleId(), link.targetAlias(), targetId,
                    link.correspondenceId(), link.direction()));
        }
        List<TransformationBindingTrace> bindings = new ArrayList<>();
        if(baseline != null) bindings.addAll(baseline.bindingTraces());
        for(TransformationBindingTrace binding : candidate.bindingTraces()) {
            if(!affectedInputIds.contains(binding.sourceElementId())) continue;
            bindings.add(new TransformationBindingTrace(binding.ruleId(), binding.bindingId(), binding.kind(),
                    binding.sourceElementId(), outputIdRemap.getOrDefault(binding.targetElementId(), binding.targetElementId()),
                    binding.sourceFeatureId(), binding.targetFeatureId()));
        }
        return new TransformationTrace(candidate.executionId(), candidate.transformationId(), candidate.sourceModelId(),
                candidate.targetModelId(), distinctLinks(links), distinctBindings(bindings), direction);
    }

    /** Descripción: Ejecuta la operación «conflicts» correspondiente a la responsabilidad de TraceIndex. Entradas: document: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; sourceDelta: dato de entrada necesario para la operación; targetDelta: dato de entrada necesario para la operación; trace: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<TransformationSynchronizationConflict> conflicts(TransformationDefinitionDocument document,
            TransformationSynchronizationRequest request, TransformationModelDelta sourceDelta,
            TransformationModelDelta targetDelta, TraceIndex trace) {
        Set<String> sourceAffected = affectedElementIds(sourceDelta, request.baselineSourceModel(), request.sourceModel());
        Set<String> targetAffected = affectedElementIds(targetDelta, request.baselineTargetModel(), request.targetModel());
        List<TransformationSynchronizationConflict> result = new ArrayList<>();
        for(TracePair pair : trace.pairs()) {
            if(!sourceAffected.contains(pair.sourceId()) || !targetAffected.contains(pair.targetId())) continue;
            List<String> sourceFeatures = sourceDelta.modifiedAttributeIds().getOrDefault(pair.sourceId(), List.of());
            List<String> targetFeatures = targetDelta.modifiedAttributeIds().getOrDefault(pair.targetId(), List.of());
            boolean structural = structuralChange(sourceDelta, pair.sourceId(), request.baselineSourceModel(), request.sourceModel())
                    || structuralChange(targetDelta, pair.targetId(), request.baselineTargetModel(), request.targetModel());
            if(structural || featureConflict(document, pair.correspondenceId(), sourceFeatures, targetFeatures)) {
                result.add(new TransformationSynchronizationConflict(pair.correspondenceId(), pair.sourceId(), pair.targetId(),
                        sourceFeatures, targetFeatures, structural ? "Concurrent structural change" : "Concurrent feature change"));
            }
        }
        addAmbiguousConcurrentAdditions(document, request, sourceDelta, targetDelta, result);
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «addAmbiguousConcurrentAdditions» correspondiente a la responsabilidad de TraceIndex. Entradas: document: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; sourceDelta: dato de entrada necesario para la operación; targetDelta: dato de entrada necesario para la operación; conflicts: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addAmbiguousConcurrentAdditions(TransformationDefinitionDocument document,
            TransformationSynchronizationRequest request, TransformationModelDelta sourceDelta,
            TransformationModelDelta targetDelta, List<TransformationSynchronizationConflict> conflicts) {
        if(sourceDelta.addedElementIds().isEmpty() || targetDelta.addedElementIds().isEmpty()) return;
        Map<String,CanonicalModelElement> source = indexElements(request.sourceModel().elements());
        Map<String,CanonicalModelElement> target = indexElements(request.targetModel().elements());
        for(String sourceId : sourceDelta.addedElementIds()) {
            CanonicalModelElement sourceElement = source.get(sourceId);
            if(sourceElement == null) continue;
            for(TransformationRule rule : document.rules()) {
                if(!rule.enabled() || rule.direction() != TransformationRuleDirection.FORWARD
                        || !rule.sourceTypeId().equals(sourceElement.classId())) continue;
                Set<String> targetTypes = new LinkedHashSet<>();
                rule.targets().forEach(pattern -> targetTypes.add(pattern.typeId()));
                for(String targetId : targetDelta.addedElementIds()) {
                    CanonicalModelElement targetElement = target.get(targetId);
                    if(targetElement == null || !targetTypes.contains(targetElement.classId())) continue;
                    conflicts.add(new TransformationSynchronizationConflict(rule.correspondenceId(), sourceId, targetId,
                            List.of(), List.of(), "Concurrent untraced additions are ambiguous"));
                }
            }
        }
    }

    /** Descripción: Ejecuta la operación «featureConflict» correspondiente a la responsabilidad de TraceIndex. Entradas: document: dato de entrada necesario para la operación; correspondenceId: identificador que delimita el recurso objetivo; sourceFeatures: dato de entrada necesario para la operación; targetFeatures: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean featureConflict(TransformationDefinitionDocument document, String correspondenceId,
            List<String> sourceFeatures, List<String> targetFeatures) {
        if(sourceFeatures.isEmpty() || targetFeatures.isEmpty()) return true;
        Set<String> target = Set.copyOf(targetFeatures);
        boolean sawExactMapping = false;
        for(TransformationRule rule : document.rules()) {
            if(!rule.enabled() || rule.direction() != TransformationRuleDirection.FORWARD
                    || !rule.correspondenceId().equals(correspondenceId)) continue;
            for(TransformationBinding binding : rule.bindings()) {
                if(binding.kind() != TransformationBindingKind.DIRECT || binding.sourceFeatureId().isBlank()) {
                    if(!sourceFeatures.isEmpty()) return true;
                    continue;
                }
                if(sourceFeatures.contains(binding.sourceFeatureId())) {
                    sawExactMapping = true;
                    if(target.contains(binding.targetFeatureId())) return true;
                }
            }
        }
        return !sawExactMapping;
    }

    /** Descripción: Ejecuta la operación «selectedOutputFeatures» correspondiente a la responsabilidad de TraceIndex. Entradas: document: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación; delta: dato de entrada necesario para la operación; affectedInputIds: dato de entrada necesario para la operación; trace: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,Set<String>> selectedOutputFeatures(TransformationDefinitionDocument document,
            TransformationExecutionDirection direction, TransformationModelDelta delta, Set<String> affectedInputIds,
            TraceIndex trace) {
        Map<String,Set<String>> result = new LinkedHashMap<>();
        for(TracePair pair : trace.pairs()) {
            String inputId = direction == TransformationExecutionDirection.FORWARD ? pair.sourceId() : pair.targetId();
            if(!affectedInputIds.contains(inputId)) continue;
            List<String> changed = delta.modifiedAttributeIds().getOrDefault(inputId, List.of());
            if(changed.isEmpty()) continue;
            TransformationRuleDirection ruleDirection = direction == TransformationExecutionDirection.FORWARD
                    ? TransformationRuleDirection.FORWARD : TransformationRuleDirection.REVERSE;
            Set<String> selected = new LinkedHashSet<>();
            boolean unknownDependency = false;
            for(TransformationRule rule : document.rules()) {
                if(!rule.enabled() || rule.direction() != ruleDirection || !rule.correspondenceId().equals(pair.correspondenceId())) continue;
                for(TransformationBinding binding : rule.bindings()) {
                    if(binding.kind() == TransformationBindingKind.DIRECT && changed.contains(binding.sourceFeatureId())) selected.add(binding.targetFeatureId());
                    else if(binding.kind() == TransformationBindingKind.EXPRESSION) unknownDependency = true;
                }
                if(rule.guard() != null) unknownDependency = true;
            }
            if(!unknownDependency) result.put(selectionKey(inputId, pair.correspondenceId()), selected);
        }
        return result;
    }

    /** Descripción: Actualiza el estado gestionado por TraceIndex mediante la operación «mergeElement». Entradas: outputId: identificador que delimita el recurso objetivo; previous: dato de entrada necesario para la operación; generated: dato de entrada necesario para la operación; selectedFeatures: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private CanonicalModelElement mergeElement(String outputId, CanonicalModelElement previous,
            CanonicalModelElement generated, Set<String> selectedFeatures) {
        Map<String,Object> attributes;
        if(previous == null || selectedFeatures == null) attributes = generated.attributes();
        else if(selectedFeatures.isEmpty()) attributes = previous.attributes();
        else {
            attributes = new LinkedHashMap<>(previous.attributes());
            for(String feature : selectedFeatures) {
                if(generated.attributes().containsKey(feature)) attributes.put(feature, generated.attributes().get(feature));
                else attributes.remove(feature);
            }
        }
        Map<String,String> annotations = new LinkedHashMap<>();
        if(previous != null) annotations.putAll(previous.annotations());
        annotations.putAll(generated.annotations());
        return new CanonicalModelElement(outputId, generated.classId(), attributes, annotations);
    }

    /** Descripción: Actualiza el estado gestionado por TraceIndex mediante la operación «mergeRelations». Entradas: current: dato de entrada necesario para la operación; candidate: dato de entrada necesario para la operación; affectedOutputIds: dato de entrada necesario para la operación; removedOutputIds: dato de entrada necesario para la operación; outputIdRemap: dato de entrada necesario para la operación; propagateDeletions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<CanonicalModelRelation> mergeRelations(CanonicalModelDocument current, CanonicalModelDocument candidate,
            Set<String> affectedOutputIds, Set<String> removedOutputIds, Map<String,String> outputIdRemap, boolean propagateDeletions) {
        Map<String,CanonicalModelRelation> result = new LinkedHashMap<>();
        for(CanonicalModelRelation relation : current.relations()) {
            boolean touches = affectedOutputIds.contains(relation.sourceElementId()) || affectedOutputIds.contains(relation.targetElementId());
            boolean removed = removedOutputIds.contains(relation.sourceElementId()) || removedOutputIds.contains(relation.targetElementId());
            if(touches || (propagateDeletions && removed)) continue;
            result.put(relationKey(relation), relation);
        }
        for(CanonicalModelRelation relation : candidate.relations()) {
            String source = outputIdRemap.getOrDefault(relation.sourceElementId(), relation.sourceElementId());
            String target = outputIdRemap.getOrDefault(relation.targetElementId(), relation.targetElementId());
            if(!affectedOutputIds.contains(source) && !affectedOutputIds.contains(target)) continue;
            CanonicalModelRelation remapped = new CanonicalModelRelation(relation.id(), relation.relationId(), source, target, relation.annotations());
            result.put(relationKey(remapped), remapped);
        }
        return result.values().stream().sorted(Comparator.comparing(CanonicalModelRelation::id)).toList();
    }

    /** Descripción: Actualiza el estado gestionado por TraceIndex mediante la operación «mergeRoots». Entradas: currentRoots: dato de entrada necesario para la operación; candidateRoots: dato de entrada necesario para la operación; affectedOutputIds: dato de entrada necesario para la operación; removedOutputIds: dato de entrada necesario para la operación; outputIdRemap: dato de entrada necesario para la operación; propagateDeletions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> mergeRoots(List<String> currentRoots, List<String> candidateRoots, Set<String> affectedOutputIds,
            Set<String> removedOutputIds, Map<String,String> outputIdRemap, boolean propagateDeletions) {
        LinkedHashSet<String> roots = new LinkedHashSet<>(currentRoots);
        if(propagateDeletions) roots.removeAll(removedOutputIds);
        roots.removeAll(affectedOutputIds);
        for(String candidateRoot : candidateRoots) {
            String remapped = outputIdRemap.getOrDefault(candidateRoot, candidateRoot);
            if(affectedOutputIds.contains(remapped)) roots.add(remapped);
        }
        return List.copyOf(roots);
    }

    /** Descripción: Ejecuta la operación «outputIdRemap» correspondiente a la responsabilidad de TraceIndex. Entradas: direction: dato de entrada necesario para la operación; baseline: dato de entrada necesario para la operación; candidate: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,String> outputIdRemap(TransformationExecutionDirection direction, TraceIndex baseline, TraceIndex candidate) {
        Map<String,String> result = new LinkedHashMap<>();
        for(TracePair generated : candidate.pairs()) {
            if(direction == TransformationExecutionDirection.FORWARD) {
                List<TracePair> matches = baseline.bySourceAndCorrespondence(generated.sourceId(), generated.correspondenceId());
                if(matches.size() == 1) result.put(generated.targetId(), matches.getFirst().targetId());
            } else {
                List<TracePair> matches = baseline.byTargetAndCorrespondence(generated.targetId(), generated.correspondenceId());
                if(matches.size() == 1) result.put(generated.sourceId(), matches.getFirst().sourceId());
            }
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «mappedExistingOutputIds» correspondiente a la responsabilidad de TraceIndex. Entradas: direction: dato de entrada necesario para la operación; baseline: dato de entrada necesario para la operación; removedInputs: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Set<String> mappedExistingOutputIds(TransformationExecutionDirection direction, TraceIndex baseline, Set<String> removedInputs) {
        Set<String> result = new LinkedHashSet<>();
        for(TracePair pair : baseline.pairs()) {
            if(direction == TransformationExecutionDirection.FORWARD && removedInputs.contains(pair.sourceId())) result.add(pair.targetId());
            if(direction == TransformationExecutionDirection.REVERSE && removedInputs.contains(pair.targetId())) result.add(pair.sourceId());
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «delta» correspondiente a la responsabilidad de TraceIndex. Entradas: baseline: dato de entrada necesario para la operación; current: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationModelDelta delta(CanonicalModelDocument baseline, CanonicalModelDocument current) {
        Map<String,CanonicalModelElement> before = indexElements(baseline.elements());
        Map<String,CanonicalModelElement> after = indexElements(current.elements());
        List<String> added = after.keySet().stream().filter(id -> !before.containsKey(id)).sorted().toList();
        List<String> removed = before.keySet().stream().filter(id -> !after.containsKey(id)).sorted().toList();
        List<String> modified = new ArrayList<>();
        Map<String,List<String>> modifiedAttributes = new LinkedHashMap<>();
        for(String id : before.keySet()) {
            CanonicalModelElement left = before.get(id), right = after.get(id);
            if(right == null) continue;
            LinkedHashSet<String> features = new LinkedHashSet<>();
            LinkedHashSet<String> keys = new LinkedHashSet<>(left.attributes().keySet()); keys.addAll(right.attributes().keySet());
            for(String key : keys) if(!Objects.deepEquals(left.attributes().get(key), right.attributes().get(key))) features.add(key);
            if(!left.classId().equals(right.classId())) features.add("$class");
            if(!features.isEmpty() || !left.annotations().equals(right.annotations())) {
                modified.add(id);
                if(!features.isEmpty()) modifiedAttributes.put(id, List.copyOf(features));
            }
        }
        Map<String,CanonicalModelRelation> beforeRelations = indexRelations(baseline.relations());
        Map<String,CanonicalModelRelation> afterRelations = indexRelations(current.relations());
        List<String> addedRelations = afterRelations.keySet().stream().filter(id -> !beforeRelations.containsKey(id)).sorted().toList();
        List<String> removedRelations = beforeRelations.keySet().stream().filter(id -> !afterRelations.containsKey(id)).sorted().toList();
        List<String> modifiedRelations = beforeRelations.keySet().stream().filter(afterRelations::containsKey)
                .filter(id -> !beforeRelations.get(id).equals(afterRelations.get(id))).sorted().toList();
        return new TransformationModelDelta(added, removed, modified.stream().sorted().toList(), modifiedAttributes,
                addedRelations, removedRelations, modifiedRelations);
    }

    /** Descripción: Ejecuta la operación «affectedElementIds» correspondiente a la responsabilidad de TraceIndex. Entradas: delta: dato de entrada necesario para la operación; baseline: dato de entrada necesario para la operación; current: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Set<String> affectedElementIds(TransformationModelDelta delta, CanonicalModelDocument baseline, CanonicalModelDocument current) {
        Set<String> result = new LinkedHashSet<>();
        result.addAll(delta.addedElementIds()); result.addAll(delta.removedElementIds()); result.addAll(delta.modifiedElementIds());
        Map<String,CanonicalModelRelation> before = indexRelations(baseline.relations());
        Map<String,CanonicalModelRelation> after = indexRelations(current.relations());
        for(String id : concat(delta.addedRelationIds(), delta.removedRelationIds(), delta.modifiedRelationIds())) {
            CanonicalModelRelation relation = after.getOrDefault(id, before.get(id));
            if(relation != null) { result.add(relation.sourceElementId()); result.add(relation.targetElementId()); }
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «structuralChange» correspondiente a la responsabilidad de TraceIndex. Entradas: delta: dato de entrada necesario para la operación; elementId: identificador que delimita el recurso objetivo; baseline: dato de entrada necesario para la operación; current: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean structuralChange(TransformationModelDelta delta, String elementId, CanonicalModelDocument baseline, CanonicalModelDocument current) {
        if(delta.addedElementIds().contains(elementId) || delta.removedElementIds().contains(elementId)) return true;
        Set<String> affected = affectedElementIds(new TransformationModelDelta(List.of(), List.of(), List.of(), Map.of(),
                delta.addedRelationIds(), delta.removedRelationIds(), delta.modifiedRelationIds()), baseline, current);
        return affected.contains(elementId);
    }

    /** Descripción: Valida las condiciones necesarias para «requireBaseline» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireBaseline(TransformationSynchronizationRequest request) {
        if(request.baselineSourceModel() == null || request.baselineTargetModel() == null)
            throw new TransformationExecutionException("Incremental synchronization requires source and target baseline models");
        if(!request.baselineSourceModel().metamodelId().equals(request.sourceModel().metamodelId())
                || !request.baselineTargetModel().metamodelId().equals(request.targetModel().metamodelId()))
            throw new TransformationExecutionException("Incremental synchronization baseline metamodels do not match the current models");
    }

    /** Descripción: Valida las condiciones necesarias para «requireTraceWhenNeeded» y rechaza entradas no válidas. Entradas: document: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; sourceDelta: dato de entrada necesario para la operación; targetDelta: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireTraceWhenNeeded(TransformationDefinitionDocument document, TransformationSynchronizationRequest request,
            TransformationModelDelta sourceDelta, TransformationModelDelta targetDelta) {
        boolean existingUpdate = !sourceDelta.modifiedElementIds().isEmpty() || !sourceDelta.removedElementIds().isEmpty()
                || !targetDelta.modifiedElementIds().isEmpty() || !targetDelta.removedElementIds().isEmpty();
        if(request.baselineTrace() == null && (document.synchronizationPolicy().requireTraceForUpdates() || existingUpdate))
            throw new TransformationExecutionException("Incremental synchronization requires a previous transformation trace for updates or deletions");
    }

    /** Descripción: Valida las condiciones necesarias para «validateDeclaredSide» y rechaza entradas no válidas. Entradas: declared: dato de entrada necesario para la operación; sourceChanged: dato de entrada necesario para la operación; targetChanged: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateDeclaredSide(TransformationSynchronizationSide declared, boolean sourceChanged, boolean targetChanged) {
        if(declared == TransformationSynchronizationSide.SOURCE && targetChanged)
            throw new TransformationExecutionException("Target-side changes were detected although changedSide is SOURCE");
        if(declared == TransformationSynchronizationSide.TARGET && sourceChanged)
            throw new TransformationExecutionException("Source-side changes were detected although changedSide is TARGET");
        if(declared == TransformationSynchronizationSide.BOTH && (!sourceChanged || !targetChanged)) {
            // BOTH is accepted as a conservative user hint; detected deltas remain authoritative.
        }
    }

    /** Descripción: Ejecuta la operación «result» correspondiente a la responsabilidad de TraceIndex. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; primary: dato de entrada necesario para la operación; resolution: dato de entrada necesario para la operación; trace: dato de entrada necesario para la operación; directions: dato de entrada necesario para la operación; sourceDelta: dato de entrada necesario para la operación; targetDelta: dato de entrada necesario para la operación; conflicts: dato de entrada necesario para la operación; blocked: dato de entrada necesario para la operación; noOp: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationSynchronizationResult result(CanonicalModelDocument source, CanonicalModelDocument target,
            TransformationExecutionDirection primary, String resolution, TransformationTrace trace,
            List<TransformationExecutionDirection> directions, TransformationModelDelta sourceDelta,
            TransformationModelDelta targetDelta, List<TransformationSynchronizationConflict> conflicts,
            boolean blocked, boolean noOp) {
        return new TransformationSynchronizationResult(source, target, primary, resolution, trace,
                TransformationSynchronizationMode.INCREMENTAL, directions, sourceDelta, targetDelta, conflicts, blocked, noOp);
    }

    /** Descripción: Ejecuta la operación «indexElements» correspondiente a la responsabilidad de TraceIndex. Entradas: elements: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,CanonicalModelElement> indexElements(Collection<CanonicalModelElement> elements) {
        Map<String,CanonicalModelElement> result = new LinkedHashMap<>();
        for(CanonicalModelElement element : elements) result.put(element.id(), element);
        return result;
    }
    /** Descripción: Ejecuta la operación «indexRelations» correspondiente a la responsabilidad de TraceIndex. Entradas: relations: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,CanonicalModelRelation> indexRelations(Collection<CanonicalModelRelation> relations) {
        Map<String,CanonicalModelRelation> result = new LinkedHashMap<>();
        for(CanonicalModelRelation relation : relations) result.put(relation.id(), relation);
        return result;
    }
    /** Descripción: Ejecuta la operación «relationKey» correspondiente a la responsabilidad de TraceIndex. Entradas: relation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String relationKey(CanonicalModelRelation relation) {
        return relation.relationId()+"|"+relation.sourceElementId()+"|"+relation.targetElementId();
    }
    /** Descripción: Ejecuta la operación «selectionKey» correspondiente a la responsabilidad de TraceIndex. Entradas: inputId: identificador que delimita el recurso objetivo; correspondenceId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String selectionKey(String inputId, String correspondenceId) { return inputId+"\u0000"+correspondenceId; }
    /** Descripción: Ejecuta la operación «concat» correspondiente a la responsabilidad de TraceIndex. Entradas: first: dato de entrada necesario para la operación; second: dato de entrada necesario para la operación; third: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> concat(List<String> first, List<String> second, List<String> third) {
        List<String> result = new ArrayList<>(first.size() + second.size() + third.size());
        result.addAll(first); result.addAll(second); result.addAll(third); return result;
    }
    /** Descripción: Evalúa la condición «canonicalSource» sin modificar el estado externo. Entradas: link: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private String canonicalSource(TransformationTraceLink link) {
        return link.direction() == TransformationExecutionDirection.REVERSE ? link.targetElementId() : link.sourceElementId();
    }
    /** Descripción: Evalúa la condición «canonicalTarget» sin modificar el estado externo. Entradas: link: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private String canonicalTarget(TransformationTraceLink link) {
        return link.direction() == TransformationExecutionDirection.REVERSE ? link.sourceElementId() : link.targetElementId();
    }
    /** Descripción: Ejecuta la operación «distinctLinks» correspondiente a la responsabilidad de TraceIndex. Entradas: links: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<TransformationTraceLink> distinctLinks(List<TransformationTraceLink> links) {
        Map<String,TransformationTraceLink> result = new LinkedHashMap<>();
        for(TransformationTraceLink link : links) result.put(link.correspondenceId()+"|"+canonicalSource(link)+"|"+canonicalTarget(link), link);
        return List.copyOf(result.values());
    }
    /** Descripción: Ejecuta la operación «distinctBindings» correspondiente a la responsabilidad de TraceIndex. Entradas: bindings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<TransformationBindingTrace> distinctBindings(List<TransformationBindingTrace> bindings) {
        Map<String,TransformationBindingTrace> result = new LinkedHashMap<>();
        for(TransformationBindingTrace binding : bindings) result.put(binding.ruleId()+"|"+binding.bindingId()+"|"+binding.sourceElementId()+"|"+binding.targetElementId(), binding);
        return List.copyOf(result.values());
    }

    private record Propagation(CanonicalModelDocument output, TransformationTrace trace, boolean applied) { }
    private record TracePair(String sourceId, String targetId, String correspondenceId, String targetAlias) { }

    private static final class TraceIndex {
        private final List<TracePair> pairs;
        /** Descripción: Inicializa una instancia de TraceIndex con sus dependencias y datos de entrada. Entradas: pairs: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private TraceIndex(List<TracePair> pairs) { this.pairs = List.copyOf(pairs); }
        /** Descripción: Ejecuta la operación «from» correspondiente a la responsabilidad de TraceIndex. Entradas: trace: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static TraceIndex from(TransformationTrace trace) {
            if(trace == null) return new TraceIndex(List.of());
            List<TracePair> pairs = new ArrayList<>();
            for(TransformationTraceLink link : trace.links()) {
                String source = link.direction() == TransformationExecutionDirection.REVERSE ? link.targetElementId() : link.sourceElementId();
                String target = link.direction() == TransformationExecutionDirection.REVERSE ? link.sourceElementId() : link.targetElementId();
                pairs.add(new TracePair(source, target, link.correspondenceId(), link.targetAlias()));
            }
            return new TraceIndex(pairs);
        }
        /** Descripción: Ejecuta la operación «pairs» correspondiente a la responsabilidad de TraceIndex. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<TracePair> pairs() { return pairs; }
        /** Descripción: Ejecuta la operación «bySourceAndCorrespondence» correspondiente a la responsabilidad de TraceIndex. Entradas: source: dato de entrada necesario para la operación; correspondence: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ List<TracePair> bySourceAndCorrespondence(String source, String correspondence) {
            return pairs.stream().filter(pair -> pair.sourceId().equals(source) && pair.correspondenceId().equals(correspondence)).toList();
        }
        /** Descripción: Ejecuta la operación «byTargetAndCorrespondence» correspondiente a la responsabilidad de TraceIndex. Entradas: target: dato de entrada necesario para la operación; correspondence: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ List<TracePair> byTargetAndCorrespondence(String target, String correspondence) {
            return pairs.stream().filter(pair -> pair.targetId().equals(target) && pair.correspondenceId().equals(correspondence)).toList();
        }
    }
}
