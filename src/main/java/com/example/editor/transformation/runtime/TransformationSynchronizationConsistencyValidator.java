package com.example.editor.transformation.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Valida la identidad, versión y unicidad de una baseline incremental antes de propagar cambios. */
final class TransformationSynchronizationConsistencyValidator {
    /** Descripción: Valida baseline, modelos actuales y traza para impedir sincronizaciones sobre una historia incompatible o ambigua. Entradas: request: solicitud incremental. Salida: no devuelve valor y falla cerrado si detecta inconsistencia. */
    void validate(TransformationSynchronizationRequest request) {
        CanonicalModelDocument sourceBaseline = request.baselineSourceModel();
        CanonicalModelDocument targetBaseline = request.baselineTargetModel();
        CanonicalModelDocument source = request.sourceModel();
        CanonicalModelDocument target = request.targetModel();
        requireIdentity(sourceBaseline, source, "source");
        requireIdentity(targetBaseline, target, "target");
        requireMetamodelRevision(sourceBaseline, source, "source");
        requireMetamodelRevision(targetBaseline, target, "target");
        if (request.baselineTrace() != null) validateTrace(request.baselineTrace(), sourceBaseline, targetBaseline);
    }

    /** Descripción: Exige que baseline y modelo actual representen el mismo documento y metamodelo. Entradas: baseline: modelo base; current: modelo actual; side: lado lógico. Salida: no devuelve valor. */
    private void requireIdentity(CanonicalModelDocument baseline, CanonicalModelDocument current, String side) {
        if (!baseline.id().equals(current.id())) throw new TransformationExecutionException("Incremental " + side + " model id differs from its baseline; rebase the synchronization session");
        if (!baseline.metamodelId().equals(current.metamodelId())) throw new TransformationExecutionException("Incremental " + side + " metamodel differs from its baseline");
    }

    /** Descripción: Impide reutilizar una baseline cuando el fingerprint/ETag del metamodelo cambió. Entradas: baseline: modelo base; current: modelo actual; side: lado lógico. Salida: no devuelve valor. */
    private void requireMetamodelRevision(CanonicalModelDocument baseline, CanonicalModelDocument current, String side) {
        String before = text(baseline.metamodelEtag());
        String now = text(current.metamodelEtag());
        if (!before.isBlank() && !now.isBlank() && !before.equals(now)) {
            throw new TransformationExecutionException("Incremental " + side + " metamodel revision changed after the baseline; rebase before synchronizing");
        }
    }

    /** Descripción: Comprueba orientación, referencias existentes y correspondencias uno-a-uno de la traza baseline. Entradas: trace: traza; source: baseline origen; target: baseline destino. Salida: no devuelve valor. */
    private void validateTrace(TransformationTrace trace, CanonicalModelDocument source, CanonicalModelDocument target) {
        if (trace.direction() == TransformationExecutionDirection.FORWARD) {
            requireTraceModels(trace.sourceModelId(), trace.targetModelId(), source.id(), target.id());
        } else {
            requireTraceModels(trace.sourceModelId(), trace.targetModelId(), target.id(), source.id());
        }
        Set<String> sourceIds = elementIds(source);
        Set<String> targetIds = elementIds(target);
        Map<String, String> bySource = new HashMap<>();
        Map<String, String> byTarget = new HashMap<>();
        for (TransformationTraceLink link : trace.links()) {
            String canonicalSource = link.direction() == TransformationExecutionDirection.REVERSE ? link.targetElementId() : link.sourceElementId();
            String canonicalTarget = link.direction() == TransformationExecutionDirection.REVERSE ? link.sourceElementId() : link.targetElementId();
            if (!sourceIds.contains(canonicalSource)) throw new TransformationExecutionException("Baseline trace references missing source element: " + canonicalSource);
            if (!targetIds.contains(canonicalTarget)) throw new TransformationExecutionException("Baseline trace references missing target element: " + canonicalTarget);
            String sourceKey = link.correspondenceId() + "|" + canonicalSource;
            String targetKey = link.correspondenceId() + "|" + canonicalTarget;
            String previousTarget = bySource.putIfAbsent(sourceKey, canonicalTarget);
            if (previousTarget != null && !previousTarget.equals(canonicalTarget)) {
                throw new TransformationExecutionException("Baseline trace contains ambiguous source correspondence: " + link.correspondenceId() + " / " + canonicalSource);
            }
            String previousSource = byTarget.putIfAbsent(targetKey, canonicalSource);
            if (previousSource != null && !previousSource.equals(canonicalSource)) {
                throw new TransformationExecutionException("Baseline trace contains ambiguous target correspondence: " + link.correspondenceId() + " / " + canonicalTarget);
            }
        }
    }

    /** Descripción: Comprueba que la cabecera de traza se refiere a los modelos baseline en la orientación esperada. Entradas: actualSource: id origen de traza; actualTarget: id destino; expectedSource: id esperado; expectedTarget: id esperado. Salida: no devuelve valor. */
    private void requireTraceModels(String actualSource, String actualTarget, String expectedSource, String expectedTarget) {
        if (!expectedSource.equals(actualSource) || !expectedTarget.equals(actualTarget)) {
            throw new TransformationExecutionException("Baseline trace model identities do not match the synchronization baseline; rebase before synchronizing");
        }
    }

    /** Descripción: Indexa identificadores de elementos de un modelo baseline. Entradas: model: modelo. Salida: conjunto de ids. */
    private Set<String> elementIds(CanonicalModelDocument model) {
        Set<String> ids = new HashSet<>();
        model.elements().forEach(element -> ids.add(element.id()));
        return ids;
    }

    /** Descripción: Normaliza textos opcionales usados por el validador. Entradas: value: valor. Salida: cadena no nula. */
    private String text(String value) { return value == null ? "" : value.trim(); }
}
