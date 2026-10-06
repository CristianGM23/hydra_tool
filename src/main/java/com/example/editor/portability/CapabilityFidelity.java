package com.example.editor.portability;

/** Fidelidad esperada al importar, exportar o inyectar una capacidad. */
public enum CapabilityFidelity {
    EXACT,
    SEMANTIC,
    STRUCTURAL,
    PARTIAL,
    PROJECTION_ONLY,
    UNSUPPORTED
}
