package com.example.editor.integration;
import java.util.Set;
/** Funcionalidades neutrales que pueden incluirse en un paquete de integración. */
public enum IntegrationFeature {
    VALIDATION_RUNTIME(IntegrationCapability.VALIDATION_BRIDGE),
    QUICK_FIX_RUNTIME(IntegrationCapability.QUICK_FIX_BRIDGE),
    OPERATION_RUNTIME(IntegrationCapability.OPERATION_BRIDGE),
    EVENT_PUBLICATION(IntegrationCapability.EVENT_PUBLISH),
    EVENT_CONSUMPTION(IntegrationCapability.EVENT_CONSUME),
    EVENT_FLOW_RUNTIME(IntegrationCapability.EVENT_FLOW_RUNTIME),
    GAMIFICATION_RUNTIME(IntegrationCapability.GAMIFICATION_RUNTIME),
    GAMIFICATION_UI(IntegrationCapability.GAMIFICATION_RUNTIME, IntegrationCapability.UI_CONTRIBUTION),
    EDITOR_UI_CONTRIBUTION(IntegrationCapability.UI_CONTRIBUTION),
    NOTATION_CONTRIBUTION(IntegrationCapability.NOTATION_CONTRIBUTION),
    PALETTE_CONTRIBUTION(IntegrationCapability.PALETTE_CONTRIBUTION),
    VALIDATION_PROJECTION(IntegrationCapability.VALIDATION_PROJECTION),
    EVENT_DEFINITION_PROJECTION(IntegrationCapability.EVENT_DEFINITION_PROJECTION),
    EVENT_FLOW_PROJECTION(IntegrationCapability.EVENT_FLOW_PROJECTION),
    GAMIFICATION_PROJECTION(IntegrationCapability.GAMIFICATION_PROJECTION),
    CODE_GENERATION(IntegrationCapability.CODE_GENERATION),
    PROJECT_IMPORT(IntegrationCapability.PROJECT_IMPORT),
    PROJECT_EXPORT(IntegrationCapability.PROJECT_EXPORT);

    private final Set<IntegrationCapability> requiredCapabilities;
    /** Descripción: Inicializa una instancia de IntegrationFeature con sus dependencias y datos de entrada. Entradas: requiredCapabilities: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ IntegrationFeature(IntegrationCapability... requiredCapabilities) { this.requiredCapabilities = Set.of(requiredCapabilities); }
    /** Descripción: Valida las condiciones necesarias para «requiredCapabilities» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Set<IntegrationCapability> requiredCapabilities() { return requiredCapabilities; }
    /** Descripción: Procesa la operación «runtimeFeature» coordinando las responsabilidades de IntegrationFeature. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean runtimeFeature() { return name().endsWith("RUNTIME") || this == EVENT_PUBLICATION || this == EVENT_CONSUMPTION || this == GAMIFICATION_UI || this == EDITOR_UI_CONTRIBUTION || this == PALETTE_CONTRIBUTION || this == NOTATION_CONTRIBUTION || this == CODE_GENERATION; }
}
