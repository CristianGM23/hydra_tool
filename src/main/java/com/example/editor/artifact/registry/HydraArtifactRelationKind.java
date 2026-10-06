package com.example.editor.artifact.registry;

/** Semántica explícita de una relación entre artefactos registrados. */
public enum HydraArtifactRelationKind {
    USES,
    USES_METAMODEL,
    GENERATED_FROM,
    VALIDATED_BY,
    TRANSFORMS_TO,
    COMPILED_FROM,
    HAS_TRACE,
    USES_GRAMMAR,
    REFERENCES
}
