package com.example.editor.integration.web;

import java.util.List;
import java.util.Map;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.roundtrip.RoundTripSyncPreview;
import com.example.editor.integration.roundtrip.resolution.ElementConflict;
import com.example.editor.integration.roundtrip.resolution.RoundTripResolutionPlan;

/** Web projection of an advanced resolution plan without generated file content. */
public record RoundTripResolutionPlanView(String operationId, IntegrationTechnology technology,
        String profileId, String targetRoot, RoundTripSyncPreview preview,
        List<RoundTripResolutionActionView> actions, List<ElementConflict> unresolved,
        boolean canApply, Map<String, String> metadata) {
    /** Descripción: Ejecuta la operación «from» correspondiente a la responsabilidad de RoundTripResolutionPlanView. Entradas: plan: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static RoundTripResolutionPlanView from(RoundTripResolutionPlan plan){
        return new RoundTripResolutionPlanView(plan.operationId(), plan.technology(), plan.profileId(),
                plan.targetRoot().toString(), plan.preview(), plan.actions().stream()
                        .map(RoundTripResolutionActionView::from).toList(), plan.unresolved(),
                plan.canApply(), plan.metadata());
    }
}
