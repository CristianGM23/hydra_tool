package com.example.editor.project.recovery;

import java.util.List;

/** Evidencia explicable que relaciona un elemento externo con una capacidad neutral. */
public record ExternalProjectMappingEvidence(String id, String sourcePath, String sourceLocator,
        String sourceElement, String targetKind, String targetId, String matchType, int score,
        ExternalProjectConfidence confidence, ExternalProjectFidelity fidelity,
        List<String> recoveredFields, List<String> lostFields, List<String> alternatives,
        List<String> reasons, boolean autoSelectable) {
    public ExternalProjectMappingEvidence {
        id = id == null ? "" : id;
        sourcePath = sourcePath == null ? "" : sourcePath;
        sourceLocator = sourceLocator == null ? "" : sourceLocator;
        sourceElement = sourceElement == null ? "" : sourceElement;
        targetKind = targetKind == null ? "" : targetKind;
        targetId = targetId == null ? "" : targetId;
        matchType = matchType == null ? "" : matchType;
        score = Math.max(0, Math.min(100, score));
        confidence = confidence == null ? ExternalProjectConfidence.INFERRED : confidence;
        fidelity = fidelity == null ? ExternalProjectFidelity.PARTIAL : fidelity;
        recoveredFields = recoveredFields == null ? List.of() : List.copyOf(recoveredFields);
        lostFields = lostFields == null ? List.of() : List.copyOf(lostFields);
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    /** Devuelve una copia con alternativas y decisión automática ya reconciliadas. */
    /** Descripción: Ejecuta la operación «reconcile» correspondiente a la responsabilidad de ExternalProjectMappingEvidence. Entradas: nextAlternatives: dato de entrada necesario para la operación; safe: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExternalProjectMappingEvidence reconcile(List<String> nextAlternatives, boolean safe) {
        return new ExternalProjectMappingEvidence(id, sourcePath, sourceLocator, sourceElement,
                targetKind, targetId, matchType, score, confidence, fidelity, recoveredFields,
                lostFields, nextAlternatives, reasons, safe);
    }
}
