package com.example.editor.grammar.diagnostic;

import java.util.Map;

/** Candidato ordenado para reparar una referencia sin acoplar HYDRA al proveedor nativo. */
public record ReferenceCandidate(
        String name,
        String symbolId,
        String kind,
        String documentId,
        double score,
        String reason,
        Map<String, Double> scoreBreakdown) {
    public ReferenceCandidate {
        name = text(name);
        symbolId = text(symbolId);
        kind = text(kind);
        documentId = text(documentId);
        score = Math.max(0d, Math.min(1d, score));
        reason = text(reason);
        scoreBreakdown = scoreBreakdown == null ? Map.of() : Map.copyOf(scoreBreakdown);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de ReferenceCandidate. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
