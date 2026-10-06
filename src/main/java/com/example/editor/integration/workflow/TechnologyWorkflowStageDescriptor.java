package com.example.editor.integration.workflow;

import java.util.List;
import com.example.editor.integration.IntegrationSupportLevel;

/** Contrato portable de una fase tecnológica y de sus prerrequisitos visibles. */
public record TechnologyWorkflowStageDescriptor(
        TechnologyWorkflowStage stage,
        IntegrationSupportLevel support,
        boolean applicable,
        TechnologyWorkflowAction action,
        String title,
        String description,
        String outcome,
        List<String> prerequisites) {
    public TechnologyWorkflowStageDescriptor {
        if(stage == null || support == null || action == null) throw new IllegalArgumentException("stage, support and action are required");
        title = text(title);
        description = text(description);
        outcome = text(outcome);
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TechnologyWorkflowStageDescriptor. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value == null ? "" : value.trim();}
}
