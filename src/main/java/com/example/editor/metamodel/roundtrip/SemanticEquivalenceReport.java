package com.example.editor.metamodel.roundtrip;

import java.util.List;

/** Resultado neutral de comparar dos metamodelos sin depender de sus identificadores regenerados. */
public record SemanticEquivalenceReport(SemanticFidelity fidelity, List<SemanticDifference> differences) {
    public SemanticEquivalenceReport {
        fidelity = fidelity == null ? SemanticFidelity.UNAVAILABLE : fidelity;
        differences = differences == null ? List.of() : List.copyOf(differences);
    }
    /** Descripción: Indica si la comparación conserva todo el significado estructural aceptado por HYDRA. Entradas: no requiere parámetros de entrada. Salida: devuelve true cuando la fidelidad es EXACT o HIGH y, por tanto, no se ha declarado pérdida semántica. */
    public boolean semanticallyEquivalent() { return fidelity.lossless(); }
    /** Descripción: Comprueba si ambos metamodelos son semánticamente idénticos según el comparador neutral. Entradas: no requiere parámetros de entrada. Salida: devuelve true únicamente cuando la fidelidad calculada es EXACT. */
    public boolean exact() { return fidelity == SemanticFidelity.EXACT; }
}
