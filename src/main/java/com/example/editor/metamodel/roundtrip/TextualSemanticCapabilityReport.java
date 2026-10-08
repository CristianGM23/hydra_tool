package com.example.editor.metamodel.roundtrip;

import java.util.List;

/** Capacidad real de materializar el significado neutral en un runtime textual nativo. */
public record TextualSemanticCapabilityReport(SemanticFidelity fidelity, List<TextualSemanticIssue> issues) {
    public TextualSemanticCapabilityReport {
        fidelity = fidelity == null ? SemanticFidelity.UNAVAILABLE : fidelity;
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
    /** Descripción: Determina si el runtime textual puede resolver el modelo de forma nativa con la información canónica disponible. Entradas: no requiere parámetros de entrada. Salida: devuelve true cuando ningún problema registrado bloquea la ejecución o el linking nativo. */
    public boolean nativeRuntimeReady() { return issues.stream().noneMatch(TextualSemanticIssue::blocksNativeRuntime); }
}
