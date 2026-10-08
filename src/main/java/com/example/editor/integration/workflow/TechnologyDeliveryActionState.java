package com.example.editor.integration.workflow;

/** Estado operativo de una acción concreta del recorrido tecnológico para un perfil persistido. */
public enum TechnologyDeliveryActionState {
    READY,
    READY_WITH_WARNINGS,
    BLOCKED,
    EXTERNAL,
    NOT_APPLICABLE
}
