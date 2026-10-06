package com.example.editor.workbench.capability;

import java.util.List;

/** Cross-layer readiness view used by Home and the capability center for metamodel or grammar contexts. */
public record HydraCapabilityReadiness(String contextId, String contextName, String contextType, String semanticContextId, int score,
        int configuredLayers, int totalLayers, List<HydraCapabilityLayerSummary> layers,
        List<String> recommendedNextSteps, List<String> warnings) {
    public HydraCapabilityReadiness {
        if(contextId == null || contextId.isBlank()){throw new IllegalArgumentException("contextId is required");}
        contextName = contextName == null || contextName.isBlank() ? contextId : contextName.trim();
        contextType = contextType == null || contextType.isBlank() ? "METAMODEL" : contextType.trim().toUpperCase();
        semanticContextId = semanticContextId == null || semanticContextId.isBlank() ? contextId : semanticContextId.trim();
        if(!contextType.equals("METAMODEL") && !contextType.equals("GRAMMAR")){throw new IllegalArgumentException("Unsupported capability context type: "+contextType);}
        if(score < 0 || score > 100){throw new IllegalArgumentException("score must be between 0 and 100");}
        if(configuredLayers < 0 || totalLayers < 1 || configuredLayers > totalLayers){throw new IllegalArgumentException("invalid layer counts");}
        layers = List.copyOf(layers == null ? List.of() : layers);
        recommendedNextSteps = List.copyOf(recommendedNextSteps == null ? List.of() : recommendedNextSteps);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
    /** Descripción: Devuelve el identificador semántico compatible con consumidores históricos orientados a metamodelos. Entradas: no requiere parámetros. Salida: devuelve el identificador del contexto actual. */
    /** Descripción: Ejecuta la operación «metamodelId» correspondiente a la responsabilidad de HydraCapabilityReadiness. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String metamodelId(){ return contextId; }
    /** Descripción: Devuelve el nombre semántico compatible con consumidores históricos orientados a metamodelos. Entradas: no requiere parámetros. Salida: devuelve el nombre del contexto actual. */
    /** Descripción: Ejecuta la operación «metamodelName» correspondiente a la responsabilidad de HydraCapabilityReadiness. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String metamodelName(){ return contextName; }
}
