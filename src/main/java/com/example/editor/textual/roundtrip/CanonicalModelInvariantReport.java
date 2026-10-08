package com.example.editor.textual.roundtrip;

import java.util.List;

/** Resultado de validar identidad, cardinalidades, referencias y topología de contención de una instancia canónica. */
public record CanonicalModelInvariantReport(boolean valid, List<String> violations) {
    public CanonicalModelInvariantReport {
        violations = violations == null ? List.of() : List.copyOf(violations);
        valid = valid && violations.isEmpty();
    }
    /** Descripción: Exige una instancia estructuralmente válida antes de serializarla o promoverla. Entradas: no requiere parámetros. Salida: finaliza sin error cuando el modelo es válido o lanza una excepción con todas las violaciones detectadas. */
    public void requireValid() {
        if(!valid) throw new IllegalStateException("Canonical model invariants failed: " + violations);
    }
}
