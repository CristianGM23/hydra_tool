package com.example.editor.integration.workflow;

import java.util.List;
import com.example.editor.integration.IntegrationSupportLevel;

/** Acción operativa de entrega derivada para un perfil tecnológico concreto. */
public record TechnologyDeliveryActionDescriptor(
        TechnologyWorkflowStage stage,
        TechnologyWorkflowAction action,
        TechnologyDeliveryActionState state,
        IntegrationSupportLevel support,
        boolean hydraExecutable,
        boolean mutatesHost,
        boolean targetDirectoryRequired,
        boolean previewRequired,
        String apiSuffix,
        String httpMethod,
        String reason,
        List<String> prerequisites,
        List<String> evidence) {
    public TechnologyDeliveryActionDescriptor {
        if(stage == null || action == null || state == null || support == null) throw new IllegalArgumentException("stage, action, state and support are required");
        apiSuffix = normalize(apiSuffix);
        httpMethod = normalize(httpMethod);
        reason = normalize(reason);
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
    /** Descripción: Normaliza texto opcional de un descriptor de acción sin introducir valores nulos. Entradas: value: texto de entrada. Salida: texto no nulo y sin espacios exteriores. */
    private static String normalize(String value){return value == null ? "" : value.trim();}
}
