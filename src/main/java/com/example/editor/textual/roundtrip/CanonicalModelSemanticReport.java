package com.example.editor.textual.roundtrip;

import java.util.List;

/** Resultado neutral de comparar dos instancias canónicas sin depender de IDs de relaciones regenerados. */
public record CanonicalModelSemanticReport(boolean equivalent, List<String> differences) {
    public CanonicalModelSemanticReport {
        differences = differences == null ? List.of() : List.copyOf(differences);
        equivalent = equivalent && differences.isEmpty();
    }
}
