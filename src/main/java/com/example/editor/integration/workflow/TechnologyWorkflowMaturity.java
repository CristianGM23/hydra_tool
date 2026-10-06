package com.example.editor.integration.workflow;

/** Madurez observable de una integración, calculada a partir de implementaciones reales y no solo del catálogo declarado. */
public enum TechnologyWorkflowMaturity {
    FULL_WORKFLOW,
    OPERATIONAL,
    PARTIAL,
    DECLARED_ONLY,
    PROJECTION_ONLY
}
