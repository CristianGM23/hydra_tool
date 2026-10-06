package com.example.editor.artifact.registry;

/** Resultado semántico de compatibilidad de un artefacto con sus dependencias actuales. */
public enum HydraArtifactCompatibilityStatus { COMPATIBLE, REVALIDATION_REQUIRED, MIGRATION_REQUIRED, INCOMPATIBLE }
