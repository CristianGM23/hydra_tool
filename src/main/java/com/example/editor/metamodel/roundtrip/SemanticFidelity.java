package com.example.editor.metamodel.roundtrip;

/** Grado de fidelidad semántica observado después de una proyección y reimportación. */
public enum SemanticFidelity {
    EXACT,
    HIGH,
    APPROXIMATE,
    LOSSY,
    UNAVAILABLE;

    /** Descripción: Determina si este nivel de fidelidad conserva el significado estructural requerido para un round-trip seguro. Entradas: no requiere parámetros de entrada. Salida: devuelve true para EXACT y HIGH; devuelve false para aproximaciones, pérdidas o resultados no disponibles. */
    public boolean lossless() { return this == EXACT || this == HIGH; }
}
